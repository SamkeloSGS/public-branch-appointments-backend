package com.samdev.appointments.branchmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ValueTypeTests {

	@Test
	@DisplayName("BM-2 time range requires start before end")
	void timeRangeRequiresStartBeforeEnd() {

		assertThat(TimeRange.of("09:00", "17:00").start()).isEqualTo(LocalTime.of(9, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> TimeRange.of("17:00", "09:00"));
		assertThatIllegalArgumentException().isThrownBy(() -> TimeRange.of("09:00", "09:00"));
	}

	@Test
	void timeRangeIsHalfOpen() {

		var range = TimeRange.of("09:00", "12:00");

		assertThat(range.contains(LocalTime.of(9, 0))).isTrue();
		assertThat(range.contains(LocalTime.of(11, 59))).isTrue();
		assertThat(range.contains(LocalTime.of(12, 0))).isFalse();
		assertThat(range.overlaps(TimeRange.of("12:00", "13:00"))).isFalse();
		assertThat(range.overlaps(TimeRange.of("11:00", "13:00"))).isTrue();
		assertThat(range.overlaps(TimeRange.of("10:00", "11:00"))).isTrue();
	}

	@Test
	@DisplayName("BM-2 overlapping ranges are rejected, others are sorted")
	void sortsAndRejectsOverlaps() {

		assertThat(TimeRange.sortedWithoutOverlaps(List.of(TimeRange.of("13:00", "17:00"), TimeRange.of("09:00", "12:00"))))
				.extracting(TimeRange::start)
				.containsExactly(LocalTime.of(9, 0), LocalTime.of(13, 0));

		assertThatIllegalArgumentException().isThrownBy(() -> TimeRange.sortedWithoutOverlaps(
				List.of(TimeRange.of("09:00", "12:00"), TimeRange.of("11:30", "13:00"))));
	}

	@ParameterizedTest
	@CsvSource({ "-90.1, 0", "90.1, 0", "0, -180.1", "0, 180.1" })
	void geoLocationValidatesRanges(double latitude, double longitude) {
		assertThatIllegalArgumentException().isThrownBy(() -> new GeoLocation(latitude, longitude));
	}

	@Test
	void geoLocationComputesDistance() {

		var johannesburg = new GeoLocation(-26.2041, 28.0473);
		var pretoria = new GeoLocation(-25.7479, 28.2293);

		assertThat(johannesburg.distanceInKmTo(pretoria)).isBetween(50.0, 60.0);
		assertThat(johannesburg.distanceInKmTo(johannesburg)).isZero();
	}

	@Test
	void addressRequiresAllPartsAndTrims() {

		assertThat(new Address(" 1 Main Rd ", "Cape Town", "Western Cape", "8001").street()).isEqualTo("1 Main Rd");
		assertThatIllegalArgumentException().isThrownBy(() -> new Address("1 Main Rd", " ", "Western Cape", "8001"));
		assertThatIllegalArgumentException().isThrownBy(() -> new Address(null, "Cape Town", "Western Cape", "8001"));
	}

	@Test
	@DisplayName("BM-1 the configured branch needs a code, a name and at least one customer segment")
	void branchProfileValidates() {

		var address = new Address("1 Main Rd", "Cape Town", "Western Cape", "8001");
		var location = new GeoLocation(-33.9, 18.4);
		var zone = ZoneId.of("Africa/Johannesburg");
		var both = Set.of(CustomerSegment.PERSONAL, CustomerSegment.BUSINESS);

		var branch = new BranchProfile(" MAIN ", "Main Branch", address, location, zone, both);

		assertThat(branch.code()).isEqualTo("MAIN");
		assertThat(branch.serves(CustomerSegment.BUSINESS)).isTrue();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new BranchProfile(" ", "Main Branch", address, location, zone, both));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new BranchProfile("MAIN", "x".repeat(121), address, location, zone, both));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new BranchProfile("MAIN", "Main Branch", address, location, zone, Set.of()));
	}
}
