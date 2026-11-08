package com.samdev.appointments.branchmanagement.application.handler;

import org.jmolecules.ddd.types.Association;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.CalendarExceptionId;
import com.samdev.appointments.branchmanagement.CapacityOverrideId;
import com.samdev.appointments.branchmanagement.application.command.AddCalendarException;
import com.samdev.appointments.branchmanagement.application.command.RemoveCalendarException;
import com.samdev.appointments.branchmanagement.application.command.RemoveCapacityOverride;
import com.samdev.appointments.branchmanagement.application.command.SetCapacityOverride;
import com.samdev.appointments.branchmanagement.application.exception.NotFoundException;
import com.samdev.appointments.branchmanagement.application.port.CapacityPoolLocks;
import com.samdev.appointments.branchmanagement.application.support.BranchLookup;
import com.samdev.appointments.branchmanagement.domain.entity.CalendarException;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityOverride;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.repository.CalendarExceptions;
import com.samdev.appointments.branchmanagement.domain.repository.CapacityOverrides;

/**
 * Handles the dated deviations: calendar exceptions and capacity overrides. Both succeed regardless of existing
 * appointments; the impact on bookings is Booking's concern.
 */
@Component
public class CalendarCommandHandler {

	private final CalendarExceptions exceptions;
	private final CapacityOverrides overrides;
	private final CapacityPoolLocks locks;
	private final BranchLookup lookup;

	CalendarCommandHandler(CalendarExceptions exceptions, CapacityOverrides overrides, CapacityPoolLocks locks,
			BranchLookup lookup) {

		this.exceptions = exceptions;
		this.overrides = overrides;
		this.locks = locks;
		this.lookup = lookup;
	}

	@Transactional
	public CalendarExceptionId handle(AddCalendarException command) {

		var exception = CalendarException.add(CalendarExceptionId.random(), command.date(), command.type(),
				command.hours(), command.reason(), lookup.today());

		if (exceptions.existsByDate(command.date())) {
			throw new BusinessRuleViolation("There is already a calendar exception on " + command.date());
		}

		return exceptions.save(exception).getId();
	}

	@Transactional
	public void handle(RemoveCalendarException command) {

		var exception = exceptions.findById(command.exceptionId())
				.orElseThrow(() -> new NotFoundException("Calendar exception", command.exceptionId()));

		exceptions.delete(exception.remove());
	}

	@Transactional
	public CapacityOverrideId handle(SetCapacityOverride command) {

		var pool = lookup.requirePool(command.poolId());

		var override = CapacityOverride.set(CapacityOverrideId.random(), pool, command.date(), command.window(),
				command.capacity(), command.reason(), command.note(), lookup.today());

		locks.lockOverridesOf(pool.getId());

		overrides.findAllByCapacityPoolAndDate(Association.forId(pool.getId()), command.date()).stream()
				.filter(override::conflictsWith)
				.findFirst()
				.ifPresent(existing -> {
					throw new BusinessRuleViolation(existing.isWholeDay()
							? "Pool already has a whole-day override on " + command.date()
							: "Window overlaps the existing override " + existing.getWindow() + " on " + command.date());
				});

		return overrides.save(override).getId();
	}

	@Transactional
	public void handle(RemoveCapacityOverride command) {

		var override = overrides.findById(command.overrideId())
				.orElseThrow(() -> new NotFoundException("Capacity override", command.overrideId()));

		overrides.delete(override.remove());
	}
}
