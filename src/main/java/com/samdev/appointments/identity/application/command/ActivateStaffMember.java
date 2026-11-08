package com.samdev.appointments.identity.application.command;

import com.samdev.appointments.identity.Subject;

/**
 * System, at first sign-in: links the subject to the invitation with this verified email.
 */
public record ActivateStaffMember(Subject subject, String email) {}
