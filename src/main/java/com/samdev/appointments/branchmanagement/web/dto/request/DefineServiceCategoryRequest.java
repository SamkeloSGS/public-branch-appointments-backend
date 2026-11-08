package com.samdev.appointments.branchmanagement.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CustomerSegment;

public record DefineServiceCategoryRequest(@NotBlank String name, @NotNull CustomerSegment segment,
		@Nullable String description) {}
