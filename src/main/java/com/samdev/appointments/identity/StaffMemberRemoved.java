package com.samdev.appointments.identity;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * @param invitationWithdrawn removed while still {@code INVITED}: the person never signed in as staff
 */
@DomainEvent
public record StaffMemberRemoved(StaffMemberId staffMemberId, boolean invitationWithdrawn, Instant occurredAt) {
}
