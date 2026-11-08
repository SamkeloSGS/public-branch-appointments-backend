package com.samdev.appointments.branchmanagement.web.controller;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import java.net.URI;
import java.time.Duration;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.application.command.ChangeServiceOffering;
import com.samdev.appointments.branchmanagement.application.command.OfferService;
import com.samdev.appointments.branchmanagement.application.command.WithdrawServiceOffering;
import com.samdev.appointments.branchmanagement.application.handler.ServiceOfferingCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.web.dto.request.ChangeOfferingRequest;
import com.samdev.appointments.branchmanagement.web.dto.request.OfferServiceRequest;
import com.samdev.appointments.branchmanagement.web.dto.response.OfferingResponse;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

@Tag(name = "Service offerings (admin)",
		description = "Services the branch offers, with duration, slot interval and booking window.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_MANAGER)
@RestController
@RequestMapping("/api/v1/admin/offerings")
class AdminOfferingController {

	private final ServiceOfferingCommandHandler commands;
	private final BranchQueries queries;

	AdminOfferingController(ServiceOfferingCommandHandler commands, BranchQueries queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@GetMapping
	@Operation(summary = "List the offerings, including withdrawn ones")
	List<OfferingResponse> offerings() {
		return queries.offerings().stream().map(OfferingResponse::of).toList();
	}

	@GetMapping("/{offeringId}")
	@Operation(summary = "Get an offering with its ETag")
	ResponseEntity<OfferingResponse> offering(@PathVariable UUID offeringId) {
		return current(new ServiceOfferingId(offeringId));
	}

	@PostMapping
	@Operation(summary = "Offer a service at the branch")
	ResponseEntity<OfferingResponse> offer(@RequestBody @Valid OfferServiceRequest request) {
		var offeringId = commands.handle(new OfferService(new ServiceId(request.serviceId()),
				new CapacityPoolId(request.poolId()),
				request.durationMinutes() == null ? null : Duration.ofMinutes(request.durationMinutes()),
				Duration.ofMinutes(request.slotIntervalMinutes()), Duration.ofMinutes(request.minLeadTimeMinutes()),
				request.maxAdvanceDays()));

		var offering = OfferingResponse.of(queries.offering(offeringId));

		return ResponseEntity.created(URI.create("/api/v1/admin/offerings/" + offeringId))
				.eTag(ETags.of(offering.version()))
				.body(offering);
	}

	@PatchMapping("/{offeringId}")
	@Operation(summary = "Change an offering; absent fields keep their value (If-Match)")
	ResponseEntity<OfferingResponse> change(@PathVariable UUID offeringId,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid ChangeOfferingRequest request) {

		var serviceOfferingId = new ServiceOfferingId(offeringId);
		var expectedVersion = ETags.expectedVersion(ifMatch);
		var current = queries.offering(serviceOfferingId);
		var window = current.getBookingWindow();

		commands.handle(new ChangeServiceOffering(serviceOfferingId, expectedVersion,
				request.poolId() == null ? current.getCapacityPoolId() : new CapacityPoolId(request.poolId()),
				request.durationMinutes() == null ? current.getDuration() : Duration.ofMinutes(request.durationMinutes()),
				request.slotIntervalMinutes() == null ? current.getSlotInterval()
						: Duration.ofMinutes(request.slotIntervalMinutes()),
				Duration.ofMinutes(request.minLeadTimeMinutes() == null ? window.minLeadTimeMinutes()
						: request.minLeadTimeMinutes()),
				request.maxAdvanceDays() == null ? window.maxAdvanceDays() : request.maxAdvanceDays()));

		return current(serviceOfferingId);
	}

	/**
	 * Withdraws the offering. It is kept (inactive) rather than deleted.
	 */
	@DeleteMapping("/{offeringId}")
	@Operation(summary = "Withdraw an offering; it is kept as inactive (If-Match)")
	ResponseEntity<OfferingResponse> withdraw(@PathVariable UUID offeringId,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var serviceOfferingId = new ServiceOfferingId(offeringId);

		commands.handle(new WithdrawServiceOffering(serviceOfferingId, ETags.expectedVersion(ifMatch)));

		return current(serviceOfferingId);
	}

	private ResponseEntity<OfferingResponse> current(ServiceOfferingId id) {

		var offering = OfferingResponse.of(queries.offering(id));

		return ResponseEntity.ok().eTag(ETags.of(offering.version())).body(offering);
	}
}
