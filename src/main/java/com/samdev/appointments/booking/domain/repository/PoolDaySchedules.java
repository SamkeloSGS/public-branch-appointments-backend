package com.samdev.appointments.booking.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.booking.domain.entity.PoolDaySchedule;
import com.samdev.appointments.booking.domain.vo.PoolDayScheduleId;

public interface PoolDaySchedules extends Repository<PoolDaySchedule, PoolDayScheduleId> {

	PoolDaySchedule save(PoolDaySchedule schedule);

	/**
	 * Saves and flushes, so a version conflict surfaces here.
	 */
	PoolDaySchedule saveAndFlush(PoolDaySchedule schedule);

	Optional<PoolDaySchedule> findByCapacityPoolIdAndDate(UUID capacityPoolId, LocalDate date);

	List<PoolDaySchedule> findByDate(LocalDate date);

	List<PoolDaySchedule> findByDateGreaterThanEqual(LocalDate from);

	List<PoolDaySchedule> findByCapacityPoolIdAndDateGreaterThanEqual(UUID capacityPoolId, LocalDate from);

	List<PoolDaySchedule> findByCapacityPoolIdAndDateBetween(UUID capacityPoolId, LocalDate from, LocalDate to);

	List<PoolDaySchedule> findByOverbookedTrueAndDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
