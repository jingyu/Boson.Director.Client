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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Tests of {@link DirectorOAuth} that need no Director: its configuration, the token it starts with, and
 * the checks a call makes before it sends anything. The calls are tested end to end against the Director.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DirectorOAuthTests {
	private Vertx vertx;

	@BeforeAll
	void setup() {
		vertx = Vertx.vertx();
	}

	@AfterAll
	void tearDown() throws Exception {
		vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	private DirectorOAuth.Builder builder() {
		return DirectorOAuth.builder().vertx(vertx).directorUrl("http://127.0.0.1:1");
	}

	@Test
	void theSessionTokenIsRequired() {
		assertThrows(IllegalStateException.class, () -> builder().build());
		assertThrows(IllegalArgumentException.class, () -> builder().sessionToken(""));
		assertThrows(NullPointerException.class, () -> builder().sessionToken(null));
	}

	@Test
	void startsWithTheGivenToken() throws Exception {
		DirectorOAuth oauth = builder().sessionToken("token-1").build();
		assertEquals("token-1", oauth.getSessionToken());
		assertThrows(NullPointerException.class, () -> oauth.bindUserIdentity(null));
		assertThrows(NullPointerException.class, () -> oauth.disconnectIdentity(null));

		oauth.close().get(10, TimeUnit.SECONDS);
		assertTrue(oauth.isClosed());
		assertThrows(IllegalStateException.class, oauth::getSession);
		assertThrows(IllegalStateException.class, oauth::refresh);
	}
}
