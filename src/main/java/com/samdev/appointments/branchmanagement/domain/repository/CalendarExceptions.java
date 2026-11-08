package com.samdev.appointments.branchmanagement.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.branchmanagement.CalendarExceptionId;
import com.samdev.appointments.branchmanagement.domain.entity.CalendarException;

public interface CalendarExceptions extends Repository<CalendarException, CalendarExceptionId> {

	CalendarException save(CalendarException exception);

	Optional<CalendarException> findById(CalendarExceptionId id);

	void delete(CalendarException exception);

	boolean existsByDate(LocalDate date);

	List<CalendarException> findAllByDateGreaterThanEqualOrderByDate(LocalDate from);
}
