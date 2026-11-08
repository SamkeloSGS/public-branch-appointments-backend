package com.samdev.appointments.branchmanagement.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.application.port.CapacityPoolLocks;

/**
 * Uses a PostgreSQL transaction-scoped advisory lock keyed by the pool id. The lock is released at commit or rollback.
 */
@Component
class PostgresCapacityPoolLocks implements CapacityPoolLocks {

	private final JdbcTemplate jdbc;

	PostgresCapacityPoolLocks(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockOverridesOf(CapacityPoolId poolId) {
		jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, "capacity-override:" + poolId);
	}
}
