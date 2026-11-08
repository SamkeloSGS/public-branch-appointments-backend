package com.samdev.appointments.customer;

import java.time.Instant;

import org.jmolecules.event.annotation.DomainEvent;

@DomainEvent
public record CustomerErased(CustomerId customerId, Instant occurredAt) {}
