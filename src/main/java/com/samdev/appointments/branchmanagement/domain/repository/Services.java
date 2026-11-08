package com.samdev.appointments.branchmanagement.domain.repository;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.domain.entity.Service;

public interface Services extends Repository<Service, ServiceId> {

	Service save(Service service);

	Optional<Service> findById(ServiceId id);

	boolean existsByNameIgnoreCase(String name);

	List<Service> findAll();

	List<Service> findAllByActiveTrue();
}
