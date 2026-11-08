package com.samdev.appointments.booking.web.dto.response;

import com.samdev.appointments.booking.application.query.AppointmentQueries.DayListEntry;

/**
 * An appointment on the staff day list, with the customer display name.
 */
public record DayListEntryResponse(AppointmentResponse appointment, String customerName) {

	public static DayListEntryResponse of(DayListEntry entry) {
		return new DayListEntryResponse(AppointmentResponse.of(entry.appointment()), entry.customerName());
	}
}
