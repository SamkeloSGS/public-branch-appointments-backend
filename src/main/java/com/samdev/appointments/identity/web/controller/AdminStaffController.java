package com.samdev.appointments.identity.web.controller;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.identity.AccessControl;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.application.command.ChangeRole;
import com.samdev.appointments.identity.application.command.InviteStaffMember;
import com.samdev.appointments.identity.application.command.ReinstateStaffMember;
import com.samdev.appointments.identity.application.command.ReinviteStaffMember;
import com.samdev.appointments.identity.application.command.RemoveStaffMember;
import com.samdev.appointments.identity.application.command.SuspendStaffMember;
import com.samdev.appointments.identity.application.handler.StaffCommandHandler;
import com.samdev.appointments.identity.application.query.StaffQueries;
import com.samdev.appointments.identity.domain.vo.StaffStatus;
import com.samdev.appointments.identity.infrastructure.config.IdentityProperties;
import com.samdev.appointments.identity.web.dto.request.ChangeRoleRequest;
import com.samdev.appointments.identity.web.dto.request.InviteStaffRequest;
import com.samdev.appointments.identity.web.dto.response.ChangeLogEntryResponse;
import com.samdev.appointments.identity.web.dto.response.InvitationResponse;
import com.samdev.appointments.identity.web.dto.response.StaffMemberResponse;

@Tag(name = "Staff (admin)",
		description = "The branch manager manages staff and their roles. Sign-in itself is Keycloak's job.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_MANAGER)
@RestController
@RequestMapping("/api/v1/admin/staff")
class AdminStaffController {

	private final StaffCommandHandler commands;
	private final StaffQueries queries;
	private final AccessControl access;
	private final IdentityProperties properties;

	AdminStaffController(StaffCommandHandler commands, StaffQueries queries, AccessControl access,
			IdentityProperties properties) {

		this.commands = commands;
		this.queries = queries;
		this.access = access;
		this.properties = properties;
	}

	@GetMapping
	@Operation(summary = "List staff members, optionally by role and status")
	List<StaffMemberResponse> staff(@RequestParam @Nullable StaffRole role, @RequestParam @Nullable StaffStatus status) {
		return queries.list(role, status).stream().map(StaffMemberResponse::of).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a staff member with its ETag")
	ResponseEntity<StaffMemberResponse> member(@PathVariable UUID id) {
		return current(new StaffMemberId(id));
	}

	@PostMapping
	@Operation(summary = "Invite a staff member; the response contains the sign-up address to pass on")
	ResponseEntity<InvitationResponse> invite(@RequestBody @Valid InviteStaffRequest request) {

		var actor = actor();
		var id = commands.handle(new InviteStaffMember(actor, request.email(), request.displayName(), request.role()));
		var member = StaffMemberResponse.of(queries.get(id));

		return ResponseEntity.created(URI.create("/api/v1/admin/staff/" + id))
				.eTag(ETags.of(member.version()))
				.body(new InvitationResponse(member, properties.signUpAddress()));
	}

	@PutMapping("/{id}/role")
	@Operation(summary = "Change the role (If-Match)")
	ResponseEntity<StaffMemberResponse> changeRole(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid ChangeRoleRequest request) {

		var actor = actor();
		var staffMemberId = new StaffMemberId(id);
		commands.handle(new ChangeRole(actor, staffMemberId, ETags.expectedVersion(ifMatch), request.role()));

		return current(staffMemberId);
	}

	@PostMapping("/{id}/reinvite")
	@Operation(summary = "Restart the 14-day invitation window (If-Match)")
	ResponseEntity<StaffMemberResponse> reinvite(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var actor = actor();
		var staffMemberId = new StaffMemberId(id);
		commands.handle(new ReinviteStaffMember(actor, staffMemberId, ETags.expectedVersion(ifMatch)));

		return current(staffMemberId);
	}

	@PostMapping("/{id}/suspend")
	@Operation(summary = "Suspend staff access; the person keeps their customer bookings (If-Match)")
	ResponseEntity<StaffMemberResponse> suspend(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var actor = actor();
		var staffMemberId = new StaffMemberId(id);
		commands.handle(new SuspendStaffMember(actor, staffMemberId, ETags.expectedVersion(ifMatch)));

		return current(staffMemberId);
	}

	@PostMapping("/{id}/reinstate")
	@Operation(summary = "Restore access with the same role (If-Match)")
	ResponseEntity<StaffMemberResponse> reinstate(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var actor = actor();
		var staffMemberId = new StaffMemberId(id);
		commands.handle(new ReinstateStaffMember(actor, staffMemberId, ETags.expectedVersion(ifMatch)));

		return current(staffMemberId);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Remove a staff member; the Keycloak account is left alone (If-Match)")
	ResponseEntity<StaffMemberResponse> remove(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var actor = actor();
		var staffMemberId = new StaffMemberId(id);
		commands.handle(new RemoveStaffMember(actor, staffMemberId, ETags.expectedVersion(ifMatch)));

		return current(staffMemberId);
	}

	@GetMapping("/{id}/changes")
	@Operation(summary = "Change log of one staff member, oldest first")
	List<ChangeLogEntryResponse> changes(@PathVariable UUID id) {
		return queries.changes(new StaffMemberId(id)).stream().map(ChangeLogEntryResponse::of).toList();
	}

	/**
	 * The calling staff member, recorded as the actor of commands.
	 */
	private StaffMemberId actor() {
		return access.currentUser().staffMemberId();
	}

	private ResponseEntity<StaffMemberResponse> current(StaffMemberId id) {

		var member = StaffMemberResponse.of(queries.get(id));

		return ResponseEntity.ok().eTag(ETags.of(member.version())).body(member);
	}
}
