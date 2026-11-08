package com.samdev.appointments.booking.infrastructure.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.samdev.appointments.booking.application.handler.HousekeepingHandler;

/**
 * The scheduled system commands.
 */
@Component
class BookingJobs {

	private static final Logger LOG = LoggerFactory.getLogger(BookingJobs.class);

	private final HousekeepingHandler housekeeping;

	BookingJobs(HousekeepingHandler housekeeping) {
		this.housekeeping = housekeeping;
	}

	@Scheduled(fixedDelayString = "${appointments.booking.sweep-interval:PT5M}", initialDelayString = "PT1M")
	void sweepNoShows() {
		var marked = housekeeping.sweepNoShows();
		if (marked > 0) {
			LOG.info("Marked {} appointments as no-shows", marked);
		}
	}

	@Scheduled(fixedDelayString = "${appointments.booking.sweep-interval:PT5M}", initialDelayString = "PT1M")
	void expireRebookings() {
		var expired = housekeeping.expireRebookings();
		if (expired > 0) {
			LOG.info("Cancelled {} appointments whose rebooking expired", expired);
		}
	}

	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
	void purgeIdempotencyKeys() {
		housekeeping.purgeIdempotencyKeys();
	}
}
