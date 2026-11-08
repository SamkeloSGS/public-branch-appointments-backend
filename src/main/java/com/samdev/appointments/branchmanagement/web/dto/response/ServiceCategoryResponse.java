package com.samdev.appointments.branchmanagement.web.dto.response;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceCategory;

public record ServiceCategoryResponse(UUID id, String name, CustomerSegment segment, @Nullable String description,
		boolean active, long version) {

	public static ServiceCategoryResponse of(ServiceCategory category) {
		return new ServiceCategoryResponse(category.getId().value(), category.getName(), category.getSegment(),
				category.getDescription(), category.isActive(), category.getVersion());
	}
}
