package com.samdev.appointments.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.ServiceId;
import com.samdev.appointments.customer.CustomerId;

/**
 * The booking root package is its published API, and its events carry no personal data.
 */
class BookingPublishedApiTests {

	static final String API_PACKAGE = AppointmentDirectory.class.getPackageName();

	static final List<Class<?>> rootTypes = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(API_PACKAGE).stream()
			.filter(it -> it.getPackageName().equals(API_PACKAGE))
			.filter(JavaClass::isTopLevelClass)
			.filter(it -> !it.getSimpleName().equals("package-info"))
			.map(JavaClass::reflect)
			.collect(Collectors.toList());

	static final List<Class<?>> events = rootTypes.stream()
			.filter(it -> it.isAnnotationPresent(DomainEvent.class))
			.toList();

	/**
	 * IDs, times, the booking reference and enums: nothing that identifies a person beyond the customer ID.
	 */
	static final Set<Class<?>> ALLOWED_PAYLOAD = Set.of(AppointmentId.class, CustomerId.class,
			ServiceId.class, CapacityPoolId.class, Instant.class, LocalDate.class, String.class, BookedBy.class,
			ClosedReason.class, RebookingCause.class);

	@Test
	@DisplayName("BK-15 no event contains personal data, for every event type in the module")
	void eventsCarryNoPersonalData() {

		assertThat(events).hasSize(9);

		for (var event : events) {
			for (var component : event.getRecordComponents()) {
				assertThat(component.getName().toLowerCase())
						.as(event.getSimpleName() + "." + component.getName())
						.doesNotContain("name", "email", "mail", "phone", "subject", "contact");
				assertThat(component.getType()).as(event.getSimpleName() + "." + component.getName())
						.isIn(ALLOWED_PAYLOAD);
				if (component.getType() == String.class) {
					assertThat(component.getName()).isEqualTo("reference");
				}
			}
			assertThat(event.getRecordComponents()).extracting(it -> it.getName()).contains("occurredAt");
		}
	}

	@Test
	void everyPublicRootTypeIsPartOfTheContract() {

		var contract = Set.of(AppointmentDirectory.class, AppointmentDetails.class, AppointmentId.class,
				AppointmentStatus.class, BookedBy.class, ClosedReason.class, RebookingCause.class);

		var publicTypes = rootTypes.stream()
				.filter(it -> Modifier.isPublic(it.getModifiers()))
				.filter(it -> !it.isAnnotationPresent(DomainEvent.class))
				.collect(Collectors.toSet());

		assertThat(publicTypes).containsExactlyInAnyOrderElementsOf(contract);
	}

	@Test
	void facadeExposesOnlyPublishedAndJdkTypes() {

		var types = Stream.concat(
				Arrays.stream(AppointmentDirectory.class.getMethods())
						.flatMap(it -> Stream.concat(Stream.of(it.getReturnType()), Arrays.stream(it.getParameterTypes()))),
				Arrays.stream(AppointmentDetails.class.getRecordComponents()).map(it -> it.getType()))
				.filter(it -> !it.isPrimitive())
				.toList();

		assertThat(types).allSatisfy(it -> assertThat(it.getName())
				.matches("java\\..*|com\\.samdev\\.appointments\\.(booking|branchmanagement|customer)\\.[A-Z].*"));
	}
}
