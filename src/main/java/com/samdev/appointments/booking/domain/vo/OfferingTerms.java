package com.samdev.appointments.booking.domain.vo;

import java.time.Duration;

/**
 * How one service is booked at one branch, from the branch's service offering.
 */
public record OfferingTerms(Duration duration, Duration slotInterval, Duration minLeadTime, int maxAdvanceDays) {

	public OfferingTerms {
		if (duration.isZero() || duration.isNegative() || slotInterval.isZero() || slotInterval.isNegative()) {
			throw new IllegalArgumentException("Duration and slot interval must be positive");
		}
	}

	public static OfferingTerms ofMinutes(int duration, int slotInterval, int minLeadTime, int maxAdvanceDays) {
		return new OfferingTerms(Duration.ofMinutes(duration), Duration.ofMinutes(slotInterval),
				Duration.ofMinutes(minLeadTime), maxAdvanceDays);
	}
}
