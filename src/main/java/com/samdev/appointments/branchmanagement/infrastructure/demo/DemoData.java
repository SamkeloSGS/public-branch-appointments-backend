package com.samdev.appointments.branchmanagement.infrastructure.demo;

import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.branchmanagement.CustomerSegment;

/**
 * Shape of {@code demo/branch-management.json}.
 */
record DemoData(String source, List<DemoCategory> categories, DemoBranch branch) {

	/**
	 * @param smartIdOnly services offered only when the branch has Smart ID services; false when absent
	 */
	record DemoCategory(String name, CustomerSegment segment, @Nullable String description,
			@Nullable Boolean smartIdOnly, List<DemoService> services) {

		boolean onlyWithSmartId() {
			return Boolean.TRUE.equals(smartIdOnly);
		}
	}

	/**
	 * @param minutes default duration
	 * @param leadTimeMinutes booking lead time; 60 when absent
	 * @param maxAdvanceDays booking horizon; 30 when absent
	 * @param prerequisites what customers need to bring; none when absent
	 */
	record DemoService(String name, @Nullable String description, int minutes, @Nullable Integer leadTimeMinutes,
			@Nullable Integer maxAdvanceDays, @Nullable List<String> prerequisites) {}

	/**
	 * How the configured branch is set up. Its name, address and segments come from {@code appointments.branch}.
	 *
	 * @param weekdays Monday to Friday
	 * @param saturday absent when closed; Sundays are closed
	 * @param consultants size of the pool serving personal customers
	 * @param businessBankers size of the pool serving business customers
	 */
	record DemoBranch(boolean smartId, Hours weekdays, @Nullable Hours saturday, int consultants,
			int businessBankers) {}

	record Hours(LocalTime opens, LocalTime closes) {}
}
