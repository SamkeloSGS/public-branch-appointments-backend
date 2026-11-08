package com.samdev.appointments.customer.domain.vo;

import org.jmolecules.ddd.types.ValueObject;

/**
 * Given and family name, each 1 to 80 characters after trimming. Embedded in the customer row.
 */
public record PersonName(String givenName, String familyName) implements ValueObject {

	public PersonName {
		givenName = part(givenName, "Given name");
		familyName = part(familyName, "Family name");
	}

	public String displayName() {
		return givenName + " " + familyName;
	}

	private static String part(String value, String label) {

		if (value == null || value.isBlank() || value.strip().length() > 80) {
			throw new IllegalArgumentException(label + " must be 1 to 80 characters");
		}

		return value.strip();
	}
}
