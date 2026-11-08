package com.samdev.appointments.booking.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.booking.AppointmentBooked;
import com.samdev.appointments.booking.AppointmentCancelled;
import com.samdev.appointments.booking.AppointmentCheckedIn;
import com.samdev.appointments.booking.AppointmentCompleted;
import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentMarkedNoShow;
import com.samdev.appointments.booking.AppointmentRebookingRequired;
import com.samdev.appointments.booking.AppointmentRescheduled;
import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.BookedBy;
import com.samdev.appointments.booking.ClosedReason;
import com.samdev.appointments.booking.RebookingCause;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.booking.domain.vo.BookingReference;
import com.samdev.appointments.booking.domain.vo.TimeSlot;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerId;

/**
 * Every transition and time rule of the appointment lifecycle. The aggregate never reads a clock.
 */
class AppointmentTests {

	static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
	static final LocalDate DATE = LocalDate.parse("2026-10-14");
	static final Instant DAY_BEFORE = Instant.parse("2026-10-13T08:00:00Z");
	static final Duration CUTOFF = Duration.ofMinutes(60);
	static final Duration GRACE = Duration.ofMinutes(15);

	@Test
	@DisplayName("BK-2 booking creates a BOOKED appointment with its reference and publishes AppointmentBooked")
	void book() {

		var appointment = booked(Actor.CUSTOMER);

		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(appointment.getBookedBy()).isEqualTo(BookedBy.CUSTOMER);
		assertThat(appointment.getSlot().startsAt()).isEqualTo(Instant.parse("2026-10-14T07:00:00Z"));
		assertThat(events(appointment)).singleElement().isInstanceOfSatisfying(AppointmentBooked.class,
				it -> assertThat(it.reference()).isEqualTo(appointment.getReference().value()));
	}

	@Test
	@DisplayName("BK-2 the start must be in the future")
	void startInFuture() {
		assertRejected(RejectionCode.OUTSIDE_BOOKING_WINDOW,
				() -> Appointment.book(AppointmentId.random(), reference(), CustomerId.random(),
						ServiceId.random(), CapacityPoolId.random(), slot("09:00"), Actor.STAFF,
						Instant.parse("2026-10-14T07:00:00Z")));
	}

	@Test
	@DisplayName("BK-5 rescheduling keeps reference and duration and publishes the previous times")
	void reschedule() {

		var appointment = booked(Actor.CUSTOMER);
		var reference = appointment.getReference();

		appointment.reschedule(slot("11:00"), Actor.CUSTOMER, DAY_BEFORE, CUTOFF);

		assertThat(appointment.getReference()).isEqualTo(reference);
		assertThat(appointment.getSlot().startTime()).isEqualTo(LocalTime.parse("11:00"));
		assertThat(events(appointment)).last().isInstanceOfSatisfying(AppointmentRescheduled.class,
				it -> assertThat(it.previousStartsAt()).isEqualTo(Instant.parse("2026-10-14T07:00:00Z")));

		assertThatIllegalArgumentException().isThrownBy(() -> appointment.reschedule(
				TimeSlot.of(DATE, LocalTime.parse("13:00"), Duration.ofMinutes(60), ZONE), Actor.STAFF, DAY_BEFORE,
				CUTOFF));
	}

	@Test
	@DisplayName("BK-5/BK-6 customers cannot change a BOOKED appointment after the cutoff; staff can")
	void cutoff() {

		var lateForCustomer = Instant.parse("2026-10-14T06:30:00Z");

		var customers = booked(Actor.CUSTOMER);
		assertRejected(RejectionCode.PAST_CUTOFF,
				() -> customers.cancel(Actor.CUSTOMER, ClosedReason.CUSTOMER_CANCELLED, lateForCustomer, CUTOFF));
		assertRejected(RejectionCode.PAST_CUTOFF,
				() -> customers.reschedule(slot("11:00"), Actor.CUSTOMER, lateForCustomer, CUTOFF));

		customers.cancel(Actor.STAFF, ClosedReason.STAFF_CANCELLED, lateForCustomer, CUTOFF);
		assertThat(customers.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(customers.getClosedReason()).isEqualTo(ClosedReason.STAFF_CANCELLED);
		assertThat(events(customers)).last().isInstanceOf(AppointmentCancelled.class);
	}

	@Test
	@DisplayName("BK-10 a displaced appointment needs rebooking and can be rescheduled by the customer with no cutoff")
	void displacedIsExemptFromCutoff() {

		var appointment = booked(Actor.CUSTOMER);

		appointment.displace(RebookingCause.BRANCH_UNAVAILABLE, DAY_BEFORE);

		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.REBOOKING_REQUIRED);
		assertThat(appointment.holdsReservation()).isFalse();
		assertThat(events(appointment)).last().isInstanceOfSatisfying(AppointmentRebookingRequired.class,
				it -> assertThat(it.cause()).isEqualTo(RebookingCause.BRANCH_UNAVAILABLE));

		// 08:45 at the branch: within the cutoff of the original 09:00 start
		appointment.reschedule(slot("10:00"), Actor.CUSTOMER, Instant.parse("2026-10-14T06:45:00Z"), CUTOFF);
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
	}

	@Test
	@DisplayName("BK-7 check-in only on the local date; complete only after check-in")
	void checkInAndComplete() {

		var appointment = booked(Actor.STAFF);

		assertRejected(RejectionCode.INVALID_STATE, () -> appointment.complete(DAY_BEFORE));
		assertRejected(RejectionCode.INVALID_STATE, () -> appointment.checkIn(DAY_BEFORE, DATE.minusDays(1)));

		appointment.checkIn(DAY_BEFORE, DATE);
		assertThat(events(appointment)).last().isInstanceOf(AppointmentCheckedIn.class);

		appointment.complete(DAY_BEFORE);
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
		assertThat(appointment.holdsReservation()).as("finishing early does not free the place").isTrue();
		assertThat(events(appointment)).last().isInstanceOf(AppointmentCompleted.class);
	}

	@Test
	@DisplayName("BK-7 a no-show only after start plus the grace period")
	void noShow() {

		var appointment = booked(Actor.STAFF);
		var start = appointment.getSlot().startsAt();

		assertRejected(RejectionCode.INVALID_STATE, () -> appointment.markNoShow(start.plus(Duration.ofMinutes(14)), GRACE));

		appointment.markNoShow(start.plus(GRACE), GRACE);
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
		assertThat(events(appointment)).last().isInstanceOf(AppointmentMarkedNoShow.class);
	}

	@Test
	@DisplayName("BK-7 COMPLETED, NO_SHOW and CANCELLED are terminal")
	void terminalStatuses() {

		var cancelled = booked(Actor.STAFF);
		cancelled.cancel(Actor.STAFF, ClosedReason.STAFF_CANCELLED, DAY_BEFORE, CUTOFF);

		for (Runnable action : List.<Runnable> of(
				() -> cancelled.cancel(Actor.STAFF, ClosedReason.STAFF_CANCELLED, DAY_BEFORE, CUTOFF),
				() -> cancelled.reschedule(slot("10:00"), Actor.STAFF, DAY_BEFORE, CUTOFF),
				() -> cancelled.checkIn(DAY_BEFORE, DATE),
				() -> cancelled.displace(RebookingCause.CAPACITY_REDUCED, DAY_BEFORE))) {
			assertRejected(RejectionCode.INVALID_STATE, action);
		}

		var displaced = booked(Actor.STAFF);
		displaced.displace(RebookingCause.CAPACITY_REDUCED, DAY_BEFORE);
		assertRejected(RejectionCode.INVALID_STATE, () -> displaced.checkIn(DAY_BEFORE, DATE));
		assertRejected(RejectionCode.INVALID_STATE, () -> displaced.markNoShow(Instant.MAX, GRACE));

		displaced.cancel(Actor.SYSTEM, ClosedReason.REBOOKING_EXPIRED, DAY_BEFORE, CUTOFF);
		assertThat(displaced.getClosedReason()).isEqualTo(ClosedReason.REBOOKING_EXPIRED);
	}

	@Test
	@DisplayName("BK-2 a slot never spans midnight")
	void slotNeverSpansMidnight() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> TimeSlot.of(DATE, LocalTime.parse("23:30"), Duration.ofMinutes(45), ZONE));
		assertThat(slot("09:00").localDateOf(Instant.parse("2026-10-13T22:30:00Z"))).isEqualTo(DATE);
	}

	static Appointment booked(Actor actor) {
		return Appointment.book(AppointmentId.random(), reference(), CustomerId.random(),
				ServiceId.random(), CapacityPoolId.random(), slot("09:00"), actor, DAY_BEFORE);
	}

	static TimeSlot slot(String start) {
		return TimeSlot.of(DATE, LocalTime.parse(start), Duration.ofMinutes(30), ZONE);
	}

	static BookingReference reference() {
		return BookingReference.random(new Random());
	}

	static void assertRejected(RejectionCode code, Runnable action) {
		assertThatExceptionOfType(BookingRejected.class).isThrownBy(action::run)
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(code));
	}

	static List<Object> events(Appointment appointment) {
		return PoolDayScheduleTests.events(appointment);
	}
}
