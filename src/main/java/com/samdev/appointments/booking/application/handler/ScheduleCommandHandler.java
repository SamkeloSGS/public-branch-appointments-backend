package com.samdev.appointments.booking.application.handler;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.samdev.appointments.booking.application.command.RecomputeSchedule;
import com.samdev.appointments.booking.application.port.ScheduleProjection;
import com.samdev.appointments.booking.domain.entity.PoolDaySchedule;
import com.samdev.appointments.booking.domain.repository.PoolDaySchedules;
import com.samdev.appointments.booking.domain.service.CapacityProfileCalculator;
import com.samdev.appointments.branchmanagement.CapacityPoolId;

/**
 * Recomputes pool days after the projection changed: each in its own transaction, retried on a version conflict
 * with a concurrent booking. Only pool days that already exist are touched; a missing one has no reservations and
 * gets a fresh profile when it is first used.
 */
@Component
public class ScheduleCommandHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ScheduleCommandHandler.class);
	private static final int ATTEMPTS = 5;

	private final PoolDaySchedules schedules;
	private final ScheduleProjection projection;
	private final Clock clock;
	private final TransactionTemplate transaction;

	ScheduleCommandHandler(PoolDaySchedules schedules, ScheduleProjection projection, Clock clock,
			PlatformTransactionManager transactionManager) {

		this.schedules = schedules;
		this.projection = projection;
		this.clock = clock;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	public void handle(RecomputeSchedule command) {

		for (var attempt = 1; attempt <= ATTEMPTS; attempt++) {
			try {
				transaction.executeWithoutResult(status -> recompute(command));
				return;
			} catch (ObjectOptimisticLockingFailureException e) {
				if (attempt == ATTEMPTS) {
					throw e;
				}
				LOG.debug("Pool day {} {} changed concurrently, recomputing again", command.poolId(), command.date());
			}
		}
	}

	/**
	 * Every pool day from today on.
	 */
	public void recomputeAll() {
		recompute(schedules.findByDateGreaterThanEqual(today()));
	}

	/**
	 * Every pool day on the date.
	 */
	public void recomputeDate(LocalDate date) {
		if (!date.isBefore(today())) {
			recompute(schedules.findByDate(date));
		}
	}

	/**
	 * Every pool day of the pool from today on.
	 */
	public void recomputePool(CapacityPoolId poolId) {
		recompute(schedules.findByCapacityPoolIdAndDateGreaterThanEqual(poolId.value(), today()));
	}

	public void recomputePoolDate(CapacityPoolId poolId, LocalDate date) {
		if (!date.isBefore(today())) {
			handle(new RecomputeSchedule(poolId, date));
		}
	}

	private void recompute(List<PoolDaySchedule> days) {
		days.forEach(it -> handle(new RecomputeSchedule(it.getCapacityPoolId(), it.getDate())));
	}

	private void recompute(RecomputeSchedule command) {

		var schedule = schedules.findByCapacityPoolIdAndDate(command.poolId().value(), command.date()).orElse(null);

		if (schedule == null) {
			return;
		}

		var inputs = projection.inputs(command.poolId(), command.date());

		schedule.applyCapacity(CapacityProfileCalculator.profile(inputs), clock.instant());
		schedules.saveAndFlush(schedule);
	}

	/**
	 * Today in the branch's time zone.
	 */
	private LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), projection.timeZone());
	}
}
