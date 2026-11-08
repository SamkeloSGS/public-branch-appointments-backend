package com.samdev.appointments.branchmanagement.infrastructure.demo;

import java.io.IOException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.ExceptionType;
import com.samdev.appointments.branchmanagement.OverrideReason;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.branchmanagement.application.command.AddCalendarException;
import com.samdev.appointments.branchmanagement.application.command.DefineCapacityPool;
import com.samdev.appointments.branchmanagement.application.command.DefineService;
import com.samdev.appointments.branchmanagement.application.command.DefineServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.OfferService;
import com.samdev.appointments.branchmanagement.application.command.SetCapacityOverride;
import com.samdev.appointments.branchmanagement.application.command.SetWeeklyHours;
import com.samdev.appointments.branchmanagement.application.command.UpdateServiceDetails;
import com.samdev.appointments.branchmanagement.application.handler.CalendarCommandHandler;
import com.samdev.appointments.branchmanagement.application.port.BranchSettings;
import com.samdev.appointments.branchmanagement.application.handler.CapacityPoolCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.OpeningHoursCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCatalogCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCategoryCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceOfferingCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.infrastructure.demo.DemoData.DemoBranch;
import com.samdev.appointments.branchmanagement.infrastructure.demo.DemoData.DemoCategory;
import com.samdev.appointments.branchmanagement.infrastructure.demo.DemoData.DemoService;

/**
 * Loads Capitec demo data from {@code demo/branch-management.json} at startup when the {@code local} profile is
 * active: the service catalog, and for the configured branch its weekly hours, pools, offerings and calendar.
 * Everything goes through the module's command handlers, so all rules apply and the usual domain events are
 * published. Idempotent: categories and services that already exist (by segment and name, and name) are skipped,
 * and the branch is only set up while it has no capacity pools, so restarting with the profile adds nothing.
 */
@Component
@Profile("local")
class DemoDataLoader implements ApplicationRunner {

	static final String RESOURCE = "demo/branch-management.json";

	private static final Logger LOG = LoggerFactory.getLogger(DemoDataLoader.class);
	private static final Duration SLOT_INTERVAL = Duration.ofMinutes(15);
	private static final List<MonthDay> PUBLIC_HOLIDAYS = List.of(MonthDay.of(1, 1), MonthDay.of(3, 21),
			MonthDay.of(4, 27), MonthDay.of(5, 1), MonthDay.of(6, 16), MonthDay.of(8, 9), MonthDay.of(9, 24),
			MonthDay.of(12, 16), MonthDay.of(12, 25), MonthDay.of(12, 26));

	private final JsonMapper json;
	private final Clock clock;
	private final BranchProfile branch;
	private final ServiceCategoryCommandHandler categories;
	private final ServiceCatalogCommandHandler services;
	private final OpeningHoursCommandHandler openingHours;
	private final CapacityPoolCommandHandler pools;
	private final ServiceOfferingCommandHandler offerings;
	private final CalendarCommandHandler calendar;
	private final BranchQueries queries;

	DemoDataLoader(JsonMapper json, Clock clock, BranchSettings settings, ServiceCategoryCommandHandler categories,
			ServiceCatalogCommandHandler services, OpeningHoursCommandHandler openingHours,
			CapacityPoolCommandHandler pools, ServiceOfferingCommandHandler offerings, CalendarCommandHandler calendar,
			BranchQueries queries) {

		this.json = json;
		this.clock = clock;
		this.branch = settings.branch();
		this.categories = categories;
		this.services = services;
		this.openingHours = openingHours;
		this.pools = pools;
		this.offerings = offerings;
		this.calendar = calendar;
		this.queries = queries;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {

		DemoData data;
		try (var in = new ClassPathResource(RESOURCE).getInputStream()) {
			data = json.readValue(in, DemoData.class);
		}

		var catalog = loadCatalog(data.categories());

		if (!queries.pools().isEmpty()) {
			LOG.info("Demo data: {} catalog services; branch {} already set up", catalog.size(), branch.code());
			return;
		}

		var offered = setUpBranch(data.branch(), catalog);

		LOG.info("Demo data: {} catalog services, branch {} set up with {} offerings", catalog.size(), branch.code(),
				offered);
	}

	// Catalog

	/**
	 * A service of the catalog with what is needed to offer it.
	 */
	record CatalogService(ServiceId id, CustomerSegment segment, boolean smartIdOnly, DemoService definition) {}

	private List<CatalogService> loadCatalog(List<DemoCategory> demoCategories) {

		var existingCategories = queries.categories(null, true).stream()
				.collect(Collectors.toMap(it -> key(it.getSegment(), it.getName()), it -> it.getId()));
		var existingServices = queries.allServices().stream()
				.collect(Collectors.toMap(it -> it.getName().toLowerCase(), Function.identity()));

		var catalog = new ArrayList<CatalogService>();

		for (var category : demoCategories) {

			var categoryId = existingCategories.computeIfAbsent(key(category.segment(), category.name()),
					it -> categories.handle(
							new DefineServiceCategory(category.name(), category.segment(), category.description())));

			for (var service : category.services()) {

				var prerequisites = service.prerequisites() == null ? List.<String> of() : service.prerequisites();
				var existing = existingServices.get(service.name().toLowerCase());
				var serviceId = existing != null ? existing.getId()
						: services.handle(new DefineService(service.name(), categoryId, service.description(),
								Duration.ofMinutes(service.minutes()), prerequisites));

				// Services loaded before prerequisites existed get them once; later edits are kept
				if (existing != null && existing.getPrerequisites().isEmpty() && !prerequisites.isEmpty()) {
					services.handle(new UpdateServiceDetails(serviceId, null, existing.getDescription(), prerequisites));
				}

				catalog.add(new CatalogService(serviceId, category.segment(), category.onlyWithSmartId(), service));
			}
		}

		return catalog;
	}

	// Branch

	/**
	 * @return the number of offerings
	 */
	private int setUpBranch(DemoBranch demo, List<CatalogService> catalog) {

		openingHours.handle(new SetWeeklyHours(null, weeklyHours(demo)));

		var poolsBySegment = definePools(demo);
		var homeAffairsDesk = demo.smartId() ? pools.handle(new DefineCapacityPool("Home Affairs desk", 1)) : null;
		var offered = 0;

		for (var service : catalog) {
			if (!branch.serves(service.segment()) || (service.smartIdOnly() && !demo.smartId())) {
				continue;
			}

			var poolId = service.smartIdOnly() ? homeAffairsDesk : poolsBySegment.get(service.segment());
			var definition = service.definition();

			offerings.handle(new OfferService(service.id(), poolId, null, SLOT_INTERVAL,
					Duration.ofMinutes(orDefault(definition.leadTimeMinutes(), 60)),
					orDefault(definition.maxAdvanceDays(), 30)));
			offered++;
		}

		addCalendar(poolsBySegment);

		return offered;
	}

	private static Map<DayOfWeek, List<TimeRange>> weeklyHours(DemoBranch demo) {

		var hours = new EnumMap<DayOfWeek, List<TimeRange>>(DayOfWeek.class);
		var weekdays = List.of(new TimeRange(demo.weekdays().opens(), demo.weekdays().closes()));

		for (var day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
				DayOfWeek.FRIDAY)) {
			hours.put(day, weekdays);
		}
		if (demo.saturday() != null) {
			hours.put(DayOfWeek.SATURDAY, List.of(new TimeRange(demo.saturday().opens(), demo.saturday().closes())));
		}

		return hours;
	}

	/**
	 * Consultants serve personal customers and business bankers serve business customers, for the segments the
	 * branch serves.
	 */
	private Map<CustomerSegment, CapacityPoolId> definePools(DemoBranch demo) {

		var result = new EnumMap<CustomerSegment, CapacityPoolId>(CustomerSegment.class);

		if (branch.serves(CustomerSegment.PERSONAL)) {
			result.put(CustomerSegment.PERSONAL,
					pools.handle(new DefineCapacityPool("Consultants", demo.consultants())));
		}
		if (branch.serves(CustomerSegment.BUSINESS)) {
			result.put(CustomerSegment.BUSINESS,
					pools.handle(new DefineCapacityPool("Business bankers", demo.businessBankers())));
		}

		return result;
	}

	/**
	 * The next three public holidays closed, Christmas Eve as a half day, and a few capacity overrides: a training
	 * day, a short-staffed afternoon and extra business bankers at month end.
	 */
	private void addCalendar(Map<CustomerSegment, CapacityPoolId> pools) {

		var today = LocalDate.now(clock.withZone(branch.timeZone()));

		upcomingHolidays(today, 3).forEach(date -> calendar.handle(
				new AddCalendarException(date, ExceptionType.CLOSED, List.of(), "Public holiday")));

		var christmasEve = next(MonthDay.of(12, 24), today);
		calendar.handle(new AddCalendarException(christmasEve, ExceptionType.SPECIAL_HOURS,
				List.of(TimeRange.of("08:00", "12:00")), "Christmas Eve"));

		var consultants = pools.get(CustomerSegment.PERSONAL);
		var bankers = pools.get(CustomerSegment.BUSINESS);

		if (consultants != null) {
			calendar.handle(new SetCapacityOverride(consultants,
					today.with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY)), null, 1, OverrideReason.TRAINING,
					"Product training"));
			calendar.handle(new SetCapacityOverride(consultants,
					today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)), TimeRange.of("13:00", "15:00"), 1,
					OverrideReason.SHORT_STAFFED, "Two consultants on leave"));
		}
		if (bankers != null) {
			calendar.handle(new SetCapacityOverride(bankers, lastWeekdayOfMonthFrom(today), null, 4,
					OverrideReason.EXTRA_STAFF, "Month-end business demand"));
		}
	}

	private static List<LocalDate> upcomingHolidays(LocalDate today, int count) {
		return PUBLIC_HOLIDAYS.stream()
				.map(it -> next(it, today))
				.sorted()
				.limit(count)
				.toList();
	}

	/**
	 * The next occurrence of the day from tomorrow on.
	 */
	private static LocalDate next(MonthDay day, LocalDate today) {
		var date = day.atYear(today.getYear());
		return date.isAfter(today) ? date : day.atYear(today.getYear() + 1);
	}

	private static LocalDate lastWeekdayOfMonthFrom(LocalDate today) {

		Function<LocalDate, LocalDate> lastWeekday = month -> {
			var date = month.with(TemporalAdjusters.lastDayOfMonth());
			while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
				date = date.minusDays(1);
			}
			return date;
		};

		var thisMonth = lastWeekday.apply(today);
		return thisMonth.isAfter(today) ? thisMonth : lastWeekday.apply(today.plusMonths(1));
	}

	private static String key(CustomerSegment segment, String name) {
		return segment + ":" + name.strip().toLowerCase();
	}

	private static int orDefault(@Nullable Integer value, int fallback) {
		return value == null ? fallback : value;
	}
}
