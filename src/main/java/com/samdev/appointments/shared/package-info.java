/**
 * Web conventions every module's API follows: field-level validation errors ({@link ProblemDetailsSupport}) and
 * paged lists ({@link PageResponse}). Not a bounded context: it holds no data, publishes no events and depends on no
 * module, so every module may use it.
 */
@ApplicationModule(displayName = "Shared web conventions")
package com.samdev.appointments.shared;

import org.springframework.modulith.ApplicationModule;
