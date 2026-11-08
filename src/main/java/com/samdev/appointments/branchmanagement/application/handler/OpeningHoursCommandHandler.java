package com.samdev.appointments.branchmanagement.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.application.command.SetWeeklyHours;
import com.samdev.appointments.branchmanagement.application.exception.StaleVersionException;
import com.samdev.appointments.branchmanagement.application.support.BranchLookup;
import com.samdev.appointments.branchmanagement.domain.entity.OpeningHours;
import com.samdev.appointments.branchmanagement.domain.repository.OpeningHoursRepository;
import com.samdev.appointments.branchmanagement.domain.vo.WeeklyHours;

/**
 * Handles the command on the {@link OpeningHours} aggregate.
 */
@Component
public class OpeningHoursCommandHandler {

	private final OpeningHoursRepository openingHours;
	private final BranchLookup lookup;

	OpeningHoursCommandHandler(OpeningHoursRepository openingHours, BranchLookup lookup) {
		this.openingHours = openingHours;
		this.lookup = lookup;
	}

	@Transactional
	public void handle(SetWeeklyHours command) {

		var hours = WeeklyHours.of(command.weeklyHours());
		var current = lookup.openingHours();

		StaleVersionException.check("Opening hours", current.getId(), command.expectedVersion(), current.getVersion());

		current.change(hours);
		openingHours.save(current);
	}
}
