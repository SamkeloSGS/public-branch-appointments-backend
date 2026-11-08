package com.samdev.appointments.notification.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jmolecules.ddd.types.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.samdev.appointments.notification.domain.entity.Notification;
import com.samdev.appointments.notification.domain.vo.NotificationId;
import com.samdev.appointments.notification.domain.vo.NotificationStatus;
import com.samdev.appointments.notification.domain.vo.NotificationType;

public interface Notifications extends Repository<Notification, NotificationId> {

	Notification save(Notification notification);

	Optional<Notification> findById(NotificationId id);

	boolean existsBySourceEventKey(String sourceEventKey);

	long countBySourceEventKeyStartingWith(String prefix);

	List<Notification> findByAppointmentIdOrderByCreatedAtAsc(UUID appointmentId);

	List<Notification> findByAppointmentIdAndStatus(UUID appointmentId, NotificationStatus status);

	List<Notification> findByCustomerIdAndStatus(UUID customerId, NotificationStatus status);

	boolean existsByAppointmentIdAndTypeInAndEventAtAfter(UUID appointmentId, Collection<NotificationType> types,
			Instant eventAt);

	Page<Notification> findByStatusAndCreatedAtBetweenOrderByCreatedAtDesc(NotificationStatus status, Instant from,
			Instant to, Pageable page);

	Page<Notification> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to, Pageable page);

	/**
	 * Claims the next due notification for this transaction: the row stays locked until it commits, and rows other
	 * instances have locked are skipped, so no notification is processed twice at the same time.
	 *
	 * @param excluded IDs already handled in this run; never empty
	 */
	@Query(value = """
			SELECT id FROM notification.notification
			WHERE status = 'PENDING' AND send_after <= :now AND id NOT IN (:excluded)
			ORDER BY send_after
			LIMIT 1
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	Optional<UUID> claimNextDue(Instant now, Collection<UUID> excluded);

	@Query("SELECT n FROM Notification n JOIN n.deliveries d WHERE d.providerMessageId = :providerMessageId")
	Optional<Notification> findByProviderMessageId(String providerMessageId);
}
