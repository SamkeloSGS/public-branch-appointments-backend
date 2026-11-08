package com.samdev.appointments.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.samdev.appointments.identity.Subject;
import com.samdev.appointments.identity.infrastructure.config.KeycloakAdminProperties;

/**
 * The Keycloak admin calls against a local stand-in for Keycloak: client-credentials token, then delete by subject.
 */
class KeycloakLoginAccountsTests {

	HttpServer keycloak;
	final List<String> requests = new CopyOnWriteArrayList<>();
	volatile int deleteStatus = 204;
	volatile int tokenStatus = 200;

	final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
	KeycloakLoginAccounts accounts;

	@BeforeEach
	void start() throws IOException {

		keycloak = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

		keycloak.createContext("/realms/appointments/protocol/openid-connect/token", exchange -> {
			var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			requests.add("TOKEN " + body);
			respond(exchange, tokenStatus, "{\"access_token\":\"service-token\",\"token_type\":\"Bearer\"}");
		});
		keycloak.createContext("/admin/realms/appointments/users/", exchange -> {
			requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
					+ exchange.getRequestHeaders().getFirst("Authorization"));
			respond(exchange, deleteStatus, "");
		});
		keycloak.start();

		var issuer = "http://localhost:" + keycloak.getAddress().getPort() + "/realms/appointments";
		accounts = new KeycloakLoginAccounts(issuer,
				new KeycloakAdminProperties(true, "appointments-backend", "s3cret", "appointments-web",
						"http://localhost:4200/admin"), RestClient.builder(), metrics);
	}

	@AfterEach
	void stop() {
		keycloak.stop(0);
	}

	@Test
	@DisplayName("IA-15 deletes the account by subject with a client-credentials token of appointments-backend")
	void deletes() {

		accounts.delete(new Subject("5f2c-user"));

		assertThat(requests).hasSize(2);
		assertThat(requests.get(0)).startsWith("TOKEN ").contains("grant_type=client_credentials")
				.contains("client_id=appointments-backend").contains("client_secret=s3cret");
		assertThat(requests.get(1)).isEqualTo("DELETE /admin/realms/appointments/users/5f2c-user Bearer service-token");
		assertThat(deletions("deleted")).isEqualTo(1);
	}

	@Test
	@DisplayName("IA-15 an account that is already gone counts as deleted")
	void alreadyGone() {

		deleteStatus = 404;

		accounts.delete(new Subject("gone"));

		assertThat(requests).last().asString().startsWith("DELETE");
		assertThat(deletions("already_gone")).isEqualTo(1);
	}

	@Test
	@DisplayName("IA-15 a refused or failed call throws, so the erasure event is retried")
	void failures() {

		deleteStatus = 503;
		assertThatIllegalStateException().isThrownBy(() -> accounts.delete(new Subject("x")))
				.withMessageContaining("503");

		tokenStatus = 401;
		assertThatIllegalStateException().isThrownBy(() -> accounts.delete(new Subject("x")))
				.withMessageContaining("401");

		assertThat(deletions("failed")).isEqualTo(2);
	}

	@Test
	@DisplayName("IA-15 account deletion needs a client secret when enabled")
	void needsSecret() {
		assertThatIllegalStateException().isThrownBy(() -> new KeycloakLoginAccounts("http://localhost/realms/a",
				new KeycloakAdminProperties(true, "appointments-backend", " ", "appointments-web",
						"http://localhost:4200/admin"), RestClient.builder(), metrics));
	}

	private double deletions(String outcome) {
		var counter = metrics.find("identity.login_account.deletions").tag("outcome", outcome).counter();
		return counter == null ? 0 : counter.count();
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		var bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}
}
