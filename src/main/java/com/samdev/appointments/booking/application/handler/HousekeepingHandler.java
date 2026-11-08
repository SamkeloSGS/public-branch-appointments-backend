package com.samdev.appointments.booking.application.handler;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.ClosedReason;
import com.samdev.appointments.booking.application.command.CancelAppointment;
import com.samdev.appointments.booking.application.command.MarkNoShow;
import com.samdev.appointments.booking.application.port.BookingSettings;
import com.samdev.appointments.booking.application.port.IdempotencyKeys;
import com.samdev.appointments.booking.domain.repository.Appointments;
import com.samdev.appointments.booking.domain.vo.Actor;

/**
 * The system commands run by scheduled jobs. Each appointment is handled in its own transaction, so one failure
 * does not stop the rest.
 */
@Component
public class HousekeepingHandler {

	private static final Logger LOG = LoggerFactory.getLogger(HousekeepingHandler.class);

	private final Appointments appointments;
	private final AppointmentCommandHandler commands;
	private final AppointmentStatusHandler statuses;
	private final IdempotencyKeys idempotencyKeys;
	private final BookingSettings settings;
	private final Clock clock;

	HousekeepingHandler(Appointments appointments, AppointmentCommandHandler commands,
			AppointmentStatusHandler statuses, IdempotencyKeys idempotencyKeys, BookingSettings settings, Clock clock) {

		this.appointments = appointments;
		this.commands = commands;
		this.statuses = statuses;
		this.idempotencyKeys = idempotencyKeys;
		this.settings = settings;
		this.clock = clock;
	}

	/**
	 * Marks {@code BOOKED} appointments as no-shows once their end plus the grace period has passed.
	 *
	 * @return how many were marked
	 */
	public int sweepNoShows() {

		var cutoff = clock.instant().minus(settings.noShowGrace());
		var marked = 0;

		for (var appointment : appointments.findByStatusAndSlot_EndsAtBefore(AppointmentStatus.BOOKED, cutoff)) {
			try {
				statuses.handle(new MarkNoShow(appointment.getId(), null, Actor.SYSTEM));
				marked++;
			} catch (RuntimeException e) {
				LOG.warn("Could not mark appointment {} as a no-show: {}", appointment.getId(), e.getMessage());
			}
		}

		return marked;
	}

	/**
	 * Cancels appointments still needing rebooking once their original start has passed.
	 *
	 * @return how many were cancelled
	 */
	public int expireRebookings() {

		var expired = 0;

		for (var appointment : appointments.findByStatusAndSlot_StartsAtBefore(AppointmentStatus.REBOOKING_REQUIRED,
				clock.instant())) {
			try {
				commands.handle(new CancelAppointment(appointment.getId(), null, Actor.SYSTEM,
						ClosedReason.REBOOKING_EXPIRED));
				expired++;
			} catch (RuntimeException e) {
				LOG.warn("Could not expire appointment {}: {}", appointment.getId(), e.getMessage());
			}
		}

		return expired;
	}

	public int purgeIdempotencyKeys() {
		return idempotencyKeys.purgeOlderThan(clock.instant().minus(settings.idempotencyRetention()));
	}
}
