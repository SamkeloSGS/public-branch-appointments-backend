package com.samdev.appointments.notification.domain.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.jmolecules.ddd.types.Entity;
import org.jspecify.annotations.Nullable;

import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.DeliveryId;
import com.samdev.appointments.notification.domain.vo.DeliveryStatus;
import com.samdev.appointments.notification.domain.vo.ErrorCode;
import com.samdev.appointments.notification.domain.vo.RetryPolicy;
import com.samdev.appointments.notification.domain.vo.SendOutcome;

/**
 * What happened when a notification was sent on one channel. Its ID is passed to providers as an idempotency key
 * where they support one. Stores a provider error code or category only, never an address or message text.
 */
@Table(schema = "notification", name = "delivery")
public class Delivery implements Entity<Notification, DeliveryId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final DeliveryId id;

	@Enumerated(EnumType.STRING)
	private final Channel channel;

	@Enumerated(EnumType.STRING)
	private DeliveryStatus status;

	private int attempts;

	private @Nullable String providerMessageId;

	private @Nullable String lastError;

	private @Nullable Instant lastAttemptAt;

	private @Nullable Instant sentAt;

	private Delivery(DeliveryId id, Channel channel) {
		this.id = Objects.requireNonNull(id);
		this.channel = Objects.requireNonNull(channel);
		this.status = DeliveryStatus.RETRYING;
	}

	static Delivery start(Channel channel) {
		return new Delivery(DeliveryId.random(), channel);
	}

	/**
	 * Accepted: {@code SENT}. Permanent failure: {@code FAILED}. Transient failure: {@code RETRYING}, or
	 * {@code FAILED} once the attempts are used up.
	 */
	void record(SendOutcome outcome, Instant now, RetryPolicy policy) {

		if (!isRetrying()) {
			throw new IllegalStateException("Delivery " + id + " is " + status + " and takes no more attempts");
		}

		attempts++;
		lastAttemptAt = now;

		switch (outcome.kind()) {
			case ACCEPTED -> {
				status = DeliveryStatus.SENT;
				providerMessageId = outcome.providerMessageId();
				sentAt = now;
				lastError = null;
			}
			case PERMANENT -> {
				status = DeliveryStatus.FAILED;
				lastError = ErrorCode.reduce(outcome.errorCode());
			}
			case TRANSIENT -> {
				status = attempts >= policy.maxAttempts() ? DeliveryStatus.FAILED : DeliveryStatus.RETRYING;
				lastError = ErrorCode.reduce(outcome.errorCode());
			}
		}
	}

	/**
	 * The customer no longer offers this channel: stop retrying it.
	 */
	void abandon(String reason) {
		if (isRetrying()) {
			status = DeliveryStatus.FAILED;
			lastError = ErrorCode.reduce(reason);
		}
	}

	/**
	 * A provider callback for a sent message.
	 */
	void report(DeliveryStatus reported) {
		if (status.accepted() && (reported == DeliveryStatus.DELIVERED || reported == DeliveryStatus.BOUNCED)) {
			status = reported;
		}
	}

	boolean isRetrying() {
		return status == DeliveryStatus.RETRYING;
	}

	/**
	 * When the next attempt may happen; the start of time for one never attempted.
	 */
	Instant nextAttemptAt(RetryPolicy policy) {
		return lastAttemptAt == null ? Instant.EPOCH : lastAttemptAt.plus(policy.delayAfter(attempts));
	}

	@Override
	public DeliveryId getId() {
		return id;
	}

	public Channel getChannel() {
		return channel;
	}

	public DeliveryStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public @Nullable String getProviderMessageId() {
		return providerMessageId;
	}

	public @Nullable String getLastError() {
		return lastError;
	}

	public @Nullable Instant getLastAttemptAt() {
		return lastAttemptAt;
	}

	public @Nullable Instant getSentAt() {
		return sentAt;
	}
}
