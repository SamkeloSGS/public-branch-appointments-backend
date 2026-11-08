package com.samdev.appointments.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The identity root package is its published API, and its events carry no personal data.
 */
class IdentityPublishedApiTests {

	static final String API_PACKAGE = AccessControl.class.getPackageName();

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
	@DisplayName("IA-16 no event contains a name, email or subject, for every event type in the module")
	void eventsCarryNoPersonalData() {

		assertThat(events).hasSize(6);

		for (var event : events) {
			for (var component : event.getRecordComponents()) {
				assertThat(component.getName().toLowerCase())
						.as(event.getSimpleName() + "." + component.getName())
						.doesNotContain("name", "email", "subject", "mail");
				assertThat(component.getType()).as(event.getSimpleName() + "." + component.getName())
						.isNotEqualTo(Subject.class);
			}
			assertThat(event.getRecordComponents()).extracting(it -> it.getName())
					.contains("staffMemberId", "occurredAt");
		}
	}

	@Test
	void everyPublicRootTypeIsPartOfTheContract() {

		var contract = Set.of(AccessControl.class, CurrentUser.class, StaffRole.class, Subject.class,
				StaffMemberId.class, PublicEndpoints.class, RequiresRole.class,
				LoginAccounts.class);

		var publicTypes = rootTypes.stream()
				.filter(it -> Modifier.isPublic(it.getModifiers()))
				.filter(it -> !it.isAnnotationPresent(DomainEvent.class))
				.collect(Collectors.toSet());

		assertThat(publicTypes).containsExactlyInAnyOrderElementsOf(contract);
	}

	@Test
	void facadeExposesOnlyApiAndJdkTypes() {

		var types = Arrays.stream(AccessControl.class.getMethods())
				.flatMap(it -> java.util.stream.Stream.concat(java.util.stream.Stream.of(it.getReturnType()),
						Arrays.stream(it.getParameterTypes())))
				.filter(it -> !it.isPrimitive())
				.toList();

		assertThat(types).allSatisfy(it -> assertThat(it.getName()).matches("java\\..*|" + API_PACKAGE.replace(".", "\\.") + "\\..*"));
	}
}
