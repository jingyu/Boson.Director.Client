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

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;

/**
 * Tests that a client with a resolve-to address reaches the Director there, while the request still names
 * the host of the Director URL - which the host name alone could never reach.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ResolveToAddressTests {
	// Reserved never to resolve (RFC 2606).
	private static final String UNRESOLVABLE_HOST = "director.invalid";

	private final Id nodeId = Id.random();

	private Vertx vertx;
	private HttpServer server;
	private volatile String hostHeader;

	@BeforeAll
	void setup() throws Exception {
		vertx = Vertx.vertx();
		server = vertx.createHttpServer()
				.requestHandler(req -> {
					hostHeader = req.getHeader("Host");
					req.response().putHeader("Content-Type", "application/json")
							.end("{\"id\": \"" + nodeId.toBase58String() + "\"}");
				})
				.listen(0, "127.0.0.1")
				.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	@AfterAll
	void tearDown() throws Exception {
		vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	@Test
	void connectsToTheAddressAndNamesTheUrlHost() throws Exception {
		int port = server.actualPort();
		DirectorClient client = DirectorClient.builder()
				.vertx(vertx)
				.directorUrl("http://" + UNRESOLVABLE_HOST + ":" + port)
				.resolveToAddress(new InetSocketAddress("127.0.0.1", port))
				.userKey(Signature.KeyPair.random())
				.build();
		try {
			assertEquals(nodeId, client.getNodeId().get(10, TimeUnit.SECONDS));
			assertEquals(UNRESOLVABLE_HOST + ":" + port, hostHeader);
		} finally {
			client.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void theAdminClientConnectsToTheAddress() throws Exception {
		int port = server.actualPort();
		DirectorAdmin admin = DirectorAdmin.builder()
				.vertx(vertx)
				.directorUrl("http://" + UNRESOLVABLE_HOST + ":" + port)
				.resolveToAddress(new InetSocketAddress("127.0.0.1", port))
				.userKey(Signature.KeyPair.random())
				.build();
		try {
			assertEquals(nodeId, admin.getNodeId().get(10, TimeUnit.SECONDS));
		} finally {
			admin.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void anUnresolvedAddressIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> DirectorClient.builder()
				.resolveToAddress(InetSocketAddress.createUnresolved(UNRESOLVABLE_HOST, 9000)));
	}
}
