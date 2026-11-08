package com.samdev.appointments.booking.web.dto.response;

/**
 * What a proposed change would do to existing bookings.
 *
 * @param displacedAppointments appointments that would need a new time
 * @param overbookedIntervals intervals where more appointments would overlap than the reduced capacity allows
 */
public record ImpactResponse(int displacedAppointments, int overbookedIntervals) {}
