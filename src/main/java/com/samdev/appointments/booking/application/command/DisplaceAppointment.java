package com.samdev.appointments.booking.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentId;

/**
 * Branch manager: choose this appointment to move out of an overbooked interval.
 */
public record DisplaceAppointment(AppointmentId appointmentId, @Nullable Long expectedVersion) {}
