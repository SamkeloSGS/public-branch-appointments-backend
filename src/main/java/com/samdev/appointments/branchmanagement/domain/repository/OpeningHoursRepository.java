package com.samdev.appointments.branchmanagement.domain.repository;

import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.branchmanagement.domain.entity.OpeningHours;
import com.samdev.appointments.branchmanagement.domain.vo.OpeningHoursId;

public interface OpeningHoursRepository extends Repository<OpeningHours, OpeningHoursId> {

	OpeningHours save(OpeningHours hours);

	Optional<OpeningHours> findById(OpeningHoursId id);
}
