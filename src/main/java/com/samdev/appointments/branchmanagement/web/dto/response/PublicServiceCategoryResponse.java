package com.samdev.appointments.branchmanagement.web.dto.response;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceCategory;

/**
 * An active category with its active services. Categories without active services are included with an empty list.
 */
public record PublicServiceCategoryResponse(UUID id, String name, CustomerSegment segment,
		@Nullable String description, List<PublicServiceResponse> services) {

	public static PublicServiceCategoryResponse of(ServiceCategory category, List<PublicServiceResponse> services) {
		return new PublicServiceCategoryResponse(category.getId().value(), category.getName(), category.getSegment(),
				category.getDescription(), services);
	}
}
