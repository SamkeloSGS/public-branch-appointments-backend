package com.samdev.appointments.branchmanagement.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ServiceId;

/**
 * Branch manager. A retired service cannot gain new offerings.
 */
public record RetireService(ServiceId serviceId, @Nullable Long expectedVersion) {}
