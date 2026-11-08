package com.samdev.appointments.identity.infrastructure.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.samdev.appointments.identity.AccessControl;
import com.samdev.appointments.identity.CurrentUser;
import com.samdev.appointments.identity.StaffRole;
import com.samdev.appointments.identity.Subject;
import com.samdev.appointments.identity.application.query.StaffQueries;
import com.samdev.appointments.identity.application.support.CurrentUserResolver;
import com.samdev.appointments.identity.application.support.TokenClaims;

/**
 * {@link AccessControl} for the current HTTP request. Reads the validated token from the security context (the only
 * place in the application that reads a token), resolves the {@link CurrentUser} once per request and keeps it for
 * the rest of that request only.
 */
@Component
class RequestAccessControl implements AccessControl {

	private static final String ATTRIBUTE = RequestAccessControl.class.getName() + ".currentUser";

	private final CurrentUserResolver resolver;
	private final StaffQueries queries;

	RequestAccessControl(CurrentUserResolver resolver, StaffQueries queries) {
		this.resolver = resolver;
		this.queries = queries;
	}

	@Override
	public CurrentUser currentUser() {

		var attributes = RequestContextHolder.getRequestAttributes();

		if (attributes != null
				&& attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof CurrentUser cached) {
			return cached;
		}

		var user = resolver.resolve(claims(token()));

		if (attributes != null) {
			attributes.setAttribute(ATTRIBUTE, user, RequestAttributes.SCOPE_REQUEST);
		}

		return user;
	}

	@Override
	public void requireRole(StaffRole role) {

		var user = currentUser();

		if (user.staffRole() == null || !user.staffRole().includes(role)) {
			throw new AccessDeniedException("This action needs the " + role + " role");
		}
	}

	@Override
	public boolean isStaff(Subject subject) {
		return queries.isStaff(subject);
	}

	private static Jwt token() {

		if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
			return authentication.getToken();
		}

		throw new AuthenticationCredentialsNotFoundException("No signed-in user");
	}

	static TokenClaims claims(Jwt jwt) {
		return new TokenClaims(new Subject(jwt.getSubject()), jwt.getClaimAsString("email"),
				Boolean.TRUE.equals(jwt.getClaim("email_verified")), level(jwt.getClaimAsString("acr")));
	}

	private static int level(String acr) {
		try {
			return acr == null ? 0 : Integer.parseInt(acr.strip());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
