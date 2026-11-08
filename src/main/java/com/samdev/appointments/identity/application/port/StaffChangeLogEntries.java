package com.samdev.appointments.identity.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.application.port.StaffChangeLog.Action;

/**
 * Reads the change log of one staff member, oldest first.
 */
public interface StaffChangeLogEntries {

	List<Entry> entriesFor(StaffMemberId staffMemberId);

	record Entry(UUID id, Action action, @Nullable StaffMemberId changedBy, String detail, Instant occurredAt) {}
}
