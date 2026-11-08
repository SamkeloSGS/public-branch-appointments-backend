package com.samdev.appointments.branchmanagement.application.command;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.ServiceOfferingId;

/**
 * Branch manager.
 */
public record WithdrawServiceOffering(ServiceOfferingId offeringId,
		@Nullable Long expectedVersion) {}
