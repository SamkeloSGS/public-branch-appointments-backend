package com.samdev.appointments.booking;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jmolecules.event.annotation.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records every domain event whose transaction committed, on any thread, including events published by
 * asynchronous listeners. A plain listener registering an after-commit callback, so the event publication registry
 * does not track it. Tests filter by the IDs they created, so tests sharing the context do not interfere.
 */
@Component
public class CommittedEvents {

	private final List<Object> events = new CopyOnWriteArrayList<>();

	@EventListener
	void on(Object event) {

		if (!event.getClass().isAnnotationPresent(DomainEvent.class)) {
			return;
		}

		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					events.add(event);
				}
			});
		} else {
			events.add(event);
		}
	}

	public <T> List<T> of(Class<T> type, Predicate<T> filter) {
		return events.stream().filter(type::isInstance).map(type::cast).filter(filter).toList();
	}
}
