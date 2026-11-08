package com.samdev.appointments.branchmanagement.application.support;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.application.exception.NotFoundException;
import com.samdev.appointments.branchmanagement.application.port.BranchSettings;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityPool;
import com.samdev.appointments.branchmanagement.domain.entity.OpeningHours;
import com.samdev.appointments.branchmanagement.domain.repository.CapacityPools;
import com.samdev.appointments.branchmanagement.domain.repository.OpeningHoursRepository;

/**
 * The configured branch, its opening hours and pools, for command handlers and query services.
 */
@Component
public class BranchLookup {

	private final BranchProfile branch;
	private final OpeningHoursRepository openingHours;
	private final CapacityPools pools;
	private final Clock clock;

	BranchLookup(BranchSettings settings, OpeningHoursRepository openingHours, CapacityPools pools, Clock clock) {
		this.branch = settings.branch();
		this.openingHours = openingHours;
		this.pools = pools;
		this.clock = clock;
	}

	public BranchProfile branch() {
		return branch;
	}

	/**
	 * The single opening hours record, created by the migration.
	 */
	public OpeningHours openingHours() {
		return openingHours.findById(OpeningHours.ID)
				.orElseThrow(() -> new IllegalStateException("Opening hours row is missing; check the migrations"));
	}

	public CapacityPool requirePool(CapacityPoolId poolId) {
		return pools.findById(poolId).orElseThrow(() -> new NotFoundException("Capacity pool", poolId));
	}

	/**
	 * Today in the branch's time zone.
	 */
	public LocalDate today() {
		return LocalDate.now(clock.withZone(branch.timeZone()));
	}
}
