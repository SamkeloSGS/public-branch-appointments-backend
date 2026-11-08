package com.samdev.appointments.branchmanagement.application.query;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.application.exception.NotFoundException;
import com.samdev.appointments.branchmanagement.application.support.BranchLookup;
import com.samdev.appointments.branchmanagement.domain.entity.CalendarException;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityOverride;
import com.samdev.appointments.branchmanagement.domain.entity.CapacityPool;
import com.samdev.appointments.branchmanagement.domain.entity.OpeningHours;
import com.samdev.appointments.branchmanagement.domain.entity.Service;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceCategory;
import com.samdev.appointments.branchmanagement.domain.entity.ServiceOffering;
import com.samdev.appointments.branchmanagement.domain.repository.CalendarExceptions;
import com.samdev.appointments.branchmanagement.domain.repository.CapacityOverrides;
import com.samdev.appointments.branchmanagement.domain.repository.CapacityPools;
import com.samdev.appointments.branchmanagement.domain.repository.ServiceCategories;
import com.samdev.appointments.branchmanagement.domain.repository.ServiceOfferings;
import com.samdev.appointments.branchmanagement.domain.repository.Services;

/**
 * Read-side queries for the web layer and the {@link com.samdev.appointments.branchmanagement.BranchDirectory}.
 * Public queries only ever see active offerings.
 */
@Component
@Transactional(readOnly = true)
public class BranchQueries {

	private static final Comparator<Service> BY_NAME = Comparator.comparing(Service::getName,
			String.CASE_INSENSITIVE_ORDER);

	private static final Comparator<ServiceCategory> BY_SEGMENT_AND_NAME = Comparator
			.comparing(ServiceCategory::getSegment)
			.thenComparing(ServiceCategory::getName, String.CASE_INSENSITIVE_ORDER);

	private final Services services;
	private final ServiceCategories categories;
	private final CapacityPools pools;
	private final ServiceOfferings offerings;
	private final CalendarExceptions exceptions;
	private final CapacityOverrides overrides;
	private final BranchLookup lookup;

	BranchQueries(Services services, ServiceCategories categories, CapacityPools pools, ServiceOfferings offerings,
			CalendarExceptions exceptions, CapacityOverrides overrides, BranchLookup lookup) {

		this.services = services;
		this.categories = categories;
		this.pools = pools;
		this.offerings = offerings;
		this.exceptions = exceptions;
		this.overrides = overrides;
		this.lookup = lookup;
	}

	// Public

	public BranchProfile branch() {
		return lookup.branch();
	}

	public OpeningHours openingHours() {
		return lookup.openingHours();
	}

	public List<OfferedService> activeOfferings() {

		var catalog = catalog();

		return offerings.findAllByActiveTrue().stream()
				.map(it -> new OfferedService(it, catalog.get(it.getServiceId())))
				.sorted(Comparator.comparing((OfferedService it) -> it.service().getName(), String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	/**
	 * Active services, optionally only those of one category and/or customer segment, sorted by name.
	 */
	public List<Service> activeServices(@Nullable ServiceCategoryId categoryId, @Nullable CustomerSegment segment) {
		return services.findAllByActiveTrue().stream()
				.filter(it -> categoryId == null || it.getCategoryId().equals(categoryId))
				.filter(it -> segment == null || it.getSegment() == segment)
				.sorted(BY_NAME)
				.toList();
	}

	/**
	 * Service categories, optionally of one customer segment, sorted by segment and name.
	 */
	public List<ServiceCategory> categories(@Nullable CustomerSegment segment, boolean includeRetired) {
		return categories.findAll().stream()
				.filter(it -> includeRetired || it.isActive())
				.filter(it -> segment == null || it.getSegment() == segment)
				.sorted(BY_SEGMENT_AND_NAME)
				.toList();
	}

	public ServiceCategory category(ServiceCategoryId id) {
		return findCategory(id).orElseThrow(() -> new NotFoundException("Service category", id));
	}

	public Optional<ServiceCategory> findCategory(ServiceCategoryId id) {
		return categories.findById(id);
	}

	// Admin

	public List<Service> allServices() {
		return services.findAll().stream().sorted(BY_NAME).toList();
	}

	public Service service(ServiceId id) {
		return services.findById(id).orElseThrow(() -> new NotFoundException("Service", id));
	}

	public Optional<Service> findService(ServiceId id) {
		return services.findById(id);
	}

	public List<CapacityPool> pools() {
		return pools.findAll().stream()
				.sorted(Comparator.comparing(CapacityPool::getName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public CapacityPool pool(CapacityPoolId poolId) {
		return lookup.requirePool(poolId);
	}

	public List<ServiceOffering> offerings() {
		return offerings.findAll();
	}

	public ServiceOffering offering(ServiceOfferingId id) {
		return offerings.findById(id).orElseThrow(() -> new NotFoundException("Service offering", id));
	}

	/**
	 * Calendar exceptions from today (in the branch's time zone) onwards.
	 */
	public List<CalendarException> upcomingExceptions() {
		return exceptions.findAllByDateGreaterThanEqualOrderByDate(lookup.today());
	}

	/**
	 * Capacity overrides in the given date range, both ends inclusive. Without {@code from}, starts today in the
	 * branch's time zone; without {@code to}, includes everything after {@code from}.
	 */
	public List<CapacityOverride> overrides(@Nullable LocalDate from, @Nullable LocalDate to) {

		var start = from == null ? lookup.today() : from;

		if (to != null && to.isBefore(start)) {
			throw new IllegalArgumentException("'to' must not be before 'from'");
		}

		return to == null
				? overrides.findAllByDateGreaterThanEqualOrderByDate(start)
				: overrides.findAllByDateBetweenOrderByDate(start, to);
	}

	private Map<ServiceId, Service> catalog() {
		return services.findAll().stream().collect(Collectors.toMap(Service::getId, Function.identity()));
	}
}
