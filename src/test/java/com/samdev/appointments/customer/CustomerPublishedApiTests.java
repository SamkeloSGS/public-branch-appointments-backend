package com.samdev.appointments.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The customer root package is its published API, and its events carry no personal data.
 */
class CustomerPublishedApiTests {

	static final String API_PACKAGE = CustomerDirectory.class.getPackageName();

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

	@Test
	@DisplayName("CU-10 no event contains a name, email, phone or identity subject, for every event type in the module")
	void eventsCarryNoPersonalData() {

		assertThat(events).hasSize(7);

		for (var event : events) {
			for (var component : event.getRecordComponents()) {
				assertThat(component.getName().toLowerCase())
						.as(event.getSimpleName() + "." + component.getName())
						.doesNotContain("name", "email", "mail", "phone", "subject", "contact");
				assertThat(component.getType()).as(event.getSimpleName() + "." + component.getName())
						.isIn(CustomerId.class, Instant.class, boolean.class);
			}
			assertThat(event.getRecordComponents()).extracting(it -> it.getName())
					.contains("customerId", "occurredAt");
		}
	}

	@Test
	@DisplayName("CU-10 serialised events, as stored in the publication registry, contain only IDs, flags and times")
	void serialisedEventsCarryNoPersonalData() throws Exception {

		var json = JsonMapper.builder().build();
		var id = CustomerId.random();
		var now = Instant.now();

		for (var event : events) {
			var arguments = Arrays.stream(event.getRecordComponents())
					.map(it -> it.getType() == CustomerId.class ? id : it.getType() == Instant.class ? now : true)
					.toArray();
			var instance = event.getDeclaredConstructors()[0].newInstance(arguments);

			var fields = json.readValue(json.writeValueAsString(instance), Map.class).keySet();
			assertThat(fields).as(event.getSimpleName()).isSubsetOf("customerId", "selfRegistered", "occurredAt");
		}
	}

	@Test
	void everyPublicRootTypeIsPartOfTheContract() {

		var contract = Set.of(CustomerDirectory.class, CustomerContact.class, CustomerId.class, IdentitySubject.class);

		var publicTypes = rootTypes.stream()
				.filter(it -> Modifier.isPublic(it.getModifiers()))
				.filter(it -> !it.isAnnotationPresent(DomainEvent.class))
				.collect(Collectors.toSet());

		assertThat(publicTypes).containsExactlyInAnyOrderElementsOf(contract);
	}

	@Test
	void facadeExposesOnlyApiAndJdkTypes() {

		var types = Arrays.stream(CustomerDirectory.class.getMethods())
				.flatMap(it -> Stream.concat(Stream.of(it.getReturnType()), Arrays.stream(it.getParameterTypes())))
				.filter(it -> !it.isPrimitive())
				.toList();

		assertThat(types).allSatisfy(
				it -> assertThat(it.getName()).matches("java\\..*|" + API_PACKAGE.replace(".", "\\.") + "\\..*"));
	}
}
