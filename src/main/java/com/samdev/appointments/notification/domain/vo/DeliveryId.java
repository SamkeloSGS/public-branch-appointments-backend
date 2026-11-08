package com.samdev.appointments.notification.domain.vo;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record DeliveryId(UUID value) implements Identifier {

	public DeliveryId {
		Objects.requireNonNull(value, "The delivery id must not be null");
	}

	public static DeliveryId random() {
		return new DeliveryId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
