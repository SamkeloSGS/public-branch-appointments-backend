package com.samdev.appointments.branchmanagement.domain.repository;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Association;
import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.domain.entity.Service;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceOffering;

public interface ServiceOfferings extends Repository<ServiceOffering, ServiceOfferingId> {

	ServiceOffering save(ServiceOffering offering);

	Optional<ServiceOffering> findById(ServiceOfferingId id);

	boolean existsByService(Association<Service, ServiceId> service);

	List<ServiceOffering> findAll();

	List<ServiceOffering> findAllByActiveTrue();
}
