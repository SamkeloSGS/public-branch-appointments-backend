package com.samdev.appointments.branchmanagement.domain.vo;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record OpeningHoursId(UUID value) implements Identifier {

	public OpeningHoursId {
		Objects.requireNonNull(value, "OpeningHours id must not be null");
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
