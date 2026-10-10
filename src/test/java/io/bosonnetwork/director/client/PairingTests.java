/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.director.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.director.client.exceptions.RegistrationDeniedException;
import io.bosonnetwork.director.client.exceptions.RegistrationExpiredException;

/**
 * Tests of pairing a new device: the pairing code, and the user key handed from the approving device to
 * the new one - sealed by one client, relayed by a stub Director, opened by the other - including its
 * interoperability with the format apps used before it moved into this library.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PairingTests {
	// The id the mock Director gives the registration: Base58, as a real one is.
	private static final String REGISTRATION_ID = "Gj7rTyQ4HXa9v2Lm";

	private final Id nodeId = Id.random();
	private final Signature.KeyPair userKey = Signature.KeyPair.random();
	private final Id userId = Id.of(userKey.publicKey().bytes());

	private Vertx vertx;
	private HttpServer server;

	// The stub Director's registration: the request as the new device made it, the approval's body, the
	// key it relayed, and how the registration finishes.
	private final AtomicReference<JsonObject> request = new AtomicReference<>();
	private final AtomicReference<JsonObject> approvalBody = new AtomicReference<>();
	private final AtomicReference<String> relayedKey = new AtomicReference<>();
	private volatile int finishStatus;
	private volatile Id finishUserId;

	@BeforeAll
	void setup() throws Exception {
		vertx = Vertx.vertx();
		server = vertx.createHttpServer().requestHandler(req -> req.body().onSuccess(body -> {
			String path = req.path();
			if (path.endsWith("/client/id")) {
				req.response().putHeader("Content-Type", "application/json").end("{\"id\":\"" + nodeId + "\"}");
			} else if (req.method() == HttpMethod.POST && path.endsWith("/client/devices/registrations")) {
				request.set(body.toJsonObject());
				req.response().setStatusCode(201).putHeader("Content-Type", "application/json")
						.end("{\"registrationId\":\"" + REGISTRATION_ID + "\"}");
			} else if (req.method() == HttpMethod.GET && path.endsWith("/" + REGISTRATION_ID)) {
				JsonObject made = request.get();
				req.response().putHeader("Content-Type", "application/json").end(new JsonObject()
						.put("deviceId", made.getString("deviceId"))
						.put("deviceName", made.getString("deviceName"))
						.put("appName", made.getString("appName"))
						.put("kind", "app")
						.put("wantsUserKey", made.getBoolean("wantsUserKey", false))
						.put("nonce", made.getString("nonce"))
						.put("requestedFrom", "192.0.2.7")
						.put("createdAt", 1000L)
						.put("expiresAt", 181000L).toBuffer());
			} else if (req.method() == HttpMethod.PATCH) {
				approvalBody.set(body.toJsonObject());
				relayedKey.set(body.toJsonObject().getString("userPrivateKey"));
				req.response().setStatusCode(204).end();
			} else if (req.method() == HttpMethod.POST && path.endsWith("/" + REGISTRATION_ID)) {
				if (finishStatus != 201) {
					req.response().setStatusCode(finishStatus).end("Refused - by the stub");
					return;
				}
				JsonObject finished = new JsonObject().put("userId", finishUserId.toString());
				if (relayedKey.get() != null)
					finished.put("userPrivateKey", relayedKey.get());
				req.response().setStatusCode(201).putHeader("Content-Type", "application/json").end(finished.toBuffer());
			} else {
				req.response().setStatusCode(404).end("Not Found");
			}
		})).listen(0, "127.0.0.1").toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	@AfterAll
	void tearDown() throws Exception {
		vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	@BeforeEach
	void reset() {
		request.set(null);
		approvalBody.set(null);
		relayedKey.set(null);
		finishStatus = 201;
		finishUserId = userId;
	}

	private String url() {
		return "http://127.0.0.1:" + server.actualPort();
	}

	private static <T> T await(Future<T> future) throws Exception {
		return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
	}

	@Test
	void theCodeRoundTrips() {
		String registrationId = Id.random().toBase58String();
		PairingCode code = new DeviceRegistration(registrationId, Signature.KeyPair.random(), CryptoBox.KeyPair.random())
				.getPairingCode();
		assertTrue(code.toString().startsWith("boson:pair:1:" + registrationId + ":"), code.toString());
		// The key: 32 bytes in base64url without padding.
		assertEquals(43, code.toString().substring(("boson:pair:1:" + registrationId + ":").length()).length());
		assertEquals(code, PairingCode.parse(code.toString()));
		assertEquals(code, PairingCode.parse("  " + code + "\n"));
		assertEquals(registrationId, code.getRegistrationId());
	}

	@Test
	void theFormBeforeIsStillRead() {
		// Apps from Boson 3.1 show bosonpair:1:<id>:<key>.
		String registrationId = Id.random().toBase58String();
		String key = Base64.getUrlEncoder().withoutPadding().encodeToString(CryptoBox.KeyPair.random().publicKey().bytes());
		PairingCode legacy = PairingCode.parse("bosonpair:1:" + registrationId + ":" + key);
		assertEquals(PairingCode.parse("boson:pair:1:" + registrationId + ":" + key), legacy);
		assertEquals("boson:pair:1:" + registrationId + ":" + key, legacy.toString());
	}

	@Test
	void whatIsNotACodeIsRejected() {
		String id = Id.random().toBase58String();
		String key = Base64.getUrlEncoder().withoutPadding().encodeToString(CryptoBox.KeyPair.random().publicKey().bytes());
		for (String text : new String[] { "", "bosonpair", "boson:pair:1:" + id, "boson:pair:2:" + id + ":" + key,
				"boson:pair:1::" + key, "boson:pair:1:" + id + ":not!base64", "boson:pair:1:" + id + ":AAAA",
				// Padded, or not a Base58 registration id.
				"boson:pair:1:" + id + ":" + key + "=", "boson:pair:1:reg-1:" + key, "boson:pair:1:" + id + ":" + key + ":x",
				"bosonpair:1:" + id, "bosonpair:2:" + id + ":" + key, "otherpair:1:" + id + ":" + key,
				"bosonpair:1::" + key, "pmpair:1:" + id + ":" + key })
			assertThrows(IllegalArgumentException.class, () -> PairingCode.parse(text), text);
	}

	@Test
	void theUserKeyIsHandedOverSealed() throws Exception {
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		DirectorClient approver = DirectorClient.builder().vertx(vertx).directorUrl(url()).userKey(userKey).build();
		try {
			DeviceRegistration registration = await(Future.fromCompletionStage(
					newDevice.requestDeviceRegistration(Signature.KeyPair.random(), "Phone", "Tests", true)));
			assertEquals(REGISTRATION_ID, registration.getRegistrationId());
			assertTrue(request.get().getBoolean("wantsUserKey"));

			// The approving device reads the code the new device shows, as text.
			PairingCode scanned = PairingCode.parse(registration.getPairingCode().toString());
			approver.approveDeviceRegistration(scanned).get(30, TimeUnit.SECONDS);

			// What the Director relayed is not the key.
			byte[] relayed = Base64.getUrlDecoder().decode(relayedKey.get());
			assertNotEquals(userKey.privateKey().bytes().length, relayed.length);

			DeviceApproval approval = newDevice.finishDeviceRegistration(registration).get(30, TimeUnit.SECONDS);
			assertEquals(userId, approval.getUserId());
			assertArrayEquals(userKey.privateKey().bytes(), approval.getUserKey().orElseThrow().privateKey().bytes());
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
			approver.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void aDeviceThatDidNotAskGetsNoKey() throws Exception {
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		DirectorClient approver = DirectorClient.builder().vertx(vertx).directorUrl(url()).userKey(userKey).build();
		try {
			DeviceRegistration registration = newDevice.requestDeviceRegistration(Signature.KeyPair.random(),
					"Laptop", "boson-cli").get(30, TimeUnit.SECONDS);
			assertFalse(request.get().containsKey("wantsUserKey"));

			PendingDevice pending = approver.getDeviceRegistration(registration.getPairingCode())
					.get(30, TimeUnit.SECONDS).orElseThrow();
			assertFalse(pending.wantsUserKey());
			assertEquals("Laptop", pending.getDeviceName());
			assertEquals("192.0.2.7", pending.getRequestedFrom().orElseThrow());
			assertEquals(181000L, pending.getExpiresAt());

			// An id is enough when no key goes over.
			approver.approveDeviceRegistration(registration.getRegistrationId(), null, false).get(30, TimeUnit.SECONDS);
			assertFalse(approvalBody.get().containsKey("userPrivateKey"));
			assertFalse(approvalBody.get().containsKey("admin"));

			DeviceApproval approval = newDevice.finishDeviceRegistration(registration).get(30, TimeUnit.SECONDS);
			assertEquals(userId, approval.getUserId());
			assertTrue(approval.getUserKey().isEmpty());
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
			approver.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void aDeviceThatWantsTheKeyNeedsThePairingCode() throws Exception {
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		DirectorClient approver = DirectorClient.builder().vertx(vertx).directorUrl(url()).userKey(userKey).build();
		try {
			DeviceRegistration registration = newDevice.requestDeviceRegistration(Signature.KeyPair.random(),
					"Phone", "Photon", true).get(30, TimeUnit.SECONDS);
			ExecutionException e = assertThrows(ExecutionException.class, () -> approver
					.approveDeviceRegistration(registration.getRegistrationId(), null, false).get(30, TimeUnit.SECONDS));
			assertInstanceOf(IllegalStateException.class, e.getCause());

			// A client without the user key can't hand it over, even with the code.
			DirectorClient keyless = DirectorClient.builder().vertx(vertx).directorUrl(url()).userId(userId)
					.deviceKey(Signature.KeyPair.random()).build();
			try {
				ExecutionException noKey = assertThrows(ExecutionException.class, () -> keyless
						.approveDeviceRegistration(registration.getPairingCode()).get(30, TimeUnit.SECONDS));
				assertInstanceOf(IllegalStateException.class, noKey.getCause());
			} finally {
				keyless.close().get(10, TimeUnit.SECONDS);
			}
			assertNull(approvalBody.get(), "nothing was sent");
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
			approver.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void anAdminApprovalSignsTheDeviceWithItsNonce() throws Exception {
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		DirectorClient approver = DirectorClient.builder().vertx(vertx).directorUrl(url()).userKey(userKey).build();
		try {
			Signature.KeyPair deviceKey = Signature.KeyPair.random();
			DeviceRegistration registration = newDevice.requestDeviceRegistration(deviceKey, "Browser", "Portal")
					.get(30, TimeUnit.SECONDS);
			approver.approveDeviceRegistration(registration.getPairingCode(), "pass phrase", true).get(30, TimeUnit.SECONDS);

			JsonObject body = approvalBody.get();
			assertTrue(body.getBoolean("admin"));
			assertEquals("pass phrase", body.getString("passphrase"));
			byte[] nonce = Base64.getUrlDecoder().decode(request.get().getString("nonce"));
			byte[] userSig = Base64.getUrlDecoder().decode(body.getString("userSig"));
			assertTrue(userKey.publicKey().verify(DirectorClient.deviceAuthorization(
					Id.of(deviceKey.publicKey().bytes()), nonce), userSig));

			// Without the user key there is nothing to sign with.
			DirectorClient keyless = DirectorClient.builder().vertx(vertx).directorUrl(url()).userId(userId)
					.deviceKey(Signature.KeyPair.random()).build();
			try {
				assertThrows(IllegalStateException.class,
						() -> keyless.approveDeviceRegistration(registration.getPairingCode(), null, true));
			} finally {
				keyless.close().get(10, TimeUnit.SECONDS);
			}
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
			approver.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void aDeviceThatAskedForTheKeyFailsWithoutIt() throws Exception {
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		try {
			DeviceRegistration registration = newDevice.requestDeviceRegistration(Signature.KeyPair.random(),
					"Phone", "Photon", true).get(30, TimeUnit.SECONDS);
			// The Director (or something in between) finishes without the key the device asked for.
			ExecutionException e = assertThrows(ExecutionException.class,
					() -> newDevice.finishDeviceRegistration(registration).get(30, TimeUnit.SECONDS));
			assertNotNull(e.getCause());
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void aKeySealedTheFirstWayOpens() throws Exception {
		// Apps sealed the 64-byte user key to the code's key before this library did: the same bytes.
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		try {
			DeviceRegistration registration = newDevice.requestDeviceRegistration(Signature.KeyPair.random(),
					"Phone", "Tests", true).get(30, TimeUnit.SECONDS);
			byte[] sealed = CryptoBox.encryptSealed(userKey.privateKey().bytes(),
					registration.getPairingCode().publicKey());
			relayedKey.set(Base64.getUrlEncoder().withoutPadding().encodeToString(sealed));

			assertEquals(userId, newDevice.finishDeviceRegistration(registration).get(30, TimeUnit.SECONDS).getUserId());
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void aKeyOfAnotherUserIsRefused() throws Exception {
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		DirectorClient approver = DirectorClient.builder().vertx(vertx).directorUrl(url()).userKey(userKey).build();
		try {
			DeviceRegistration registration = newDevice.requestDeviceRegistration(Signature.KeyPair.random(),
					"Phone", "Tests", true).get(30, TimeUnit.SECONDS);
			approver.approveDeviceRegistration(registration.getPairingCode()).get(30, TimeUnit.SECONDS);

			// The Director names a user the relayed key does not belong to.
			finishUserId = Id.random();
			ExecutionException e = assertThrows(ExecutionException.class,
					() -> newDevice.finishDeviceRegistration(registration).get(30, TimeUnit.SECONDS));
			assertInstanceOf(DirectorException.class, e.getCause());
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
			approver.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void deniedAndExpiredAreTyped() throws Exception {
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		try {
			DeviceRegistration registration = newDevice.requestDeviceRegistration(Signature.KeyPair.random(),
					"Phone", "Tests").get(30, TimeUnit.SECONDS);

			finishStatus = 412;
			ExecutionException denied = assertThrows(ExecutionException.class,
					() -> newDevice.finishDeviceRegistration(registration).get(30, TimeUnit.SECONDS));
			assertInstanceOf(RegistrationDeniedException.class, denied.getCause());

			finishStatus = 408;
			ExecutionException expired = assertThrows(ExecutionException.class,
					() -> newDevice.finishDeviceRegistration(registration).get(30, TimeUnit.SECONDS));
			assertInstanceOf(RegistrationExpiredException.class, expired.getCause());
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
		}
	}
}
