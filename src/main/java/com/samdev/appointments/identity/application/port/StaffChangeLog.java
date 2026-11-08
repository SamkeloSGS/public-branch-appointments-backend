package com.samdev.appointments.identity.application.port;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffMemberId;

/**
 * Append-only record of every change to a staff member's access. Written in the same transaction as the change: if
 * the row cannot be written, the command fails. The detail never contains an email address.
 */
public interface StaffChangeLog {

	enum Action {
		INVITED, REINVITED, ACTIVATED, ROLE_CHANGED, SUSPENDED, REINSTATED, REMOVED
	}

	void record(StaffMemberId staffMemberId, Action action, @Nullable StaffMemberId changedBy, String detail);
}
