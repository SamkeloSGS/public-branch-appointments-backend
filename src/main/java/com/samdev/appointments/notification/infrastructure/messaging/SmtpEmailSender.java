package com.samdev.appointments.notification.infrastructure.messaging;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.samdev.appointments.notification.application.port.EmailSender;
import com.samdev.appointments.notification.domain.vo.SendOutcome;
import com.samdev.appointments.notification.infrastructure.config.NotificationProperties;

/**
 * Email over SMTP: Mailpit locally, a provider's SMTP endpoint (such as Amazon SES) elsewhere. The delivery ID is the
 * message ID. Logs the delivery ID only, never the address or text.
 */
@Component
class SmtpEmailSender implements EmailSender {

	private static final Logger LOG = LoggerFactory.getLogger(SmtpEmailSender.class);

	private final JavaMailSender mail;
	private final NotificationProperties properties;

	SmtpEmailSender(JavaMailSender mail, NotificationProperties properties) {
		this.mail = mail;
		this.properties = properties;
	}

	@Override
	public SendOutcome send(OutgoingEmail email) {

		var messageId = "<" + email.idempotencyKey() + "@appointments>";

		try {
			var message = mail.createMimeMessage();
			var helper = new MimeMessageHelper(message, false, "UTF-8");
			helper.setFrom(properties.emailFrom());
			helper.setTo(email.to());
			helper.setSubject(email.subject());
			helper.setText(email.body(), false);
			message.saveChanges();
			message.setHeader("Message-ID", messageId);

			mail.send(message);

			return SendOutcome.accepted(messageId);

		} catch (MessagingException | MailParseException e) {
			LOG.warn("Email {} could not be built: {}", email.idempotencyKey(), e.getClass().getSimpleName());
			return SendOutcome.permanentFailure("INVALID_MESSAGE");
		} catch (MailAuthenticationException e) {
			LOG.warn("Email {} not sent: SMTP authentication failed", email.idempotencyKey());
			return SendOutcome.transientFailure("SMTP_AUTHENTICATION");
		} catch (MailSendException e) {
			var invalidAddress = e.getFailedMessages().values().stream().anyMatch(SendFailedException.class::isInstance);
			LOG.warn("Email {} not sent: {}", email.idempotencyKey(), invalidAddress ? "address refused" : "SMTP unavailable");
			return invalidAddress ? SendOutcome.permanentFailure("INVALID_ADDRESS")
					: SendOutcome.transientFailure("SMTP_UNAVAILABLE");
		} catch (MailException e) {
			LOG.warn("Email {} not sent: {}", email.idempotencyKey(), e.getClass().getSimpleName());
			return SendOutcome.transientFailure("SMTP_ERROR");
		}
	}
}
