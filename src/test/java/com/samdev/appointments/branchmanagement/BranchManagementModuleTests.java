package com.samdev.appointments.branchmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

import com.samdev.appointments.SharedPersistenceConfiguration;
import com.samdev.appointments.TestcontainersConfiguration;
import com.samdev.appointments.branchmanagement.application.command.AddCalendarException;
import com.samdev.appointments.branchmanagement.application.command.ChangePoolBaseCapacity;
import com.samdev.appointments.branchmanagement.application.command.ChangeServiceOffering;
import com.samdev.appointments.branchmanagement.application.command.DefineCapacityPool;
import com.samdev.appointments.branchmanagement.application.command.DefineService;
import com.samdev.appointments.branchmanagement.application.command.DefineServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.MoveServiceToCategory;
import com.samdev.appointments.branchmanagement.application.command.OfferService;
import com.samdev.appointments.branchmanagement.application.command.RemoveCalendarException;
import com.samdev.appointments.branchmanagement.application.command.RemoveCapacityOverride;
import com.samdev.appointments.branchmanagement.application.command.RetireService;
import com.samdev.appointments.branchmanagement.application.command.RetireServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.SetCapacityOverride;
import com.samdev.appointments.branchmanagement.application.command.SetWeeklyHours;
import com.samdev.appointments.branchmanagement.application.command.UpdateServiceDetails;
import com.samdev.appointments.branchmanagement.application.command.UpdateServiceCategory;
import com.samdev.appointments.branchmanagement.application.command.WithdrawServiceOffering;
import com.samdev.appointments.branchmanagement.application.exception.NotFoundException;
import com.samdev.appointments.branchmanagement.application.exception.StaleVersionException;
import com.samdev.appointments.branchmanagement.application.handler.CalendarCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.CapacityPoolCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.OpeningHoursCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCatalogCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceCategoryCommandHandler;
import com.samdev.appointments.branchmanagement.application.handler.ServiceOfferingCommandHandler;
import com.samdev.appointments.branchmanagement.application.query.BranchQueries;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.branchmanagement.domain.vo.WeeklyHours;

/**
 * Command to persisted state to published event, for every command of section 6, against PostgreSQL.
 */
@ApplicationModuleTest
@Import({ TestcontainersConfiguration.class, SharedPersistenceConfiguration.class })
class BranchManagementModuleTests {

	@Autowired OpeningHoursCommandHandler openingHours;
	@Autowired ServiceCatalogCommandHandler services;
	@Autowired ServiceCategoryCommandHandler categories;
	@Autowired CapacityPoolCommandHandler pools;
	@Autowired ServiceOfferingCommandHandler offerings;
	@Autowired CalendarCommandHandler calendar;
	@Autowired BranchQueries queries;
	@Autowired BranchDirectory directory;
	@Autowired ModuleFixtures fixtures;

	static final LocalDate TOMORROW = LocalDate.now(ModuleFixtures.ZONE).plusDays(1);

	// BM-1

	@Test
	@DisplayName("BM-1 the branch comes from configuration and serves both segments by default")
	void branchFromConfiguration() {

		var branch = directory.branch();

		assertThat(branch.code()).isEqualTo("MAIN");
		assertThat(branch.name()).isEqualTo("Main Branch");
		assertThat(branch.address().city()).isEqualTo("Cape Town");
		assertThat(branch.timeZone()).isEqualTo(ModuleFixtures.ZONE);
		assertThat(branch.segments()).containsExactlyInAnyOrder(CustomerSegment.PERSONAL, CustomerSegment.BUSINESS);
	}

	// BM-2

	@Test
	@DisplayName("BM-2 SetWeeklyHours stores the hours and publishes the full new schedule")
	void setsWeeklyHours(PublishedEvents events) {

		try {
			var event = exactlyOne(events, WeeklyHoursChanged.class, () -> {
				openingHours.handle(new SetWeeklyHours(null, ModuleFixtures.WEEKDAYS));
				return null;
			});

			assertThat(event.weeklyHours()).hasSize(7);
			assertThat(queries.openingHours().getWeeklyHours().on(DayOfWeek.TUESDAY)).hasSize(2);
			assertThat(queries.openingHours().getWeeklyHours().on(DayOfWeek.SUNDAY)).isEmpty();
		} finally {
			fixtures.standardHours();
		}
	}

	@Test
	@DisplayName("BM-2 overlapping ranges are rejected and nothing changes")
	void rejectsOverlappingHours() {

		fixtures.standardHours();

		assertThatIllegalArgumentException().isThrownBy(() -> openingHours.handle(new SetWeeklyHours(null,
				Map.of(DayOfWeek.FRIDAY, List.of(TimeRange.of("08:00", "12:00"), TimeRange.of("11:00", "13:00"))))));

		assertThat(queries.openingHours().getWeeklyHours().asMap()).isEqualTo(
				WeeklyHours.of(ModuleFixtures.EVERY_DAY).asMap());
	}

	// BM-4

	@Test
	@DisplayName("BM-4 DefineService and RetireService publish their events; retired services cannot be offered")
	void serviceCatalog(PublishedEvents events) {

		var name = ModuleFixtures.uniqueName("Passport renewal");
		var categoryId = fixtures.category(CustomerSegment.PERSONAL);

		var serviceId = exactlyOne(events, ServiceDefined.class, () -> services
				.handle(new DefineService(name, categoryId, "Bring your ID", Duration.ofMinutes(45)))).serviceId();

		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> services
				.handle(new DefineService(name.toUpperCase(), categoryId, null, Duration.ofMinutes(10))));

		exactlyOne(events, ServiceRetired.class, () -> {
			services.handle(new RetireService(serviceId, null));
			return null;
		});

		var poolId = fixtures.pool();

		assertThatExceptionOfType(BusinessRuleViolation.class)
				.isThrownBy(() -> fixtures.offering(serviceId, poolId))
				.withMessageContaining("retired");
	}

	@Test
	@DisplayName("BM-4 UpdateServiceDetails replaces the description and prerequisites and publishes one event")
	void serviceDetails(PublishedEvents events) {

		var serviceId = services.handle(new DefineService(ModuleFixtures.uniqueName("Open an account"),
				fixtures.category(CustomerSegment.PERSONAL), null, Duration.ofMinutes(30), List.of("Your ID document")));

		assertThat(queries.service(serviceId).getPrerequisites()).containsExactly("Your ID document");

		exactlyOne(events, ServiceDetailsUpdated.class, () -> {
			services.handle(new UpdateServiceDetails(serviceId, 0L, "Open a transactional account",
					List.of("Your ID document", "Proof of address")));
			return null;
		});

		var service = queries.service(serviceId);
		assertThat(service.getDescription()).isEqualTo("Open a transactional account");
		assertThat(service.getPrerequisites()).containsExactly("Your ID document", "Proof of address");
		assertThatExceptionOfType(StaleVersionException.class).isThrownBy(
				() -> services.handle(new UpdateServiceDetails(serviceId, 0L, null, List.of())));
	}

	@Test
	@DisplayName("BM-13 service category commands publish their events; names are unique per segment")
	void serviceCategories(PublishedEvents events) {

		var name = ModuleFixtures.uniqueName("Loans");

		var defined = exactlyOne(events, ServiceCategoryDefined.class,
				() -> categories.handle(new DefineServiceCategory(name, CustomerSegment.PERSONAL, "Home loans")));

		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> categories
				.handle(new DefineServiceCategory(name.toUpperCase(), CustomerSegment.PERSONAL, null)));

		// The same name is fine for business customers
		categories.handle(new DefineServiceCategory(name, CustomerSegment.BUSINESS, null));

		exactlyOne(events, ServiceCategoryUpdated.class, () -> {
			categories.handle(new UpdateServiceCategory(defined.categoryId(), 0L, name + " and credit", null));
			return null;
		});
		exactlyOne(events, ServiceCategoryRetired.class, () -> {
			categories.handle(new RetireServiceCategory(defined.categoryId(), 1L));
			return null;
		});

		assertThat(directory.findServiceCategory(defined.categoryId())).hasValueSatisfying(it -> {
			assertThat(it.name()).isEqualTo(name + " and credit");
			assertThat(it.active()).isFalse();
		});
		assertThatExceptionOfType(BusinessRuleViolation.class)
				.isThrownBy(() -> fixtures.service(defined.categoryId()))
				.withMessageContaining("retired");
	}

	@Test
	@DisplayName("BM-13 services look up their category and move only within their segment")
	void servicesInCategories(PublishedEvents events) {

		var personal = fixtures.category(CustomerSegment.PERSONAL);
		var otherPersonal = fixtures.category(CustomerSegment.PERSONAL);
		var business = fixtures.category(CustomerSegment.BUSINESS);

		var defined = exactlyOne(events, ServiceDefined.class, () -> services.handle(new DefineService(
				ModuleFixtures.uniqueName("Account opening"), personal, null, Duration.ofMinutes(20))));

		assertThat(defined.categoryId()).isEqualTo(personal);
		assertThat(defined.segment()).isEqualTo(CustomerSegment.PERSONAL);

		var moved = exactlyOne(events, ServiceMovedToCategory.class, () -> {
			services.handle(new MoveServiceToCategory(defined.serviceId(), 0L, otherPersonal));
			return null;
		});

		assertThat(moved.categoryId()).isEqualTo(otherPersonal);
		assertThat(directory.findService(defined.serviceId())).hasValueSatisfying(
				it -> assertThat(it.categoryId()).isEqualTo(otherPersonal));

		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> services
				.handle(new MoveServiceToCategory(defined.serviceId(), null, business)));
		assertThatExceptionOfType(StaleVersionException.class).isThrownBy(() -> services
				.handle(new MoveServiceToCategory(defined.serviceId(), 0L, personal)));
		assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> services.handle(new DefineService(
				ModuleFixtures.uniqueName("X"), ServiceCategoryId.random(), null, Duration.ofMinutes(20))));
	}

	@Test
	@DisplayName("BM-13 the branch offers services of both segments it serves")
	void branchSegments() {

		var poolId = fixtures.pool();

		fixtures.offering(fixtures.service(CustomerSegment.PERSONAL), poolId);
		fixtures.offering(fixtures.service(CustomerSegment.BUSINESS), poolId);
	}

	// BM-5

	@Test
	@DisplayName("BM-5 DefineCapacityPool and ChangePoolBaseCapacity publish their events")
	void capacityPools(PublishedEvents events) {

		var poolId = exactlyOne(events, CapacityPoolDefined.class,
				() -> pools.handle(new DefineCapacityPool(ModuleFixtures.uniqueName("Consultants"), 3))).poolId();

		var changed = exactlyOne(events, CapacityPoolBaseCapacityChanged.class, () -> {
			pools.handle(new ChangePoolBaseCapacity(poolId, null, 5));
			return null;
		});

		assertThat(changed.baseCapacity()).isEqualTo(5);
		assertThat(queries.pool(poolId).getBaseCapacity().value()).isEqualTo(5);
	}

	@Test
	@DisplayName("BM-5 duplicate pool names are rejected; base capacity below 1 is invalid")
	void rejectsInvalidPools() {

		var name = ModuleFixtures.uniqueName("Consultants");
		pools.handle(new DefineCapacityPool(name, 3));

		assertThatExceptionOfType(BusinessRuleViolation.class)
				.isThrownBy(() -> pools.handle(new DefineCapacityPool(name.toLowerCase(), 1)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> pools.handle(new DefineCapacityPool(ModuleFixtures.uniqueName("Tellers"), 0)));
	}

	// BM-6

	@Test
	@DisplayName("BM-6 OfferService, ChangeServiceOffering and WithdrawServiceOffering publish their events")
	void offerings(PublishedEvents events) {

		var serviceId = fixtures.service();
		var poolId = fixtures.pool();
		var otherPool = fixtures.pool();

		var offered = exactlyOne(events, ServiceOffered.class, () -> offerings.handle(new OfferService(serviceId,
				poolId, null, Duration.ofMinutes(15), Duration.ofMinutes(60), 14)));

		assertThat(offered.durationMinutes()).isEqualTo(30);
		var offeringId = offered.offeringId();

		var changed = exactlyOne(events, ServiceOfferingChanged.class, () -> {
			offerings.handle(new ChangeServiceOffering(offeringId, null, otherPool, Duration.ofMinutes(60),
					Duration.ofMinutes(30), Duration.ZERO, 30));
			return null;
		});
		assertThat(changed.poolId()).isEqualTo(otherPool);

		exactlyOne(events, ServiceOfferingWithdrawn.class, () -> {
			offerings.handle(new WithdrawServiceOffering(offeringId, null));
			return null;
		});

		assertThat(queries.offering(offeringId).isActive()).isFalse();
	}

	@Test
	@DisplayName("BM-6 duplicate offerings and unknown pools are rejected")
	void rejectsInvalidOfferings() {

		var serviceId = fixtures.service();
		var poolId = fixtures.pool();
		fixtures.offering(serviceId, poolId);

		assertThatExceptionOfType(BusinessRuleViolation.class)
				.isThrownBy(() -> fixtures.offering(serviceId, poolId))
				.withMessageContaining("already offered");

		assertThatExceptionOfType(NotFoundException.class)
				.isThrownBy(() -> fixtures.offering(fixtures.service(), CapacityPoolId.random()));
	}

	// BM-7

	@Test
	@DisplayName("BM-7 AddCalendarException and RemoveCalendarException publish their events")
	void calendarExceptions(PublishedEvents events) {

		var date = ModuleFixtures.uniqueDate();

		var added = exactlyOne(events, CalendarExceptionAdded.class, () -> calendar.handle(new AddCalendarException(
				date, ExceptionType.SPECIAL_HOURS, List.of(TimeRange.of("09:00", "12:00")), "Half day")));

		assertThat(queries.upcomingExceptions()).anyMatch(it -> it.getId().equals(added.exceptionId()));

		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> calendar
				.handle(new AddCalendarException(date, ExceptionType.CLOSED, List.of(), null)));

		exactlyOne(events, CalendarExceptionRemoved.class, () -> {
			calendar.handle(new RemoveCalendarException(added.exceptionId()));
			return null;
		});

		assertThat(queries.upcomingExceptions()).noneMatch(it -> it.getDate().equals(date));
	}

	@Test
	@DisplayName("BM-7 a date in the past in the branch's time zone is rejected")
	void rejectsPastException() {

		var yesterday = LocalDate.now(ModuleFixtures.ZONE).minusDays(1);

		assertThatIllegalArgumentException().isThrownBy(() -> calendar
				.handle(new AddCalendarException(yesterday, ExceptionType.CLOSED, List.of(), null)));
	}

	// BM-8

	@Test
	@DisplayName("BM-8 SetCapacityOverride and RemoveCapacityOverride publish their events")
	void capacityOverrides(PublishedEvents events) {

		var poolId = fixtures.pool();

		var set = exactlyOne(events, CapacityOverrideSet.class, () -> calendar.handle(new SetCapacityOverride(
				poolId, TOMORROW, null, 9, OverrideReason.EXTRA_STAFF, "Campaign")));

		assertThat(set.capacity()).isEqualTo(9);

		exactlyOne(events, CapacityOverrideRemoved.class, () -> {
			calendar.handle(new RemoveCapacityOverride(set.overrideId()));
			return null;
		});

		assertThat(queries.overrides(TOMORROW, TOMORROW)).noneMatch(it -> it.getCapacityPoolId().equals(poolId));
	}

	@Test
	@DisplayName("BM-8 conflicting whole-day or overlapping windowed overrides are rejected")
	void rejectsConflictingOverrides() {

		var poolId = fixtures.pool();
		var otherPool = fixtures.pool();

		calendar.handle(new SetCapacityOverride(poolId, TOMORROW, null, 1, OverrideReason.LEAVE, null));
		calendar.handle(new SetCapacityOverride(poolId, TOMORROW, TimeRange.of("09:00", "12:00"), 0,
				OverrideReason.TRAINING, null));

		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> calendar.handle(
				new SetCapacityOverride(poolId, TOMORROW, null, 2, OverrideReason.LEAVE, null)));
		assertThatExceptionOfType(BusinessRuleViolation.class).isThrownBy(() -> calendar.handle(new SetCapacityOverride(
				poolId, TOMORROW, TimeRange.of("11:00", "13:00"), 2, OverrideReason.LEAVE, null)));

		// Adjacent window and another pool are fine
		calendar.handle(new SetCapacityOverride(poolId, TOMORROW, TimeRange.of("12:00", "13:00"), 2,
				OverrideReason.LEAVE, null));
		calendar.handle(new SetCapacityOverride(otherPool, TOMORROW, null, 2, OverrideReason.LEAVE, null));

		assertThat(queries.overrides(TOMORROW, TOMORROW))
				.filteredOn(it -> Set.of(poolId, otherPool).contains(it.getCapacityPoolId()))
				.hasSize(4);
	}

	@Test
	@DisplayName("BM-8 an unknown pool is not found")
	void rejectsUnknownPool() {

		assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> calendar.handle(new SetCapacityOverride(
				CapacityPoolId.random(), TOMORROW, null, 1, OverrideReason.LEAVE, null)));
	}

	// BM-12

	@Test
	@DisplayName("BM-12 a command based on an outdated version is rejected")
	void rejectsStaleVersion() {

		var version = queries.openingHours().getVersion();

		openingHours.handle(new SetWeeklyHours(version, ModuleFixtures.EVERY_DAY));

		assertThatExceptionOfType(StaleVersionException.class)
				.isThrownBy(() -> openingHours.handle(new SetWeeklyHours(version, Map.of())));
	}

	// Section 8

	@Test
	@DisplayName("BranchDirectory exposes the branch, services and a schedule snapshot")
	void branchDirectory() {

		var offered = fixtures.offeredService();
		var date = ModuleFixtures.uniqueDate();
		var closure = calendar.handle(new AddCalendarException(date, ExceptionType.CLOSED, List.of(), null));
		calendar.handle(new SetCapacityOverride(offered.poolId(), TOMORROW, TimeRange.of("09:00", "10:00"), 1,
				OverrideReason.SHORT_STAFFED, null));

		assertThat(directory.findService(offered.serviceId())).isPresent();
		assertThat(directory.findService(ServiceId.random())).isEmpty();

		var snapshot = directory.scheduleSnapshot();

		assertThat(snapshot.timeZone()).isEqualTo(ModuleFixtures.ZONE);
		assertThat(snapshot.weeklyHours().get(DayOfWeek.MONDAY)).hasSize(1);
		assertThat(snapshot.pools()).filteredOn(it -> it.id().equals(offered.poolId()))
				.singleElement().satisfies(it -> assertThat(it.baseCapacity()).isEqualTo(2));
		assertThat(snapshot.offerings()).filteredOn(it -> it.id().equals(offered.offeringId()))
				.singleElement().satisfies(it -> assertThat(it.poolId()).isEqualTo(offered.poolId()));
		assertThat(snapshot.calendarExceptions()).anyMatch(it -> it.id().equals(closure));
		assertThat(snapshot.capacityOverrides()).filteredOn(it -> it.poolId().equals(offered.poolId()))
				.singleElement()
				.satisfies(it -> assertThat(it.window()).isEqualTo(TimeRange.of("09:00", "10:00")));
	}

	/**
	 * Runs the command and asserts it published exactly one domain event, of the given type (BM-10).
	 */
	private static <T> T exactlyOne(PublishedEvents events, Class<T> type, Supplier<?> command) {

		var before = domainEvents(events).size();
		command.get();
		var after = domainEvents(events);

		assertThat(after).hasSize(before + 1);
		assertThat(after.get(after.size() - 1)).isInstanceOf(type);

		return type.cast(after.get(after.size() - 1));
	}

	private static List<Object> domainEvents(PublishedEvents events) {

		var result = new ArrayList<Object>();
		events.ofType(Object.class).forEach(it -> {
			if (it.getClass().isAnnotationPresent(DomainEvent.class)) {
				result.add(it);
			}
		});

		return result;
	}
}
