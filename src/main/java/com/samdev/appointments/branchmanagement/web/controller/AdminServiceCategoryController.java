package com.samdev.appointments.branchmanagement.web.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.application.command.DefineServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.RetireServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.UpdateServiceCategory;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCategoryCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.web.dto.request.DefineServiceCategoryRequest;
import com.samdev.appointments.branchmanagement.web.dto.request.UpdateServiceCategoryRequest;
import com.samdev.appointments.branchmanagement.web.dto.response.ServiceCategoryResponse;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

@Tag(name = "Service categories (admin)",
		description = "Categories of the service catalog, each for personal or business customers.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_MANAGER)
@RestController
@RequestMapping("/api/v1/admin/service-categories")
class AdminServiceCategoryController {

	private final ServiceCategoryCommandHandler commands;
	private final BranchQueries queries;

	AdminServiceCategoryController(ServiceCategoryCommandHandler commands, BranchQueries queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@GetMapping
	@Operation(summary = "List service categories, active and retired, optionally of one customer segment")
	List<ServiceCategoryResponse> categories(@RequestParam @Nullable CustomerSegment segment) {
		return queries.categories(segment, true).stream().map(ServiceCategoryResponse::of).toList();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a service category with its ETag")
	ResponseEntity<ServiceCategoryResponse> category(@PathVariable UUID id) {
		return current(new ServiceCategoryId(id));
	}

	@PostMapping
	@Operation(summary = "Define a service category for personal or business customers")
	ResponseEntity<ServiceCategoryResponse> define(@RequestBody @Valid DefineServiceCategoryRequest request) {
		var id = commands.handle(new DefineServiceCategory(request.name(), request.segment(), request.description()));
		var category = ServiceCategoryResponse.of(queries.category(id));

		return ResponseEntity.created(URI.create("/api/v1/admin/service-categories/" + id))
				.eTag(ETags.of(category.version()))
				.body(category);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Rename a category or change its description; the segment cannot change (If-Match)")
	ResponseEntity<ServiceCategoryResponse> update(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid UpdateServiceCategoryRequest request) {

		var categoryId = new ServiceCategoryId(id);
		commands.handle(new UpdateServiceCategory(categoryId, ETags.expectedVersion(ifMatch), request.name(),
				request.description()));

		return current(categoryId);
	}

	@PostMapping("/{id}/retire")
	@Operation(summary = "Retire a category; it cannot gain services (If-Match)")
	ResponseEntity<ServiceCategoryResponse> retire(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var categoryId = new ServiceCategoryId(id);
		commands.handle(new RetireServiceCategory(categoryId, ETags.expectedVersion(ifMatch)));

		return current(categoryId);
	}

	private ResponseEntity<ServiceCategoryResponse> current(ServiceCategoryId id) {

		var category = ServiceCategoryResponse.of(queries.category(id));

		return ResponseEntity.ok().eTag(ETags.of(category.version())).body(category);
	}
}
