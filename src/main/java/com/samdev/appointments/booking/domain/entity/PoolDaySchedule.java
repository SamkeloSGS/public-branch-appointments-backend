package com.samdev.appointments.booking.domain.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.jmolecules.ddd.types.AggregateRoot;
import org.springframework.data.domain.AbstractAggregateRoot;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.booking.OverbookingResolved;
import com.samdev.appointments.booking.PoolDayOverbooked;
import com.samdev.appointments.booking.domain.event.ReservationDisplaced;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.booking.domain.exception.RejectionCode;
import com.samdev.appointments.booking.domain.vo.CapacityProfile;
import com.samdev.appointments.booking.domain.vo.CapacitySegment;
import com.samdev.appointments.booking.domain.vo.PoolDayScheduleId;
import com.samdev.appointments.booking.domain.vo.Reservation;
import com.samdev.appointments.branchmanagement.CapacityPoolId;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * The diary page for one capacity pool on one date: its capacity profile and one reservation per appointment that
 * occupies the pool. The only place capacity is enforced.
 * <p>
 * Capacity at a moment is the capacity of the segment covering it, 0 if none does. Load at a moment is the number
 * of reservations whose half-open range contains it. Both only change at segment and reservation boundaries, so
 * every rule is checked at those breakpoints. A moment is overbooked when load exceeds a capacity of at least 1.
 */
@Table(schema = "booking", name = "pool_day_schedule")
public class PoolDaySchedule extends AbstractAggregateRoot<PoolDaySchedule>
		implements AggregateRoot<PoolDaySchedule, PoolDayScheduleId> {

	@AttributeOverride(name = "value", column = @Column(name = "id"))
	private final PoolDayScheduleId id;

	private final UUID capacityPoolId;


	@Column(name = "schedule_date")
	private final LocalDate date;

	@CollectionTable(schema = "booking", name = "capacity_segment", joinColumns = @JoinColumn(name = "schedule_id"))
	private Set<CapacitySegment> segments = new HashSet<>();

	@CollectionTable(schema = "booking", name = "reservation", joinColumns = @JoinColumn(name = "schedule_id"))
	private Set<Reservation> reservations = new HashSet<>();

	private boolean overbooked;

	@Version
	private Long version;

	private PoolDaySchedule(PoolDayScheduleId id, CapacityPoolId capacityPoolId, LocalDate date,
			CapacityProfile profile) {

		this.id = Objects.requireNonNull(id);
		this.capacityPoolId = capacityPoolId.value();
		this.date = Objects.requireNonNull(date);
		this.segments = new HashSet<>(profile.segments());
	}

	/**
	 * A new page with the given profile and no reservations.
	 */
	public static PoolDaySchedule open(CapacityPoolId capacityPoolId, LocalDate date,
			CapacityProfile profile) {
		return new PoolDaySchedule(PoolDayScheduleId.random(), capacityPoolId, date, profile);
	}

	/**
	 * Adds a reservation if, at every moment of the range, load is below capacity. Checks nothing but this page.
	 *
	 * @throws BookingRejected {@code SLOT_UNAVAILABLE} otherwise
	 */
	public void reserve(AppointmentId appointmentId, TimeRange range) {

		if (holds(appointmentId)) {
			throw new IllegalStateException("Appointment " + appointmentId + " already has a reservation here");
		}
		if (!canReserve(range)) {
			throw new BookingRejected(RejectionCode.SLOT_UNAVAILABLE, "That time is no longer available");
		}

		reservations.add(Reservation.of(appointmentId, range));
	}

	/**
	 * Whether {@link #reserve} would accept the range now.
	 */
	public boolean canReserve(TimeRange range) {

		var profile = profile();

		return moments(range, profile, reservations).stream()
				.allMatch(it -> load(it, reservations) < profile.capacityAt(it));
	}

	/**
	 * Removes the appointment's reservation if present, then re-evaluates overbooking.
	 */
	public void release(AppointmentId appointmentId, Instant now) {

		reservations.removeIf(it -> it.appointmentId().equals(appointmentId.value()));
		evaluateOverbooking(now);
	}

	/**
	 * Replaces the profile; never rejects. Every reservation with zero capacity at any moment of its range is removed
	 * and reported as {@link ReservationDisplaced}. Then overbooking is re-evaluated.
	 */
	public void applyCapacity(CapacityProfile profile, Instant now) {

		var impact = impactOf(profile);

		if (!new HashSet<>(profile.segments()).equals(segments)) {
			segments.clear();
			segments.addAll(profile.segments());
		}

		for (var displaced : impact.displaced()) {
			reservations.remove(displaced);
			registerEvent(new ReservationDisplaced(displaced.appointment(), getCapacityPoolId(), date,
					displaced.fromTime(), displaced.toTime(), now));
		}

		evaluateOverbooking(now);
	}

	/**
	 * What {@link #applyCapacity} would do with this profile, without changing anything.
	 */
	public Impact impactOf(CapacityProfile profile) {

		var displaced = reservations.stream()
				.filter(it -> moments(it.range(), profile, List.of()).stream().anyMatch(m -> profile.capacityAt(m) == 0))
				.sorted(BY_START)
				.toList();

		var remaining = reservations.stream().filter(it -> !displaced.contains(it)).toList();

		return new Impact(displaced, overbookedIntervals(profile, remaining));
	}

	/**
	 * Maximal intervals where load exceeds a capacity of at least 1.
	 */
	public List<TimeRange> overbookedIntervals() {
		return overbookedIntervals(profile(), reservations);
	}

	public CapacityProfile profile() {
		return new CapacityProfile(List.copyOf(segments));
	}

	public int capacityAt(LocalTime moment) {
		return profile().capacityAt(moment);
	}

	public int loadAt(LocalTime moment) {
		return load(moment, reservations);
	}

	public boolean holds(AppointmentId appointmentId) {
		return reservationOf(appointmentId).isPresent();
	}

	public Optional<Reservation> reservationOf(AppointmentId appointmentId) {
		return reservations.stream().filter(it -> it.appointmentId().equals(appointmentId.value())).findFirst();
	}

	private void evaluateOverbooking(Instant now) {

		var nowOverbooked = !overbookedIntervals().isEmpty();

		if (!overbooked && nowOverbooked) {
			registerEvent(new PoolDayOverbooked(getCapacityPoolId(), date, now));
		}
		if (overbooked && !nowOverbooked) {
			registerEvent(new OverbookingResolved(getCapacityPoolId(), date, now));
		}

		overbooked = nowOverbooked;
	}

	private static final Comparator<Reservation> BY_START = Comparator.comparing(Reservation::fromTime)
			.thenComparing(Reservation::appointmentId);

	private static List<TimeRange> overbookedIntervals(CapacityProfile profile, Collection<Reservation> reservations) {

		var points = breakpoints(profile, reservations);
		var result = new ArrayList<TimeRange>();

		for (var i = 0; i + 1 < points.size(); i++) {

			var moment = points.get(i);
			var capacity = profile.capacityAt(moment);

			if (capacity >= 1 && load(moment, reservations) > capacity) {
				var last = result.isEmpty() ? null : result.getLast();
				if (last != null && last.end().equals(moment)) {
					result.set(result.size() - 1, new TimeRange(last.start(), points.get(i + 1)));
				} else {
					result.add(new TimeRange(moment, points.get(i + 1)));
				}
			}
		}

		return result;
	}

	/**
	 * The moments where capacity or load can change inside the range, starting with its start.
	 */
	private static List<LocalTime> moments(TimeRange range, CapacityProfile profile,
			Collection<Reservation> reservations) {

		var result = new ArrayList<LocalTime>();
		result.add(range.start());

		breakpoints(profile, reservations).stream()
				.filter(it -> it.isAfter(range.start()) && it.isBefore(range.end()))
				.forEach(result::add);

		return result;
	}

	private static List<LocalTime> breakpoints(CapacityProfile profile, Collection<Reservation> reservations) {

		var points = new TreeSet<LocalTime>();

		profile.segments().forEach(it -> {
			points.add(it.fromTime());
			points.add(it.toTime());
		});
		reservations.forEach(it -> {
			points.add(it.fromTime());
			points.add(it.toTime());
		});

		return List.copyOf(points);
	}

	private static int load(LocalTime moment, Collection<Reservation> reservations) {
		return (int) reservations.stream().filter(it -> it.range().contains(moment)).count();
	}

	/**
	 * @param displaced reservations that would be removed
	 * @param overbookedIntervals intervals that would be overbooked afterwards
	 */
	public record Impact(List<Reservation> displaced, List<TimeRange> overbookedIntervals) {}

	@Override
	public PoolDayScheduleId getId() {
		return id;
	}

	public CapacityPoolId getCapacityPoolId() {
		return new CapacityPoolId(capacityPoolId);
	}

	public LocalDate getDate() {
		return date;
	}

	public List<Reservation> getReservations() {
		return reservations.stream().sorted(BY_START).toList();
	}

	public boolean isOverbooked() {
		return overbooked;
	}

	public Long getVersion() {
		return version;
	}
}
