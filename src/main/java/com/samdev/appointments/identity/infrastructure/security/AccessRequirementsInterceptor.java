package com.samdev.appointments.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.samdev.appointments.identity.AccessControl;
import com.samdev.appointments.identity.RequiresRole;

/**
 * Enforces {@link RequiresRole} on every controller of every module, before arguments are bound, so a caller without
 * access gets {@code 403} rather than a validation error.
 */
@Component
class AccessRequirementsInterceptor implements HandlerInterceptor, WebMvcConfigurer {

	private final AccessControl access;

	AccessRequirementsInterceptor(AccessControl access) {
		this.access = access;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

		if (!(handler instanceof HandlerMethod method)) {
			return true;
		}

		var role = find(method, RequiresRole.class);

		if (role != null) {
			access.requireRole(role.value());
		}

		return true;
	}

	private static <A extends java.lang.annotation.Annotation> A find(HandlerMethod method, Class<A> type) {
		var onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), type);
		return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), type);
	}
}
