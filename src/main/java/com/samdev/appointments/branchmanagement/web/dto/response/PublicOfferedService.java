package com.samdev.appointments.branchmanagement.web.dto.response;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.application.query.OfferedService;

public record PublicOfferedService(UUID serviceId, String name, UUID categoryId, CustomerSegment segment,
		@Nullable String description, List<String> prerequisites, long durationMinutes) {

	public static PublicOfferedService of(OfferedService offered) {
		var service = offered.service();
		return new PublicOfferedService(service.getId().value(), service.getName(), service.getCategoryId().value(),
				service.getSegment(), service.getDescription(), service.getPrerequisites(),
				offered.offering().getDuration().toMinutes());
	}
}
