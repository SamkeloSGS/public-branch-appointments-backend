package com.samdev.appointments.booking;

import java.util.Optional;

/**
 * The read-only interface other modules use. Notification reads message details here at send time.
 */
public interface AppointmentDirectory {

	Optional<AppointmentDetails> find(AppointmentId appointmentId);
}
