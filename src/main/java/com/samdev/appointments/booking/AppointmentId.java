package com.samdev.appointments.booking;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record AppointmentId(UUID value) implements Identifier {

	public AppointmentId {
		Objects.requireNonNull(value, "Appointment id must not be null");
	}

	public static AppointmentId random() {
		return new AppointmentId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
