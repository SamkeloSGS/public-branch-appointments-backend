package com.samdev.appointments.branchmanagement;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.samdev.appointments.branchmanagement.application.command.DefineCapacityPool;
import com.samdev.appointments.branchmanagement.application.command.DefineService;
import com.samdev.appointments.branchmanagement.application.command.DefineServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.OfferService;
import com.samdev.appointments.branchmanagement.application.command.SetWeeklyHours;
import com.samdev.appointments.branchmanagement.application.handler.CapacityPoolCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.OpeningHoursCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCatalogCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCategoryCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceOfferingCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.domain.vo.WeeklyHours;

/**
 * Sets up data through the module's own command handlers. Tests share one database and the one configured branch
 * (see {@code appointments.branch} in {@code application.yml}), so every name is unique, every test keeps to the
 * branch's standard hours ({@link #EVERY_DAY}), and calendar exceptions use {@link #uniqueDate()}.
 */
@Component
public class ModuleFixtures {

	/**
	 * The configured branch's time zone.
	 */
	public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

	/**
	 * The standard hours every test relies on: open every day from 08:00 to 17:00.
	 */
	public static final Map<DayOfWeek, List<TimeRange>> EVERY_DAY = Arrays.stream(DayOfWeek.values())
			.collect(Collectors.toMap(Function.identity(), it -> List.of(TimeRange.of("08:00", "17:00"))));

	/**
	 * Different hours for tests of the hours themselves, which set {@link #EVERY_DAY} again afterwards.
	 */
	public static final Map<DayOfWeek, List<TimeRange>> WEEKDAYS = Map.of(
			DayOfWeek.MONDAY, List.of(TimeRange.of("08:00", "17:00")),
			DayOfWeek.TUESDAY, List.of(TimeRange.of("08:00", "12:00"), TimeRange.of("13:00", "17:00")));

	/**
	 * Days from today of the next {@link #uniqueDate()}. Starts after the week from tomorrow that tests book on and ask
	 * availability for, and stays inside the booking window of the booking fixtures' offerings for 50 tests.
	 */
	private static final AtomicInteger NEXT_DAY = new AtomicInteger(10);

	private final OpeningHoursCommandHandler openingHours;
	private final ServiceCatalogCommandHandler services;
	private final ServiceCategoryCommandHandler categories;
	private final CapacityPoolCommandHandler pools;
	private final ServiceOfferingCommandHandler offerings;
	private final BranchQueries queries;

	ModuleFixtures(OpeningHoursCommandHandler openingHours, ServiceCatalogCommandHandler services,
			ServiceCategoryCommandHandler categories, CapacityPoolCommandHandler pools,
			ServiceOfferingCommandHandler offerings, BranchQueries queries) {

		this.openingHours = openingHours;
		this.services = services;
		this.categories = categories;
		this.pools = pools;
		this.offerings = offerings;
		this.queries = queries;
	}

	public static String uniqueCode() {
		return "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	}

	public static String uniqueName(String prefix) {
		return prefix + " " + uniqueCode();
	}

	/**
	 * A future date no other test uses, for calendar exceptions: a calendar exception closes or changes the date for
	 * every pool of the branch.
	 */
	public static LocalDate uniqueDate() {
		return LocalDate.now(ZONE).plusDays(NEXT_DAY.getAndIncrement());
	}

	/**
	 * Sets the branch's standard hours, {@link #EVERY_DAY}, unless it already has them.
	 */
	public void standardHours() {
		if (!queries.openingHours().getWeeklyHours().equals(WeeklyHours.of(EVERY_DAY))) {
			openingHours.handle(new SetWeeklyHours(null, EVERY_DAY));
		}
	}

	public ServiceCategoryId category(CustomerSegment segment) {
		return categories.handle(new DefineServiceCategory(uniqueName("Category"), segment, null));
	}

	/**
	 * A personal-banking service in a new category.
	 */
	public ServiceId service() {
		return service(category(CustomerSegment.PERSONAL));
	}

	public ServiceId service(CustomerSegment segment) {
		return service(category(segment));
	}

	public ServiceId service(ServiceCategoryId category) {
		return services.handle(new DefineService(uniqueName("Service"), category, null, Duration.ofMinutes(30)));
	}

	public CapacityPoolId pool() {
		return pools.handle(new DefineCapacityPool(uniqueName("Pool"), 2));
	}

	public ServiceOfferingId offering(ServiceId serviceId, CapacityPoolId poolId) {
		return offerings.handle(new OfferService(serviceId, poolId, null, Duration.ofMinutes(15), Duration.ZERO, 30));
	}

	/**
	 * A new service offered in a new pool, with the branch on its standard hours.
	 */
	public OfferedService offeredService() {

		var serviceId = service();
		var poolId = pool();
		var offeringId = offering(serviceId, poolId);

		standardHours();

		return new OfferedService(serviceId, poolId, offeringId);
	}

	public record OfferedService(ServiceId serviceId, CapacityPoolId poolId, ServiceOfferingId offeringId) {}
}
