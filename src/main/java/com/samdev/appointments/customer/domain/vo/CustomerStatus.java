package com.samdev.appointments.customer.domain.vo;

/**
 * {@code ACTIVE <-> DEACTIVATED}, and either of them {@code -> ERASED}, which is terminal.
 */
public enum CustomerStatus {
	ACTIVE, DEACTIVATED, ERASED
}
