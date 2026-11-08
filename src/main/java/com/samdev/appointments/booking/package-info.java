/**
 * Booking: availability, the appointment lifecycle, and the only place capacity is enforced. Keeps a read-only
 * projection of the branch schedule from Branch management events, resolves customers through Customer's directory, and
 * checks access through Identity.
 * <p>
 * Only this package is the module's public API: identifiers, enums, events and the read-only
 * {@link com.samdev.appointments.booking.AppointmentDirectory}. Events carry IDs, the booking reference and times,
 * never personal data.
 */
@ApplicationModule(displayName = "Booking")
package com.samdev.appointments.booking;

import org.springframework.modulith.ApplicationModule;
