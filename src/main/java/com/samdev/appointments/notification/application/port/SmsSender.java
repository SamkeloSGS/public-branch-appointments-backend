package com.samdev.appointments.notification.application.port;

import com.samdev.appointments.notification.domain.vo.SendOutcome;

/**
 * Sends one rendered text message through a provider. Implementations never log the number or the text, and report
 * failures as outcomes with an error code rather than throwing.
 */
public interface SmsSender {

	SendOutcome send(OutgoingSms sms);

	/**
	 * @param idempotencyKey the delivery ID, for providers that support one
	 * @param to E.164
	 */
	record OutgoingSms(String idempotencyKey, String to, String body) {

		@Override
		public String toString() {
			return "OutgoingSms[" + idempotencyKey + "]";
		}
	}
}
