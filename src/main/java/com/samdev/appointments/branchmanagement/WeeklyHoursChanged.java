package com.samdev.appointments.branchmanagement;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record WeeklyHoursChanged(Map<DayOfWeek, List<TimeRange>> weeklyHours, Instant occurredAt) {
}
