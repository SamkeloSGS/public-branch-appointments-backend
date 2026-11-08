package com.samdev.appointments.notification.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.notification.infrastructure.config.NotificationProperties;

/**
 * Deletes notifications older than the retention period, with their deliveries, daily at 03:00 UTC.
 */
@Component
public class NotificationRetention {

	private static final Logger LOG = LoggerFactory.getLogger(NotificationRetention.class);

	private final JdbcTemplate jdbc;
	private final NotificationProperties properties;
	private final Clock clock;

	NotificationRetention(JdbcTemplate jdbc, NotificationProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @return the number of notifications deleted
	 */
	@Scheduled(cron = "0 0 3 * * *", zone = "UTC")
	@Transactional
	public int purgeExpired() {

		var cutoff = clock.instant().minus(properties.retention());
		var deleted = jdbc.update("DELETE FROM notification.notification WHERE created_at < ?", Timestamp.from(cutoff));

		if (deleted > 0) {
			LOG.info("Deleted {} notifications older than {}", deleted, properties.retention());
		}

		return deleted;
	}
}
