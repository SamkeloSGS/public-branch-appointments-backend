package com.samdev.appointments.branchmanagement.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.branchmanagement.CalendarExceptionAdded;
import com.samdev.appointments.branchmanagement.CalendarExceptionId;
import com.samdev.appointments.branchmanagement.CalendarExceptionRemoved;
import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.branchmanagement.domain.vo.Texts;

/**
 * A dated deviation from the branch's weekly hours: closed, or special hours. The date is in the branch's time zone.
 */
@Table(schema = "branch_management", name = "calendar_exception")
public class CalendarException extends AbstractAggregateRoot<CalendarException>
		implements AggregateRoot<CalendarException, CalendarExceptionId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final CalendarExceptionId id;

	@Column(name = "exception_date")
	private final LocalDate date;

	@Enumerated(EnumType.STRING)
	private final ExceptionType type;

	@CollectionTable(schema = "branch_management", name = "calendar_exception_hours",
			joinColumns = @JoinColumn(name = "exception_id"))
	@AttributeOverride(name = "start", column = @Column(name = "opens_at"))
	@AttributeOverride(name = "end", column = @Column(name = "closes_at"))
	private final List<TimeRange> hours = new ArrayList<>();

	private final @Nullable String reason;

	@Version
	private Long version;

	private CalendarException(CalendarExceptionId id, LocalDate date, ExceptionType type, List<TimeRange> hours,
			@Nullable String reason) {

		this.id = Objects.requireNonNull(id);
		this.date = Objects.requireNonNull(date, "Date is required");
		this.type = Objects.requireNonNull(type, "Exception type is required");
		this.hours.addAll(validHours(type, hours == null ? List.of() : hours));
		this.reason = Texts.optional(reason, "Reason", 200);
	}

	/**
	 * @param today today in the branch's time zone
	 */
	public static CalendarException add(CalendarExceptionId id, LocalDate date, ExceptionType type,
			List<TimeRange> hours, @Nullable String reason, LocalDate today) {

		if (date != null && date.isBefore(today)) {
			throw new IllegalArgumentException("Date " + date + " is in the past in the branch's time zone");
		}

		var exception = new CalendarException(id, date, type, hours, reason);
		exception.registerEvent(new CalendarExceptionAdded(id, date, type, List.copyOf(exception.hours),
				Instant.now()));
		return exception;
	}

	/**
	 * Marks the exception for removal. The caller deletes it through the repository, which publishes the event.
	 */
	public CalendarException remove() {
		registerEvent(new CalendarExceptionRemoved(id, date, Instant.now()));
		return this;
	}

	private static List<TimeRange> validHours(ExceptionType type, List<TimeRange> hours) {
		return switch (type) {
			case CLOSED -> {
				if (!hours.isEmpty()) {
					throw new IllegalArgumentException("A CLOSED exception must not have hours");
				}
				yield List.of();
			}
			case SPECIAL_HOURS -> {
				if (hours.isEmpty()) {
					throw new IllegalArgumentException("A SPECIAL_HOURS exception needs at least one time range");
				}
				yield TimeRange.sortedWithoutOverlaps(hours);
			}
		};
	}

	@Override
	public CalendarExceptionId getId() {
		return id;
	}

	public LocalDate getDate() {
		return date;
	}

	public ExceptionType getType() {
		return type;
	}

	public List<TimeRange> getHours() {
		return hours.stream().sorted(TimeRange.BY_START).toList();
	}

	public @Nullable String getReason() {
		return reason;
	}

	public Long getVersion() {
		return version;
	}
}
