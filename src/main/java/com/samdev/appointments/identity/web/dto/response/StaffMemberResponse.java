package com.samdev.appointments.identity.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.domain.entity.StaffMember;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

/**
 * @param invitationExpiresAt only while the staff member is INVITED
 */
public record StaffMemberResponse(UUID id, @Nullable String email, String displayName, StaffRole role,
		StaffStatus status, @Nullable Instant invitationExpiresAt, long version) {

	public static StaffMemberResponse of(StaffMember member) {
		return new StaffMemberResponse(member.getId().value(),
				member.getEmail() == null ? null : member.getEmail().value(), member.getDisplayName(),
				member.getRole(), member.getStatus(),
				member.getStatus() == StaffStatus.INVITED
						? member.getInvitedAt().plus(StaffMember.INVITATION_VALIDITY) : null,
				member.getVersion());
	}
}
