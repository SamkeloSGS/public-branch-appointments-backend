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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.application.command.DefineService;
import com.samdev.appointments.branchmanagement.application.command.MoveServiceToCategory;
import com.samdev.appointments.branchmanagement.application.command.RetireService;
import com.samdev.appointments.branchmanagement.application.command.UpdateServiceDetails;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCatalogCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.web.dto.request.ChangeServiceCategoryRequest;
import com.samdev.appointments.branchmanagement.web.dto.request.DefineServiceRequest;
import com.samdev.appointments.branchmanagement.web.dto.request.UpdateServiceRequest;
import com.samdev.appointments.branchmanagement.web.dto.response.ServiceResponse;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

@Tag(name = "Service catalog (admin)", description = "The catalog of appointment types.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_MANAGER)
@RestController
@RequestMapping("/api/v1/admin/services")
class AdminServiceController {

	private final ServiceCatalogCommandHandler commands;
	private final BranchQueries queries;

	AdminServiceController(ServiceCatalogCommandHandler commands, BranchQueries queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@GetMapping
	@Operation(summary = "List all services, active and retired")
	List<ServiceResponse> services() {
		return queries.allServices().stream().map(ServiceResponse::of).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a service with its ETag")
	ResponseEntity<ServiceResponse> service(@PathVariable UUID id) {
		return current(new ServiceId(id));
	}

	@PostMapping
	@Operation(summary = "Define a service in an active category; it takes the category's customer segment")
	ResponseEntity<ServiceResponse> define(@RequestBody @Valid DefineServiceRequest request) {
		var id = commands.handle(new DefineService(request.name(), new ServiceCategoryId(request.categoryId()),
				request.description(), Duration.ofMinutes(request.defaultDurationMinutes()),
				request.prerequisiteList()));

		var service = ServiceResponse.of(queries.service(id));

		return ResponseEntity.created(URI.create("/api/v1/admin/services/" + id))
				.eTag(ETags.of(service.version()))
				.body(service);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Replace a service's description and prerequisites (If-Match)")
	ResponseEntity<ServiceResponse> updateDetails(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid UpdateServiceRequest request) {

		var serviceId = new ServiceId(id);
		commands.handle(new UpdateServiceDetails(serviceId, ETags.expectedVersion(ifMatch), request.description(),
				request.prerequisiteList()));

		return current(serviceId);
	}

	@PutMapping("/{id}/category")
	@Operation(summary = "Move a service to another active category of the same customer segment (If-Match)")
	ResponseEntity<ServiceResponse> changeCategory(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid ChangeServiceCategoryRequest request) {

		var serviceId = new ServiceId(id);
		commands.handle(new MoveServiceToCategory(serviceId, ETags.expectedVersion(ifMatch),
				new ServiceCategoryId(request.categoryId())));

		return current(serviceId);
	}

	@PostMapping("/{id}/retire")
	@Operation(summary = "Retire a service; it cannot gain new offerings (If-Match)")
	ResponseEntity<ServiceResponse> retire(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var serviceId = new ServiceId(id);
		commands.handle(new RetireService(serviceId, ETags.expectedVersion(ifMatch)));

		return current(serviceId);
	}

	private ResponseEntity<ServiceResponse> current(ServiceId id) {

		var service = ServiceResponse.of(queries.service(id));

		return ResponseEntity.ok().eTag(ETags.of(service.version())).body(service);
	}
}
