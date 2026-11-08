package com.samdev.appointments.branchmanagement.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.branchmanagement.TimeRange;

class ValueObjectTests {

	@Test
	@DisplayName("BM-5 base capacity is at least 1")
	void capacityIsAtLeastOne() {

		assertThat(new Capacity(1).value()).isEqualTo(1);
		assertThatIllegalArgumentException().isThrownBy(() -> new Capacity(0));
	}

	@Test
	void bookingWindowValidatesBounds() {

		assertThat(new BookingWindow(0, 1)).isNotNull();
		assertThat(new BookingWindow(120, 365).minLeadTime()).isEqualTo(Duration.ofHours(2));
		assertThatIllegalArgumentException().isThrownBy(() -> new BookingWindow(-1, 30));
		assertThatIllegalArgumentException().isThrownBy(() -> new BookingWindow(0, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new BookingWindow(0, 366));
		assertThatIllegalArgumentException().isThrownBy(() -> BookingWindow.of(Duration.ofSeconds(30), 30));
	}

	@Test
	@DisplayName("BM-2 a day without ranges is closed")
	void weeklyHoursCoverEveryDay() {

		var hours = WeeklyHours.of(Map.of(DayOfWeek.MONDAY, List.of(TimeRange.of("09:00", "17:00"))));

		assertThat(hours.asMap()).hasSize(7);
		assertThat(hours.on(DayOfWeek.MONDAY)).hasSize(1);
		assertThat(hours.on(DayOfWeek.SUNDAY)).isEmpty();
		assertThat(hours.isEmpty()).isFalse();
		assertThat(WeeklyHours.none().isEmpty()).isTrue();
	}

	@Test
	@DisplayName("BM-2 overlapping ranges within a day are rejected")
	void weeklyHoursRejectOverlaps() {

		assertThatIllegalArgumentException().isThrownBy(() -> WeeklyHours.of(Map.of(DayOfWeek.MONDAY,
				List.of(TimeRange.of("09:00", "13:00"), TimeRange.of("12:00", "17:00")))));
	}

	@Test
	void weeklyHoursRoundTripThroughOpeningPeriods() {

		var hours = WeeklyHours.of(Map.of(
				DayOfWeek.MONDAY, List.of(TimeRange.of("13:00", "17:00"), TimeRange.of("08:00", "12:00")),
				DayOfWeek.SATURDAY, List.of(TimeRange.of("09:00", "12:00"))));

		assertThat(WeeklyHours.fromPeriods(hours.toPeriods())).isEqualTo(hours);
	}
}
