package com.samdev.appointments.branchmanagement.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.samdev.appointments.branchmanagement.application.port.BranchSettings;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BranchProperties.class)
class BranchConfiguration {

	/**
	 * Checks the configuration once, at start-up.
	 */
	@Bean
	BranchSettings branchSettings(BranchProperties properties) {
		var branch = properties.toProfile();
		return () -> branch;
	}
}
