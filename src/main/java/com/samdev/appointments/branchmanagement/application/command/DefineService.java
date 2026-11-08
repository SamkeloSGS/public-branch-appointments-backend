package com.samdev.appointments.branchmanagement.application.command;

import java.time.Duration;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ServiceCategoryId;

/**
 * Branch manager. The service joins an active category and takes its customer segment.
 *
 * @param prerequisites what customers need to bring, in order; may be empty
 */
public record DefineService(String name, ServiceCategoryId categoryId, @Nullable String description,
		Duration defaultDuration, List<String> prerequisites) {

	public DefineService(String name, ServiceCategoryId categoryId, @Nullable String description,
			Duration defaultDuration) {
		this(name, categoryId, description, defaultDuration, List.of());
	}
}
