package com.samdev.appointments.notification.infrastructure.messaging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.samdev.appointments.notification.application.port.TemplateRenderer;
import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.NotificationType;
import com.samdev.appointments.notification.infrastructure.config.NotificationProperties;

/**
 * Templates are files named {@code <type>.<channel>.<language>.txt} under {@code notification/templates}, such as
 * {@code confirmation.email.en.txt}. Email templates start with a {@code Subject:} line and a blank line. Placeholders:
 * {@code {customerName}}, {@code {reference}}, {@code {service}}, {@code {branch}}, {@code {branchAddress}},
 * {@code {date}}, {@code {shortDate}}, {@code {time}} and {@code {link}}. Dates are formatted in the customer's
 * language; the values are already local to the branch.
 * <p>
 * Construction fails, and with it the application, when a type and channel lack a default-language template.
 */
@Component
public class ClasspathTemplateRenderer implements TemplateRenderer {

	public static final String LOCATION = "classpath*:notification/templates/*.txt";

	private static final Pattern NAME = Pattern.compile("([a-z_]+)\\.(email|sms)\\.([a-z]{2,3})\\.txt");

	private static final String REGION = "ZA";

	private final Map<String, String> templates = new HashMap<>();
	private final String defaultLanguage;

	@Autowired
	ClasspathTemplateRenderer(NotificationProperties properties) {
		this(LOCATION, properties.defaultLanguage());
	}

	public ClasspathTemplateRenderer(String location, String defaultLanguage) {

		this.defaultLanguage = defaultLanguage;

		try {
			for (var resource : new PathMatchingResourcePatternResolver().getResources(location)) {
				var matcher = NAME.matcher(String.valueOf(resource.getFilename()));
				if (matcher.matches()) {
					templates.put(key(matcher.group(1), matcher.group(2), matcher.group(3)),
							resource.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		for (var type : NotificationType.values()) {
			for (var channel : Channel.values()) {
				if (!templates.containsKey(key(type, channel, defaultLanguage))) {
					throw new IllegalStateException("Missing notification template "
							+ key(type, channel, defaultLanguage) + ".txt; every type and channel needs one in the "
							+ "default language");
				}
			}
		}
	}

	@Override
	public RenderedMessage render(NotificationType type, Channel channel, String language, MessageValues values) {

		var resolvedLanguage = templates.containsKey(key(type, channel, language)) ? language : defaultLanguage;
		var template = templates.get(key(type, channel, resolvedLanguage));
		// Customers are in South Africa: en-ZA and af-ZA write dates the way they expect
		var locale = Locale.of(resolvedLanguage, REGION);

		var text = template
				.replace("{customerName}", values.customerName())
				.replace("{reference}", values.reference())
				.replace("{service}", values.serviceName())
				.replace("{branchAddress}", values.branchAddress())
				.replace("{branch}", values.branchName())
				.replace("{date}", DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
						.format(values.date()))
				.replace("{shortDate}", DateTimeFormatter.ofPattern("EEE d MMM", locale).format(values.date()))
				.replace("{time}", DateTimeFormatter.ofPattern("HH:mm").format(values.time()))
				.replace("{link}", values.manageLink())
				.strip();

		if (channel == Channel.SMS) {
			return new RenderedMessage(null, text);
		}

		var lines = text.split("\n", 2);

		if (!lines[0].startsWith("Subject:")) {
			throw new IllegalStateException("Email template " + key(type, channel, resolvedLanguage)
					+ " must start with a Subject: line");
		}

		return new RenderedMessage(lines[0].substring("Subject:".length()).strip(),
				lines.length > 1 ? lines[1].strip() : "");
	}

	private static String key(NotificationType type, Channel channel, String language) {
		return key(type.name().toLowerCase(Locale.ROOT), channel.name().toLowerCase(Locale.ROOT), language);
	}

	private static String key(String type, String channel, String language) {
		return type + "." + channel + "." + language;
	}
}
