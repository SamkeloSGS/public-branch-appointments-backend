package com.samdev.appointments.booking.web.controller;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import java.net.URI;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.application.command.BookAppointment;
import com.samdev.appointments.booking.application.command.CancelAppointment;
import com.samdev.appointments.booking.application.command.RescheduleAppointment;
import com.samdev.appointments.booking.application.exception.AppointmentNotFoundException;
import com.samdev.appointments.booking.application.handler.AppointmentCommandHandler;
import com.samdev.appointments.booking.application.query.AppointmentQueries;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.booking.web.dto.request.BookingRequest;
import com.samdev.appointments.booking.web.dto.request.RescheduleRequest;
import com.samdev.appointments.booking.web.dto.response.AppointmentResponse;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.IdentitySubject;
import com.samdev.appointments.identity.AccessControl;
import com.samdev.appointments.shared.PageResponse;

/**
 * Customers book and manage their own appointments, found by booking reference. A reference that is not the
 * caller's own is not found.
 */
@Tag(name = "My appointments", description = "The signed-in customer's own appointments.")
@SecurityRequirement(name = "keycloak")
@RestController
@RequestMapping("/api/v1/appointments")
class MyAppointmentsController {

	private final AppointmentCommandHandler commands;
	private final AppointmentQueries queries;
	private final CustomerDirectory customers;
	private final AccessControl access;

	MyAppointmentsController(AppointmentCommandHandler commands, AppointmentQueries queries,
			CustomerDirectory customers, AccessControl access) {

		this.commands = commands;
		this.queries = queries;
		this.customers = customers;
		this.access = access;
	}

	@PostMapping
	@Operation(summary = "Book an open time; a repeat with the same Idempotency-Key returns the first appointment")
	ResponseEntity<AppointmentResponse> book(@RequestBody @Valid BookingRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) @Nullable String idempotencyKey) {

		var customer = customer().orElseThrow(() -> new BookingRejected(RejectionCode.PROFILE_REQUIRED,
				"Create your customer profile before booking"));

		var result = commands.handle(new BookAppointment(customer,
				new ServiceId(request.serviceId()), request.start(), Actor.CUSTOMER, idempotencyKey));
		var appointment = AppointmentResponse.of(queries.get(result.appointmentId()));

		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.location(URI.create("/api/v1/appointments/mine/" + appointment.reference()))
				.eTag(ETags.of(appointment.version()))
				.body(appointment);
	}

	@GetMapping("/mine")
	@Operation(summary = "Own appointments, one page at a time: upcoming (not ended yet, soonest first) or past "
			+ "(latest first)")
	PageResponse<AppointmentResponse> mine(@RequestParam(defaultValue = "upcoming") Filter filter,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {

		var request = PageResponse.request(page, size);

		return PageResponse.of(customer()
				.map(it -> queries.mine(it, filter == Filter.upcoming, request))
				.orElseGet(() -> Page.empty(request)), AppointmentResponse::of);
	}

	@GetMapping("/mine/{reference}")
	@Operation(summary = "One own appointment with its ETag")
	ResponseEntity<AppointmentResponse> appointment(@PathVariable String reference) {
		return current(reference);
	}

	@PostMapping("/mine/{reference}/reschedule")
	@Operation(summary = "Move to another open time at the same branch (If-Match); not within the cutoff")
	ResponseEntity<AppointmentResponse> reschedule(@PathVariable String reference,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid RescheduleRequest request) {

		var id = own(reference);
		commands.handle(new RescheduleAppointment(id, ETags.expectedVersion(ifMatch), request.start(), Actor.CUSTOMER));

		return current(reference);
	}

	@PostMapping("/mine/{reference}/cancel")
	@Operation(summary = "Cancel (If-Match); not within the cutoff unless the appointment needs rebooking")
	ResponseEntity<AppointmentResponse> cancel(@PathVariable String reference,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {

		var id = own(reference);
		commands.handle(new CancelAppointment(id, ETags.expectedVersion(ifMatch), Actor.CUSTOMER, null));

		return current(reference);
	}

	enum Filter {
		upcoming, past
	}

	private Optional<CustomerId> customer() {
		return customers.findByIdentity(new IdentitySubject(access.currentUser().subject().value()));
	}

	private AppointmentId own(String reference) {
		var customer = customer().orElseThrow(AppointmentNotFoundException::byReference);
		return queries.mine(customer, reference).getId();
	}

	private ResponseEntity<AppointmentResponse> current(String reference) {

		var customer = customer().orElseThrow(AppointmentNotFoundException::byReference);
		var appointment = AppointmentResponse.of(queries.mine(customer, reference));

		return ResponseEntity.ok().eTag(ETags.of(appointment.version())).body(appointment);
	}
}
