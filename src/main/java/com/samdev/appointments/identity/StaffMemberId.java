package com.samdev.appointments.identity;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record StaffMemberId(UUID value) implements Identifier {

	public StaffMemberId {
		Objects.requireNonNull(value, "Staff member id must not be null");
	}

	public static StaffMemberId random() {
		return new StaffMemberId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
