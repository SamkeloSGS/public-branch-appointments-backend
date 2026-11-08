package com.samdev.appointments.notification.infrastructure.persistence;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrates the {@code notification} schema with its own migration folder and history table.
 */
@Configuration(proxyBeanMethods = false)
class NotificationFlywayConfiguration {

	static final String SCHEMA = "notification";

	@Bean
	FlywayMigrationInitializer notificationFlywayInitializer(DataSource dataSource) {

		var flyway = Flyway.configure()
				.dataSource(dataSource)
				.schemas(SCHEMA)
				.createSchemas(true)
				.table("flyway_schema_history")
				.locations("classpath:db/migration/" + SCHEMA)
				.load();

		return new FlywayMigrationInitializer(flyway);
	}
}
