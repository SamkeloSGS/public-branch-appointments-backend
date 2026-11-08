package com.samdev.appointments.booking.domain.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jmolecules.ddd.types.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.domain.entity.Appointment;

public interface Appointments extends Repository<Appointment, AppointmentId> {

	Appointment save(Appointment appointment);

	/**
	 * Saves and flushes the whole unit of work, so constraint and version conflicts surface here.
	 */
	Appointment saveAndFlush(Appointment appointment);

	Optional<Appointment> findById(AppointmentId id);

	List<Appointment> findByIdIn(Collection<AppointmentId> ids);

	Optional<Appointment> findByReference(String reference);

	Optional<Appointment> findByCustomerIdAndReference(UUID customerId, String reference);

	Page<Appointment> findByCustomerIdAndSlot_EndsAtAfterOrderBySlot_StartsAtAsc(UUID customerId, Instant now,
			Pageable page);

	Page<Appointment> findByCustomerIdAndSlot_EndsAtLessThanEqualOrderBySlot_StartsAtDesc(UUID customerId,
			Instant now, Pageable page);

	List<Appointment> findBySlot_DateOrderBySlot_StartTimeAsc(LocalDate date);

	List<Appointment> findByCustomerIdAndStatusInAndSlot_StartsAtAfter(UUID customerId,
			Collection<AppointmentStatus> statuses, Instant now);

	long countByCustomerIdAndStatusAndSlot_StartsAtAfter(UUID customerId, AppointmentStatus status, Instant now);

	List<Appointment> findByStatusAndSlot_EndsAtBefore(AppointmentStatus status, Instant cutoff);

	List<Appointment> findByStatusAndSlot_StartsAtBefore(AppointmentStatus status, Instant cutoff);

	/**
	 * Whether the customer has another {@code BOOKED} or {@code CHECKED_IN} appointment overlapping the range.
	 */
	@Query("""
			SELECT COUNT(a) > 0 FROM Appointment a
			WHERE a.customerId = :customerId AND a.id <> :excluded
			  AND a.status IN (com.samdev.appointments.booking.AppointmentStatus.BOOKED,
			                   com.samdev.appointments.booking.AppointmentStatus.CHECKED_IN)
			  AND a.slot.startsAt < :endsAt AND a.slot.endsAt > :startsAt
			""")
	boolean overlaps(UUID customerId, Instant startsAt, Instant endsAt, AppointmentId excluded);
}
