package com.samdev.appointments.identity.infrastructure.security;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Refusals by the security filter chain as Problem Details. A token below the required sign-in level gets
 * {@code 401} with {@code WWW-Authenticate: Bearer error="insufficient_user_authentication"} (RFC 9470), so the
 * Angular app can start the step-up; everything else gets {@code 403}.
 */
class SecurityErrorResponses implements AccessDeniedHandler {

	static final String REQUIRED_LEVEL = "2";

	private final JsonMapper json;

	SecurityErrorResponses(JsonMapper json) {
		this.json = json;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
			throws IOException {

		if (e instanceof InsufficientUserAuthenticationException) {
			response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_user_authentication\", "
					+ "error_description=\"A second factor is required\", acr_values=\"" + REQUIRED_LEVEL + "\"");
			write(response, HttpStatus.UNAUTHORIZED, "Second factor required", e.getMessage(), request);
		} else {
			write(response, HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this action", request);
		}
	}

	/**
	 * {@code 429} for {@link RequestRateLimiter}, with the code {@code RATE_LIMITED}; the caller sets
	 * {@code Retry-After}.
	 */
	void tooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
		write(response, HttpStatus.TOO_MANY_REQUESTS, "Too many requests",
				"Too many requests in a short time. Try again after the time in Retry-After.", request, "RATE_LIMITED");
	}

	private void write(HttpServletResponse response, HttpStatus status, String title, String detail,
			HttpServletRequest request) throws IOException {
		write(response, status, title, detail, request, null);
	}

	private void write(HttpServletResponse response, HttpStatus status, String title, String detail,
			HttpServletRequest request, @Nullable String code) throws IOException {

		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		if (code != null) {
			problem.setProperty("code", code);
		}
		problem.setInstance(java.net.URI.create(request.getRequestURI()));

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write(json.writeValueAsString(problem));
	}
}
