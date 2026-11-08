package com.samdev.appointments.branchmanagement.application.query;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.branchmanagement.BranchDirectory;
import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.ServiceCategoryId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.domain.entity.Service;

@Component
@Transactional(readOnly = true)
class DefaultBranchDirectory implements BranchDirectory {

	private final BranchQueries queries;

	DefaultBranchDirectory(BranchQueries queries) {
		this.queries = queries;
	}

	@Override
	public BranchProfile branch() {
		return queries.branch();
	}

	@Override
	public Optional<ServiceSummary> findService(ServiceId serviceId) {
		return queries.findService(serviceId).map(DefaultBranchDirectory::summary);
	}

	@Override
	public Optional<ServiceCategorySummary> findServiceCategory(ServiceCategoryId categoryId) {
		return queries.findCategory(categoryId).map(it -> new ServiceCategorySummary(it.getId(), it.getName(),
				it.getSegment(), it.getDescription(), it.isActive()));
	}

	@Override
	public ScheduleSnapshot scheduleSnapshot() {

		var pools = queries.pools().stream()
				.map(it -> new PoolSnapshot(it.getId(), it.getName(), it.getBaseCapacity().value()))
				.toList();

		var offerings = queries.offerings().stream()
				.map(it -> new OfferingSnapshot(it.getId(), it.getServiceId(), it.getCapacityPoolId(),
						(int) it.getDuration().toMinutes(), (int) it.getSlotInterval().toMinutes(),
						it.getBookingWindow().minLeadTimeMinutes(), it.getBookingWindow().maxAdvanceDays(),
						it.isActive()))
				.toList();

		var exceptions = queries.upcomingExceptions().stream()
				.map(it -> new ExceptionSnapshot(it.getId(), it.getDate(), it.getType(), it.getHours()))
				.toList();

		var overrides = queries.overrides(null, null).stream()
				.map(it -> new OverrideSnapshot(it.getId(), it.getCapacityPoolId(), it.getDate(), it.getWindow(),
						it.getCapacity(), it.getReason()))
				.toList();

		return new ScheduleSnapshot(queries.branch().timeZone(), queries.openingHours().getWeeklyHours().asMap(),
				pools, offerings, exceptions, overrides);
	}

	private static ServiceSummary summary(Service service) {
		return new ServiceSummary(service.getId(), service.getName(), service.getCategoryId(), service.getSegment(),
				service.getDescription(), (int) service.getDefaultDuration().toMinutes(), service.isActive());
	}
}
