package com.samdev.appointments.identity.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.application.port.StaffChangeLog;
import com.samdev.appointments.identity.application.port.StaffChangeLogEntries;

/**
 * Writes change-log rows in the caller's transaction, so a failed write rolls the command back.
 */
@Component
class JdbcStaffChangeLog implements StaffChangeLog, StaffChangeLogEntries {

	private final JdbcTemplate jdbc;
	private final Clock clock;

	JdbcStaffChangeLog(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(StaffMemberId staffMemberId, Action action, @Nullable StaffMemberId changedBy, String detail) {
		jdbc.update("""
				INSERT INTO identity.staff_change_log (id, staff_member_id, action, changed_by, detail, occurred_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""", UUID.randomUUID(), staffMemberId.value(), action.name(),
				changedBy == null ? null : changedBy.value(), detail, Timestamp.from(clock.instant()));
	}

	@Override
	public List<Entry> entriesFor(StaffMemberId staffMemberId) {
		return jdbc.query("""
				SELECT id, action, changed_by, detail, occurred_at FROM identity.staff_change_log
				WHERE staff_member_id = ? ORDER BY occurred_at, id
				""", (rs, row) -> {
			var changedBy = rs.getObject("changed_by", UUID.class);
			return new Entry(rs.getObject("id", UUID.class), Action.valueOf(rs.getString("action")),
					changedBy == null ? null : new StaffMemberId(changedBy), rs.getString("detail"),
					rs.getTimestamp("occurred_at").toInstant());
		}, staffMemberId.value());
	}
}
