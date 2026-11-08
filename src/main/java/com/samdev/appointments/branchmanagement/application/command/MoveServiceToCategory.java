package com.samdev.appointments.branchmanagement.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceId;

/**
 * Branch manager. Moves an active service to another active category of the same customer segment.
 */
public record MoveServiceToCategory(ServiceId serviceId, @Nullable Long expectedVersion,
		ServiceCategoryId categoryId) {}
