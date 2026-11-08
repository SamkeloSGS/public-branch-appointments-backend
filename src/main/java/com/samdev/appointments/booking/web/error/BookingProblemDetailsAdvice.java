package com.samdev.appointments.booking.web.error;

import java.time.DateTimeException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.samdev.appointments.booking.application.exception.AppointmentNotFoundException;
import com.samdev.appointments.booking.application.exception.OfferingNotFoundException;
import com.samdev.appointments.booking.application.exception.StaleAppointmentVersionException;
import com.samdev.appointments.booking.domain.exception.BookingRejected;
import com.samdev.appointments.shared.ProblemDetailsSupport;

/**
 * Problem Details for the booking endpoints. Rejections are {@code 409} with a machine-readable {@code code}.
 */
@RestControllerAdvice(basePackages = "com.samdev.appointments.booking.web")
class BookingProblemDetailsAdvice extends ProblemDetailsSupport {

	@ExceptionHandler(BookingRejected.class)
	ProblemDetail rejected(BookingRejected e) {
		var problem = problem(HttpStatus.CONFLICT, "Booking rejected", e.getMessage());
		problem.setProperty("code", e.getCode().name());
		return problem;
	}

	@ExceptionHandler({ IllegalArgumentException.class, DateTimeException.class })
	ProblemDetail invalid(RuntimeException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
	}

	@ExceptionHandler({ AppointmentNotFoundException.class, OfferingNotFoundException.class })
	ProblemDetail notFound(RuntimeException e) {
		return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
	}

	@ExceptionHandler(StaleAppointmentVersionException.class)
	ProblemDetail stale(StaleAppointmentVersionException e) {
		return problem(HttpStatus.PRECONDITION_FAILED, "Stale version", e.getMessage());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail stale(ObjectOptimisticLockingFailureException e) {
		return problem(HttpStatus.PRECONDITION_FAILED, "Stale version",
				"The appointment was changed by another request; reload and try again");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail forbidden(AccessDeniedException e) {
		return problem(HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this action");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
