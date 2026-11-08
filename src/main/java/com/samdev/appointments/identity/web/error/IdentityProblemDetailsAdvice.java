package com.samdev.appointments.identity.web.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.samdev.appointments.identity.application.exception.StaffNotFoundException;
import com.samdev.appointments.identity.application.exception.StaleStaffVersionException;
import com.samdev.appointments.identity.domain.exception.RuleCode;
import com.samdev.appointments.identity.domain.exception.StaffRuleViolation;
import com.samdev.appointments.shared.ProblemDetailsSupport;

/**
 * Problem Details for the identity endpoints. Rule violations are {@code 409} with a {@code code} property.
 */
@RestControllerAdvice(basePackages = "com.samdev.appointments.identity.web")
class IdentityProblemDetailsAdvice extends ProblemDetailsSupport {

	@ExceptionHandler(StaffRuleViolation.class)
	ProblemDetail ruleViolated(StaffRuleViolation e) {
		return conflict(e.getCode(), e.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail ruleViolated(DataIntegrityViolationException e) {
		return conflict(RuleCode.EMAIL_IN_USE, "A staff member with this email already exists");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalid(IllegalArgumentException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
	}

	@ExceptionHandler(StaffNotFoundException.class)
	ProblemDetail notFound(StaffNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
	}

	@ExceptionHandler(StaleStaffVersionException.class)
	ProblemDetail stale(StaleStaffVersionException e) {
		return problem(HttpStatus.PRECONDITION_FAILED, "Stale version", e.getMessage());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail stale(ObjectOptimisticLockingFailureException e) {
		return problem(HttpStatus.PRECONDITION_FAILED, "Stale version",
				"The staff member was changed by another request; reload and try again");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail forbidden(AccessDeniedException e) {
		return problem(HttpStatus.FORBIDDEN, "Forbidden", "You are not allowed to perform this action");
	}

	private static ProblemDetail conflict(RuleCode code, String detail) {
		var problem = problem(HttpStatus.CONFLICT, "Rule violated", detail);
		problem.setProperty("code", code.name());
		return problem;
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
