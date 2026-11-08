package com.samdev.appointments;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.samdev.appointments.identity.PublicEndpoints;

/**
 * OpenAPI description at {@code /v3/api-docs} and Swagger UI at {@code /swagger-ui.html}, for all modules. Swagger
 * UI signs in through Keycloak with the authorization code flow and PKCE (the {@code appointments-web} client).
 * Secured endpoints reference the {@value #SECURITY_SCHEME} scheme.
 */
@Configuration(proxyBeanMethods = false)
public class ApiDocumentationConfiguration {

	public static final String SECURITY_SCHEME = "keycloak";

	@Bean
	OpenAPI appointmentsOpenApi(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {

		var openIdConnect = issuer + "/protocol/openid-connect";

		return new OpenAPI()
				.info(new Info()
						.title("Branch Appointments API")
						.version("v1")
						.description("The branch, service catalog, capacity, calendar, staff access, customers, "
								+ "availability, appointments and notifications. Errors are RFC 9457 Problem Details. "
								+ "Updates of existing resources need the resource's ETag in If-Match. Admin endpoints "
								+ "need a sign-in with a second factor."))
				.components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
						.type(SecurityScheme.Type.OAUTH2)
						.description("Keycloak sign-in (authorization code with PKCE)")
						.flows(new OAuthFlows().authorizationCode(new OAuthFlow()
								.authorizationUrl(openIdConnect + "/auth")
								.tokenUrl(openIdConnect + "/token")
								.scopes(new Scopes().addString("openid", "OpenID Connect"))))));
	}

	@Bean
	GroupedOpenApi publicApi() {
		return GroupedOpenApi.builder().group("public").pathsToMatch("/api/v1/**").pathsToExclude("/api/v1/admin/**").build();
	}

	@Bean
	GroupedOpenApi adminApi() {
		return GroupedOpenApi.builder().group("admin").pathsToMatch("/api/v1/admin/**").build();
	}

	@Bean
	PublicEndpoints apiDocumentationEndpoints() {
		return () -> List.of(PublicEndpoints.Endpoint.get("/v3/api-docs"), PublicEndpoints.Endpoint.get("/v3/api-docs/**"),
				PublicEndpoints.Endpoint.get("/swagger-ui.html"), PublicEndpoints.Endpoint.get("/swagger-ui/**"));
	}
}
