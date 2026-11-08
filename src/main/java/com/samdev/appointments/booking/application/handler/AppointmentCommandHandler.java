package com.samdev.appointments.booking.application.handler;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.RebookingCause;
import com.samdev.appointments.booking.application.command.BookAppointment;
import com.samdev.appointments.booking.application.command.CancelAppointment;
import com.samdev.appointments.booking.application.command.DisplaceAppointment;
import com.samdev.appointments.booking.application.command.RescheduleAppointment;
import com.samdev.appointments.booking.application.exception.AppointmentNotFoundException;
import com.samdev.appointments.booking.application.exception.StaleAppointmentVersionException;
import com.samdev.appointments.booking.application.port.BookingMetrics;
import com.samdev.appointments.booking.application.port.BookingMetrics.Operation;
import com.samdev.appointments.booking.application.port.BookingSettings;
import com.samdev.appointments.booking.application.port.IdempotencyKeys;
import com.samdev.appointments.booking.application.port.ScheduleProjection;
import com.samdev.appointments.booking.application.support.BookingPolicy;
import com.samdev.appointments.booking.domain.entity.Appointment;
import com.samdev.appointments.booking.domain.entity.PoolDaySchedule;
import com.samdev.appointments.booking.domain.event.ReservationDisplaced;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.repository.Appointments;
import com.samdev.appointments.booking.domain.repository.PoolDaySchedules;
import com.samdev.appointments.booking.domain.service.CapacityProfileCalculator;
import com.samdev.appointments.booking.domain.vo.BookingReference;
import com.samdev.appointments.booking.domain.vo.CapacityProfile;
import com.samdev.appointments.branchmanagement.CapacityPoolId;

/**
 * Booking, rescheduling, cancelling and displacing. Each changes one appointment and one or two pool days in the
 * same transaction: the one place in the system where two aggregates change together. The pool day's version
 * decides races: if the commit fails on a version conflict or a unique constraint, the command is retried once from
 * the start; if it fails again, the time is reported as unavailable.
 */
@Component
public class AppointmentCommandHandler {

	private static final Set<String> RETRYABLE_CONSTRAINTS = Set.of("pool_day_schedule_pool_date_uk",
			"appointment_reference_uk", "idempotency_key_pkey", "reservation_appointment_uk");

	private static final String CUSTOMER_OVERLAP_CONSTRAINT = "appointment_customer_no_overlap";
    private static final Logger LOG = LoggerFactory.getLogger(AppointmentCommandHandler.class);

	private final Appointments appointments;
	private final PoolDaySchedules schedules;
	private final ScheduleProjection projection;
	private final BookingPolicy policy;
	private final IdempotencyKeys idempotencyKeys;
	private final BookingSettings settings;
	private final BookingMetrics metrics;
	private final Clock clock;
	private final TransactionTemplate transaction;
	private final SecureRandom random = new SecureRandom();

	AppointmentCommandHandler(Appointments appointments, PoolDaySchedules schedules, ScheduleProjection projection,
			BookingPolicy policy, IdempotencyKeys idempotencyKeys, BookingSettings settings, BookingMetrics metrics,
			Clock clock, PlatformTransactionManager transactionManager) {

		this.appointments = appointments;
		this.schedules = schedules;
		this.projection = projection;
		this.policy = policy;
		this.idempotencyKeys = idempotencyKeys;
		this.settings = settings;
		this.metrics = metrics;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * @return the new appointment, or the one an earlier request with the same idempotency key created
	 */
	public BookingResult handle(BookAppointment command) {

		var result = measured(Operation.BOOK, () -> transaction.execute(status -> book(command)),
				RejectionCode.SLOT_UNAVAILABLE);

		if (result.created()) {
			metrics.booked(command.actor());
		}

		return result;
	}

	public void handle(RescheduleAppointment command) {
		measured(Operation.RESCHEDULE, () -> transaction.execute(status -> reschedule(command)),
				RejectionCode.SLOT_UNAVAILABLE);
	}

	public void handle(CancelAppointment command) {
		measured(Operation.CANCEL, () -> transaction.execute(status -> cancel(command)), RejectionCode.INVALID_STATE);
	}

	public void handle(DisplaceAppointment command) {
		measured(Operation.DISPLACE, () -> transaction.execute(status -> displace(command)),
				RejectionCode.INVALID_STATE);
	}

	/**
	 * A capacity change removed the appointment's reservation: it now needs a new time. Does nothing if the
	 * appointment has moved or changed status in the meantime.
	 */
	public void handle(ReservationDisplaced event) {
		retrying(Operation.DISPLACE, () -> transaction.execute(status -> displaced(event)), RejectionCode.INVALID_STATE);
	}

	private BookingResult book(BookAppointment command) {

		var now = clock.instant();
		var key = command.idempotencyKey();

		if (key != null) {
			var earlier = idempotencyKeys.find(command.customerId(), key);
			if (earlier.isPresent()) {
				return new BookingResult(earlier.get(), false);
			}
		}

		var date = command.start().toLocalDate();
		var offering = projection.offering(command.serviceId()).orElse(null);
		var slot = policy.check(command.customerId(), projection.timeZone(), offering, command.start(),
				it -> profileOf(it.poolId(), date), null, command.actor(), null, now);
		var schedule = scheduleFor(offering.poolId(), date);
		var id = AppointmentId.random();
		schedule.reserve(id, slot.range());

		var appointment = Appointment.book(id, BookingReference.random(random), command.customerId(),
				command.serviceId(), offering.poolId(), slot, command.actor(), now);
		schedules.save(schedule);
		appointments.saveAndFlush(appointment);

		if (key != null) {
			idempotencyKeys.remember(command.customerId(), key, id);
		}

		return new BookingResult(id, true);
	}

	private Void reschedule(RescheduleAppointment command) {

		var now = clock.instant();
		var appointment = load(command.appointmentId(), command.expectedVersion());

		appointment.assertChangeable(command.actor(), now, settings.cancelCutoff());

		var poolId = appointment.getCapacityPoolId();
		var date = command.start().toLocalDate();
		var offering = projection.offering(appointment.getServiceId()).orElse(null);

		var slot = policy.check(appointment.getCustomerId(), projection.timeZone(), offering, command.start(),
				it -> profileOf(poolId, date), appointment.getSlot().duration(), command.actor(),
				appointment.getId(), now);

		var target = scheduleFor(poolId, date);

		if (appointment.holdsReservation()) {
			schedules.findByCapacityPoolIdAndDate(poolId.value(), appointment.getSlot().date()).ifPresent(it -> {
				it.release(appointment.getId(), now);
				schedules.save(it);
			});
		}

		target.reserve(appointment.getId(), slot.range());
		appointment.reschedule(slot, command.actor(), now, settings.cancelCutoff());

		schedules.save(target);
		appointments.saveAndFlush(appointment);

		return null;
	}

	private Void cancel(CancelAppointment command) {

		var now = clock.instant();
		var appointment = load(command.appointmentId(), command.expectedVersion());
		var heldReservation = appointment.holdsReservation();

		appointment.cancel(command.actor(), command.closedReason(), now, settings.cancelCutoff());

		if (heldReservation) {
			release(appointment);
		}

		appointments.saveAndFlush(appointment);

		return null;
	}

	private Void displace(DisplaceAppointment command) {

		var now = clock.instant();
		var appointment = load(command.appointmentId(), command.expectedVersion());
		var schedule = schedules.findByCapacityPoolIdAndDate(appointment.getCapacityPoolId().value(),
				appointment.getSlot().date()).orElse(null);

		var reservation = schedule == null ? null : schedule.reservationOf(appointment.getId()).orElse(null);

		if (reservation == null || schedule.overbookedIntervals().stream()
				.noneMatch(it -> it.overlaps(reservation.range()))) {
			throw new BookingRejected(RejectionCode.INVALID_STATE,
					"Only an appointment in an overbooked interval can be chosen to move");
		}

		appointment.displace(RebookingCause.CAPACITY_REDUCED, now);
		schedule.release(appointment.getId(), now);

		schedules.save(schedule);
		appointments.saveAndFlush(appointment);

		return null;
	}

	private Void displaced(ReservationDisplaced event) {

		var appointment = appointments.findById(event.appointmentId()).orElse(null);

		if (appointment == null || appointment.getStatus() != AppointmentStatus.BOOKED
				|| !appointment.getCapacityPoolId().equals(event.capacityPoolId())
				|| !appointment.getSlot().date().equals(event.date())
				|| !appointment.getSlot().startTime().equals(event.fromTime())) {
			return null;
		}

		var stillReserved = schedules.findByCapacityPoolIdAndDate(event.capacityPoolId().value(), event.date())
				.map(it -> it.holds(appointment.getId()))
				.orElse(false);

		if (!stillReserved) {
			appointment.displace(RebookingCause.BRANCH_UNAVAILABLE, clock.instant());
			appointments.saveAndFlush(appointment);
		}

		return null;
	}

	private Appointment load(AppointmentId id, @Nullable Long expectedVersion) {

		var appointment = appointments.findById(id)
				.orElseThrow(() -> new AppointmentNotFoundException("Appointment " + id + " not found"));
		StaleAppointmentVersionException.check(id, expectedVersion, appointment.getVersion());

		return appointment;
	}

	private void release(Appointment appointment) {
		schedules.findByCapacityPoolIdAndDate(appointment.getCapacityPoolId().value(), appointment.getSlot().date())
				.ifPresent(it -> {
					it.release(appointment.getId(), clock.instant());
					schedules.save(it);
				});
	}

	/**
	 * The pool day, opened with a freshly computed profile if it does not exist yet.
	 */
	private PoolDaySchedule scheduleFor(CapacityPoolId poolId, LocalDate date) {
		return schedules.findByCapacityPoolIdAndDate(poolId.value(), date)
				.orElseGet(() -> PoolDaySchedule.open(poolId, date,
						CapacityProfileCalculator.profile(projection.inputs(poolId, date))));
	}

	/**
	 * The stored profile of an existing pool day (kept current by recomputation), else a fresh one.
	 */
	private CapacityProfile profileOf(CapacityPoolId poolId, LocalDate date) {
		return schedules.findByCapacityPoolIdAndDate(poolId.value(), date)
				.map(PoolDaySchedule::profile)
				.orElseGet(() -> CapacityProfileCalculator.profile(projection.inputs(poolId, date)));
	}

	/**
	 * Runs a command with {@link #retrying} and counts how it ended.
	 */
	private <T> T measured(Operation operation, Supplier<T> attempt, RejectionCode onSecondConflict) {
		try {
			var result = retrying(operation, attempt, onSecondConflict);
			metrics.completed(operation);
			return result;
		} catch (BookingRejected e) {
			metrics.rejected(operation, e.getCode());
			throw e;
		}
	}

	private <T> T retrying(Operation operation, Supplier<T> attempt, RejectionCode onSecondConflict) {
		try {
			return attempt.get();
		} catch (RuntimeException e) {
			if (!isRetryable(e)) {
				throw translate(e);
			}
		}
		metrics.retried(operation);
		try {
			return attempt.get();
		} catch (RuntimeException e) {
			if (isRetryable(e)) {
				throw new BookingRejected(onSecondConflict, onSecondConflict == RejectionCode.SLOT_UNAVAILABLE
						? "That time is no longer available" : "The appointment was changed by another request");
			}
			throw translate(e);
		}
	}

	/**
	 * Version conflicts, unique-constraint races, and deadlocks (two bookings of one customer waiting on each other's
	 * uncommitted row in the overlap constraint): the next attempt sees the winner's committed state.
	 */
	private static boolean isRetryable(RuntimeException e) {
		return e instanceof ObjectOptimisticLockingFailureException || e instanceof PessimisticLockingFailureException
				|| (e instanceof DataIntegrityViolationException && RETRYABLE_CONSTRAINTS.stream()
						.anyMatch(it -> message(e).contains(it)));
	}

	private static RuntimeException translate(RuntimeException e) {
		if (e instanceof DataIntegrityViolationException && message(e).contains(CUSTOMER_OVERLAP_CONSTRAINT)) {
			return new BookingRejected(RejectionCode.OVERLAPPING_APPOINTMENT,
					"The customer already has an appointment at that time");
		}
		return e;
	}

	private static String message(RuntimeException e) {
		var cause = ((DataIntegrityViolationException) e).getMostSpecificCause();
		return String.valueOf(cause.getMessage());
	}

	/**
	 * @param created {@code false} when an earlier request with the same idempotency key created it
	 */
	public record BookingResult(AppointmentId appointmentId, boolean created) {}
}
