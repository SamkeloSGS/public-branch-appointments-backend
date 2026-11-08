package com.samdev.appointments.customer.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.samdev.appointments.customer.CustomerId;
import com.samdev.appointments.customer.domain.entity.Customer;
import com.samdev.appointments.customer.domain.vo.CustomerStatus;

public interface Customers extends Repository<Customer, CustomerId> {

	Customer save(Customer customer);

	Optional<Customer> findById(CustomerId id);

	List<Customer> findByIdIn(Collection<CustomerId> ids);

	Optional<Customer> findByIdentitySubject(String identitySubject);

	Optional<Customer> findByEmailAndStatusNot(String email, CustomerStatus status);

	/**
	 * Customers not in status {@code excluded} whose given, family or full name starts with {@code namePrefix}
	 * (lowercase, LIKE wildcards escaped with a backslash), or whose email or phone equals the given value.
	 */
	@Query("""
			SELECT c FROM Customer c
			WHERE c.status <> :excluded AND (
			     LOWER(c.name.givenName) LIKE :namePrefix ESCAPE '\\'
			  OR LOWER(c.name.familyName) LIKE :namePrefix ESCAPE '\\'
			  OR LOWER(CONCAT(c.name.givenName, ' ', c.name.familyName)) LIKE :namePrefix ESCAPE '\\'
			  OR c.email = :email
			  OR c.phone = :phone)
			ORDER BY LOWER(c.name.familyName), LOWER(c.name.givenName), c.createdAt
			""")
	Page<Customer> search(String namePrefix, String email, String phone, CustomerStatus excluded, Pageable page);
}
