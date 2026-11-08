package com.samdev.appointments.branchmanagement.application.handler;

import org.jmolecules.ddd.types.Association;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.application.command.ChangeServiceOffering;
import com.samdev.appointments.branchmanagement.application.command.OfferService;
import com.samdev.appointments.branchmanagement.application.command.WithdrawServiceOffering;
import com.samdev.appointments.branchmanagement.application.exception.NotFoundException;
import com.samdev.appointments.branchmanagement.application.exception.StaleVersionException;
import com.samdev.appointments.branchmanagement.application.support.BranchLookup;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceOffering;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.repository.ServiceOfferings;
import com.samdev.appointments.branchmanagement.domain.repository.Services;
import com.samdev.appointments.branchmanagement.domain.vo.BookingWindow;

/**
 * Handles the commands on service offerings, including the checks that span aggregates: the service must exist and
 * be active, the pool must exist, and the branch offers each service at most once.
 */
@Component
public class ServiceOfferingCommandHandler {

	private final ServiceOfferings offerings;
	private final Services services;
	private final BranchLookup lookup;

	ServiceOfferingCommandHandler(ServiceOfferings offerings, Services services, BranchLookup lookup) {
		this.offerings = offerings;
		this.services = services;
		this.lookup = lookup;
	}

	@Transactional
	public ServiceOfferingId handle(OfferService command) {

		var service = services.findById(command.serviceId())
				.orElseThrow(() -> new NotFoundException("Service", command.serviceId()));

		lookup.requirePool(command.poolId());

		if (offerings.existsByService(Association.forId(service.getId()))) {
			throw new BusinessRuleViolation("Service '" + service.getName() + "' is already offered");
		}

		var offering = ServiceOffering.offer(ServiceOfferingId.random(), lookup.branch(), service, command.poolId(),
				command.duration(), command.slotInterval(),
				BookingWindow.of(command.minLeadTime(), command.maxAdvanceDays()));

		return offerings.save(offering).getId();
	}

	@Transactional
	public void handle(ChangeServiceOffering command) {

		var offering = load(command.offeringId(), command.expectedVersion());
		lookup.requirePool(command.poolId());

		offering.change(command.poolId(), command.duration(), command.slotInterval(),
				BookingWindow.of(command.minLeadTime(), command.maxAdvanceDays()));
		offerings.save(offering);
	}

	@Transactional
	public void handle(WithdrawServiceOffering command) {

		var offering = load(command.offeringId(), command.expectedVersion());
		offerings.save(offering.withdraw());
	}

	private ServiceOffering load(ServiceOfferingId id, @Nullable Long expectedVersion) {

		var offering = offerings.findById(id)
				.orElseThrow(() -> new NotFoundException("Service offering", id));

		StaleVersionException.check("Service offering", id, expectedVersion, offering.getVersion());

		return offering;
	}
}
