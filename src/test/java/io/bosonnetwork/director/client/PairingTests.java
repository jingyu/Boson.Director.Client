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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
	private final Id nodeId = Id.random();
	private final Signature.KeyPair userKey = Signature.KeyPair.random();
	private final Id userId = Id.of(userKey.publicKey().bytes());

	private Vertx vertx;
	private HttpServer server;

	// The stub Director's registration: the key relayed by the approval, and how it finishes.
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
				req.response().setStatusCode(201).putHeader("Content-Type", "application/json")
						.end("{\"registrationId\":\"reg-1\"}");
			} else if (req.method() == HttpMethod.PATCH) {
				relayedKey.set(body.toJsonObject().getString("userPrivateKey"));
				req.response().setStatusCode(204).end();
			} else if (req.method() == HttpMethod.POST && path.endsWith("/reg-1")) {
				if (finishStatus != 201) {
					req.response().setStatusCode(finishStatus).end("Refused - by the stub");
					return;
				}
				req.response().setStatusCode(201).putHeader("Content-Type", "application/json")
						.end(new JsonObject().put("userId", finishUserId.toString())
								.put("userPrivateKey", relayedKey.get()).toBuffer());
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
		PairingCode code = new DeviceRegistration("reg-1", Signature.KeyPair.random(), CryptoBox.KeyPair.random())
				.getPairingCode();
		assertTrue(code.toString().startsWith("bosonpair:1:reg-1:"));
		assertEquals(code, PairingCode.parse(code.toString()));
		assertEquals(code, PairingCode.parse("  " + code + "\n"));
		assertEquals("reg-1", code.getRegistrationId());
	}

	@Test
	void whatIsNotACodeIsRejected() {
		String key = Base64.getUrlEncoder().withoutPadding().encodeToString(CryptoBox.KeyPair.random().publicKey().bytes());
		for (String text : new String[] { "", "bosonpair", "bosonpair:1:reg-1", "bosonpair:2:reg-1:" + key,
				"otherpair:1:reg-1:" + key, "bosonpair:1::" + key, "bosonpair:1:reg-1:not!base64",
				"bosonpair:1:reg-1:AAAA" })
			assertThrows(IllegalArgumentException.class, () -> PairingCode.parse(text), text);
	}

	@Test
	void theUserKeyIsHandedOverSealed() throws Exception {
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		DirectorClient approver = DirectorClient.builder().vertx(vertx).directorUrl(url()).userKey(userKey).build();
		try {
			DeviceRegistration registration = await(Future.fromCompletionStage(
					newDevice.requestDeviceRegistration(Signature.KeyPair.random(), "Phone", "Tests")));
			assertEquals("reg-1", registration.getRegistrationId());

			// The approving device reads the code the new device shows, as text.
			PairingCode scanned = PairingCode.parse(registration.getPairingCode().toString());
			approver.approveDeviceRegistration(scanned).get(30, TimeUnit.SECONDS);

			// What the Director relayed is not the key.
			byte[] relayed = Base64.getUrlDecoder().decode(relayedKey.get());
			assertNotEquals(userKey.privateKey().bytes().length, relayed.length);

			DeviceApproval approval = newDevice.finishDeviceRegistration(registration).get(30, TimeUnit.SECONDS);
			assertEquals(userId, approval.getUserId());
			assertArrayEquals(userKey.privateKey().bytes(), approval.getUserKey().privateKey().bytes());
		} finally {
			newDevice.close().get(10, TimeUnit.SECONDS);
			approver.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void aKeySealedTheFirstWayOpens() throws Exception {
		// Apps sealed the 64-byte user key to the code's key before this library did: the same bytes.
		DirectorGuest newDevice = DirectorGuest.builder().vertx(vertx).directorUrl(url()).build();
		try {
			DeviceRegistration registration = newDevice.requestDeviceRegistration(Signature.KeyPair.random(),
					"Phone", "Tests").get(30, TimeUnit.SECONDS);
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
					"Phone", "Tests").get(30, TimeUnit.SECONDS);
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
