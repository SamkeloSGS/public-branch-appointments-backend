package com.samdev.appointments.identity;

/**
 * The one interface other modules use for access decisions. Other modules never read the token themselves.
 * Refusals are thrown as Spring Security's {@code AccessDeniedException}, which every module maps to {@code 403}.
 */
public interface AccessControl {

	/**
	 * The caller of the current request.
	 */
	CurrentUser currentUser();

	/**
	 * Passes for the given role and every role above it.
	 */
	void requireRole(StaffRole role);

	/**
	 * True for a subject whose staff record is {@code INVITED}, {@code ACTIVE} or {@code SUSPENDED}.
	 */
	boolean isStaff(Subject subject);
}
