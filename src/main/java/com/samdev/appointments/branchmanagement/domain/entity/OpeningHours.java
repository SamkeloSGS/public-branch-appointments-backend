package com.samdev.appointments.branchmanagement.domain.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.branchmanagement.WeeklyHoursChanged;
import com.samdev.appointments.branchmanagement.domain.vo.OpeningHoursId;
import com.samdev.appointments.branchmanagement.domain.vo.OpeningPeriod;
import com.samdev.appointments.branchmanagement.domain.vo.WeeklyHours;

/**
 * The branch's recurring weekly hours. There is exactly one, with {@link #ID}, created by the migration with no
 * hours, so the branch takes no bookings until a branch manager sets them.
 */
@Table(schema = "branch_management", name = "opening_hours")
public class OpeningHours extends AbstractAggregateRoot<OpeningHours>
		implements AggregateRoot<OpeningHours, OpeningHoursId> {

	public static final OpeningHoursId ID = new OpeningHoursId(new UUID(0, 1));

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final OpeningHoursId id;

	@CollectionTable(schema = "branch_management", name = "opening_hours_period",
			joinColumns = @JoinColumn(name = "opening_hours_id"))
	private List<OpeningPeriod> periods = new ArrayList<>();

	@Version
	private Long version;

	/**
	 * Package-private for unit tests; the application loads the one created by the migration.
	 */
	OpeningHours(OpeningHoursId id) {
		this.id = Objects.requireNonNull(id);
	}

	public void change(WeeklyHours hours) {

		Objects.requireNonNull(hours, "Weekly hours are required");

		periods.clear();
		periods.addAll(hours.toPeriods());

		registerEvent(new WeeklyHoursChanged(hours.asMap(), Instant.now()));
	}

	@Override
	public OpeningHoursId getId() {
		return id;
	}

	public WeeklyHours getWeeklyHours() {
		return WeeklyHours.fromPeriods(periods);
	}

	public boolean isEmpty() {
		return periods.isEmpty();
	}

	public Long getVersion() {
		return version;
	}
}
