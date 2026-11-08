package com.samdev.appointments.booking.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AbstractAggregateRoot;

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
 * One customer's booking for one service at one time. Behaviour methods take the current time and the
 * actor; the aggregate never reads a clock. Rescheduling keeps service, pool, reference and duration.
 */
@Table(schema = "booking", name = "appointment")
public class Appointment extends AbstractAggregateRoot<Appointment> implements AggregateRoot<Appointment, AppointmentId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final AppointmentId id;

	private final String reference;

	private final UUID customerId;


	private final UUID serviceId;

	private final UUID capacityPoolId;

	private TimeSlot slot;

	@Enumerated(EnumType.STRING)
	private AppointmentStatus status;

	@Enumerated(EnumType.STRING)
	private final BookedBy bookedBy;

	@Enumerated(EnumType.STRING)
	private @Nullable ClosedReason closedReason;

	private final Instant createdAt;

	@Version
	private Long version;

	private Appointment(AppointmentId id, BookingReference reference, CustomerId customerId,
			ServiceId serviceId, CapacityPoolId capacityPoolId, TimeSlot slot, BookedBy bookedBy, Instant now) {

		this.id = Objects.requireNonNull(id);
		this.reference = reference.value();
		this.customerId = customerId.value();
		this.serviceId = serviceId.value();
		this.capacityPoolId = capacityPoolId.value();
		this.slot = Objects.requireNonNull(slot);
		this.status = AppointmentStatus.BOOKED;
		this.bookedBy = bookedBy;
		this.createdAt = now;
	}

	/**
	 * A new {@code BOOKED} appointment. The caller has reserved its place on the pool day.
	 */
	public static Appointment book(AppointmentId id, BookingReference reference, CustomerId customerId,
			ServiceId serviceId, CapacityPoolId capacityPoolId, TimeSlot slot, Actor actor,
			Instant now) {

		assertFuture(slot, now);

		var appointment = new Appointment(id, reference, customerId, serviceId, capacityPoolId, slot,
				actor.bookedBy(), now);
		appointment.registerEvent(new AppointmentBooked(id, reference.value(), customerId, serviceId,
				slot.startsAt(), slot.endsAt(), actor.bookedBy(), now));

		return appointment;
	}

	/**
	 * Checks that the actor may still cancel or reschedule: the status allows it, and a customer changing a
	 * {@code BOOKED} appointment is before the cutoff. Staff, the system, and appointments needing rebooking are
	 * exempt from the cutoff.
	 */
	public void assertChangeable(Actor actor, Instant now, Duration cutoff) {

		if (status != AppointmentStatus.BOOKED && status != AppointmentStatus.REBOOKING_REQUIRED) {
			throw invalidState("changed");
		}
		if (actor == Actor.CUSTOMER && status == AppointmentStatus.BOOKED
				&& !now.isBefore(slot.startsAt().minus(cutoff))) {
			throw new BookingRejected(RejectionCode.PAST_CUTOFF,
					"Appointments can be changed online until " + cutoff.toMinutes() + " minutes before the start");
		}
	}

	/**
	 * Moves the appointment to a new start with the same duration; it is {@code BOOKED} afterwards.
	 */
	public void reschedule(TimeSlot newSlot, Actor actor, Instant now, Duration cutoff) {

		assertChangeable(actor, now, cutoff);
		assertFuture(newSlot, now);

		if (!newSlot.duration().equals(slot.duration())) {
			throw new IllegalArgumentException("Rescheduling keeps the duration of the appointment");
		}

		var previous = slot;
		slot = newSlot;
		status = AppointmentStatus.BOOKED;

		registerEvent(new AppointmentRescheduled(id, reference, getCustomerId(), getServiceId(),
				slot.startsAt(), slot.endsAt(), previous.startsAt(), previous.endsAt(), now));
	}

	public void cancel(Actor actor, ClosedReason reason, Instant now, Duration cutoff) {

		assertChangeable(actor, now, cutoff);

		status = AppointmentStatus.CANCELLED;
		closedReason = Objects.requireNonNull(reason);

		registerEvent(new AppointmentCancelled(id, reference, getCustomerId(), getServiceId(),
				slot.startsAt(), slot.endsAt(), reason, now));
	}

	/**
	 * Only on the appointment's local date.
	 *
	 * @param branchToday today in the branch time zone
	 */
	public void checkIn(Instant now, LocalDate branchToday) {

		assertStatus(AppointmentStatus.BOOKED, "checked in");

		if (!slot.date().equals(branchToday)) {
			throw new BookingRejected(RejectionCode.INVALID_STATE, "Check-in is only possible on the appointment date");
		}

		status = AppointmentStatus.CHECKED_IN;
		registerEvent(new AppointmentCheckedIn(id, reference, getCustomerId(), getServiceId(),
				slot.startsAt(), slot.endsAt(), now));
	}

	public void complete(Instant now) {

		assertStatus(AppointmentStatus.CHECKED_IN, "completed");

		status = AppointmentStatus.COMPLETED;
		registerEvent(new AppointmentCompleted(id, reference, getCustomerId(), getServiceId(),
				slot.startsAt(), slot.endsAt(), now));
	}

	/**
	 * Only after the start plus the grace period.
	 */
	public void markNoShow(Instant now, Duration grace) {

		assertStatus(AppointmentStatus.BOOKED, "marked as a no-show");

		if (now.isBefore(slot.startsAt().plus(grace))) {
			throw new BookingRejected(RejectionCode.INVALID_STATE,
					"A no-show can be recorded " + grace.toMinutes() + " minutes after the start");
		}

		status = AppointmentStatus.NO_SHOW;
		registerEvent(new AppointmentMarkedNoShow(id, reference, getCustomerId(), getServiceId(),
				slot.startsAt(), slot.endsAt(), now));
	}

	/**
	 * Its time is no longer bookable, or a branch manager chose it to resolve overbooking. The customer picks a new
	 * time, or it expires when its original start passes.
	 */
	public void displace(RebookingCause cause, Instant now) {

		assertStatus(AppointmentStatus.BOOKED, "displaced");

		status = AppointmentStatus.REBOOKING_REQUIRED;
		registerEvent(new AppointmentRebookingRequired(id, reference, getCustomerId(), getServiceId(),
				slot.startsAt(), slot.endsAt(), Objects.requireNonNull(cause), now));
	}

	/**
	 * Every appointment except a cancelled one or one needing rebooking occupies its place on the pool day; finishing
	 * early does not free it.
	 */
	public boolean holdsReservation() {
		return status != AppointmentStatus.CANCELLED && status != AppointmentStatus.REBOOKING_REQUIRED;
	}

	private static void assertFuture(TimeSlot slot, Instant now) {
		if (!slot.startsAt().isAfter(now)) {
			throw new BookingRejected(RejectionCode.OUTSIDE_BOOKING_WINDOW, "The start must be in the future");
		}
	}

	private void assertStatus(AppointmentStatus expected, String action) {
		if (status != expected) {
			throw invalidState(action);
		}
	}

	private BookingRejected invalidState(String action) {
		return new BookingRejected(RejectionCode.INVALID_STATE,
				"A " + status + " appointment cannot be " + action);
	}

	@Override
	public AppointmentId getId() {
		return id;
	}

	public BookingReference getReference() {
		return new BookingReference(reference);
	}

	public CustomerId getCustomerId() {
		return new CustomerId(customerId);
	}

	public ServiceId getServiceId() {
		return new ServiceId(serviceId);
	}

	public CapacityPoolId getCapacityPoolId() {
		return new CapacityPoolId(capacityPoolId);
	}

	public TimeSlot getSlot() {
		return slot;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public BookedBy getBookedBy() {
		return bookedBy;
	}

	public @Nullable ClosedReason getClosedReason() {
		return closedReason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Long getVersion() {
		return version;
	}
}
