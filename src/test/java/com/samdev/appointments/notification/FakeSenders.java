package com.samdev.appointments.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.samdev.appointments.notification.application.port.EmailSender;
import com.samdev.appointments.notification.application.port.SmsSender;
import com.samdev.appointments.notification.domain.vo.SendOutcome;

/**
 * In-memory providers for every test context: they record what would have been sent and answer with a programmable
 * outcome (accepted by default).
 */
@Component
@Primary
public class FakeSenders implements EmailSender, SmsSender {

	public record Sent(String channel, String idempotencyKey, String to, String subject, String body) {}

	private final List<Sent> sent = new CopyOnWriteArrayList<>();
	private volatile Function<String, SendOutcome> emailOutcome = FakeSenders::accept;
	private volatile Function<String, SendOutcome> smsOutcome = FakeSenders::accept;

	@Override
	public SendOutcome send(OutgoingEmail email) {
		var outcome = emailOutcome.apply(email.idempotencyKey());
		if (outcome.kind() == SendOutcome.Kind.ACCEPTED) {
			sent.add(new Sent("EMAIL", email.idempotencyKey(), email.to(), email.subject(), email.body()));
		}
		return outcome;
	}

	@Override
	public SendOutcome send(OutgoingSms sms) {
		var outcome = smsOutcome.apply(sms.idempotencyKey());
		if (outcome.kind() == SendOutcome.Kind.ACCEPTED) {
			sent.add(new Sent("SMS", sms.idempotencyKey(), sms.to(), "", sms.body()));
		}
		return outcome;
	}

	/**
	 * @param outcome per idempotency key; may throw to simulate a provider outage
	 */
	public void email(Function<String, SendOutcome> outcome) {
		this.emailOutcome = outcome;
	}

	public void sms(Function<String, SendOutcome> outcome) {
		this.smsOutcome = outcome;
	}

	public void reset() {
		emailOutcome = FakeSenders::accept;
		smsOutcome = FakeSenders::accept;
	}

	public List<Sent> to(String recipient) {
		return sent.stream().filter(it -> it.to().equals(recipient)).toList();
	}

	public List<Sent> withKey(String idempotencyKey) {
		return sent.stream().filter(it -> it.idempotencyKey().equals(idempotencyKey)).toList();
	}

	private static SendOutcome accept(String key) {
		return SendOutcome.accepted("fake-" + key);
	}
}
