package com.samdev.appointments.notification.application.support;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import com.samdev.appointments.booking.AppointmentBooked;
import com.samdev.appointments.booking.AppointmentCancelled;
import com.samdev.appointments.booking.AppointmentCheckedIn;
import com.samdev.appointments.booking.AppointmentRebookingRequired;
import com.samdev.appointments.booking.AppointmentRescheduled;
import com.samdev.appointments.customer.CustomerErased;
import com.samdev.appointments.notification.application.handler.NotificationCommandHandler;

/**
 * Creates and withdraws notifications after Booking and Customer commit. Each runs in its own transaction on another
 * thread: a failure here never affects the publishing module, and the event publication registry retries it.
 */
@Component
class BookingEventsListener {

	private final NotificationCommandHandler commands;

	BookingEventsListener(NotificationCommandHandler commands) {
		this.commands = commands;
	}

	@ApplicationModuleListener
	void on(AppointmentBooked event) {
		commands.handle(event);
	}

	@ApplicationModuleListener
	void on(AppointmentRescheduled event) {
		commands.handle(event);
	}

	@ApplicationModuleListener
	void on(AppointmentCancelled event) {
		commands.handle(event);
	}

	@ApplicationModuleListener
	void on(AppointmentRebookingRequired event) {
		commands.handle(event);
	}

	@ApplicationModuleListener
	void on(AppointmentCheckedIn event) {
		commands.handle(event);
	}

	@ApplicationModuleListener
	void on(CustomerErased event) {
		commands.handle(event);
	}
}
