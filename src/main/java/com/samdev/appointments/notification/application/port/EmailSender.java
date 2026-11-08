package com.samdev.appointments.notification.application.port;

import com.samdev.appointments.notification.domain.vo.SendOutcome;

/**
 * Sends one rendered email through a provider. Implementations never log the address or the text, and report
 * failures as outcomes with an error code rather than throwing.
 */
public interface EmailSender {

	SendOutcome send(OutgoingEmail email);

	/**
	 * @param idempotencyKey the delivery ID, for providers that support one
	 */
	record OutgoingEmail(String idempotencyKey, String to, String subject, String body) {

		@Override
		public String toString() {
			return "OutgoingEmail[" + idempotencyKey + "]";
		}
	}
}
