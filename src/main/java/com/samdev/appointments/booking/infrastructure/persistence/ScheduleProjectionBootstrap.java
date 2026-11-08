package com.samdev.appointments.booking.infrastructure.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.samdev.appointments.booking.application.support.ProjectionRebuild;

/**
 * At start-up, after the other start-up runners (such as the demo data loaders), builds the projection if it has
 * not been built yet, for events published before this module existed.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class ScheduleProjectionBootstrap implements ApplicationRunner {

	private final ProjectionRebuild rebuild;

	ScheduleProjectionBootstrap(ProjectionRebuild rebuild) {
		this.rebuild = rebuild;
	}

	@Override
	public void run(ApplicationArguments args) {
		rebuild.ensureBuilt();
	}
}
