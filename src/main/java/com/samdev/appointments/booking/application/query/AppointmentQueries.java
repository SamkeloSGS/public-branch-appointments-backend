package com.samdev.appointments.booking.application.query;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.AppointmentStatus;
import com.samdev.appointments.booking.application.exception.AppointmentNotFoundException;
import com.samdev.appointments.booking.domain.entity.Appointment;
import com.samdev.appointments.booking.domain.repository.Appointments;
import com.samdev.appointments.booking.domain.repository.PoolDaySchedules;
import com.samdev.appointments.booking.domain.vo.BookingReference;
import com.samdev.appointments.booking.domain.vo.Reservation;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.TimeRange;
import com.samdev.appointments.customer.CustomerDirectory;
import com.samdev.appointments.customer.CustomerId;

@Component
@Transactional(readOnly = true)
public class AppointmentQueries {

	private final Appointments appointments;
	private final PoolDaySchedules schedules;
	private final CustomerDirectory customers;
	private final Clock clock;

	AppointmentQueries(Appointments appointments, PoolDaySchedules schedules, CustomerDirectory customers,
			Clock clock) {

		this.appointments = appointments;
		this.schedules = schedules;
		this.customers = customers;
		this.clock = clock;
	}

	/**
	 * One page of the customer's appointments that have not ended yet, soonest first; or of those that have, latest
	 * first.
	 */
	public Page<Appointment> mine(CustomerId customerId, boolean upcoming, Pageable page) {

		var now = clock.instant();

		return upcoming ? appointments.findByCustomerIdAndSlot_EndsAtAfterOrderBySlot_StartsAtAsc(customerId.value(), now, page)
				: appointments.findByCustomerIdAndSlot_EndsAtLessThanEqualOrderBySlot_StartsAtDesc(customerId.value(), now,
						page);
	}

	/**
	 * Only the customer's own appointments; any other reference is not found.
	 */
	public Appointment mine(CustomerId customerId, String reference) {
		return parse(reference)
				.flatMap(it -> appointments.findByCustomerIdAndReference(customerId.value(), it.value()))
				.orElseThrow(AppointmentNotFoundException::byReference);
	}

	public Appointment get(AppointmentId id) {
		return appointments.findById(id)
				.orElseThrow(() -> new AppointmentNotFoundException("Appointment " + id + " not found"));
	}

	public Optional<Appointment> byReference(String reference) {
		return parse(reference).flatMap(it -> appointments.findByReference(it.value()));
	}

	/**
	 * The branch's appointments on the date, ordered by start, with customer display names. Erased customers appear
	 * with the directory's placeholder.
	 */
	public List<DayListEntry> dayList(LocalDate date, @Nullable AppointmentStatus status) {

		var day = appointments.findBySlot_DateOrderBySlot_StartTimeAsc(date).stream()
				.filter(it -> status == null || it.getStatus() == status)
				.toList();

		var names = customers.displayNames(day.stream().map(Appointment::getCustomerId).collect(Collectors.toSet()));

		return day.stream()
				.map(it -> new DayListEntry(it, names.getOrDefault(it.getCustomerId(), CustomerDirectory.FORMER_CUSTOMER)))
				.toList();
	}

	/**
	 * Overbooked pool days in the range, with the appointments overlapping each overbooked interval.
	 */
	public List<OverbookedDay> overbooked(LocalDate from, LocalDate to) {

		return schedules.findByOverbookedTrueAndDateBetweenOrderByDateAsc(from, to)
				.stream()
				.map(schedule -> {

					var reserved = appointments.findByIdIn(
							schedule.getReservations().stream().map(Reservation::appointment).toList());

					var intervals = schedule.overbookedIntervals().stream()
							.map(interval -> new OverbookedInterval(interval, schedule.capacityAt(interval.start()),
									schedule.loadAt(interval.start()),
									reserved.stream()
											.filter(it -> it.getSlot().range().overlaps(interval))
											.sorted(Comparator.comparing(it -> it.getSlot().startTime()))
											.toList()))
							.toList();

					return new OverbookedDay(schedule.getCapacityPoolId(), schedule.getDate(), intervals);
				})
				.toList();
	}

	private static Optional<BookingReference> parse(String reference) {
		try {
			return Optional.of(BookingReference.parse(reference));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public record DayListEntry(Appointment appointment, String customerName) {}

	public record OverbookedDay(CapacityPoolId poolId, LocalDate date, List<OverbookedInterval> intervals) {}

	public record OverbookedInterval(TimeRange range, int capacity, int load, List<Appointment> appointments) {}
}
