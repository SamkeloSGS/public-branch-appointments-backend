package com.samdev.appointments.branchmanagement.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ServiceCategoryId;

/**
 * Branch manager. A retired category cannot gain services; its existing services are not changed.
 */
public record RetireServiceCategory(ServiceCategoryId categoryId, @Nullable Long expectedVersion) {}
