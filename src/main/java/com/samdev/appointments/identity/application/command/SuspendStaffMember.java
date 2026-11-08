package com.samdev.appointments.identity.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffMemberId;

/**
 * Branch manager.
 *
 * @param actor the staff member issuing the command; {@code null} for the system
 */
public record SuspendStaffMember(@Nullable StaffMemberId actor, StaffMemberId staffMemberId,
		@Nullable Long expectedVersion) {}
