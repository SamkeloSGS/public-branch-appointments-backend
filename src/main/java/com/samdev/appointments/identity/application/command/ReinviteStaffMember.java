package com.samdev.appointments.identity.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffMemberId;

/**
 * Branch manager. Restarts the 14-day invitation window.
 *
 * @param actor the staff member issuing the command; {@code null} for the system
 */
public record ReinviteStaffMember(@Nullable StaffMemberId actor, StaffMemberId staffMemberId,
		@Nullable Long expectedVersion) {}
