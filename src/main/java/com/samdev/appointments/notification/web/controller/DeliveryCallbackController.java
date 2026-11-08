package com.samdev.appointments.notification.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import com.samdev.appointments.notification.application.handler.DeliveryCallbackHandler;
import com.samdev.appointments.notification.infrastructure.config.NotificationProperties;
import com.samdev.appointments.notification.web.dto.request.DeliveryCallback;

/**
 * Delivery status callbacks from providers. No token: the body must carry a valid HMAC-SHA256 signature made with
 * the provider's configured secret, sent as {@code X-Signature: <hex>}. Unknown message IDs are ignored.
 */
@Tag(name = "Delivery callbacks", description = "Providers report delivered or bounced messages. Signed, no sign-in.")
@RestController
class DeliveryCallbackController {

	private final DeliveryCallbackHandler callbacks;
	private final NotificationProperties properties;
	private final JsonMapper json;

	DeliveryCallbackController(DeliveryCallbackHandler callbacks, NotificationProperties properties, JsonMapper json) {
		this.callbacks = callbacks;
		this.properties = properties;
		this.json = json;
	}

	@PostMapping("/api/v1/webhooks/notifications/{provider}")
	@Operation(summary = "Provider callback: delivered or bounced; 401 for an invalid signature")
	ResponseEntity<Void> callback(@PathVariable String provider,
			@RequestHeader(name = "X-Signature", required = false) @Nullable String signature,
			@RequestBody String body) {

		var secret = properties.webhookSecrets().get(provider);

		if (secret == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown provider");
		}
		if (signature == null || !MessageDigest.isEqual(sign(secret, body).getBytes(StandardCharsets.US_ASCII),
				signature.strip().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid signature");
		}

		DeliveryCallback callback;
		try {
			callback = json.readValue(body, DeliveryCallback.class);
		} catch (RuntimeException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unreadable callback");
		}

		if (callback.messageId() != null && callback.status() != null) {
			callbacks.handle(callback.messageId(), callback.status());
		}

		return ResponseEntity.ok().build();
	}

	/**
	 * Lowercase hex HMAC-SHA256 of the body.
	 */
	static String sign(String secret, String body) {
		try {
			var mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException(e);
		}
	}
}
