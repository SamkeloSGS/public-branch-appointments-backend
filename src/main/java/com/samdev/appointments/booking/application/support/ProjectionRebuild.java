package com.samdev.appointments.booking.application.support;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.samdev.appointments.booking.application.handler.ScheduleCommandHandler;
import com.samdev.appointments.booking.application.port.ProjectionUpdates;
import com.samdev.appointments.booking.application.port.ScheduleProjection;
import com.samdev.appointments.branchmanagement.BranchDirectory;

/**
 * Rebuilds the projection from {@link BranchDirectory#scheduleSnapshot}, then recomputes the future pool days. Used
 * at start-up when the projection has not been built yet, and when an event refers to something the projection does
 * not know yet.
 */
@Component
public class ProjectionRebuild {

	private static final Logger LOG = LoggerFactory.getLogger(ProjectionRebuild.class);

	private final BranchDirectory branch;
	private final ScheduleProjection projection;
	private final ProjectionUpdates updates;
	private final ScheduleCommandHandler schedules;
	private final Clock clock;

	ProjectionRebuild(BranchDirectory branch, ScheduleProjection projection, ProjectionUpdates updates,
			ScheduleCommandHandler schedules, Clock clock) {

		this.branch = branch;
		this.projection = projection;
		this.updates = updates;
		this.schedules = schedules;
		this.clock = clock;
	}

	public void rebuild() {
		updates.rebuild(branch.scheduleSnapshot(), clock.instant());
		schedules.recomputeAll();
	}

	/**
	 * Builds the projection if it has not been built yet.
	 *
	 * @return whether it was built now
	 */
	public boolean ensureBuilt() {

		if (projection.isBuilt()) {
			return false;
		}

		rebuild();
		LOG.info("Built the booking schedule projection from Branch management");

		return true;
	}
}
