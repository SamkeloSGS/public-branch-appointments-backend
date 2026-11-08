package com.samdev.appointments.branchmanagement.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ServiceCategoryId;

/**
 * Branch manager. Renames the category or changes its description.
 */
public record UpdateServiceCategory(ServiceCategoryId categoryId, @Nullable Long expectedVersion, String name,
		@Nullable String description) {}
