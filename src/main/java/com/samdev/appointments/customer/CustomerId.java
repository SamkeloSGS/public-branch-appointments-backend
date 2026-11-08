package com.samdev.appointments.customer;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record CustomerId(UUID value) implements Identifier {

	public CustomerId {
		Objects.requireNonNull(value, "Customer id must not be null");
	}

	public static CustomerId random() {
		return new CustomerId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
