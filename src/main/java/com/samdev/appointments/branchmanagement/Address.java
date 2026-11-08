package com.samdev.appointments.branchmanagement;

import org.jmolecules.ddd.types.ValueObject;

public record Address(String street, String city, String province, String postalCode) implements ValueObject {

	public Address {
		street = Validation.text(street, "Street", 1, 200);
		city = Validation.text(city, "City", 1, 100);
		province = Validation.text(province, "Province", 1, 100);
		postalCode = Validation.text(postalCode, "Postal code", 1, 20);
	}
}
