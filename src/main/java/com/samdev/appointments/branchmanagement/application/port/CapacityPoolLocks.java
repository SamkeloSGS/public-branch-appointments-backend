package com.samdev.appointments.branchmanagement.application.port;

import com.samdev.appointments.branchmanagement.CapacityPoolId;

/**
 * Serializes changes to the capacity overrides of one pool within the current transaction, so two concurrent requests
 * cannot both pass the overlap check. Implemented in infrastructure.
 */
public interface CapacityPoolLocks {

	void lockOverridesOf(CapacityPoolId poolId);
}
