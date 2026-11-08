/**
 * Customers: who a person is and how they may be contacted. The only module that stores personal data. Depends on
 * identity's public API for access decisions.
 * <p>
 * Only this package is the module's public API: identifiers, events, and the read-only
 * {@link com.samdev.appointments.customer.CustomerDirectory}. Events carry IDs only, never personal data.
 */
@ApplicationModule(displayName = "Customer")
package com.samdev.appointments.customer;

import org.springframework.modulith.ApplicationModule;
