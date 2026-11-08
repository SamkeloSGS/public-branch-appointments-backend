package com.samdev.appointments.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The caller needs this role or one above it. On a controller class or handler method; a method annotation replaces
 * the class one. Checked before the request body is read, so callers without access get {@code 403}, never a
 * validation error.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface RequiresRole {

	StaffRole value();
}
