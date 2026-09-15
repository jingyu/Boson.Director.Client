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

import static io.bosonnetwork.director.client.DirectorTransport.decode;
import static io.bosonnetwork.director.client.DirectorTransport.decodeKey;
import static io.bosonnetwork.director.client.DirectorTransport.encode;
import static io.bosonnetwork.director.client.DirectorTransport.json;
import static io.bosonnetwork.director.client.DirectorTransport.jsonList;
import static io.bosonnetwork.director.client.DirectorTransport.optional;
import static io.bosonnetwork.director.client.DirectorTransport.paged;
import static io.bosonnetwork.director.client.DirectorTransport.putIfNotNull;
import static io.bosonnetwork.director.client.DirectorTransport.toCaller;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.cwt.SignedCwt;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.service.AccessScope;
import io.bosonnetwork.vertx.ContextualFuture;
import io.bosonnetwork.web.PaginatedResult;

/**
 * An asynchronous client for the admin API of a Boson Director, the account service of a Boson
 * super node.
 * <p>
 * It covers everything the admin API offers: users and their devices, subscriptions and payments,
 * the plan catalog and each plan's per-service features, the node blacklist, and federation with
 * other super nodes. Like {@link DirectorClient}, it hides HTTP: callers deal in {@link Id}s, keys
 * and the model types of this package, and every call returns a {@link CompletableFuture}.
 *
 * <h2>Authentication</h2>
 * The client acts as one administrator, identified by its user key ({@link Builder#userKey}). The
 * Director admits the key of the node's root user, and of any registered user marked as an
 * administrator; any other key is refused with
 * {@link io.bosonnetwork.director.client.exceptions.UnauthorizedException}.
 * <p>
 * There is no sign-in. The client issues its own short-lived access token, signed with the user key
 * and bound to the node id ({@link Builder#nodeId}), which is why both are required: the token is
 * valid for this node's admin API only. It is renewed shortly before it expires.
 *
 * <h2>Lookups and lists</h2>
 * Looking up something that does not exist completes with an empty {@link Optional}; changing or
 * removing it fails with {@link NotFoundException}. A list call without paging arguments returns
 * everything; its paged overload returns one page, with the totals needed to fetch the rest. Where
 * the Director supports ordering, a list call takes {@link Sort} keys naming fields of the listed
 * objects, such as {@code createdAt}; each such call documents the fields it accepts.
 *
 * <h2>Transport security</h2>
 * Use an {@code https} Director URL for any Director that is not on the local machine. A certificate
 * from a public CA is validated as usual, and a self-signed certificate pinned to the node id is
 * accepted as well.
 *
 * <h2>Errors</h2>
 * A call that reaches the Director and is refused fails with a {@link DirectorException}, or one of
 * the more specific subclasses in {@link io.bosonnetwork.director.client.exceptions}, carrying the
 * HTTP status. A call that never gets an answer fails with a {@code DirectorException} whose status
 * is {@link DirectorException#NO_HTTP_STATUS}. The federation calls fail with
 * {@link io.bosonnetwork.director.client.exceptions.NotEnabledException} on a node that does not
 * federate. Invalid arguments, and calls on a closed client, throw at once
 * ({@link NullPointerException}, {@link IllegalArgumentException} or {@link IllegalStateException})
 * rather than failing the returned future.
 *
 * <h2>Threading</h2>
 * The client is thread-safe. A call made on a Vert.x context completes on that context, and so do
 * the continuations chained on the returned {@link CompletableFuture}. A call made from any other
 * thread completes on a Vert.x event loop; such a caller may block on the returned future, which
 * must never be done on an event loop. A Vert.x caller can turn a returned future back into a
 * {@link io.vertx.core.Future} with {@code Future.fromCompletionStage}. Cancellation is not
 * supported: {@code cancel()} returns {@code false} and never stops a call in flight. Call
 * {@link #close()} when done with the client.
 *
 * <p>Example:
 * <pre>{@code
 * DirectorAdmin admin = DirectorAdmin.builder()
 *         .vertx(vertx)
 *         .directorUrl("https://node.example.com:8443")
 *         .nodeId(nodeId)
 *         .userKey(adminKey)
 *         .build();
 *
 * admin.listUsers(1, 20, Sort.desc("createdAt"))
 *         .thenAccept(page -> page.items().forEach(user -> System.out.println(user.getId())));
 * }</pre>
 */
public class DirectorAdmin {
	// Every admin API lives under this path of the Director API.
	private static final String ADMIN_API = "/admin";

	// Lifetime of the self-issued access token. Kept short: it is a bearer credential for the whole
	// admin API, and issuing a new one costs a signature rather than a round trip.
	private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);

	// The access token is renewed this long before it expires, so that no request carries a token
	// that expires while the request is in flight.
	private static final long TOKEN_REFRESH_MARGIN = 60 * 1000;

	private final URL directorUrl;
	private final Id nodeId;
	private final CryptoIdentity identity;

	private final DirectorTransport transport;
	private final DirectorTransport.TokenSource tokens;

	private final Object tokenLock = new Object();
	// The cached access token, and when it expires in epoch milliseconds; both guarded by tokenLock.
	private @Nullable String token;
	private long tokenExpiresAt;

	private static final Logger log = LoggerFactory.getLogger(DirectorAdmin.class);

	private DirectorAdmin(Builder builder) {
		Vertx vertx = Objects.requireNonNull(builder.vertx, "Vert.x instance must be set");
		this.directorUrl = Objects.requireNonNull(builder.directorUrl, "directorUrl must be set");
		this.nodeId = Objects.requireNonNull(builder.nodeId, "nodeId must be set");
		this.identity = new CryptoIdentity(Objects.requireNonNull(builder.userKey, "userKey must be set"));

		this.transport = new DirectorTransport(vertx, directorUrl, ADMIN_API, nodeId, log);
		this.tokens = new DirectorTransport.TokenSource() {
			@Override
			public Future<String> token() {
				return Future.succeededFuture(accessToken());
			}

			@Override
			public boolean rejected(String value) {
				// The token is issued here, so a new one would be refused for the same reason this one
				// was: the key is not an administrator of this node. Drop it, and do not repeat the call.
				invalidateToken(value);
				return false;
			}
		};
	}

	/**
	 * Creates a new {@link Builder}.
	 *
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns the URL of the Director this client talks to.
	 *
	 * @return the Director URL
	 */
	public URL getDirectorUrl() {
		return directorUrl;
	}

	/**
	 * Returns the Boson id of the super node this client administers.
	 *
	 * @return the node id
	 */
	public Id getNodeId() {
		return nodeId;
	}

	/**
	 * Returns the id of the administrator this client acts as.
	 *
	 * @return the user id
	 */
	public Id getUserId() {
		return identity.getId();
	}

	/**
	 * Closes the client and releases its connections. Calls made after closing throw
	 * {@link IllegalStateException}.
	 *
	 * @return a future completing when the client is closed
	 */
	public CompletableFuture<Void> close() {
		transport.close();
		return ContextualFuture.succeededFuture();
	}

	/**
	 * Tells whether {@link #close()} has been called.
	 *
	 * @return {@code true} if the client is closed
	 */
	public boolean isClosed() {
		return transport.isClosed();
	}

	// ---- Node ----------------------------------------------------------------------------------

	/**
	 * Gets the status of the super node: what it is, what it runs and which services it offers.
	 *
	 * @return a future completing with the node status
	 */
	public CompletableFuture<NodeStatus> getNodeStatus() {
		return fetch(new Query("/node"), NodeStatus.class);
	}

	// ---- Users ---------------------------------------------------------------------------------

	/**
	 * Creates a user account. Unlike self-registration there is no proof-of-work: the administrator
	 * vouches for the account.
	 *
	 * @param user the account to create
	 * @return a future completing when the user is created; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the user exists
	 */
	public CompletableFuture<Void> addUser(NewUser user) {
		Objects.requireNonNull(user, "user");
		return execute(HttpMethod.POST, "/users", user.fields());
	}

	/**
	 * Lists all users.
	 *
	 * @return a future completing with every user
	 */
	public CompletableFuture<PaginatedResult<Profile>> listUsers() {
		return fetchPage(new Query("/users"), Profile.class);
	}

	/**
	 * Lists one page of users.
	 *
	 * @param page     the page number, from 1
	 * @param pageSize the number of users per page
	 * @param sort     the order, by {@code name}, {@code email}, {@code admin}, {@code planName},
	 *                 {@code createdAt} or {@code updatedAt}
	 * @return a future completing with the page
	 * @throws IllegalArgumentException if the page or page size is below 1
	 */
	public CompletableFuture<PaginatedResult<Profile>> listUsers(long page, long pageSize, Sort... sort) {
		return fetchPage(new Query("/users").page(page, pageSize).sort(sort), Profile.class);
	}

	/**
	 * Looks up a user.
	 *
	 * @param userId the user id
	 * @return a future completing with the user, or empty if there is no such user
	 */
	public CompletableFuture<Optional<Profile>> getUser(Id userId) {
		return find(new Query(userPath(userId)), Profile.class);
	}

	/**
	 * Updates a user account. Only the fields set on the update are changed.
	 *
	 * @param userId the user id
	 * @param update the fields to change
	 * @return a future completing when the user is updated
	 * @throws IllegalArgumentException if the update changes nothing
	 */
	public CompletableFuture<Void> updateUser(Id userId, UserUpdate update) {
		String path = userPath(userId);
		checkUpdate(update.isEmpty(), "user");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Removes a user account.
	 *
	 * @param userId the user id
	 * @return a future completing when the user is removed
	 */
	public CompletableFuture<Void> removeUser(Id userId) {
		return execute(HttpMethod.DELETE, userPath(userId), null);
	}

	// ---- Devices -------------------------------------------------------------------------------

	/**
	 * Registers a device to a user. No signature from the device is involved: the administrator
	 * vouches for it.
	 *
	 * @param userId     the user the device belongs to
	 * @param deviceId   the device id
	 * @param deviceName a name for the device, shown to the user
	 * @param appName    the name of the app the device runs
	 * @return a future completing when the device is registered; it fails with
	 *         {@link NotFoundException} if there is no such user, and with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the device is
	 *         already registered
	 */
	public CompletableFuture<Void> addDevice(Id userId, Id deviceId, String deviceName, String appName) {
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("userId", Objects.requireNonNull(userId, "userId"));
		body.put("deviceId", Objects.requireNonNull(deviceId, "deviceId"));
		body.put("deviceName", Objects.requireNonNull(deviceName, "deviceName"));
		body.put("appName", Objects.requireNonNull(appName, "appName"));
		return execute(HttpMethod.POST, "/devices", body);
	}

	/**
	 * Lists the devices registered to a user.
	 *
	 * @param userId the user id
	 * @return a future completing with the devices; it fails with {@link NotFoundException} if there
	 *         is no such user
	 */
	public CompletableFuture<List<Device>> listDevices(Id userId) {
		return fetchList(new Query(userPath(userId) + "/devices"), Device.class);
	}

	/**
	 * Looks up a device.
	 *
	 * @param deviceId the device id
	 * @return a future completing with the device, or empty if there is no such device
	 */
	public CompletableFuture<Optional<Device>> getDevice(Id deviceId) {
		return find(new Query(devicePath(deviceId)), Device.class);
	}

	/**
	 * Removes a device from its user's account.
	 *
	 * @param deviceId the device id
	 * @return a future completing when the device is removed
	 */
	public CompletableFuture<Void> removeDevice(Id deviceId) {
		return execute(HttpMethod.DELETE, devicePath(deviceId), null);
	}

	// ---- Subscriptions -------------------------------------------------------------------------

	/**
	 * Subscribes a user to a plan, named by id. A user can have one active subscription at a time,
	 * and the free plan needs none.
	 *
	 * @param userId    the user id
	 * @param planId    the plan id
	 * @param status    the state the subscription starts in
	 * @param startDate when the subscription starts, in epoch milliseconds, or {@code 0} for now
	 * @param endDate   when the subscription ends, in epoch milliseconds
	 * @return a future completing with the subscription; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the user already
	 *         has an active subscription
	 * @throws IllegalArgumentException if the plan id is not positive, or the dates are invalid
	 */
	public CompletableFuture<Subscription> addSubscription(Id userId, int planId, Subscription.Status status,
			long startDate, long endDate) {
		checkId(planId, "planId");
		return submitSubscription(userId, planId, status, startDate, endDate);
	}

	/**
	 * Subscribes a user to a plan, named by name. See
	 * {@link #addSubscription(Id, int, Subscription.Status, long, long)}.
	 *
	 * @param userId    the user id
	 * @param planName  the plan name
	 * @param status    the state the subscription starts in
	 * @param startDate when the subscription starts, in epoch milliseconds, or {@code 0} for now
	 * @param endDate   when the subscription ends, in epoch milliseconds
	 * @return a future completing with the subscription
	 * @throws IllegalArgumentException if the plan name is empty, or the dates are invalid
	 */
	public CompletableFuture<Subscription> addSubscription(Id userId, String planName, Subscription.Status status,
			long startDate, long endDate) {
		checkNotEmpty(planName, "planName");
		return submitSubscription(userId, planName, status, startDate, endDate);
	}

	private CompletableFuture<Subscription> submitSubscription(Id userId, Object plan, Subscription.Status status,
			long startDate, long endDate) {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(status, "status");
		if (startDate < 0)
			throw new IllegalArgumentException("Invalid startDate: " + startDate);
		if (endDate <= startDate)
			throw new IllegalArgumentException("endDate must be after startDate");

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("userId", userId);
		// Sent the way the Director takes it: a number names the plan by id, a string by name.
		body.put("plan", plan);
		body.put("status", status);
		if (startDate > 0)
			body.put("startDate", startDate);
		body.put("endDate", endDate);
		return submit(HttpMethod.POST, "/subscriptions", body, Subscription.class);
	}

	/**
	 * Lists all of a user's subscriptions, past and present.
	 *
	 * @param userId the user id
	 * @return a future completing with the subscriptions; it fails with {@link NotFoundException} if
	 *         there is no such user
	 */
	public CompletableFuture<PaginatedResult<Subscription>> listSubscriptions(Id userId) {
		return fetchPage(new Query(userSubscriptionsPath(userId)), Subscription.class);
	}

	/**
	 * Lists one page of a user's subscriptions.
	 *
	 * @param userId   the user id
	 * @param page     the page number, from 1
	 * @param pageSize the number of subscriptions per page
	 * @return a future completing with the page
	 * @throws IllegalArgumentException if the page or page size is below 1
	 */
	public CompletableFuture<PaginatedResult<Subscription>> listSubscriptions(Id userId, long page, long pageSize) {
		return fetchPage(new Query(userSubscriptionsPath(userId)).page(page, pageSize), Subscription.class);
	}

	/**
	 * Looks up a subscription.
	 *
	 * @param subscriptionId the subscription id
	 * @return a future completing with the subscription, or empty if there is no such subscription
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Optional<Subscription>> getSubscription(long subscriptionId) {
		return find(new Query(subscriptionPath(subscriptionId)), Subscription.class);
	}

	/**
	 * Looks up a user's active subscription.
	 *
	 * @param userId the user id
	 * @return a future completing with the subscription, or empty if the user has none
	 */
	public CompletableFuture<Optional<Subscription>> getActiveSubscription(Id userId) {
		return find(new Query(userSubscriptionsPath(userId) + "/active"), Subscription.class);
	}

	/**
	 * Updates a subscription. Only the fields set on the update are changed.
	 *
	 * @param subscriptionId the subscription id
	 * @param update         the fields to change
	 * @return a future completing when the subscription is updated
	 * @throws IllegalArgumentException if the id is not positive, or the update changes nothing
	 */
	public CompletableFuture<Void> updateSubscription(long subscriptionId, SubscriptionUpdate update) {
		String path = subscriptionPath(subscriptionId);
		checkUpdate(update.isEmpty(), "subscription");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Cancels a subscription.
	 *
	 * @param subscriptionId the subscription id
	 * @return a future completing when the subscription is cancelled; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ForbiddenException} if it has already
	 *         ended
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Void> cancelSubscription(long subscriptionId) {
		return execute(HttpMethod.DELETE, subscriptionPath(subscriptionId), null);
	}

	// ---- Payments ------------------------------------------------------------------------------

	/**
	 * Records a payment toward a user's subscription.
	 *
	 * @param payment the payment to record
	 * @return a future completing with the payment; it fails with {@link NotFoundException} if the
	 *         user or subscription does not exist
	 */
	public CompletableFuture<Payment> addPayment(NewPayment payment) {
		Objects.requireNonNull(payment, "payment");
		return submit(HttpMethod.POST, "/payments", payment.fields(), Payment.class);
	}

	/**
	 * Lists all of a user's payments.
	 *
	 * @param userId the user id
	 * @return a future completing with the payments; it fails with {@link NotFoundException} if there
	 *         is no such user
	 */
	public CompletableFuture<PaginatedResult<Payment>> listPayments(Id userId) {
		return fetchPage(new Query(userPaymentsPath(userId)), Payment.class);
	}

	/**
	 * Lists one page of a user's payments.
	 *
	 * @param userId   the user id
	 * @param page     the page number, from 1
	 * @param pageSize the number of payments per page
	 * @return a future completing with the page
	 * @throws IllegalArgumentException if the page or page size is below 1
	 */
	public CompletableFuture<PaginatedResult<Payment>> listPayments(Id userId, long page, long pageSize) {
		return fetchPage(new Query(userPaymentsPath(userId)).page(page, pageSize), Payment.class);
	}

	/**
	 * Looks up a payment.
	 *
	 * @param paymentId the payment id
	 * @return a future completing with the payment, or empty if there is no such payment
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Optional<Payment>> getPayment(long paymentId) {
		return find(new Query(paymentPath(paymentId)), Payment.class);
	}

	/**
	 * Updates a payment: its status, and the transaction that settles it.
	 *
	 * @param paymentId the payment id
	 * @param update    the new status and transaction details
	 * @return a future completing when the payment is updated; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.InvalidRequestException} if a pending or
	 *         confirmed payment lacks its transaction details
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Void> updatePayment(long paymentId, PaymentUpdate update) {
		String path = paymentPath(paymentId);
		Objects.requireNonNull(update, "update");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Cancels a payment that is still unpaid or pending.
	 *
	 * @param paymentId the payment id
	 * @return a future completing when the payment is cancelled; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ForbiddenException} if it has already
	 *         ended
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Void> cancelPayment(long paymentId) {
		return execute(HttpMethod.DELETE, paymentPath(paymentId), null);
	}

	// ---- Plans ---------------------------------------------------------------------------------

	/**
	 * Adds a plan to the node's catalog. Plans are billed monthly.
	 *
	 * @param plan the plan to add
	 * @return a future completing with the plan, including its id
	 */
	public CompletableFuture<Plan> addPlan(NewPlan plan) {
		Objects.requireNonNull(plan, "plan");
		return submit(HttpMethod.POST, "/plans", plan.fields(), Plan.class);
	}

	/**
	 * Lists every plan in the catalog, including the inactive ones.
	 *
	 * @return a future completing with the plans
	 */
	public CompletableFuture<List<Plan>> listPlans() {
		return fetchList(new Query("/plans"), Plan.class);
	}

	/**
	 * Looks up a plan by id.
	 *
	 * @param planId the plan id
	 * @return a future completing with the plan, or empty if there is no such plan
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Optional<Plan>> getPlan(int planId) {
		return find(new Query(planPath(planId)), Plan.class);
	}

	/**
	 * Looks up a plan by name. The Director reads a name made only of digits as a plan id.
	 *
	 * @param planName the plan name
	 * @return a future completing with the plan, or empty if there is no such plan
	 * @throws IllegalArgumentException if the name is empty
	 */
	public CompletableFuture<Optional<Plan>> getPlan(String planName) {
		return find(new Query(planPath(planName)), Plan.class);
	}

	/**
	 * Updates a plan, named by id. Only the fields set on the update are changed.
	 *
	 * @param planId the plan id
	 * @param update the fields to change
	 * @return a future completing when the plan is updated
	 * @throws IllegalArgumentException if the id is not positive, or the update changes nothing
	 */
	public CompletableFuture<Void> updatePlan(int planId, PlanUpdate update) {
		String path = planPath(planId);
		checkUpdate(update.isEmpty(), "plan");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Updates a plan, named by name. Only the fields set on the update are changed.
	 *
	 * @param planName the plan name
	 * @param update   the fields to change
	 * @return a future completing when the plan is updated
	 * @throws IllegalArgumentException if the name is empty, or the update changes nothing
	 */
	public CompletableFuture<Void> updatePlan(String planName, PlanUpdate update) {
		String path = planPath(planName);
		checkUpdate(update.isEmpty(), "plan");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Withdraws a plan, named by id, from new subscriptions. Existing subscriptions are unaffected.
	 *
	 * @param planId the plan id
	 * @return a future completing when the plan is inactive
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Void> deactivatePlan(int planId) {
		return execute(HttpMethod.DELETE, planPath(planId), null);
	}

	/**
	 * Withdraws a plan, named by name, from new subscriptions. Existing subscriptions are unaffected.
	 *
	 * @param planName the plan name
	 * @return a future completing when the plan is inactive
	 * @throws IllegalArgumentException if the name is empty
	 */
	public CompletableFuture<Void> deactivatePlan(String planName) {
		return execute(HttpMethod.DELETE, planPath(planName), null);
	}

	// ---- Features ------------------------------------------------------------------------------

	/**
	 * Sets what a plan grants on one service, the plan named by id. The feature document is opaque to
	 * the Director; the service reads it. A plan has one document per service.
	 *
	 * @param planId    the plan id
	 * @param serviceId the service id, such as {@code io.bosonnetwork.ionstore}
	 * @param feature   the feature document
	 * @return a future completing with the feature, including its id
	 * @throws IllegalArgumentException if the plan id is not positive, or the service id is empty
	 */
	public CompletableFuture<Feature> addFeature(int planId, String serviceId, Map<String, ?> feature) {
		checkId(planId, "planId");
		return submitFeature(planId, serviceId, feature);
	}

	/**
	 * Sets what a plan grants on one service, the plan named by name. See
	 * {@link #addFeature(int, String, Map)}.
	 *
	 * @param planName  the plan name
	 * @param serviceId the service id, such as {@code io.bosonnetwork.ionstore}
	 * @param feature   the feature document
	 * @return a future completing with the feature, including its id
	 * @throws IllegalArgumentException if the plan name or service id is empty
	 */
	public CompletableFuture<Feature> addFeature(String planName, String serviceId, Map<String, ?> feature) {
		checkNotEmpty(planName, "planName");
		return submitFeature(planName, serviceId, feature);
	}

	private CompletableFuture<Feature> submitFeature(Object plan, String serviceId, Map<String, ?> feature) {
		checkNotEmpty(serviceId, "serviceId");
		Objects.requireNonNull(feature, "feature");

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		// Sent the way the Director takes it: a number names the plan by id, a string by name.
		body.put("plan", plan);
		body.put("serviceId", serviceId);
		body.put("feature", new LinkedHashMap<String, @Nullable Object>(feature));
		return submit(HttpMethod.POST, "/features", body, Feature.class);
	}

	/**
	 * Lists every feature, of every plan.
	 *
	 * @return a future completing with the features
	 */
	public CompletableFuture<List<Feature>> listFeatures() {
		return fetchList(new Query("/features"), Feature.class);
	}

	/**
	 * Lists the features that match a filter.
	 *
	 * @param filter the plan and service to match
	 * @param sort   the order, by {@code id}, {@code planId}, {@code planName}, {@code serviceId},
	 *               {@code createdAt} or {@code updatedAt}; by service id when none is given
	 * @return a future completing with the features
	 */
	public CompletableFuture<List<Feature>> listFeatures(FeatureFilter filter, Sort... sort) {
		Objects.requireNonNull(filter, "filter");
		Query query = new Query("/features");
		String plan = filter.getPlan();
		if (plan != null)
			query.add("plan", plan);
		String serviceId = filter.getServiceId();
		if (serviceId != null)
			query.add("service", serviceId);
		return fetchList(query.sort(sort), Feature.class);
	}

	/**
	 * Looks up a feature.
	 *
	 * @param featureId the feature id
	 * @return a future completing with the feature, or empty if there is no such feature
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Optional<Feature>> getFeature(int featureId) {
		return find(new Query(featurePath(featureId)), Feature.class);
	}

	/**
	 * Updates a feature. Only the fields set on the update are changed.
	 *
	 * @param featureId the feature id
	 * @param update    the fields to change
	 * @return a future completing when the feature is updated
	 * @throws IllegalArgumentException if the id is not positive, or the update changes nothing
	 */
	public CompletableFuture<Void> updateFeature(int featureId, FeatureUpdate update) {
		String path = featurePath(featureId);
		checkUpdate(update.isEmpty(), "feature");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Removes a feature, so the plan falls back to the service's defaults.
	 *
	 * @param featureId the feature id
	 * @return a future completing when the feature is removed
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Void> removeFeature(int featureId) {
		return execute(HttpMethod.DELETE, featurePath(featureId), null);
	}

	// ---- Blacklist -----------------------------------------------------------------------------

	/**
	 * Blacklists a node by its id.
	 *
	 * @param nodeId the node id
	 * @param reason why, or {@code null}
	 * @return a future completing with the blacklist entry
	 */
	public CompletableFuture<BlacklistedNode> addBlacklistedNode(Id nodeId, @Nullable String reason) {
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("nodeId", Objects.requireNonNull(nodeId, "nodeId"));
		putIfNotNull(body, "reason", reason);
		return submit(HttpMethod.POST, "/blacklist", body, BlacklistedNode.class);
	}

	/**
	 * Blacklists a host, by name or address.
	 *
	 * @param host   the host name or address
	 * @param reason why, or {@code null}
	 * @return a future completing with the blacklist entry
	 * @throws IllegalArgumentException if the host is empty, or made only of digits (the Director
	 *         would read it back as an entry id)
	 */
	public CompletableFuture<BlacklistedNode> addBlacklistedHost(String host, @Nullable String reason) {
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("nodeHost", checkHost(host));
		putIfNotNull(body, "reason", reason);
		return submit(HttpMethod.POST, "/blacklist", body, BlacklistedNode.class);
	}

	/**
	 * Lists every blacklist entry.
	 *
	 * @return a future completing with the entries
	 */
	public CompletableFuture<PaginatedResult<BlacklistedNode>> listBlacklistedNodes() {
		return fetchPage(new Query("/blacklist"), BlacklistedNode.class);
	}

	/**
	 * Lists one page of blacklist entries.
	 *
	 * @param page     the page number, from 1
	 * @param pageSize the number of entries per page
	 * @return a future completing with the page
	 * @throws IllegalArgumentException if the page or page size is below 1
	 */
	public CompletableFuture<PaginatedResult<BlacklistedNode>> listBlacklistedNodes(long page, long pageSize) {
		return fetchPage(new Query("/blacklist").page(page, pageSize), BlacklistedNode.class);
	}

	/**
	 * Looks up a blacklist entry by its id.
	 *
	 * @param id the entry id
	 * @return a future completing with the entry, or empty if there is no such entry
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Optional<BlacklistedNode>> getBlacklistedNode(long id) {
		return find(new Query(blacklistPath(id)), BlacklistedNode.class);
	}

	/**
	 * Looks up the blacklist entry of a node.
	 *
	 * @param nodeId the node id
	 * @return a future completing with the entry, or empty if the node is not blacklisted
	 */
	public CompletableFuture<Optional<BlacklistedNode>> getBlacklistedNode(Id nodeId) {
		return find(new Query(blacklistPath(nodeId)), BlacklistedNode.class);
	}

	/**
	 * Looks up the blacklist entry of a host.
	 *
	 * @param host the host name or address
	 * @return a future completing with the entry, or empty if the host is not blacklisted
	 * @throws IllegalArgumentException if the host is empty, or made only of digits
	 */
	public CompletableFuture<Optional<BlacklistedNode>> getBlacklistedHost(String host) {
		return find(new Query(blacklistPath(host)), BlacklistedNode.class);
	}

	/**
	 * Updates a blacklist entry, named by its id. Only the fields set on the update are changed.
	 *
	 * @param id     the entry id
	 * @param update the fields to change
	 * @return a future completing when the entry is updated
	 * @throws IllegalArgumentException if the id is not positive, or the update changes nothing
	 */
	public CompletableFuture<Void> updateBlacklistedNode(long id, BlacklistUpdate update) {
		String path = blacklistPath(id);
		checkUpdate(update.isEmpty(), "blacklist");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Updates the blacklist entry of a node. Only the fields set on the update are changed.
	 *
	 * @param nodeId the node id
	 * @param update the fields to change
	 * @return a future completing when the entry is updated
	 * @throws IllegalArgumentException if the update changes nothing
	 */
	public CompletableFuture<Void> updateBlacklistedNode(Id nodeId, BlacklistUpdate update) {
		String path = blacklistPath(nodeId);
		checkUpdate(update.isEmpty(), "blacklist");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Updates the blacklist entry of a host. Only the fields set on the update are changed.
	 *
	 * @param host   the host name or address
	 * @param update the fields to change
	 * @return a future completing when the entry is updated
	 * @throws IllegalArgumentException if the host is empty or made only of digits, or the update
	 *         changes nothing
	 */
	public CompletableFuture<Void> updateBlacklistedHost(String host, BlacklistUpdate update) {
		String path = blacklistPath(host);
		checkUpdate(update.isEmpty(), "blacklist");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Removes a blacklist entry, named by its id.
	 *
	 * @param id the entry id
	 * @return a future completing when the entry is removed
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Void> removeBlacklistedNode(long id) {
		return execute(HttpMethod.DELETE, blacklistPath(id), null);
	}

	/**
	 * Removes the blacklist entry of a node.
	 *
	 * @param nodeId the node id
	 * @return a future completing when the entry is removed
	 */
	public CompletableFuture<Void> removeBlacklistedNode(Id nodeId) {
		return execute(HttpMethod.DELETE, blacklistPath(nodeId), null);
	}

	/**
	 * Removes the blacklist entry of a host.
	 *
	 * @param host the host name or address
	 * @return a future completing when the entry is removed
	 * @throws IllegalArgumentException if the host is empty, or made only of digits
	 */
	public CompletableFuture<Void> removeBlacklistedHost(String host) {
		return execute(HttpMethod.DELETE, blacklistPath(host), null);
	}

	// ---- Federation ----------------------------------------------------------------------------

	/**
	 * Lists every node this node has federated with.
	 *
	 * @return a future completing with the nodes
	 */
	public CompletableFuture<PaginatedResult<FederatedNode>> listFederatedNodes() {
		return fetchPage(new Query("/federation/nodes"), FederatedNode.class);
	}

	/**
	 * Lists one page of federated nodes.
	 *
	 * @param page     the page number, from 1
	 * @param pageSize the number of nodes per page
	 * @return a future completing with the page
	 * @throws IllegalArgumentException if the page or page size is below 1
	 */
	public CompletableFuture<PaginatedResult<FederatedNode>> listFederatedNodes(long page, long pageSize) {
		return fetchPage(new Query("/federation/nodes").page(page, pageSize), FederatedNode.class);
	}

	/**
	 * Looks up a federated node.
	 *
	 * @param nodeId the node id
	 * @return a future completing with the node, or empty if this node has not federated with it
	 */
	public CompletableFuture<Optional<FederatedNode>> getFederatedNode(Id nodeId) {
		return find(new Query(federatedNodePath(nodeId)), FederatedNode.class);
	}

	/**
	 * Updates what this node records about a federated node. Only the fields set on the update are
	 * changed.
	 *
	 * @param nodeId the node id
	 * @param update the fields to change
	 * @return a future completing when the node is updated
	 * @throws IllegalArgumentException if the update changes nothing
	 */
	public CompletableFuture<Void> updateFederatedNode(Id nodeId, FederatedNodeUpdate update) {
		String path = federatedNodePath(nodeId);
		checkUpdate(update.isEmpty(), "federated node");
		return execute(HttpMethod.PUT, path, update.fields());
	}

	/**
	 * Removes a node from this node's federation.
	 *
	 * @param nodeId the node id
	 * @return a future completing when the node is removed
	 */
	public CompletableFuture<Void> removeFederatedNode(Id nodeId) {
		return execute(HttpMethod.DELETE, federatedNodePath(nodeId), null);
	}

	/**
	 * Lists the services a federated node shares with this node.
	 *
	 * @param nodeId the node id
	 * @return a future completing with the services, empty if the node shares none
	 */
	public CompletableFuture<List<FederatedService>> listFederatedServices(Id nodeId) {
		return fetchList(new Query("/federation/services/" + Objects.requireNonNull(nodeId, "nodeId").toBase58String()),
				FederatedService.class);
	}

	/**
	 * Proposes federation to another super node. This node looks the other one up on the DHT, checks
	 * it, and sends it the proposal; the call completes once the other node has answered. Every
	 * proposal is recorded, whatever the outcome, and can be inspected with
	 * {@link #listFederationProposals()}.
	 *
	 * @param nodeId the id of the node to federate with
	 * @return a future completing with the node as it now stands in the federation, or empty if the
	 *         proposal did not make it a federated node; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ForbiddenException} if the node is
	 *         blacklisted, with {@link io.bosonnetwork.director.client.exceptions.ConflictException} if
	 *         this node is already federated with it, with
	 *         {@link io.bosonnetwork.director.client.exceptions.RateLimitException} while the last
	 *         proposal to it is in its cooldown period, and with a {@link DirectorException} of status
	 *         422 if it cannot be found or validated as a super node
	 */
	public CompletableFuture<Optional<FederatedNode>> proposeFederation(Id nodeId) {
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("nodeId", Objects.requireNonNull(nodeId, "nodeId"));
		transport.checkOpen();

		// The Director answers with the federated node, or with a JSON null when there is none.
		return toCaller(call(HttpMethod.POST, "/federation/proposals", body).compose(res -> decode(res, b -> {
			if (b.toString(StandardCharsets.UTF_8).trim().equals("null"))
				return Optional.<FederatedNode>empty();

			return Optional.of(Json.objectMapper().readValue(b.getBytes(), FederatedNode.class));
		})));
	}

	/**
	 * Lists every federation proposal, made or received.
	 *
	 * @return a future completing with the proposals
	 */
	public CompletableFuture<PaginatedResult<FederationProposal>> listFederationProposals() {
		return fetchPage(new Query("/federation/proposals"), FederationProposal.class);
	}

	/**
	 * Lists the federation proposals that match a filter.
	 *
	 * @param filter the node, role and statuses to match
	 * @return a future completing with the proposals
	 */
	public CompletableFuture<PaginatedResult<FederationProposal>> listFederationProposals(ProposalFilter filter) {
		return fetchPage(proposalQuery(filter), FederationProposal.class);
	}

	/**
	 * Lists one page of the federation proposals that match a filter.
	 *
	 * @param filter   the node, role and statuses to match
	 * @param page     the page number, from 1
	 * @param pageSize the number of proposals per page
	 * @param sort     the order, by {@code id}, {@code nodeId}, {@code name}, {@code role}, {@code status},
	 *                 {@code proposedAt}, {@code confirmedAt}, {@code createdAt} or {@code updatedAt}
	 * @return a future completing with the page
	 * @throws IllegalArgumentException if the page or page size is below 1
	 */
	public CompletableFuture<PaginatedResult<FederationProposal>> listFederationProposals(ProposalFilter filter,
			long page, long pageSize, Sort... sort) {
		return fetchPage(proposalQuery(filter).page(page, pageSize).sort(sort), FederationProposal.class);
	}

	/**
	 * Looks up a federation proposal.
	 *
	 * @param proposalId the proposal id
	 * @return a future completing with the proposal, or empty if there is no such proposal
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Optional<FederationProposal>> getFederationProposal(long proposalId) {
		return find(new Query(proposalPath(proposalId)), FederationProposal.class);
	}

	/**
	 * Removes a federation proposal from the record. A federation it established is unaffected.
	 *
	 * @param proposalId the proposal id
	 * @return a future completing when the proposal is removed
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public CompletableFuture<Void> removeFederationProposal(long proposalId) {
		return execute(HttpMethod.DELETE, proposalPath(proposalId), null);
	}

	private static Query proposalQuery(ProposalFilter filter) {
		Objects.requireNonNull(filter, "filter");
		Query query = new Query("/federation/proposals");
		Id nodeId = filter.getNodeId();
		if (nodeId != null)
			query.add("nodeId", nodeId.toBase58String());

		// One role flag filters by that role.
		FederationProposal.Role role = filter.getRole();
		if (role == FederationProposal.Role.OFFER)
			query.add("offer", "true");
		else if (role == FederationProposal.Role.ANSWER)
			query.add("answer", "true");

		for (FederationProposal.Status status : filter.getStatuses())
			query.add("status", status.toString());

		return query;
	}

	// ---- Authentication ------------------------------------------------------------------------

	// Returns a current access token: the cached one, or a new one issued with the user key.
	private String accessToken() {
		synchronized (tokenLock) {
			long now = System.currentTimeMillis();
			String current = token;
			if (current != null && now < tokenExpiresAt - TOKEN_REFRESH_MARGIN)
				return current;

			// Issued by the administrator for itself: the Director accepts a token whose issuer is its
			// subject, and grants the admin role from the user record, not from the scope claim.
			current = SignedCwt.builder(identity)
					.subject(identity.getId())
					.audience(nodeId)
					.expiration(TOKEN_LIFETIME)
					.notBeforeNow()
					.issuedAtNow()
					.scope(AccessScope.ADMIN.toString())
					.buildToString();

			token = current;
			tokenExpiresAt = now + TOKEN_LIFETIME.toMillis();
			return current;
		}
	}

	private void invalidateToken(String value) {
		synchronized (tokenLock) {
			if (value.equals(token))
				token = null;
		}
	}

	// ---- Requests ------------------------------------------------------------------------------

	// Sends an authenticated request to the admin API. Every call goes through here, so adding one to
	// this client is a method that names its path and decodes its answer.
	private Future<HttpResponse<Buffer>> call(HttpMethod method, String path,
			@Nullable Map<String, @Nullable Object> body) {
		return transport.call(method, path, body, tokens);
	}

	// A request answered with no content.
	private CompletableFuture<Void> execute(HttpMethod method, String path,
			@Nullable Map<String, @Nullable Object> body) {
		transport.checkOpen();
		return toCaller(call(method, path, body).<Void>mapEmpty());
	}

	// A request answered with one object.
	private <T> CompletableFuture<T> submit(HttpMethod method, String path,
			@Nullable Map<String, @Nullable Object> body, Class<T> type) {
		transport.checkOpen();
		return toCaller(call(method, path, body).compose(res -> decode(res, json(type))));
	}

	private <T> CompletableFuture<T> fetch(Query query, Class<T> type) {
		return submit(HttpMethod.GET, query.toString(), null, type);
	}

	private <T> CompletableFuture<Optional<T>> find(Query query, Class<T> type) {
		transport.checkOpen();
		return toCaller(optional(call(HttpMethod.GET, query.toString(), null)
				.compose(res -> decode(res, json(type)))));
	}

	private <T> CompletableFuture<List<T>> fetchList(Query query, Class<T> type) {
		transport.checkOpen();
		return toCaller(call(HttpMethod.GET, query.toString(), null)
				.compose(res -> decode(res, jsonList(type))));
	}

	private <T> CompletableFuture<PaginatedResult<T>> fetchPage(Query query, Class<T> type) {
		transport.checkOpen();
		return toCaller(call(HttpMethod.GET, query.toString(), null)
				.compose(res -> decode(res, paged(type))));
	}

	// A request path with its query string.
	private static final class Query {
		private final StringBuilder uri;
		private boolean hasParams;

		private Query(String path) {
			this.uri = new StringBuilder(path);
		}

		private Query add(String name, String value) {
			uri.append(hasParams ? '&' : '?').append(name).append('=').append(encode(value));
			hasParams = true;
			return this;
		}

		private Query page(long page, long pageSize) {
			if (page < 1)
				throw new IllegalArgumentException("Invalid page: " + page);
			if (pageSize < 1)
				throw new IllegalArgumentException("Invalid pageSize: " + pageSize);

			return add("page", Long.toString(page)).add("pageSize", Long.toString(pageSize));
		}

		private Query sort(Sort... sort) {
			for (Sort key : sort)
				add("orderBy", Objects.requireNonNull(key, "sort").toParam());
			return this;
		}

		@Override
		public String toString() {
			return uri.toString();
		}
	}

	// ---- Helpers -------------------------------------------------------------------------------

	private static String userPath(Id userId) {
		return "/users/" + Objects.requireNonNull(userId, "userId").toBase58String();
	}

	private static String devicePath(Id deviceId) {
		return "/devices/" + Objects.requireNonNull(deviceId, "deviceId").toBase58String();
	}

	// The Director tells a user id from a subscription or payment id by its length, which is why one
	// route serves both.
	private static String userSubscriptionsPath(Id userId) {
		return "/subscriptions/" + Objects.requireNonNull(userId, "userId").toBase58String();
	}

	private static String subscriptionPath(long subscriptionId) {
		return "/subscriptions/" + checkId(subscriptionId, "subscriptionId");
	}

	private static String userPaymentsPath(Id userId) {
		return "/payments/" + Objects.requireNonNull(userId, "userId").toBase58String();
	}

	private static String paymentPath(long paymentId) {
		return "/payments/" + checkId(paymentId, "paymentId");
	}

	private static String planPath(int planId) {
		return "/plans/" + checkId(planId, "planId");
	}

	private static String planPath(String planName) {
		return "/plans/" + encode(checkNotEmpty(planName, "planName"));
	}

	private static String featurePath(int featureId) {
		return "/features/" + checkId(featureId, "featureId");
	}

	private static String blacklistPath(long id) {
		return "/blacklist/" + checkId(id, "id");
	}

	private static String blacklistPath(Id nodeId) {
		return "/blacklist/" + Objects.requireNonNull(nodeId, "nodeId").toBase58String();
	}

	private static String blacklistPath(String host) {
		return "/blacklist/" + encode(checkHost(host));
	}

	private static String federatedNodePath(Id nodeId) {
		return "/federation/nodes/" + Objects.requireNonNull(nodeId, "nodeId").toBase58String();
	}

	private static String proposalPath(long proposalId) {
		return "/federation/proposals/" + checkId(proposalId, "proposalId");
	}

	private static long checkId(long id, String name) {
		if (id <= 0)
			throw new IllegalArgumentException("Invalid " + name + ": " + id);
		return id;
	}

	private static String checkNotEmpty(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isEmpty())
			throw new IllegalArgumentException(name + " is empty");
		return value;
	}

	// The Director reads a blacklist key made only of digits as an entry id, so a host named that way
	// could never be looked up, changed or removed by name.
	private static String checkHost(String host) {
		checkNotEmpty(host, "host");
		if (host.chars().allMatch(Character::isDigit))
			throw new IllegalArgumentException("A host made only of digits would be read as an entry id: " + host);
		return host;
	}

	private static void checkUpdate(boolean empty, String what) {
		if (empty)
			throw new IllegalArgumentException("The " + what + " update changes nothing");
	}

	/**
	 * Fluent builder for {@link DirectorAdmin}.
	 * <p>
	 * The Director URL, the node id and the administrator's user key are all required. Not
	 * thread-safe.
	 */
	@NullUnmarked
	public static class Builder {
		private Vertx vertx;
		private URL directorUrl;
		private Id nodeId;
		private Signature.KeyPair userKey;

		private Builder() {
			// Adopt the Vert.x instance of the calling context, if there is one.
			this.vertx = Vertx.currentContext() != null ? Vertx.currentContext().owner() : null;
		}

		/**
		 * Sets the Vert.x instance the client runs on. Required unless the builder was created on a
		 * Vert.x context, whose instance is then used.
		 *
		 * @param vertx the Vert.x instance
		 * @return this builder
		 */
		public Builder vertx(Vertx vertx) {
			this.vertx = Objects.requireNonNull(vertx, "vertx");
			return this;
		}

		/**
		 * Sets the URL of the Director (required): scheme, host, port and any path prefix the Director
		 * is published under, without the {@code /api/v1} part.
		 *
		 * @param url an {@code http} or {@code https} URL
		 * @return this builder
		 * @throws IllegalArgumentException if the URL is not http(s)
		 */
		public Builder directorUrl(URL url) {
			Objects.requireNonNull(url, "url");
			if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https"))
				throw new IllegalArgumentException("Invalid Director URL protocol (must be http or https): " + url.getProtocol());
			this.directorUrl = url;
			return this;
		}

		/**
		 * Sets the URL of the Director (required) from a string.
		 *
		 * @param url an {@code http} or {@code https} URL
		 * @return this builder
		 * @throws IllegalArgumentException if the URL is malformed or not http(s)
		 * @see #directorUrl(URL)
		 */
		public Builder directorUrl(String url) {
			Objects.requireNonNull(url, "url");
			try {
				return directorUrl(new URL(url));
			} catch (MalformedURLException e) {
				throw new IllegalArgumentException("Invalid Director URL: " + url, e);
			}
		}

		/**
		 * Sets the Boson id of the super node the Director runs on (required). The access tokens are
		 * bound to it, and over HTTPS a self-signed Director certificate pinned to it is accepted.
		 *
		 * @param nodeId the super node id
		 * @return this builder
		 */
		public Builder nodeId(Id nodeId) {
			this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
			return this;
		}

		/**
		 * Sets the administrator's key pair (required).
		 *
		 * @param key the key pair of the node's root user, or of a user marked as an administrator
		 * @return this builder
		 */
		public Builder userKey(Signature.KeyPair key) {
			this.userKey = Objects.requireNonNull(key, "key");
			return this;
		}

		/**
		 * Sets the administrator's key pair from an encoded private key.
		 *
		 * @param privateKey the private key, as a {@code 0x}-prefixed hex string or a Base58 string
		 * @return this builder
		 * @throws IllegalArgumentException if the key is invalid
		 */
		public Builder userKey(String privateKey) {
			Objects.requireNonNull(privateKey, "privateKey");
			return userKey(decodeKey(privateKey));
		}

		/**
		 * Sets the administrator's key pair from a raw private key.
		 *
		 * @param privateKey the private key bytes ({@link Signature.PrivateKey#BYTES} long)
		 * @return this builder
		 * @throws IllegalArgumentException if the key length is invalid
		 */
		public Builder userKey(byte[] privateKey) {
			Objects.requireNonNull(privateKey, "privateKey");
			return userKey(decodeKey(privateKey));
		}

		/**
		 * Validates the configuration and builds the client.
		 *
		 * @return the client, ready to use
		 * @throws IllegalStateException if Vert.x, the Director URL, the node id or the user key is
		 *         missing
		 */
		public DirectorAdmin build() {
			try {
				return new DirectorAdmin(this);
			} catch (NullPointerException | IllegalArgumentException e) {
				throw new IllegalStateException("Invalid DirectorAdmin configuration: " + e.getMessage(), e);
			}
		}
	}
}
