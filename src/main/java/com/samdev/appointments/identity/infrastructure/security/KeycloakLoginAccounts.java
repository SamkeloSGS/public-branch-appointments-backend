package com.samdev.appointments.identity.infrastructure.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.samdev.appointments.identity.LoginAccounts;
import com.samdev.appointments.identity.Subject;
import com.samdev.appointments.identity.infrastructure.config.KeycloakAdminProperties;

/**
 * Deletes Keycloak accounts through the admin REST API ({@link KeycloakAdminClient}). The Keycloak user ID is the
 * token subject. Outcomes are counted as {@code identity.login_account.deletions} (deleted, already gone, failed).
 */
class KeycloakLoginAccounts implements LoginAccounts {

	private static final Logger LOG = LoggerFactory.getLogger(KeycloakLoginAccounts.class);

	private final KeycloakAdminClient keycloak;
	private final MeterRegistry metrics;

	/**
	 * @param realmUrl the realm address the backend calls ({@code appointments.identity.keycloak-url}), such as
	 *        {@code http://localhost:8180/realms/appointments}
	 */
	KeycloakLoginAccounts(String realmUrl, KeycloakAdminProperties properties, RestClient.Builder http,
			MeterRegistry metrics) {
		this(new KeycloakAdminClient(realmUrl, properties, http), metrics);
	}

	KeycloakLoginAccounts(KeycloakAdminClient keycloak, MeterRegistry metrics) {
		this.keycloak = keycloak;
		this.metrics = metrics;
	}

	@Override
	public void delete(Subject subject) {

		try {
			var status = keycloak.http().delete()
					.uri(keycloak.admin("/users/{id}"), subject.value())
					.headers(it -> it.setBearerAuth(keycloak.serviceAccountToken()))
					.exchange((request, response) -> response.getStatusCode());

			if (status.value() == 404) {
				LOG.info("Login account already gone; nothing to delete");
				count("already_gone");
				return;
			}
			if (!status.is2xxSuccessful()) {
				throw KeycloakAdminClient.refused("the account deletion request", status);
			}

			LOG.info("Deleted the login account of an erased customer");
			count("deleted");

		} catch (RestClientException e) {
			count("failed");
			throw new IllegalStateException("Keycloak could not be reached to delete a login account", e);
		} catch (IllegalStateException e) {
			count("failed");
			throw e;
		}
	}

	private void count(String outcome) {
		Counter.builder("identity.login_account.deletions")
				.description("Keycloak login deletions for erased customers, by outcome")
				.tag("outcome", outcome)
				.register(metrics)
				.increment();
	}
}
