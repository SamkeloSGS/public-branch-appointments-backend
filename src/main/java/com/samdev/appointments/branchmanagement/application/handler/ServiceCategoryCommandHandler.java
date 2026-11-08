package com.samdev.appointments.branchmanagement.application.handler;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.application.command.DefineServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.RetireServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.UpdateServiceCategory;
import com.samdev.appointments.branchmanagement.application.exception.NotFoundException;
import com.samdev.appointments.branchmanagement.application.exception.StaleVersionException;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceCategory;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.repository.ServiceCategories;

/**
 * Handles the commands on service categories. Category names are unique within a customer segment.
 */
@Component
public class ServiceCategoryCommandHandler {

	private final ServiceCategories categories;

	ServiceCategoryCommandHandler(ServiceCategories categories) {
		this.categories = categories;
	}

	@Transactional
	public ServiceCategoryId handle(DefineServiceCategory command) {

		var category = ServiceCategory.define(ServiceCategoryId.random(), command.name(), command.segment(),
				command.description());

		assertNameFree(category.getSegment(), category.getName());

		return categories.save(category).getId();
	}

	@Transactional
	public void handle(UpdateServiceCategory command) {

		var category = load(command.categoryId(), command.expectedVersion());

		var name = command.name() == null ? null : command.name().strip();

		if (name != null && !category.getName().equalsIgnoreCase(name)) {
			assertNameFree(category.getSegment(), name);
		}

		category.update(command.name(), command.description());
		categories.save(category);
	}

	@Transactional
	public void handle(RetireServiceCategory command) {

		var category = load(command.categoryId(), command.expectedVersion());

		category.retire();
		categories.save(category);
	}

	private ServiceCategory load(ServiceCategoryId id, @Nullable Long expectedVersion) {

		var category = categories.findById(id).orElseThrow(() -> new NotFoundException("Service category", id));
		StaleVersionException.check("Service category", id, expectedVersion, category.getVersion());

		return category;
	}

	private void assertNameFree(CustomerSegment segment, String name) {
		if (categories.existsBySegmentAndNameIgnoreCase(segment, name)) {
			throw new BusinessRuleViolation("A " + segment + " category named '" + name + "' already exists");
		}
	}
}
