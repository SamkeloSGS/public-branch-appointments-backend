package com.samdev.appointments.branchmanagement.web.dto.response;

import java.util.UUID;

import com.samdev.appointments.branchmanagement.domain.entity.ServiceOffering;

public record OfferingResponse(UUID id, UUID serviceId, UUID poolId, long durationMinutes,
		long slotIntervalMinutes, int minLeadTimeMinutes, int maxAdvanceDays, boolean active, long version) {

	public static OfferingResponse of(ServiceOffering offering) {
		return new OfferingResponse(offering.getId().value(),
				offering.getServiceId().value(), offering.getCapacityPoolId().value(),
				offering.getDuration().toMinutes(), offering.getSlotInterval().toMinutes(),
				offering.getBookingWindow().minLeadTimeMinutes(), offering.getBookingWindow().maxAdvanceDays(),
				offering.isActive(), offering.getVersion());
	}
}
