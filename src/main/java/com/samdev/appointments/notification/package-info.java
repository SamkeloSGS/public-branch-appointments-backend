/**
 * Notification: tells customers by email or SMS when something happens to their appointment, and records whether
 * each message went out. Reacts to Booking and Customer events and reads current details from the directories of
 * Booking, Customer and Branch management at send time. Stores IDs only: no name, address, number or message text.
 * <p>
 * Publishes no events and exposes no facade; nothing depends on this module, so bookings keep working when it is
 * down and messages go out when it recovers.
 */
@ApplicationModule(displayName = "Notification")
package com.samdev.appointments.notification;

import org.springframework.modulith.ApplicationModule;
