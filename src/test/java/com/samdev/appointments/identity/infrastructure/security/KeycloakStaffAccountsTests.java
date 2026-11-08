package com.samdev.appointments.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.domain.vo.EmailAddress;
import com.samdev.appointments.identity.infrastructure.config.KeycloakAdminProperties;

/**
 * The Keycloak admin calls for staff invitations against a local stand-in for Keycloak: find by email, create with
 * the invitation attribute, execute-actions email; find by attribute and delete on withdrawal.
 */
class KeycloakStaffAccountsTests {

	static final Instant NOW = Instant.parse("2026-11-01T08:00:00Z");
	static final StaffMemberId INVITATION = new StaffMemberId(java.util.UUID.fromString(
			"6a3c1a8e-0d7e-4c4e-9b8e-2f1b5a0c9d11"));
	static final EmailAddress EMAIL = new EmailAddress("lerato+staff@capitec.test");

	HttpServer keycloak;
	final List<String> requests = new CopyOnWriteArrayList<>();
	volatile String usersByEmail = "[]";
	volatile String usersByInvitation = "[]";
	volatile int actionsStatus = 204;

	final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
	KeycloakStaffAccounts accounts;

	@BeforeEach
	void start() throws IOException {

		keycloak = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

		keycloak.createContext("/realms/appointments/protocol/openid-connect/token",
				exchange -> respond(exchange, 200, "{\"access_token\":\"service-token\"}"));
		keycloak.createContext("/admin/realms/appointments/users", exchange -> {
			var method = exchange.getRequestMethod();
			var path = exchange.getRequestURI().getPath();
			var query = exchange.getRequestURI().getRawQuery() == null ? ""
					: URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
			var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			requests.add((method + " " + path + " " + query + " " + body).strip());

			if (method.equals("GET")) {
				respond(exchange, 200, query.contains("q=") ? usersByInvitation : usersByEmail);
			} else if (method.equals("POST")) {
				exchange.getResponseHeaders().add("Location",
						"http://keycloak/admin/realms/appointments/users/new-user-id");
				respond(exchange, 201, "");
			} else if (path.endsWith("/execute-actions-email")) {
				respond(exchange, actionsStatus, "");
			} else {
				respond(exchange, 204, "");
			}
		});
		keycloak.start();

		var realm = "http://localhost:" + keycloak.getAddress().getPort() + "/realms/appointments";
		var properties = new KeycloakAdminProperties(true, "appointments-backend", "s3cret", "appointments-web",
				"http://localhost:4200/admin");
		accounts = new KeycloakStaffAccounts(new KeycloakAdminClient(realm, properties, RestClient.builder()), metrics,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@AfterEach
	void stop() {
		keycloak.stop(0);
	}

	@Test
	@DisplayName("IA-5 a new person gets a login made for the invitation and one email: verify, password, authenticator")
	void createsLoginAndEmailsSetupLink() {

		accounts.invite(INVITATION, EMAIL, "Lerato Mokoena", NOW.plus(Duration.ofDays(14)));

		assertThat(requests).hasSize(3);
		assertThat(requests.get(0)).startsWith("GET /admin/realms/appointments/users ")
				.contains("email=lerato+staff@capitec.test").contains("exact=true");
		assertThat(requests.get(1)).startsWith("POST /admin/realms/appointments/users")
				.contains("\"username\":\"lerato+staff@capitec.test\"").contains("\"firstName\":\"Lerato\"")
				.contains("\"lastName\":\"Mokoena\"").contains("\"emailVerified\":false")
				.contains("\"staffMemberId\":[\"" + INVITATION + "\"]");
		assertThat(requests.get(2))
				.startsWith("PUT /admin/realms/appointments/users/new-user-id/execute-actions-email")
				.contains("client_id=appointments-web").contains("redirect_uri=http://localhost:4200/admin")
				.contains("lifespan=" + Duration.ofDays(14).toSeconds())
				.endsWith("[\"VERIFY_EMAIL\",\"UPDATE_PASSWORD\",\"CONFIGURE_TOTP\"]");
		assertThat(outcome("emailed")).isEqualTo(1);
	}

	@Test
	@DisplayName("IA-5 someone who already has a verified login gets no email and no second login")
	void existingVerifiedLogin() {

		usersByEmail = "[{\"id\":\"their-own\",\"emailVerified\":true}]";

		accounts.invite(INVITATION, EMAIL, "Lerato", NOW.plus(Duration.ofDays(14)));

		assertThat(requests).singleElement().asString().startsWith("GET");
		assertThat(outcome("existing_account")).isEqualTo(1);
	}

	@Test
	@DisplayName("IA-5 an existing login with an unverified email only gets the verify link")
	void existingUnverifiedLogin() {

		usersByEmail = "[{\"id\":\"their-own\",\"emailVerified\":false}]";

		accounts.invite(INVITATION, EMAIL, "Lerato", NOW.plus(Duration.ofDays(14)));

		assertThat(requests).hasSize(2);
		assertThat(requests.get(1)).contains("/users/their-own/execute-actions-email").endsWith("[\"VERIFY_EMAIL\"]");
	}

	@Test
	@DisplayName("IA-5 re-inviting reuses the login made for the invitation and sends the setup link again")
	void reinviteReusesLogin() {

		usersByEmail = "[{\"id\":\"made-for-it\",\"emailVerified\":false,\"attributes\":{\"staffMemberId\":[\""
				+ INVITATION + "\"]}}]";

		accounts.invite(INVITATION, EMAIL, "Lerato", NOW.plus(Duration.ofDays(2)));

		assertThat(requests).hasSize(2).noneMatch(it -> it.startsWith("POST"));
		assertThat(requests.get(1)).contains("/users/made-for-it/execute-actions-email")
				.contains("lifespan=" + Duration.ofDays(2).toSeconds())
				.endsWith("[\"VERIFY_EMAIL\",\"UPDATE_PASSWORD\",\"CONFIGURE_TOTP\"]");
	}

	@Test
	@DisplayName("IA-10 withdrawing deletes only the login made for that invitation")
	void withdrawDeletesOnlyItsLogin() {

		usersByInvitation = "[{\"id\":\"made-for-it\",\"attributes\":{\"staffMemberId\":[\"" + INVITATION + "\"]}},"
				+ "{\"id\":\"loose-match\",\"attributes\":{\"staffMemberId\":[\"" + INVITATION + "-other\"]}}]";

		accounts.withdraw(INVITATION);

		assertThat(requests).hasSize(2);
		assertThat(requests.get(0)).contains("q=staffMemberId:" + INVITATION);
		assertThat(requests.get(1)).isEqualTo("DELETE /admin/realms/appointments/users/made-for-it");
		assertThat(outcome("withdrawn")).isEqualTo(1);
	}

	@Test
	@DisplayName("IA-5 a refused call throws, so the invitation event is retried")
	void failures() {

		actionsStatus = 500;

		assertThatIllegalStateException()
				.isThrownBy(() -> accounts.invite(INVITATION, EMAIL, "Lerato", NOW.plus(Duration.ofDays(14))))
				.withMessageContaining("500");
		assertThat(outcome("failed")).isEqualTo(1);
	}

	private double outcome(String outcome) {
		var counter = metrics.find("identity.staff_invitations").tag("outcome", outcome).counter();
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
