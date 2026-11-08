package com.samdev.appointments.notification.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.samdev.appointments.booking.AppointmentDetails;
import com.samdev.appointments.booking.AppointmentDirectory;
import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.branchmanagement.BranchDirectory;
import com.samdev.appointments.branchmanagement.Address;
import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.GeoLocation;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerContact;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.notification.FakeSenders;
import com.samdev.appointments.notification.application.port.NotificationMetrics;
import com.samdev.appointments.notification.application.port.NotificationSettings;
import com.samdev.appointments.notification.domain.entity.Notification;
import com.samdev.appointments.notification.domain.repository.Notifications;
import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.DeliveryStatus;
import com.samdev.appointments.notification.domain.vo.NotificationId;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;
import com.samdev.appointments.notification.domain.vo.NotificationType;
import com.samdev.appointments.notification.domain.vo.RetryPolicy;
import com.samdev.appointments.notification.domain.vo.SendOutcome;
import com.samdev.appointments.notification.domain.vo.StatusReason;
import com.samdev.appointments.notification.infrastructure.messaging.ClasspathTemplateRenderer;

/**
 * The dispatcher's rules with the other modules' directories stubbed: relevance, channel choice at send time,
 * language, retries, error reduction and isolation between notifications.
 */
class NotificationDispatcherTests {

	static final Instant NOW = Instant.parse("2026-10-13T08:00:00Z");
	static final Instant START = Instant.parse("2026-10-14T07:30:00Z");

	final Map<UUID, Notification> stored = new HashMap<>();
	final ArrayDeque<UUID> due = new ArrayDeque<>();
	final Notifications notifications = mock(Notifications.class);
	final AppointmentDirectory appointments = mock(AppointmentDirectory.class);
	final CustomerDirectory customers = mock(CustomerDirectory.class);
	final BranchDirectory branches = mock(BranchDirectory.class);
	final FakeSenders senders = new FakeSenders();
	final NotificationMetrics metrics = mock(NotificationMetrics.class);
	final MutableClock clock = new MutableClock(NOW);

	NotificationDispatcher dispatcher;

	@BeforeEach
	void setUp() {

		var transactions = mock(PlatformTransactionManager.class);
		when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

		when(notifications.claimNextDue(any(), anyCollection())).thenAnswer(it -> Optional.ofNullable(due.poll()));
		when(notifications.findById(any())).thenAnswer(it -> Optional.ofNullable(
				stored.get(((NotificationId) it.getArgument(0)).value())));
		when(notifications.save(any())).thenAnswer(it -> it.getArgument(0));
		when(branches.branch()).thenReturn(new BranchProfile("MAIN", "Main Branch",
				new Address("1 Main Rd", "Cape Town", "Western Cape", "8001"), new GeoLocation(-33.9, 18.4),
				ZoneId.of("Africa/Johannesburg"), Set.of(CustomerSegment.PERSONAL)));
		when(branches.findService(any())).thenReturn(Optional.empty());

		NotificationSettings settings = new NotificationSettings() {
			public Duration reminderLeadTime() { return Duration.ofHours(24); }
			public int batchSize() { return 100; }
			public RetryPolicy retryPolicy() { return RetryPolicy.DEFAULT; }
			public String manageLink(String reference) { return "https://example.test/" + reference; }
		};

		dispatcher = new NotificationDispatcher(notifications, appointments, customers, branches, senders, senders,
				new ClasspathTemplateRenderer(ClasspathTemplateRenderer.LOCATION, "en"), settings, metrics, clock,
				transactions);
	}

	@Test
	@DisplayName("NT-1/NT-6 a due confirmation goes out on every channel the customer offers now")
	void sendsOnEveryChannel() {

		var notification = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED,
				contact("thandi@example.test", "+27825550101", "en"));

		dispatcher.dispatchDue();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(notification.getDeliveries()).extracting(it -> it.getChannel() + " " + it.getStatus())
				.containsExactly("EMAIL SENT", "SMS SENT");
		assertThat(senders.to("thandi@example.test")).singleElement()
				.satisfies(it -> assertThat(it.subject()).startsWith("Appointment confirmed"));
		assertThat(senders.to("+27825550101")).hasSize(1);
		verify(metrics).attempted(Channel.EMAIL, SendOutcome.Kind.ACCEPTED);
		verify(metrics).attempted(Channel.SMS, SendOutcome.Kind.ACCEPTED);
	}

	@Test
	@DisplayName("NT-6 a channel disabled after the notification was created is not used; the language is the customer's")
	void channelsAndLanguageAtSendTime() {

		var notification = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED,
				contact(null, "+27825550102", "af"));

		dispatcher.dispatchDue();

		assertThat(notification.getDeliveries()).extracting(it -> it.getChannel()).containsExactly(Channel.SMS);
		assertThat(senders.to("+27825550102")).singleElement()
				.satisfies(it -> assertThat(it.body()).startsWith("Bespreek"));
	}

	@Test
	@DisplayName("NT-6 no contact at all is CUSTOMER_UNAVAILABLE; a contact without channels is NO_CHANNEL")
	void noChannel() {

		var unavailable = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED, null);
		var noChannel = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED, contact(null, null, "en"));

		dispatcher.dispatchDue();

		assertThat(unavailable.getStatusReason()).isEqualTo(StatusReason.CUSTOMER_UNAVAILABLE);
		assertThat(noChannel.getStatusReason()).isEqualTo(StatusReason.NO_CHANNEL);
		assertThat(noChannel.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
		verify(metrics).skipped(StatusReason.CUSTOMER_UNAVAILABLE);
		verify(metrics).skipped(StatusReason.NO_CHANNEL);
	}

	@Test
	@DisplayName("NT-7 a message that no longer matches the appointment is skipped and nothing is sent")
	void relevance() {

		var confirmationOfCancelled = queue(NotificationType.CONFIRMATION, AppointmentStatus.CANCELLED,
				contact("a@example.test", null, "en"));
		var cancellation = queue(NotificationType.CANCELLATION, AppointmentStatus.CANCELLED,
				contact("b@example.test", null, "en"));
		var rebookingOfBooked = queue(NotificationType.REBOOKING_REQUIRED, AppointmentStatus.BOOKED,
				contact("c@example.test", null, "en"));
		var lateReminder = queue(NotificationType.REMINDER, AppointmentStatus.BOOKED, contact("d@example.test", null, "en"));
		var gone = queue(NotificationType.CONFIRMATION, null, contact("e@example.test", null, "en"));

		clock.set(START.plusSeconds(60));
		dispatcher.dispatchDue();

		assertThat(confirmationOfCancelled.getStatusReason()).isEqualTo(StatusReason.APPOINTMENT_CHANGED);
		assertThat(cancellation.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(rebookingOfBooked.getStatusReason()).isEqualTo(StatusReason.APPOINTMENT_CHANGED);
		assertThat(lateReminder.getStatusReason()).isEqualTo(StatusReason.TOO_LATE);
		assertThat(gone.getStatusReason()).isEqualTo(StatusReason.APPOINTMENT_CHANGED);
		assertThat(senders.to("a@example.test")).isEmpty();
		assertThat(senders.to("d@example.test")).isEmpty();
	}

	@Test
	@DisplayName("NT-8 a transient failure is retried after the delay; the channel that succeeded is not sent again")
	void retries() {

		var notification = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED,
				contact("retry@example.test", "+27825550103", "en"));
		senders.email(key -> SendOutcome.transientFailure("THROTTLED"));

		dispatcher.dispatchDue();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(notification.getSendAfter()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
		assertThat(senders.to("+27825550103")).hasSize(1);

		senders.reset();
		clock.set(NOW.plus(Duration.ofMinutes(1)));
		due.add(notification.getId().value());
		dispatcher.dispatchDue();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(senders.to("retry@example.test")).hasSize(1);
		assertThat(senders.to("+27825550103")).as("SMS was not sent again").hasSize(1);
	}

	@Test
	@DisplayName("NT-11 a provider error naming the recipient is reduced before it is stored")
	void errorsAreReduced() {

		var notification = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED,
				contact("bounce@example.test", null, "en"));
		senders.email(key -> SendOutcome.permanentFailure("550 Unknown user bounce@example.test"));

		dispatcher.dispatchDue();

		assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
		assertThat(notification.getDeliveries().getFirst().getStatus()).isEqualTo(DeliveryStatus.FAILED);
		assertThat(notification.getDeliveries().getFirst().getLastError()).isEqualTo("550 Unknown user [address]");
	}

	@Test
	@DisplayName("NT-14/NT-15 a failure on one notification does not stop the others; a provider outage is retried")
	void isolation() {

		var broken = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED, contact("x@example.test", null, "en"));
		when(appointments.find(broken.getAppointmentId())).thenThrow(new IllegalStateException("directory down"));
		var fine = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED, contact("y@example.test", null, "en"));
		var outage = queue(NotificationType.CONFIRMATION, AppointmentStatus.BOOKED, contact("z@example.test", null, "en"));
		senders.email(key -> {
			if (key.equals(outage.getDeliveries().isEmpty() ? "" : outage.getDeliveries().getFirst().getId().toString())) {
				throw new IllegalStateException("provider down");
			}
			return SendOutcome.accepted("ok-" + key);
		});

		assertThat(dispatcher.dispatchDue()).isEqualTo(2);

		assertThat(broken.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(fine.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(outage.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(outage.getDeliveries().getFirst().getLastError()).isEqualTo("PROVIDER_ERROR");
	}

	private Notification queue(NotificationType type, @Nullable AppointmentStatus status,
			@Nullable CustomerContact contact) {

		var appointmentId = AppointmentId.random();
		var customerId = contact == null ? CustomerId.random() : contact.customerId();
		var notification = Notification.create(UUID.randomUUID().toString(), customerId, appointmentId,
				type, NOW, NOW, NOW);

		stored.put(notification.getId().value(), notification);
		due.add(notification.getId().value());

		when(appointments.find(appointmentId)).thenReturn(Optional.ofNullable(status == null ? null
				: new AppointmentDetails(appointmentId, "ABCD2345", customerId, ServiceId.random(),
						LocalDate.parse("2026-10-14"), LocalTime.parse("09:30"), LocalTime.parse("10:00"), START,
						START.plus(Duration.ofMinutes(30)), status)));
		when(customers.contactFor(customerId)).thenReturn(Optional.ofNullable(contact));

		return notification;
	}

	private static CustomerContact contact(@Nullable String email, @Nullable String phone, String language) {
		return new CustomerContact(CustomerId.random(), "Thandi Nkosi", language, email, phone);
	}

	static class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void set(Instant now) {
			this.now = now;
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}
}
