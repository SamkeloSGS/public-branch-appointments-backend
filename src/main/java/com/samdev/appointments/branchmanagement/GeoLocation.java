package com.samdev.appointments.branchmanagement;

import org.jmolecules.ddd.types.ValueObject;

public record GeoLocation(double latitude, double longitude) implements ValueObject {

	private static final double EARTH_RADIUS_KM = 6371.0;

	public GeoLocation {
		if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
			throw new IllegalArgumentException("Latitude must be between -90 and 90");
		}
		if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
			throw new IllegalArgumentException("Longitude must be between -180 and 180");
		}
	}

	/**
	 * Great-circle distance using the haversine formula.
	 */
	public double distanceInKmTo(GeoLocation other) {
		double dLat = Math.toRadians(other.latitude - latitude);
		double dLng = Math.toRadians(other.longitude - longitude);
		double a = Math.pow(Math.sin(dLat / 2), 2)
				+ Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude)) * Math.pow(Math.sin(dLng / 2), 2);
		return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
	}
}
