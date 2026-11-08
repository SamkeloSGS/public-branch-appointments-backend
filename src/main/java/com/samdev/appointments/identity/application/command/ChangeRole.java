package com.samdev.appointments.identity.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.StaffRole;

/**
 * Branch manager. The new role.
 *
 * @param actor the staff member issuing the command; {@code null} for the system
 */
public record ChangeRole(@Nullable StaffMemberId actor, StaffMemberId staffMemberId, @Nullable Long expectedVersion,
		StaffRole role) {}
