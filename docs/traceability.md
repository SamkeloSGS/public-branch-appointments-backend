# Requirement traceability: Branch management

Every requirement from the spec (section 11) maps to at least one test. Test names carry the requirement ID in
`@DisplayName`, so `grep -r '"BM-7' src/test` finds them.

| Req | What | Tests |
|---|---|---|
| BM-1 | The branch from configuration (`appointments.branch`, never stored) | `ValueTypeTests.branchProfileValidates`, `BranchManagementModuleTests.branchFromConfiguration`, `web/controller/AdminApiTests.readsTheBranch`, `web/controller/PublicApiTests.showsTheBranch`, `web/controller/PublicApiTests.cachesPublicReads` |
| BM-2 | Set weekly hours | `ValueTypeTests`, `domain/vo/ValueObjectTests` (overlaps, closed days), `domain/entity/OpeningHoursTests`, `BranchManagementModuleTests.setsWeeklyHours`, `.rejectsOverlappingHours` (nothing changes), `web/controller/AdminApiTests.replacesWeeklyHours` (400) |
| BM-3 | Activate, suspend, close | Removed: the app runs for one branch, which is always open; its hours decide when it can be booked |
| BM-4 | Service catalog (with description and prerequisites) | `domain/entity/CatalogAndPoolTests`, `BranchManagementModuleTests.serviceCatalog`, `.serviceDetails`, `web/controller/AdminApiTests.serviceDetails`, `web/controller/AdminApiTests.retiredServiceCannotBeOffered`, `web/controller/PublicApiTests.listsActiveServices` |
| BM-5 | Capacity pools | `domain/vo/ValueObjectTests.capacityIsAtLeastOne`, `domain/entity/CatalogAndPoolTests`, `BranchManagementModuleTests.capacityPools`, `.rejectsInvalidPools`, `web/controller/AdminApiTests.capacityPools` (409 / 400) |
| BM-6 | Offer a service | `domain/entity/ServiceOfferingTests`, `BranchManagementModuleTests.offerings`, `.rejectsInvalidOfferings`, `web/controller/AdminApiTests.offerings` |
| BM-7 | Calendar exceptions | `domain/entity/CalendarTests` (exceptions), `BranchManagementModuleTests.calendarExceptions`, `.rejectsPastException`, `web/controller/AdminApiTests.calendarExceptions` |
| BM-8 | Capacity overrides | `domain/entity/CalendarTests` (overrides, conflicts), `BranchManagementModuleTests.capacityOverrides`, `.rejectsConflictingOverrides`, `.rejectsUnknownPool`, `web/controller/AdminApiTests.capacityOverrides` |
| BM-9 | Public branch details | `web/controller/PublicApiTests.showsTheBranch` (hours, active offerings only) |
| BM-10 | Event delivery | `BranchManagementModuleTests` (every command asserts exactly one domain event via `exactlyOne`), `EventDeliveryTests` (pool events: completed publication, failing listener keeps state and leaves publication incomplete, rollback delivers nothing) |
| BM-11 | Authorisation | `web/controller/AuthorisationTests` |
| BM-12 | Concurrency | `BranchManagementModuleTests.rejectsStaleVersion`, `web/controller/AdminApiTests.optimisticConcurrency` (412, 428), `.differentAggregatesDoNotConflict` |
| BM-13 | Service categories and customer segments (added after the spec: `ServiceCategory` aggregate for PERSONAL or BUSINESS; services look up their category; the branch serves one or both segments and offers only matching services) | `domain/entity/ServiceCategoryTests`, `BranchManagementModuleTests.serviceCategories`, `.servicesInCategories`, `.branchSegments`, `web/controller/AdminApiTests.serviceCategories`, `.servicesInCategories`, `web/controller/PublicApiTests.servicesByCategory`, `.showsSegments` |

Architecture (spec 3.4), published API and database rules: `ArchitectureTests`, `PublishedApiTests`,
`DatabaseCatalogTests`.

# Requirement traceability: Identity and access

| Req | What | Tests |
|---|---|---|
| IA-1 | Token validation | `identity/infrastructure/security/TokenValidationTests` (real decoder against a local key server: valid, missing, expired, 30 s tolerance, unknown key, wrong issuer, wrong audience, key rotation) |
| IA-2 | Current user and roles | `identity/domain/entity/StaffMemberTests.roleOrder`, `IdentityModuleTests.unverifiedEmailDoesNotActivate`, `.lifecycle` (suspended is a customer), `identity/web/controller/IdentityApiTests.meAsCustomer`, `.meAsStaff`, `branchmanagement/web/controller/AuthorisationTests.roleClaimsAreIgnored` |
| IA-3 | Manager-only endpoints | `AuthorisationTests.managementNeedsTheBranchManager`, `AdminAccessMatrixTests` |
| IA-4 | Second factor | `AuthorisationTests.adminNeedsSecondFactor`, `.customerEndpointsAcceptLevelOne`, `IdentityApiTests.staffEndpointsNeedTheBranchManager`, `AdminAccessMatrixTests`; authenticator setup verified manually against the local Keycloak |
| IA-5 | Invite | `StaffMemberTests.invites`, `.invitationNeedsEmailAndName`, `IdentityModuleTests.invites`, `.rejectsEmailInUse`, `IdentityApiTests.invites`; setup email: `StaffInvitationEmailTests` (sent after commit, again on re-invite, not for a closed invitation), `identity/infrastructure/security/KeycloakStaffAccountsTests` (login made for the invitation, one execute-actions email valid until the invitation expires, existing logins untouched); verified end to end against the local Keycloak and Mailpit |
| IA-6 | Activation at first sign-in | `StaffMemberTests.activates`, `.expiredInvitation`, `IdentityModuleTests.activatesAtFirstSignIn`, `.unverifiedEmailDoesNotActivate`, `.expiredInvitationIsNotActivated`, `.concurrentFirstRequestsActivateOnce` |
| IA-7 | Change role | `StaffMemberTests.changesRole`, `IdentityModuleTests.lifecycle`, `IdentityApiTests.manageStaff` |
| IA-8 | Change branch assignments | Removed: every staff member works at the one branch |
| IA-9 | Suspend and reinstate | `StaffMemberTests.suspendsAndReinstates`, `IdentityModuleTests.lifecycle`, `IdentityApiTests.suspensionAppliesOnNextRequest` |
| IA-10 | Remove | `StaffMemberTests.removes`, `IdentityModuleTests.removeAndInviteAgain`, `IdentityApiTests.manageStaff`, `StaffMemberTests.withdrawsInvitation`, `StaffInvitationEmailTests.withdrawal`, `KeycloakStaffAccountsTests.withdrawDeletesOnlyItsLogin` |
| IA-11 | Protecting administrative access | `IdentityModuleTests.protectsAdministrativeAccess`, `IdentityApiTests.manageStaff` (self-change) |
| IA-12 | First branch manager | `IdentityModuleTests.invitesFirstAdmin` |
| IA-13 | Change log | `IdentityModuleTests.invites` (actor, no email), `.lifecycle` (one row per command), `.changeLogFailsClosed`, `.changeLogIsAppendOnly` |
| IA-14 | Keycloak configuration | `keycloak/appointments-realm.json`; verified against Keycloak 26.8.0 in `compose.yaml` (import, PKCE S256, exact redirects, verify email, step-up flow, token settings); user profile allows admin-only unmanaged attributes (`staffMemberId` on logins made for invitations) |
| IA-15 | Account deletion support | `IdentityModuleTests.isStaff`, `identity/infrastructure/security/KeycloakLoginAccountsTests` (client-credentials token, delete by subject, 404 tolerated, failures retried), `customer/LoginAccountErasureTests` (erasure deletes the login, staff keep theirs, no login no call) |
| IA-16 | Events | `IdentityModuleTests` (`exactlyOne` per command), `IdentityPublishedApiTests.eventsCarryNoPersonalData` |
| IA-17 | Own details | `IdentityApiTests.meAsCustomer`, `.meAsStaff` |
| IA-18 | Concurrency | `IdentityModuleTests.rejectsStaleVersion`, `IdentityApiTests.suspensionAppliesOnNextRequest` (412) |

# Requirement traceability: Customer

Test classes are under `src/test/java/com/samdev/appointments/customer/`.

| Req | What | Tests |
|---|---|---|
| CU-1 | Self-registration | `domain/vo/ValueObjectTests`, `domain/entity/CustomerTests.registersWithDefaults`, `.needsAContactDetail`, `CustomerModuleTests.selfRegisters`, `.usesVerifiedEmail`, `.rejectsDuplicatesAndInvalidInput`, `web/controller/CustomerApiTests.selfRegistration`, `.invalidRegistration` |
| CU-2 | Linking a staff-created customer | `CustomerTests.linkIdentity`, `CustomerModuleTests.linksStaffCreatedCustomer`, `.linksOnlyOnVerifiedEmail`, `.rejectsCustomerLinkedElsewhere`, `CustomerApiTests.linksByVerifiedEmail`, `.doesNotLeakEmailOwnership` |
| CU-3 | Staff-created customer | `CustomerTests.registersByStaff`, `CustomerModuleTests.staffCreatesCustomer`, `.uniqueIndexes`, `CustomerApiTests.branchStaff` (409 with `existingCustomerId`) |
| CU-4 | Update contact details | `CustomerTests.removingEmailEnablesSms`, `.removingPhoneDisablesSms`, `.updateNeedsAContactDetail`, `CustomerModuleTests.updateRejectsTakenEmail`, `CustomerApiTests.selfUpdates` |
| CU-5 | Contact preferences | `ValueObjectTests.contactPreferences`, `CustomerTests.preferenceRules`, `CustomerModuleTests.rejectsUnsupportedLanguage`, `CustomerApiTests.selfUpdates` |
| CU-6 | Deactivate and reactivate | `CustomerTests.deactivateAndReactivate`, `CustomerModuleTests.facade`, `CustomerApiTests.deactivatedCustomer`, `.branchManager` |
| CU-7 | Erasure | `CustomerTests.erase`, `CustomerModuleTests.eraseAndRegisterAgain`, `CustomerApiTests.deactivatedCustomer`, `.branchManager` |
| CU-8 | Staff search, view and audit | `CustomerModuleTests.search`, `.auditFailsClosed`, `.auditRetention`, `.staffCreatesCustomer`, `.everyCommandPublishesOneEvent` (audit rows), `CustomerApiTests.branchStaff`, `.selfUpdates` (no rows for self-service) |
| CU-9 | Facade behaviour | `CustomerModuleTests.facade`, `.eraseAndRegisterAgain` |
| CU-10 | Events | `CustomerModuleTests` (`exactlyOne` per command), `CustomerPublishedApiTests.eventsCarryNoPersonalData`, `.serialisedEventsCarryNoPersonalData` |
| CU-11 | Authorisation | `CustomerApiTests.selfRegistration` (404 without a profile), `.branchStaff` (403 for status changes), `.customersAreRefused`, `AdminAccessMatrixTests` |
| CU-12 | Concurrency | `CustomerModuleTests.rejectsStaleVersion`, `CustomerApiTests.selfUpdates`, `.branchManager` (412, 428) |

Demo customers: `infrastructure/demo/DemoCustomerLoaderTests`.

# Requirement traceability: Booking

Test classes are under `src/test/java/com/samdev/appointments/booking/`. `BookingModuleTests` runs against the real
Branch management and Customer modules; events from asynchronous listeners are awaited.

| Req | What | Tests |
|---|---|---|
| BK-1 | View availability | `domain/service/AvailabilityCalculatorTests`, `BookingModuleTests.projectionFollowsEvents` (special hours), `.openDaysOverARange` (one read for a range: booked day, closure, special hours, override; open days agree with each day's open times), `web/controller/BookingApiTests.availability` (public, order, closed day empty, 404, 31-day limit) |
| BK-2 | Book an appointment | `domain/entity/AppointmentTests.book`, `.startInFuture`, `.slotNeverSpansMidnight`, `domain/vo/BookingReferenceTests`, `BookingModuleTests.books`, `BookingApiTests.customerBooking` (`PROFILE_REQUIRED`) |
| BK-3 | No double booking | `domain/entity/PoolDayScheduleTests.reservesUpToCapacity`, `.mixedDurations`, `.respectsSegments`, `BookingModuleTests.lastPlace` (4 parallel requests), `.customerOverlapAcrossPools` (exclusion constraint) |
| BK-4 | Booking policy | `AvailabilityCalculatorTests.onGrid`, `.bookingWindow`, `BookingModuleTests.policy` (every code), `.staffSkipLeadTime`, `BookingApiTests.rejections` |
| BK-5 | Reschedule | `AppointmentTests.reschedule`, `.cutoff`, `BookingModuleTests.reschedule`, `BookingApiTests.customerBooking` |
| BK-6 | Cancel | `AppointmentTests.cutoff`, `PoolDayScheduleTests.release`, `BookingModuleTests.cancel`, `BookingApiTests.customerBooking`, `.staff` |
| BK-7 | Check-in, complete, no-show | `AppointmentTests.checkInAndComplete`, `.noShow`, `.terminalStatuses`, `BookingModuleTests.statusActions` (including the sweep) |
| BK-8 | Schedule projection | `domain/service/CapacityProfileCalculatorTests` (each rule step), `BookingModuleTests.projection` (idempotent, older ignored, missing row, rebuild), `.projectionFollowsEvents` |
| BK-9 | Capacity reduced below bookings | `PoolDayScheduleTests.overbooking`, `.restoringCapacityResolves`, `BookingModuleTests.overbooking` |
| BK-10 | Displacement | `PoolDayScheduleTests.displacement`, `.closureDisplacesAll`, `AppointmentTests.displacedIsExemptFromCutoff`, `BookingModuleTests.closure` (one-way, expiry) |
| BK-11 | Resolving overbooking | `BookingModuleTests.overbooking` (overbooked list, manual displace, only from an overbooked interval), `BookingApiTests.branchManager` |
| BK-12 | Impact preview | `PoolDayScheduleTests.impactMatchesApply`, `BookingModuleTests.closure`, `.overridePreviewMatches`, `BookingApiTests.branchManager` |
| BK-13 | Customer changes | `BookingModuleTests.customerChanges`, `.policy` (`CUSTOMER_INACTIVE`) |
| BK-14 | Staff day list | `BookingModuleTests.dayList` (order, placeholder), `BookingApiTests.staff` |
| BK-15 | Events | `BookingModuleTests` (each command's event once, via `CommittedEvents`), `BookingPublishedApiTests.eventsCarryNoPersonalData` |
| BK-16 | Authorisation | `BookingApiTests.customerBooking` (other customers get 404), `.staff` (customers 403, unknown reference 404), `.branchManager`, `AdminAccessMatrixTests` |
| BK-17 | Idempotency | `BookingModuleTests.idempotency`, `BookingApiTests.customerBooking` |

# Requirement traceability: Notification

Test classes are under `src/test/java/com/samdev/appointments/notification/`. Providers are replaced by
`FakeSenders`; `NotificationModuleTests` and `NotificationApiTests` run with Booking, Customer and Branch management.

| Req | What | Tests |
|---|---|---|
| NT-1 | Confirmation | `domain/entity/NotificationTests.sent`, `application/handler/NotificationDispatcherTests.sendsOnEveryChannel`, `NotificationModuleTests.booked`, `.endToEnd` |
| NT-2 | Reminder | `NotificationModuleTests.booked` (send time), `.noReminderWhenSoon`; not sent early: the dispatcher only claims rows whose `send_after` has passed (`Notifications.claimNextDue`) |
| NT-3 | Rescheduled | `NotificationModuleTests.rescheduled` (supersession, new reminder, stale older event), `.endToEnd` (message shows the new time) |
| NT-4 | Cancellation | `NotificationModuleTests.cancelledAndRebooking` (including `CUSTOMER_ERASED`) |
| NT-5 | Rebooking required | `NotificationModuleTests.cancelledAndRebooking`, `infrastructure/messaging/TemplateRendererTests.rebookingLink` |
| NT-6 | Channels and preferences at send time | `NotificationDispatcherTests.sendsOnEveryChannel`, `.channelsAndLanguageAtSendTime`, `.noChannel`, `NotificationTests.abandon`, `TemplateRendererTests.languages` |
| NT-7 | Relevance check | `NotificationDispatcherTests.relevance`, `NotificationModuleTests.skippedWhenChanged` |
| NT-8 | Retries and failure | `NotificationTests.retries`, `.mixedOutcomes`, `.independentChannels`, `NotificationDispatcherTests.retries` |
| NT-9 | Idempotent event handling | `NotificationModuleTests.idempotent`, `.atomic` |
| NT-10 | Customer erased | `NotificationModuleTests.customerErasedAndCheckIn` |
| NT-11 | No personal data | `domain/vo/ErrorCodeTests`, `NotificationDispatcherTests.errorsAreReduced`, `NotificationModuleTests.noPersonalDataColumns`, `web/controller/NotificationApiTests.appointmentView` |
| NT-12 | Staff views and resend | `NotificationApiTests.appointmentView`, `.resend`, `.customersAreRefused`, `AdminAccessMatrixTests` |
| NT-13 | Templates | `TemplateRendererTests.rendersEveryTemplate`, `.smsFitsOneSegment`, `.missingTemplateFails` |
| NT-14 | Dispatcher concurrency | `NotificationModuleTests.concurrentDispatchers`, `NotificationDispatcherTests.isolation` |
| NT-15 | Isolation | `NotificationModuleTests.providerOutage`, `NotificationDispatcherTests.isolation`, `NotificationPublishedApiTests` (nothing depends on the module) |
| NT-16 | Delivery callbacks | `NotificationTests.callbacks`, `NotificationApiTests.callbacks` |

Retention: `NotificationModuleTests.retention`.

## Operations (OPS)

Not from a module spec: monitoring added on top of the modules (see [conventions](conventions.md#monitoring)).

| Req | What | Tests |
|---|---|---|
| OPS-1 | Health, liveness and readiness are public without details; no other actuator endpoint is reachable | `MonitoringTests.health`, `.nothingElseExposed` |
| OPS-2 | Business and queue metrics, labelled with codes and enums only | `MonitoringTests.gauges`, `booking/BookingModuleTests.countsOutcomes`, `NotificationDispatcherTests.sendsOnEveryChannel`, `.noChannel`, `identity/infrastructure/security/KeycloakLoginAccountsTests` |
| OPS-3 | Rate limits per user or client address, a smaller one for booking changes; 429 with Retry-After | `identity/infrastructure/security/RequestRateLimiterTests` |
| OPS-4 | Failed event listeners are retried up to a limit; completed publications are deleted after retention | `EventPublicationMaintenanceTests` |
