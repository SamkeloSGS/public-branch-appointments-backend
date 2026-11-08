package com.samdev.appointments.branchmanagement.application.command;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ServiceId;

/**
 * Branch manager. Replaces the service's description and prerequisites.
 */
public record UpdateServiceDetails(ServiceId serviceId, @Nullable Long expectedVersion, @Nullable String description,
		List<String> prerequisites) {}
