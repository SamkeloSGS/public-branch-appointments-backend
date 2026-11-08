package com.samdev.appointments.booking.domain.vo;

import com.samdev.appointments.booking.BookedBy;

/**
 * Who issues a command. Only customers are bound by the cutoff, the minimum lead time and the booking limit.
 */
public enum Actor {

	CUSTOMER, STAFF, SYSTEM;

	public BookedBy bookedBy() {
		return this == CUSTOMER ? BookedBy.CUSTOMER : BookedBy.STAFF;
	}
}
