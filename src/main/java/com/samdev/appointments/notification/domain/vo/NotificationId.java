package com.samdev.appointments.notification.domain.vo;

import java.util.Objects;
import java.util.UUID;

import org.jmolecules.ddd.types.Identifier;

public record NotificationId(UUID value) implements Identifier {

	public NotificationId {
		Objects.requireNonNull(value, "The notification id must not be null");
	}

	public static NotificationId random() {
		return new NotificationId(UUID.randomUUID());
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
