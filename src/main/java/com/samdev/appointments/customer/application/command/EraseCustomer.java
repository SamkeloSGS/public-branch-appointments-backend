package com.samdev.appointments.customer.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.identity.Subject;

/**
 * Removes the customer's personal data for good.
 *
 * @param staff the branch manager, {@code null} when customers erase their own data
 */
public record EraseCustomer(@Nullable Subject staff, CustomerId customerId, @Nullable Long expectedVersion) {}
