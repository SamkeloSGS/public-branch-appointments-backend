package com.samdev.appointments.identity.web.dto.request;

import jakarta.validation.constraints.NotNull;

import com.samdev.appointments.identity.StaffRole;

public record ChangeRoleRequest(@NotNull StaffRole role) {
}
