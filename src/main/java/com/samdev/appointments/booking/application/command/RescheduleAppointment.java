package com.samdev.appointments.booking.application.command;

import java.time.LocalDateTime;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.domain.vo.Actor;

/**
 * Customer or branch staff. Same branch, service, pool, reference and duration; only the start changes.
 *
 * @param start local date and time in the branch time zone
 */
public record RescheduleAppointment(AppointmentId appointmentId, @Nullable Long expectedVersion, LocalDateTime start,
		Actor actor) {}
