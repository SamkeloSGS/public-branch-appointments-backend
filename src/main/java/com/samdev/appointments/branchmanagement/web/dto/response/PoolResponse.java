package com.samdev.appointments.branchmanagement.web.dto.response;

import java.util.UUID;

import com.samdev.appointments.branchmanagement.domain.entity.CapacityPool;

public record PoolResponse(UUID id, String name, int baseCapacity, long version) {

	public static PoolResponse of(CapacityPool pool) {
		return new PoolResponse(pool.getId().value(), pool.getName(),
				pool.getBaseCapacity().value(), pool.getVersion());
	}
}
