package com.samdev.appointments.customer.web.dto.response;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.domain.entity.Customer;
import com.samdev.appointments.customer.domain.vo.CustomerStatus;

/**
 * One customer. Name and contact details are empty once the customer is erased.
 *
 * @param linked whether the customer has a login account
 */
public record CustomerResponse(UUID id, @Nullable String givenName, @Nullable String familyName,
		@Nullable String email, @Nullable String phone, boolean emailEnabled, boolean smsEnabled, String language,
		CustomerStatus status, boolean linked, long version) {

	public static CustomerResponse of(Customer customer) {

		var name = customer.getName();
		var preferences = customer.getPreferences();

		return new CustomerResponse(customer.getId().value(), name == null ? null : name.givenName(),
				name == null ? null : name.familyName(),
				customer.getEmail() == null ? null : customer.getEmail().value(),
				customer.getPhone() == null ? null : customer.getPhone().value(), preferences.emailEnabled(),
				preferences.smsEnabled(), preferences.language(), customer.getStatus(),
				customer.getIdentitySubject() != null, customer.getVersion());
	}
}
