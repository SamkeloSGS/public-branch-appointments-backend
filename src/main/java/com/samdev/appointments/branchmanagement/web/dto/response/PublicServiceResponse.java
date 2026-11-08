package com.samdev.appointments.branchmanagement.web.dto.response;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.domain.entity.Service;

public record PublicServiceResponse(UUID id, String name, UUID categoryId, CustomerSegment segment,
		@Nullable String description, List<String> prerequisites, long defaultDurationMinutes) {

	public static PublicServiceResponse of(Service service) {
		return new PublicServiceResponse(service.getId().value(), service.getName(), service.getCategoryId().value(),
				service.getSegment(), service.getDescription(), service.getPrerequisites(),
				service.getDefaultDuration().toMinutes());
	}
}
