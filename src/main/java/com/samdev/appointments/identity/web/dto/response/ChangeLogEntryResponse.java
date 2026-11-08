package com.samdev.appointments.identity.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.application.port.StaffChangeLog.Action;
import com.samdev.appointments.identity.application.port.StaffChangeLogEntries.Entry;

/**
 * @param changedBy {@code null} for changes made by the system
 */
public record ChangeLogEntryResponse(UUID id, Action action, @Nullable UUID changedBy, String detail,
		Instant occurredAt) {

	public static ChangeLogEntryResponse of(Entry entry) {
		return new ChangeLogEntryResponse(entry.id(), entry.action(),
				entry.changedBy() == null ? null : entry.changedBy().value(), entry.detail(), entry.occurredAt());
	}
}
