package com.samdev.appointments.notification.application.handler;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.AppointmentBooked;
import com.samdev.appointments.booking.AppointmentCancelled;
import com.samdev.appointments.booking.AppointmentCheckedIn;
import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentRebookingRequired;
import com.samdev.appointments.booking.AppointmentRescheduled;
import com.samdev.appointments.booking.ClosedReason;
import com.samdev.appointments.customer.CustomerErased;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.notification.application.exception.NotificationNotFoundException;
import com.samdev.appointments.notification.application.port.NotificationSettings;
import com.samdev.appointments.notification.domain.entity.Notification;
import com.samdev.appointments.notification.domain.exception.NotificationRuleViolation;
import com.samdev.appointments.notification.domain.repository.Notifications;
import com.samdev.appointments.notification.domain.vo.NotificationId;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;
import com.samdev.appointments.notification.domain.vo.NotificationType;

/**
 * Turns appointment and customer events into notifications to send, and withdraws those that no longer apply. Never
 * calls a provider or another module.
 * <p>
 * Each event is handled in one transaction. Its source event key makes a repeat create nothing. A message that
 * supersedes others (rescheduled, cancelled, needs rebooking) cancels every pending notification for the
 * appointment, and an event older than such a message is recognised as stale and creates nothing.
 */
@Component
public class NotificationCommandHandler {

	private static final EnumSet<NotificationType> SUPERSEDING = EnumSet.of(NotificationType.RESCHEDULED,
			NotificationType.CANCELLATION, NotificationType.REBOOKING_REQUIRED);

	private final Notifications notifications;
	private final NotificationSettings settings;
	private final Clock clock;

	NotificationCommandHandler(Notifications notifications, NotificationSettings settings, Clock clock) {
		this.notifications = notifications;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional
	public void handle(AppointmentBooked event) {
		react("AppointmentBooked", event.appointmentId(), event.customerId(), event.occurredAt(),
				NotificationType.CONFIRMATION, event.startsAt());
	}

	@Transactional
	public void handle(AppointmentRescheduled event) {
		react("AppointmentRescheduled", event.appointmentId(), event.customerId(),
				event.occurredAt(), NotificationType.RESCHEDULED, event.startsAt());
	}

	@Transactional
	public void handle(AppointmentCancelled event) {

		if (event.closedReason() == ClosedReason.CUSTOMER_ERASED) {
			cancelPending(event.appointmentId());
			return;
		}

		react("AppointmentCancelled", event.appointmentId(), event.customerId(), event.occurredAt(),
				NotificationType.CANCELLATION, null);
	}

	@Transactional
	public void handle(AppointmentRebookingRequired event) {
		react("AppointmentRebookingRequired", event.appointmentId(), event.customerId(),
				event.occurredAt(), NotificationType.REBOOKING_REQUIRED, null);
	}

	/**
	 * The customer is here: no reminder needed.
	 */
	@Transactional
	public void handle(AppointmentCheckedIn event) {
		notifications.findByAppointmentIdAndStatus(event.appointmentId().value(), NotificationStatus.PENDING).stream()
				.filter(it -> it.getType() == NotificationType.REMINDER)
				.forEach(it -> notifications.save(cancelled(it)));
	}

	@Transactional
	public void handle(CustomerErased event) {
		notifications.findByCustomerIdAndStatus(event.customerId().value(), NotificationStatus.PENDING)
				.forEach(it -> notifications.save(cancelled(it)));
	}

	/**
	 * Staff: send the same kind of message again, due now. Goes through the full relevance check; the original is
	 * unchanged.
	 *
	 * @throws NotificationRuleViolation for a notification still pending or cancelled
	 */
	@Transactional
	public NotificationId resend(NotificationId id) {

		var original = notifications.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));

		if (original.getStatus() == NotificationStatus.PENDING || original.getStatus() == NotificationStatus.CANCELLED) {
			throw new NotificationRuleViolation(
					"Only a sent, failed or skipped notification can be sent again, not a " + original.getStatus() + " one");
		}

		var prefix = "RESEND:" + id + ":";
		var sequence = notifications.countBySourceEventKeyStartingWith(prefix) + 1;
		var now = clock.instant();

		var copy = Notification.create(prefix + sequence, original.getCustomerId(), original.getAppointmentId(),
				original.getType(), now, now, now);

		return notifications.save(copy).getId();
	}

	/**
	 * @param reminderFor the appointment start, to create a reminder for; {@code null} for none
	 */
	private void react(String eventType, AppointmentId appointmentId, CustomerId customerId, Instant occurredAt, NotificationType type, @Nullable Instant reminderFor) {

		var key = key(eventType, appointmentId, occurredAt, type);

		if (notifications.existsBySourceEventKey(key)) {
			return;
		}
		if (notifications.existsByAppointmentIdAndTypeInAndEventAtAfter(appointmentId.value(), SUPERSEDING,
				occurredAt)) {
			return;
		}
		if (type.supersedes()) {
			cancelPending(appointmentId);
		}

		var now = clock.instant();

		notifications.save(Notification.create(key, customerId, appointmentId, type, now, occurredAt, now));

		if (reminderFor != null) {

			var sendAfter = reminderFor.minus(settings.reminderLeadTime());

			if (sendAfter.isAfter(occurredAt)) {
				notifications.save(Notification.create(key(eventType, appointmentId, occurredAt,
						NotificationType.REMINDER), customerId, appointmentId, NotificationType.REMINDER,
						sendAfter, occurredAt, now));
			}
		}
	}

	private void cancelPending(AppointmentId appointmentId) {
		notifications.findByAppointmentIdAndStatus(appointmentId.value(), NotificationStatus.PENDING)
				.forEach(it -> notifications.save(cancelled(it)));
	}

	private static Notification cancelled(Notification notification) {
		notification.cancel();
		return notification;
	}

	/**
	 * {@code <event type>:<appointment ID>:<event occurredAt>:<notification type>}
	 */
	static String key(String eventType, AppointmentId appointmentId, Instant occurredAt, NotificationType type) {
		return eventType + ":" + appointmentId + ":" + occurredAt + ":" + type;
	}
}
