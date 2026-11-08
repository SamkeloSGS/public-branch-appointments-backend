package com.samdev.appointments.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

import com.samdev.appointments.identity.infrastructure.config.RateLimitProperties;

/**
 * Token buckets per caller, without Spring: 3 requests a minute without a token, 5 signed in, and 2 changes.
 */
class RequestRateLimiterTests {

	final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

	RequestRateLimiter limiter = limiter(true);

	@AfterEach
	void clearUser() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("OPS-3 requests without a token are limited per client address; another address has its own limit")
	void anonymousPerAddress() throws Exception {

		for (var i = 0; i < 3; i++) {
			assertThat(send("GET", "/api/v1/branches", "10.0.0.1").getStatus()).isEqualTo(200);
		}

		var refused = send("GET", "/api/v1/branches", "10.0.0.1");

		assertThat(refused.getStatus()).isEqualTo(429);
		assertThat(refused.getHeader("Retry-After")).isNotNull()
				.satisfies(it -> assertThat(Integer.parseInt(it)).isBetween(1, 60));
		assertThat(refused.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"").doesNotContain("10.0.0.1");
		assertThat(send("GET", "/api/v1/branches", "10.0.0.2").getStatus()).isEqualTo(200);
		assertThat(metrics.get("http.server.requests.rate_limited").tag("limit", "anonymous").counter().count())
				.isEqualTo(1);
	}

	@Test
	@DisplayName("OPS-3 signed-in users are limited by subject, whatever address they use")
	void perUser() throws Exception {

		signIn("user-a");
		for (var i = 0; i < 5; i++) {
			assertThat(send("GET", "/api/v1/appointments/mine", "10.0.0." + i).getStatus()).isEqualTo(200);
		}
		assertThat(send("GET", "/api/v1/appointments/mine", "10.0.0.9").getStatus()).isEqualTo(429);

		signIn("user-b");
		assertThat(send("GET", "/api/v1/appointments/mine", "10.0.0.1").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("OPS-3 booking changes have their own, smaller limit; reading appointments does not use it")
	void changes() throws Exception {

		signIn("user-c");

		assertThat(send("POST", "/api/v1/appointments", "10.0.0.1").getStatus()).isEqualTo(200);
		assertThat(send("POST", "/api/v1/appointments/mine/ABCD2345/cancel", "10.0.0.1").getStatus()).isEqualTo(200);
		assertThat(send("GET", "/api/v1/appointments/mine", "10.0.0.1").getStatus()).isEqualTo(200);
		assertThat(send("POST", "/api/v1/appointments", "10.0.0.1").getStatus()).isEqualTo(429);
		assertThat(metrics.get("http.server.requests.rate_limited").tag("limit", "changes").counter().count())
				.isEqualTo(1);
	}

	@Test
	@DisplayName("OPS-3 health checks are never limited, and nothing is limited when switched off")
	void exemptions() throws Exception {

		for (var i = 0; i < 10; i++) {
			assertThat(send("GET", "/actuator/health/readiness", "10.0.0.1").getStatus()).isEqualTo(200);
		}

		limiter = limiter(false);
		for (var i = 0; i < 10; i++) {
			assertThat(send("GET", "/api/v1/branches", "10.0.0.3").getStatus()).isEqualTo(200);
		}
	}

	private RequestRateLimiter limiter(boolean enabled) {
		return new RequestRateLimiter(new RateLimitProperties(enabled, 3, 5, 2, List.of("/api/v1/appointments/**")),
				new SecurityErrorResponses(JsonMapper.builder().build()), metrics);
	}

	private MockHttpServletResponse send(String method, String path, @Nullable String address) throws Exception {

		var request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(address);
		var response = new MockHttpServletResponse();

		limiter.doFilter(request, response, new MockFilterChain());

		return response;
	}

	private static void signIn(String subject) {
		var jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(300)).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(), subject));
	}
}
