package com.samdev.appointments.identity;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record StaffMemberInvited(StaffMemberId staffMemberId, StaffRole role, Instant occurredAt) {
}
