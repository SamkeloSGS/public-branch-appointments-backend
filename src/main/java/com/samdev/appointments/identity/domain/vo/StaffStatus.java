package com.samdev.appointments.identity.domain.vo;

/**
 * {@code INVITED → ACTIVE}, {@code ACTIVE ⇄ SUSPENDED}, and any status except {@code REMOVED} to {@code REMOVED}.
 * {@code REMOVED} is terminal.
 */
public enum StaffStatus {
	INVITED, ACTIVE, SUSPENDED, REMOVED
}
