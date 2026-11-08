package com.samdev.appointments.branchmanagement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record CalendarExceptionAdded(CalendarExceptionId exceptionId, LocalDate date, ExceptionType type, List<TimeRange> hours, Instant occurredAt) {
}
