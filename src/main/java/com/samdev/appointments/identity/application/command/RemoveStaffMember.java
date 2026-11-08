package com.samdev.appointments.identity.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffMemberId;

/**
 * Branch manager. Ends staff access; the Keycloak account is left alone.
 *
 * @param actor the staff member issuing the command; {@code null} for the system
 */
public record RemoveStaffMember(@Nullable StaffMemberId actor, StaffMemberId staffMemberId,
		@Nullable Long expectedVersion) {}
