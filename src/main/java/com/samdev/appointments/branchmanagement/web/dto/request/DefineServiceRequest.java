package com.samdev.appointments.branchmanagement.web.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/**
 * @param prerequisites what customers need to bring, in order; absent for none
 */
public record DefineServiceRequest(@NotBlank String name, @NotNull UUID categoryId, @Nullable String description,
		@NotNull Integer defaultDurationMinutes, @Nullable List<String> prerequisites) {

	public List<String> prerequisiteList() {
		return prerequisites == null ? List.of() : prerequisites;
	}
}
