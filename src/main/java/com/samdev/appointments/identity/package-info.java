/**
 * Identity and access: staff roles, and the {@link com.samdev.appointments.identity.AccessControl}
 * facade every other module uses. Keycloak handles sign-in; this module never sees a password. Depends on no other
 * module.
 * <p>
 * Only this package is the module's public API: identifiers, events, {@code AccessControl}, {@code CurrentUser} and
 * {@code PublicEndpoints}.
 */
@ApplicationModule(displayName = "Identity and access")
package com.samdev.appointments.identity;

import org.springframework.modulith.ApplicationModule;
