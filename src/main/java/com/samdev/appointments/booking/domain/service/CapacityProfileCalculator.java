package com.samdev.appointments.booking.domain.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.samdev.appointments.booking.domain.vo.CapacityProfile;
import com.samdev.appointments.booking.domain.vo.CapacitySegment;
import com.samdev.appointments.booking.domain.vo.ScheduleInputs;
import com.samdev.appointments.branchmanagement.TimeRange;

/**
 * Builds the capacity profile of a pool day, implementing Branch management's effective capacity rule:
 * <ol>
 * <li>Branch not {@code ACTIVE}: not bookable.</li>
 * <li>A {@code CLOSED} calendar exception for the date: capacity 0.</li>
 * <li>Outside the applicable hours (special hours if an exception exists, else weekly hours): not bookable.</li>
 * <li>A windowed override covering the time: its capacity.</li>
 * <li>A whole-day override: its capacity.</li>
 * <li>Otherwise: the pool's base capacity.</li>
 * </ol>
 * Not bookable means no segment, which is capacity 0. Adjacent segments with the same capacity are merged.
 */
public final class CapacityProfileCalculator {

	private CapacityProfileCalculator() {}

	public static CapacityProfile profile(ScheduleInputs inputs) {

		if (!inputs.available() || inputs.closed()) {
			return CapacityProfile.EMPTY;
		}

		var segments = new ArrayList<CapacitySegment>();

		for (var hours : inputs.hours()) {

			var points = new TreeSet<LocalTime>(List.of(hours.start(), hours.end()));
			inputs.windows().forEach(it -> {
				if (hours.contains(it.window().start())) {
					points.add(it.window().start());
				}
				if (hours.contains(it.window().end())) {
					points.add(it.window().end());
				}
			});

			var list = List.copyOf(points);
			for (var i = 0; i + 1 < list.size(); i++) {
				add(segments, new TimeRange(list.get(i), list.get(i + 1)), capacityAt(inputs, list.get(i)));
			}
		}

		return new CapacityProfile(segments);
	}

	private static int capacityAt(ScheduleInputs inputs, LocalTime moment) {
		return inputs.windows().stream()
				.filter(it -> it.window().contains(moment))
				.mapToInt(ScheduleInputs.WindowCapacity::capacity)
				.findFirst()
				.orElse(inputs.wholeDayCapacity() != null ? inputs.wholeDayCapacity() : inputs.baseCapacity());
	}

	private static void add(List<CapacitySegment> segments, TimeRange range, int capacity) {

		var last = segments.isEmpty() ? null : segments.getLast();

		if (last != null && last.toTime().equals(range.start()) && last.capacity() == capacity) {
			segments.set(segments.size() - 1, new CapacitySegment(last.fromTime(), range.end(), capacity));
		} else {
			segments.add(CapacitySegment.of(range, capacity));
		}
	}
}
