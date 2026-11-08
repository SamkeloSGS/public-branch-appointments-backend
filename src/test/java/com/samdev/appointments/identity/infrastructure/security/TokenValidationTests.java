package com.samdev.appointments.identity.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.samdev.appointments.IntegrationTest;

/**
 * IA-1 with the production decoder: signature against published keys, issuer, audience, expiry with 30 seconds of
 * tolerance, and key rotation without a restart. Keys are served by a local HTTP server standing in for Keycloak.
 */
@IntegrationTest
class TokenValidationTests {

	static final String ISSUER = "https://auth.appointments.test/realms/appointments";

	static final RSAKey FIRST_KEY = key();
	static final RSAKey ROTATED_KEY = key();
	static final RSAKey UNPUBLISHED_KEY = key();
	static final AtomicReference<JWKSet> PUBLISHED = new AtomicReference<>(new JWKSet(FIRST_KEY.toPublicJWK()));
	static final HttpServer KEY_SERVER = keyServer();

	@Autowired MockMvc mvc;

	@DynamicPropertySource
	static void keycloak(DynamicPropertyRegistry registry) {
		registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
		registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
				() -> "http://localhost:" + KEY_SERVER.getAddress().getPort() + "/certs");
	}

	@AfterAll
	static void stopKeyServer() {
		KEY_SERVER.stop(0);
	}

	@Test
	@DisplayName("IA-1 a valid token for the right issuer and audience is accepted")
	void acceptsValidToken() throws Exception {
		call(token(FIRST_KEY, ISSUER, "appointments-api", Instant.now().plusSeconds(300))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("IA-1 missing, expired, unknown-key, wrong-issuer and wrong-audience tokens are refused with 401")
	void refusesInvalidTokens() throws Exception {

		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());

		call(token(FIRST_KEY, ISSUER, "appointments-api", Instant.now().minusSeconds(120)))
				.andExpect(status().isUnauthorized());
		call(token(UNPUBLISHED_KEY, ISSUER, "appointments-api", Instant.now().plusSeconds(300)))
				.andExpect(status().isUnauthorized());
		call(token(FIRST_KEY, "https://evil.test/realms/appointments", "appointments-api",
				Instant.now().plusSeconds(300))).andExpect(status().isUnauthorized());
		call(token(FIRST_KEY, ISSUER, "some-other-api", Instant.now().plusSeconds(300)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("IA-1 expiry is checked with at most 30 seconds of clock tolerance")
	void toleratesSmallClockSkew() throws Exception {
		call(token(FIRST_KEY, ISSUER, "appointments-api", Instant.now().minusSeconds(10))).andExpect(status().isOk());
	}

	@Test
	@DisplayName("IA-1 after Keycloak rotates its signing key, new tokens are accepted without a restart")
	void acceptsRotatedKey() throws Exception {

		call(token(FIRST_KEY, ISSUER, "appointments-api", Instant.now().plusSeconds(300))).andExpect(status().isOk());

		PUBLISHED.set(new JWKSet(List.of(ROTATED_KEY.toPublicJWK(), FIRST_KEY.toPublicJWK())));

		call(token(ROTATED_KEY, ISSUER, "appointments-api", Instant.now().plusSeconds(300))).andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions call(String token) throws Exception {
		return mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private static String token(RSAKey key, String issuer, String audience, Instant expiry) throws JOSEException {

		var claims = new JWTClaimsSet.Builder()
				.subject(UUID.randomUUID().toString())
				.issuer(issuer)
				.audience(audience)
				.issueTime(Date.from(Instant.now().minusSeconds(600)))
				.expirationTime(Date.from(expiry))
				.claim("acr", "1")
				.build();

		var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));

		return jwt.serialize();
	}

	private static RSAKey key() {
		try {
			return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
		} catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
	}

	private static HttpServer keyServer() {
		try {
			var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/certs", exchange -> {
				var body = PUBLISHED.get().toString().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			return server;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
