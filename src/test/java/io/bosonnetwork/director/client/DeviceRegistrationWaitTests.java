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

import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.json.Json;

/**
 * Tests that {@link DirectorAuth#finishDeviceRegistration(Signature.KeyPair, String)} waits as long as
 * the Director holds it, against a stub Director that answers only after the connection's own idle
 * timeout has passed. The real Director holds the call until the user answers - which may well take
 * longer than a minute - so this takes over a minute to run.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DeviceRegistrationWaitTests {
	// Longer than the 60 seconds a pooled connection may sit idle.
	private static final long ANSWER_DELAY = TimeUnit.SECONDS.toMillis(65);

	private final Id userId = Id.random();
	private final byte[] userKey = { 7, 7, 7 };

	private Vertx vertx;
	private HttpServer server;

	@BeforeAll
	void setup() throws Exception {
		vertx = Vertx.vertx();
		server = await(vertx.createHttpServer().requestHandler(req -> req.body().onSuccess(body ->
				vertx.setTimer(ANSWER_DELAY, t -> req.response().setStatusCode(201)
						.putHeader("Content-Type", "application/json")
						.end(new JsonObject()
								.put("userId", userId.toString())
								.put("userPrivateKey", Json.BASE64_ENCODER.encodeToString(userKey))
								.toBuffer())))).listen(0, "127.0.0.1"));
	}

	@AfterAll
	void tearDown() throws Exception {
		await(vertx.close());
	}

	private static <T> T await(Future<T> future) throws Exception {
		return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
	}

	@Test
	void finishOutwaitsTheConnectionIdleTimeout() throws Exception {
		DirectorAuth auth = DirectorAuth.builder().vertx(vertx).directorUrl("http://127.0.0.1:" + server.actualPort())
				.build();
		try {
			DeviceApproval approval = auth.finishDeviceRegistration(Signature.KeyPair.random(), "registration")
					.get(ANSWER_DELAY + TimeUnit.SECONDS.toMillis(30), TimeUnit.MILLISECONDS);
			assertEquals(userId, approval.getUserId());
			assertArrayEquals(userKey, approval.getUserKey());
		} finally {
			auth.close().get(10, TimeUnit.SECONDS);
		}
	}
}
