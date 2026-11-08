package com.samdev.appointments.branchmanagement.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.application.command.ChangePoolBaseCapacity;
import com.samdev.appointments.branchmanagement.application.command.DefineCapacityPool;
import com.samdev.appointments.branchmanagement.application.exception.StaleVersionException;
import com.samdev.appointments.branchmanagement.application.support.BranchLookup;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityPool;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.repository.CapacityPools;
import com.samdev.appointments.branchmanagement.domain.vo.Capacity;

/**
 * Handles the commands on capacity pools.
 */
@Component
public class CapacityPoolCommandHandler {

	private final CapacityPools pools;
	private final BranchLookup lookup;

	CapacityPoolCommandHandler(CapacityPools pools, BranchLookup lookup) {
		this.pools = pools;
		this.lookup = lookup;
	}

	@Transactional
	public CapacityPoolId handle(DefineCapacityPool command) {

		var pool = CapacityPool.define(CapacityPoolId.random(), command.name(), new Capacity(command.baseCapacity()));

		if (pools.existsByNameIgnoreCase(pool.getName())) {
			throw new BusinessRuleViolation("There is already a capacity pool named '" + pool.getName() + "'");
		}

		return pools.save(pool).getId();
	}

	@Transactional
	public void handle(ChangePoolBaseCapacity command) {

		var pool = lookup.requirePool(command.poolId());
		StaleVersionException.check("Capacity pool", pool.getId(), command.expectedVersion(), pool.getVersion());

		pool.changeBaseCapacity(new Capacity(command.baseCapacity()));
		pools.save(pool);
	}
}
