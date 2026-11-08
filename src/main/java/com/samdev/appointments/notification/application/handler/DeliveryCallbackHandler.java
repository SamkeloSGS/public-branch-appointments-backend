package com.samdev.appointments.notification.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.notification.domain.repository.Notifications;
import com.samdev.appointments.notification.domain.vo.DeliveryStatus;

/**
 * Applies provider callbacks about sent messages.
 */
@Component
public class DeliveryCallbackHandler {

	private final Notifications notifications;

	DeliveryCallbackHandler(Notifications notifications) {
		this.notifications = notifications;
	}

	/**
	 * @return whether a delivery with this provider message ID exists; unknown IDs are ignored
	 */
	@Transactional
	public boolean handle(String providerMessageId, DeliveryStatus reported) {
		return notifications.findByProviderMessageId(providerMessageId)
				.map(it -> {
					it.recordCallback(providerMessageId, reported);
					notifications.save(it);
					return true;
				})
				.orElse(false);
	}
}
