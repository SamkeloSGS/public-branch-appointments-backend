package com.samdev.appointments.branchmanagement.application.command;

import com.samdev.appointments.branchmanagement.CalendarExceptionId;

/**
 * Branch manager.
 */
public record RemoveCalendarException(CalendarExceptionId exceptionId) {}
