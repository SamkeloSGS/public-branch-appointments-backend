package com.samdev.appointments.customer.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.identity.Subject;

/**
 * Branch manager.
 */
public record ReactivateCustomer(Subject staff, CustomerId customerId, @Nullable Long expectedVersion) {}
