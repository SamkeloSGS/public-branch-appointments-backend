package com.samdev.appointments.branchmanagement.infrastructure.persistence;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrates the {@code branch_management} schema with its own migration folder and history table, so the module's
 * tables can later move to another database.
 */
@Configuration(proxyBeanMethods = false)
class BranchManagementFlywayConfiguration {

	static final String SCHEMA = "branch_management";

	@Bean
	FlywayMigrationInitializer branchManagementFlywayInitializer(DataSource dataSource) {

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
