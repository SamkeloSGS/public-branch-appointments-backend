package com.samdev.appointments.branchmanagement;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record ServiceId(UUID value) implements Identifier {

	public ServiceId {
		Objects.requireNonNull(value, "Service id must not be null");
	}

	public static ServiceId random() {
		return new ServiceId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
