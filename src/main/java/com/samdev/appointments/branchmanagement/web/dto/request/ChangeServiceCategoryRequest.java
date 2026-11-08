package com.samdev.appointments.branchmanagement.web.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ChangeServiceCategoryRequest(@NotNull UUID categoryId) {}
