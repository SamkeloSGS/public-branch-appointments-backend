package com.samdev.appointments.identity;

/**
 * Staff roles in ascending order: each role includes the ones before it.
 */
public enum StaffRole {

	BRANCH_STAFF, BRANCH_MANAGER;

	/**
	 * Whether this role passes a check for the given role: true for the same role and every role above it.
	 */
	public boolean includes(StaffRole required) {
		return compareTo(required) >= 0;
	}
}
