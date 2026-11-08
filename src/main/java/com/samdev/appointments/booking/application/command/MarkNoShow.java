package com.samdev.appointments.booking.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.domain.vo.Actor;

/**
 * Branch staff or the no-show sweep, after the start plus the grace period.
 */
public record MarkNoShow(AppointmentId appointmentId, @Nullable Long expectedVersion, Actor actor) {}
