package com.samdev.appointments.branchmanagement.domain.repository;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityPool;

public interface CapacityPools extends Repository<CapacityPool, CapacityPoolId> {

	CapacityPool save(CapacityPool pool);

	Optional<CapacityPool> findById(CapacityPoolId id);

	boolean existsByNameIgnoreCase(String name);

	List<CapacityPool> findAll();
}
