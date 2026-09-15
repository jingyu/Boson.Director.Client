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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.exceptions.DirectorException;

/**
 * Tests that a Director answering 200 with a body the client cannot read fails the call with a
 * {@link DirectorException}, as any other failed call does, and never with the parser's own exception.
 * A stub answers every request with the body under test.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MalformedResponseTests {
	// Bodies that do not carry a usable id, or uri.
	private static final List<String> MALFORMED = List.of("", "<html>Not a Director</html>", "{}",
			"{\"id\": \"\", \"uri\": \"\"}", "{\"id\": \"0OIl\"}");

	private final Signature.KeyPair userKey = Signature.KeyPair.random();

	private Vertx vertx;
	private HttpServer server;
	private volatile String body = "";

	@BeforeAll
	void setup() throws Exception {
		vertx = Vertx.vertx();
		server = vertx.createHttpServer()
				.requestHandler(req -> req.response().setStatusCode(200).putHeader("Content-Type", "application/json").end(body))
				.listen(0, "127.0.0.1")
				.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	@AfterAll
	void tearDown() throws Exception {
		vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	private String url() {
		return "http://127.0.0.1:" + server.actualPort();
	}

	@Test
	void nodeIdLookup() throws Exception {
		DirectorClient client = DirectorClient.builder().vertx(vertx).directorUrl(url()).userKey(userKey).build();
		DirectorAdmin admin = DirectorAdmin.builder().vertx(vertx).directorUrl(url()).userKey(userKey).build();
		try {
			assertMalformed(client::getNodeId);
			assertMalformed(admin::getNodeId);
			// Without a configured node id, an authenticated call looks it up first.
			assertMalformed(client::getProfile);
			assertMalformed(admin::listUsers);
		} finally {
			client.close().get(10, TimeUnit.SECONDS);
			admin.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void avatarUri() throws Exception {
		DirectorClient client = DirectorClient.builder().vertx(vertx).directorUrl(url()).nodeId(Id.random())
				.userKey(userKey).build();
		try {
			assertMalformed(() -> client.updateAvatar(new byte[] { 1 }, "image/png"));
		} finally {
			client.close().get(10, TimeUnit.SECONDS);
		}
	}

	private void assertMalformed(Supplier<CompletableFuture<?>> call) {
		for (String malformed : MALFORMED) {
			body = malformed;
			ExecutionException e = assertThrows(ExecutionException.class, () -> call.get().get(10, TimeUnit.SECONDS),
					() -> "Accepted: " + malformed);
			DirectorException error = assertInstanceOf(DirectorException.class, e.getCause(),
					() -> "Body " + malformed + " failed with " + e.getCause());
			assertEquals(200, error.getStatus());
		}
	}
}
