package com.samdev.appointments.booking.web.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.BookedBy;
import com.samdev.appointments.booking.ClosedReason;
import com.samdev.appointments.booking.domain.entity.Appointment;

/**
 * One appointment. Date and times are local to the branch; {@code startsAt} and {@code endsAt} are the same moments
 * in UTC.
 */
public record AppointmentResponse(UUID id, String reference, UUID customerId, UUID serviceId,
		LocalDate date, LocalTime startTime, LocalTime endTime, Instant startsAt, Instant endsAt,
		AppointmentStatus status, BookedBy bookedBy, @Nullable ClosedReason closedReason, long version) {

	public static AppointmentResponse of(Appointment appointment) {

		var slot = appointment.getSlot();

		return new AppointmentResponse(appointment.getId().value(), appointment.getReference().value(),
				appointment.getCustomerId().value(),
				appointment.getServiceId().value(), slot.date(), slot.startTime(), slot.endTime(), slot.startsAt(),
				slot.endsAt(), appointment.getStatus(), appointment.getBookedBy(), appointment.getClosedReason(),
				appointment.getVersion());
	}
}
