package com.samdev.appointments.identity.application.query;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.Subject;
import com.samdev.appointments.identity.application.exception.StaffNotFoundException;
import com.samdev.appointments.identity.application.port.StaffChangeLogEntries;
import com.samdev.appointments.identity.domain.entity.StaffMember;
import com.samdev.appointments.identity.domain.repository.StaffMembers;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

@Component
@Transactional(readOnly = true)
public class StaffQueries {

	private final StaffMembers staff;
	private final StaffChangeLogEntries changeLog;

	StaffQueries(StaffMembers staff, StaffChangeLogEntries changeLog) {
		this.staff = staff;
		this.changeLog = changeLog;
	}

	/**
	 * Staff members, optionally filtered by role and status, sorted by display name.
	 */
	public List<StaffMember> list(@Nullable StaffRole role, @Nullable StaffStatus status) {
		return staff.findAll().stream()
				.filter(it -> role == null || it.getRole() == role)
				.filter(it -> status == null || it.getStatus() == status)
				.sorted(Comparator.comparing(StaffMember::getDisplayName, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public StaffMember get(StaffMemberId id) {
		return staff.findById(id).orElseThrow(() -> new StaffNotFoundException(id));
	}

	public List<StaffChangeLogEntries.Entry> changes(StaffMemberId id) {
		get(id);
		return changeLog.entriesFor(id);
	}

	/**
	 * True for a subject whose staff record is INVITED, ACTIVE or SUSPENDED.
	 */
	public boolean isStaff(Subject subject) {
		return staff.findBySubject(subject.value())
				.filter(it -> EnumSet.of(StaffStatus.INVITED, StaffStatus.ACTIVE, StaffStatus.SUSPENDED)
						.contains(it.getStatus()))
				.isPresent();
	}
}
