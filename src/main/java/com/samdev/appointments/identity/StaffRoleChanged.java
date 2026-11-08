package com.samdev.appointments.identity;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record StaffRoleChanged(StaffMemberId staffMemberId, StaffRole role, Instant occurredAt) {
}
