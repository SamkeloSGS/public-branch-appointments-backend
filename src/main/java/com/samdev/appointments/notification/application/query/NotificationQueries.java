package com.samdev.appointments.notification.application.query;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.samdev.appointments.booking.AppointmentId;
import com.samdev.appointments.notification.application.exception.NotificationNotFoundException;
import com.samdev.appointments.notification.domain.entity.Notification;
import com.samdev.appointments.notification.domain.repository.Notifications;
import com.samdev.appointments.notification.domain.vo.NotificationId;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;

@Component
@Transactional(readOnly = true)
public class NotificationQueries {

	private final Notifications notifications;

	NotificationQueries(Notifications notifications) {
		this.notifications = notifications;
	}

	public Notification get(NotificationId id) {
		return notifications.findById(id).orElseThrow(() -> new NotificationNotFoundException(id));
	}

	/**
	 * Every notification for the appointment, oldest first.
	 */
	public List<Notification> forAppointment(AppointmentId appointmentId) {
		return notifications.findByAppointmentIdOrderByCreatedAtAsc(appointmentId.value());
	}

	/**
	 * One page of the notifications created in the period, newest first, optionally in one status.
	 */
	public Page<Notification> recent(@Nullable NotificationStatus status, Instant from, Instant to, Pageable page) {
		return status == null
				? notifications.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to, page)
				: notifications.findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(status, from, to, page);
	}
}
