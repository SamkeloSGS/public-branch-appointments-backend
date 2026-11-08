package com.samdev.appointments.branchmanagement.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Association;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceDefined;
import com.samdev.appointments.branchmanagement.ServiceDetailsUpdated;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceMovedToCategory;
import com.samdev.appointments.branchmanagement.ServiceRetired;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.vo.Durations;
import com.samdev.appointments.branchmanagement.domain.vo.Texts;

/**
 * A type of appointment in the branch's catalog, with a default duration, a description and what customers need to
 * bring (prerequisites). Belongs to exactly one service category and takes its customer segment from it; the segment
 * never changes.
 */
@Table(schema = "branch_management", name = "service")
public class Service extends AbstractAggregateRoot<Service> implements AggregateRoot<Service, ServiceId> {

	private static final int MAX_PREREQUISITES = 10;

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final ServiceId id;

	private String name;

	@Column(name = "category_id")
	private Association<ServiceCategory, ServiceCategoryId> category;

	@Enumerated(EnumType.STRING)
	private final CustomerSegment segment;

	private @Nullable String description;

	/**
	 * What the customer needs to bring or have done, in the order shown, for example "Your ID document".
	 */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(schema = "branch_management", name = "service_prerequisite",
			joinColumns = @JoinColumn(name = "service_id"))
	@OrderColumn(name = "position")
	@Column(name = "text")
	private List<String> prerequisites = new ArrayList<>();

	@Column(name = "default_duration_min")
	private int defaultDurationMinutes;

	private boolean active;

	@Version
	private Long version;

	private Service(ServiceId id, String name, ServiceCategory category, @Nullable String description,
			List<String> prerequisites, Duration defaultDuration) {

		this.id = Objects.requireNonNull(id);
		this.name = Texts.required(name, "Service name", 1, 80);
		this.category = Association.forId(category.getId());
		this.segment = category.getSegment();
		this.description = Texts.optional(description, "Description", 500);
		this.prerequisites.addAll(validPrerequisites(prerequisites));
		this.defaultDurationMinutes = Durations.positiveWholeMinutes(defaultDuration, "Default duration");
		this.active = true;
	}

	/**
	 * Defines a service in an active category.
	 */
	public static Service define(ServiceId id, String name, ServiceCategory category, @Nullable String description,
			List<String> prerequisites, Duration defaultDuration) {

		Objects.requireNonNull(category, "Category is required").assertActive();

		var service = new Service(id, name, category, description, prerequisites, defaultDuration);
		service.registerEvent(new ServiceDefined(id, service.name, category.getId(), service.segment,
				service.defaultDurationMinutes, Instant.now()));
		return service;
	}

	/**
	 * Replaces the description and the prerequisites. Possible for a retired service too, so its history stays
	 * readable.
	 */
	public void updateDetails(@Nullable String description, List<String> prerequisites) {

		var validDescription = Texts.optional(description, "Description", 500);
		var validPrerequisites = validPrerequisites(prerequisites);

		this.description = validDescription;
		this.prerequisites.clear();
		this.prerequisites.addAll(validPrerequisites);

		registerEvent(new ServiceDetailsUpdated(id, Instant.now()));
	}

	/**
	 * Moves the service to another active category of the same customer segment.
	 */
	public void moveTo(ServiceCategory target) {

		Objects.requireNonNull(target, "Category is required");

		if (!active) {
			throw new BusinessRuleViolation("Service '" + name + "' is retired and cannot change category");
		}
		if (getCategoryId().equals(target.getId())) {
			throw new BusinessRuleViolation("Service '" + name + "' is already in category '" + target.getName() + "'");
		}
		if (target.getSegment() != segment) {
			throw new BusinessRuleViolation("Service '" + name + "' is a " + segment
					+ " service and cannot move to the " + target.getSegment() + " category '" + target.getName() + "'");
		}
		target.assertActive();

		this.category = Association.forId(target.getId());
		registerEvent(new ServiceMovedToCategory(id, target.getId(), Instant.now()));
	}

	/**
	 * Retires the service. It cannot gain new offerings afterwards; existing offerings are not changed.
	 */
	public void retire() {

		if (!active) {
			throw new BusinessRuleViolation("Service '" + name + "' is already retired");
		}

		active = false;
		registerEvent(new ServiceRetired(id, Instant.now()));
	}

	@Override
	public ServiceId getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public ServiceCategoryId getCategoryId() {
		return category.getId();
	}

	public CustomerSegment getSegment() {
		return segment;
	}

	public @Nullable String getDescription() {
		return description;
	}

	public List<String> getPrerequisites() {
		return List.copyOf(prerequisites);
	}

	public Duration getDefaultDuration() {
		return Duration.ofMinutes(defaultDurationMinutes);
	}

	public boolean isActive() {
		return active;
	}

	public Long getVersion() {
		return version;
	}

	/**
	 * Trimmed, blank entries dropped; at most {@value #MAX_PREREQUISITES}, each up to 200 characters.
	 */
	private static List<String> validPrerequisites(@Nullable List<String> prerequisites) {

		var valid = (prerequisites == null ? List.<String> of() : prerequisites).stream()
				.filter(it -> it != null && !it.isBlank())
				.map(it -> Texts.required(it, "Prerequisite", 1, 200))
				.toList();

		if (valid.size() > MAX_PREREQUISITES) {
			throw new IllegalArgumentException("A service has at most " + MAX_PREREQUISITES + " prerequisites");
		}

		return valid;
	}
}
