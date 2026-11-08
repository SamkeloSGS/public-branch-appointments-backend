package com.samdev.appointments.branchmanagement.web.dto;

import jakarta.validation.constraints.NotBlank;

import com.samdev.appointments.branchmanagement.Address;

public record AddressDto(@NotBlank String street, @NotBlank String city, @NotBlank String province,
		@NotBlank String postalCode) {

	public static AddressDto of(Address address) {
		return new AddressDto(address.street(), address.city(), address.province(), address.postalCode());
	}

	public Address toAddress() {
		return new Address(street, city, province, postalCode);
	}
}
