package com.samdev.appointments.booking.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.samdev.appointments.booking.application.port.BookingSettings;

/**
 * Limits and timings; the defaults are placeholders for the business to confirm.
 */
@ConfigurationProperties("appointments.booking")
public record BookingProperties(@DefaultValue("PT60M") Duration cancelCutoff, @DefaultValue("PT15M") Duration noShowGrace,
		@DefaultValue("3") int maxFutureAppointments, @DefaultValue("31") int maxAvailabilityDays,
		@DefaultValue("PT24H") Duration idempotencyRetention) implements BookingSettings {}
