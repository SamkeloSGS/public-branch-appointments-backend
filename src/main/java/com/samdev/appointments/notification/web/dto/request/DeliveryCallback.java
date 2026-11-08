package com.samdev.appointments.notification.web.dto.request;

import com.samdev.appointments.notification.domain.vo.DeliveryStatus;

/**
 * A provider's report about a message it accepted earlier.
 *
 * @param status {@code DELIVERED} or {@code BOUNCED}
 */
public record DeliveryCallback(String messageId, DeliveryStatus status) {}
