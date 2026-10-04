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

import static io.bosonnetwork.director.client.DirectorTransport.decodeKey;
import static io.bosonnetwork.director.client.DirectorTransport.putIfNotNull;
import static io.bosonnetwork.director.client.DirectorTransport.requiredString;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.crypto.pow.RegistrationPowClient;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.director.client.exceptions.ProofOfWorkException;
import io.bosonnetwork.director.client.exceptions.RegistrationDisabledException;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.service.AccessScope;
import io.bosonnetwork.web.PaginatedResult;
import io.bosonnetwork.web.client.AccessTokenSource;
import io.bosonnetwork.web.client.SelfIssuedAccessTokens;

/**
 * An asynchronous client for the client API of a Boson Director, the account service of a Boson
 * super node.
 * <p>
 * It covers what an app needs to manage its account on a super node: proof-of-work registration,
 * devices and approving new ones, the account passphrase, the profile and avatar, other users' public
 * profiles and avatars, the node's identity and status, and the user's plan. What comes before the app
 * holds a key to act with - OAuth sign-in and joining an account from a new device - is
 * {@link DirectorGuest}, and an OAuth sign-in in progress is a {@link DirectorOAuth}. HTTP is an implementation detail: callers deal in {@link Id}s, keys and the model
 * types of this package, and every call returns a {@link CompletableFuture}.
 *
 * <h2>Identity and authentication</h2>
 * A client acts as one user, identified in one of two ways:
 * <ul>
 *   <li><b>user key</b> ({@link Builder#userKey(Signature.KeyPair)}) - the client holds the user's
 *       key pair. This is required to register the user, and it is what the client authenticates
 *       with whenever it has it.</li>
 *   <li><b>device</b> ({@link Builder#userId(Id)} with {@link Builder#deviceKey(Signature.KeyPair)})
 *       - the client holds only the key of a device already registered to the user, and
 *       authenticates as that device.</li>
 * </ul>
 * A device key may be configured alongside the user key. It is then the device registered by
 * {@link #registerUser(UserRegistration)} (as the initial device) and by
 * {@link #registerDevice(String, String)}. A client configured with neither identity is rejected
 * when it is built.
 * <p>
 * There is no sign-in. The client issues its own short-lived access tokens, signed with the key it
 * authenticates with and bound to the node id, and renews them shortly before they expire. The node
 * id is the configured one ({@link Builder#nodeId(Id)}), or else the one the Director reports,
 * looked up by the first call that needs it. If the Director rejects a token and its clock is far
 * from the local one, the client dates its tokens by the Director's clock from then on and repeats
 * that call once: the Director rejects a token before acting on the request, so the repeat is safe.
 * No other failure is retried.
 *
 * <h2>Passphrase</h2>
 * An account may carry a passphrase as a second factor. Once one is set, the Director requires it
 * for the gated operations - registering or removing a device and updating the profile - which is
 * why those methods take an optional passphrase. Pass {@code null} when the account has none. If
 * it is required but missing, the call fails with
 * {@link io.bosonnetwork.director.client.exceptions.PassphraseRequiredException}; if it is wrong,
 * with {@link io.bosonnetwork.director.client.exceptions.ForbiddenException}.
 *
 * <h2>Transport security</h2>
 * Use an {@code https} Director URL for any Director that is not on the local machine. A
 * certificate from a public CA is validated as usual. If the node id is configured
 * ({@link Builder#nodeId(Id)}), a self-signed certificate pinned to that id is accepted as well.
 *
 * <h2>Errors</h2>
 * A call that reaches the Director and is refused fails with a {@link DirectorException}, or one of
 * the more specific subclasses in {@link io.bosonnetwork.director.client.exceptions}, carrying the
 * HTTP status. A call that never gets an answer fails with a {@code DirectorException} whose status
 * is {@link DirectorException#NO_HTTP_STATUS}. Invalid arguments, and calls the client cannot make
 * in its current state (no key to register with, already closed), throw at once
 * ({@link NullPointerException}, {@link IllegalArgumentException} or {@link IllegalStateException})
 * rather than failing the returned future.
 *
 * <h2>Threading</h2>
 * The client is thread-safe. A call made on a Vert.x context completes on that context, and so do
 * the continuations chained on the returned {@link CompletableFuture}. A call made from any other
 * thread completes on a Vert.x event loop, unless the builder was given a
 * {@linkplain DirectorBuilder#callbackExecutor(java.util.concurrent.Executor) callback executor}, which
 * such an app should do if its continuations may block. A caller may block on the returned future, which
 * must never be done on an event loop. A Vert.x caller can turn a returned future back into a
 * {@link io.vertx.core.Future} with {@code Future.fromCompletionStage}. The futures follow the
 * CompletableFuture contract: {@code cancel()}, {@code complete()} and the timeouts complete the future,
 * though the call in flight is not stopped and its result is then ignored. Call
 * {@link #close()} when done with the client.
 *
 * <p>Example:
 * <pre>{@code
 * DirectorClient director = DirectorClient.builder()
 *         .vertx(vertx)
 *         .directorUrl("https://node.example.com:8443")
 *         .userKey(userKey)
 *         .deviceKey(deviceKey)
 *         .build();
 *
 * director.registerUser(new UserRegistration().name("Alice").initialDevice("Laptop", "MyApp"))
 *         .thenCompose(v -> director.getProfile())
 *         .thenAccept(profile -> System.out.println("Plan: " + profile.getPlanName()));
 * }</pre>
 */
public class DirectorClient {
	// Every client API lives under this path of the Director API.
	private static final String CLIENT_API = "/client";
	private static final String AUTH_API = "/auth";

	// Size of the random nonce signed to obtain an access token.
	private static final int AUTH_NONCE_SIZE = 32;

	// Nonces the proof-of-work solver may try before it gives up (see RegistrationPowClient.solve).
	private static final long MAX_POW_NONCES = 1_000_000L;

	private static final String CONTENT_TYPE_PNG = "image/png";
	private static final String CONTENT_TYPE_JPEG = "image/jpeg";

	private final Vertx vertx;

	private final URL directorUrl;
	// The configured node id, or the one the Director reported once looked up.
	private volatile @Nullable Id nodeId;

	// client credentials
	private final Signature.@Nullable KeyPair userKey;
	private final Id userId;
	private final Signature.@Nullable KeyPair deviceKey;
	private final @Nullable Id deviceId;

	private final DirectorTransport transport;
	// The auth API, for the user's linked OAuth sign-ins: made on first use, with the same tokens.
	private @Nullable DirectorTransport authTransport;
	private final @Nullable InetSocketAddress resolveToAddress;
	private final @Nullable Executor callbackExecutor;
	private final AccessTokenSource tokens;

	private static final Logger log = LoggerFactory.getLogger(DirectorClient.class);

	private DirectorClient(Builder builder) {
		this.vertx = Objects.requireNonNull(builder.vertx, "Vert.x instance must be set");
		this.directorUrl = Objects.requireNonNull(builder.directorUrl, "directorUrl must be set");
		this.nodeId = builder.nodeId;

		// A client acts as one user: holding the user key, or as one of the user's devices.
		if (builder.userKey == null && (builder.userId == null || builder.deviceKey == null))
			throw new IllegalArgumentException("A client acts as a user: set the user key, or the user id together with a device key");

		this.userKey = builder.userKey;
		this.userId = Objects.requireNonNull(builder.userId);
		this.deviceKey = builder.deviceKey;
		this.deviceId = deviceKey != null ? Id.of(deviceKey.publicKey().bytes()) : null;

		this.resolveToAddress = builder.resolveToAddress;
		this.callbackExecutor = builder.callbackExecutor;
		this.transport = new DirectorTransport(vertx, directorUrl, CLIENT_API, nodeId, resolveToAddress, callbackExecutor, log);

		// Tokens are signed with the user key when the client has it: that works before any device is
		// registered. A device signs its own, naming itself as the client.
		Signature.KeyPair signer = userKey != null ? userKey : Objects.requireNonNull(deviceKey);
		SelfIssuedAccessTokens.Builder tokens = SelfIssuedAccessTokens.builder(new CryptoIdentity(signer))
				.subject(userId)
				.scope(AccessScope.CLIENT)
				.audience(this::resolveNodeId)
				.logger(log);
		if (userKey == null)
			tokens.clientId(Objects.requireNonNull(deviceId));
		this.tokens = tokens.build();
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
	 * Returns the id of the user this client acts as.
	 *
	 * @return the user id
	 */
	public Id getUserId() {
		return userId;
	}

	/**
	 * Returns the id of the device key configured on this client.
	 *
	 * @return the device id, or {@code null} if no device key is configured
	 */
	public @Nullable Id getDeviceId() {
		return deviceId;
	}

	/**
	 * Closes the client and releases its connections. Calls made after closing throw
	 * {@link IllegalStateException}.
	 *
	 * @return a future completing when the client is closed
	 */
	public CompletableFuture<Void> close() {
		DirectorTransport auth;
		synchronized (this) {
			auth = authTransport;
		}
		return transport.deliver(auth == null ? transport.close() :
				Future.join(transport.close(), auth.close()).<Void>mapEmpty());
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
	 * Gets the Boson id of the super node the Director runs on. Sent without authentication.
	 *
	 * @return a future completing with the node id
	 */
	public CompletableFuture<Id> getNodeId() {
		checkOpen();
		return transport.deliver(fetchNodeId());
	}

	/**
	 * Gets the status of the super node: what it is, what it runs and which services it offers.
	 * Sent without authentication.
	 *
	 * @return a future completing with the node status
	 */
	public CompletableFuture<NodeStatus> getNodeStatus() {
		checkOpen();
		return transport.deliver(call(HttpMethod.GET, "/node", null, false)
				.compose(res -> res.json(NodeStatus.class)));
	}

	private Future<Id> fetchNodeId() {
		return call(HttpMethod.GET, "/id", null, false)
				.compose(res -> res.idField("id"));
	}

	// The configured node id, or the one the Director reports, looked up once it is needed. Concurrent
	// first calls each look it up rather than share one lookup, so that each completes on its own
	// caller's context.
	private Future<Id> resolveNodeId() {
		Id id = nodeId;
		if (id != null)
			return Future.succeededFuture(id);

		return fetchNodeId().map(fetched -> {
			this.nodeId = fetched;
			return fetched;
		});
	}

	// ---- Registration --------------------------------------------------------------------------

	/**
	 * Registers the user of this client's user key with the Director, proving its registration
	 * with proof-of-work.
	 * <p>
	 * The client fetches a challenge, solves it with the user key on a Vert.x worker thread (it is
	 * memory-hard by design: typically about a second, longer when the node raises the effort under
	 * load), and submits the solution with the registration. When the registration names an
	 * {@linkplain UserRegistration#initialDevice(String, String) initial device}, this client's
	 * device key is registered as that device in the same request.
	 * <p>
	 * This is the permissionless registration path; it works on nodes whose registration policy is
	 * {@code pow} or {@code either}. A node that only accepts OAuth registration fails the call with
	 * {@link RegistrationDisabledException}. A node on the legacy {@code open} policy does not accept
	 * proof-of-work and refuses the registration as an invalid request.
	 *
	 * @param registration the account details to register with
	 * @return a future completing when the user is registered; it fails with a
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the user (or the
	 *         initial device) is already registered, or with {@link ProofOfWorkException} if no solution
	 *         was found
	 * @throws IllegalStateException if the client has no user key, or the registration names an
	 *         initial device and the client has no device key
	 */
	public CompletableFuture<Void> registerUser(UserRegistration registration) {
		checkOpen();
		Objects.requireNonNull(registration, "registration");

		Signature.KeyPair uk = userKey;
		if (uk == null)
			throw new IllegalStateException("Registering a user needs the user key");

		Signature.KeyPair dk = null;
		if (registration.hasInitialDevice()) {
			dk = deviceKey;
			if (dk == null)
				throw new IllegalStateException("Registering an initial device needs the device key");
		}
		final Signature.KeyPair initialDeviceKey = dk;

		return transport.deliver(resolveNodeId().compose(nid -> fetchChallenge().compose(challenge ->
				solve(nid, uk, challenge).compose(solution ->
						submitRegistration(registration, nid, uk, initialDeviceKey, challenge, solution)))));
	}

	private Future<Challenge> fetchChallenge() {
		return call(HttpMethod.GET, "/users/challenge", null, false)
				.recover(e -> Future.failedFuture(e instanceof NotFoundException ?
						new RegistrationDisabledException(NotFoundException.STATUS,
								"This node does not accept proof-of-work registration; it registers users through OAuth only") :
						e))
				.compose(res -> res.decode(Challenge::parse));
	}

	private Future<RegistrationPowClient.Result> solve(Id nid, Signature.KeyPair uk, Challenge challenge) {
		// Memory-hard by design: never on an event loop. Unordered, so that concurrent registrations
		// do not queue behind each other.
		return vertx.executeBlocking(() -> RegistrationPowClient.solve(nid.bytesUnsafe(), uk,
				challenge.n, challenge.k, challenge.effort, challenge.nonce, MAX_POW_NONCES), false)
				// The solver gives up with an IllegalStateException, which callers would take for a
				// precondition of the client (closed, no key); type it for what it is.
				.recover(e -> Future.failedFuture(e instanceof IllegalStateException ?
						new ProofOfWorkException("No proof-of-work solution found; register again to solve a fresh challenge", e) : e));
	}

	private Future<Void> submitRegistration(UserRegistration registration, Id nid, Signature.KeyPair uk,
			Signature.@Nullable KeyPair dk, Challenge challenge, RegistrationPowClient.Result solution) {
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("userId", Id.of(uk.publicKey().bytes()));
		putIfNotNull(body, "userName", registration.getName());
		putIfNotNull(body, "email", registration.getEmail());
		putIfNotNull(body, "bio", registration.getBio());
		putIfNotNull(body, "passphrase", registration.getPassphrase());
		body.put("challenge", challenge.token);
		body.put("challengeSig", challenge.signature);
		body.put("powNonce", solution.powNonce());
		body.put("solution", solution.solution());
		body.put("userSig", solution.signature());

		String path = "/users";
		if (dk != null) {
			body.put("deviceId", Id.of(dk.publicKey().bytes()));
			body.put("deviceName", Objects.requireNonNull(registration.getDeviceName()));
			body.put("appName", Objects.requireNonNull(registration.getAppName()));
			// The device co-signs the same solution, over a message bound to its own key.
			body.put("deviceSig", RegistrationPowClient.sign(nid.bytesUnsafe(), dk, challenge.nonce,
					solution.powNonce(), challenge.effort));
			path = "/usersAndInitialDevice";
		}

		// The registration also answers with an access token for the new account, issued by the node;
		// this client issues its own.
		return call(HttpMethod.POST, path, body, false).mapEmpty();
	}

	/**
	 * Deactivates the user's account: the Director removes the user and the user's devices. It cannot be
	 * undone. The account must not be passphrase-protected; see {@link #deactivate(String)}.
	 *
	 * @return a future completing when the account is deactivated
	 */
	public CompletableFuture<Void> deactivate() {
		return deactivate(null);
	}

	/**
	 * Deactivates the user's account: the Director removes the user and the user's devices. It cannot be
	 * undone. The client is still open afterwards, but the Director no longer knows the user.
	 *
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @return a future completing when the account is deactivated
	 */
	public CompletableFuture<Void> deactivate(@Nullable String passphrase) {
		checkOpen();
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		putIfNotNull(body, "passphrase", passphrase);
		return execute(HttpMethod.POST, "/users/deactivate", body);
	}

	// ---- Devices -------------------------------------------------------------------------------

	/**
	 * Registers this client's device key as a device of the user. The account must not be
	 * passphrase-protected; see {@link #registerDevice(String, String, String)}.
	 *
	 * @param deviceName a name for the device, shown to the user
	 * @param appName the name of the app the device runs
	 * @return a future completing when the device is registered
	 * @throws IllegalStateException if the client has no device key
	 */
	public CompletableFuture<Void> registerDevice(String deviceName, String appName) {
		return registerDevice(deviceName, appName, null);
	}

	/**
	 * Registers this client's device key as a device of the user.
	 *
	 * @param deviceName a name for the device, shown to the user
	 * @param appName the name of the app the device runs
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @return a future completing when the device is registered
	 * @throws IllegalStateException if the client has no device key
	 */
	public CompletableFuture<Void> registerDevice(String deviceName, String appName, @Nullable String passphrase) {
		Signature.KeyPair dk = deviceKey;
		if (dk == null)
			throw new IllegalStateException("No device key configured; pass the key of the device to register");

		return registerDevice(dk, deviceName, appName, passphrase);
	}

	/**
	 * Registers a device of the user, given the device's key pair. The key signs the registration,
	 * proving the device holds it; it is not sent. The user key authorizes it, signing the device id with
	 * the same nonce: registering a device directly takes the user key, whatever the session.
	 * <p>
	 * A device key is registered with one user only: registering a key the Director already knows
	 * fails with {@link io.bosonnetwork.director.client.exceptions.ConflictException}.
	 *
	 * @param key the key pair of the device to register
	 * @param deviceName a name for the device, shown to the user
	 * @param appName the name of the app the device runs
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @return a future completing when the device is registered
	 * @throws IllegalStateException if the client has no user key to authorize the device with
	 */
	public CompletableFuture<Void> registerDevice(Signature.KeyPair key, String deviceName, String appName,
			@Nullable String passphrase) {
		checkOpen();
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(deviceName, "deviceName");
		Objects.requireNonNull(appName, "appName");
		Signature.KeyPair uk = userKey;
		if (uk == null)
			throw new IllegalStateException("Registering a device directly takes the user key, which this client does not have");

		Id deviceId = Id.of(key.publicKey().bytes());
		byte[] nonce = Random.randomBytes(AUTH_NONCE_SIZE);
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("deviceId", deviceId);
		body.put("deviceName", deviceName);
		body.put("appName", appName);
		body.put("nonce", nonce);
		body.put("deviceSig", key.privateKey().sign(nonce));
		body.put("userSig", uk.privateKey().sign(deviceAuthorization(deviceId, nonce)));
		putIfNotNull(body, "passphrase", passphrase);

		return execute(HttpMethod.POST, "/devices", body);
	}

	/**
	 * Lists the devices registered to the user.
	 *
	 * @return a future completing with the devices
	 */
	public CompletableFuture<List<Device>> listDevices() {
		checkOpen();
		return fetchList("/devices", Device.class);
	}

	/**
	 * Gets one of the user's devices.
	 *
	 * @param deviceId the id of the device
	 * @return a future completing with the device, or empty if the user has no such device
	 */
	public CompletableFuture<Optional<Device>> getDevice(Id deviceId) {
		checkOpen();
		Objects.requireNonNull(deviceId, "deviceId");
		return fetchOptional("/devices/" + deviceId.toBase58String(), Device.class);
	}

	/**
	 * Removes a device from the user's account. The account must not be passphrase-protected; see
	 * {@link #removeDevice(Id, String)}.
	 *
	 * @param deviceId the id of the device to remove
	 * @return a future completing when the device is removed; it fails with
	 *         {@link NotFoundException} if the user has no such device
	 */
	public CompletableFuture<Void> removeDevice(Id deviceId) {
		return removeDevice(deviceId, null);
	}

	/**
	 * Removes a device from the user's account.
	 *
	 * @param deviceId the id of the device to remove
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @return a future completing when the device is removed; it fails with
	 *         {@link NotFoundException} if the user has no such device
	 */
	public CompletableFuture<Void> removeDevice(Id deviceId, @Nullable String passphrase) {
		checkOpen();
		Objects.requireNonNull(deviceId, "deviceId");

		// Always send a body, if only an empty one: the Director parses one whenever it is present.
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		putIfNotNull(body, "passphrase", passphrase);

		return execute(HttpMethod.POST, "/devices/" + deviceId.toBase58String() + "/remove", body);
	}

	/**
	 * Reads a pending request from a new device to join the user's account, so that the user can see what
	 * they are about to approve. The new device made the request with
	 * {@link DirectorGuest#requestDeviceRegistration(Signature.KeyPair, String, String, boolean)} and shows its
	 * pairing code, typically as a QR code.
	 *
	 * @param code the pairing code the new device shows
	 * @return a future completing with the device asking to join, or empty if there is no such pending
	 *         request (it was answered, or it expired)
	 */
	public CompletableFuture<Optional<PendingDevice>> getDeviceRegistration(PairingCode code) {
		Objects.requireNonNull(code, "code");
		return getDeviceRegistration(code.getRegistrationId());
	}

	/**
	 * Reads a pending request from a new device by its registration id, as
	 * {@link #getDeviceRegistration(PairingCode)} does.
	 *
	 * @param registrationId the id of the registration request
	 * @return a future completing with the device asking to join, or empty if there is no such pending
	 *         request
	 */
	public CompletableFuture<Optional<PendingDevice>> getDeviceRegistration(String registrationId) {
		checkOpen();
		Objects.requireNonNull(registrationId, "registrationId");
		return fetchOptional(registrationPath(registrationId), PendingDevice.class);
	}

	/**
	 * Approves a pending request from a new device to join the user's account. The account must not be
	 * passphrase-protected; see {@link #approveDeviceRegistration(PairingCode, String)}.
	 *
	 * @param code the pairing code the new device shows
	 * @return a future completing when the request is approved
	 */
	public CompletableFuture<Void> approveDeviceRegistration(PairingCode code) {
		return approveDeviceRegistration(code, null);
	}

	/**
	 * Approves a pending request from a new device to join the user's account: the Director registers the
	 * device to the user. If the device asked for the user key, this client's user key goes with the
	 * approval, sealed to the pairing code so that only the new device can open it (the Director relays it
	 * without being able to read it); the new device receives it from
	 * {@link DirectorGuest#finishDeviceRegistration(DeviceRegistration)}. A device that did not ask never
	 * gets it.
	 *
	 * @param code the pairing code the new device shows
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @return a future completing when the request is approved; it fails with {@link NotFoundException} if
	 *         there is no such pending request, or {@link IllegalStateException} if the device asks for the
	 *         user key and this client has none
	 */
	public CompletableFuture<Void> approveDeviceRegistration(PairingCode code, @Nullable String passphrase) {
		return approveDeviceRegistration(code, passphrase, false);
	}

	/**
	 * Approves a pending request, as {@link #approveDeviceRegistration(PairingCode, String)} does, and with
	 * {@code admin} makes the new device an administrator's device: the user key signs the device id with
	 * the nonce the device signed, as in a direct add, so admin power stays tied to the user key. Only an
	 * administrator can do this.
	 *
	 * @param code the pairing code the new device shows
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @param admin whether the device acts as an administrator's device
	 * @return a future completing when the request is approved
	 * @throws IllegalStateException if {@code admin} and the client has no user key
	 */
	public CompletableFuture<Void> approveDeviceRegistration(PairingCode code, @Nullable String passphrase,
			boolean admin) {
		Objects.requireNonNull(code, "code");
		return approve(code.getRegistrationId(), code, passphrase, admin);
	}

	/**
	 * Approves a pending request by its registration id. That is enough only for a device that did not ask
	 * for the user key: one that did must be approved with the pairing code it shows, since the key is
	 * sealed to the code, never to a key the Director supplies.
	 *
	 * @param registrationId the id of the registration request
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @param admin whether the device acts as an administrator's device
	 * @return a future completing when the request is approved; it fails with {@link IllegalStateException}
	 *         if the device asks for the user key
	 * @throws IllegalStateException if {@code admin} and the client has no user key
	 */
	public CompletableFuture<Void> approveDeviceRegistration(String registrationId, @Nullable String passphrase,
			boolean admin) {
		Objects.requireNonNull(registrationId, "registrationId");
		return approve(registrationId, null, passphrase, admin);
	}

	private CompletableFuture<Void> approve(String registrationId, @Nullable PairingCode code,
			@Nullable String passphrase, boolean admin) {
		checkOpen();
		Signature.KeyPair uk = userKey;
		if (admin && uk == null)
			throw new IllegalStateException("Making a device an administrator's takes the user key, which this client does not have");

		// The request says whether the device wants the user key, and gives the nonce an administrator's
		// approval signs: read it first.
		Future<Void> approval = call(HttpMethod.GET, registrationPath(registrationId), null, true)
				.compose(res -> res.json(PendingDevice.class))
				.compose(device -> {
					Map<String, @Nullable Object> body = new LinkedHashMap<>();
					body.put("approved", true);
					if (device.wantsUserKey()) {
						if (code == null)
							return Future.failedFuture(new IllegalStateException("This device asks for the user key: approve it with the pairing code it shows"));
						if (uk == null)
							return Future.failedFuture(new IllegalStateException("This device asks for the user key, which this client does not have"));
						body.put("userPrivateKey", CryptoBox.encryptSealed(uk.privateKey().bytes(), code.publicKey()));
					}
					if (admin) {
						byte[] nonce = device.nonce();
						if (nonce == null)
							return Future.failedFuture(new IllegalStateException("The Director did not say which nonce the device signed"));
						body.put("admin", true);
						body.put("userSig", Objects.requireNonNull(uk).privateKey().sign(deviceAuthorization(device.getDeviceId(), nonce)));
					}
					putIfNotNull(body, "passphrase", passphrase);
					return call(HttpMethod.PATCH, registrationPath(registrationId), body, true).<Void>mapEmpty();
				});
		return transport.deliver(approval);
	}

	/**
	 * Denies a pending request from a new device to join the user's account. The new device learns of it
	 * from {@link DirectorGuest#finishDeviceRegistration(DeviceRegistration)}.
	 *
	 * @param code the pairing code the new device shows
	 * @return a future completing when the request is denied; it fails with {@link NotFoundException} if
	 *         there is no such pending request
	 */
	public CompletableFuture<Void> denyDeviceRegistration(PairingCode code) {
		Objects.requireNonNull(code, "code");
		return denyDeviceRegistration(code.getRegistrationId());
	}

	/**
	 * Denies a pending request by its registration id. Denying needs no passphrase.
	 *
	 * @param registrationId the id of the registration request
	 * @return a future completing when the request is denied
	 */
	public CompletableFuture<Void> denyDeviceRegistration(String registrationId) {
		checkOpen();
		Objects.requireNonNull(registrationId, "registrationId");

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("approved", false);
		return execute(HttpMethod.PATCH, registrationPath(registrationId), body);
	}

	/**
	 * Renames one of the user's devices. The name is all that changes, so no passphrase is needed.
	 *
	 * @param deviceId the id of the device
	 * @param name the new name, 1 to 128 characters
	 * @return a future completing with the renamed device; it fails with {@link NotFoundException} if the
	 *         user has no such device
	 */
	public CompletableFuture<Device> renameDevice(Id deviceId, String name) {
		checkOpen();
		Objects.requireNonNull(deviceId, "deviceId");
		Objects.requireNonNull(name, "name");

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("name", name);
		return transport.deliver(call(HttpMethod.PUT, "/devices/" + deviceId.toBase58String(), body, true)
				.compose(res -> res.json(Device.class)));
	}

	/**
	 * Lists the account's security log, newest first: the last 50 things that happened to its passphrase,
	 * devices and sign-ins.
	 *
	 * @return a future completing with the events
	 */
	public CompletableFuture<List<SecurityEvent>> listSecurityEvents() {
		checkOpen();
		return transport.deliver(call(HttpMethod.GET, "/security/events", null, true)
				.compose(res -> res.decode(content -> new JsonArray(content).stream()
						.map(o -> {
							JsonObject e = (JsonObject) o;
							return new SecurityEvent(e.getString("kind"), e.getString("method"),
									e.getLong("at", 0L), e.getString("address"));
						}).toList())));
	}

	// ---- Sign in with Boson Identity -----------------------------------------------------------

	/**
	 * Reads a web page's pending request to be signed in ("Sign in with Boson Identity"), from the code the
	 * page shows (see {@link SignInRequest#parseCode(String)}).
	 *
	 * @param requestId the request id
	 * @return a future completing with the request, or empty if there is no such pending request
	 */
	public CompletableFuture<Optional<SignInRequest>> getSignInRequest(String requestId) {
		checkOpen();
		Objects.requireNonNull(requestId, "requestId");
		return fetchOptional(signInPath(requestId), SignInRequest.class);
	}

	/**
	 * Approves a web page's sign-in, with the number the user picked: the one the page shows. A wrong
	 * number refuses the request. Signing in to the admin dashboard takes the user key and an
	 * administrator's account. The page gets a web session, which can't approve device registrations.
	 *
	 * @param requestId the request id
	 * @param number the number the user picked
	 * @return a future completing when the page is signed in; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} for a wrong number
	 */
	public CompletableFuture<Void> approveSignInRequest(String requestId, int number) {
		checkOpen();
		Objects.requireNonNull(requestId, "requestId");
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("approved", true);
		body.put("number", number);
		return execute(HttpMethod.PATCH, signInPath(requestId), body);
	}

	/**
	 * Refuses a web page's sign-in.
	 *
	 * @param requestId the request id
	 * @return a future completing when the request is refused
	 */
	public CompletableFuture<Void> denySignInRequest(String requestId) {
		checkOpen();
		Objects.requireNonNull(requestId, "requestId");
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("approved", false);
		return execute(HttpMethod.PATCH, signInPath(requestId), body);
	}

	static String signInPath(String requestId) {
		return "/auth/requests/" + DirectorTransport.encode(requestId);
	}

	// ---- Linked sign-ins ------------------------------------------------------------------------

	/**
	 * Lists the OAuth sign-ins linked to the user: the accounts (GitHub, Google, ...) that can sign in to
	 * the portal as the user, and that can reset a forgotten passphrase.
	 *
	 * @return a future completing with the linked sign-ins
	 */
	public CompletableFuture<List<LinkedIdentity>> listLinkedIdentities() {
		checkOpen();
		DirectorTransport auth = authTransport();
		return auth.deliver(auth.call(HttpMethod.GET, "/identities", null, tokens)
				.compose(res -> res.json(LinkedIdentities.class))
				.map(linked -> linked.identities));
	}

	/**
	 * Unlinks one of the user's OAuth sign-ins. It takes the passphrase, if the user has one: a sign-in can
	 * reset the passphrase, so the passphrase guards the list of them.
	 *
	 * @param sessionId the id of the sign-in, as {@link #listLinkedIdentities()} lists it
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @return a future completing when the sign-in is unlinked
	 */
	public CompletableFuture<Void> disconnectLinkedIdentity(Id sessionId, @Nullable String passphrase) {
		checkOpen();
		Objects.requireNonNull(sessionId, "sessionId");
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		putIfNotNull(body, "passphrase", passphrase);
		DirectorTransport auth = authTransport();
		return auth.deliver(auth.call(HttpMethod.POST, "/identities/" + sessionId.toBase58String() + "/disconnect",
				body, tokens).<Void>mapEmpty());
	}

	private synchronized DirectorTransport authTransport() {
		if (authTransport == null)
			authTransport = new DirectorTransport(vertx, directorUrl, AUTH_API, nodeId, resolveToAddress, callbackExecutor, log);
		return authTransport;
	}

	private static final class LinkedIdentities {
		private final List<LinkedIdentity> identities;

		@JsonCreator
		LinkedIdentities(@JsonProperty(value = "identities", required = true) List<LinkedIdentity> identities) {
			this.identities = List.copyOf(identities);
		}
	}

	// What the user key signs to register a device directly: the device id, then the nonce the device
	// signed. The Director checks the same bytes.
	static byte[] deviceAuthorization(Id deviceId, byte[] nonce) {
		byte[] message = new byte[Id.BYTES + nonce.length];
		System.arraycopy(deviceId.getBytes(), 0, message, 0, Id.BYTES);
		System.arraycopy(nonce, 0, message, Id.BYTES, nonce.length);
		return message;
	}

	static String registrationPath(String registrationId) {
		return "/devices/registrations/" + DirectorTransport.encode(registrationId);
	}

	// ---- Passphrase ----------------------------------------------------------------------------

	/**
	 * Sets the account passphrase, when the account has none yet. To change an existing passphrase
	 * use {@link #updatePassphrase(String, String)}; calling this instead fails with
	 * {@link io.bosonnetwork.director.client.exceptions.PassphraseRequiredException}.
	 * <p>
	 * The user key alone cannot reset a forgotten passphrase. Make recovery codes right after setting it
	 * ({@link #makeRecoveryCodes(String)}), so that the user can reset it with one through
	 * {@link DirectorGuest#redeemRecoveryCode(Id, String)}.
	 *
	 * @param passphrase the new passphrase
	 * @return a future completing when the passphrase is set
	 * @throws IllegalArgumentException if the passphrase is empty
	 */
	public CompletableFuture<Void> setPassphrase(String passphrase) {
		checkOpen();
		checkPassphrase(passphrase, "passphrase");

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("passphrase", passphrase);
		return execute(HttpMethod.PUT, "/passphrase", body);
	}

	/**
	 * Changes the account passphrase.
	 *
	 * @param currentPassphrase the passphrase the account has now
	 * @param newPassphrase the passphrase to replace it with
	 * @return a future completing when the passphrase is changed; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ForbiddenException} if the current
	 *         passphrase is wrong
	 * @throws IllegalArgumentException if either passphrase is empty
	 */
	public CompletableFuture<Void> updatePassphrase(String currentPassphrase, String newPassphrase) {
		checkOpen();
		checkPassphrase(currentPassphrase, "currentPassphrase");
		checkPassphrase(newPassphrase, "newPassphrase");

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("passphrase", newPassphrase);
		body.put("currentPassphrase", currentPassphrase);
		return execute(HttpMethod.PUT, "/passphrase", body);
	}

	/**
	 * Removes the account passphrase. Succeeds without effect if the account has none.
	 *
	 * @param currentPassphrase the passphrase the account has now
	 * @return a future completing when the passphrase is removed; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ForbiddenException} if the passphrase
	 *         is wrong
	 * @throws IllegalArgumentException if the passphrase is empty
	 */
	public CompletableFuture<Void> clearPassphrase(String currentPassphrase) {
		checkOpen();
		checkPassphrase(currentPassphrase, "currentPassphrase");

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("passphrase", currentPassphrase);
		return execute(HttpMethod.POST, "/passphrase/clear", body);
	}

	/**
	 * Makes new recovery codes for the passphrase, replacing the user's current ones, used or not. Each
	 * resets a forgotten passphrase once, without the user key. They are returned only now: the
	 * Director keeps a hash of each, so show them to the user to save, apart from the user key.
	 *
	 * @param passphrase the account passphrase, which the codes reset
	 * @return a future completing with the codes, as {@code XXXX-XXXX-XXXX-XXXX}; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the account has no
	 *         passphrase, {@link io.bosonnetwork.director.client.exceptions.ForbiddenException} if it is
	 *         wrong, or {@link io.bosonnetwork.director.client.exceptions.RateLimitException} after too many
	 *         wrong ones
	 * @throws IllegalArgumentException if the passphrase is empty
	 */
	public CompletableFuture<List<String>> makeRecoveryCodes(String passphrase) {
		checkOpen();
		checkPassphrase(passphrase, "passphrase");

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("passphrase", passphrase);
		return transport.deliver(call(HttpMethod.POST, "/passphrase/recovery-codes", body, true)
				.compose(res -> res.decode(content -> {
					JsonArray codes = new JsonObject(content).getJsonArray("recoveryCodes");
					if (codes == null || codes.isEmpty())
						throw new IllegalArgumentException("missing 'recoveryCodes'");
					return codes.stream().map(String.class::cast).toList();
				})));
	}

	/**
	 * Gets the ways the user could reset a forgotten passphrase without the user key: linked OAuth
	 * sign-ins, passkeys that can recover, and unused recovery codes.
	 *
	 * @return a future completing with the recovery methods
	 */
	public CompletableFuture<RecoveryMethods> getRecoveryMethods() {
		checkOpen();
		return transport.deliver(call(HttpMethod.GET, "/me", null, true)
				.compose(res -> res.decode(content -> {
					JsonObject methods = new JsonObject(content).getJsonObject("recoveryMethods");
					if (methods == null)
						throw new IllegalArgumentException("missing 'recoveryMethods'");
					return new RecoveryMethods(methods.getInteger("oauth", 0), methods.getInteger("passkeys", 0),
							methods.getInteger("recoveryCodes", 0));
				})));
	}

	// ---- Profile -------------------------------------------------------------------------------

	/**
	 * Gets the user's profile.
	 *
	 * @return a future completing with the profile
	 */
	public CompletableFuture<Profile> getProfile() {
		checkOpen();
		return transport.deliver(fetchProfile());
	}

	/**
	 * Updates the user's profile. The account must not be passphrase-protected; see
	 * {@link #updateProfile(ProfileUpdate, String)}.
	 *
	 * @param update the fields to change
	 * @return a future completing when the profile is updated
	 * @throws IllegalArgumentException if the update changes nothing
	 */
	public CompletableFuture<Void> updateProfile(ProfileUpdate update) {
		return updateProfile(update, null);
	}

	/**
	 * Updates the user's profile. Only the fields set on the update are changed.
	 *
	 * @param update the fields to change
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @return a future completing when the profile is updated
	 * @throws IllegalArgumentException if the update changes nothing
	 */
	public CompletableFuture<Void> updateProfile(ProfileUpdate update, @Nullable String passphrase) {
		checkOpen();
		Objects.requireNonNull(update, "update");
		if (update.isEmpty())
			throw new IllegalArgumentException("The profile update changes nothing");

		Map<String, @Nullable Object> body = new LinkedHashMap<>(update.fields());
		putIfNotNull(body, "passphrase", passphrase);
		return execute(HttpMethod.PUT, "/profile", body);
	}

	/**
	 * Gets the public profile of any user: of this node, or of another super node, which the Director then
	 * asks on this client's behalf.
	 *
	 * @param userId the id of the user
	 * @return a future completing with the user's public profile, or empty if no such user is known
	 */
	public CompletableFuture<Optional<PublicProfile>> getUserProfile(Id userId) {
		checkOpen();
		Objects.requireNonNull(userId, "userId");
		// Authenticated: the Director resolves a user of another node only for a user of its own.
		return transport.deliver(call(HttpMethod.GET, "/profile/" + userId.toBase58String(), null, true)
				.compose(res -> res.json(PublicProfile.class))
				.map(Optional::of)
				.recover(DirectorClient::emptyIfNotFound));
	}

	private Future<Profile> fetchProfile() {
		return call(HttpMethod.GET, "/profile", null, true)
				.compose(res -> res.json(Profile.class));
	}

	// ---- Avatar --------------------------------------------------------------------------------

	/**
	 * Uploads a new avatar for the user, replacing the current one. The Director accepts PNG and
	 * JPEG images, within a size limit set by the node.
	 *
	 * @param image the image data
	 * @param contentType the image type, {@code image/png} or {@code image/jpeg}
	 * @return a future completing with the avatar URI now in the user's profile
	 * @throws IllegalArgumentException if the image is empty or the type is not PNG or JPEG
	 */
	public CompletableFuture<String> updateAvatar(byte[] image, String contentType) {
		checkOpen();
		Objects.requireNonNull(image, "image");
		Objects.requireNonNull(contentType, "contentType");
		if (image.length == 0)
			throw new IllegalArgumentException("The avatar image is empty");
		String type = avatarType(contentType);
		return transport.deliver(uploadAvatar(Buffer.buffer(image), type));
	}

	/**
	 * Uploads an image file as the user's new avatar, replacing the current one. The image type is
	 * taken from the file extension: {@code .png}, {@code .jpg} or {@code .jpeg}.
	 *
	 * @param file the image file
	 * @return a future completing with the avatar URI now in the user's profile; it fails with the
	 *         file system's error if the file cannot be read
	 * @throws IllegalArgumentException if the file extension is not one of the above
	 */
	public CompletableFuture<String> updateAvatar(Path file) {
		checkOpen();
		Objects.requireNonNull(file, "file");
		String type = avatarTypeOf(file);
		return transport.deliver(vertx.fileSystem().readFile(file.toString())
				.compose(image -> uploadAvatar(image, type)));
	}

	/**
	 * Downloads the user's current avatar.
	 *
	 * @return a future completing with the avatar, or empty if the user has none
	 */
	public CompletableFuture<Optional<Avatar>> getAvatar() {
		checkOpen();
		return transport.deliver(fetchAvatar("/avatar"));
	}

	/**
	 * Downloads the avatar of any user: of this node, or of another super node, which the Director then
	 * fetches on this client's behalf.
	 *
	 * @param userId the id of the user
	 * @return a future completing with the avatar, or empty if the user has none or is not known
	 */
	public CompletableFuture<Optional<Avatar>> getUserAvatar(Id userId) {
		checkOpen();
		Objects.requireNonNull(userId, "userId");
		return transport.deliver(fetchAvatar(userAvatarPath(userId)));
	}

	/**
	 * Checks an avatar of the user the caller holds against the Director, the way an image cache keeps
	 * avatars current: the image is downloaded only if it changed. See
	 * {@link #refreshUserAvatar(Id, Avatar)}.
	 *
	 * @param held the copy the caller holds
	 * @return a future completing with the outcome
	 */
	public CompletableFuture<AvatarRefresh> refreshAvatar(Avatar held) {
		checkOpen();
		Objects.requireNonNull(held, "held");
		return transport.deliver(refresh("/avatar", held));
	}

	/**
	 * Checks an avatar of any user the caller holds against the Director, the way an image cache keeps
	 * avatars current. The Director is asked whether the held copy is still the avatar, by the validators
	 * it was downloaded with, and sends the image only if it is not. A copy without validators is
	 * downloaded again.
	 *
	 * @param userId the id of the user
	 * @param held the copy the caller holds, as downloaded, or restored with
	 *        {@link Avatar#of(String, byte[], String, String)}
	 * @return a future completing with the outcome: the held copy is still current, the avatar changed
	 *         (with the new one), or it was removed
	 */
	public CompletableFuture<AvatarRefresh> refreshUserAvatar(Id userId, Avatar held) {
		checkOpen();
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(held, "held");
		return transport.deliver(refresh(userAvatarPath(userId), held));
	}

	private static String userAvatarPath(Id userId) {
		return "/avatar/" + userId.toBase58String();
	}

	private Future<AvatarRefresh> refresh(String path, Avatar held) {
		if (!held.hasValidators())
			return fetchAvatar(path).map(avatar -> avatar.map(AvatarRefresh::changed).orElseGet(AvatarRefresh::removed));

		MultiMap conditions = MultiMap.caseInsensitiveMultiMap();
		held.getETag().ifPresent(tag -> conditions.set("If-None-Match", tag));
		held.getLastModified().ifPresent(time -> conditions.set("If-Modified-Since", time));
		// Authenticated for the reason given in fetchAvatar.
		return transport.conditionalGet(path, conditions, tokens)
				.map(res -> res.statusCode() == DirectorTransport.NOT_MODIFIED ?
						AvatarRefresh.unchanged(held) : AvatarRefresh.changed(avatarOf(res)))
				.recover(e -> e instanceof NotFoundException ? Future.succeededFuture(AvatarRefresh.removed()) :
						Future.failedFuture(e));
	}

	/**
	 * Removes the user's avatar. Succeeds without effect if the user has none.
	 *
	 * @return a future completing when the avatar is removed
	 */
	public CompletableFuture<Void> removeAvatar() {
		checkOpen();
		// The Director answers 404 when there is no avatar to remove: already the state asked for.
		return transport.deliver(call(HttpMethod.DELETE, "/avatar", null, true)
				.<Void>mapEmpty()
				.recover(e -> e instanceof NotFoundException ? Future.succeededFuture() : Future.failedFuture(e)));
	}

	// Authenticated even for another user's avatar: the Director fetches one from another node only for a
	// user of its own.
	private Future<Optional<Avatar>> fetchAvatar(String path) {
		return call(HttpMethod.GET, path, null, true)
				.map(res -> Optional.of(avatarOf(res)))
				.recover(DirectorClient::emptyIfNotFound);
	}

	private static Avatar avatarOf(DirectorTransport.Response res) {
		String type = res.getHeader("Content-Type");
		return new Avatar(type != null ? type : "application/octet-stream", res.body().getBytes(),
				res.getHeader("ETag"), res.getHeader("Last-Modified"));
	}

	private Future<String> uploadAvatar(Buffer image, String contentType) {
		return call(HttpMethod.PUT, "/avatar", image, contentType, true)
				.compose(res -> res.stringField("uri"));
	}

	// The Director stores only these two types; it would fail anything else, and not as a bad request.
	private static String avatarType(String contentType) {
		String type = contentType.trim().toLowerCase(Locale.ROOT);
		int params = type.indexOf(';');
		if (params >= 0)
			type = type.substring(0, params).trim();

		return switch (type) {
			case CONTENT_TYPE_PNG -> CONTENT_TYPE_PNG;
			case CONTENT_TYPE_JPEG, "image/jpg" -> CONTENT_TYPE_JPEG;
			default -> throw new IllegalArgumentException("Unsupported avatar type (PNG or JPEG only): " + contentType);
		};
	}

	private static String avatarTypeOf(Path file) {
		Path fileName = file.getFileName();
		String name = fileName != null ? fileName.toString().toLowerCase(Locale.ROOT) : "";
		if (name.endsWith(".png"))
			return CONTENT_TYPE_PNG;
		if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
			return CONTENT_TYPE_JPEG;

		throw new IllegalArgumentException("Unsupported avatar file (.png, .jpg or .jpeg only): " + file);
	}

	// ---- Plan ----------------------------------------------------------------------------------

	/**
	 * Gets the user's current plan: its name, its details from the node's plan catalog, and the
	 * subscription that grants it, if any. A user without an active subscription is on the node's
	 * free plan.
	 *
	 * @return a future completing with the user's plan
	 */
	public CompletableFuture<UserPlan> getPlan() {
		checkOpen();
		// The profile names the plan the Director applies to the user - the plan of the active
		// subscription, or the free plan without one - so it is the authority on the name. The catalog
		// adds the details, and the subscription the terms.
		Future<Profile> profile = fetchProfile();
		Future<Optional<Subscription>> subscription = fetchActiveSubscription();
		Future<List<Plan>> plans = fetchPlans();

		return transport.deliver(Future.all(profile, subscription, plans).map(v -> {
			String name = profile.result().getPlanName();
			Subscription s = subscription.result().orElse(null);
			Plan plan = null;
			for (Plan p : plans.result()) {
				if (s != null ? p.getId() == s.getPlanId() : p.getName().equals(name)) {
					plan = p;
					break;
				}
			}

			return new UserPlan(name, plan, s);
		}));
	}

	/**
	 * Lists the plans the node offers.
	 *
	 * @return a future completing with the active plans
	 */
	public CompletableFuture<List<Plan>> getPlans() {
		checkOpen();
		return transport.deliver(fetchPlans());
	}

	private Future<List<Plan>> fetchPlans() {
		return call(HttpMethod.GET, "/plans", null, false).compose(res -> res.jsonList(Plan.class));
	}

	// ---- Subscriptions and payments ------------------------------------------------------------

	/**
	 * Orders a subscription to a plan, for a number of monthly billing cycles. The subscription is pending
	 * until its payment is made; see {@link #submitPayment(long, PaymentTransaction)}.
	 *
	 * @param planId the id of the plan, as {@link #getPlans()} lists it
	 * @param billingCycles the number of months to pay for
	 * @return a future completing with the subscription and its payment; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the user already has an
	 *         active subscription, and with an
	 *         {@link io.bosonnetwork.director.client.exceptions.InvalidRequestException} for the free plan,
	 *         which needs none
	 * @throws IllegalArgumentException if the plan id or the billing cycles are not positive
	 */
	public CompletableFuture<SubscriptionOrder> subscribe(int planId, int billingCycles) {
		checkOpen();
		checkPositive(planId, "planId");
		checkPositive(billingCycles, "billingCycles");
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("planId", planId);
		body.put("billingCycles", billingCycles);
		return transport.deliver(call(HttpMethod.POST, "/subscriptions", body, true)
				.compose(res -> res.json(SubscriptionOrder.class)));
	}

	/**
	 * Lists the user's subscriptions, all on one page.
	 *
	 * @return a future completing with the subscriptions
	 */
	public CompletableFuture<PaginatedResult<Subscription>> listSubscriptions() {
		checkOpen();
		return fetchPage("/subscriptions", 0, 0, Subscription.class);
	}

	/**
	 * Lists one page of the user's subscriptions.
	 *
	 * @param page the page number, from 1
	 * @param pageSize the number of subscriptions on a page
	 * @return a future completing with the page
	 * @throws IllegalArgumentException if the page or the page size is not positive
	 */
	public CompletableFuture<PaginatedResult<Subscription>> listSubscriptions(long page, long pageSize) {
		checkOpen();
		checkPositive(page, "page");
		checkPositive(pageSize, "pageSize");
		return fetchPage("/subscriptions", page, pageSize, Subscription.class);
	}

	/**
	 * Gets the user's active subscription, the one that grants the user's plan now.
	 *
	 * @return a future completing with the active subscription, or empty if the user has none (and is on
	 *         the free plan)
	 */
	public CompletableFuture<Optional<Subscription>> getActiveSubscription() {
		checkOpen();
		return transport.deliver(fetchActiveSubscription());
	}

	private Future<Optional<Subscription>> fetchActiveSubscription() {
		return call(HttpMethod.GET, "/subscriptions/active", null, true)
				// The API documents a 404 for "no active subscription"; the Director answers 204.
				.compose(res -> res.statusCode() == 204 ? Future.succeededFuture(Optional.<Subscription>empty()) :
						res.json(Subscription.class).map(Optional::of))
				.recover(DirectorClient::emptyIfNotFound);
	}

	/**
	 * Gets one of the user's subscriptions.
	 *
	 * @param subscriptionId the id of the subscription
	 * @return a future completing with the subscription, or empty if the user has no such subscription
	 */
	public CompletableFuture<Optional<Subscription>> getSubscription(long subscriptionId) {
		checkOpen();
		return fetchOptional("/subscriptions/" + subscriptionId, Subscription.class);
	}

	/**
	 * Renews one of the user's subscriptions for more monthly billing cycles. It is extended once the
	 * payment of the renewal is made.
	 *
	 * @param subscriptionId the id of the subscription: active, or past due
	 * @param billingCycles the number of months to add
	 * @return a future completing with the subscription and the payment for the renewal
	 * @throws IllegalArgumentException if the billing cycles are not positive
	 */
	public CompletableFuture<SubscriptionOrder> renewSubscription(long subscriptionId, int billingCycles) {
		checkOpen();
		checkPositive(billingCycles, "billingCycles");
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("intent", "renew");
		body.put("billingCycles", billingCycles);
		return transport.deliver(call(HttpMethod.PUT, "/subscriptions/" + subscriptionId, body, true)
				.compose(res -> res.json(SubscriptionOrder.class)));
	}

	/**
	 * Upgrades one of the user's subscriptions to another plan. It changes once the payment of the upgrade
	 * is made.
	 *
	 * @param subscriptionId the id of the subscription, which must be active
	 * @param planId the id of the plan to upgrade to
	 * @return a future completing with the subscription and the payment for the upgrade
	 * @throws IllegalArgumentException if the plan id is not positive
	 */
	public CompletableFuture<SubscriptionOrder> upgradeSubscription(long subscriptionId, int planId) {
		checkOpen();
		checkPositive(planId, "planId");
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("intent", "upgrade");
		body.put("newPlanId", planId);
		return transport.deliver(call(HttpMethod.PUT, "/subscriptions/" + subscriptionId, body, true)
				.compose(res -> res.json(SubscriptionOrder.class)));
	}

	/**
	 * Lists the user's payments, all on one page.
	 *
	 * @return a future completing with the payments
	 */
	public CompletableFuture<PaginatedResult<Payment>> listPayments() {
		checkOpen();
		return fetchPage("/payments", 0, 0, Payment.class);
	}

	/**
	 * Lists one page of the user's payments.
	 *
	 * @param page the page number, from 1
	 * @param pageSize the number of payments on a page
	 * @return a future completing with the page
	 * @throws IllegalArgumentException if the page or the page size is not positive
	 */
	public CompletableFuture<PaginatedResult<Payment>> listPayments(long page, long pageSize) {
		checkOpen();
		checkPositive(page, "page");
		checkPositive(pageSize, "pageSize");
		return fetchPage("/payments", page, pageSize, Payment.class);
	}

	/**
	 * Gets one of the user's payments.
	 *
	 * @param paymentId the id of the payment
	 * @return a future completing with the payment, or empty if the user has no such payment
	 */
	public CompletableFuture<Optional<Payment>> getPayment(long paymentId) {
		checkOpen();
		return fetchOptional("/payments/" + paymentId, Payment.class);
	}

	/**
	 * Submits the transaction that pays for one of the user's payments. The Director verifies it on the
	 * network and then confirms the payment, which activates or extends its subscription.
	 *
	 * @param paymentId the id of the payment, which must be unpaid or pending
	 * @param transaction the transaction that pays for it
	 * @return a future completing when the Director has accepted the transaction for verification
	 */
	public CompletableFuture<Void> submitPayment(long paymentId, PaymentTransaction transaction) {
		checkOpen();
		Objects.requireNonNull(transaction, "transaction");
		return execute(HttpMethod.PUT, "/payments/" + paymentId, transaction.fields());
	}

	/**
	 * Cancels one of the user's payments, which must be unpaid or pending; the subscription it was for is
	 * cancelled with it.
	 *
	 * @param paymentId the id of the payment
	 * @return a future completing when the payment is cancelled; it fails with {@link NotFoundException} if
	 *         the user has no such payment
	 */
	public CompletableFuture<Void> cancelPayment(long paymentId) {
		checkOpen();
		return execute(HttpMethod.DELETE, "/payments/" + paymentId, null);
	}

	// ---- HTTP ----------------------------------------------------------------------------------

	// Sends a request with an optional JSON body to the client API. Every API call goes through here or
	// the overload below, so adding one to this client is a method that checks the client is open, names
	// its path and decodes its answer - the simple ones through one of the helpers below, which only send
	// and decode.
	private Future<DirectorTransport.Response> call(HttpMethod method, String path,
			@Nullable Map<String, ?> json, boolean authenticated) {
		return transport.call(method, path, json, authenticated ? tokens : null);
	}

	private Future<DirectorTransport.Response> call(HttpMethod method, String path, @Nullable Buffer body,
			@Nullable String contentType, boolean authenticated) {
		return transport.call(method, path, body, contentType, authenticated ? tokens : null);
	}

	// An authenticated request answered with no content.
	private CompletableFuture<Void> execute(HttpMethod method, String path, @Nullable Map<String, ?> json) {
		return transport.deliver(call(method, path, json, true).<Void>mapEmpty());
	}

	// An authenticated lookup of something that may not be there.
	private <T> CompletableFuture<Optional<T>> fetchOptional(String path, Class<T> type) {
		return transport.deliver(call(HttpMethod.GET, path, null, true)
				.compose(res -> res.json(type))
				.map(Optional::of)
				.recover(DirectorClient::emptyIfNotFound));
	}

	// An authenticated request answered with a page; page 0 asks for everything on one page.
	private <T> CompletableFuture<PaginatedResult<T>> fetchPage(String path, long page, long pageSize, Class<T> type) {
		String query = page > 0 ? path + "?page=" + page + "&pageSize=" + pageSize : path;
		return transport.deliver(call(HttpMethod.GET, query, null, true).compose(res -> res.paged(type)));
	}

	private static void checkPositive(long value, String name) {
		if (value <= 0)
			throw new IllegalArgumentException(name + " must be positive: " + value);
	}

	// An authenticated request answered with a list.
	private <T> CompletableFuture<List<T>> fetchList(String path, Class<T> type) {
		return transport.deliver(call(HttpMethod.GET, path, null, true)
				.compose(res -> res.jsonList(type)));
	}

	// A proof-of-work challenge, as issued by the Director.
	private static final class Challenge {
		// Opaque: relayed back verbatim with the registration.
		private final byte[] token;
		private final byte[] signature;
		// Seeds the solver.
		private final byte[] nonce;
		private final int n;
		private final int k;
		private final int effort;

		private Challenge(byte[] token, byte[] signature, byte[] nonce, int n, int k, int effort) {
			this.token = token;
			this.signature = signature;
			this.nonce = nonce;
			this.n = n;
			this.k = k;
			this.effort = effort;
		}

		// Byte fields are unpadded base64url. The effort is authenticated inside the signed token, so
		// it is used as given.
		private static Challenge parse(Buffer body) {
			JsonObject json = new JsonObject(body);
			return new Challenge(
					Json.BASE64_DECODER.decode(requiredString(json, "challenge")),
					Json.BASE64_DECODER.decode(requiredString(json, "challengeSig")),
					Json.BASE64_DECODER.decode(requiredString(json, "nonce")),
					Objects.requireNonNull(json.getInteger("n"), "missing 'n'"),
					Objects.requireNonNull(json.getInteger("k"), "missing 'k'"),
					Objects.requireNonNull(json.getInteger("effort"), "missing 'effort'"));
		}
	}

	// ---- Helpers -------------------------------------------------------------------------------

	private void checkOpen() {
		transport.checkOpen();
	}

	// A lookup of something that is not there: empty, not a failure.
	private static <T> Future<Optional<T>> emptyIfNotFound(Throwable e) {
		return e instanceof NotFoundException ? Future.succeededFuture(Optional.empty()) : Future.failedFuture(e);
	}

	private static void checkPassphrase(String passphrase, String name) {
		Objects.requireNonNull(passphrase, name);
		if (passphrase.isEmpty())
			throw new IllegalArgumentException(name + " is empty");
	}

	/**
	 * Fluent builder for {@link DirectorClient}.
	 * <p>
	 * The Director URL and an identity are required: the user key, optionally with a device key, or
	 * the user id together with a device key. Not thread-safe.
	 */
	@NullUnmarked
	public static class Builder extends DirectorBuilder<Builder> {
		private Signature.KeyPair userKey;
		private Id userId;
		private Signature.KeyPair deviceKey;

		private Builder() {
		}

		/**
		 * Sets the user's key pair; the user id is derived from it. Replaces a user id set with
		 * {@link #userId(Id)}.
		 *
		 * @param key the user key pair
		 * @return this builder
		 */
		public Builder userKey(Signature.KeyPair key) {
			Objects.requireNonNull(key, "key");
			this.userKey = key;
			this.userId = Id.of(key.publicKey().bytes());
			return this;
		}

		/**
		 * Sets the user's key pair from an encoded private key.
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
		 * Sets the user's key pair from a raw private key.
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
		 * Sets the user id, for a client that authenticates with a device key rather than the user key.
		 * Replaces a user key set with {@link #userKey(Signature.KeyPair)}.
		 *
		 * @param userId the user id
		 * @return this builder
		 */
		public Builder userId(Id userId) {
			this.userId = Objects.requireNonNull(userId, "userId");
			this.userKey = null;
			return this;
		}

		/**
		 * Sets the key pair of this client's device.
		 *
		 * @param key the device key pair
		 * @return this builder
		 */
		public Builder deviceKey(Signature.KeyPair key) {
			this.deviceKey = Objects.requireNonNull(key, "key");
			return this;
		}

		/**
		 * Sets the key pair of this client's device from an encoded private key.
		 *
		 * @param privateKey the private key, as a {@code 0x}-prefixed hex string or a Base58 string
		 * @return this builder
		 * @throws IllegalArgumentException if the key is invalid
		 */
		public Builder deviceKey(String privateKey) {
			Objects.requireNonNull(privateKey, "privateKey");
			return deviceKey(decodeKey(privateKey));
		}

		/**
		 * Sets the key pair of this client's device from a raw private key.
		 *
		 * @param privateKey the private key bytes ({@link Signature.PrivateKey#BYTES} long)
		 * @return this builder
		 * @throws IllegalArgumentException if the key length is invalid
		 */
		public Builder deviceKey(byte[] privateKey) {
			Objects.requireNonNull(privateKey, "privateKey");
			return deviceKey(decodeKey(privateKey));
		}

		/**
		 * Validates the configuration and builds the client.
		 *
		 * @return the client, ready to use
		 * @throws IllegalStateException if Vert.x, the Director URL or the identity is missing, or the
		 *         identity is incomplete (a device key without a user, or a user id without a device key)
		 */
		public DirectorClient build() {
			try {
				return new DirectorClient(this);
			} catch (NullPointerException | IllegalArgumentException e) {
				throw new IllegalStateException("Invalid DirectorClient configuration: " + e.getMessage(), e);
			}
		}
	}
}
