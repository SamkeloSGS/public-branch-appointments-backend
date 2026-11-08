package com.samdev.appointments.booking.domain.vo;

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;

import org.jmolecules.ddd.types.ValueObject;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * One line on a pool day: the time range one appointment occupies.
 */
public record Reservation(@Column(name = "appointment_id") UUID appointmentId, LocalTime fromTime, LocalTime toTime)
		implements ValueObject {

	public Reservation {
		Objects.requireNonNull(appointmentId, "Appointment id is required");
		if (fromTime == null || toTime == null || !fromTime.isBefore(toTime)) {
			throw new IllegalArgumentException("A reservation starts before it ends");
		}
	}

	public static Reservation of(AppointmentId appointmentId, TimeRange range) {
		return new Reservation(appointmentId.value(), range.start(), range.end());
	}

	public AppointmentId appointment() {
		return new AppointmentId(appointmentId);
	}

	public TimeRange range() {
		return new TimeRange(fromTime, toTime);
	}
}
