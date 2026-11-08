package com.samdev.appointments.identity;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record StaffMemberSuspended(StaffMemberId staffMemberId, Instant occurredAt) {
}
