package com.samdev.appointments.branchmanagement.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Association;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.branchmanagement.CapacityOverrideId;
import com.samdev.appointments.branchmanagement.CapacityOverrideRemoved;
import com.samdev.appointments.branchmanagement.CapacityOverrideSet;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.OverrideReason;
import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.branchmanagement.domain.vo.Texts;

/**
 * A dated change to a pool's capacity, for the whole day or a time window. The capacity is absolute and may be above
 * the pool's base capacity.
 */
@Table(schema = "branch_management", name = "capacity_override")
public class CapacityOverride extends AbstractAggregateRoot<CapacityOverride>
		implements AggregateRoot<CapacityOverride, CapacityOverrideId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final CapacityOverrideId id;

	@Column(name = "capacity_pool_id")
	private final Association<CapacityPool, CapacityPoolId> capacityPool;

	@Column(name = "override_date")
	private final LocalDate date;

	/**
	 * Absent means the whole day.
	 */
	@AttributeOverride(name = "start", column = @Column(name = "from_time"))
	@AttributeOverride(name = "end", column = @Column(name = "to_time"))
	private final @Nullable TimeRange window;

	private final int capacity;

	@Enumerated(EnumType.STRING)
	private final OverrideReason reason;

	private final @Nullable String note;

	@Version
	private Long version;

	private CapacityOverride(CapacityOverrideId id, CapacityPool pool, LocalDate date, @Nullable TimeRange window,
			int capacity, OverrideReason reason, @Nullable String note) {

		if (capacity < 0) {
			throw new IllegalArgumentException("Capacity must not be negative");
		}

		this.id = Objects.requireNonNull(id);
		this.capacityPool = Association.forId(pool.getId());
		this.date = Objects.requireNonNull(date, "Date is required");
		this.window = window;
		this.capacity = capacity;
		this.reason = Objects.requireNonNull(reason, "Reason is required");
		this.note = Texts.optional(note, "Note", 200);
	}

	/**
	 * Sets an override for the given pool. Conflicts with other overrides of the same pool and date are checked by the
	 * application service using {@link #conflictsWith(CapacityOverride)}.
	 *
	 * @param today today in the branch's time zone
	 */
	public static CapacityOverride set(CapacityOverrideId id, CapacityPool pool, LocalDate date,
			@Nullable TimeRange window, int capacity, OverrideReason reason, @Nullable String note, LocalDate today) {

		if (date != null && date.isBefore(today)) {
			throw new IllegalArgumentException("Date " + date + " is in the past in the branch's time zone");
		}

		var override = new CapacityOverride(id, pool, date, window, capacity, reason, note);
		override.registerEvent(new CapacityOverrideSet(id, pool.getId(), date, window, capacity,
				reason, Instant.now()));
		return override;
	}

	/**
	 * Marks the override for removal. The caller deletes it through the repository, which publishes the event.
	 */
	public CapacityOverride remove() {
		registerEvent(new CapacityOverrideRemoved(id, getCapacityPoolId(), date, Instant.now()));
		return this;
	}

	/**
	 * Two overrides for the same pool and date conflict if both are whole-day, or both are windowed and the windows
	 * overlap. A whole-day and a windowed override may coexist.
	 */
	public boolean conflictsWith(CapacityOverride other) {

		if (!capacityPool.equals(other.capacityPool) || !date.equals(other.date) || id.equals(other.id)) {
			return false;
		}
		if (window == null || other.window == null) {
			return window == null && other.window == null;
		}
		return window.overlaps(other.window);
	}

	public boolean isWholeDay() {
		return window == null;
	}

	@Override
	public CapacityOverrideId getId() {
		return id;
	}

	public CapacityPoolId getCapacityPoolId() {
		return capacityPool.getId();
	}

	public LocalDate getDate() {
		return date;
	}

	public @Nullable TimeRange getWindow() {
		return window;
	}

	public int getCapacity() {
		return capacity;
	}

	public OverrideReason getReason() {
		return reason;
	}

	public @Nullable String getNote() {
		return note;
	}

	public Long getVersion() {
		return version;
	}
}
