package com.samdev.appointments.notification.domain.vo;

import java.time.Duration;
import java.util.List;

/**
 * How often and how long a channel is retried after transient failures.
 *
 * @param delays the wait after the first, second, ... failed attempt; the last repeats
 */
public record RetryPolicy(int maxAttempts, List<Duration> delays) {

	public static final RetryPolicy DEFAULT = new RetryPolicy(5,
			List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60)));

	public RetryPolicy {
		if (maxAttempts < 1 || delays.isEmpty()) {
			throw new IllegalArgumentException("At least one attempt and one delay are required");
		}
		delays = List.copyOf(delays);
	}

	/**
	 * The wait after the given number of attempts.
	 */
	public Duration delayAfter(int attempts) {
		return delays.get(Math.min(Math.max(attempts, 1), delays.size()) - 1);
	}
}
