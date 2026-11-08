package com.samdev.appointments.customer.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Settings, the clock, and scheduling for the access-audit retention.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CustomerProperties.class)
@EnableScheduling
class CustomerConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Clock customerClock() {
		return Clock.systemUTC();
	}
}
