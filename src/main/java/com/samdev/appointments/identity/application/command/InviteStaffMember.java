package com.samdev.appointments.identity.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.StaffRole;

/**
 * Branch manager. Creates an INVITED staff member, matched later by verified email.
 *
 * @param actor the staff member issuing the command; {@code null} for the system
 */
public record InviteStaffMember(@Nullable StaffMemberId actor, String email, String displayName, StaffRole role) {}
