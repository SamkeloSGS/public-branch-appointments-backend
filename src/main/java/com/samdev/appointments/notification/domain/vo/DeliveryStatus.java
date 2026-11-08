package com.samdev.appointments.notification.domain.vo;

/**
 * {@code RETRYING} until accepted ({@code SENT}) or given up ({@code FAILED}). Provider callbacks may later report
 * {@code DELIVERED} or {@code BOUNCED} for a sent message.
 */
public enum DeliveryStatus {

	RETRYING, SENT, FAILED, DELIVERED, BOUNCED;

	/**
	 * The provider accepted the message; it is never sent again on this channel.
	 */
	public boolean accepted() {
		return this == SENT || this == DELIVERED || this == BOUNCED;
	}
}
