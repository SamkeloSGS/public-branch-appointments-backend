package com.samdev.appointments.booking.infrastructure.security;

import java.util.List;

import org.springframework.stereotype.Component;

import com.samdev.appointments.identity.PublicEndpoints;

/**
 * Availability is public: visitors see open times before signing in.
 */
@Component
class BookingPublicEndpoints implements PublicEndpoints {

	@Override
	public List<Endpoint> publicEndpoints() {
		return List.of(Endpoint.get("/api/v1/availability"), Endpoint.get("/api/v1/availability/days"));
	}
}
