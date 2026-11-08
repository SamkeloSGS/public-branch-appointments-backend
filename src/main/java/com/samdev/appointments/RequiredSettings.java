package com.samdev.appointments;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Stops the start-up when a setting refers to a variable nobody set, such as {@code ${DATABASE_URL}} in production
 * without that environment variable. Spring would otherwise bind most of them as the literal text
 * {@code "${DATABASE_URL}"}. Runs once the configuration files and Vault are read; registered in
 * {@code META-INF/spring.factories}.
 */
class RequiredSettings implements EnvironmentPostProcessor {

	/** A placeholder without a default: {@code ${NAME}} but not {@code ${NAME:default}} */
	private static final Pattern REQUIRED = Pattern.compile("\\$\\{([^:}$]+)}");

	private static final Set<String> SKIPPED = Set.of("configurationProperties",
			StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
			StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

		var missing = new TreeSet<String>();
		for (var source : environment.getPropertySources()) {
			if (!(source instanceof EnumerablePropertySource<?> enumerable) || SKIPPED.contains(source.getName())) {
				continue;
			}
			for (var name : enumerable.getPropertyNames()) {
				try {
					environment.getProperty(name);
				}
				catch (IllegalArgumentException unresolved) {
					var matcher = REQUIRED.matcher(String.valueOf(enumerable.getProperty(name)));
					while (matcher.find()) {
						if (!environment.containsProperty(matcher.group(1))) {
							missing.add(matcher.group(1));
						}
					}
				}
			}
		}
		if (!missing.isEmpty()) {
			throw new IllegalStateException("Required settings are not set: " + String.join(", ", missing)
					+ ". Set them as environment variables (or Vault keys), or run locally with the local profile.");
		}
	}
}
