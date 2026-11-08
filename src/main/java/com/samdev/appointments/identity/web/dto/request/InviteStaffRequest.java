package com.samdev.appointments.identity.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.samdev.appointments.identity.StaffRole;

public record InviteStaffRequest(@NotBlank String email, @NotBlank String displayName, @NotNull StaffRole role) {
}
