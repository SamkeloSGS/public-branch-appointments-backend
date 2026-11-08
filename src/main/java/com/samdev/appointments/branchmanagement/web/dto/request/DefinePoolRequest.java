package com.samdev.appointments.branchmanagement.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DefinePoolRequest(@NotBlank String name, @NotNull Integer baseCapacity) {}
