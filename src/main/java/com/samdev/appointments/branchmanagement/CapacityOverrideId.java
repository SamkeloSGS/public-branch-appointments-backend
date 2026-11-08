package com.samdev.appointments.branchmanagement;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record CapacityOverrideId(UUID value) implements Identifier {

	public CapacityOverrideId {
		Objects.requireNonNull(value, "CapacityOverride id must not be null");
	}

	public static CapacityOverrideId random() {
		return new CapacityOverrideId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
