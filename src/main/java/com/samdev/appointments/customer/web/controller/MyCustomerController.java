package com.samdev.appointments.customer.web.controller;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import java.net.URI;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.customer.IdentitySubject;
import com.samdev.appointments.customer.application.command.ChangeContactPreferences;
import com.samdev.appointments.customer.application.command.EraseCustomer;
import com.samdev.appointments.customer.application.command.RegisterCustomer;
import com.samdev.appointments.customer.application.command.UpdateContactDetails;
import com.samdev.appointments.customer.application.handler.CustomerCommandHandler;
import com.samdev.appointments.customer.application.query.CustomerQueries;
import com.samdev.appointments.customer.web.dto.request.ContactDetailsRequest;
import com.samdev.appointments.customer.web.dto.request.ContactPreferencesRequest;
import com.samdev.appointments.customer.web.dto.request.CustomerRequest;
import com.samdev.appointments.customer.web.dto.response.CustomerResponse;
import com.samdev.appointments.identity.AccessControl;

/**
 * Self-service. The customer is always the one linked to the caller's sign-in; no endpoint accepts a customer ID.
 * Nothing here is written to the access audit.
 */
@Tag(name = "My customer profile",
		description = "The signed-in person's own profile, contact details and contact preferences.")
@SecurityRequirement(name = "keycloak")
@RestController
@RequestMapping("/api/v1/customers/me")
class MyCustomerController {

	private final CustomerCommandHandler commands;
	private final CustomerQueries queries;
	private final AccessControl access;

	MyCustomerController(CustomerCommandHandler commands, CustomerQueries queries, AccessControl access) {
		this.commands = commands;
		this.queries = queries;
		this.access = access;
	}

	@PostMapping
	@Operation(summary = "Create own profile; links a branch-created profile with the same verified email instead")
	ResponseEntity<CustomerResponse> register(@RequestBody @Valid CustomerRequest request) {

		var user = access.currentUser();
		commands.handle(new RegisterCustomer(subject(), user.email(), request.givenName(), request.familyName(),
				request.email(), request.phone(), request.language()));

		var customer = CustomerResponse.of(queries.mine(subject()));

		return ResponseEntity.created(URI.create("/api/v1/customers/me")).eTag(ETags.of(customer.version()))
				.body(customer);
	}

	@GetMapping
	@Operation(summary = "Own profile with its ETag")
	ResponseEntity<CustomerResponse> profile() {
		return current();
	}

	@PutMapping
	@Operation(summary = "Update own name and contact details; an empty email or phone removes it (If-Match)")
	ResponseEntity<CustomerResponse> update(@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid ContactDetailsRequest request) {

		var id = queries.mine(subject()).getId();
		commands.handle(new UpdateContactDetails(null, id, ETags.expectedVersion(ifMatch), request.givenName(),
				request.familyName(), request.email(), request.phone()));

		return current();
	}

	@PutMapping("/contact-preferences")
	@Operation(summary = "Change enabled channels and language (If-Match)")
	ResponseEntity<CustomerResponse> changePreferences(
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid ContactPreferencesRequest request) {

		var id = queries.mine(subject()).getId();
		commands.handle(new ChangeContactPreferences(id, ETags.expectedVersion(ifMatch), request.emailEnabled(),
				request.smsEnabled(), request.language()));

		return current();
	}

	@DeleteMapping
	@Operation(summary = "Erase own personal data for good; future appointments are cancelled (If-Match)")
	ResponseEntity<Void> erase(@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var id = queries.mine(subject()).getId();
		commands.handle(new EraseCustomer(null, id, ETags.expectedVersion(ifMatch)));

		return ResponseEntity.noContent().build();
	}

	private IdentitySubject subject() {
		return new IdentitySubject(access.currentUser().subject().value());
	}

	private ResponseEntity<CustomerResponse> current() {

		var customer = CustomerResponse.of(queries.mine(subject()));

		return ResponseEntity.ok().eTag(ETags.of(customer.version())).body(customer);
	}
}
