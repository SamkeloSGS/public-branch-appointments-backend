package com.samdev.appointments.notification.infrastructure.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.samdev.appointments.notification.application.port.NotificationSettings;
import com.samdev.appointments.notification.domain.vo.RetryPolicy;

/**
 * @param manageUrl where customers manage an appointment; {@code {reference}} is replaced by the booking reference
 * @param emailFrom the sender address of notification emails
 * @param webhookSecrets per provider name, the shared secret that signs its delivery callbacks
 */
@ConfigurationProperties("appointments.notification")
public record NotificationProperties(@DefaultValue("PT24H") Duration reminderLeadTime,
		@DefaultValue("100") int batchSize, @DefaultValue("5") int maxAttempts,
		@DefaultValue({ "PT1M", "PT5M", "PT15M", "PT1H" }) List<Duration> retryDelays,
		@DefaultValue("P365D") Duration retention,
		String manageUrl, @DefaultValue("en") String defaultLanguage, String emailFrom,
		@Nullable Map<String, String> webhookSecrets) implements NotificationSettings {

	public NotificationProperties {
		webhookSecrets = webhookSecrets == null ? Map.of() : Map.copyOf(webhookSecrets);
	}

	@Override
	public RetryPolicy retryPolicy() {
		return new RetryPolicy(maxAttempts, retryDelays);
	}

	@Override
	public String manageLink(String reference) {
		return manageUrl.replace("{reference}", reference);
	}
}
