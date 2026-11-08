package com.samdev.appointments.identity.web.dto.response;

/**
 * No email is sent in this version: the admin passes the sign-up address on. The person is linked at their first
 * sign-in with the invited, verified email.
 */
public record InvitationResponse(StaffMemberResponse staffMember, String signUpUrl) {}
