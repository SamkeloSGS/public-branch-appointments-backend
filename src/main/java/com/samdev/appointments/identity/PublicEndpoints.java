package com.samdev.appointments.identity;

import java.util.List;

/**
 * Endpoints a module serves without a token. Each module registers a bean; the application's security
 * configuration collects them, so this module never depends on the others.
 */
public interface PublicEndpoints {

	List<Endpoint> publicEndpoints();

	/**
	 * @param method an HTTP method name such as {@code GET}
	 * @param pattern an Ant-style path pattern such as {@code /api/v1/branches/*}
	 */
	record Endpoint(String method, String pattern) {

		public static Endpoint get(String pattern) {
			return new Endpoint("GET", pattern);
		}
	}
}
