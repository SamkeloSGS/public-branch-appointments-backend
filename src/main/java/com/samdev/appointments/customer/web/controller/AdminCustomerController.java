package com.samdev.appointments.customer.web.controller;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.application.command.DeactivateCustomer;
import com.samdev.appointments.customer.application.command.EraseCustomer;
import com.samdev.appointments.customer.application.command.ReactivateCustomer;
import com.samdev.appointments.customer.application.command.RegisterCustomerByStaff;
import com.samdev.appointments.customer.application.command.UpdateContactDetails;
import com.samdev.appointments.customer.application.handler.CustomerCommandHandler;
import com.samdev.appointments.customer.application.query.CustomerQueries;
import com.samdev.appointments.customer.web.dto.request.ContactDetailsRequest;
import com.samdev.appointments.customer.web.dto.request.CustomerRequest;
import com.samdev.appointments.customer.web.dto.response.CustomerResponse;
import com.samdev.appointments.customer.web.dto.response.CustomerSummaryResponse;
import com.samdev.appointments.identity.AccessControl;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.Subject;
import com.samdev.appointments.shared.PageResponse;

/**
 * Staff access to customers. Any staff member may find any customer; every search, view and change is
 * written to the access audit, and the request fails if the audit row cannot be written.
 */
@Tag(name = "Customers (admin)",
		description = "Branch staff search, view, create and correct customers; the branch manager changes status and erases. "
				+ "Every call is audited.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_STAFF)
@RestController
@RequestMapping("/api/v1/admin/customers")
class AdminCustomerController {

	private final CustomerCommandHandler commands;
	private final CustomerQueries queries;
	private final AccessControl access;

	AdminCustomerController(CustomerCommandHandler commands, CustomerQueries queries, AccessControl access) {
		this.commands = commands;
		this.queries = queries;
		this.access = access;
	}

	@GetMapping
	@Operation(summary = "Search by name prefix, exact email or exact phone (at least 3 characters), one page at a time")
	PageResponse<CustomerSummaryResponse> search(@RequestParam String query, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {
		return PageResponse.of(queries.search(staff(), query, PageResponse.request(page, size)),
				CustomerSummaryResponse::of);
	}

	@GetMapping("/{id}")
	@Operation(summary = "View one customer with its ETag")
	ResponseEntity<CustomerResponse> view(@PathVariable UUID id) {

		var customer = CustomerResponse.of(queries.view(staff(), new CustomerId(id)));

		return ResponseEntity.ok().eTag(ETags.of(customer.version())).body(customer);
	}

	@PostMapping
	@Operation(summary = "Create a customer without a login account, for a walk-in or phone booking")
	ResponseEntity<CustomerResponse> create(@RequestBody @Valid CustomerRequest request) {

		var id = commands.handle(new RegisterCustomerByStaff(staff(), request.givenName(), request.familyName(),
				request.email(), request.phone(), request.language()));
		var customer = CustomerResponse.of(queries.get(id));

		return ResponseEntity.created(URI.create("/api/v1/admin/customers/" + id)).eTag(ETags.of(customer.version()))
				.body(customer);
	}

	@PutMapping("/{id}")
	@Operation(summary = "Correct name and contact details (If-Match)")
	ResponseEntity<CustomerResponse> update(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid ContactDetailsRequest request) {

		var customerId = new CustomerId(id);
		commands.handle(new UpdateContactDetails(staff(), customerId, ETags.expectedVersion(ifMatch),
				request.givenName(), request.familyName(), request.email(), request.phone()));

		return current(customerId);
	}

	@PostMapping("/{id}/deactivate")
	@RequiresRole(StaffRole.BRANCH_MANAGER)
	@Operation(summary = "Deactivate: no new bookings, data kept (If-Match)")
	ResponseEntity<CustomerResponse> deactivate(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var customerId = new CustomerId(id);
		commands.handle(new DeactivateCustomer(staff(), customerId, ETags.expectedVersion(ifMatch)));

		return current(customerId);
	}

	@PostMapping("/{id}/reactivate")
	@RequiresRole(StaffRole.BRANCH_MANAGER)
	@Operation(summary = "Reactivate a deactivated customer (If-Match)")
	ResponseEntity<CustomerResponse> reactivate(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var customerId = new CustomerId(id);
		commands.handle(new ReactivateCustomer(staff(), customerId, ETags.expectedVersion(ifMatch)));

		return current(customerId);
	}

	@DeleteMapping("/{id}")
	@RequiresRole(StaffRole.BRANCH_MANAGER)
	@Operation(summary = "Erase a customer's personal data for good; the ID stays valid (If-Match)")
	ResponseEntity<Void> erase(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		commands.handle(new EraseCustomer(staff(), new CustomerId(id), ETags.expectedVersion(ifMatch)));

		return ResponseEntity.noContent().build();
	}

	private Subject staff() {
		return access.currentUser().subject();
	}

	/**
	 * The state after a command, which the command itself audited.
	 */
	private ResponseEntity<CustomerResponse> current(CustomerId id) {

		var customer = CustomerResponse.of(queries.get(id));

		return ResponseEntity.ok().eTag(ETags.of(customer.version())).body(customer);
	}
}
