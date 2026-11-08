package com.samdev.appointments.branchmanagement.web.error;

import java.time.DateTimeException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.samdev.appointments.branchmanagement.application.exception.NotFoundException;
import com.samdev.appointments.branchmanagement.application.exception.StaleVersionException;
import com.samdev.appointments.branchmanagement.domain.exception.BusinessRuleViolation;
import com.samdev.appointments.shared.ProblemDetailsSupport;

/**
 * Maps failures to RFC 9457 Problem Details: validation {@code 400}, not authorised {@code 403}, not found
 * {@code 404}, invariant or uniqueness violation {@code 409}, stale version {@code 412}. Framework errors (malformed
 * JSON, bean validation, ...) are handled by {@link ProblemDetailsSupport}, with the invalid fields.
 */
@RestControllerAdvice(basePackages = "com.samdev.appointments.branchmanagement.web")
class ProblemDetailsAdvice extends ProblemDetailsSupport {

	@ExceptionHandler({ IllegalArgumentException.class, DateTimeException.class })
	ProblemDetail invalid(RuntimeException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
	}

	@ExceptionHandler(BusinessRuleViolation.class)
	ProblemDetail conflict(BusinessRuleViolation e) {
		return problem(HttpStatus.CONFLICT, "Business rule violated", e.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail conflict(DataIntegrityViolationException e) {
		return problem(HttpStatus.CONFLICT, "Conflicting change",
				"The change conflicts with existing data, possibly a concurrent request");
	}

	@ExceptionHandler(StaleVersionException.class)
	ProblemDetail stale(StaleVersionException e) {
		return problem(HttpStatus.PRECONDITION_FAILED, "Stale version", e.getMessage());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail stale(ObjectOptimisticLockingFailureException e) {
		return problem(HttpStatus.PRECONDITION_FAILED, "Stale version",
				"The resource was changed by another request; reload it and try again");
	}

	@ExceptionHandler({ AccessDeniedException.class, AuthorizationDeniedException.class })
	ProblemDetail forbidden(RuntimeException e) {
		return problem(HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this action");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
