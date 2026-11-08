package com.samdev.appointments.customer.application.query;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.customer.CustomerContact;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.IdentitySubject;
import com.samdev.appointments.customer.domain.entity.Customer;
import com.samdev.appointments.customer.domain.repository.Customers;

/**
 * The read-only facade for other modules. System access: nothing is written to the access audit.
 */
@Component
@Transactional(readOnly = true)
class JpaCustomerDirectory implements CustomerDirectory {

	private final Customers customers;

	JpaCustomerDirectory(Customers customers) {
		this.customers = customers;
	}

	@Override
	public Optional<CustomerId> findByIdentity(IdentitySubject subject) {
		return customers.findByIdentitySubject(subject.value()).map(Customer::getId);
	}

	@Override
	public boolean isActive(CustomerId id) {
		return customers.findById(id).filter(Customer::isActive).isPresent();
	}

	@Override
	public Optional<CustomerContact> contactFor(CustomerId id) {
		return customers.findById(id)
				.filter(it -> !it.isErased())
				.map(it -> {
					var preferences = it.getPreferences();
					var email = preferences.emailEnabled() && it.getEmail() != null ? it.getEmail().value() : null;
					var phone = preferences.smsEnabled() && it.getPhone() != null ? it.getPhone().value() : null;
					return new CustomerContact(id, it.getName().displayName(), preferences.language(), email, phone);
				});
	}

	@Override
	public Map<CustomerId, String> displayNames(Set<CustomerId> ids) {

		var result = new HashMap<CustomerId, String>();
		ids.forEach(it -> result.put(it, FORMER_CUSTOMER));

		if (!ids.isEmpty()) {
			customers.findByIdIn(ids).stream()
					.filter(it -> !it.isErased())
					.forEach(it -> result.put(it.getId(), it.getName().displayName()));
		}

		return result;
	}
}
