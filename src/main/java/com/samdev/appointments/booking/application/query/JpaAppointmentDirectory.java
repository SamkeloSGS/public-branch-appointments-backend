package com.samdev.appointments.booking.application.query;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.AppointmentDetails;
import com.samdev.appointments.booking.AppointmentDirectory;
import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.domain.repository.Appointments;

/**
 * The read-only facade for other modules.
 */
@Component
@Transactional(readOnly = true)
class JpaAppointmentDirectory implements AppointmentDirectory {

	private final Appointments appointments;

	JpaAppointmentDirectory(Appointments appointments) {
		this.appointments = appointments;
	}

	@Override
	public Optional<AppointmentDetails> find(AppointmentId appointmentId) {
		return appointments.findById(appointmentId).map(it -> {
			var slot = it.getSlot();
			return new AppointmentDetails(it.getId(), it.getReference().value(), it.getCustomerId(),
					it.getServiceId(), slot.date(), slot.startTime(), slot.endTime(), slot.startsAt(), slot.endsAt(),
					it.getStatus());
		});
	}
}
