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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.exceptions.DirectorException;

/**
 * Tests of {@link DirectorAuth} that need no Director: the URL it builds, the checks a call makes
 * before it sends anything, and a Director that cannot be reached. The calls themselves are tested end
 * to end against the Director.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DirectorAuthTests {
	// Nothing listens here, so every request fails to connect.
	private static final String UNREACHABLE_URL = "http://127.0.0.1:1";

	private Vertx vertx;

	@BeforeAll
	void setup() {
		vertx = Vertx.vertx();
	}

	@AfterAll
	void tearDown() throws Exception {
		vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	private DirectorAuth auth(String url) {
		return DirectorAuth.builder().vertx(vertx).directorUrl(url).build();
	}

	@Test
	void authorizeUrl() throws Exception {
		DirectorAuth auth = auth("https://node.example.com:8443");
		assertEquals("https://node.example.com:8443/api/v1/auth/oauth/github/authorize" +
						"?redirect_uri=io.bosonnetwork.photon%3A%2F%2Fauth&scope=client",
				auth.authorizeUrl("github", "io.bosonnetwork.photon://auth"));
		auth.close().get(10, TimeUnit.SECONDS);

		// A Director published under a path prefix keeps it, with or without a trailing slash.
		DirectorAuth prefixed = auth("https://example.com/boson/");
		assertEquals("https://example.com/boson/api/v1/auth/oauth/google/authorize" +
						"?redirect_uri=https%3A%2F%2Fapp.example.com%2Fdone%3Fa%3D1&scope=client",
				prefixed.authorizeUrl("google", "https://app.example.com/done?a=1"));
		prefixed.close().get(10, TimeUnit.SECONDS);
	}

	@Test
	void unreachableDirectorFailsWithoutStatus() throws Exception {
		DirectorAuth auth = auth(UNREACHABLE_URL);
		try {
			ExecutionException e = assertThrows(ExecutionException.class,
					() -> auth.getProviders().get(30, TimeUnit.SECONDS));
			DirectorException error = assertInstanceOf(DirectorException.class, e.getCause());
			assertEquals(DirectorException.NO_HTTP_STATUS, error.getStatus());
		} finally {
			auth.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void invalidArgumentsThrow() throws Exception {
		DirectorAuth auth = auth(UNREACHABLE_URL);
		Signature.KeyPair key = Signature.KeyPair.random();
		try {
			assertThrows(IllegalArgumentException.class, () -> auth.authorizeUrl("", "app://auth"));
			assertThrows(IllegalArgumentException.class, () -> auth.authorizeUrl("github", ""));
			assertThrows(IllegalArgumentException.class, () -> auth.getSession(""));
			assertThrows(IllegalArgumentException.class, () -> auth.bindUserIdentity("", key));
			assertThrows(NullPointerException.class, () -> auth.bindUserIdentity("token", null));
			assertThrows(NullPointerException.class, () -> auth.requestDeviceRegistration(null, "Phone", "App"));
			assertThrows(IllegalArgumentException.class, () -> auth.finishDeviceRegistration(key, ""));
		} finally {
			auth.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void closedClientRefusesCalls() throws Exception {
		DirectorAuth auth = auth(UNREACHABLE_URL);
		auth.close().get(10, TimeUnit.SECONDS);
		assertTrue(auth.isClosed());
		assertThrows(IllegalStateException.class, auth::getProviders);
		assertThrows(IllegalStateException.class, auth::isProofOfWorkRegistrationEnabled);
	}

	@Test
	void incompleteConfigurationIsRejected() {
		assertThrows(IllegalStateException.class, () -> DirectorAuth.builder().vertx(vertx).build());
		assertThrows(IllegalArgumentException.class,
				() -> DirectorAuth.builder().vertx(vertx).directorUrl("ftp://node.example.com"));
	}
}
