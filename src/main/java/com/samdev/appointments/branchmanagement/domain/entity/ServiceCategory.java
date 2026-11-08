package com.samdev.appointments.branchmanagement.domain.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceCategoryDefined;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceCategoryRetired;
import com.samdev.appointments.branchmanagement.ServiceCategoryUpdated;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.vo.Texts;

/**
 * A grouping of services for one customer segment, for example "Loans" for personal customers. The
 * segment never changes, because the services in the category and their offerings depend on it.
 */
@Table(schema = "branch_management", name = "service_category")
public class ServiceCategory extends AbstractAggregateRoot<ServiceCategory>
		implements AggregateRoot<ServiceCategory, ServiceCategoryId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final ServiceCategoryId id;

	private String name;

	@Enumerated(EnumType.STRING)
	private final CustomerSegment segment;

	private @Nullable String description;

	private boolean active;

	@Version
	private Long version;

	private ServiceCategory(ServiceCategoryId id, String name, CustomerSegment segment,
			@Nullable String description) {

		this.id = Objects.requireNonNull(id);
		this.name = validName(name);
		this.segment = Objects.requireNonNull(segment, "Segment is required");
		this.description = Texts.optional(description, "Description", 500);
		this.active = true;
	}

	public static ServiceCategory define(ServiceCategoryId id, String name, CustomerSegment segment,
			@Nullable String description) {

		var category = new ServiceCategory(id, name, segment, description);
		category.registerEvent(new ServiceCategoryDefined(id, category.name, segment, category.description,
				Instant.now()));
		return category;
	}

	public void update(String name, @Nullable String description) {

		assertActive();

		this.name = validName(name);
		this.description = Texts.optional(description, "Description", 500);

		registerEvent(new ServiceCategoryUpdated(id, this.name, this.description, Instant.now()));
	}

	/**
	 * Retires the category. It cannot gain services afterwards; its existing services are not changed.
	 */
	public void retire() {

		assertActive();

		active = false;
		registerEvent(new ServiceCategoryRetired(id, Instant.now()));
	}

	/**
	 * @throws BusinessRuleViolation if the category is retired
	 */
	public void assertActive() {
		if (!active) {
			throw new BusinessRuleViolation("Service category '" + name + "' is retired");
		}
	}

	@Override
	public ServiceCategoryId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public CustomerSegment getSegment() {
		return segment;
	}

	public @Nullable String getDescription() {
		return description;
	}

	public boolean isActive() {
		return active;
	}

	public Long getVersion() {
		return version;
	}

	private static String validName(String name) {
		return Texts.required(name, "Category name", 1, 80);
	}
}
