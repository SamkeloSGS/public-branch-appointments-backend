package com.samdev.appointments.customer.web.dto.response;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.customer.domain.entity.Customer;
import com.samdev.appointments.customer.domain.vo.CustomerStatus;

/**
 * A search result.
 */
public record CustomerSummaryResponse(UUID id, String givenName, String familyName, @Nullable String email,
		@Nullable String phone, CustomerStatus status) {

	public static CustomerSummaryResponse of(Customer customer) {
		return new CustomerSummaryResponse(customer.getId().value(), customer.getName().givenName(),
				customer.getName().familyName(), customer.getEmail() == null ? null : customer.getEmail().value(),
				customer.getPhone() == null ? null : customer.getPhone().value(), customer.getStatus());
	}
}
