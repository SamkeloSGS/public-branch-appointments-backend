package com.samdev.appointments.booking.web.controller;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.booking.application.query.AppointmentQueries;
import com.samdev.appointments.booking.application.query.ImpactPreview;
import com.samdev.appointments.booking.web.dto.request.ImpactPreviewRequest;
import com.samdev.appointments.booking.web.dto.response.ImpactResponse;
import com.samdev.appointments.booking.web.dto.response.OverbookedDayResponse;
import com.samdev.appointments.identity.RequiresRole;
import com.samdev.appointments.identity.StaffRole;

/**
 * The branch manager sees what capacity changes do to existing bookings, before and after making them.
 */
@Tag(name = "Schedule (admin)",
		description = "Impact preview of closures, hours and capacity changes, and overbooked days to resolve.")
@SecurityRequirement(name = "keycloak")
@RequiresRole(StaffRole.BRANCH_MANAGER)
@RestController
@RequestMapping("/api/v1/admin")
class AdminScheduleController {

	private static final int MAX_DAYS = 92;

	private final ImpactPreview preview;
	private final AppointmentQueries queries;

	AdminScheduleController(ImpactPreview preview, AppointmentQueries queries) {
		this.preview = preview;
		this.queries = queries;
	}

	@PostMapping("/schedule-impact")
	@Operation(summary = "Preview a closure, hours change or capacity override: appointments displaced, intervals "
			+ "overbooked. Changes nothing")
	ImpactResponse impact(@RequestBody @Valid ImpactPreviewRequest request) {

		var impact = preview.preview(request.change());

		return new ImpactResponse(impact.displacedAppointments(), impact.overbookedIntervals());
	}

	@GetMapping("/overbooked")
	@Operation(summary = "Overbooked pool days with the appointments in each overbooked interval (at most 92 days)")
	List<OverbookedDayResponse> overbooked(@RequestParam LocalDate from,
			@RequestParam LocalDate to) {

		if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
			throw new IllegalArgumentException("The range must run forwards and cover at most " + MAX_DAYS + " days");
		}

		return queries.overbooked(from, to).stream().map(OverbookedDayResponse::of).toList();
	}
}
