package com.samdev.appointments.booking.infrastructure.persistence;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.application.port.ProjectionUpdates;
import com.samdev.appointments.booking.application.port.ScheduleProjection;
import com.samdev.appointments.booking.domain.vo.OfferingTerms;
import com.samdev.appointments.booking.domain.vo.ScheduleInputs;
import com.samdev.appointments.booking.domain.vo.ScheduleInputs.WindowCapacity;
import com.samdev.appointments.branchmanagement.BranchDirectory;
import com.samdev.appointments.branchmanagement.BranchDirectory.ScheduleSnapshot;
import com.samdev.appointments.branchmanagement.CalendarExceptionId;
import com.samdev.appointments.branchmanagement.CapacityOverrideId;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * The schedule projection in the {@code booking.view_*} tables. Plain JDBC: these are copies, not aggregates.
 * <p>
 * Every update runs in its own transaction holding the projection's advisory lock, so updates never interleave. An
 * event is applied only if it is newer than the last event applied to the same row and than the last rebuild.
 */
@Component
class JdbcScheduleProjection implements ScheduleProjection, ProjectionUpdates {

	private final JdbcTemplate jdbc;
	private final BranchDirectory branch;

	JdbcScheduleProjection(JdbcTemplate jdbc, BranchDirectory branch) {
		this.jdbc = jdbc;
		this.branch = branch;
	}

	// Reads

	@Override
	@Transactional(readOnly = true)
	public boolean isBuilt() {
		return exists("SELECT COUNT(*) FROM booking.view_schedule");
	}

	@Override
	public ZoneId timeZone() {
		return branch.branch().timeZone();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<OfferingView> offering(ServiceId serviceId) {
		return jdbc.query("""
				SELECT * FROM booking.view_offering WHERE service_id = ?
				ORDER BY active DESC, last_event_at DESC LIMIT 1
				""", (rs, row) -> new OfferingView(new ServiceOfferingId(rs.getObject("id", UUID.class)), serviceId,
				new CapacityPoolId(rs.getObject("capacity_pool_id", UUID.class)),
				OfferingTerms.ofMinutes(rs.getInt("duration_min"), rs.getInt("slot_interval_min"),
						rs.getInt("min_lead_time_min"), rs.getInt("max_advance_days")),
				rs.getBoolean("active")), serviceId.value()).stream().findFirst();
	}

	@Override
	@Transactional(readOnly = true)
	public ScheduleInputs inputs(CapacityPoolId poolId, LocalDate date) {
		return inputs(poolId, date, date).get(date);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<LocalDate, ScheduleInputs> inputs(CapacityPoolId poolId, LocalDate from, LocalDate to) {

		var dates = from.datesUntil(to.plusDays(1)).toList();

		if (!isBuilt()) {
			return dates.stream().collect(Collectors.toMap(date -> date, date -> ScheduleInputs.unavailable()));
		}

		// The latest exception of each date, and the special hours of the range's exceptions
		var exceptions = new HashMap<LocalDate, ExceptionRow>();
		jdbc.query("""
				SELECT DISTINCT ON (exception_date) exception_date, id, type FROM booking.view_exception
				WHERE exception_date BETWEEN ? AND ? AND NOT removed
				ORDER BY exception_date, last_event_at DESC
				""", (RowCallbackHandler) rs -> exceptions.put(rs.getObject("exception_date", LocalDate.class),
				new ExceptionRow(rs.getObject("id", UUID.class), ExceptionType.valueOf(rs.getString("type")))),
				from, to);

		var exceptionHours = new HashMap<UUID, List<TimeRange>>();
		if (!exceptions.isEmpty()) {
			jdbc.query("""
					SELECT h.exception_id, h.opens_at, h.closes_at FROM booking.view_exception_hours h
					JOIN booking.view_exception e ON e.id = h.exception_id
					WHERE e.exception_date BETWEEN ? AND ? AND NOT e.removed
					""", (RowCallbackHandler) rs -> exceptionHours
					.computeIfAbsent(rs.getObject("exception_id", UUID.class), id -> new ArrayList<>())
					.add(range(rs.getTime("opens_at"), rs.getTime("closes_at"))), from, to);
		}

		var weeklyHours = new HashMap<Integer, List<TimeRange>>();
		jdbc.query("SELECT day_of_week, opens_at, closes_at FROM booking.view_weekly_hours",
				(RowCallbackHandler) rs -> weeklyHours.computeIfAbsent(rs.getInt("day_of_week"), day -> new ArrayList<>())
						.add(range(rs.getTime("opens_at"), rs.getTime("closes_at"))));

		var baseCapacity = jdbc.query("SELECT base_capacity FROM booking.view_pool WHERE id = ?",
				(rs, row) -> rs.getInt(1), poolId.value()).stream().findFirst().orElse(0);

		var overrides = new HashMap<LocalDate, List<Map<String, Object>>>();
		for (var override : jdbc.queryForList("""
				SELECT override_date, from_time, to_time, capacity FROM booking.view_override
				WHERE capacity_pool_id = ? AND override_date BETWEEN ? AND ? AND NOT removed
				""", poolId.value(), from, to)) {
			overrides.computeIfAbsent(((Date) override.get("override_date")).toLocalDate(),
					date -> new ArrayList<>()).add(override);
		}

		var result = new HashMap<LocalDate, ScheduleInputs>();

		for (var date : dates) {

			var exception = exceptions.get(date);
			var hours = exception != null ? exceptionHours.getOrDefault(exception.id(), List.of())
					: weeklyHours.getOrDefault(date.getDayOfWeek().getValue(), List.of());

			Integer wholeDay = null;
			var windows = new ArrayList<WindowCapacity>();

			for (var override : overrides.getOrDefault(date, List.of())) {

				var capacity = ((Number) override.get("capacity")).intValue();

				if (override.get("from_time") == null) {
					wholeDay = capacity;
				} else {
					windows.add(new WindowCapacity(
							range((Time) override.get("from_time"), (Time) override.get("to_time")), capacity));
				}
			}

			result.put(date, new ScheduleInputs(true, exception != null,
					exception != null && exception.type() == ExceptionType.CLOSED, hours, baseCapacity, wholeDay,
					windows));
		}

		return result;
	}

	private record ExceptionRow(UUID id, ExceptionType type) {}

	// Updates

	@Override
	@Transactional
	public void rebuild(ScheduleSnapshot snapshot, Instant at) {

		lock();

		jdbc.update("""
				INSERT INTO booking.view_schedule (id, hours_event_at, rebuilt_at) VALUES (1, ?, ?)
				ON CONFLICT (id) DO UPDATE SET hours_event_at = EXCLUDED.hours_event_at,
				    rebuilt_at = EXCLUDED.rebuilt_at
				""", ts(at), ts(at));

		jdbc.update("DELETE FROM booking.view_weekly_hours");
		jdbc.update("DELETE FROM booking.view_pool");
		jdbc.update("DELETE FROM booking.view_offering");
		jdbc.update("DELETE FROM booking.view_exception");
		jdbc.update("DELETE FROM booking.view_override");

		insertWeeklyHours(snapshot.weeklyHours());

		snapshot.pools().forEach(it -> jdbc.update("""
				INSERT INTO booking.view_pool (id, base_capacity, last_event_at) VALUES (?, ?, ?)
				""", it.id().value(), it.baseCapacity(), ts(at)));

		snapshot.offerings().forEach(it -> jdbc.update("""
				INSERT INTO booking.view_offering (id, service_id, capacity_pool_id, duration_min, slot_interval_min,
				    min_lead_time_min, max_advance_days, active, last_event_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", it.id().value(), it.serviceId().value(), it.poolId().value(), it.durationMinutes(),
				it.slotIntervalMinutes(), it.minLeadTimeMinutes(), it.maxAdvanceDays(), it.active(), ts(at)));

		snapshot.calendarExceptions().forEach(it -> {
			jdbc.update("""
					INSERT INTO booking.view_exception (id, exception_date, type, removed, last_event_at)
					VALUES (?, ?, ?, FALSE, ?)
					""", it.id().value(), it.date(), it.type().name(), ts(at));
			insertExceptionHours(it.id().value(), it.hours());
		});

		snapshot.capacityOverrides().forEach(it -> jdbc.update("""
				INSERT INTO booking.view_override (id, capacity_pool_id, override_date, from_time, to_time, capacity,
				    removed, last_event_at)
				VALUES (?, ?, ?, ?, ?, ?, FALSE, ?)
				""", it.id().value(), it.poolId().value(), it.date(), start(it.window()), end(it.window()),
				it.capacity(), ts(at)));
	}

	@Override
	@Transactional
	public Result weeklyHours(Map<DayOfWeek, List<TimeRange>> hours, Instant at) {

		if (!lockAndCheck(at)) {
			return Result.IGNORED;
		}

		var updated = jdbc.update("""
				UPDATE booking.view_schedule SET hours_event_at = ?
				WHERE hours_event_at IS NULL OR hours_event_at < ?
				""", ts(at), ts(at));

		if (updated == 0) {
			return Result.IGNORED;
		}

		jdbc.update("DELETE FROM booking.view_weekly_hours");
		insertWeeklyHours(hours);

		return Result.APPLIED;
	}

	@Override
	@Transactional
	public Result pool(CapacityPoolId poolId, int baseCapacity, Instant at) {

		if (!lockAndCheck(at)) {
			return Result.IGNORED;
		}

		return result(jdbc.update("""
				INSERT INTO booking.view_pool (id, base_capacity, last_event_at) VALUES (?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET base_capacity = EXCLUDED.base_capacity,
				    last_event_at = EXCLUDED.last_event_at
				WHERE booking.view_pool.last_event_at < EXCLUDED.last_event_at
				""", poolId.value(), baseCapacity, ts(at)));
	}

	@Override
	@Transactional
	public Result offering(ServiceOfferingId offeringId, ServiceId serviceId, CapacityPoolId poolId,
			OfferingTerms terms, @Nullable Boolean active, Instant at) {

		if (!lockAndCheck(at)) {
			return Result.IGNORED;
		}

		if (active == null && !exists("SELECT COUNT(*) FROM booking.view_offering WHERE id = ?", offeringId.value())) {
			return Result.MISSING;
		}

		return result(jdbc.update("""
				INSERT INTO booking.view_offering (id, service_id, capacity_pool_id, duration_min, slot_interval_min,
				    min_lead_time_min, max_advance_days, active, last_event_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET capacity_pool_id = EXCLUDED.capacity_pool_id,
				    duration_min = EXCLUDED.duration_min, slot_interval_min = EXCLUDED.slot_interval_min,
				    min_lead_time_min = EXCLUDED.min_lead_time_min, max_advance_days = EXCLUDED.max_advance_days,
				    active = COALESCE(CAST(? AS BOOLEAN), booking.view_offering.active),
				    last_event_at = EXCLUDED.last_event_at
				WHERE booking.view_offering.last_event_at < EXCLUDED.last_event_at
				""", offeringId.value(), serviceId.value(), poolId.value(), (int) terms.duration().toMinutes(),
				(int) terms.slotInterval().toMinutes(), (int) terms.minLeadTime().toMinutes(), terms.maxAdvanceDays(),
				active == null || active, ts(at), active));
	}

	@Override
	@Transactional
	public Result offeringWithdrawn(ServiceOfferingId offeringId, Instant at) {

		if (!lockAndCheck(at)) {
			return Result.IGNORED;
		}

		return tombstone("view_offering", "active = FALSE", offeringId.value(), at);
	}

	@Override
	@Transactional
	public Result exception(CalendarExceptionId exceptionId, LocalDate date, ExceptionType type,
			List<TimeRange> hours, Instant at) {

		if (!lockAndCheck(at)) {
			return Result.IGNORED;
		}

		var result = result(jdbc.update("""
				INSERT INTO booking.view_exception (id, exception_date, type, removed, last_event_at)
				VALUES (?, ?, ?, FALSE, ?)
				ON CONFLICT (id) DO UPDATE SET exception_date = EXCLUDED.exception_date, type = EXCLUDED.type,
				    removed = FALSE, last_event_at = EXCLUDED.last_event_at
				WHERE booking.view_exception.last_event_at < EXCLUDED.last_event_at
				""", exceptionId.value(), date, type.name(), ts(at)));

		if (result == Result.APPLIED) {
			jdbc.update("DELETE FROM booking.view_exception_hours WHERE exception_id = ?", exceptionId.value());
			insertExceptionHours(exceptionId.value(), hours);
		}

		return result;
	}

	@Override
	@Transactional
	public Result exceptionRemoved(CalendarExceptionId exceptionId, Instant at) {

		if (!lockAndCheck(at)) {
			return Result.IGNORED;
		}

		return tombstone("view_exception", "removed = TRUE", exceptionId.value(), at);
	}

	@Override
	@Transactional
	public Result override(CapacityOverrideId overrideId, CapacityPoolId poolId, LocalDate date,
			@Nullable TimeRange window, int capacity, Instant at) {

		if (!lockAndCheck(at)) {
			return Result.IGNORED;
		}

		return result(jdbc.update("""
				INSERT INTO booking.view_override (id, capacity_pool_id, override_date, from_time, to_time, capacity,
				    removed, last_event_at)
				VALUES (?, ?, ?, ?, ?, ?, FALSE, ?)
				ON CONFLICT (id) DO UPDATE SET override_date = EXCLUDED.override_date,
				    from_time = EXCLUDED.from_time, to_time = EXCLUDED.to_time, capacity = EXCLUDED.capacity,
				    removed = FALSE, last_event_at = EXCLUDED.last_event_at
				WHERE booking.view_override.last_event_at < EXCLUDED.last_event_at
				""", overrideId.value(), poolId.value(), date, start(window), end(window), capacity, ts(at)));
	}

	@Override
	@Transactional
	public Result overrideRemoved(CapacityOverrideId overrideId, Instant at) {

		if (!lockAndCheck(at)) {
			return Result.IGNORED;
		}

		return tombstone("view_override", "removed = TRUE", overrideId.value(), at);
	}

	/**
	 * Marks a row removed or withdrawn; {@code MISSING} if the row was never added.
	 */
	private Result tombstone(String table, String assignment, UUID id, Instant at) {

		var updated = jdbc.update("UPDATE booking." + table + " SET " + assignment + ", last_event_at = ? "
				+ "WHERE id = ? AND last_event_at < ?", ts(at), id, ts(at));

		if (updated == 1) {
			return Result.APPLIED;
		}

		return exists("SELECT COUNT(*) FROM booking." + table + " WHERE id = ?", id) ? Result.IGNORED : Result.MISSING;
	}

	/**
	 * Takes the projection lock and tells whether an event at this time is newer than the last rebuild.
	 */
	private boolean lockAndCheck(Instant at) {

		lock();

		var rebuiltAt = jdbc.queryForList("SELECT rebuilt_at FROM booking.view_schedule", Timestamp.class);

		return rebuiltAt.isEmpty() || rebuiltAt.getFirst() == null || at.isAfter(rebuiltAt.getFirst().toInstant());
	}

	private void lock() {
		jdbc.queryForList("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(?, 0))", "booking-projection");
	}

	private void insertWeeklyHours(Map<DayOfWeek, List<TimeRange>> hours) {
		hours.forEach((day, ranges) -> ranges.forEach(range -> jdbc.update("""
				INSERT INTO booking.view_weekly_hours (day_of_week, opens_at, closes_at) VALUES (?, ?, ?)
				""", day.getValue(), Time.valueOf(range.start()), Time.valueOf(range.end()))));
	}

	private void insertExceptionHours(UUID exceptionId, List<TimeRange> hours) {
		hours.forEach(range -> jdbc.update("""
				INSERT INTO booking.view_exception_hours (exception_id, opens_at, closes_at) VALUES (?, ?, ?)
				""", exceptionId, Time.valueOf(range.start()), Time.valueOf(range.end())));
	}

	private boolean exists(String sql, Object... args) {
		var count = jdbc.queryForObject(sql, Long.class, args);
		return count != null && count > 0;
	}

	private static Result result(int updated) {
		return updated > 0 ? Result.APPLIED : Result.IGNORED;
	}

	private static TimeRange range(Time from, Time to) {
		return new TimeRange(from.toLocalTime(), to.toLocalTime());
	}

	private static @Nullable Time start(@Nullable TimeRange window) {
		return window == null ? null : Time.valueOf(window.start());
	}

	private static @Nullable Time end(@Nullable TimeRange window) {
		return window == null ? null : Time.valueOf(window.end());
	}

	private static Timestamp ts(Instant instant) {
		return Timestamp.from(instant);
	}
}
