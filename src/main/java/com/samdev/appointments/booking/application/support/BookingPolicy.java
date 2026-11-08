package com.samdev.appointments.booking.application.support;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.application.port.BookingSettings;
import com.samdev.appointments.booking.application.port.ScheduleProjection.OfferingView;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.repository.Appointments;
import com.samdev.appointments.booking.domain.service.AvailabilityCalculator;
import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.booking.domain.vo.CapacityProfile;
import com.samdev.appointments.booking.domain.vo.TimeSlot;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.customer.CustomerId;

/**
 * The rules that span appointments or modules, checked before a place is reserved, in this order and each with its
 * own code:
 * <ol>
 * <li>The customer is active ({@code CUSTOMER_INACTIVE}).</li>
 * <li>The branch is {@code ACTIVE} and the offering is active ({@code OFFERING_UNAVAILABLE}).</li>
 * <li>The start lies in bookable time ({@code SLOT_UNAVAILABLE}), on the slot grid and inside the booking window
 * ({@code OUTSIDE_BOOKING_WINDOW}). Staff are exempt from the minimum lead time.</li>
 * <li>The customer has no other {@code BOOKED} or {@code CHECKED_IN} appointment overlapping the time
 * ({@code OVERLAPPING_APPOINTMENT}).</li>
 * <li>When customers book themselves: fewer than the maximum number of future {@code BOOKED} appointments
 * ({@code TOO_MANY_APPOINTMENTS}).</li>
 * </ol>
 */
@Component
public class BookingPolicy {

	private final CustomerDirectory customers;
	private final Appointments appointments;
	private final BookingSettings settings;

	BookingPolicy(CustomerDirectory customers, Appointments appointments, BookingSettings settings) {
		this.customers = customers;
		this.appointments = appointments;
		this.settings = settings;
	}

	/**
	 * Runs the checks and returns the slot to reserve.
	 *
	 * @param profile the capacity profile of the pool day the appointment would use
	 * @param duration {@code null} for the offering's duration (new bookings); the appointment's own duration when
	 *     rescheduling
	 * @param moving the appointment being rescheduled, ignored by the overlap check; {@code null} for new bookings,
	 *     which are also subject to the limit check
	 */
	public TimeSlot check(CustomerId customerId, ZoneId zone, @Nullable OfferingView offering,
			LocalDateTime start, Function<OfferingView, CapacityProfile> profile, @Nullable Duration duration,
			Actor actor, @Nullable AppointmentId moving, Instant now) {

		// 1. Is the customer allowed to book at all? Asks Customer's directory: only ACTIVE counts.
		//    Example: the branch manager deactivated Thandi, or she erased her data -> CUSTOMER_INACTIVE.
		if (!customers.isActive(customerId)) {
			throw new BookingRejected(RejectionCode.CUSTOMER_INACTIVE, "The customer cannot make bookings");
		}

		// 2. Is this service bookable? The offering comes from Booking's projection (view_offering).
		//    Example: the branch does not offer "Home loan", or the offering was withdrawn -> OFFERING_UNAVAILABLE.
		if (offering == null || !offering.active()) {
			throw new BookingRejected(RejectionCode.OFFERING_UNAVAILABLE, "The branch does not offer this service");
		}

		// 3. Is the requested time a valid slot? Four sub-checks, below.
		var terms = offering.terms();
		// 3a. Build the slot: start + duration, in the branch time zone. A slot never crosses midnight.
		//     Example: 30-minute service at 10:00 -> 10:00-10:30. At 23:45 it would end after midnight
		//     -> SLOT_UNAVAILABLE.
		TimeSlot slot;
		try {
			slot = TimeSlot.of(start.toLocalDate(), start.toLocalTime(), duration == null ? terms.duration() : duration,
					zone);
		} catch (IllegalArgumentException e) {
			throw slotUnavailable();
		}

		var capacity = profile.apply(offering);

		// 3b. Does the whole slot fall inside bookable time (open hours with capacity of at least 1)?
		//     Example: branch open 08:00-17:00 -> 16:45-17:15 runs past closing; a CLOSED holiday has no bookable
		//     time; a 0-capacity override from 12:00-13:00 blocks 11:45-12:15 -> SLOT_UNAVAILABLE.
		if (capacity.runContaining(slot.range()).isEmpty()) {
			throw slotUnavailable();
		}
		// 3c. Does it start on the slot grid, counted from the start of that bookable period?
		//     Example: open from 08:00 with a 15-minute grid -> 10:00 and 10:15 are fine, 10:10
		//     -> OUTSIDE_BOOKING_WINDOW.
		if (!AvailabilityCalculator.onGrid(capacity, slot.range(), terms.slotInterval())) {
			throw new BookingRejected(RejectionCode.OUTSIDE_BOOKING_WINDOW,
					"Appointments start every " + terms.slotInterval().toMinutes() + " minutes");
		}
		// 3d. Is it inside the booking window? Must be in the future, at least the minimum lead time away (customers
		//     only; staff may book a walk-in for "now"), and no more than the maximum advance days ahead.
		//     Example: lead time 60 min, it is 09:20 -> 10:00 is too soon; max 30 days -> a date 40 days out is too far
		//     -> OUTSIDE_BOOKING_WINDOW.
		if (!AvailabilityCalculator.withinWindow(slot.date(), slot.startTime(), zone, terms, now,
				actor != Actor.CUSTOMER)) {
			throw new BookingRejected(RejectionCode.OUTSIDE_BOOKING_WINDOW,
					"That time is too soon or too far ahead to book");
		}

		// 4. Does the customer already have another BOOKED or CHECKED_IN appointment at an overlapping time? When rescheduling, the appointment being moved is ignored. A database exclusion constraint backs
		//    this up for two requests at the same moment.
		//    Example: Thandi has a card replacement 10:00-10:15 and books an account opening 10:00-10:30
		//    -> OVERLAPPING_APPOINTMENT.
		var excluded = moving == null ? AppointmentId.random() : moving;
		if (appointments.overlaps(customerId.value(), slot.startsAt(), slot.endsAt(), excluded)) {
			throw new BookingRejected(RejectionCode.OVERLAPPING_APPOINTMENT,
					"The customer already has an appointment at that time");
		}

		// 5. Customers booking for themselves may hold only a limited number of upcoming BOOKED appointments
		//    (max-future-appointments, default 3). Staff bookings and reschedules are exempt.
		//    Example: Thandi already has 3 upcoming bookings and books a fourth online -> TOO_MANY_APPOINTMENTS.
		if (moving == null && actor == Actor.CUSTOMER && appointments.countByCustomerIdAndStatusAndSlot_StartsAtAfter(
				customerId.value(), AppointmentStatus.BOOKED, now) >= settings.maxFutureAppointments()) {
			throw new BookingRejected(RejectionCode.TOO_MANY_APPOINTMENTS,
					"You can hold at most " + settings.maxFutureAppointments() + " upcoming appointments");
		}

		// All checks passed: the caller reserves this slot on the pool day, where capacity is the final authority.
		return slot;
	}

	private static BookingRejected slotUnavailable() {
		return new BookingRejected(RejectionCode.SLOT_UNAVAILABLE, "That time is not available");
	}
}
