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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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
 * Tests of {@link DirectorAdmin} that need no Director: the checks a call makes before it sends
 * anything, and a Director that cannot be reached. The calls themselves are tested end to end
 * against the Director.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DirectorAdminTests {
	// Nothing listens here, so every request fails to connect.
	private static final String UNREACHABLE_URL = "http://127.0.0.1:1";

	private final Signature.KeyPair adminKey = Signature.KeyPair.random();
	private final Id nodeId = Id.random();

	private Vertx vertx;

	@BeforeAll
	void setup() {
		vertx = Vertx.vertx();
	}

	@AfterAll
	void tearDown() throws Exception {
		vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	private DirectorAdmin.Builder builder() {
		return DirectorAdmin.builder().vertx(vertx).directorUrl(UNREACHABLE_URL).nodeId(nodeId).userKey(adminKey);
	}

	@Test
	void unreachableDirectorFailsWithoutStatus() throws Exception {
		DirectorAdmin admin = builder().build();
		try {
			ExecutionException e = assertThrows(ExecutionException.class,
					() -> admin.getNodeStatus().get(30, TimeUnit.SECONDS));
			DirectorException error = assertInstanceOf(DirectorException.class, e.getCause());
			assertEquals(DirectorException.NO_HTTP_STATUS, error.getStatus());
		} finally {
			admin.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void reportsItsIdentity() throws Exception {
		DirectorAdmin admin = builder().build();
		try {
			assertEquals(Id.of(adminKey.publicKey().bytes()), admin.getUserId());
			assertEquals(nodeId, admin.getNodeId());
			assertEquals(UNREACHABLE_URL, admin.getDirectorUrl().toString());
		} finally {
			admin.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void closedClientRefusesCalls() throws Exception {
		DirectorAdmin admin = builder().build();
		admin.close().get(10, TimeUnit.SECONDS);
		assertTrue(admin.isClosed());
		assertThrows(IllegalStateException.class, admin::getNodeStatus);
		assertThrows(IllegalStateException.class, admin::listUsers);
	}

	@Test
	void incompleteConfigurationIsRejected() {
		assertThrows(IllegalStateException.class,
				() -> DirectorAdmin.builder().vertx(vertx).directorUrl(UNREACHABLE_URL).userKey(adminKey).build());
		assertThrows(IllegalStateException.class,
				() -> DirectorAdmin.builder().vertx(vertx).directorUrl(UNREACHABLE_URL).nodeId(nodeId).build());
		assertThrows(IllegalStateException.class,
				() -> DirectorAdmin.builder().vertx(vertx).nodeId(nodeId).userKey(adminKey).build());
		assertThrows(IllegalArgumentException.class, () -> builder().directorUrl("ftp://node.example.com"));
		assertThrows(IllegalArgumentException.class, () -> builder().userKey(new byte[16]));
	}

	@Test
	void invalidArgumentsThrow() throws Exception {
		DirectorAdmin admin = builder().build();
		try {
			Id someone = Id.random();
			assertThrows(IllegalArgumentException.class, () -> admin.listUsers(0, 10));
			assertThrows(IllegalArgumentException.class, () -> admin.listUsers(1, 0));
			assertThrows(IllegalArgumentException.class, () -> admin.updateUser(someone, new UserUpdate()));
			assertThrows(IllegalArgumentException.class, () -> admin.getSubscription(0));
			assertThrows(IllegalArgumentException.class, () -> admin.cancelPayment(-1));
			assertThrows(IllegalArgumentException.class,
					() -> admin.addSubscription(someone, "Pro", Subscription.Status.ACTIVE, 10, 10));
			assertThrows(IllegalArgumentException.class,
					() -> admin.addSubscription(someone, 0, Subscription.Status.ACTIVE, 0, 10));
			assertThrows(IllegalArgumentException.class, () -> admin.getPlan(""));
			assertThrows(IllegalArgumentException.class, () -> admin.getPlan(0));
			assertThrows(IllegalArgumentException.class, () -> admin.updatePlan(1, new PlanUpdate()));
			assertThrows(IllegalArgumentException.class, () -> admin.removeFeature(0));
			assertThrows(IllegalArgumentException.class, () -> admin.addFeature("Pro", "", java.util.Map.of()));
			// A host made only of digits would be read back as a blacklist entry id.
			assertThrows(IllegalArgumentException.class, () -> admin.getBlacklistedHost("12345"));
			assertThrows(IllegalArgumentException.class, () -> admin.addBlacklistedHost("", null));
			assertThrows(IllegalArgumentException.class,
					() -> admin.updateFederatedNode(someone, new FederatedNodeUpdate()));
			assertThrows(NullPointerException.class, () -> admin.removeUser(null));
		} finally {
			admin.close().get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void requestObjectsValidateTheirValues() {
		Id someone = Id.random();
		assertThrows(IllegalArgumentException.class, () -> new NewUser(someone, ""));
		assertThrows(IllegalArgumentException.class, () -> new UserUpdate().passphrase(""));
		assertThrows(IllegalArgumentException.class, () -> new NewPlan("Plan", new BigDecimal("-1"), "USD"));
		assertThrows(IllegalArgumentException.class, () -> new NewPlan("", BigDecimal.ONE, "USD"));
		assertThrows(IllegalArgumentException.class, () -> new PlanUpdate().price(new BigDecimal("-0.01")));
		assertThrows(IllegalArgumentException.class, () -> NewPayment.renewal(someone, 1, 0, BigDecimal.ONE, "USD"));
		assertThrows(IllegalArgumentException.class, () -> NewPayment.subscription(someone, 0, 1, BigDecimal.ONE, "USD"));
		assertThrows(IllegalArgumentException.class, () -> NewPayment.upgrade(someone, 1, 1, 0, BigDecimal.ONE, "USD"));
		assertThrows(IllegalArgumentException.class, () -> NewPayment.renewal(someone, 1, 1, BigDecimal.ZERO, "USD"));
		assertThrows(IllegalArgumentException.class,
				() -> new PaymentUpdate(Payment.Status.PENDING).tokenAmount(BigDecimal.ZERO));
		assertThrows(IllegalArgumentException.class, () -> new SubscriptionUpdate().endDate(0));
		assertThrows(IllegalArgumentException.class, () -> new SubscriptionUpdate().planId(0));
		assertThrows(IllegalArgumentException.class, () -> new FeatureUpdate().serviceId(""));
		assertThrows(IllegalArgumentException.class, () -> new FeatureFilter().plan(0));

		assertTrue(new UserUpdate().isEmpty());
		assertFalse(new UserUpdate().bio(null).isEmpty());
		assertFalse(new BlacklistUpdate().reason(null).isEmpty());
	}

	@Test
	void sortKeysNameColumns() {
		assertEquals("name.asc", Sort.asc("name").toParam());
		assertEquals("created_at.desc", Sort.desc("created_at").toParam());
		assertEquals(Sort.asc("name"), Sort.asc("name"));
		assertThrows(IllegalArgumentException.class, () -> Sort.asc("name; DROP TABLE users"));
		assertThrows(IllegalArgumentException.class, () -> Sort.asc("1name"));
		// The dot separates the field from the direction, so a field never contains one.
		assertThrows(IllegalArgumentException.class, () -> Sort.asc("u.name"));
		assertThrows(IllegalArgumentException.class, () -> Sort.desc(""));
	}
}
