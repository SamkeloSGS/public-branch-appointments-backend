package com.samdev.appointments.branchmanagement.application.handler;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.application.command.DefineService;
import com.samdev.appointments.branchmanagement.application.command.MoveServiceToCategory;
import com.samdev.appointments.branchmanagement.application.command.RetireService;
import com.samdev.appointments.branchmanagement.application.command.UpdateServiceDetails;
import com.samdev.appointments.branchmanagement.application.exception.NotFoundException;
import com.samdev.appointments.branchmanagement.application.exception.StaleVersionException;
import com.samdev.appointments.branchmanagement.domain.entity.Service;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceCategory;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.repository.ServiceCategories;
import com.samdev.appointments.branchmanagement.domain.repository.Services;

/**
 * Handles the commands on services of the branch's catalog. Every service is looked up in, and belongs to, a
 * service category.
 */
@Component
public class ServiceCatalogCommandHandler {

	private final Services services;
	private final ServiceCategories categories;

	ServiceCatalogCommandHandler(Services services, ServiceCategories categories) {
		this.services = services;
		this.categories = categories;
	}

	@Transactional
	public ServiceId handle(DefineService command) {

		var category = requireCategory(command.categoryId());
		var service = Service.define(ServiceId.random(), command.name(), category, command.description(),
				command.prerequisites(), command.defaultDuration());

		if (services.existsByNameIgnoreCase(service.getName())) {
			throw new BusinessRuleViolation("A service named '" + service.getName() + "' already exists");
		}

		return services.save(service).getId();
	}

	@Transactional
	public void handle(UpdateServiceDetails command) {

		var service = load(command.serviceId(), command.expectedVersion());

		service.updateDetails(command.description(), command.prerequisites());
		services.save(service);
	}

	@Transactional
	public void handle(MoveServiceToCategory command) {

		var service = load(command.serviceId(), command.expectedVersion());

		service.moveTo(requireCategory(command.categoryId()));
		services.save(service);
	}

	@Transactional
	public void handle(RetireService command) {

		var service = load(command.serviceId(), command.expectedVersion());

		service.retire();
		services.save(service);
	}

	private Service load(ServiceId id, @Nullable Long expectedVersion) {

		var service = services.findById(id).orElseThrow(() -> new NotFoundException("Service", id));
		StaleVersionException.check("Service", id, expectedVersion, service.getVersion());

		return service;
	}

	private ServiceCategory requireCategory(ServiceCategoryId id) {
		return categories.findById(id).orElseThrow(() -> new NotFoundException("Service category", id));
	}
}
