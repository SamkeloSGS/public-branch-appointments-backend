package com.samdev.appointments.branchmanagement.infrastructure.security;

import static com.samdev.appointments.identity.PublicEndpoints.Endpoint.get;

import java.util.List;

import org.springframework.stereotype.Component;

import com.samdev.appointments.identity.PublicEndpoints;

/**
 * The customer-facing endpoints of this module, served without a token.
 */
@Component
class BranchManagementPublicEndpoints implements PublicEndpoints {

	@Override
	public List<Endpoint> publicEndpoints() {
		return List.of(get("/api/v1/branch"), get("/api/v1/services"), get("/api/v1/service-categories"));
	}
}
