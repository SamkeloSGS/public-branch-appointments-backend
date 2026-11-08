package com.samdev.appointments.booking;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.samdev.appointments.booking.application.port.ScheduleProjection;
import com.samdev.appointments.booking.application.port.ScheduleProjection.OfferingView;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ModuleFixtures;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.ServiceOfferingId;
import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.branchmanagement.application.command.DefineCapacityPool;
import com.samdev.appointments.branchmanagement.application.command.OfferService;
import com.samdev.appointments.branchmanagement.application.handler.CapacityPoolCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceOfferingCommandHandler;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.application.command.RegisterCustomerByStaff;
import com.samdev.appointments.customer.application.handler.CustomerCommandHandler;
import com.samdev.appointments.identity.Subject;

/**
 * Sets up bookable services through Branch management's commands and customers through Customer's, then waits until
 * this module's projection has caught up with the events.
 * <p>
 * Every service gets its own pool, so tests do not share capacity. The branch's hours and calendar are shared: the
 * branch is on {@link ModuleFixtures#EVERY_DAY}, and a test that adds a calendar exception books on its own
 * {@link ModuleFixtures#uniqueDate()} with {@link OpenService#on(LocalDate)}.
 */
@Component
public class BookingFixtures {

	public static final ZoneId ZONE = ModuleFixtures.ZONE;

	static final int MAX_ADVANCE_DAYS = 60;

	private static final List<TimeRange> STANDARD_DAY = List.of(TimeRange.of("08:00", "17:00"));

	private final ModuleFixtures branchFixtures;
	private final CapacityPoolCommandHandler pools;
	private final ServiceOfferingCommandHandler offerings;
	private final CustomerCommandHandler customers;
	private final ScheduleProjection projection;

	BookingFixtures(ModuleFixtures branchFixtures, CapacityPoolCommandHandler pools,
			ServiceOfferingCommandHandler offerings, CustomerCommandHandler customers, ScheduleProjection projection) {

		this.branchFixtures = branchFixtures;
		this.pools = pools;
		this.offerings = offerings;
		this.customers = customers;
		this.projection = projection;
	}

	/**
	 * A 30-minute service on a 30-minute grid in its own pool, no lead time and {@value #MAX_ADVANCE_DAYS} days
	 * ahead, booked tomorrow.
	 */
	public OpenService openService(int capacity) {
		return openService(capacity, Duration.ZERO);
	}

	public OpenService openService(int capacity, Duration minLeadTime) {

		branchFixtures.standardHours();

		var poolId = pools.handle(new DefineCapacityPool(ModuleFixtures.uniqueName("Pool"), capacity));
		var serviceId = branchFixtures.service();
		var offeringId = offerings.handle(new OfferService(serviceId, poolId, Duration.ofMinutes(30),
				Duration.ofMinutes(30), minLeadTime, MAX_ADVANCE_DAYS));

		var service = new OpenService(serviceId, poolId, offeringId, tomorrow());
		awaitBookable(service);

		return service;
	}

	/**
	 * Another service in its own pool, booked on the same date.
	 */
	public OpenService secondPool(OpenService service, int capacity, int durationMinutes) {

		var poolId = pools.handle(new DefineCapacityPool(ModuleFixtures.uniqueName("Pool"), capacity));
		var serviceId = branchFixtures.service();
		var offeringId = offerings.handle(new OfferService(serviceId, poolId, Duration.ofMinutes(durationMinutes),
				Duration.ofMinutes(30), Duration.ZERO, MAX_ADVANCE_DAYS));

		var second = new OpenService(serviceId, poolId, offeringId, service.date());
		awaitBookable(second);

		return second;
	}

	/**
	 * Waits until the projection has the offering and the branch's standard hours for the service's date.
	 */
	public void awaitBookable(OpenService service) {
		await().atMost(Duration.ofSeconds(15)).until(() -> projection.offering(service.serviceId())
				.filter(OfferingView::active).isPresent()
				&& projection.inputs(service.poolId(), service.date()).hours().equals(STANDARD_DAY));
	}

	public CustomerId customer() {
		return customers.handle(new RegisterCustomerByStaff(new Subject("booking-fixtures"), "Test", "Customer",
				"booking-" + UUID.randomUUID().toString().substring(0, 8) + "@customers.test", null, null));
	}

	public static LocalDate tomorrow() {
		return LocalDate.now(ZONE).plusDays(1);
	}

	public static LocalDateTime tomorrowAt(String time) {
		return tomorrow().atTime(LocalTime.parse(time));
	}

	/**
	 * A bookable service and the date tests book it on.
	 */
	public record OpenService(ServiceId serviceId, CapacityPoolId poolId, ServiceOfferingId offeringId,
			LocalDate date) {

		/**
		 * The same service, booked on another date.
		 */
		public OpenService on(LocalDate other) {
			return new OpenService(serviceId, poolId, offeringId, other);
		}

		public LocalDateTime at(String time) {
			return date.atTime(LocalTime.parse(time));
		}
	}
}
