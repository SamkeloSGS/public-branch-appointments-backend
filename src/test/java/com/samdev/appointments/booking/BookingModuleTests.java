package com.samdev.appointments.booking;

import static com.samdev.appointments.booking.BookingFixtures.tomorrow;
import static com.samdev.appointments.booking.BookingFixtures.tomorrowAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.samdev.appointments.IntegrationTest;
import com.samdev.appointments.booking.BookingFixtures.OpenService;
import com.samdev.appointments.booking.application.command.BookAppointment;
import com.samdev.appointments.booking.application.command.CancelAppointment;
import com.samdev.appointments.booking.application.command.CheckInAppointment;
import com.samdev.appointments.booking.application.command.CompleteAppointment;
import com.samdev.appointments.booking.application.command.DisplaceAppointment;
import com.samdev.appointments.booking.application.command.MarkNoShow;
import com.samdev.appointments.booking.application.command.RescheduleAppointment;
import com.samdev.appointments.booking.application.handler.AppointmentCommandHandler;
import com.samdev.appointments.booking.application.handler.AppointmentStatusHandler;
import com.samdev.appointments.booking.application.handler.HousekeepingHandler;
import com.samdev.appointments.booking.application.port.ProjectionUpdates;
import com.samdev.appointments.booking.application.port.ProjectionUpdates.Result;
import com.samdev.appointments.booking.application.port.ScheduleProjection;
import com.samdev.appointments.booking.application.query.AppointmentQueries;
import com.samdev.appointments.booking.application.query.AvailabilityQueries;
import com.samdev.appointments.booking.application.query.ImpactPreview;
import com.samdev.appointments.booking.application.query.ImpactPreview.CalendarExceptionChange;
import com.samdev.appointments.booking.application.query.ImpactPreview.CapacityOverrideChange;
import com.samdev.appointments.booking.application.support.ProjectionRebuild;
import com.samdev.appointments.booking.domain.entity.PoolDaySchedule;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.repository.PoolDaySchedules;
import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.branchmanagement.CapacityOverrideId;
import com.samdev.appointments.branchmanagement.ModuleFixtures;
import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.OverrideReason;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.branchmanagement.application.command.AddCalendarException;
import com.samdev.appointments.branchmanagement.application.command.ChangePoolBaseCapacity;
import com.samdev.appointments.branchmanagement.application.command.RemoveCalendarException;
import com.samdev.appointments.branchmanagement.application.command.SetCapacityOverride;
import com.samdev.appointments.branchmanagement.application.handler.CalendarCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.CapacityPoolCommandHandler;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.application.command.DeactivateCustomer;
import com.samdev.appointments.customer.application.command.EraseCustomer;
import com.samdev.appointments.customer.application.handler.CustomerCommandHandler;
import com.samdev.appointments.identity.Subject;

/**
 * Commands, policies, concurrency and reactions to other modules against PostgreSQL, with Branch management and
 * Customer running for real. Events from asynchronous listeners are awaited.
 */
@IntegrationTest
class BookingModuleTests {

	static final Subject STAFF = new Subject("booking-module-tests");
	static final Duration WAIT = Duration.ofSeconds(15);

	@Autowired BookingFixtures fixtures;
	@Autowired CommittedEvents events;
	@Autowired AppointmentCommandHandler commands;
	@Autowired AppointmentStatusHandler statuses;
	@Autowired HousekeepingHandler housekeeping;
	@Autowired AppointmentQueries queries;
	@Autowired AvailabilityQueries availability;
	@Autowired ImpactPreview preview;
	@Autowired PoolDaySchedules schedules;
	@Autowired ScheduleProjection projection;
	@Autowired ProjectionUpdates updates;
	@Autowired ProjectionRebuild rebuild;
	@Autowired AppointmentDirectory directory;
	@Autowired CustomerCommandHandler customers;
	@Autowired CustomerDirectory customerDirectory;
	@Autowired CapacityPoolCommandHandler pools;
	@Autowired CalendarCommandHandler calendar;
	@Autowired JdbcTemplate jdbc;
	@Autowired MeterRegistry metrics;

	// BK-2, BK-15, BK-17

	@Test
	@DisplayName("BK-2/BK-15 booking creates a BOOKED appointment with the offering's duration, a reservation, and one event")
	void books() {

		var branch = fixtures.openService(2);
		var customer = fixtures.customer();

		var id = book(branch, customer, "09:00", Actor.CUSTOMER);

		var appointment = queries.get(id);
		assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(appointment.getSlot().duration()).isEqualTo(Duration.ofMinutes(30));
		assertThat(appointment.getReference().value()).hasSize(8);
		assertThat(schedule(branch).holds(id)).isTrue();
		assertThat(events.of(AppointmentBooked.class, it -> it.appointmentId().equals(id))).singleElement()
				.satisfies(it -> assertThat(it.bookedBy()).isEqualTo(BookedBy.CUSTOMER));

		assertThat(directory.find(id)).hasValueSatisfying(it -> {
			assertThat(it.reference()).isEqualTo(appointment.getReference().value());
			assertThat(it.status()).isEqualTo(AppointmentStatus.BOOKED);
		});
	}

	@Test
	@DisplayName("BK-17 the same idempotency key and customer returns the first appointment; another customer books anew")
	void idempotency() {

		var branch = fixtures.openService(5);
		var customer = fixtures.customer();
		var command = new BookAppointment(customer, branch.serviceId(), tomorrowAt("10:00"),
				Actor.CUSTOMER, "key-1");

		var first = commands.handle(command);
		var repeat = commands.handle(command);

		assertThat(first.created()).isTrue();
		assertThat(repeat.created()).isFalse();
		assertThat(repeat.appointmentId()).isEqualTo(first.appointmentId());
		assertThat(schedule(branch).getReservations()).hasSize(1);

		var other = commands.handle(new BookAppointment(fixtures.customer(), branch.serviceId(),
				tomorrowAt("10:00"), Actor.CUSTOMER, "key-1"));
		assertThat(other.created()).isTrue();
		assertThat(other.appointmentId()).isNotEqualTo(first.appointmentId());
	}

	// OPS-2

	@Test
	@DisplayName("OPS-2 booking outcomes are counted: new appointments by actor, rejections by operation and code")
	void countsOutcomes() {

		var branch = fixtures.openService(1);
		var customer = fixtures.customer();
		var booked = count("booking.appointments.booked", "actor", "customer");
		var completed = count("booking.commands.completed", "operation", "book");
		var rejected = count("booking.commands.rejected", "code", "SLOT_UNAVAILABLE");
		var command = new BookAppointment(customer, branch.serviceId(), tomorrowAt("09:00"),
				Actor.CUSTOMER, "metrics-key");

		commands.handle(command);
		commands.handle(command);
		assertRejected(RejectionCode.SLOT_UNAVAILABLE, () -> book(branch, fixtures.customer(), "09:00", Actor.CUSTOMER));

		assertThat(count("booking.appointments.booked", "actor", "customer")).as("a repeated key is not a new booking")
				.isEqualTo(booked + 1);
		assertThat(count("booking.commands.completed", "operation", "book")).isEqualTo(completed + 2);
		assertThat(count("booking.commands.rejected", "code", "SLOT_UNAVAILABLE")).isEqualTo(rejected + 1);
	}

	private double count(String name, String tag, String value) {
		return metrics.find(name).tag(tag, value).counters().stream().mapToDouble(Counter::count).sum();
	}

	// BK-3

	@Test
	@DisplayName("BK-3 concurrent bookings for the last place: exactly one succeeds, the others get SLOT_UNAVAILABLE")
	void lastPlace() throws Exception {

		var branch = fixtures.openService(1);
		var tasks = new ArrayList<Callable<AppointmentId>>();
		for (var i = 0; i < 4; i++) {
			var customer = fixtures.customer();
			tasks.add(() -> book(branch, customer, "11:00", Actor.CUSTOMER));
		}

		var succeeded = 0;
		var codes = new ArrayList<RejectionCode>();

		try (var executor = Executors.newFixedThreadPool(4)) {
			for (var result : executor.invokeAll(tasks)) {
				try {
					result.get();
					succeeded++;
				} catch (Exception e) {
					codes.add(e.getCause() instanceof BookingRejected rejected ? rejected.getCode() : null);
				}
			}
		}

		assertThat(succeeded).isEqualTo(1);
		assertThat(codes).hasSize(3).containsOnly(RejectionCode.SLOT_UNAVAILABLE);
		assertThat(schedule(branch).loadAt(LocalTime.parse("11:00"))).isEqualTo(1);
	}

	@Test
	@DisplayName("BK-3/BK-4 one customer booking overlapping times in two pools at once: the database lets only one through")
	void customerOverlapAcrossPools() throws Exception {

		var branch = fixtures.openService(3);
		var other = fixtures.secondPool(branch, 3, 60);
		var customer = fixtures.customer();

		var outcomes = new ArrayList<Object>();
		try (var executor = Executors.newFixedThreadPool(2)) {
			for (var result : executor.invokeAll(List.<Callable<AppointmentId>> of(
					() -> book(branch, customer, "13:00", Actor.STAFF),
					() -> book(other, customer, "13:00", Actor.STAFF)))) {
				try {
					outcomes.add(result.get());
				} catch (Exception e) {
					outcomes.add(e.getCause() instanceof BookingRejected rejected ? rejected.getCode() : e.getCause());
				}
			}
		}

		assertThat(outcomes).filteredOn(AppointmentId.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(RejectionCode.class::isInstance)
				.containsExactly(RejectionCode.OVERLAPPING_APPOINTMENT);
	}

	// BK-4

	@Test
	@DisplayName("BK-4 each policy check rejects with its own code and creates nothing")
	void policy() {

		var branch = fixtures.openService(5);
		var customer = fixtures.customer();

		assertRejected(RejectionCode.OFFERING_UNAVAILABLE, () -> commands.handle(new BookAppointment(customer,
				ServiceId.random(), tomorrowAt("09:00"), Actor.CUSTOMER, null)));
		assertRejected(RejectionCode.OUTSIDE_BOOKING_WINDOW, () -> book(branch, customer, "09:10", Actor.CUSTOMER));
		assertRejected(RejectionCode.SLOT_UNAVAILABLE, () -> book(branch, customer, "18:00", Actor.CUSTOMER));
		assertRejected(RejectionCode.OUTSIDE_BOOKING_WINDOW, () -> commands.handle(new BookAppointment(customer,
				branch.serviceId(), tomorrow().plusDays(BookingFixtures.MAX_ADVANCE_DAYS + 10).atTime(9, 0),
				Actor.CUSTOMER, null)));

		book(branch, customer, "09:00", Actor.CUSTOMER);
		assertRejected(RejectionCode.OVERLAPPING_APPOINTMENT, () -> book(branch, customer, "09:00", Actor.CUSTOMER));

		book(branch, customer, "10:00", Actor.CUSTOMER);
		book(branch, customer, "11:00", Actor.CUSTOMER);
		assertRejected(RejectionCode.TOO_MANY_APPOINTMENTS, () -> book(branch, customer, "12:00", Actor.CUSTOMER));
		assertThat(book(branch, customer, "12:00", Actor.STAFF)).as("staff skip the limit").isNotNull();

		var inactive = fixtures.customer();
		customers.handle(new DeactivateCustomer(STAFF, inactive, null));
		assertRejected(RejectionCode.CUSTOMER_INACTIVE, () -> book(branch, inactive, "14:00", Actor.STAFF));

		assertThat(schedule(branch).getReservations()).hasSize(4);
	}

	@Test
	@DisplayName("BK-4 staff skip the minimum lead time; customers do not")
	void staffSkipLeadTime() {

		var branch = fixtures.openService(2, Duration.ofDays(3));
		var customer = fixtures.customer();

		assertRejected(RejectionCode.OUTSIDE_BOOKING_WINDOW, () -> book(branch, customer, "09:00", Actor.CUSTOMER));
		assertThat(book(branch, customer, "09:00", Actor.STAFF)).isNotNull();
	}

	// BK-5, BK-6, BK-7

	@Test
	@DisplayName("BK-5 rescheduling moves the reservation and keeps the reference; an unavailable time changes nothing")
	void reschedule() {

		var branch = fixtures.openService(1);
		var customer = fixtures.customer();
		var id = book(branch, customer, "09:00", Actor.CUSTOMER);
		var reference = queries.get(id).getReference();
		var blocker = book(branch, fixtures.customer(), "15:00", Actor.CUSTOMER);

		commands.handle(new RescheduleAppointment(id, null, tomorrowAt("10:00"), Actor.CUSTOMER));

		var moved = queries.get(id);
		assertThat(moved.getReference()).isEqualTo(reference);
		assertThat(moved.getSlot().startTime()).hasToString("10:00");
		assertThat(schedule(branch).reservationOf(id)).hasValueSatisfying(
				it -> assertThat(it.fromTime()).hasToString("10:00"));
		assertThat(events.of(AppointmentRescheduled.class, it -> it.appointmentId().equals(id))).singleElement()
				.satisfies(it -> assertThat(it.previousStartsAt()).isEqualTo(tomorrowAt("09:00").atZone(BookingFixtures.ZONE).toInstant()));

		assertRejected(RejectionCode.SLOT_UNAVAILABLE,
				() -> commands.handle(new RescheduleAppointment(id, null, tomorrowAt("15:00"), Actor.CUSTOMER)));
		assertThat(schedule(branch).reservationOf(id).orElseThrow().fromTime()).hasToString("10:00");
		assertThat(schedule(branch).holds(blocker)).isTrue();

		commands.handle(new RescheduleAppointment(id, null, tomorrow().plusDays(1).atTime(9, 0), Actor.STAFF));
		assertThat(schedule(branch).holds(id)).as("moved to another day").isFalse();
	}

	@Test
	@DisplayName("BK-6 cancelling releases the place immediately and publishes the reason")
	void cancel() {

		var branch = fixtures.openService(1);
		var id = book(branch, fixtures.customer(), "09:00", Actor.CUSTOMER);

		commands.handle(new CancelAppointment(id, null, Actor.CUSTOMER, null));

		assertThat(queries.get(id).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(events.of(AppointmentCancelled.class, it -> it.appointmentId().equals(id))).singleElement()
				.satisfies(it -> assertThat(it.closedReason()).isEqualTo(ClosedReason.CUSTOMER_CANCELLED));
		assertThat(book(branch, fixtures.customer(), "09:00", Actor.CUSTOMER)).isNotNull();
	}

	@Test
	@DisplayName("BK-7 check-in on the day, complete, and the no-show sweep, each publishing its event once")
	void statusActions() {

		var branch = fixtures.openService(3);
		var first = book(branch, fixtures.customer(), "09:00", Actor.STAFF);
		var second = book(branch, fixtures.customer(), "09:00", Actor.STAFF);

		assertRejected(RejectionCode.INVALID_STATE, () -> statuses.handle(new CheckInAppointment(first, null)));
		assertRejected(RejectionCode.INVALID_STATE,
				() -> statuses.handle(new MarkNoShow(first, null, Actor.STAFF)));

		// Move the first to today, so it can be checked in; the second into the past
		var today = LocalDate.now(BookingFixtures.ZONE);
		var startsAt = today.atTime(9, 0).atZone(BookingFixtures.ZONE).toInstant();
		jdbc.update("UPDATE booking.appointment SET local_date = ?, starts_at = ?, ends_at = ? WHERE id = ?", today,
				Timestamp.from(startsAt), Timestamp.from(startsAt.plus(Duration.ofMinutes(30))), first.value());
		movePast(second, Duration.ofHours(2));

		statuses.handle(new CheckInAppointment(first, null));
		statuses.handle(new CompleteAppointment(first, null));

		assertThat(housekeeping.sweepNoShows()).isGreaterThanOrEqualTo(1);
		assertThat(queries.get(second).getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
		assertThat(queries.get(first).getStatus()).isEqualTo(AppointmentStatus.COMPLETED);

		assertThat(events.of(AppointmentCheckedIn.class, it -> it.appointmentId().equals(first))).hasSize(1);
		assertThat(events.of(AppointmentCompleted.class, it -> it.appointmentId().equals(first))).hasSize(1);
		assertThat(events.of(AppointmentMarkedNoShow.class, it -> it.appointmentId().equals(second))).hasSize(1);
		assertThat(schedule(branch).getReservations()).as("finished appointments keep their place").hasSize(2);
	}

	// BK-8

	@Test
	@DisplayName("BK-8 projection updates are idempotent, older events are ignored, and a rebuild matches the source")
	void projection() {

		var branch = fixtures.openService(2);
		var poolId = branch.poolId();
		var later = Instant.now().plusSeconds(60);

		assertThat(updates.pool(poolId, 7, later)).isEqualTo(Result.APPLIED);
		assertThat(updates.pool(poolId, 7, later)).isEqualTo(Result.IGNORED);
		assertThat(updates.pool(poolId, 1, later.minusSeconds(30))).isEqualTo(Result.IGNORED);
		assertThat(projection.inputs(poolId, tomorrow()).baseCapacity()).isEqualTo(7);

		assertThat(updates.overrideRemoved(CapacityOverrideId.random(), later)).isEqualTo(Result.MISSING);

		rebuild.rebuild();
		assertThat(projection.isBuilt()).isTrue();
		assertThat(projection.inputs(poolId, tomorrow()).baseCapacity()).isEqualTo(2);
		assertThat(updates.pool(poolId, 9, later.minusSeconds(3600)))
				.as("older than the rebuild").isEqualTo(Result.IGNORED);
	}

	@Test
	@DisplayName("BK-8 Branch management events reach the projection")
	void projectionFollowsEvents() {

		var branch = fixtures.openService(2).on(ModuleFixtures.uniqueDate());

		pools.handle(new ChangePoolBaseCapacity(branch.poolId(), null, 4));
		await().atMost(WAIT).until(() -> projection.inputs(branch.poolId(), branch.date()).baseCapacity() == 4);

		calendar.handle(new AddCalendarException(branch.date(), ExceptionType.SPECIAL_HOURS,
				List.of(TimeRange.of("10:00", "12:00")), null));
		await().atMost(WAIT).until(() -> projection.inputs(branch.poolId(), branch.date())
				.hours().equals(List.of(TimeRange.of("10:00", "12:00"))));
		assertThat(availability.openTimes(branch.serviceId(), branch.date()))
				.extracting(Object::toString).containsExactly("10:00", "10:30", "11:00", "11:30");
	}

	@Test
	@DisplayName("BK-1 open days read a whole range at once and agree with each day's open times")
	void openDaysOverARange() {

		var branch = fixtures.openService(1);
		var booked = branch.on(ModuleFixtures.uniqueDate());
		var closed = ModuleFixtures.uniqueDate();
		var special = ModuleFixtures.uniqueDate();
		var overridden = ModuleFixtures.uniqueDate();

		book(booked, fixtures.customer(), "09:00", Actor.CUSTOMER);
		calendar.handle(new AddCalendarException(closed, ExceptionType.CLOSED, List.of(), null));
		calendar.handle(new AddCalendarException(special, ExceptionType.SPECIAL_HOURS,
				List.of(TimeRange.of("10:00", "12:00")), null));
		calendar.handle(new SetCapacityOverride(branch.poolId(), overridden, TimeRange.of("09:00", "10:00"), 3,
				OverrideReason.SHORT_STAFFED, null));
		await().atMost(WAIT).until(() -> projection.inputs(branch.poolId(), closed).closed()
				&& projection.inputs(branch.poolId(), special).hasException()
				&& !projection.inputs(branch.poolId(), overridden).windows().isEmpty());

		var from = booked.date();
		var to = from.plusDays(30);
		var range = projection.inputs(branch.poolId(), from, to);

		assertThat(range).hasSize(31);
		assertThat(range.get(closed).closed()).isTrue();
		assertThat(range.get(special).hours()).containsExactly(TimeRange.of("10:00", "12:00"));
		assertThat(range.get(overridden).windows()).singleElement()
				.satisfies(it -> assertThat(it.capacity()).isEqualTo(3));
		assertThat(range.get(booked.date()).hasException()).isFalse();
		assertThat(range.get(booked.date()).baseCapacity()).isEqualTo(1);

		var expected = from.datesUntil(to.plusDays(1))
				.filter(date -> !availability.openTimes(branch.serviceId(), date).isEmpty())
				.toList();
		assertThat(availability.openDays(branch.serviceId(), from, to)).isEqualTo(expected).doesNotContain(closed)
				.contains(booked.date(), special, overridden);
	}

	// BK-9, BK-10, BK-11

	@Test
	@DisplayName("BK-9/BK-11 reduced capacity overbooks without changing appointments; a manager displaces one to resolve it")
	void overbooking() {

		var branch = fixtures.openService(2);
		var first = book(branch, fixtures.customer(), "09:00", Actor.CUSTOMER);
		var second = book(branch, fixtures.customer(), "09:00", Actor.CUSTOMER);
		var elsewhere = book(branch, fixtures.customer(), "14:00", Actor.CUSTOMER);

		pools.handle(new ChangePoolBaseCapacity(branch.poolId(), null, 1));
		await().atMost(WAIT).until(() -> schedule(branch).isOverbooked());

		assertThat(queries.get(first).getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(queries.get(second).getStatus()).isEqualTo(AppointmentStatus.BOOKED);
		assertThat(events.of(PoolDayOverbooked.class, it -> it.capacityPoolId().equals(branch.poolId()))).hasSize(1);
		assertRejected(RejectionCode.SLOT_UNAVAILABLE, () -> book(branch, fixtures.customer(), "09:00", Actor.STAFF));

		var overbooked = queries.overbooked(tomorrow(), tomorrow()).stream()
				.filter(it -> it.poolId().equals(branch.poolId()))
				.toList();
		assertThat(overbooked).singleElement().satisfies(day -> assertThat(day.intervals()).singleElement()
				.satisfies(interval -> assertThat(interval.appointments()).extracting(it -> it.getId())
						.containsExactlyInAnyOrder(first, second)));

		assertRejected(RejectionCode.INVALID_STATE, () -> commands.handle(new DisplaceAppointment(elsewhere, null)));

		commands.handle(new DisplaceAppointment(second, null));

		assertThat(queries.get(second).getStatus()).isEqualTo(AppointmentStatus.REBOOKING_REQUIRED);
		assertThat(events.of(AppointmentRebookingRequired.class, it -> it.appointmentId().equals(second)))
				.singleElement().satisfies(it -> assertThat(it.cause()).isEqualTo(RebookingCause.CAPACITY_REDUCED));
		assertThat(schedule(branch).isOverbooked()).isFalse();
		assertThat(events.of(OverbookingResolved.class, it -> it.capacityPoolId().equals(branch.poolId()))).hasSize(1);
	}

	@Test
	@DisplayName("BK-10/BK-12 a closure displaces appointments as previewed; undoing it restores nothing; expiry cancels")
	void closure() {

		var branch = fixtures.openService(2).on(ModuleFixtures.uniqueDate());
		var customer = fixtures.customer();
		var morning = book(branch, customer, "09:00", Actor.CUSTOMER);
		var afternoon = book(branch, fixtures.customer(), "15:00", Actor.CUSTOMER);

		var impact = preview.preview(new CalendarExceptionChange(branch.date(), ExceptionType.CLOSED, List.of()));
		assertThat(impact.displacedAppointments()).isEqualTo(2);
		assertThat(schedule(branch).getReservations()).as("the preview changes nothing").hasSize(2);

		var exception = calendar.handle(new AddCalendarException(branch.date(), ExceptionType.CLOSED, List.of(),
				"Public holiday"));

		await().atMost(WAIT).until(() -> queries.get(morning).getStatus() == AppointmentStatus.REBOOKING_REQUIRED
				&& queries.get(afternoon).getStatus() == AppointmentStatus.REBOOKING_REQUIRED);
		assertThat(events.of(AppointmentRebookingRequired.class, it -> it.appointmentId().equals(morning)))
				.singleElement().satisfies(it -> assertThat(it.cause()).isEqualTo(RebookingCause.BRANCH_UNAVAILABLE));
		assertThat(schedule(branch).getReservations()).isEmpty();

		calendar.handle(new RemoveCalendarException(exception));
		await().atMost(WAIT).until(() -> !availability.openTimes(branch.serviceId(), branch.date()).isEmpty());
		assertThat(queries.get(morning).getStatus()).as("displacement is one-way")
				.isEqualTo(AppointmentStatus.REBOOKING_REQUIRED);

		commands.handle(new RescheduleAppointment(morning, null, branch.at("10:00"), Actor.CUSTOMER));
		assertThat(queries.get(morning).getStatus()).isEqualTo(AppointmentStatus.BOOKED);

		movePast(afternoon, Duration.ofMinutes(5));
		housekeeping.expireRebookings();
		assertThat(queries.get(afternoon).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(queries.get(afternoon).getClosedReason()).isEqualTo(ClosedReason.REBOOKING_EXPIRED);
	}

	@Test
	@DisplayName("BK-12 an override preview reports the same displaced and overbooked counts the real change produces")
	void overridePreviewMatches() {

		var branch = fixtures.openService(2);
		book(branch, fixtures.customer(), "09:00", Actor.CUSTOMER);
		book(branch, fixtures.customer(), "09:00", Actor.CUSTOMER);
		var lunch = book(branch, fixtures.customer(), "12:00", Actor.CUSTOMER);

		var window = TimeRange.of("08:00", "12:30");
		var lowered = preview.preview(new CapacityOverrideChange(branch.poolId(), tomorrow(), window, 1));
		var zero = preview.preview(
				new CapacityOverrideChange(branch.poolId(), tomorrow(), TimeRange.of("12:00", "13:00"), 0));

		assertThat(lowered).isEqualTo(new ImpactPreview.Impact(0, 1));
		assertThat(zero).isEqualTo(new ImpactPreview.Impact(1, 0));

		calendar.handle(new SetCapacityOverride(branch.poolId(), tomorrow(), window, 1, OverrideReason.SHORT_STAFFED,
				null));
		await().atMost(WAIT).until(() -> schedule(branch).isOverbooked());
		assertThat(schedule(branch).overbookedIntervals()).hasSize(1);
		assertThat(schedule(branch).holds(lunch)).isTrue();
	}

	// BK-13, BK-14

	@Test
	@DisplayName("BK-13 an erased customer's future appointments are cancelled; a deactivated customer's stay")
	void customerChanges() {

		var branch = fixtures.openService(3);
		var erased = fixtures.customer();
		var deactivated = fixtures.customer();
		var erasedAppointment = book(branch, erased, "09:00", Actor.CUSTOMER);
		var keptAppointment = book(branch, deactivated, "09:00", Actor.CUSTOMER);

		customers.handle(new DeactivateCustomer(STAFF, deactivated, null));
		customers.handle(new EraseCustomer(STAFF, erased, null));

		await().atMost(WAIT).until(() -> queries.get(erasedAppointment).getStatus() == AppointmentStatus.CANCELLED);
		assertThat(queries.get(erasedAppointment).getClosedReason()).isEqualTo(ClosedReason.CUSTOMER_ERASED);
		assertThat(schedule(branch).holds(erasedAppointment)).isFalse();
		assertThat(queries.get(keptAppointment).getStatus()).isEqualTo(AppointmentStatus.BOOKED);
	}

	@Test
	@DisplayName("BK-14 the day list is ordered by start, with display names and the placeholder for erased customers")
	void dayList() {

		var branch = fixtures.openService(3);
		var erased = fixtures.customer();
		var later = book(branch, fixtures.customer(), "11:00", Actor.STAFF);
		var earlier = book(branch, erased, "09:00", Actor.STAFF);
		customers.handle(new EraseCustomer(STAFF, erased, null));

		await().atMost(WAIT).untilAsserted(() -> assertThat(queries.dayList(tomorrow(), null))
				.filteredOn(it -> it.appointment().getId().equals(earlier) || it.appointment().getId().equals(later))
				.extracting(it -> it.appointment().getSlot().startTime().toString() + " " + it.customerName())
				.containsExactly("09:00 " + CustomerDirectory.FORMER_CUSTOMER, "11:00 Test Customer"));
	}

	private AppointmentId book(OpenService branch, CustomerId customer, String time, Actor actor) {
		return book(branch, customer, branch.at(time), actor);
	}

	private AppointmentId book(OpenService branch, CustomerId customer, LocalDateTime start, Actor actor) {
		return commands.handle(new BookAppointment(customer, branch.serviceId(), start, actor, null))
				.appointmentId();
	}

	private PoolDaySchedule schedule(OpenService branch) {
		return schedules.findByCapacityPoolIdAndDate(branch.poolId().value(), branch.date()).orElseThrow();
	}

	/**
	 * Moves an appointment so it started the given time ago.
	 */
	private void movePast(AppointmentId id, Duration ago) {

		var start = Instant.now().minus(ago).truncatedTo(ChronoUnit.MINUTES);

		jdbc.update("UPDATE booking.appointment SET starts_at = ?, ends_at = ? WHERE id = ?", Timestamp.from(start),
				Timestamp.from(start.plus(Duration.ofMinutes(30))), id.value());
	}

	private static void assertRejected(RejectionCode code, Runnable action) {
		assertThatExceptionOfType(BookingRejected.class).isThrownBy(action::run)
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(code));
	}
}
