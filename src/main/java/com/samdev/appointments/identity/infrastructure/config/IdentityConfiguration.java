package com.samdev.appointments.identity.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ IdentityProperties.class, RateLimitProperties.class })
class IdentityConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Clock identityClock() {
		return Clock.systemUTC();
	}
}
