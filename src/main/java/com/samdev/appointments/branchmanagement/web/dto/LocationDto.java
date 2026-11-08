package com.samdev.appointments.branchmanagement.web.dto;

import jakarta.validation.constraints.NotNull;

import com.samdev.appointments.branchmanagement.GeoLocation;

public record LocationDto(@NotNull Double latitude, @NotNull Double longitude) {

	public static LocationDto of(GeoLocation location) {
		return new LocationDto(location.latitude(), location.longitude());
	}

	public GeoLocation toLocation() {
		return new GeoLocation(latitude, longitude);
	}
}
