package com.samdev.appointments.branchmanagement;

import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The branch this application runs for. Fixed by configuration ({@code appointments.branch}), never stored.
 *
 * @param code short code, for example on reports
 * @param segments the customer segments the branch serves: personal, business or both; never empty
 */
public record BranchProfile(String code, String name, Address address, GeoLocation location, ZoneId timeZone,
		Set<CustomerSegment> segments) {

	public BranchProfile {
		code = Validation.text(code, "Branch code", 1, 10);
		name = Validation.text(name, "Branch name", 1, 120);
		Objects.requireNonNull(address, "Branch address is required");
		Objects.requireNonNull(location, "Branch location is required");
		Objects.requireNonNull(timeZone, "Branch time zone is required");

		if (segments == null || segments.isEmpty()) {
			throw new IllegalArgumentException("The branch must serve at least one customer segment");
		}
		segments = Collections.unmodifiableSet(EnumSet.copyOf(segments));
	}

	public boolean serves(CustomerSegment segment) {
		return segments.contains(segment);
	}
}
