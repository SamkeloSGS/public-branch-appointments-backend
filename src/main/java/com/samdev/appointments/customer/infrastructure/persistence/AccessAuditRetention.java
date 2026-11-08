package com.samdev.appointments.customer.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access-audit rows are kept for {@value #RETENTION_MONTHS} months, then purged daily. Search query text may contain
 * personal data and goes with them. The database refuses any other update or delete.
 */
@Component
public class AccessAuditRetention {

	/**
	 * Must match {@code customer.protect_access_audit()} in the migration.
	 */
	public static final int RETENTION_MONTHS = 24;

	private static final Logger LOG = LoggerFactory.getLogger(AccessAuditRetention.class);

	private final JdbcTemplate jdbc;
	private final Clock clock;

	AccessAuditRetention(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/**
	 * Daily at 02:30 UTC.
	 *
	 * @return the number of rows deleted
	 */
	@Scheduled(cron = "0 30 2 * * *", zone = "UTC")
	@Transactional
	public int purgeExpired() {

		var cutoff = clock.instant().atOffset(ZoneOffset.UTC).minusMonths(RETENTION_MONTHS).toInstant();
		var deleted = jdbc.update("DELETE FROM customer.access_audit WHERE occurred_at < ?", Timestamp.from(cutoff));

		if (deleted > 0) {
			LOG.info("Purged {} access-audit rows older than {} months", deleted, RETENTION_MONTHS);
		}

		return deleted;
	}
}
