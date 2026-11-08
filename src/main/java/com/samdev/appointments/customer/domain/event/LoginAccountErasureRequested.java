package com.samdev.appointments.customer.domain.event;

import com.samdev.appointments.customer.IdentitySubject;

/**
 * Internal: an erased customer had a login account, which should be deleted unless the person is also staff.
 * Carries the subject only because the customer row no longer does. Never published to other modules.
 */
public record LoginAccountErasureRequested(IdentitySubject subject) {}
