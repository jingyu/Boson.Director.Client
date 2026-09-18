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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.exceptions.DirectorException;

/**
 * Tests of downloading an avatar against a copy the caller holds, against a stub Director that
 * versions the avatar with an entity tag. (The Director itself versions it by modification time; that
 * is tested end to end against the Director.)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AvatarRevalidationTests {
	private final Id nodeId = Id.random();
	private final Id userId = Id.random();

	private Vertx vertx;
	private HttpServer server;
	private DirectorClient client;

	// The stub's avatar: its version and image, or none; whether it answers 304 to anything; and the
	// conditional headers of the last avatar request.
	private volatile String version;
	private volatile byte[] image;
	private volatile boolean alwaysNotModified;
	private volatile MultiMap lastConditions;

	@BeforeAll
	void setup() throws Exception {
		vertx = Vertx.vertx();
		server = await(vertx.createHttpServer().requestHandler(req -> {
			if (req.path().endsWith("/client/id")) {
				req.response().putHeader("Content-Type", "application/json").end("{\"id\":\"" + nodeId + "\"}");
				return;
			}

			MultiMap conditions = MultiMap.caseInsensitiveMultiMap();
			if (req.getHeader("If-None-Match") != null)
				conditions.set("If-None-Match", req.getHeader("If-None-Match"));
			if (req.getHeader("If-Modified-Since") != null)
				conditions.set("If-Modified-Since", req.getHeader("If-Modified-Since"));
			lastConditions = conditions;

			if (image == null) {
				req.response().setStatusCode(404).end("Not Found");
			} else if (alwaysNotModified || ("\"" + version + "\"").equals(req.getHeader("If-None-Match"))) {
				req.response().setStatusCode(304).putHeader("ETag", "\"" + version + "\"").end();
			} else {
				req.response().putHeader("Content-Type", "image/png").putHeader("ETag", "\"" + version + "\"")
						.end(Buffer.buffer(image));
			}
		}).listen(0, "127.0.0.1"));

		client = DirectorClient.builder().vertx(vertx).directorUrl("http://127.0.0.1:" + server.actualPort())
				.userKey(Signature.KeyPair.random()).build();
	}

	@AfterAll
	void tearDown() throws Exception {
		await(client.close());
		await(vertx.close());
	}

	@BeforeEach
	void reset() {
		version = "v1";
		image = "first".getBytes(StandardCharsets.UTF_8);
		alwaysNotModified = false;
		lastConditions = null;
	}

	private static <T> T await(Future<T> future) throws Exception {
		return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
	}

	private static <T> T await(CompletableFuture<T> future) throws Exception {
		return future.get(30, TimeUnit.SECONDS);
	}

	@Test
	void anUnchangedAvatarIsTheCallersCopy() throws Exception {
		Avatar first = await(client.getUserAvatar(userId));
		assertNotNull(first);
		assertEquals("\"v1\"", first.getETag().orElseThrow());
		assertTrue(first.isRevalidatable());
		assertTrue(lastConditions.isEmpty());

		assertSame(first, await(client.getUserAvatar(userId, first)));
		assertEquals("\"v1\"", lastConditions.get("If-None-Match"));

		// A copy restored from storage revalidates just the same.
		Avatar restored = Avatar.of(first.getContentType(), first.getData(), first.getETag().orElse(null),
				first.getLastModified().orElse(null));
		assertSame(restored, await(client.getUserAvatar(userId, restored)));
	}

	@Test
	void aChangedAvatarIsDownloaded() throws Exception {
		Avatar first = await(client.getUserAvatar(userId));
		assertNotNull(first);

		version = "v2";
		image = "second".getBytes(StandardCharsets.UTF_8);
		Avatar second = await(client.getUserAvatar(userId, first));
		assertNotNull(second);
		assertNotSame(first, second);
		assertArrayEquals(image, second.getData());
		assertEquals("\"v2\"", second.getETag().orElseThrow());
	}

	@Test
	void aRemovedAvatarIsNull() throws Exception {
		Avatar first = await(client.getUserAvatar(userId));
		image = null;
		assertNull(await(client.getUserAvatar(userId, first)));
	}

	@Test
	void aCopyWithoutValidatorsIsDownloadedAgain() throws Exception {
		Avatar bare = Avatar.of("image/png", new byte[] { 1 }, null, null);
		assertFalse(bare.isRevalidatable());

		Avatar downloaded = await(client.getUserAvatar(userId, bare));
		assertNotSame(bare, downloaded);
		assertTrue(lastConditions.isEmpty());
		// No copy at all: an unconditional download.
		assertArrayEquals(image, await(client.getUserAvatar(userId, null)).getData());
		assertTrue(lastConditions.isEmpty());
	}

	@Test
	void notModifiedIsAFailureUnlessAsked() {
		// Only a conditional request can be answered 304; to any other it is a malformed answer.
		alwaysNotModified = true;
		ExecutionException e = assertThrows(ExecutionException.class,
				() -> client.getUserAvatar(userId).get(30, TimeUnit.SECONDS));
		DirectorException error = assertInstanceOf(DirectorException.class, e.getCause());
		assertEquals(304, error.getStatus());
	}
}
