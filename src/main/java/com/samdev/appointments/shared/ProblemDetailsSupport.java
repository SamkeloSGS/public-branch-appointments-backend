package com.samdev.appointments.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/**
 * The base of every module's Problem Details advice. A request that fails validation is a {@code 400} with an
 * {@code errors} list naming each invalid field and why, so a client can show the message next to the field:
 *
 * <pre>
 * { "title": "Invalid request", "status": 400, "detail": "2 fields are invalid",
 *   "errors": [ { "field": "givenName", "message": "must not be blank" },
 *               { "field": "size", "message": "must be less than or equal to 100" } ] }
 * </pre>
 *
 * Fields are named as in the request: a body property path ({@code hours[0].start}), or a query parameter or header
 * name. A rule that spans fields has no {@code field}. Messages are English and never repeat the rejected value.
 */
public abstract class ProblemDetailsSupport extends ResponseEntityExceptionHandler {

	/**
	 * One invalid field.
	 *
	 * @param field the property path, query parameter or header; {@code null} for a rule across fields
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FieldProblem(@Nullable String field, String message) {}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return invalid(ex, problems(ex.getBindingResult()), headers, status, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		var problems = new ArrayList<FieldProblem>();

		for (var result : ex.getParameterValidationResults()) {
			if (result instanceof Errors errors) {
				problems.addAll(problems(errors));
				continue;
			}
			var name = parameterName(result.getMethodParameter());
			for (var error : result.getResolvableErrors()) {
				problems.add(new FieldProblem(name, message(error)));
			}
		}
		for (var error : ex.getCrossParameterValidationResults()) {
			problems.add(new FieldProblem(null, message(error)));
		}

		return invalid(ex, problems, headers, status, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {

		var type = ex.getRequiredType() == null ? "value" : ex.getRequiredType().getSimpleName();

		return invalid(ex, List.of(new FieldProblem(ex.getPropertyName(), "must be a valid " + type)), headers,
				status, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		var field = ex.getMostSpecificCause() instanceof JacksonException json ? path(json) : null;

		if (field == null) {
			return super.handleHttpMessageNotReadable(ex, headers, status, request);
		}

		return invalid(ex, List.of(new FieldProblem(field, "has an invalid value")), headers, status, request);
	}

	private @Nullable ResponseEntity<Object> invalid(Exception ex, List<FieldProblem> problems, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {

		var body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				problems.size() == 1 ? "1 field is invalid" : problems.size() + " fields are invalid");
		body.setTitle("Invalid request");
		body.setProperty("errors", problems);

		return handleExceptionInternal(ex, body, headers, status, request);
	}

	private static List<FieldProblem> problems(Errors errors) {

		var problems = new ArrayList<FieldProblem>();

		for (var error : errors.getAllErrors()) {
			problems.add(new FieldProblem(error instanceof FieldError field ? field.getField() : null, message(error)));
		}

		return problems;
	}

	private static String message(MessageSourceResolvable error) {
		return error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
	}

	private static @Nullable String parameterName(org.springframework.core.MethodParameter parameter) {

		var param = parameter.getParameterAnnotation(RequestParam.class);
		if (param != null && !param.name().isEmpty()) {
			return param.name();
		}
		var header = parameter.getParameterAnnotation(RequestHeader.class);
		if (header != null && !header.name().isEmpty()) {
			return header.name();
		}

		return parameter.getParameterName();
	}

	/**
	 * The JSON path of the property that could not be read, like {@code hours[0].start}.
	 */
	private static @Nullable String path(JacksonException e) {

		var path = e.getPath().stream()
				.map(it -> it.getPropertyName() != null ? "." + it.getPropertyName() : "[" + it.getIndex() + "]")
				.collect(Collectors.joining());

		return path.isEmpty() ? null : path.startsWith(".") ? path.substring(1) : path;
	}
}
