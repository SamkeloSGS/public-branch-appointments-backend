package com.samdev.appointments.notification.domain.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.notification.domain.exception.NotificationRuleViolation;
import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.DeliveryId;
import com.samdev.appointments.notification.domain.vo.DeliveryStatus;
import com.samdev.appointments.notification.domain.vo.NotificationId;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;
import com.samdev.appointments.notification.domain.vo.NotificationType;
import com.samdev.appointments.notification.domain.vo.RetryPolicy;
import com.samdev.appointments.notification.domain.vo.SendOutcome;
import com.samdev.appointments.notification.domain.vo.StatusReason;

/**
 * The intent to send one customer one message about one appointment, with what happened on each channel.
 * <p>
 * Each channel is retried on its own. The notification stays {@code PENDING} while any delivery is retrying, with
 * {@code sendAfter} moved to the earliest next attempt; then it is {@code SENT} if any channel was accepted, else
 * {@code FAILED}.
 */
@Table(schema = "notification", name = "notification")
public class Notification implements AggregateRoot<Notification, NotificationId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final NotificationId id;

	/**
	 * Identifies the cause, so handling an event twice creates nothing new.
	 */
	private final String sourceEventKey;

	private final UUID customerId;

	private final UUID appointmentId;

	@Enumerated(EnumType.STRING)
	private final NotificationType type;

	@Enumerated(EnumType.STRING)
	private NotificationStatus status;

	@Enumerated(EnumType.STRING)
	private @Nullable StatusReason statusReason;

	private Instant sendAfter;

	/**
	 * When the triggering event happened; lets a late, older event be recognised as stale.
	 */
	private final Instant eventAt;

	private final Instant createdAt;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	@JoinColumn(name = "notification_id", nullable = false)
	private List<Delivery> deliveries = new ArrayList<>();

	@Version
	private Long version;

	private Notification(NotificationId id, String sourceEventKey, CustomerId customerId, AppointmentId appointmentId,
			NotificationType type, Instant sendAfter, Instant eventAt, Instant now) {

		this.id = Objects.requireNonNull(id);
		this.sourceEventKey = Objects.requireNonNull(sourceEventKey);
		this.customerId = customerId.value();
		this.appointmentId = appointmentId.value();
		this.type = Objects.requireNonNull(type);
		this.status = NotificationStatus.PENDING;
		this.sendAfter = Objects.requireNonNull(sendAfter);
		this.eventAt = Objects.requireNonNull(eventAt);
		this.createdAt = now;
	}

	public static Notification create(String sourceEventKey, CustomerId customerId, AppointmentId appointmentId,
			NotificationType type, Instant sendAfter, Instant eventAt, Instant now) {
		return new Notification(NotificationId.random(), sourceEventKey, customerId, appointmentId, type,
				sendAfter, eventAt, now);
	}

	/**
	 * Withdrawn before sending, for example because a newer message about the appointment was queued.
	 */
	public void cancel() {
		assertPending("cancelled");
		status = NotificationStatus.CANCELLED;
	}

	/**
	 * Nothing will be sent.
	 */
	public void skip(StatusReason reason) {
		assertPending("skipped");
		status = NotificationStatus.SKIPPED;
		statusReason = Objects.requireNonNull(reason);
	}

	public boolean isDue(Instant now) {
		return status == NotificationStatus.PENDING && !sendAfter.isAfter(now);
	}

	/**
	 * The channels to attempt now, of those the customer offers: not yet attempted, or retrying and due. A channel
	 * that was accepted or has failed for good is never attempted again.
	 */
	public List<Channel> channelsToAttempt(Set<Channel> available, Instant now, RetryPolicy policy) {
		return available.stream()
				.sorted()
				.filter(channel -> delivery(channel)
						.map(it -> it.isRetrying() && !it.nextAttemptAt(policy).isAfter(now))
						.orElse(true))
				.toList();
	}

	/**
	 * Stops retrying channels the customer no longer offers.
	 */
	public void abandonChannelsOtherThan(Set<Channel> available, Instant now, RetryPolicy policy) {
		deliveries.stream()
				.filter(it -> !available.contains(it.getChannel()))
				.forEach(it -> it.abandon("CHANNEL_DISABLED"));
		resolve(now, policy);
	}

	/**
	 * The delivery for the channel, started if it does not exist yet. Its ID goes to the provider as idempotency key.
	 */
	public DeliveryId prepare(Channel channel) {

		assertPending("sent");

		return delivery(channel).orElseGet(() -> {
			var started = Delivery.start(channel);
			deliveries.add(started);
			return started;
		}).getId();
	}

	/**
	 * Records one attempt on one channel and resolves the notification's status.
	 */
	public void recordAttempt(Channel channel, SendOutcome outcome, Instant now, RetryPolicy policy) {

		prepare(channel);

		delivery(channel).orElseThrow().record(outcome, now, policy);
		resolve(now, policy);
	}

	/**
	 * A provider reported what became of a sent message.
	 */
	public boolean recordCallback(String providerMessageId, DeliveryStatus reported) {

		var delivery = deliveries.stream()
				.filter(it -> providerMessageId.equals(it.getProviderMessageId()))
				.findFirst();

		delivery.ifPresent(it -> it.report(reported));

		return delivery.isPresent();
	}

	/**
	 * While a channel retries, wait for its next attempt; otherwise {@code SENT} if any channel was accepted, else
	 * {@code FAILED}.
	 */
	private void resolve(Instant now, RetryPolicy policy) {

		if (deliveries.isEmpty()) {
			return;
		}

		var retrying = deliveries.stream().filter(Delivery::isRetrying).toList();

		if (!retrying.isEmpty()) {
			sendAfter = retrying.stream().map(it -> it.nextAttemptAt(policy)).min(Comparator.naturalOrder())
					.orElse(now);
			return;
		}

		status = deliveries.stream().anyMatch(it -> it.getStatus().accepted()) ? NotificationStatus.SENT
				: NotificationStatus.FAILED;
	}

	private Optional<Delivery> delivery(Channel channel) {
		return deliveries.stream().filter(it -> it.getChannel() == channel).findFirst();
	}

	private void assertPending(String action) {
		if (status != NotificationStatus.PENDING) {
			throw new NotificationRuleViolation("A " + status + " notification cannot be " + action);
		}
	}

	@Override
	public NotificationId getId() {
		return id;
	}

	public String getSourceEventKey() {
		return sourceEventKey;
	}

	public CustomerId getCustomerId() {
		return new CustomerId(customerId);
	}

	public AppointmentId getAppointmentId() {
		return new AppointmentId(appointmentId);
	}

	public NotificationType getType() {
		return type;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public @Nullable StatusReason getStatusReason() {
		return statusReason;
	}

	public Instant getSendAfter() {
		return sendAfter;
	}

	public Instant getEventAt() {
		return eventAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public List<Delivery> getDeliveries() {
		return deliveries.stream().sorted(Comparator.comparing(Delivery::getChannel)).toList();
	}

	public Long getVersion() {
		return version;
	}
}
