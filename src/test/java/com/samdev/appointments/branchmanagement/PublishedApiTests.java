package com.samdev.appointments.branchmanagement;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.Test;

/**
 * The module's root package is its published API. Everything public in it must be part of the contract other
 * modules consume: the domain events, the {@link BranchDirectory} facade, and the types their signatures expose.
 * Helpers that only this module needs belong in a sub-package or must not be public.
 */
class PublishedApiTests {

	static final String API_PACKAGE = BranchDirectory.class.getPackageName();

	static final Set<Class<?>> rootTypes = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(API_PACKAGE).stream()
			.filter(it -> it.getPackageName().equals(API_PACKAGE))
			.filter(JavaClass::isTopLevelClass)
			.filter(it -> !it.getSimpleName().equals("package-info"))
			.map(JavaClass::reflect)
			.collect(Collectors.toSet());

	@Test
	void everyPublicTypeInTheApiPackageIsReachableFromEventsOrTheFacade() {

		var publicTypes = names(rootTypes.stream().filter(it -> Modifier.isPublic(it.getModifiers())).toList());

		assertThat(names(reachableFromContract()))
				.as("Public types in %s that no event or BranchDirectory signature exposes", API_PACKAGE)
				.containsAll(publicTypes);
	}

	@Test
	void contractExposesOnlyApiJdkAndJMoleculesTypes() {

		var leaked = new TreeSet<String>();

		for (var type : reachableFromContract()) {
			for (var used : signatureTypes(type)) {
				var name = used.getName();
				var allowed = used.isPrimitive() || name.startsWith("java.") || name.startsWith("org.jmolecules.")
						|| used.getPackageName().equals(API_PACKAGE);
				if (!allowed) {
					leaked.add(type.getSimpleName() + " -> " + name);
				}
			}
		}

		assertThat(leaked).as("Published types exposing module-internal or framework types").isEmpty();
	}

	/**
	 * The events and the facade, plus every API-package type their signatures use, transitively.
	 */
	private static Set<Class<?>> reachableFromContract() {

		var entryPoints = rootTypes.stream()
				.filter(it -> it.isAnnotationPresent(DomainEvent.class) || it.equals(BranchDirectory.class))
				.toList();

		var reachable = new HashSet<Class<?>>();
		var queue = new ArrayDeque<Class<?>>(entryPoints);

		while (!queue.isEmpty()) {
			var type = queue.poll();
			if (!reachable.add(type)) {
				continue;
			}
			signatureTypes(type).stream()
					.filter(it -> !it.isPrimitive() && it.getPackageName().equals(API_PACKAGE))
					.map(PublishedApiTests::topLevel)
					.forEach(queue::add);
		}

		return reachable;
	}

	/**
	 * Types used in the public surface of a class: record components, public fields, public methods and
	 * constructors, and public nested types.
	 */
	private static Set<Class<?>> signatureTypes(Class<?> type) {

		var types = new HashSet<Type>();

		if (type.isRecord()) {
			for (var component : type.getRecordComponents()) {
				types.add(component.getGenericType());
			}
		}
		for (var field : type.getDeclaredFields()) {
			if (Modifier.isPublic(field.getModifiers())) {
				types.add(field.getGenericType());
			}
		}
		for (var method : type.getDeclaredMethods()) {
			if (Modifier.isPublic(method.getModifiers()) && !method.isSynthetic()) {
				types.add(method.getGenericReturnType());
				types.addAll(List.of(method.getGenericParameterTypes()));
			}
		}
		for (var constructor : type.getDeclaredConstructors()) {
			if (Modifier.isPublic(constructor.getModifiers())) {
				types.addAll(List.of(constructor.getGenericParameterTypes()));
			}
		}

		var classes = new HashSet<Class<?>>();
		types.forEach(it -> collect(it, classes));

		for (var nested : type.getDeclaredClasses()) {
			if (Modifier.isPublic(nested.getModifiers())) {
				classes.addAll(signatureTypes(nested));
			}
		}

		return classes;
	}

	private static void collect(Type type, Set<Class<?>> classes) {
		switch (type) {
			case Class<?> c when c.isArray() -> collect(c.getComponentType(), classes);
			case Class<?> c -> classes.add(c);
			case ParameterizedType p -> {
				collect(p.getRawType(), classes);
				for (var argument : p.getActualTypeArguments()) {
					collect(argument, classes);
				}
			}
			case WildcardType w -> {
				for (var bound : w.getUpperBounds()) {
					collect(bound, classes);
				}
			}
			case GenericArrayType g -> collect(g.getGenericComponentType(), classes);
			default -> {}
		}
	}

	private static Class<?> topLevel(Class<?> type) {
		return type.getEnclosingClass() == null ? type : topLevel(type.getEnclosingClass());
	}

	private static Set<String> names(Collection<Class<?>> types) {
		return types.stream().map(Class::getSimpleName).collect(Collectors.toCollection(TreeSet::new));
	}
}
