package com.samdev.appointments;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The verification that must stay green (spec section 3.4).
 */
class ArchitectureTests {

	static final ApplicationModules modules = ApplicationModules.of(AppointmentsApplication.class);

	static final JavaClasses classes = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackagesOf(AppointmentsApplication.class);

	@Test
	void moduleBoundariesAreRespected() {
		modules.verify();
	}

	@Test
	void aggregatesReferenceEachOtherOnlyByIdentifierOrAssociation() {
		JMoleculesDddRules.all().check(classes);
	}

	@Test
	void ringDependenciesPointInward() {
		JMoleculesArchitectureRules.ensureOnionSimple().check(classes);
	}

	@Test
	void writesModuleDocumentation() {
		new Documenter(modules).writeDocumentation();
	}
}
