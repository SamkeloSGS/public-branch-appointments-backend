package com.samdev.appointments.notification.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorCodeTests {

	@Test
	@DisplayName("NT-11 error codes are kept; addresses and numbers in provider errors are removed before storage")
	void reducesErrors() {

		assertThat(ErrorCode.reduce("INVALID_ADDRESS")).isEqualTo("INVALID_ADDRESS");
		assertThat(ErrorCode.reduce("smtp:550")).isEqualTo("smtp:550");
		assertThat(ErrorCode.reduce(null)).isNull();

		assertThat(ErrorCode.reduce("550 Mailbox thandi.nkosi@example.test unavailable"))
				.isEqualTo("550 Mailbox [address] unavailable")
				.doesNotContain("@");
		assertThat(ErrorCode.reduce("Number +27 82 555 0199 is blocked")).isEqualTo("Number [number] is blocked");
		assertThat(ErrorCode.reduce("+27825550199")).isEqualTo("[number]");
		assertThat(ErrorCode.reduce("x".repeat(500))).hasSize(ErrorCode.MAX_LENGTH);
	}
}
