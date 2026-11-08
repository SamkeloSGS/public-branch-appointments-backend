package com.samdev.appointments.booking.web.controller;

import static org.springframework.http.HttpHeaders.IF_MATCH;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.application.command.BookAppointment;
import com.samdev.appointments.booking.application.command.CancelAppointment;
import com.samdev.appointments.booking.application.command.CheckInAppointment;
import com.samdev.appointments.booking.application.command.CompleteAppointment;
import com.samdev.appointments.booking.application.command.DisplaceAppointment;
import com.samdev.appointments.booking.application.command.MarkNoShow;
import com.samdev.appointments.booking.application.command.RescheduleAppointment;
import com.samdev.appointments.booking.application.exception.AppointmentNotFoundException;
import com.samdev.appointments.booking.application.handler.AppointmentCommandHandler;
import com.samdev.appointments.booking.application.handler.AppointmentStatusHandler;
import com.samdev.appointments.booking.application.query.AppointmentQueries;
import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.booking.web.dto.request.RescheduleRequest;
import com.samdev.appointments.booking.web.dto.request.StaffBookingRequest;
import com.samdev.appointments.booking.web.dto.response.AppointmentResponse;
import com.samdev.appointments.booking.web.dto.response.DayListEntryResponse;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

/**
 * Branch staff work with the branch's appointments.
 */
@Tag(name = "Appointments (admin)",
		description = "Day lists, booking for customers, and the appointment lifecycle.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_STAFF)
@RestController
class AdminAppointmentsController {

	private final AppointmentCommandHandler commands;
	private final AppointmentStatusHandler statuses;
	private final AppointmentQueries queries;

	AdminAppointmentsController(AppointmentCommandHandler commands, AppointmentStatusHandler statuses,
			AppointmentQueries queries) {

		this.commands = commands;
		this.statuses = statuses;
		this.queries = queries;
	}

	@GetMapping(path = "/api/v1/admin/appointments", params = "date")
	@Operation(summary = "Day list: the branch's appointments on a date, by start, with customer names")
	List<DayListEntryResponse> dayList(@RequestParam LocalDate date,
			@RequestParam(required = false) @Nullable AppointmentStatus status) {
		return queries.dayList(date, status).stream().map(DayListEntryResponse::of).toList();
	}

	@PostMapping("/api/v1/admin/appointments")
	@Operation(summary = "Book for a customer; no minimum lead time or appointment limit (Idempotency-Key optional)")
	ResponseEntity<AppointmentResponse> book(@RequestBody @Valid StaffBookingRequest request,
			@RequestHeader(name = "Idempotency-Key", required = false) @Nullable String idempotencyKey) {

		var result = commands.handle(new BookAppointment(new CustomerId(request.customerId()),
				new ServiceId(request.serviceId()), request.start(), Actor.STAFF, idempotencyKey));
		var appointment = AppointmentResponse.of(queries.get(result.appointmentId()));

		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.location(URI.create("/api/v1/admin/appointments/" + appointment.id()))
				.eTag(ETags.of(appointment.version()))
				.body(appointment);
	}

	@GetMapping(path = "/api/v1/admin/appointments", params = "reference")
	@Operation(summary = "Find by booking reference")
	ResponseEntity<AppointmentResponse> byReference(@RequestParam String reference) {

		var appointment = queries.byReference(reference).orElseThrow(AppointmentNotFoundException::byReference);

		return current(appointment.getId());
	}

	@GetMapping("/api/v1/admin/appointments/{id}")
	@Operation(summary = "One appointment with its ETag")
	ResponseEntity<AppointmentResponse> appointment(@PathVariable UUID id) {
		return current(new AppointmentId(id));
	}

	@PostMapping("/api/v1/admin/appointments/{id}/reschedule")
	@Operation(summary = "Move to another open time (If-Match); staff are not bound by the cutoff")
	ResponseEntity<AppointmentResponse> reschedule(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch,
			@RequestBody @Valid RescheduleRequest request) {
		return act(id, ifMatch, version -> commands.handle(
				new RescheduleAppointment(new AppointmentId(id), version, request.start(), Actor.STAFF)));
	}

	@PostMapping("/api/v1/admin/appointments/{id}/cancel")
	@Operation(summary = "Cancel (If-Match)")
	ResponseEntity<AppointmentResponse> cancel(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {
		return act(id, ifMatch,
				version -> commands.handle(new CancelAppointment(new AppointmentId(id), version, Actor.STAFF, null)));
	}

	@PostMapping("/api/v1/admin/appointments/{id}/check-in")
	@Operation(summary = "Check in on the appointment date (If-Match)")
	ResponseEntity<AppointmentResponse> checkIn(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {
		return act(id, ifMatch, version -> statuses.handle(new CheckInAppointment(new AppointmentId(id), version)));
	}

	@PostMapping("/api/v1/admin/appointments/{id}/complete")
	@Operation(summary = "Complete a checked-in appointment (If-Match)")
	ResponseEntity<AppointmentResponse> complete(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {
		return act(id, ifMatch, version -> statuses.handle(new CompleteAppointment(new AppointmentId(id), version)));
	}

	@PostMapping("/api/v1/admin/appointments/{id}/no-show")
	@Operation(summary = "Mark a no-show once the start plus the grace period has passed (If-Match)")
	ResponseEntity<AppointmentResponse> noShow(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {
		return act(id, ifMatch,
				version -> statuses.handle(new MarkNoShow(new AppointmentId(id), version, Actor.STAFF)));
	}

	@PostMapping("/api/v1/admin/appointments/{id}/displace")
	@RequiresRole(StaffRole.BRANCH_MANAGER)
	@Operation(summary = "Branch manager: choose this appointment to move out of an overbooked interval (If-Match)")
	ResponseEntity<AppointmentResponse> displace(@PathVariable UUID id,
			@RequestHeader(name = IF_MATCH, required = false) @Nullable String ifMatch) {
		return act(id, ifMatch, version -> commands.handle(new DisplaceAppointment(new AppointmentId(id), version)));
	}

	private ResponseEntity<AppointmentResponse> act(UUID id, @Nullable String ifMatch, Consumer<Long> action) {

		action.accept(ETags.expectedVersion(ifMatch));

		return current(new AppointmentId(id));
	}

	private ResponseEntity<AppointmentResponse> current(AppointmentId id) {

		var appointment = AppointmentResponse.of(queries.get(id));

		return ResponseEntity.ok().eTag(ETags.of(appointment.version())).body(appointment);
	}
}
