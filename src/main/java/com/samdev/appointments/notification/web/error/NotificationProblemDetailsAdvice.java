package com.samdev.appointments.notification.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.samdev.appointments.notification.application.exception.NotificationNotFoundException;
import com.samdev.appointments.notification.domain.exception.NotificationRuleViolation;
import com.samdev.appointments.shared.ProblemDetailsSupport;

/**
 * Problem Details for the notification endpoints.
 */
@RestControllerAdvice(basePackages = "com.samdev.appointments.notification.web")
class NotificationProblemDetailsAdvice extends ProblemDetailsSupport {

	@ExceptionHandler(NotificationRuleViolation.class)
	ProblemDetail ruleViolated(NotificationRuleViolation e) {
		var problem = problem(HttpStatus.CONFLICT, "Rule violated", e.getMessage());
		problem.setProperty("code", "INVALID_STATE");
		return problem;
	}

	@ExceptionHandler(NotificationNotFoundException.class)
	ProblemDetail notFound(NotificationNotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail invalid(IllegalArgumentException e) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
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
