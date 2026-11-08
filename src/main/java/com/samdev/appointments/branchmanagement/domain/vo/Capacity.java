package com.samdev.appointments.branchmanagement.domain.vo;

import jakarta.persistence.Column;

import org.jmolecules.ddd.types.ValueObject;

/**
 * Number of customers a capacity pool can serve at the same time on a normal day. At least 1.
 */
public record Capacity(@Column(name = "base_capacity") int value) implements ValueObject {

	public Capacity {
		if (value < 1) {
			throw new IllegalArgumentException("Base capacity must be at least 1");
		}
	}
}
