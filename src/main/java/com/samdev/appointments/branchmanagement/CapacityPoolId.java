package com.samdev.appointments.branchmanagement;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record CapacityPoolId(UUID value) implements Identifier {

	public CapacityPoolId {
		Objects.requireNonNull(value, "CapacityPool id must not be null");
	}

	public static CapacityPoolId random() {
		return new CapacityPoolId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
