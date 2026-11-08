package com.samdev.appointments.notification.infrastructure.security;

import java.util.List;

import org.springframework.stereotype.Component;

import com.samdev.appointments.identity.PublicEndpoints;

/**
 * Provider delivery callbacks carry no token; they are verified by the provider's signature instead.
 */
@Component
class NotificationPublicEndpoints implements PublicEndpoints {

	@Override
	public List<Endpoint> publicEndpoints() {
		return List.of(new Endpoint("POST", "/api/v1/webhooks/notifications/*"));
	}
}
