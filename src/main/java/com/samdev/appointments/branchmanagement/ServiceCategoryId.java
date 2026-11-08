package com.samdev.appointments.branchmanagement;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record ServiceCategoryId(UUID value) implements Identifier {

	public ServiceCategoryId {
		Objects.requireNonNull(value, "Service category id must not be null");
	}

	public static ServiceCategoryId random() {
		return new ServiceCategoryId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
