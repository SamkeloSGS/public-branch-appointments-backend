package com.samdev.appointments.identity.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.identity.AccessControl;
import com.samdev.appointments.identity.application.query.StaffQueries;
import com.samdev.appointments.identity.web.dto.response.MeResponse;

@Tag(name = "Me", description = "The signed-in caller's own access.")
@SecurityRequirement(name = "keycloak")
@RestController
class MeController {

	private final AccessControl access;
	private final StaffQueries queries;

	MeController(AccessControl access, StaffQueries queries) {
		this.access = access;
		this.queries = queries;
	}

	@GetMapping("/api/v1/me")
	@Operation(summary = "Own role, branch IDs, display name if staff, and whether the token is at level 2")
	MeResponse me() {

		var user = access.currentUser();
		var displayName = user.staffMemberId() == null ? null : queries.get(user.staffMemberId()).getDisplayName();

		return MeResponse.of(user, displayName);
	}
}
