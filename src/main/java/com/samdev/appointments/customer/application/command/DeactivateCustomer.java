package com.samdev.appointments.customer.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.identity.Subject;

/**
 * Branch manager. A deactivated customer cannot make new bookings; their data is kept.
 */
public record DeactivateCustomer(Subject staff, CustomerId customerId, @Nullable Long expectedVersion) {}
