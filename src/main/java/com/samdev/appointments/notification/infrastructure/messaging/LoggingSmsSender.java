package com.samdev.appointments.notification.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samdev.appointments.notification.application.port.SmsSender;
import com.samdev.appointments.notification.domain.vo.SendOutcome;

/**
 * Stand-in until an SMS provider is chosen: accepts every message and logs only its delivery ID. Replace with a
 * provider adapter implementing {@link SmsSender}; nothing else changes.
 */
@Component
class LoggingSmsSender implements SmsSender {

	private static final Logger LOG = LoggerFactory.getLogger(LoggingSmsSender.class);

	@Override
	public SendOutcome send(OutgoingSms sms) {
		LOG.info("SMS {} accepted by the logging stand-in ({} characters)", sms.idempotencyKey(), sms.body().length());
		return SendOutcome.accepted("log-" + sms.idempotencyKey());
	}
}
