package com.samdev.appointments.branchmanagement;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record ServiceOfferingId(UUID value) implements Identifier {

	public ServiceOfferingId {
		Objects.requireNonNull(value, "ServiceOffering id must not be null");
	}

	public static ServiceOfferingId random() {
		return new ServiceOfferingId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
