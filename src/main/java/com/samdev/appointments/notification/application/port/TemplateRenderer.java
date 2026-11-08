package com.samdev.appointments.notification.application.port;

import java.time.LocalDate;
import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.NotificationType;

/**
 * Produces the subject and body of a message for a type, channel and language, falling back to the default
 * language when there is no template for the requested one.
 */
public interface TemplateRenderer {

	RenderedMessage render(NotificationType type, Channel channel, String language, MessageValues values);

	/**
	 * @param date the appointment date at the branch
	 * @param time the appointment start at the branch
	 */
	record MessageValues(String customerName, String reference, String serviceName, String branchName,
			String branchAddress, LocalDate date, LocalTime time, String manageLink) {

		@Override
		public String toString() {
			return "MessageValues[" + reference + "]";
		}
	}

	/**
	 * @param subject {@code null} for SMS
	 */
	record RenderedMessage(@Nullable String subject, String body) {

		@Override
		public String toString() {
			return "RenderedMessage";
		}
	}
}
