package com.samdev.appointments.branchmanagement.infrastructure.config;

import java.time.ZoneId;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.samdev.appointments.branchmanagement.Address;
import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.GeoLocation;

/**
 * The branch this application runs for. Each deployment sets its own; checked at start-up. Address and location are
 * bound to their own records: the domain value types get an extra constructor from the jMolecules plugin, so Spring
 * cannot bind them.
 */
@ConfigurationProperties("appointments.branch")
record BranchProperties(String code, String name, AddressProperties address, LocationProperties location,
		ZoneId timeZone, Set<CustomerSegment> segments) {

	BranchProfile toProfile() {
		return new BranchProfile(code, name,
				address == null ? null
						: new Address(address.street(), address.city(), address.province(), address.postalCode()),
				location == null ? null : new GeoLocation(location.latitude(), location.longitude()), timeZone,
				segments);
	}

	record AddressProperties(String street, String city, String province, String postalCode) {}

	record LocationProperties(double latitude, double longitude) {}
}
