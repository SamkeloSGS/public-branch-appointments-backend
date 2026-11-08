package com.samdev.appointments.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.PublishedEvents;

import com.samdev.appointments.SharedPersistenceConfiguration;
import com.samdev.appointments.TestcontainersConfiguration;
import com.samdev.appointments.customer.application.command.ChangeContactPreferences;
import com.samdev.appointments.customer.application.command.DeactivateCustomer;
import com.samdev.appointments.customer.application.command.EraseCustomer;
import com.samdev.appointments.customer.application.command.ReactivateCustomer;
import com.samdev.appointments.customer.application.command.RegisterCustomer;
import com.samdev.appointments.customer.application.command.RegisterCustomerByStaff;
import com.samdev.appointments.customer.application.command.UpdateContactDetails;
import com.samdev.appointments.customer.application.exception.StaleCustomerVersionException;
import com.samdev.appointments.customer.application.handler.CustomerCommandHandler;
import com.samdev.appointments.customer.application.query.CustomerQueries;
import com.samdev.appointments.customer.domain.entity.Customer;
import com.samdev.appointments.customer.domain.exception.CustomerRuleViolation;
import com.samdev.appointments.customer.domain.exception.EmailInUseException;
import com.samdev.appointments.customer.domain.exception.RuleCode;
import com.samdev.appointments.customer.domain.vo.CustomerStatus;
import com.samdev.appointments.customer.infrastructure.persistence.AccessAuditRetention;
import com.samdev.appointments.identity.Subject;

/**
 * Commands, linking, the directory facade and the access audit against PostgreSQL. Identity is bootstrapped too,
 * because the controllers use its access facade.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Import({ TestcontainersConfiguration.class, SharedPersistenceConfiguration.class })
class CustomerModuleTests {

	static final Subject STAFF = new Subject("staff-" + UUID.randomUUID());

	@Autowired CustomerCommandHandler commands;
	@Autowired CustomerQueries queries;
	@Autowired CustomerDirectory directory;
	@Autowired AccessAuditRetention retention;
	@Autowired JdbcTemplate jdbc;

	@AfterEach
	void dropFailingTrigger() {
		jdbc.execute("DROP TRIGGER IF EXISTS fail_access_audit ON customer.access_audit");
	}

	// CU-1, CU-2, CU-3

	@Test
	@DisplayName("CU-1/CU-10 self-registration creates an ACTIVE linked customer and publishes CustomerRegistered")
	void selfRegisters(PublishedEvents events) {

		var subject = subject();

		var event = exactlyOne(events, CustomerRegistered.class,
				() -> commands.handle(new RegisterCustomer(subject, null, "Thandi", "Nkosi", email(), "082 555 0199", null)));

		var customer = queries.get(event.customerId());
		assertThat(event.selfRegistered()).isTrue();
		assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
		assertThat(customer.getIdentitySubject()).isEqualTo(subject);
		assertThat(customer.getPhone().value()).isEqualTo("+27825550199");
		assertThat(customer.getPreferences().language()).isEqualTo("en");
		assertThat(auditRows(customer.getId())).isZero();
	}

	@Test
	@DisplayName("CU-1 without an email in the request, the verified email of the sign-in is used")
	void usesVerifiedEmail() {

		var verified = email();
		var id = commands.handle(new RegisterCustomer(subject(), verified, "Sipho", "Dlamini", null, null, null));

		assertThat(queries.get(id).getEmail().value()).isEqualTo(verified);
	}

	@Test
	@DisplayName("CU-1 a second profile for the same subject and invalid input are rejected and create nothing")
	void rejectsDuplicatesAndInvalidInput() {

		var subject = subject();
		commands.handle(new RegisterCustomer(subject, null, "Palesa", "Mokoena", email(), null, null));

		assertRule(RuleCode.PROFILE_EXISTS,
				() -> commands.handle(new RegisterCustomer(subject, null, "Palesa", "Mokoena", email(), null, null)));

		var other = subject();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> commands.handle(new RegisterCustomer(other, null, "Palesa", "Mokoena", null, null, null)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> commands.handle(new RegisterCustomer(other, null, " ", "Mokoena", email(), null, null)));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> commands.handle(new RegisterCustomer(other, null, "P", "M", email(), null, "fr")));
		assertThat(directory.findByIdentity(other)).isEmpty();
	}

	@Test
	@DisplayName("CU-2 a verified email matching a staff-created customer links it instead of creating one")
	void linksStaffCreatedCustomer(PublishedEvents events) {

		var email = email();
		var staffCreated = commands.handle(new RegisterCustomerByStaff(STAFF, "Johan", "Botha", email, null, "af"));
		var subject = subject();

		var event = exactlyOne(events, CustomerIdentityLinked.class, () -> commands.handle(
				new RegisterCustomer(subject, email.toUpperCase(), "Johnny", "B", null, "082 555 0100", null)));

		assertThat(event.customerId()).isEqualTo(staffCreated);
		assertThat(directory.findByIdentity(subject)).contains(staffCreated);
		assertThat(queries.get(staffCreated).getName().givenName()).as("staff-entered details are kept")
				.isEqualTo("Johan");
	}

	@Test
	@DisplayName("CU-2 no linking without a verified email, and never on a phone match")
	void linksOnlyOnVerifiedEmail() {

		var email = email();
		var staffCreated = commands.handle(new RegisterCustomerByStaff(STAFF, "Ayesha", "Patel", email, "084 555 0177",
				null));

		var unverified = subject();
		assertRule(RuleCode.CONTACT_DETAILS_UNAVAILABLE, () -> commands.handle(
				new RegisterCustomer(unverified, null, "Ayesha", "Patel", email, null, null)));

		var samePhone = subject();
		var created = commands.handle(new RegisterCustomer(samePhone, null, "Ayesha", "Patel", null, "084 555 0177",
				null));

		assertThat(created).isNotEqualTo(staffCreated);
		assertThat(queries.get(staffCreated).getIdentitySubject()).isNull();
	}

	@Test
	@DisplayName("CU-2 a matching customer already linked to another subject is rejected without saying why")
	void rejectsCustomerLinkedElsewhere() {

		var email = email();
		var staffCreated = commands.handle(new RegisterCustomerByStaff(STAFF, "Bongani", "Zulu", email, null, null));
		commands.handle(new RegisterCustomer(subject(), email, "Bongani", "Zulu", null, null, null));

		assertThatExceptionOfType(CustomerRuleViolation.class)
				.isThrownBy(() -> commands.handle(new RegisterCustomer(subject(), email, "B", "Z", null, null, null)))
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(RuleCode.CONTACT_DETAILS_UNAVAILABLE))
				.satisfies(it -> assertThat(it.getMessage()).doesNotContainIgnoringCase("email").doesNotContain("@"));

		assertThat(directory.isActive(staffCreated)).isTrue();
	}

	@Test
	@DisplayName("CU-3/CU-8 staff create a customer without a subject, audited as CREATE; a taken email returns its owner")
	void staffCreatesCustomer(PublishedEvents events) {

		var email = email();

		var event = exactlyOne(events, CustomerRegistered.class,
				() -> commands.handle(new RegisterCustomerByStaff(STAFF, "Nomvula", "Dlamini", email, null, "zu")));

		assertThat(event.selfRegistered()).isFalse();
		assertThat(queries.get(event.customerId()).getIdentitySubject()).isNull();
		assertThat(auditRows(event.customerId(), "CREATE")).isEqualTo(1);

		assertThatExceptionOfType(EmailInUseException.class)
				.isThrownBy(() -> commands.handle(new RegisterCustomerByStaff(STAFF, "N", "D", email, null, null)))
				.satisfies(it -> assertThat(it.getExistingCustomerId()).isEqualTo(event.customerId()));
	}

	// CU-4 to CU-7, CU-10

	@Test
	@DisplayName("CU-10 every other command publishes exactly one event")
	void everyCommandPublishesOneEvent(PublishedEvents events) {

		var id = commands.handle(new RegisterCustomerByStaff(STAFF, "Pieter", "Smit", email(), "021 555 0110", null));

		exactlyOne(events, CustomerContactDetailsChanged.class, () -> {
			commands.handle(new UpdateContactDetails(STAFF, id, null, "Pieter", "Smit", null, "021 555 0110"));
			return null;
		});
		exactlyOne(events, ContactPreferencesChanged.class, () -> {
			commands.handle(new ChangeContactPreferences(id, null, false, true, "af"));
			return null;
		});
		exactlyOne(events, CustomerDeactivated.class, () -> {
			commands.handle(new DeactivateCustomer(STAFF, id, null));
			return null;
		});
		exactlyOne(events, CustomerReactivated.class, () -> {
			commands.handle(new ReactivateCustomer(STAFF, id, null));
			return null;
		});
		exactlyOne(events, CustomerErased.class, () -> {
			commands.handle(new EraseCustomer(STAFF, id, null));
			return null;
		});

		assertThat(jdbc.queryForList("SELECT action FROM customer.access_audit WHERE customer_id = ? ORDER BY occurred_at",
				String.class, id.value())).containsExactlyInAnyOrder("CREATE", "UPDATE", "DEACTIVATE", "REACTIVATE",
						"ERASE");
	}

	@Test
	@DisplayName("CU-4 another customer's email is rejected: generically for the customer, with the owner for staff")
	void updateRejectsTakenEmail() {

		var taken = email();
		var owner = commands.handle(new RegisterCustomerByStaff(STAFF, "Lwazi", "Nkosi", taken, null, null));
		var self = commands.handle(new RegisterCustomer(subject(), null, "Lerato", "M", email(), null, null));

		assertRule(RuleCode.CONTACT_DETAILS_UNAVAILABLE,
				() -> commands.handle(new UpdateContactDetails(null, self, null, "Lerato", "M", taken, null)));

		assertThatExceptionOfType(EmailInUseException.class)
				.isThrownBy(() -> commands.handle(new UpdateContactDetails(STAFF, self, null, "Lerato", "M", taken, null)))
				.satisfies(it -> assertThat(it.getExistingCustomerId()).isEqualTo(owner));
	}

	@Test
	@DisplayName("CU-5 an unsupported language is rejected")
	void rejectsUnsupportedLanguage() {

		var id = commands.handle(new RegisterCustomerByStaff(STAFF, "Annelie", "Botha", email(), null, null));

		assertThatIllegalArgumentException()
				.isThrownBy(() -> commands.handle(new ChangeContactPreferences(id, null, true, false, "de")));
	}

	@Test
	@DisplayName("CU-7 after erasure no personal data remains, the ID stays valid, and the person can register again")
	void eraseAndRegisterAgain() {

		var subject = subject();
		var email = email();
		var id = commands.handle(new RegisterCustomer(subject, email, "Sizwe", "Khumalo", null, "082 555 0123", null));

		commands.handle(new EraseCustomer(null, id, null));

		var row = jdbc.queryForMap("SELECT * FROM customer.customer WHERE id = ?", id.value());
		assertThat(row).containsEntry("status", "ERASED").containsEntry("given_name", null)
				.containsEntry("family_name", null).containsEntry("email", null).containsEntry("phone", null)
				.containsEntry("identity_subject", null);
		assertThat(directory.contactFor(id)).isEmpty();
		assertThat(directory.displayNames(Set.of(id))).containsEntry(id, CustomerDirectory.FORMER_CUSTOMER);
		assertThat(directory.findByIdentity(subject)).isEmpty();
		assertThat(auditRows(id)).as("self-service is not audited").isZero();

		var again = commands.handle(new RegisterCustomer(subject, email, "Sizwe", "Khumalo", null, null, null));
		assertThat(again).isNotEqualTo(id);
		assertRule(RuleCode.INVALID_STATE, () -> commands.handle(new ReactivateCustomer(STAFF, id, null)));
	}

	@Test
	@DisplayName("CU-3 the database refuses a second non-erased customer with the same email or subject")
	void uniqueIndexes() {

		var email = email();
		var subject = subject();
		commands.handle(new RegisterCustomer(subject, null, "Karabo", "Mabena", email, null, null));

		assertThatExceptionOfType(Exception.class).isThrownBy(() -> insertCustomer(email, null, "ACTIVE"));
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> insertCustomer(email(), subject.value(), "ACTIVE"));
	}

	// CU-8

	@Test
	@DisplayName("CU-8 search returns non-erased matches by name prefix, email or phone a page at a time, audited with the query")
	void search() {

		var family = "Zz" + UUID.randomUUID().toString().substring(0, 6);
		for (var i = 0; i < 22; i++) {
			commands.handle(new RegisterCustomerByStaff(STAFF, "Given" + i, family, null, "+2782" + (5550000 + i), null));
		}
		var erased = commands.handle(new RegisterCustomerByStaff(STAFF, "Erased", family, email(), null, null));
		commands.handle(new EraseCustomer(STAFF, erased, null));
		var byEmail = email();
		var emailMatch = commands.handle(new RegisterCustomerByStaff(STAFF, "Mail", "Match", byEmail, null, null));

		var auditBefore = searchAuditRows();

		var first = queries.search(STAFF, family.toLowerCase(), PageRequest.of(0, 20));
		assertThat(first.getContent()).hasSize(20)
				.allSatisfy(it -> assertThat(it.getName().familyName()).isEqualTo(family));
		assertThat(first.getTotalElements()).as("the erased customer is left out").isEqualTo(22);
		assertThat(queries.search(STAFF, family.toLowerCase(), PageRequest.of(1, 20)).getContent()).hasSize(2)
				.doesNotContainAnyElementsOf(first.getContent());
		assertThat(search("given0 " + family)).hasSize(1);
		assertThat(search(byEmail.toUpperCase())).extracting(it -> it.getId()).containsExactly(emailMatch);
		assertThat(search("082 555 0003")).extracting(it -> it.getName().givenName())
				.containsExactly("Given3");
		assertThat(search("%%%")).as("wildcards are literal").isEmpty();

		assertThat(searchAuditRows()).isEqualTo(auditBefore + 6);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM customer.access_audit WHERE search_query = ?", Long.class,
				byEmail.toUpperCase())).isEqualTo(1);

		assertThatIllegalArgumentException().isThrownBy(() -> search(" ab "));
	}

	@Test
	@DisplayName("CU-8 viewing writes one VIEW row; if the audit row cannot be written, view and search return nothing")
	void auditFailsClosed() {

		var id = commands.handle(new RegisterCustomerByStaff(STAFF, "Audit", "Target", email(), null, null));

		queries.view(STAFF, id);
		assertThat(auditRows(id, "VIEW")).isEqualTo(1);

		jdbc.execute("""
				CREATE OR REPLACE FUNCTION customer.fail_access_audit() RETURNS trigger LANGUAGE plpgsql AS $$
				BEGIN RAISE EXCEPTION 'audit unavailable'; END; $$;
				CREATE TRIGGER fail_access_audit BEFORE INSERT ON customer.access_audit
				FOR EACH ROW EXECUTE FUNCTION customer.fail_access_audit();
				""");

		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> queries.view(STAFF, id));
		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> search("Target"));
		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> commands.handle(new DeactivateCustomer(STAFF, id, null)));
		assertThat(directory.isActive(id)).as("the failed command changed nothing").isTrue();
	}

	@Test
	@DisplayName("CU-8 audit rows cannot be changed or deleted, except by the retention purge after 24 months")
	void auditRetention() {

		var id = commands.handle(new RegisterCustomerByStaff(STAFF, "Old", "Audit", email(), null, null));

		assertThatExceptionOfType(Exception.class).isThrownBy(
				() -> jdbc.update("UPDATE customer.access_audit SET staff_subject = 'x' WHERE customer_id = ?", id.value()));
		assertThatExceptionOfType(Exception.class)
				.isThrownBy(() -> jdbc.update("DELETE FROM customer.access_audit WHERE customer_id = ?", id.value()));

		jdbc.update("""
				INSERT INTO customer.access_audit (id, customer_id, staff_subject, action, search_query, occurred_at)
				VALUES (?, ?, ?, 'VIEW', NULL, ?)
				""", UUID.randomUUID(), id.value(), STAFF.value(),
				Timestamp.from(Instant.now().minus(800, ChronoUnit.DAYS)));

		assertThat(retention.purgeExpired()).isGreaterThanOrEqualTo(1);
		assertThat(auditRows(id)).as("the recent CREATE row is kept").isEqualTo(1);
	}

	// CU-9, CU-12

	@Test
	@DisplayName("CU-9 contactFor returns details of enabled channels only; isActive and displayNames cover every case")
	void facade() {

		var id = commands.handle(new RegisterCustomerByStaff(STAFF, "Facade", "Person", email(), "082 555 0150", "xh"));
		commands.handle(new ChangeContactPreferences(id, null, false, true, null));

		assertThat(directory.contactFor(id)).hasValueSatisfying(it -> {
			assertThat(it.displayName()).isEqualTo("Facade Person");
			assertThat(it.language()).isEqualTo("xh");
			assertThat(it.email()).isNull();
			assertThat(it.phone()).isEqualTo("+27825550150");
		});

		var unknown = CustomerId.random();
		assertThat(directory.isActive(unknown)).isFalse();
		assertThat(directory.contactFor(unknown)).isEmpty();
		assertThat(directory.displayNames(Set.of(id, unknown))).containsEntry(id, "Facade Person")
				.containsEntry(unknown, CustomerDirectory.FORMER_CUSTOMER);
		assertThat(directory.displayNames(Set.of())).isEmpty();

		commands.handle(new DeactivateCustomer(STAFF, id, null));
		assertThat(directory.isActive(id)).isFalse();
		assertThat(directory.contactFor(id)).as("deactivated customers still get service messages").isPresent();
		assertThat(auditRows(id)).as("facade calls are not audited").isEqualTo(2);
	}

	@Test
	@DisplayName("CU-12 an update based on an outdated version is rejected")
	void rejectsStaleVersion() {

		var id = commands.handle(new RegisterCustomerByStaff(STAFF, "Stale", "Version", email(), null, null));
		var version = queries.get(id).getVersion();

		commands.handle(new UpdateContactDetails(STAFF, id, version, "Stale", "Versions", queries.get(id).getEmail().value(),
				null));

		assertThatExceptionOfType(StaleCustomerVersionException.class).isThrownBy(
				() -> commands.handle(new UpdateContactDetails(STAFF, id, version, "Stale", "Other", email(), null)));
	}

	private void insertCustomer(String email, String subject, String status) {
		jdbc.update("""
				INSERT INTO customer.customer (id, identity_subject, given_name, family_name, email, phone, email_enabled,
				    sms_enabled, preferred_language, status, created_at, version)
				VALUES (?, ?, 'A', 'B', ?, NULL, TRUE, FALSE, 'en', ?, now(), 0)
				""", UUID.randomUUID(), subject, email, status);
	}

	private long auditRows(CustomerId id) {
		return jdbc.queryForObject("SELECT count(*) FROM customer.access_audit WHERE customer_id = ?", Long.class,
				id.value());
	}

	private long auditRows(CustomerId id, String action) {
		return jdbc.queryForObject("SELECT count(*) FROM customer.access_audit WHERE customer_id = ? AND action = ?",
				Long.class, id.value(), action);
	}

	/**
	 * The first page of a search.
	 */
	private List<Customer> search(String query) {
		return queries.search(STAFF, query, PageRequest.of(0, 20)).getContent();
	}

	private long searchAuditRows() {
		return jdbc.queryForObject("SELECT count(*) FROM customer.access_audit WHERE action = 'SEARCH' AND staff_subject = ?",
				Long.class, STAFF.value());
	}

	static IdentitySubject subject() {
		return new IdentitySubject(UUID.randomUUID().toString());
	}

	static String email() {
		return "person-" + UUID.randomUUID().toString().substring(0, 8) + "@customers.test";
	}

	private static void assertRule(RuleCode code, Runnable command) {
		assertThatExceptionOfType(CustomerRuleViolation.class).isThrownBy(command::run)
				.satisfies(it -> assertThat(it.getCode()).isEqualTo(code));
	}

	private static <T> T exactlyOne(PublishedEvents events, Class<T> type, Supplier<?> command) {

		var before = domainEvents(events).size();
		command.get();
		var after = domainEvents(events);

		assertThat(after).hasSize(before + 1);
		assertThat(after.get(after.size() - 1)).isInstanceOf(type);

		return type.cast(after.get(after.size() - 1));
	}

	private static List<Object> domainEvents(PublishedEvents events) {
		var result = new ArrayList<Object>();
		events.ofType(Object.class).forEach(it -> {
			if (it.getClass().isAnnotationPresent(DomainEvent.class)) {
				result.add(it);
			}
		});
		return result;
	}
}
