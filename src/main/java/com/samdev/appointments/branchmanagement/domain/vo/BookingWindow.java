package com.samdev.appointments.branchmanagement.domain.vo;

import java.time.Duration;

import jakarta.persistence.Column;

import org.jmolecules.ddd.types.ValueObject;

/**
 * Minimum lead time and maximum days in advance for booking an offering.
 */
public record BookingWindow(@Column(name = "min_lead_time_min") int minLeadTimeMinutes,
		@Column(name = "max_advance_days") int maxAdvanceDays) implements ValueObject {

	public BookingWindow {
		if (minLeadTimeMinutes < 0) {
			throw new IllegalArgumentException("Minimum lead time must not be negative");
		}
		if (maxAdvanceDays < 1 || maxAdvanceDays > 365) {
			throw new IllegalArgumentException("Maximum advance days must be between 1 and 365");
		}
	}

	public static BookingWindow of(Duration minLeadTime, int maxAdvanceDays) {
		return new BookingWindow(Durations.wholeMinutes(minLeadTime, "Minimum lead time"), maxAdvanceDays);
	}

	public Duration minLeadTime() {
		return Duration.ofMinutes(minLeadTimeMinutes);
	}
}
