package com.samdev.appointments.notification.domain.vo;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * What a provider said about one attempt.
 *
 * @param providerMessageId set when accepted
 * @param errorCode a provider error code or category when not accepted; reduced before it is stored
 */
public record SendOutcome(Kind kind, @Nullable String providerMessageId, @Nullable String errorCode) {

	public enum Kind {

		ACCEPTED,

		/** Worth trying again later: timeouts, throttling, provider outages. */
		TRANSIENT,

		/** Will never work: invalid address, blocked number. */
		PERMANENT
	}

	public SendOutcome {
		Objects.requireNonNull(kind);
	}

	public static SendOutcome accepted(String providerMessageId) {
		return new SendOutcome(Kind.ACCEPTED, providerMessageId, null);
	}

	public static SendOutcome transientFailure(String errorCode) {
		return new SendOutcome(Kind.TRANSIENT, null, errorCode);
	}

	public static SendOutcome permanentFailure(String errorCode) {
		return new SendOutcome(Kind.PERMANENT, null, errorCode);
	}
}
