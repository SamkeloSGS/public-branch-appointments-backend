package com.samdev.appointments.booking.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.ClosedReason;
import com.samdev.appointments.booking.domain.vo.Actor;

/**
 * Customer, branch staff or the system.
 *
 * @param reason {@code null} for the actor's default: customer or staff cancelled
 */
public record CancelAppointment(AppointmentId appointmentId, @Nullable Long expectedVersion, Actor actor,
		@Nullable ClosedReason reason) {

	public ClosedReason closedReason() {
		if (reason != null) {
			return reason;
		}
		return actor == Actor.CUSTOMER ? ClosedReason.CUSTOMER_CANCELLED : ClosedReason.STAFF_CANCELLED;
	}
}
