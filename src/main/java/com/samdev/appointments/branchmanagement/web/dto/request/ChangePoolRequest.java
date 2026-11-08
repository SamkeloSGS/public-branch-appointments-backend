package com.samdev.appointments.branchmanagement.web.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChangePoolRequest(@NotNull Integer baseCapacity) {}
