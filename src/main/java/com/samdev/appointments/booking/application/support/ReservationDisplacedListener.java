package com.samdev.appointments.booking.application.support;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import com.samdev.appointments.booking.application.handler.AppointmentCommandHandler;
import com.samdev.appointments.booking.domain.event.ReservationDisplaced;

/**
 * Turns a displaced reservation into an appointment needing rebooking, which publishes
 * {@code AppointmentRebookingRequired}. One appointment per transaction.
 */
@Component
class ReservationDisplacedListener {

	private final AppointmentCommandHandler commands;

	ReservationDisplacedListener(AppointmentCommandHandler commands) {
		this.commands = commands;
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ReservationDisplaced event) {
		commands.handle(event);
	}
}
