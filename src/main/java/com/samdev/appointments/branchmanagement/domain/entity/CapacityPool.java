package com.samdev.appointments.branchmanagement.domain.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.branchmanagement.CapacityPoolBaseCapacityChanged;
import com.samdev.appointments.branchmanagement.CapacityPoolDefined;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.domain.vo.Capacity;
import com.samdev.appointments.branchmanagement.domain.vo.Texts;

/**
 * A named group of staff at the branch who serve one or more offerings. All offerings assigned to the same pool draw
 * from the same capacity.
 */
@Table(schema = "branch_management", name = "capacity_pool")
public class CapacityPool extends AbstractAggregateRoot<CapacityPool>
		implements AggregateRoot<CapacityPool, CapacityPoolId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final CapacityPoolId id;

	private final String name;

	private Capacity baseCapacity;

	@Version
	private Long version;

	private CapacityPool(CapacityPoolId id, String name, Capacity baseCapacity) {
		this.id = Objects.requireNonNull(id);
		this.name = Texts.required(name, "Pool name", 1, 80);
		this.baseCapacity = Objects.requireNonNull(baseCapacity, "Base capacity is required");
	}

	public static CapacityPool define(CapacityPoolId id, String name, Capacity baseCapacity) {

		var pool = new CapacityPool(id, name, baseCapacity);
		pool.registerEvent(new CapacityPoolDefined(id, pool.name, baseCapacity.value(), Instant.now()));
		return pool;
	}

	public void changeBaseCapacity(Capacity capacity) {

		this.baseCapacity = Objects.requireNonNull(capacity, "Base capacity is required");
		registerEvent(new CapacityPoolBaseCapacityChanged(id, capacity.value(), Instant.now()));
	}

	@Override
	public CapacityPoolId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Capacity getBaseCapacity() {
		return baseCapacity;
	}

	public Long getVersion() {
		return version;
	}
}
