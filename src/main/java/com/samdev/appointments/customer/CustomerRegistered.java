package com.samdev.appointments.customer;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

/**
 * @param selfRegistered {@code true} when the person registered themselves, {@code false} when branch staff did
 */
@DomainEvent
public record CustomerRegistered(CustomerId customerId, boolean selfRegistered, Instant occurredAt) {}
