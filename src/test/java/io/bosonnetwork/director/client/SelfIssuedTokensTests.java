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

import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.cwt.Claim;
import io.bosonnetwork.cwt.SignedCwt;
import io.bosonnetwork.director.client.exceptions.UnauthorizedException;
import io.bosonnetwork.service.AccessScope;

/**
 * Tests of {@link SelfIssuedTokens} against a stub Director whose clock is set apart from ours. The
 * stub allows no skew at all, so every token it accepts is dated correctly by its clock.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SelfIssuedTokensTests {
	private static final Logger log = LoggerFactory.getLogger(SelfIssuedTokensTests.class);

	private final Id nodeId = Id.random();
	private final Signature.KeyPair userKey = Signature.KeyPair.random();

	private Vertx vertx;
	private HttpServer server;

	// The stub Director: how far its clock is ahead of ours, whether it dates its answers, whether it
	// refuses every token, and how many requests it has seen.
	private volatile long clockOffset;
	private volatile boolean sendsDate;
	private volatile boolean refusesAll;
	private final AtomicInteger requests = new AtomicInteger();

	@BeforeAll
	void setup() throws Exception {
		vertx = Vertx.vertx();
		server = await(vertx.createHttpServer().requestHandler(req -> {
			requests.incrementAndGet();
			long now = System.currentTimeMillis() + clockOffset;
			if (sendsDate)
				req.response().putHeader("Date",
						DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC)));

			boolean accepted = !refusesAll && accepts(req.getHeader("Authorization"), now);
			req.response().setStatusCode(accepted ? 200 : 401).end(accepted ? "{}" : "Unauthorized");
		}).listen(0, "127.0.0.1"));
	}

	@AfterAll
	void tearDown() throws Exception {
		await(vertx.close());
	}

	@BeforeEach
	void reset() {
		clockOffset = 0;
		sendsDate = true;
		refusesAll = false;
		requests.set(0);
	}

	// Accepts a token for this node that is valid at the given time, allowing no skew.
	private boolean accepts(String authorization, long now) {
		if (authorization == null || !authorization.startsWith("Bearer "))
			return false;

		try {
			SignedCwt cwt = SignedCwt.parser().ignoreExpiration().ignoreNotBefore().ignoreIssuedAt()
					.parse(authorization.substring("Bearer ".length()));
			long seconds = now / 1000;
			return cwt.getClaims().get(Claim.NOT_BEFORE.getValue()) instanceof Number nbf &&
					cwt.getClaims().get(Claim.EXPIRATION.getValue()) instanceof Number exp &&
					nbf.longValue() <= seconds && seconds < exp.longValue() &&
					nodeId.equals(cwt.getClaimAsId(Claim.AUDIENCE.getValue()));
		} catch (Exception e) {
			return false;
		}
	}

	@Test
	void correctsForADirectorClockAhead() throws Exception {
		assertCorrected(TimeUnit.HOURS.toMillis(1));
	}

	@Test
	void correctsForADirectorClockBehind() throws Exception {
		assertCorrected(-TimeUnit.HOURS.toMillis(1));
	}

	private void assertCorrected(long offset) throws Exception {
		clockOffset = offset;
		withTransport((transport, tokens) -> {
			// Rejected once, then repeated with a token dated by the Director's clock.
			assertEquals(200, await(get(transport, tokens)).statusCode());
			assertEquals(2, requests.get());

			// The correction sticks.
			assertEquals(200, await(get(transport, tokens)).statusCode());
			assertEquals(3, requests.get());
		});
	}

	@Test
	void smallSkewIsAbsorbedByBackdating() throws Exception {
		// Beyond the skew that triggers a correction, but within the backdating.
		clockOffset = -(SelfIssuedTokens.MAX_CLOCK_SKEW + SelfIssuedTokens.BACKDATE) / 2;
		withTransport((transport, tokens) -> {
			assertEquals(200, await(get(transport, tokens)).statusCode());
			assertEquals(1, requests.get());
		});
	}

	@Test
	void rejectionWithoutSkewIsNotRepeated() throws Exception {
		refusesAll = true;
		withTransport((transport, tokens) -> {
			assertUnauthorized(get(transport, tokens));
			assertEquals(1, requests.get());
		});
	}

	@Test
	void rejectionWithoutDateIsNotRepeated() throws Exception {
		clockOffset = TimeUnit.HOURS.toMillis(1);
		sendsDate = false;
		withTransport((transport, tokens) -> {
			assertUnauthorized(get(transport, tokens));
			assertEquals(1, requests.get());
		});
	}

	private interface TransportTest {
		void run(DirectorTransport transport, SelfIssuedTokens tokens) throws Exception;
	}

	private void withTransport(TransportTest test) throws Exception {
		DirectorTransport transport = new DirectorTransport(vertx,
				new URL("http://127.0.0.1:" + server.actualPort()), "/client", null, null, null, log);
		SelfIssuedTokens tokens = new SelfIssuedTokens(new CryptoIdentity(userKey), Id.of(userKey.publicKey().bytes()),
				null, AccessScope.CLIENT.toString(), () -> Future.succeededFuture(nodeId), log);
		try {
			test.run(transport, tokens);
		} finally {
			await(transport.close());
		}
	}

	private static Future<DirectorTransport.Response> get(DirectorTransport transport, SelfIssuedTokens tokens) {
		return transport.call(HttpMethod.GET, "/profile", null, tokens);
	}

	private static void assertUnauthorized(Future<?> future) {
		ExecutionException e = assertThrows(ExecutionException.class, () -> await(future));
		assertInstanceOf(UnauthorizedException.class, e.getCause());
	}

	private static <T> T await(Future<T> future) throws Exception {
		return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}
}
