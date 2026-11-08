package com.samdev.appointments.branchmanagement.domain.entity;

import static com.samdev.appointments.branchmanagement.domain.entity.DomainFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.samdev.appointments.branchmanagement.WeeklyHoursChanged;
import com.samdev.appointments.branchmanagement.domain.vo.WeeklyHours;

class OpeningHoursTests {

	@Test
	@DisplayName("BM-2 the branch starts with no hours, so nothing can be booked until they are set")
	void startsEmpty() {

		var hours = openingHours();

		assertThat(hours.isEmpty()).isTrue();
		assertThat(hours.getWeeklyHours()).isEqualTo(WeeklyHours.none());
	}

	@Test
	@DisplayName("BM-2 setting weekly hours registers the full new schedule")
	void setsWeeklyHours() {

		var hours = openingHours();

		hours.change(weekdays());

		assertThat(hours.getWeeklyHours()).isEqualTo(weekdays());

		var event = singleEvent(hours, WeeklyHoursChanged.class);
		assertThat(event.weeklyHours()).hasSize(7);
		assertThat(event.weeklyHours().get(DayOfWeek.MONDAY)).hasSize(2);
		assertThat(event.weeklyHours().get(DayOfWeek.SUNDAY)).isEmpty();
	}

	@Test
	@DisplayName("BM-2 closing every day leaves no hours")
	void closesEveryDay() {

		var hours = openingHours();
		hours.change(weekdays());
		clearEvents(hours);

		hours.change(WeeklyHours.none());

		assertThat(hours.isEmpty()).isTrue();
		singleEvent(hours, WeeklyHoursChanged.class);
	}
}
