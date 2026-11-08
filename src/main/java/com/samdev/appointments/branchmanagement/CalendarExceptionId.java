package com.samdev.appointments.branchmanagement;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record CalendarExceptionId(UUID value) implements Identifier {

	public CalendarExceptionId {
		Objects.requireNonNull(value, "CalendarException id must not be null");
	}

	public static CalendarExceptionId random() {
		return new CalendarExceptionId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
