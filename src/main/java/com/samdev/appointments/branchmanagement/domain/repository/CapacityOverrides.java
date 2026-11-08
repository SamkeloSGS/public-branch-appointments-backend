package com.samdev.appointments.branchmanagement.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Association;
import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.branchmanagement.CapacityOverrideId;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityOverride;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityPool;

public interface CapacityOverrides extends Repository<CapacityOverride, CapacityOverrideId> {

	CapacityOverride save(CapacityOverride override);

	Optional<CapacityOverride> findById(CapacityOverrideId id);

	void delete(CapacityOverride override);

	List<CapacityOverride> findAllByCapacityPoolAndDate(Association<CapacityPool, CapacityPoolId> pool, LocalDate date);

	List<CapacityOverride> findAllByDateBetweenOrderByDate(LocalDate from, LocalDate to);

	List<CapacityOverride> findAllByDateGreaterThanEqualOrderByDate(LocalDate from);
}
