package com.samdev.appointments.notification.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.notification.domain.exception.NotificationRuleViolation;
import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.DeliveryStatus;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;
import com.samdev.appointments.notification.domain.vo.NotificationType;
import com.samdev.appointments.notification.domain.vo.RetryPolicy;
import com.samdev.appointments.notification.domain.vo.SendOutcome;
import com.samdev.appointments.notification.domain.vo.StatusReason;

/**
 * Status resolution, per-channel retries and terminal states of a notification.
 */
class NotificationTests {

	static final Instant NOW = Instant.parse("2026-10-11T08:00:00Z");
	static final RetryPolicy POLICY = RetryPolicy.DEFAULT;
	static final EnumSet<Channel> BOTH = EnumSet.allOf(Channel.class);

	@Test
	@DisplayName("NT-1 an accepted message makes the notification SENT")
	void sent() {

		var notification = notification();

		notification.recordAttempt(Channel.EMAIL, SendOutcome.accepted("m-1"), NOW, POLICY);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getDeliveries()).singleElement().satisfies(it -> {
			assertThat(it.getStatus()).isEqualTo(DeliveryStatus.SENT);
			assertThat(it.getProviderMessageId()).isEqualTo("m-1");
			assertThat(it.getSentAt()).isEqualTo(NOW);
		});
	}

	@Test
	@DisplayName("NT-8 transient failures retry after 1, 5, 15 and 60 minutes, then the delivery fails")
	void retries() {

		var notification = notification();
		var at = NOW;

		for (var delay : new long[] { 1, 5, 15, 60 }) {
			notification.recordAttempt(Channel.EMAIL, SendOutcome.transientFailure("TIMEOUT"), at, POLICY);
			assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
			assertThat(notification.getSendAfter()).isEqualTo(at.plus(Duration.ofMinutes(delay)));
			assertThat(notification.channelsToAttempt(BOTH, at, POLICY)).containsExactly(Channel.SMS);
			at = notification.getSendAfter();
			assertThat(notification.channelsToAttempt(EnumSet.of(Channel.EMAIL), at, POLICY))
					.containsExactly(Channel.EMAIL);
		}

		notification.recordAttempt(Channel.EMAIL, SendOutcome.transientFailure("TIMEOUT"), at, POLICY);

		assertThat(notification.getDeliveries()).singleElement().satisfies(it -> {
			assertThat(it.getStatus()).isEqualTo(DeliveryStatus.FAILED);
			assertThat(it.getAttempts()).isEqualTo(5);
			assertThat(it.getLastError()).isEqualTo("TIMEOUT");
		});
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
	}

	@Test
	@DisplayName("NT-8 a permanent failure fails the delivery at once; one success makes the notification SENT")
	void mixedOutcomes() {

		var notification = notification();

		notification.recordAttempt(Channel.EMAIL, SendOutcome.permanentFailure("INVALID_ADDRESS"), NOW, POLICY);
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);

		var other = notification();
		other.prepare(Channel.EMAIL);
		other.prepare(Channel.SMS);
		other.recordAttempt(Channel.EMAIL, SendOutcome.permanentFailure("INVALID_ADDRESS"), NOW, POLICY);
		other.recordAttempt(Channel.SMS, SendOutcome.accepted("s-1"), NOW, POLICY);

		assertThat(other.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	@DisplayName("NT-8 while one channel retries the notification stays PENDING; an accepted channel is never retried")
	void independentChannels() {

		var notification = notification();
		notification.prepare(Channel.EMAIL);
		notification.prepare(Channel.SMS);

		notification.recordAttempt(Channel.SMS, SendOutcome.accepted("s-1"), NOW, POLICY);
		assertThat(notification.getStatus()).as("email not attempted yet").isEqualTo(NotificationStatus.PENDING);
		notification.recordAttempt(Channel.EMAIL, SendOutcome.transientFailure("THROTTLED"), NOW, POLICY);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.channelsToAttempt(BOTH, NOW.plus(Duration.ofMinutes(2)), POLICY))
				.containsExactly(Channel.EMAIL);

		notification.recordAttempt(Channel.EMAIL, SendOutcome.accepted("m-1"), NOW.plus(Duration.ofMinutes(2)), POLICY);
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.channelsToAttempt(BOTH, NOW.plus(Duration.ofHours(1)), POLICY)).isEmpty();
	}

	@Test
	@DisplayName("NT-6 a retrying channel the customer no longer offers is given up")
	void abandon() {

		var notification = notification();
		notification.prepare(Channel.EMAIL);
		notification.prepare(Channel.SMS);
		notification.recordAttempt(Channel.SMS, SendOutcome.accepted("s-1"), NOW, POLICY);
		notification.recordAttempt(Channel.EMAIL, SendOutcome.transientFailure("TIMEOUT"), NOW, POLICY);

		notification.abandonChannelsOtherThan(EnumSet.of(Channel.SMS), NOW, POLICY);

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getDeliveries()).filteredOn(it -> it.getChannel() == Channel.EMAIL).singleElement()
				.satisfies(it -> assertThat(it.getLastError()).isEqualTo("CHANNEL_DISABLED"));
	}

	@Test
	@DisplayName("NT-7/NT-12 skipped, cancelled, sent and failed are terminal")
	void terminal() {

		var skipped = notification();
		skipped.skip(StatusReason.TOO_LATE);
		assertThat(skipped.getStatusReason()).isEqualTo(StatusReason.TOO_LATE);

		var cancelled = notification();
		cancelled.cancel();

		for (var done : new Notification[] { skipped, cancelled }) {
			assertThatExceptionOfType(NotificationRuleViolation.class).isThrownBy(done::cancel);
			assertThatExceptionOfType(NotificationRuleViolation.class)
					.isThrownBy(() -> done.recordAttempt(Channel.EMAIL, SendOutcome.accepted("x"), NOW, POLICY));
			assertThat(done.isDue(NOW)).isFalse();
		}
	}

	@Test
	@DisplayName("NT-16 callbacks update a sent delivery to delivered or bounced; unknown IDs change nothing")
	void callbacks() {

		var notification = notification();
		notification.recordAttempt(Channel.EMAIL, SendOutcome.accepted("m-1"), NOW, POLICY);

		assertThat(notification.recordCallback("unknown", DeliveryStatus.DELIVERED)).isFalse();
		assertThat(notification.recordCallback("m-1", DeliveryStatus.BOUNCED)).isTrue();

		assertThat(notification.getDeliveries().getFirst().getStatus()).isEqualTo(DeliveryStatus.BOUNCED);
		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
	}

	static Notification notification() {
		return Notification.create("test:" + Math.random(), CustomerId.random(), AppointmentId.random(),
				NotificationType.CONFIRMATION, NOW, NOW, NOW);
	}
}
