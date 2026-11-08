package com.samdev.appointments.booking.domain.vo;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record PoolDayScheduleId(UUID value) implements Identifier {

	public PoolDayScheduleId {
		Objects.requireNonNull(value, "Schedule id must not be null");
	}

	public static PoolDayScheduleId random() {
		return new PoolDayScheduleId(UUID.randomUUID());
	}
}
