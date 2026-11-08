package com.samdev.appointments.branchmanagement.web.dto.request;

import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;

public record UpdateServiceCategoryRequest(@NotBlank String name, @Nullable String description) {}
