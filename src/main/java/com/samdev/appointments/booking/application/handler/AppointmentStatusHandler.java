package com.samdev.appointments.booking.application.handler;

import java.time.Clock;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.application.command.CheckInAppointment;
import com.samdev.appointments.booking.application.command.CompleteAppointment;
import com.samdev.appointments.booking.application.command.MarkNoShow;
import com.samdev.appointments.booking.application.exception.AppointmentNotFoundException;
import com.samdev.appointments.booking.application.exception.StaleAppointmentVersionException;
import com.samdev.appointments.booking.application.port.BookingSettings;
import com.samdev.appointments.booking.domain.entity.Appointment;
import com.samdev.appointments.booking.domain.repository.Appointments;

/**
 * Check-in, completion and no-show. They change only the appointment: its place on the pool day stays taken.
 */
@Component
public class AppointmentStatusHandler {

	private final Appointments appointments;
	private final BookingSettings settings;
	private final Clock clock;

	AppointmentStatusHandler(Appointments appointments, BookingSettings settings, Clock clock) {
		this.appointments = appointments;
		this.settings = settings;
		this.clock = clock;
	}

	@Transactional
	public void handle(CheckInAppointment command) {

		var now = clock.instant();
		var appointment = load(command.appointmentId(), command.expectedVersion());

		appointment.checkIn(now, appointment.getSlot().localDateOf(now));
		appointments.save(appointment);
	}

	@Transactional
	public void handle(CompleteAppointment command) {

		var appointment = load(command.appointmentId(), command.expectedVersion());

		appointment.complete(clock.instant());
		appointments.save(appointment);
	}

	@Transactional
	public void handle(MarkNoShow command) {

		var appointment = load(command.appointmentId(), command.expectedVersion());

		appointment.markNoShow(clock.instant(), settings.noShowGrace());
		appointments.save(appointment);
	}

	private Appointment load(AppointmentId id, @Nullable Long expectedVersion) {

		var appointment = appointments.findById(id)
				.orElseThrow(() -> new AppointmentNotFoundException("Appointment " + id + " not found"));
		StaleAppointmentVersionException.check(id, expectedVersion, appointment.getVersion());

		return appointment;
	}
}
