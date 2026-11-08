package com.samdev.appointments.branchmanagement.application.query;

import com.samdev.appointments.branchmanagement.domain.entity.Service;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceOffering;

/**
 * An active offering together with its service, for the public branch detail.
 */
public record OfferedService(ServiceOffering offering, Service service) {}
