package com.samdev.appointments.booking.domain.exception;

/**
 * A booking rule rejected the command. Mapped to {@code 409} with the {@link RejectionCode}. Messages never contain
 * personal data.
 */
public class BookingRejected extends RuntimeException {

	private final RejectionCode code;

	public BookingRejected(RejectionCode code, String message) {
		super(message);
		this.code = code;
	}

	public RejectionCode getCode() {
		return code;
	}
}
