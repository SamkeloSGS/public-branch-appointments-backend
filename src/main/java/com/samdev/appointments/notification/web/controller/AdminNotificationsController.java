package com.samdev.appointments.notification.web.controller;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.notification.application.handler.NotificationCommandHandler;
import com.samdev.appointments.notification.application.query.NotificationQueries;
import com.samdev.appointments.notification.domain.vo.NotificationId;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;
import com.samdev.appointments.notification.web.dto.response.NotificationResponse;
import com.samdev.appointments.shared.PageResponse;

/**
 * Branch staff see what was sent about the branch's appointments, and send a message again.
 */
@Tag(name = "Notifications (admin)",
		description = "Messages sent to customers about their appointments: types, statuses and delivery attempts.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_STAFF)
@RestController
class AdminNotificationsController {

	private static final Duration DEFAULT_PERIOD = Duration.ofDays(30);

	private final NotificationQueries queries;
	private final NotificationCommandHandler commands;

	AdminNotificationsController(NotificationQueries queries, NotificationCommandHandler commands) {
		this.queries = queries;
		this.commands = commands;
	}

	@GetMapping("/api/v1/admin/appointments/{appointmentId}/notifications")
	@Operation(summary = "Every notification for an appointment, with each delivery")
	List<NotificationResponse> forAppointment(@PathVariable UUID appointmentId) {
		return queries.forAppointment(new AppointmentId(appointmentId)).stream().map(NotificationResponse::of).toList();
	}

	@GetMapping("/api/v1/admin/notifications")
	@Operation(summary = "Notifications created in a period (default: the last 30 days), newest first, one page at "
			+ "a time")
	PageResponse<NotificationResponse> recent(@RequestParam(required = false) @Nullable NotificationStatus status,
			@RequestParam(required = false) @Nullable Instant from,
			@RequestParam(required = false) @Nullable Instant to, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) @Min(1) @Max(PageResponse.MAX_SIZE) int size) {

		var until = to == null ? Instant.now() : to;
		var since = from == null ? until.minus(DEFAULT_PERIOD) : from;

		return PageResponse.of(queries.recent(status, since, until, PageResponse.request(page, size)),
				NotificationResponse::of);
	}

	@PostMapping("/api/v1/admin/notifications/{id}/resend")
	@Operation(summary = "Send a sent, failed or skipped message again, due now; it is checked against the "
			+ "appointment again")
	ResponseEntity<NotificationResponse> resend(@PathVariable UUID id) {

		var copy = commands.resend(new NotificationId(id));

		return ResponseEntity.created(URI.create("/api/v1/admin/notifications/" + copy))
				.body(NotificationResponse.of(queries.get(copy)));
	}
}
