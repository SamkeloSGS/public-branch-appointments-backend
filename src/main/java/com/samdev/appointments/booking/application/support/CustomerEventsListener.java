package com.samdev.appointments.booking.application.support;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.ClosedReason;
import com.samdev.appointments.booking.application.command.CancelAppointment;
import com.samdev.appointments.booking.application.handler.AppointmentCommandHandler;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.booking.domain.repository.Appointments;
import com.samdev.appointments.booking.domain.vo.Actor;
import com.samdev.appointments.customer.CustomerErased;

/**
 * An erased customer's future appointments are cancelled, one transaction each, and their places released. Past
 * appointments are unchanged. Deactivation needs no reaction: new bookings are refused by the booking policy and
 * existing appointments stay.
 */
@Component
class CustomerEventsListener {

	private static final Logger LOG = LoggerFactory.getLogger(CustomerEventsListener.class);

	private final Appointments appointments;
	private final AppointmentCommandHandler commands;
	private final Clock clock;

	CustomerEventsListener(Appointments appointments, AppointmentCommandHandler commands, Clock clock) {
		this.appointments = appointments;
		this.commands = commands;
		this.clock = clock;
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(CustomerErased event) {

		var future = appointments.findByCustomerIdAndStatusInAndSlot_StartsAtAfter(event.customerId().value(),
				List.of(AppointmentStatus.BOOKED, AppointmentStatus.REBOOKING_REQUIRED), clock.instant());

		for (var appointment : future) {
			try {
				commands.handle(new CancelAppointment(appointment.getId(), null, Actor.SYSTEM,
						ClosedReason.CUSTOMER_ERASED));
			} catch (BookingRejected e) {
				LOG.debug("Appointment {} was closed meanwhile: {}", appointment.getId(), e.getMessage());
			}
		}
	}
}
