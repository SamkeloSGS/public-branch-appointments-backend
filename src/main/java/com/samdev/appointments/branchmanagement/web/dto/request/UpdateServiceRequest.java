package com.samdev.appointments.branchmanagement.web.dto.request;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The service's new description and prerequisites; absent prerequisites mean none.
 */
public record UpdateServiceRequest(@Nullable String description, @Nullable List<String> prerequisites) {

	public List<String> prerequisiteList() {
		return prerequisites == null ? List.of() : prerequisites;
	}
}
