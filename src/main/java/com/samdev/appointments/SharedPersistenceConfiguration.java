package com.samdev.appointments;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrates the only table shared by all modules: Spring Modulith's event publication registry in the {@code public}
 * schema. Every module migrates its own schema with its own Flyway instance and history table.
 */
@Configuration(proxyBeanMethods = false)
public class SharedPersistenceConfiguration {

	@Bean
	FlywayMigrationInitializer sharedFlywayInitializer(DataSource dataSource) {

		var flyway = Flyway.configure()
				.dataSource(dataSource)
				.schemas("public")
				.table("flyway_schema_history_shared")
				.locations("classpath:db/migration/shared")
				.load();

		return new FlywayMigrationInitializer(flyway);
	}
}
