package com.samdev.appointments.branchmanagement.domain.repository;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceCategory;

public interface ServiceCategories extends Repository<ServiceCategory, ServiceCategoryId> {

	ServiceCategory save(ServiceCategory category);

	Optional<ServiceCategory> findById(ServiceCategoryId id);

	boolean existsBySegmentAndNameIgnoreCase(CustomerSegment segment, String name);

	List<ServiceCategory> findAll();
}
