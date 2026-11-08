package com.samdev.appointments.notification.application.handler;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.samdev.appointments.booking.AppointmentDetails;
import com.samdev.appointments.booking.AppointmentDirectory;
import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.branchmanagement.BranchDirectory;
import com.samdev.appointments.customer.CustomerContact;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.notification.application.port.EmailSender;
import com.samdev.appointments.notification.application.port.EmailSender.OutgoingEmail;
import com.samdev.appointments.notification.application.port.NotificationMetrics;
import com.samdev.appointments.notification.application.port.NotificationSettings;
import com.samdev.appointments.notification.application.port.SmsSender;
import com.samdev.appointments.notification.application.port.SmsSender.OutgoingSms;
import com.samdev.appointments.notification.application.port.TemplateRenderer;
import com.samdev.appointments.notification.application.port.TemplateRenderer.MessageValues;
import com.samdev.appointments.notification.domain.repository.Notifications;
import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.NotificationId;
import com.samdev.appointments.notification.domain.vo.NotificationType;
import com.samdev.appointments.notification.domain.vo.SendOutcome;
import com.samdev.appointments.notification.domain.vo.StatusReason;

/**
 * Sends due notifications. Each is claimed with a locking read that skips rows other instances hold, and processed
 * in its own transaction: load current details, check the message still fits the appointment, choose the channels
 * the customer offers now, render in their language, send, and record each attempt. A failure on one notification
 * rolls back only that one; it stays due for the next run.
 * <p>
 * At least once: if the process stops after a provider accepted a message but before the commit, it is sent again.
 * The delivery ID is passed as idempotency key to providers that support one.
 */
@Component
public class NotificationDispatcher {

	private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatcher.class);

	/**
	 * Never a real ID; keeps the exclusion list of the claim query non-empty.
	 */
	private static final UUID NONE = new UUID(0, 0);

	private final Notifications notifications;
	private final AppointmentDirectory appointments;
	private final CustomerDirectory customers;
	private final BranchDirectory branches;
	private final EmailSender email;
	private final SmsSender sms;
	private final TemplateRenderer templates;
	private final NotificationSettings settings;
	private final NotificationMetrics metrics;
	private final Clock clock;
	private final TransactionTemplate transaction;

	NotificationDispatcher(Notifications notifications, AppointmentDirectory appointments, CustomerDirectory customers,
			BranchDirectory branches, EmailSender email, SmsSender sms, TemplateRenderer templates,
			NotificationSettings settings, NotificationMetrics metrics, Clock clock,
			PlatformTransactionManager transactionManager) {

		this.notifications = notifications;
		this.appointments = appointments;
		this.customers = customers;
		this.branches = branches;
		this.email = email;
		this.sms = sms;
		this.templates = templates;
		this.settings = settings;
		this.metrics = metrics;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * Processes up to one batch of due notifications.
	 *
	 * @return how many were processed successfully
	 */
	public int dispatchDue() {

		var handled = new ArrayList<UUID>(List.of(NONE));
		var processed = 0;

		for (var i = 0; i < settings.batchSize(); i++) {

			var current = new AtomicReference<UUID>();

			try {
				Optional<UUID> claimed = transaction.execute(status -> {
					var next = notifications.claimNextDue(clock.instant(), handled);
					next.ifPresent(id -> {
						current.set(id);
						process(new NotificationId(id));
					});
					return next;
				});

				if (claimed == null || claimed.isEmpty()) {
					break;
				}

				handled.add(claimed.get());
				processed++;

			} catch (RuntimeException e) {
				if (current.get() == null) {
					throw e;
				}
				handled.add(current.get());
				LOG.warn("Notification {} could not be processed and stays due: {}", current.get(),
						e.getClass().getSimpleName());
			}
		}

		return processed;
	}

	private void process(NotificationId id) {

		var notification = notifications.findById(id).orElseThrow();
		var now = clock.instant();
		var policy = settings.retryPolicy();

		var appointment = appointments.find(notification.getAppointmentId()).orElse(null);
		var irrelevant = appointment == null ? StatusReason.APPOINTMENT_CHANGED
				: relevance(notification.getType(), appointment, now);

		if (irrelevant != null) {
			notification.skip(irrelevant);
			metrics.skipped(irrelevant);
			notifications.save(notification);
			return;
		}

		var contact = customers.contactFor(notification.getCustomerId()).orElse(null);

		if (contact == null || (contact.email() == null && contact.phone() == null)) {
			if (notification.getDeliveries().isEmpty()) {
				var reason = contact == null ? StatusReason.CUSTOMER_UNAVAILABLE : StatusReason.NO_CHANNEL;
				notification.skip(reason);
				metrics.skipped(reason);
			} else {
				notification.abandonChannelsOtherThan(EnumSet.noneOf(Channel.class), now, policy);
			}
			notifications.save(notification);
			return;
		}

		var available = EnumSet.noneOf(Channel.class);
		if (contact.email() != null) {
			available.add(Channel.EMAIL);
		}
		if (contact.phone() != null) {
			available.add(Channel.SMS);
		}

		notification.abandonChannelsOtherThan(available, now, policy);

		if (notification.getStatus().isTerminal()) {
			notifications.save(notification);
			return;
		}

		var channels = notification.channelsToAttempt(available, now, policy);
		var values = values(appointment, contact);

		// Start every channel's delivery first, so the first result does not settle the notification
		var deliveryIds = channels.stream().map(it -> notification.prepare(it).toString()).toList();

		for (var i = 0; i < channels.size(); i++) {
			var channel = channels.get(i);
			var message = templates.render(notification.getType(), channel, contact.language(), values);
			var outcome = send(channel, deliveryIds.get(i), contact, message);
			notification.recordAttempt(channel, outcome, now, policy);
			metrics.attempted(channel, outcome.kind());
		}

		notifications.save(notification);
	}

	/**
	 * Whether the message still matches the appointment's current state; the reason to skip it otherwise.
	 */
	static @Nullable StatusReason relevance(NotificationType type, AppointmentDetails appointment, Instant now) {
		return switch (type) {
			case CONFIRMATION, RESCHEDULED, REMINDER -> appointment.status() != AppointmentStatus.BOOKED
					? StatusReason.APPOINTMENT_CHANGED
					: appointment.startsAt().isAfter(now) ? null : StatusReason.TOO_LATE;
			case CANCELLATION -> appointment.status() == AppointmentStatus.CANCELLED ? null
					: StatusReason.APPOINTMENT_CHANGED;
			case REBOOKING_REQUIRED -> appointment.status() == AppointmentStatus.REBOOKING_REQUIRED ? null
					: StatusReason.APPOINTMENT_CHANGED;
		};
	}

	private SendOutcome send(Channel channel, String deliveryId, CustomerContact contact,
			TemplateRenderer.RenderedMessage message) {
		try {
			return switch (channel) {
				case EMAIL -> email.send(new OutgoingEmail(deliveryId, contact.email(),
						message.subject() == null ? "" : message.subject(), message.body()));
				case SMS -> sms.send(new OutgoingSms(deliveryId, contact.phone(), message.body()));
			};
		} catch (RuntimeException e) {
			return SendOutcome.transientFailure("PROVIDER_ERROR");
		}
	}

	private MessageValues values(AppointmentDetails appointment, CustomerContact contact) {

		var branch = branches.branch();
		var service = branches.findService(appointment.serviceId());

		return new MessageValues(contact.displayName(), appointment.reference(),
				service.map(BranchDirectory.ServiceSummary::name).orElse(""),
				branch.name(), branch.address().street() + ", " + branch.address().city(),
				appointment.date(), appointment.startTime(), settings.manageLink(appointment.reference()));
	}
}
