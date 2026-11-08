package com.samdev.appointments;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Enforces the database rules for bounded contexts against the migrated catalog: one schema per context, no foreign
 * keys across schemas, and the event publication registry as the only shared table.
 */
@IntegrationTest
class DatabaseCatalogTests {

	@Autowired JdbcTemplate jdbc;

	@Test
	void noForeignKeyCrossesSchemas() {

		var crossSchemaKeys = jdbc.queryForList("""
				SELECT c.conname || ': ' || src_ns.nspname || '.' || src.relname
				       || ' -> ' || ref_ns.nspname || '.' || ref.relname
				FROM pg_constraint c
				JOIN pg_class src ON src.oid = c.conrelid
				JOIN pg_namespace src_ns ON src_ns.oid = src.relnamespace
				JOIN pg_class ref ON ref.oid = c.confrelid
				JOIN pg_namespace ref_ns ON ref_ns.oid = ref.relnamespace
				WHERE c.contype = 'f' AND src_ns.oid <> ref_ns.oid
				""", String.class);

		assertThat(crossSchemaKeys).as("Foreign keys across schemas").isEmpty();
	}

	@Test
	void eventPublicationIsTheOnlySharedTable() {

		var publicTables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
				""", String.class);

		assertThat(publicTables).containsExactlyInAnyOrder("event_publication", "flyway_schema_history_shared");
	}

	@Test
	void branchManagementOwnsItsSchemaAndMigrationHistory() {

		var tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'branch_management' AND table_type = 'BASE TABLE'
				""", String.class);

		assertThat(tables).contains("flyway_schema_history", "opening_hours", "opening_hours_period", "service",
				"capacity_pool", "service_offering", "calendar_exception", "calendar_exception_hours",
				"capacity_override");
	}

	@Test
	void identityOwnsItsSchemaAndMigrationHistory() {

		var tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'identity' AND table_type = 'BASE TABLE'
				""", String.class);

		assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "staff_member", "staff_change_log");
	}

	@Test
	void customerOwnsItsSchemaAndMigrationHistory() {

		var tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'customer' AND table_type = 'BASE TABLE'
				""", String.class);

		assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "customer", "access_audit");
	}

	@Test
	void bookingOwnsItsSchemaAndMigrationHistory() {

		var tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'booking' AND table_type = 'BASE TABLE'
				""", String.class);

		assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "appointment", "pool_day_schedule",
				"capacity_segment", "reservation", "idempotency_key", "view_schedule", "view_weekly_hours", "view_pool",
				"view_offering", "view_exception", "view_exception_hours", "view_override");
	}

	@Test
	void notificationOwnsItsSchemaAndMigrationHistory() {

		var tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'notification' AND table_type = 'BASE TABLE'
				""", String.class);

		assertThat(tables).containsExactlyInAnyOrder("flyway_schema_history", "notification", "delivery");
	}
}
