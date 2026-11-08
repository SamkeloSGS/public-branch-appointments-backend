package com.samdev.appointments.booking.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Settings, the clock, and scheduling for the booking jobs.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BookingProperties.class)
@EnableScheduling
class BookingConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Clock bookingClock() {
		return Clock.systemUTC();
	}
}
