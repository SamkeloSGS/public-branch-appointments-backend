package com.samdev.appointments.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.notification.application.port.TemplateRenderer.MessageValues;
import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.NotificationType;

/**
 * The template files in the codebase: complete, rendered with every value, short enough for one SMS.
 */
class TemplateRendererTests {

	static final ClasspathTemplateRenderer RENDERER = new ClasspathTemplateRenderer(
			ClasspathTemplateRenderer.LOCATION, "en");

	/**
	 * Typical values: a long service and branch name from the demo data.
	 */
	static final MessageValues VALUES = new MessageValues("Thandiwe Nkosi-Mokoena", "ABCD2345",
			"Funeral plan changes", "Johannesburg CBD", "Burnette Street, Hatfield, Pretoria",
			LocalDate.parse("2026-10-14"), LocalTime.parse("09:30"),
			"https://appointments.example.co.za/appointments/ABCD2345");

	@Test
	@DisplayName("NT-13 every type and channel renders in the default language with every placeholder filled")
	void rendersEveryTemplate() {

		for (var type : NotificationType.values()) {
			for (var channel : Channel.values()) {
				var message = RENDERER.render(type, channel, "en", VALUES);
				assertThat(message.body()).as(type + " " + channel).contains("ABCD2345").doesNotContain("{", "}");
				if (channel == Channel.EMAIL) {
					assertThat(message.subject()).as(type + " subject").isNotBlank().doesNotContain("{");
					assertThat(message.body()).contains("Wednesday, 14 October 2026").contains("09:30")
							.contains(VALUES.manageLink());
				} else {
					assertThat(message.subject()).isNull();
				}
			}
		}
	}

	@Test
	@DisplayName("NT-13 SMS messages fit in one 160-character segment for typical values")
	void smsFitsOneSegment() {
		for (var type : NotificationType.values()) {
			for (var language : new String[] { "en", "af", "zu" }) {
				assertThat(RENDERER.render(type, Channel.SMS, language, VALUES).body()).as(type + " " + language)
						.hasSizeLessThanOrEqualTo(160);
			}
		}
	}

	@Test
	@DisplayName("NT-6 messages use the customer's language and fall back to the default without a template")
	void languages() {

		var afrikaans = RENDERER.render(NotificationType.CONFIRMATION, Channel.EMAIL, "af", VALUES);
		assertThat(afrikaans.subject()).startsWith("Afspraak bevestig");
		assertThat(afrikaans.body()).contains("Woensdag");

		var zulu = RENDERER.render(NotificationType.CONFIRMATION, Channel.EMAIL, "zu", VALUES);
		assertThat(zulu.subject()).startsWith("Isikhathi siqinisekisiwe");
		assertThat(zulu.body()).startsWith("Sawubona Thandiwe").contains("ULwesithathu");

		for (var type : NotificationType.values()) {
			for (var channel : Channel.values()) {
				var message = RENDERER.render(type, channel, "zu", VALUES);
				assertThat(message.body()).as("zu " + type + " " + channel).contains("ABCD2345")
						.contains(VALUES.manageLink()).doesNotContain("{", "}");
			}
		}

		// No Xhosa templates yet: English
		var xhosa = RENDERER.render(NotificationType.CONFIRMATION, Channel.EMAIL, "xh", VALUES);
		assertThat(xhosa.subject()).startsWith("Appointment confirmed");
	}

	@Test
	@DisplayName("NT-5 the rebooking message carries the link to choose a new time")
	void rebookingLink() {
		assertThat(RENDERER.render(NotificationType.REBOOKING_REQUIRED, Channel.SMS, "en", VALUES).body())
				.contains(VALUES.manageLink());
	}

	@Test
	@DisplayName("NT-13 a missing default-language template stops the application from starting")
	void missingTemplateFails() {
		assertThatIllegalStateException()
				.isThrownBy(() -> new ClasspathTemplateRenderer("classpath*:notification/incomplete/*.txt", "en"))
				.withMessageContaining("confirmation.sms.en");
	}
}
