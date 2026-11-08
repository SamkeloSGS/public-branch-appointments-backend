package com.samdev.appointments.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.samdev.appointments.AppointmentsApplication;

/**
 * Notification publishes no events and exposes no facade, and no other module depends on it.
 */
class NotificationPublishedApiTests {

	static final String API_PACKAGE = "com.samdev.appointments.notification";

	@Test
	@DisplayName("NT-15 the module's root package exposes nothing")
	void exposesNothing() {

		var publicRootTypes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages(API_PACKAGE).stream()
				.filter(it -> it.getPackageName().equals(API_PACKAGE))
				.filter(JavaClass::isTopLevelClass)
				.filter(it -> !it.getSimpleName().equals("package-info"))
				.filter(it -> it.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
				.toList();

		assertThat(publicRootTypes).isEmpty();
	}

	@Test
	@DisplayName("NT-15 nothing in the system depends on the notification module")
	void nothingDependsOnIt() {

		var modules = ApplicationModules.of(AppointmentsApplication.class);
		var notification = modules.getModuleByName("notification").orElseThrow();

		assertThat(modules.stream().filter(it -> !it.equals(notification))
				.filter(it -> it.getBootstrapDependencies(modules).anyMatch(notification::equals))).isEmpty();
	}
}
