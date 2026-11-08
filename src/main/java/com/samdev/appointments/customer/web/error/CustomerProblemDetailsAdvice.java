package com.samdev.appointments.customer.web.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.samdev.appointments.customer.application.exception.CustomerNotFoundException;
import com.samdev.appointments.customer.application.exception.StaleCustomerVersionException;
import com.samdev.appointments.customer.domain.exception.CustomerRuleViolation;
import com.samdev.appointments.customer.domain.exception.EmailInUseException;
import com.samdev.appointments.customer.domain.exception.RuleCode;
import com.samdev.appointments.shared.ProblemDetailsSupport;

/**
 * Problem Details for the customer endpoints. Rule violations are {@code 409} with a {@code code} property. No
 * response names a person or repeats a contact detail.
 */
@RestControllerAdvice(basePackages = "com.samdev.appointments.customer.web")
class CustomerProblemDetailsAdvice extends ProblemDetailsSupport {

	@ExceptionHandler(EmailInUseException.class)
	ProblemDetail emailInUse(EmailInUseException e) {
		var problem = conflict(e.getCode(), e.getMessage());
		problem.setProperty("existingCustomerId", e.getExistingCustomerId().value());
		return problem;
	}

	@ExceptionHandler(CustomerRuleViolation.class)
	ProblemDetail ruleViolated(CustomerRuleViolation e) {
		return conflict(e.getCode(), e.getMessage());
	}

	/**
	 * A concurrent request took the same email or sign-in first. Says no more than that.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail ruleViolated(DataIntegrityViolationException e) {
		var violation = CustomerRuleViolation.detailsUnavailable();
		return conflict(violation.getCode(), violation.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalid(IllegalArgumentException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
	}

	@ExceptionHandler(CustomerNotFoundException.class)
	ProblemDetail notFound(CustomerNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
	}

	@ExceptionHandler(StaleCustomerVersionException.class)
	ProblemDetail stale(StaleCustomerVersionException e) {
		return problem(HttpStatus.PRECONDITION_FAILED, "Stale version", e.getMessage());
	}

	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	ProblemDetail stale(ObjectOptimisticLockingFailureException e) {
		return problem(HttpStatus.PRECONDITION_FAILED, "Stale version",
				"The customer was changed by another request; reload and try again");
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
