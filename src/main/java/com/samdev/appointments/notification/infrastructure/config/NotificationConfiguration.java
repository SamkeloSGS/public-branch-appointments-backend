package com.samdev.appointments.notification.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Settings, the clock, and scheduling for the dispatcher and retention jobs.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
@EnableScheduling
class NotificationConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Clock notificationClock() {
		return Clock.systemUTC();
	}
}
