package com.samdev.appointments.identity.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Request limits per caller: the token's subject when signed in, otherwise the client address. Each limit is a token
 * bucket that refills evenly over a minute, so short bursts up to the limit are allowed.
 *
 * @param enabled {@code false} turns limiting off (tests)
 * @param anonymousPerMinute requests without a token, per client address
 * @param userPerMinute requests per signed-in user
 * @param changesPerMinute non-GET requests to {@code changePaths}, per user or address, on top of the general limit
 * @param changePaths Ant-style patterns of the requests counted by {@code changesPerMinute}
 */
@ConfigurationProperties("appointments.identity.rate-limits")
public record RateLimitProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("120") int anonymousPerMinute, @DefaultValue("300") int userPerMinute,
		@DefaultValue("10") int changesPerMinute, @DefaultValue("/api/v1/appointments/**") List<String> changePaths) {}
