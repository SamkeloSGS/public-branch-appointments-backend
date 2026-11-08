package com.samdev.appointments.branchmanagement.domain.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Association;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceOffered;
import com.samdev.appointments.branchmanagement.ServiceOfferingChanged;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.ServiceOfferingWithdrawn;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.vo.BookingWindow;
import com.samdev.appointments.branchmanagement.domain.vo.Durations;

/**
 * The branch's decision to offer one service, with its own duration, slot interval and booking window.
 */
@Table(schema = "branch_management", name = "service_offering")
public class ServiceOffering extends AbstractAggregateRoot<ServiceOffering>
		implements AggregateRoot<ServiceOffering, ServiceOfferingId> {

	private static final int MIN_SLOT_INTERVAL_MINUTES = 5;
	private static final int MAX_SLOT_INTERVAL_MINUTES = 120;

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final ServiceOfferingId id;

	@Column(name = "service_id")
	private final Association<Service, ServiceId> service;

	@Column(name = "capacity_pool_id")
	private Association<CapacityPool, CapacityPoolId> capacityPool;

	@Column(name = "duration_min")
	private int durationMinutes;

	@Column(name = "slot_interval_min")
	private int slotIntervalMinutes;

	private BookingWindow bookingWindow;

	private boolean active;

	@Version
	private Long version;

	private ServiceOffering(ServiceOfferingId id, ServiceId serviceId) {
		this.id = Objects.requireNonNull(id);
		this.service = Association.forId(Objects.requireNonNull(serviceId));
		this.active = true;
	}

	/**
	 * Offers a service of a customer segment the branch serves. Checks that need other repositories (the pool exists,
	 * one offering per service) are done by the application service.
	 *
	 * @param duration the offering's duration, or {@code null} to use the service's default duration
	 */
	public static ServiceOffering offer(ServiceOfferingId id, BranchProfile branch, Service service, CapacityPoolId poolId,
			Duration duration, Duration slotInterval, BookingWindow bookingWindow) {

		if (!service.isActive()) {
			throw new BusinessRuleViolation("Service '" + service.getName() + "' is retired and cannot be offered");
		}
		if (!branch.serves(service.getSegment())) {
			throw new BusinessRuleViolation("The branch does not serve " + service.getSegment()
					+ " customers, so it cannot offer '" + service.getName() + "'");
		}

		var offering = new ServiceOffering(id, service.getId());
		offering.assignSchedule(poolId, duration == null ? service.getDefaultDuration() : duration, slotInterval,
				bookingWindow);
		offering.registerEvent(new ServiceOffered(id, service.getId(), poolId, offering.durationMinutes,
				offering.slotIntervalMinutes, bookingWindow.minLeadTimeMinutes(), bookingWindow.maxAdvanceDays(),
				Instant.now()));
		return offering;
	}

	public void change(CapacityPoolId poolId, Duration duration, Duration slotInterval, BookingWindow bookingWindow) {

		assertActive();
		assignSchedule(poolId, duration, slotInterval, bookingWindow);

		registerEvent(new ServiceOfferingChanged(id, getServiceId(), poolId, durationMinutes,
				slotIntervalMinutes, bookingWindow.minLeadTimeMinutes(), bookingWindow.maxAdvanceDays(), Instant.now()));
	}

	/**
	 * Returns {@code this}: the jMolecules ByteBuddy plugin treats every method whose name starts with {@code with} as
	 * a wither returning an instance, and fails the build for a {@code void} one.
	 */
	public ServiceOffering withdraw() {

		assertActive();

		active = false;
		registerEvent(new ServiceOfferingWithdrawn(id, getServiceId(), Instant.now()));
		return this;
	}

	private void assignSchedule(CapacityPoolId poolId, Duration duration, Duration slotInterval,
			BookingWindow bookingWindow) {

		int durationMinutes = Durations.positiveWholeMinutes(duration, "Duration");
		int intervalMinutes = Durations.positiveWholeMinutes(slotInterval, "Slot interval");

		if (intervalMinutes < MIN_SLOT_INTERVAL_MINUTES || intervalMinutes > MAX_SLOT_INTERVAL_MINUTES) {
			throw new IllegalArgumentException("Slot interval must be between " + MIN_SLOT_INTERVAL_MINUTES + " and "
					+ MAX_SLOT_INTERVAL_MINUTES + " minutes");
		}
		if (durationMinutes % intervalMinutes != 0) {
			throw new IllegalArgumentException("Duration of " + durationMinutes
					+ " minutes must be a multiple of the slot interval of " + intervalMinutes + " minutes");
		}

		Objects.requireNonNull(poolId, "Capacity pool is required");
		this.capacityPool = Association.forId(poolId);
		this.durationMinutes = durationMinutes;
		this.slotIntervalMinutes = intervalMinutes;
		this.bookingWindow = Objects.requireNonNull(bookingWindow, "Booking window is required");
	}

	private void assertActive() {
		if (!active) {
			throw new BusinessRuleViolation("Service offering " + id + " has been withdrawn");
		}
	}

	@Override
	public ServiceOfferingId getId() {
		return id;
	}

	public ServiceId getServiceId() {
		return service.getId();
	}

	public CapacityPoolId getCapacityPoolId() {
		return capacityPool.getId();
	}

	public Duration getDuration() {
		return Duration.ofMinutes(durationMinutes);
	}

	public Duration getSlotInterval() {
		return Duration.ofMinutes(slotIntervalMinutes);
	}

	public BookingWindow getBookingWindow() {
		return bookingWindow;
	}

	public boolean isActive() {
		return active;
	}

	public Long getVersion() {
		return version;
	}
}
