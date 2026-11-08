package com.samdev.appointments.notification;

import static com.samdev.appointments.booking.BookingFixtures.tomorrowAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.booking.AppointmentBooked;
import com.samdev.appointments.booking.AppointmentCancelled;
import com.samdev.appointments.booking.AppointmentCheckedIn;
import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentRebookingRequired;
import com.samdev.appointments.booking.AppointmentRescheduled;
import com.samdev.appointments.booking.BookedBy;
import com.samdev.appointments.booking.BookingFixtures;
import com.samdev.appointments.booking.BookingFixtures.OpenService;
import com.samdev.appointments.booking.ClosedReason;
import com.samdev.appointments.booking.RebookingCause;
import com.samdev.appointments.booking.application.command.BookAppointment;
import com.samdev.appointments.booking.application.command.CancelAppointment;
import com.samdev.appointments.booking.application.command.RescheduleAppointment;
import com.samdev.appointments.booking.application.handler.AppointmentCommandHandler;
import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.customer.CustomerErased;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.notification.application.handler.NotificationCommandHandler;
import com.samdev.appointments.notification.application.handler.NotificationDispatcher;
import com.samdev.appointments.notification.application.query.NotificationQueries;
import com.samdev.appointments.notification.domain.entity.Notification;
import com.samdev.appointments.notification.domain.vo.DeliveryStatus;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;
import com.samdev.appointments.notification.domain.vo.NotificationType;
import com.samdev.appointments.notification.infrastructure.persistence.NotificationRetention;

/**
 * Event reactions, the dispatcher and the database, with Booking, Customer and Branch management running for real.
 * Reactions to synthetic events are checked directly; end-to-end flows go through real bookings.
 */
@IntegrationTest
class NotificationModuleTests {

	static final Duration WAIT = Duration.ofSeconds(15);

	@Autowired NotificationCommandHandler handler;
	@Autowired NotificationDispatcher dispatcher;
	@Autowired NotificationQueries queries;
	@Autowired NotificationRetention retention;
	@Autowired FakeSenders senders;
	@Autowired BookingFixtures booking;
	@Autowired AppointmentCommandHandler appointments;
	@Autowired CustomerDirectory customers;
	@Autowired JdbcTemplate jdbc;

	@AfterEach
	void reset() {
		senders.reset();
		jdbc.execute("DROP TRIGGER IF EXISTS fail_reminder ON notification.notification");
	}

	// NT-1 to NT-5, NT-9, NT-10 with synthetic events

	@Test
	@DisplayName("NT-1/NT-2 a booking creates a confirmation due now and a reminder at start minus 24 hours")
	void booked() {

		var event = booked(Instant.now().plus(Duration.ofDays(3)));

		handler.handle(event);

		var created = queries.forAppointment(event.appointmentId());
		assertThat(created).extracting(Notification::getType)
				.containsExactly(NotificationType.CONFIRMATION, NotificationType.REMINDER);
		assertThat(created.get(1).getSendAfter())
				.isCloseTo(event.startsAt().minus(Duration.ofHours(24)), within(1, ChronoUnit.MICROS));
		assertThat(created).allMatch(it -> it.getStatus() == NotificationStatus.PENDING);
	}

	@Test
	@DisplayName("NT-2 an appointment starting sooner than the lead time gets no reminder")
	void noReminderWhenSoon() {

		var event = booked(Instant.now().plus(Duration.ofHours(5)));

		handler.handle(event);

		assertThat(queries.forAppointment(event.appointmentId())).extracting(Notification::getType)
				.containsExactly(NotificationType.CONFIRMATION);
	}

	@Test
	@DisplayName("NT-9 handling the same event twice creates nothing more")
	void idempotent() {

		var event = booked(Instant.now().plus(Duration.ofDays(3)));

		handler.handle(event);
		handler.handle(event);

		assertThat(queries.forAppointment(event.appointmentId())).hasSize(2);
	}

	@Test
	@DisplayName("NT-9 a failure while handling an event leaves nothing behind")
	void atomic() {

		jdbc.execute("""
				CREATE OR REPLACE FUNCTION notification.fail_reminder() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'storage unavailable'; END; $$;
				CREATE TRIGGER fail_reminder BEFORE INSERT ON notification.notification
				FOR EACH ROW WHEN (NEW.type = 'REMINDER') EXECUTE FUNCTION notification.fail_reminder();
				""");

		var event = booked(Instant.now().plus(Duration.ofDays(3)));

		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> handler.handle(event));
		assertThat(queries.forAppointment(event.appointmentId())).isEmpty();
	}

	@Test
	@DisplayName("NT-3 rescheduling cancels pending messages, queues RESCHEDULED and a new reminder")
	void rescheduled() {

		var first = booked(Instant.now().plus(Duration.ofDays(3)));
		handler.handle(first);

		var newStart = first.startsAt().plus(Duration.ofDays(1));
		var later = Instant.now().plusSeconds(1);
		handler.handle(new AppointmentRescheduled(first.appointmentId(), "ABCD2345", first.customerId(),
				first.serviceId(), newStart, newStart.plus(Duration.ofMinutes(30)), first.startsAt(),
				first.endsAt(), later));

		var all = queries.forAppointment(first.appointmentId());
		assertThat(all).extracting(it -> it.getType() + " " + it.getStatus()).containsExactly(
				"CONFIRMATION CANCELLED", "REMINDER CANCELLED", "RESCHEDULED PENDING", "REMINDER PENDING");
		assertThat(all.get(3).getSendAfter()).isCloseTo(newStart.minus(Duration.ofHours(24)), within(1, ChronoUnit.MICROS));

		handler.handle(first);
		assertThat(queries.forAppointment(first.appointmentId())).as("a late older event is stale").hasSize(4);
	}

	@Test
	@DisplayName("NT-4/NT-5 cancellation and rebooking supersede pending messages; erasure cancels without a message")
	void cancelledAndRebooking() {

		var booked = booked(Instant.now().plus(Duration.ofDays(3)));
		handler.handle(booked);
		handler.handle(new AppointmentRebookingRequired(booked.appointmentId(), "ABCD2345", booked.customerId(),
				booked.serviceId(), booked.startsAt(), booked.endsAt(),
				RebookingCause.BRANCH_UNAVAILABLE, Instant.now().plusSeconds(1)));
		handler.handle(cancelled(booked, ClosedReason.STAFF_CANCELLED, Instant.now().plusSeconds(2)));

		assertThat(queries.forAppointment(booked.appointmentId())).extracting(it -> it.getType() + " " + it.getStatus())
				.containsExactly("CONFIRMATION CANCELLED", "REMINDER CANCELLED", "REBOOKING_REQUIRED CANCELLED",
						"CANCELLATION PENDING");

		var erased = booked(Instant.now().plus(Duration.ofDays(3)));
		handler.handle(erased);
		handler.handle(cancelled(erased, ClosedReason.CUSTOMER_ERASED, Instant.now().plusSeconds(1)));

		assertThat(queries.forAppointment(erased.appointmentId())).extracting(Notification::getStatus)
				.containsOnly(NotificationStatus.CANCELLED).hasSize(2);
	}

	@Test
	@DisplayName("NT-10 an erased customer's pending notifications are cancelled; check-in cancels the reminder only")
	void customerErasedAndCheckIn() {

		var booked = booked(Instant.now().plus(Duration.ofDays(3)));
		var other = booked(Instant.now().plus(Duration.ofDays(4)));
		handler.handle(booked);
		handler.handle(other);

		handler.handle(new AppointmentCheckedIn(other.appointmentId(), "ABCD2345", other.customerId(),
				other.serviceId(), other.startsAt(), other.endsAt(), Instant.now()));
		assertThat(queries.forAppointment(other.appointmentId())).extracting(it -> it.getType() + " " + it.getStatus())
				.containsExactly("CONFIRMATION PENDING", "REMINDER CANCELLED");

		handler.handle(new CustomerErased(booked.customerId(), Instant.now()));
		assertThat(queries.forAppointment(booked.appointmentId())).extracting(Notification::getStatus)
				.containsOnly(NotificationStatus.CANCELLED);
	}

	// End to end through Booking

	@Test
	@DisplayName("NT-1/NT-3 a real booking is confirmed by email; after rescheduling the message shows the new time")
	void endToEnd() {

		var branch = booking.openService(2);
		var customer = booking.customer();
		var email = customers.contactFor(customer).orElseThrow().email();

		var id = book(branch, customer, "09:00");
		await().atMost(WAIT).until(() -> has(id, NotificationType.CONFIRMATION));

		dispatcher.dispatchDue();
		assertThat(status(id, NotificationType.CONFIRMATION)).isEqualTo(NotificationStatus.SENT);
		assertThat(senders.to(email)).singleElement().satisfies(it -> {
			assertThat(it.subject()).startsWith("Appointment confirmed");
			assertThat(it.body()).contains("Hello Test Customer").contains("09:00");
		});

		appointments.handle(new RescheduleAppointment(id, null, tomorrowAt("11:30"), Actor.CUSTOMER));
		await().atMost(WAIT).until(() -> has(id, NotificationType.RESCHEDULED));

		dispatcher.dispatchDue();
		assertThat(senders.to(email)).hasSize(2).last().satisfies(it -> {
			assertThat(it.subject()).startsWith("Appointment moved");
			assertThat(it.body()).contains("11:30");
		});
	}

	@Test
	@DisplayName("NT-7 a confirmation whose appointment was cancelled before sending is skipped")
	void skippedWhenChanged() {

		var branch = booking.openService(2);
		var customer = booking.customer();
		var id = book(branch, customer, "10:00");
		await().atMost(WAIT).until(() -> has(id, NotificationType.CONFIRMATION));

		// Cancelled after the confirmation was queued, before the cancellation event is handled
		jdbc.update("UPDATE booking.appointment SET status = 'CANCELLED', closed_reason = 'STAFF_CANCELLED' WHERE id = ?",
				id.value());
		dispatcher.dispatchDue();

		var confirmation = queries.forAppointment(id).getFirst();
		assertThat(confirmation.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
		assertThat(confirmation.getStatusReason()).hasToString("APPOINTMENT_CHANGED");
	}

	@Test
	@DisplayName("NT-14 two dispatchers running at once send each due notification exactly once")
	void concurrentDispatchers() throws Exception {

		var branch = booking.openService(10);
		var emails = new ArrayList<String>();
		var ids = new ArrayList<AppointmentId>();
		for (var i = 0; i < 6; i++) {
			var customer = booking.customer();
			emails.add(customers.contactFor(customer).orElseThrow().email());
			ids.add(book(branch, customer, "13:00"));
		}
		await().atMost(WAIT).until(() -> ids.stream().allMatch(it -> has(it, NotificationType.CONFIRMATION)));

		try (var executor = Executors.newFixedThreadPool(2)) {
			executor.invokeAll(List.<Callable<Integer>> of(dispatcher::dispatchDue, dispatcher::dispatchDue));
		}

		for (var i = 0; i < ids.size(); i++) {
			assertThat(status(ids.get(i), NotificationType.CONFIRMATION)).isEqualTo(NotificationStatus.SENT);
			assertThat(senders.to(emails.get(i))).hasSize(1);
		}
	}

	@Test
	@DisplayName("NT-15 with the email provider down, booking and cancelling work and messages wait for a retry")
	void providerOutage() {

		senders.email(key -> {
			throw new IllegalStateException("provider down");
		});

		var branch = booking.openService(2);
		var id = book(branch, booking.customer(), "15:00");
		appointments.handle(new CancelAppointment(id, null, Actor.CUSTOMER, null));
		await().atMost(WAIT).until(() -> has(id, NotificationType.CANCELLATION));

		dispatcher.dispatchDue();

		var cancellation = queries.forAppointment(id).stream()
				.filter(it -> it.getType() == NotificationType.CANCELLATION).findFirst().orElseThrow();
		assertThat(cancellation.getStatus()).isEqualTo(NotificationStatus.PENDING);
		assertThat(cancellation.getDeliveries()).singleElement().satisfies(it -> {
			assertThat(it.getStatus()).isEqualTo(DeliveryStatus.RETRYING);
			assertThat(it.getLastError()).isEqualTo("PROVIDER_ERROR");
		});
	}

	// NT-11, retention

	@Test
	@DisplayName("NT-11 no column in the notification schema can hold a name, address, number or message text")
	void noPersonalDataColumns() {

		var columns = jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns WHERE table_schema = 'notification'
				""", String.class);

		assertThat(columns).isNotEmpty().noneMatch(it -> it.matches(".*(name|email|mail|phone|address|body|subject|text).*"));
	}

	@Test
	@DisplayName("Retention: notifications older than the retention period are deleted with their deliveries")
	void retention() {

		var event = booked(Instant.now().plus(Duration.ofDays(3)));
		handler.handle(event);
		jdbc.update("UPDATE notification.notification SET created_at = ? WHERE appointment_id = ?",
				Timestamp.from(Instant.now().minus(400, ChronoUnit.DAYS)), event.appointmentId().value());

		assertThat(retention.purgeExpired()).isGreaterThanOrEqualTo(2);
		assertThat(queries.forAppointment(event.appointmentId())).isEmpty();
	}

	private AppointmentId book(OpenService branch, CustomerId customer, String time) {
		return appointments.handle(new BookAppointment(customer, branch.serviceId(), branch.at(time), Actor.STAFF,
				null)).appointmentId();
	}

	private boolean has(AppointmentId id, NotificationType type) {
		return queries.forAppointment(id).stream().anyMatch(it -> it.getType() == type);
	}

	private NotificationStatus status(AppointmentId id, NotificationType type) {
		return queries.forAppointment(id).stream().filter(it -> it.getType() == type).findFirst().orElseThrow()
				.getStatus();
	}

	static AppointmentBooked booked(Instant startsAt) {
		return new AppointmentBooked(AppointmentId.random(), "ABCD2345", CustomerId.random(),
				ServiceId.random(), startsAt, startsAt.plus(Duration.ofMinutes(30)), BookedBy.CUSTOMER,
				Instant.now().truncatedTo(ChronoUnit.MICROS));
	}

	static AppointmentCancelled cancelled(AppointmentBooked booked, ClosedReason reason, Instant at) {
		return new AppointmentCancelled(booked.appointmentId(), "ABCD2345", booked.customerId(),
				booked.serviceId(), booked.startsAt(), booked.endsAt(), reason, at);
	}
}
