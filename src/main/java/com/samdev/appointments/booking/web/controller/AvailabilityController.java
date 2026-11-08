package com.samdev.appointments.booking.web.controller;

import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.samdev.appointments.booking.application.query.AvailabilityQueries;
import com.samdev.appointments.booking.web.dto.response.AvailabilityResponse;
import com.samdev.appointments.booking.web.dto.response.AvailableDaysResponse;
import com.samdev.appointments.branchmanagement.ServiceId;

@Tag(name = "Availability", description = "Open times for a service at a branch. No sign-in needed.")
@RestController
@RequestMapping("/api/v1/availability")
class AvailabilityController {

	private final AvailabilityQueries queries;

	AvailabilityController(AvailabilityQueries queries) {
		this.queries = queries;
	}

	@GetMapping
	@Operation(summary = "Open start times on one day; empty when the branch is closed, 404 if not bookable at all")
	AvailabilityResponse openTimes(@RequestParam UUID serviceId,
			@RequestParam LocalDate date) {
		return new AvailabilityResponse(date, queries.openTimes(new ServiceId(serviceId), date));
	}

	@GetMapping("/days")
	@Operation(summary = "Dates in a range of at most 31 days that have at least one open time")
	AvailableDaysResponse openDays(@RequestParam UUID serviceId,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return new AvailableDaysResponse(from, to,
				queries.openDays(new ServiceId(serviceId), from, to));
	}
}
