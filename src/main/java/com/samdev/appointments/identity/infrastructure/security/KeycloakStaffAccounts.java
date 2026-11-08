package com.samdev.appointments.identity.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.application.port.StaffAccounts;
import com.samdev.appointments.identity.domain.vo.EmailAddress;

/**
 * Staff logins through Keycloak's admin REST API ({@link KeycloakAdminClient}). A login created for an invitation
 * carries the attribute {@value #INVITATION_ATTRIBUTE} (the staff member ID), which is how {@link #withdraw} finds it
 * and how it tells a login it created from one the person already had. The setup link is Keycloak's own
 * "execute actions" email, sent through the realm's SMTP settings. Outcomes are counted as
 * {@code identity.staff_invitations} (emailed, existing_account, withdrawn, failed).
 */
class KeycloakStaffAccounts implements StaffAccounts {

	static final String INVITATION_ATTRIBUTE = "staffMemberId";

	private static final Logger LOG = LoggerFactory.getLogger(KeycloakStaffAccounts.class);
	private static final ParameterizedTypeReference<List<Map<String, Object>>> USERS = new ParameterizedTypeReference<>() {};
	private static final Duration SHORTEST_LINK = Duration.ofHours(1);

	private final KeycloakAdminClient keycloak;
	private final MeterRegistry metrics;
	private final Clock clock;

	KeycloakStaffAccounts(KeycloakAdminClient keycloak, MeterRegistry metrics, Clock clock) {
		this.keycloak = keycloak;
		this.metrics = metrics;
		this.clock = clock;
	}

	@Override
	public void invite(StaffMemberId invitation, EmailAddress email, String displayName, Instant expiresAt) {

		call(() -> {
			var token = keycloak.serviceAccountToken();
			var existing = findByEmail(token, email);

			String userId;
			boolean created;
			boolean verified;

			if (existing.isEmpty()) {
				userId = create(token, invitation, email, displayName)
						.or(() -> findByEmail(token, email).map(KeycloakStaffAccounts::id))
						.orElseThrow(() -> new IllegalStateException("Keycloak created no login for the invitation"));
				created = true;
				verified = false;
			} else {
				var user = existing.get();
				userId = id(user);
				created = invitation.toString().equals(invitationOf(user));
				verified = Boolean.TRUE.equals(user.get("emailVerified"));
			}

			var actions = new ArrayList<String>();
			if (!verified) {
				actions.add("VERIFY_EMAIL");
			}
			if (created) {
				actions.add("UPDATE_PASSWORD");
				actions.add("CONFIGURE_TOTP");
			}

			if (actions.isEmpty()) {
				LOG.info("Invited person already has a verified login; they sign in as usual, no setup email sent");
				count("existing_account");
				return;
			}

			sendSetupEmail(token, userId, actions, expiresAt);
			LOG.info("Sent the staff setup email ({})", String.join(", ", actions));
			count("emailed");
		});
	}

	@Override
	public void withdraw(StaffMemberId invitation) {

		call(() -> {
			var token = keycloak.serviceAccountToken();

			for (var user : findByInvitation(token, invitation)) {
				var status = keycloak.http().delete()
						.uri(keycloak.admin("/users/{id}"), id(user))
						.headers(it -> it.setBearerAuth(token))
						.exchange((request, response) -> response.getStatusCode());

				if (!status.is2xxSuccessful() && status.value() != 404) {
					throw KeycloakAdminClient.refused("deleting the login of a withdrawn invitation", status);
				}

				LOG.info("Deleted the unused login of a withdrawn staff invitation");
				count("withdrawn");
			}
		});
	}

	private Optional<Map<String, Object>> findByEmail(String token, EmailAddress email) {
		var users = keycloak.http().get()
				.uri(keycloak.admin("/users"), builder -> builder.queryParam("email", "{email}")
						.queryParam("exact", true).queryParam("briefRepresentation", false).build(email.value()))
				.headers(it -> it.setBearerAuth(token))
				.retrieve()
				.body(USERS);
		return users == null ? Optional.empty() : users.stream().findFirst();
	}

	private List<Map<String, Object>> findByInvitation(String token, StaffMemberId invitation) {
		var users = keycloak.http().get()
				.uri(keycloak.admin("/users"), builder -> builder.queryParam("q", "{query}")
						.queryParam("briefRepresentation", false)
						.build(INVITATION_ATTRIBUTE + ":" + invitation))
				.headers(it -> it.setBearerAuth(token))
				.retrieve()
				.body(USERS);

		// The q search matches attribute values loosely; keep only exact matches
		return users == null ? List.of()
				: users.stream().filter(it -> invitation.toString().equals(invitationOf(it))).toList();
	}

	/**
	 * The new login's ID, or empty when someone else created one with this email meanwhile.
	 */
	private Optional<String> create(String token, StaffMemberId invitation, EmailAddress email, String displayName) {

		var name = displayName.strip().split("\\s+", 2);
		var user = new LinkedHashMap<String, Object>();
		user.put("username", email.value());
		user.put("email", email.value());
		user.put("firstName", name[0]);
		if (name.length > 1) {
			user.put("lastName", name[1]);
		}
		user.put("enabled", true);
		user.put("emailVerified", false);
		user.put("attributes", Map.of(INVITATION_ATTRIBUTE, List.of(invitation.toString())));

		return keycloak.http().post()
				.uri(keycloak.admin("/users"))
				.headers(it -> it.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.body(user)
				.exchange((request, response) -> {
					if (response.getStatusCode().value() == 409) {
						return Optional.<String> empty();
					}
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw KeycloakAdminClient.refused("creating a staff login", response.getStatusCode());
					}
					var location = response.getHeaders().getLocation();
					if (location == null) {
						throw new IllegalStateException("Keycloak created a staff login without returning its address");
					}
					var path = location.getPath();
					return Optional.of(path.substring(path.lastIndexOf('/') + 1));
				});
	}

	private void sendSetupEmail(String token, String userId, List<String> actions, Instant expiresAt) {

		var lifespan = Duration.between(clock.instant(), expiresAt);
		var seconds = (lifespan.compareTo(SHORTEST_LINK) < 0 ? SHORTEST_LINK : lifespan).toSeconds();
		var properties = keycloak.properties();

		var status = keycloak.http().put()
				.uri(keycloak.admin("/users/{id}/execute-actions-email"), builder -> builder
						.queryParam("client_id", "{client}").queryParam("redirect_uri", "{redirect}")
						.queryParam("lifespan", seconds)
						.build(userId, properties.setupClientId(), properties.setupRedirectUrl()))
				.headers(it -> it.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.body(actions)
				.exchange((request, response) -> response.getStatusCode());

		if (!status.is2xxSuccessful()) {
			throw KeycloakAdminClient.refused("sending the staff setup email", status);
		}
	}

	private void call(Runnable keycloakCalls) {
		try {
			keycloakCalls.run();
		} catch (RestClientException e) {
			count("failed");
			throw new IllegalStateException("Keycloak could not be reached for a staff invitation", e);
		} catch (IllegalStateException e) {
			count("failed");
			throw e;
		}
	}

	private void count(String outcome) {
		Counter.builder("identity.staff_invitations")
				.description("Keycloak calls for staff invitations, by outcome")
				.tag("outcome", outcome)
				.register(metrics)
				.increment();
	}

	private static String id(Map<String, Object> user) {
		return String.valueOf(user.get("id"));
	}

	private static @Nullable String invitationOf(Map<String, Object> user) {
		if (user.get("attributes") instanceof Map<?, ?> attributes
				&& attributes.get(INVITATION_ATTRIBUTE) instanceof List<?> values && !values.isEmpty()) {
			return String.valueOf(values.getFirst());
		}
		return null;
	}
}
