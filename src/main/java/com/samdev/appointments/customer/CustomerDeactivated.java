package com.samdev.appointments.customer;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record CustomerDeactivated(CustomerId customerId, Instant occurredAt) {}
