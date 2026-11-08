package com.samdev.appointments.branchmanagement.web.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.branchmanagement.CalendarExceptionId;
import com.samdev.appointments.branchmanagement.CapacityOverrideId;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.application.command.AddCalendarException;
import com.samdev.appointments.branchmanagement.application.command.RemoveCalendarException;
import com.samdev.appointments.branchmanagement.application.command.RemoveCapacityOverride;
import com.samdev.appointments.branchmanagement.application.command.SetCapacityOverride;
import com.samdev.appointments.branchmanagement.application.handler.CalendarCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.web.dto.TimeRangeDto;
import com.samdev.appointments.branchmanagement.web.dto.request.AddExceptionRequest;
import com.samdev.appointments.branchmanagement.web.dto.request.SetOverrideRequest;
import com.samdev.appointments.branchmanagement.web.dto.response.ExceptionResponse;
import com.samdev.appointments.branchmanagement.web.dto.response.OverrideResponse;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

/**
 * Calendar exceptions and capacity overrides. Both are created and removed, never updated, so no {@code If-Match}
 * is needed. Neither checks existing appointments; the impact preview is a Booking endpoint.
 */
@Tag(name = "Calendar (admin)",
		description = "Calendar exceptions and capacity overrides of the branch. Neither checks existing appointments.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_MANAGER)
@RestController
@RequestMapping("/api/v1/admin")
class AdminCalendarController {

	private final CalendarCommandHandler commands;
	private final BranchQueries queries;

	AdminCalendarController(CalendarCommandHandler commands, BranchQueries queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@GetMapping("/calendar-exceptions")
	@Operation(summary = "List calendar exceptions from today onwards")
	List<ExceptionResponse> exceptions() {
		return queries.upcomingExceptions().stream().map(ExceptionResponse::of).toList();
	}

	@PostMapping("/calendar-exceptions")
	@Operation(summary = "Add a CLOSED or SPECIAL_HOURS exception for today or later")
	ResponseEntity<ExceptionResponse> addException(@RequestBody @Valid AddExceptionRequest request) {

		var exceptionId = commands.handle(new AddCalendarException(request.date(), request.type(),
				TimeRangeDto.toTimeRanges(request.hours()), request.reason()));

		var exception = queries.upcomingExceptions().stream()
				.filter(it -> it.getId().equals(exceptionId))
				.map(ExceptionResponse::of)
				.findFirst()
				.orElseThrow();

		return ResponseEntity.created(URI.create("/api/v1/admin/calendar-exceptions/" + exceptionId))
				.eTag(ETags.of(exception.version()))
				.body(exception);
	}

	@DeleteMapping("/calendar-exceptions/{exceptionId}")
	@Operation(summary = "Remove a calendar exception")
	ResponseEntity<Void> removeException(@PathVariable UUID exceptionId) {
		commands.handle(new RemoveCalendarException(new CalendarExceptionId(exceptionId)));

		return ResponseEntity.noContent().build();
	}

	@GetMapping("/capacity-overrides")
	@Operation(summary = "List capacity overrides between from and to (inclusive; defaults to today onwards)")
	List<OverrideResponse> overrides(@RequestParam @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @Nullable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

		return queries.overrides(from, to).stream().map(OverrideResponse::of).toList();
	}

	@PostMapping("/capacity-overrides")
	@Operation(summary = "Set a whole-day or windowed capacity override for a pool")
	ResponseEntity<OverrideResponse> setOverride(@RequestBody @Valid SetOverrideRequest request) {

		var overrideId = commands.handle(new SetCapacityOverride(new CapacityPoolId(request.poolId()),
				request.date(), request.window() == null ? null : request.window().toTimeRange(), request.capacity(),
				request.reason(), request.note()));

		var override = queries.overrides(request.date(), request.date()).stream()
				.filter(it -> it.getId().equals(overrideId))
				.map(OverrideResponse::of)
				.findFirst()
				.orElseThrow();

		return ResponseEntity.created(URI.create("/api/v1/admin/capacity-overrides/" + overrideId))
				.eTag(ETags.of(override.version()))
				.body(override);
	}

	@DeleteMapping("/capacity-overrides/{overrideId}")
	@Operation(summary = "Remove a capacity override")
	ResponseEntity<Void> removeOverride(@PathVariable UUID overrideId) {
		commands.handle(new RemoveCapacityOverride(new CapacityOverrideId(overrideId)));

		return ResponseEntity.noContent().build();
	}
}
