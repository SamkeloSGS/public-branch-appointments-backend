package com.samdev.appointments.branchmanagement.web.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.samdev.appointments.branchmanagement.ServiceCategoryDefined;
import com.samdev.appointments.branchmanagement.ServiceCategoryRetired;
import com.samdev.appointments.branchmanagement.ServiceCategoryUpdated;
import com.samdev.appointments.branchmanagement.ServiceDefined;
import com.samdev.appointments.branchmanagement.ServiceDetailsUpdated;
import com.samdev.appointments.branchmanagement.ServiceMovedToCategory;
import com.samdev.appointments.branchmanagement.ServiceOffered;
import com.samdev.appointments.branchmanagement.ServiceOfferingChanged;
import com.samdev.appointments.branchmanagement.ServiceOfferingWithdrawn;
import com.samdev.appointments.branchmanagement.ServiceRetired;
import com.samdev.appointments.branchmanagement.WeeklyHoursChanged;

/**
 * Empties the public caches after a change to the hours or the catalog commits, and at start-up, when the branch
 * profile or the response shape may have changed. Capacity and calendar changes are not in the public reads.
 * Clearing is idempotent, so a retried event publication does no harm.
 */
@Component
class PublicCatalogEviction {

	private static final Logger log = LoggerFactory.getLogger(PublicCatalogEviction.class);

	private final CacheManager caches;

	PublicCatalogEviction(CacheManager caches) {
		this.caches = caches;
	}

	@TransactionalEventListener(classes = { WeeklyHoursChanged.class, ServiceCategoryDefined.class,
			ServiceCategoryUpdated.class, ServiceCategoryRetired.class, ServiceDefined.class,
			ServiceDetailsUpdated.class, ServiceMovedToCategory.class, ServiceRetired.class, ServiceOffered.class,
			ServiceOfferingChanged.class, ServiceOfferingWithdrawn.class })
	void catalogChanged(Object event) {
		evictAll();
	}

	@EventListener
	void started(ApplicationReadyEvent event) {
		evictAll();
	}

	private void evictAll() {
		for (var name : PublicCatalogCaches.ALL) {
			var cache = caches.getCache(name);
			if (cache == null) {
				continue;
			}
			try {
				cache.clear();
			} catch (RuntimeException e) {
				// Redis unavailable: the entries expire with their time to live
				log.warn("Could not clear cache {}: {}", name, e.getMessage());
			}
		}
	}
}
