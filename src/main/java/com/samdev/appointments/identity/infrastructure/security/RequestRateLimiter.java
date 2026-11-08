package com.samdev.appointments.identity.infrastructure.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.samdev.appointments.identity.infrastructure.config.RateLimitProperties;

/**
 * Limits requests per caller with token buckets, after the token has been validated so a signed-in user is limited by
 * subject and anyone else by client address. Two buckets apply:
 * <ul>
 * <li>a general one: {@code anonymous-per-minute} or {@code user-per-minute};</li>
 * <li>for non-GET requests to the {@code change-paths} (booking, rescheduling, cancelling), a much smaller one, so a
 * script cannot grab or churn appointment times.</li>
 * </ul>
 * A refused request gets {@code 429} with {@code Retry-After} in seconds and the code {@code RATE_LIMITED}, and is
 * counted in {@code http.server.requests.rate_limited}. Actuator paths are never limited (load balancers poll them).
 * <p>
 * Buckets live in memory, idle ones expire after 10 minutes. With several instances each has its own, so the
 * effective limit is per instance; a shared store (Bucket4j with Redis) or the gateway would make it global. Behind a
 * proxy the client address is the proxy's unless {@code server.forward-headers-strategy} is set.
 */
class RequestRateLimiter extends OncePerRequestFilter {

	private static final Duration PERIOD = Duration.ofMinutes(1);

	private final RateLimitProperties properties;
	private final SecurityErrorResponses errors;
	private final MeterRegistry metrics;
	private final AntPathMatcher paths = new AntPathMatcher();
	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
			.expireAfterAccess(Duration.ofMinutes(10))
			.maximumSize(100_000)
			.build();

	RequestRateLimiter(RateLimitProperties properties, SecurityErrorResponses errors, MeterRegistry metrics) {
		this.properties = properties;
		this.errors = errors;
		this.metrics = metrics;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !properties.enabled() || request.getRequestURI().startsWith("/actuator/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		var user = SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
				? token.getName() : null;
		var caller = user != null ? "user:" + user : "address:" + request.getRemoteAddr();

		var limit = user != null ? "user" : "anonymous";
		var perMinute = user != null ? properties.userPerMinute() : properties.anonymousPerMinute();

		if (!tryConsume(limit, caller, perMinute, request, response)) {
			return;
		}
		if (isChange(request) && !tryConsume("changes", caller, properties.changesPerMinute(), request, response)) {
			return;
		}

		chain.doFilter(request, response);
	}

	private boolean tryConsume(String limit, String caller, int perMinute, HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		var bucket = buckets.get(limit + "|" + caller, key -> Bucket.builder()
				.addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, PERIOD).build())
				.build());
		var probe = bucket.tryConsumeAndReturnRemaining(1);

		if (probe.isConsumed()) {
			return true;
		}

		var seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
		errors.tooManyRequests(request, response);

		Counter.builder("http.server.requests.rate_limited")
				.description("Requests refused with 429, by the limit that refused them")
				.tag("limit", limit)
				.register(metrics)
				.increment();

		return false;
	}

	private boolean isChange(HttpServletRequest request) {
		return !HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())
				&& properties.changePaths().stream().anyMatch(it -> paths.match(it, request.getRequestURI()));
	}
}
