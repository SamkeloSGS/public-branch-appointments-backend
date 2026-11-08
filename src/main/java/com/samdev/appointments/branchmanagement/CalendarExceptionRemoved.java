package com.samdev.appointments.branchmanagement;

import java.time.Instant;
import java.time.LocalDate;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record CalendarExceptionRemoved(CalendarExceptionId exceptionId, LocalDate date, Instant occurredAt) {
}
