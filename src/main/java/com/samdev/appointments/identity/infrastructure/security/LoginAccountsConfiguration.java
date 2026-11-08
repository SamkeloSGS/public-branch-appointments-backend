package com.samdev.appointments.identity.infrastructure.security;

import java.time.Clock;
import java.time.Instant;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.samdev.appointments.identity.LoginAccounts;
import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.application.port.StaffAccounts;
import com.samdev.appointments.identity.domain.vo.EmailAddress;
import com.samdev.appointments.identity.infrastructure.config.KeycloakAdminProperties;

/**
 * Staff logins and account deletion through Keycloak, or logging stand-ins when
 * {@code appointments.identity.keycloak-admin.enabled} is {@code false} (tests).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KeycloakAdminProperties.class)
class LoginAccountsConfiguration {

	@Bean
	LoginAccounts loginAccounts(KeycloakAdminProperties properties,
			@Value("${appointments.identity.keycloak-url}") String keycloakUrl,
			ObjectProvider<RestClient.Builder> http, MeterRegistry metrics) {

		if (properties.enabled()) {
			return new KeycloakLoginAccounts(keycloakUrl, properties, http.getIfAvailable(RestClient::builder),
					metrics);
		}

		var log = LoggerFactory.getLogger(LoginAccounts.class);
		return subject -> log.info("Keycloak account deletion is disabled; the login account is kept");
	}

	@Bean
	StaffAccounts staffAccounts(KeycloakAdminProperties properties,
			@Value("${appointments.identity.keycloak-url}") String keycloakUrl,
			ObjectProvider<RestClient.Builder> http, MeterRegistry metrics, Clock clock) {

		if (properties.enabled()) {
			return new KeycloakStaffAccounts(
					new KeycloakAdminClient(keycloakUrl, properties, http.getIfAvailable(RestClient::builder)),
					metrics, clock);
		}

		var log = LoggerFactory.getLogger(StaffAccounts.class);
		return new StaffAccounts() {

			@Override
			public void invite(StaffMemberId invitation, EmailAddress email, String displayName, Instant expiresAt) {
				log.info("Keycloak admin calls are disabled; no staff setup email sent");
			}

			@Override
			public void withdraw(StaffMemberId invitation) {
				log.info("Keycloak admin calls are disabled; no login deleted for the withdrawn invitation");
			}
		};
	}
}
