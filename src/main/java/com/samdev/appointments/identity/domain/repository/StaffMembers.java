package com.samdev.appointments.identity.domain.repository;

import java.util.List;
import java.util.Optional;

import org.jmolecules.ddd.types.Repository;

import com.samdev.appointments.identity.StaffMemberId;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.domain.entity.StaffMember;
import com.samdev.appointments.identity.domain.vo.StaffStatus;

public interface StaffMembers extends Repository<StaffMember, StaffMemberId> {

	StaffMember save(StaffMember member);

	Optional<StaffMember> findById(StaffMemberId id);

	Optional<StaffMember> findBySubject(String subject);

	Optional<StaffMember> findByEmailAndStatus(String email, StaffStatus status);

	boolean existsByEmailAndStatusNot(String email, StaffStatus status);

	boolean existsByRoleAndStatusNot(StaffRole role, StaffStatus status);

	long countByRoleAndStatus(StaffRole role, StaffStatus status);

	List<StaffMember> findAll();
}
