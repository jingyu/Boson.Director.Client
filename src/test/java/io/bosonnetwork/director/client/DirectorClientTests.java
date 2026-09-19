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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.exceptions.DirectorException;

/**
 * Tests of {@link DirectorClient} that need no Director: the checks a call makes before it sends
 * anything, and a Director that cannot be reached. The calls themselves are tested end to end
 * against the Director.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DirectorClientTests {
	// Nothing listens here, so every request fails to connect.
	private static final String UNREACHABLE_URL = "http://127.0.0.1:1";

	private final Signature.KeyPair userKey = Signature.KeyPair.random();
	private final Signature.KeyPair deviceKey = Signature.KeyPair.random();

	private Vertx vertx;

	@BeforeAll
	void setup() {
		vertx = Vertx.vertx();
	}

	@AfterAll
	void tearDown() throws Exception {
		vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	private DirectorClient.Builder builder() {
		return DirectorClient.builder().vertx(vertx).directorUrl(UNREACHABLE_URL);
	}

	@Test
	void unreachableDirectorFailsWithoutStatus() throws Exception {
		DirectorClient client = builder().userKey(userKey).build();
		try {
			ExecutionException e = assertThrows(ExecutionException.class,
					() -> client.getNodeId().get(30, TimeUnit.SECONDS));
			DirectorException error = assertInstanceOf(DirectorException.class, e.getCause());
			assertEquals(DirectorException.NO_HTTP_STATUS, error.getStatus());
		} finally {
			client.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void reportsItsIdentity() throws Exception {
		DirectorClient client = builder().userKey(userKey).deviceKey(deviceKey).build();
		assertEquals(Id.of(userKey.publicKey().bytes()), client.getUserId());
		assertEquals(Id.of(deviceKey.publicKey().bytes()), client.getDeviceId());
		client.close().get(10, TimeUnit.SECONDS);

		DirectorClient user = builder().userKey(userKey).build();
		assertEquals(Id.of(userKey.publicKey().bytes()), user.getUserId());
		assertNull(user.getDeviceId());
		user.close().get(10, TimeUnit.SECONDS);
	}

	@Test
	void closedClientRefusesCalls() throws Exception {
		DirectorClient client = builder().userKey(userKey).build();
		client.close().get(10, TimeUnit.SECONDS);
		assertTrue(client.isClosed());
		assertThrows(IllegalStateException.class, client::getNodeId);
		assertThrows(IllegalStateException.class, client::getProfile);
	}

	@Test
	void incompleteConfigurationIsRejected() {
		// An identity is required: the user key, or the user id with a device key.
		assertThrows(IllegalStateException.class, () -> builder().build());
		assertThrows(IllegalStateException.class, () -> builder().deviceKey(deviceKey).build());
		// Setting the user id drops the user key.
		assertThrows(IllegalStateException.class, () -> builder().userKey(userKey).userId(Id.random()).build());
		assertThrows(IllegalStateException.class, () -> builder().userId(Id.random()).build());
		assertThrows(IllegalStateException.class, () -> DirectorClient.builder().vertx(vertx).build());
		assertThrows(IllegalArgumentException.class, () -> builder().directorUrl("ftp://node.example.com"));
		assertThrows(IllegalArgumentException.class, () -> builder().userKey(new byte[16]));
	}

	@Test
	void registrationNeedsTheKeysItSignsWith() throws Exception {
		// Authenticated as a device: there is no user key to prove the registration with.
		DirectorClient device = builder().userId(Id.of(userKey.publicKey().bytes())).deviceKey(deviceKey).build();
		// The user key alone: there is no device key to register as the initial device, or as a device.
		DirectorClient user = builder().userKey(userKey).build();
		try {
			assertThrows(IllegalStateException.class, () -> device.registerUser(new UserRegistration()));
			assertThrows(IllegalStateException.class,
					() -> user.registerUser(new UserRegistration().initialDevice("Laptop", "Tests")));
			assertThrows(IllegalStateException.class, () -> user.registerDevice("Laptop", "Tests"));
		} finally {
			device.close().get(10, TimeUnit.SECONDS);
			user.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void approvingADeviceNeedsTheUserKey() throws Exception {
		// Acting as a device: there is no user key to hand over.
		DirectorClient device = builder().userId(Id.of(userKey.publicKey().bytes())).deviceKey(deviceKey).build();
		try {
			PairingCode code = new DeviceRegistration("registration", Signature.KeyPair.random(),
					io.bosonnetwork.crypto.CryptoBox.KeyPair.random()).getPairingCode();
			assertThrows(IllegalStateException.class, () -> device.approveDeviceRegistration(code));
		} finally {
			device.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void invalidArgumentsThrow() throws Exception {
		DirectorClient client = builder().userKey(userKey).build();
		try {
			assertThrows(IllegalArgumentException.class, () -> client.updateProfile(new ProfileUpdate()));
			assertThrows(IllegalArgumentException.class, () -> client.setPassphrase(""));
			assertThrows(IllegalArgumentException.class, () -> client.updatePassphrase("current", ""));
			assertThrows(IllegalArgumentException.class, () -> client.updateAvatar(new byte[0], "image/png"));
			assertThrows(IllegalArgumentException.class, () -> client.updateAvatar(new byte[] { 1 }, "image/gif"));
			assertThrows(IllegalArgumentException.class, () -> client.updateAvatar(Path.of("avatar.gif")));
			assertThrows(NullPointerException.class, () -> client.removeDevice(null));
			assertThrows(NullPointerException.class, () -> client.getUserProfile(null));
			assertThrows(NullPointerException.class, () -> client.getUserAvatar(null));
			assertThrows(NullPointerException.class, () -> client.getDeviceRegistration(null));
			assertThrows(NullPointerException.class, () -> client.denyDeviceRegistration(null));
			assertThrows(NullPointerException.class, () -> client.refreshUserAvatar(Id.random(), null));
		} finally {
			client.close().get(10, TimeUnit.SECONDS);
		}
	}
}
