package com.samdev.appointments.branchmanagement.domain.entity;

import java.util.Collection;

import org.springframework.data.domain.AbstractAggregateRoot;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reads the events an aggregate has registered but not yet published. {@link AbstractAggregateRoot} only exposes
 * them to Spring Data (protected methods), so tests go through reflection.
 */
final class AggregateEvents {

	private AggregateEvents() {}

	static Collection<?> of(AbstractAggregateRoot<?> aggregate) {
		return ReflectionTestUtils.invokeMethod(aggregate, "domainEvents");
	}

	static void clear(AbstractAggregateRoot<?> aggregate) {
		ReflectionTestUtils.invokeMethod(aggregate, "clearDomainEvents");
	}
}
