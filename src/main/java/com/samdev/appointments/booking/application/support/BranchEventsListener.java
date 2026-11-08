package com.samdev.appointments.booking.application.support;

import java.util.function.Supplier;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import com.samdev.appointments.booking.application.handler.ScheduleCommandHandler;
import com.samdev.appointments.booking.application.port.ProjectionUpdates;
import com.samdev.appointments.booking.application.port.ProjectionUpdates.Result;
import com.samdev.appointments.booking.domain.vo.OfferingTerms;
import com.samdev.appointments.branchmanagement.CalendarExceptionAdded;
import com.samdev.appointments.branchmanagement.CalendarExceptionRemoved;
import com.samdev.appointments.branchmanagement.CapacityOverrideRemoved;
import com.samdev.appointments.branchmanagement.CapacityOverrideSet;
import com.samdev.appointments.branchmanagement.CapacityPoolBaseCapacityChanged;
import com.samdev.appointments.branchmanagement.CapacityPoolDefined;
import com.samdev.appointments.branchmanagement.ServiceOffered;
import com.samdev.appointments.branchmanagement.ServiceOfferingChanged;
import com.samdev.appointments.branchmanagement.ServiceOfferingWithdrawn;
import com.samdev.appointments.branchmanagement.WeeklyHoursChanged;

/**
 * Keeps the schedule projection in step with Branch management. Each listener updates the projection first (in its
 * own transaction), then recomputes the affected pool days dated today or later, each in its own transaction. A
 * listener that meets a projection not built yet, or a row it does not know, rebuilds the projection from the
 * snapshot instead.
 * <p>
 * Offering changes affect only new bookings: existing appointments keep their duration. A retired service changes
 * nothing; its offerings stay bookable until withdrawn.
 */
@Component
class BranchEventsListener {

	private final ProjectionUpdates updates;
	private final ProjectionRebuild rebuild;
	private final ScheduleCommandHandler schedules;

	BranchEventsListener(ProjectionUpdates updates, ProjectionRebuild rebuild, ScheduleCommandHandler schedules) {
		this.updates = updates;
		this.rebuild = rebuild;
		this.schedules = schedules;
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(WeeklyHoursChanged event) {
		apply(() -> updates.weeklyHours(event.weeklyHours(), event.occurredAt()), schedules::recomputeAll);
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(CapacityPoolDefined event) {
		apply(() -> updates.pool(event.poolId(), event.baseCapacity(), event.occurredAt()), () -> {});
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(CapacityPoolBaseCapacityChanged event) {
		apply(() -> updates.pool(event.poolId(), event.baseCapacity(), event.occurredAt()),
				() -> schedules.recomputePool(event.poolId()));
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ServiceOffered event) {
		apply(() -> updates.offering(event.offeringId(), event.serviceId(), event.poolId(),
				OfferingTerms.ofMinutes(event.durationMinutes(), event.slotIntervalMinutes(),
						event.minLeadTimeMinutes(), event.maxAdvanceDays()),
				true, event.occurredAt()), () -> {});
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ServiceOfferingChanged event) {
		apply(() -> updates.offering(event.offeringId(), event.serviceId(), event.poolId(),
				OfferingTerms.ofMinutes(event.durationMinutes(), event.slotIntervalMinutes(),
						event.minLeadTimeMinutes(), event.maxAdvanceDays()),
				null, event.occurredAt()), () -> {});
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(ServiceOfferingWithdrawn event) {
		apply(() -> updates.offeringWithdrawn(event.offeringId(), event.occurredAt()), () -> {});
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(CalendarExceptionAdded event) {
		apply(() -> updates.exception(event.exceptionId(), event.date(), event.type(), event.hours(),
				event.occurredAt()), () -> schedules.recomputeDate(event.date()));
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(CalendarExceptionRemoved event) {
		apply(() -> updates.exceptionRemoved(event.exceptionId(), event.occurredAt()),
				() -> schedules.recomputeDate(event.date()));
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(CapacityOverrideSet event) {
		apply(() -> updates.override(event.overrideId(), event.poolId(), event.date(), event.window(),
				event.capacity(), event.occurredAt()),
				() -> schedules.recomputePoolDate(event.poolId(), event.date()));
	}

	@ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
	void on(CapacityOverrideRemoved event) {
		apply(() -> updates.overrideRemoved(event.overrideId(), event.occurredAt()),
				() -> schedules.recomputePoolDate(event.poolId(), event.date()));
	}

	/**
	 * Applies the update, building the projection first if it is not built yet and rebuilding it instead if the row
	 * is missing; recomputes the affected pool days unless the event changed nothing.
	 */
	private void apply(Supplier<Result> update, Runnable recompute) {

		rebuild.ensureBuilt();

		var result = update.get();

		if (result == Result.MISSING) {
			rebuild.rebuild();
		} else if (result == Result.APPLIED) {
			recompute.run();
		}
	}
}
