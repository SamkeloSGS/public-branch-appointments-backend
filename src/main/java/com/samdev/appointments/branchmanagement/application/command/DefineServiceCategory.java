package com.samdev.appointments.branchmanagement.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CustomerSegment;

/**
 * Branch manager. The segment cannot change later.
 */
public record DefineServiceCategory(String name, CustomerSegment segment, @Nullable String description) {}
