package com.samdev.appointments.notification.web.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.samdev.appointments.notification.domain.entity.Delivery;
import com.samdev.appointments.notification.domain.entity.Notification;
import com.samdev.appointments.notification.domain.vo.Channel;
import com.samdev.appointments.notification.domain.vo.DeliveryStatus;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;
import com.samdev.appointments.notification.domain.vo.NotificationType;
import com.samdev.appointments.notification.domain.vo.StatusReason;

/**
 * IDs, types, statuses and times only: never a recipient or message text.
 */
public record NotificationResponse(UUID id, UUID appointmentId, NotificationType type,
		NotificationStatus status, @Nullable StatusReason statusReason, Instant sendAfter, Instant createdAt,
		List<DeliveryResponse> deliveries) {

	public static NotificationResponse of(Notification notification) {
		return new NotificationResponse(notification.getId().value(), notification.getAppointmentId().value(),
				notification.getType(), notification.getStatus(),
				notification.getStatusReason(), notification.getSendAfter(), notification.getCreatedAt(),
				notification.getDeliveries().stream().map(DeliveryResponse::of).toList());
	}

	/**
	 * @param lastError a provider error code or category
	 */
	public record DeliveryResponse(Channel channel, DeliveryStatus status, int attempts, @Nullable String lastError,
			@Nullable Instant lastAttemptAt, @Nullable Instant sentAt) {

		static DeliveryResponse of(Delivery delivery) {
			return new DeliveryResponse(delivery.getChannel(), delivery.getStatus(), delivery.getAttempts(),
					delivery.getLastError(), delivery.getLastAttemptAt(), delivery.getSentAt());
		}
	}
}
