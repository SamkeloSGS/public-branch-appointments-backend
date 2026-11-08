package com.samdev.appointments.branchmanagement.web.dto.response;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.samdev.appointments.branchmanagement.BranchProfile;
import com.samdev.appointments.branchmanagement.CustomerSegment;
import com.samdev.appointments.branchmanagement.domain.entity.OpeningHours;
import com.samdev.appointments.branchmanagement.web.dto.AddressDto;
import com.samdev.appointments.branchmanagement.web.dto.LocationDto;
import com.samdev.appointments.branchmanagement.web.dto.TimeRangeDto;

/**
 * @param version the version of the weekly hours, the only part that can be changed
 */
public record BranchResponse(String code, String name, AddressDto address, LocationDto location, String timeZone,
		Set<CustomerSegment> segments, Map<DayOfWeek, List<TimeRangeDto>> weeklyHours, long version) {

	public static BranchResponse of(BranchProfile branch, OpeningHours hours) {
		return new BranchResponse(branch.code(), branch.name(), AddressDto.of(branch.address()),
				LocationDto.of(branch.location()), branch.timeZone().getId(), branch.segments(),
				TimeRangeDto.ofWeek(hours.getWeeklyHours()), hours.getVersion());
	}
}
