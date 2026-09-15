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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoIdentity;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.crypto.pow.RegistrationPowClient;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.director.client.exceptions.RegistrationDisabledException;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.service.AccessScope;
import io.bosonnetwork.vertx.ContextualFuture;

/**
 * An asynchronous client for the client API of a Boson Director, the account service of a Boson
 * super node.
 * <p>
 * It covers what an app needs to manage its account on a super node: proof-of-work registration,
 * devices, the account passphrase, the profile and avatar, the node's identity and status, and the
 * user's plan. HTTP is an implementation detail: callers deal in {@link Id}s, keys and the model
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
 * thread completes on a Vert.x event loop; such a caller may block on the returned future, which
 * must never be done on an event loop. A Vert.x caller can turn a returned future back into a
 * {@link io.vertx.core.Future} with {@code Future.fromCompletionStage}. Cancellation is not
 * supported: {@code cancel()} returns {@code false} and never stops a call in flight. Call
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
	private final SelfIssuedTokens tokens;

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

		this.transport = new DirectorTransport(vertx, directorUrl, CLIENT_API, nodeId, log);

		// Tokens are signed with the user key when the client has it: that works before any device is
		// registered. A device signs its own, naming itself as the client.
		Signature.KeyPair signer = userKey != null ? userKey : Objects.requireNonNull(deviceKey);
		this.tokens = new SelfIssuedTokens(new CryptoIdentity(signer), userId, userKey != null ? null : deviceId,
				AccessScope.CLIENT.toString(), this::resolveNodeId, log);
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
		return ContextualFuture.of(transport.close());
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
		return ContextualFuture.of(fetchNodeId());
	}

	/**
	 * Gets the status of the super node: what it is, what it runs and which services it offers.
	 * Sent without authentication.
	 *
	 * @return a future completing with the node status
	 */
	public CompletableFuture<NodeStatus> getNodeStatus() {
		checkOpen();
		return ContextualFuture.of(call(HttpMethod.GET, "/node", null, false)
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
	 *         initial device) is already registered
	 * @throws IllegalStateException if the client has no user key, or the registration names an
	 *         initial device and the client has no device key
	 */
	public CompletableFuture<Void> registerUser(UserRegistration registration) {
		Objects.requireNonNull(registration, "registration");
		checkOpen();

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

		return ContextualFuture.of(resolveNodeId().compose(nid -> fetchChallenge().compose(challenge ->
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
				challenge.n, challenge.k, challenge.effort, challenge.nonce, MAX_POW_NONCES), false);
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
	 * proving the device holds it; it is not sent.
	 * <p>
	 * A device key is registered with one user only: registering a key the Director already knows
	 * fails with {@link io.bosonnetwork.director.client.exceptions.ConflictException}.
	 *
	 * @param key the key pair of the device to register
	 * @param deviceName a name for the device, shown to the user
	 * @param appName the name of the app the device runs
	 * @param passphrase the account passphrase, or {@code null} if the account has none
	 * @return a future completing when the device is registered
	 */
	public CompletableFuture<Void> registerDevice(Signature.KeyPair key, String deviceName, String appName,
			@Nullable String passphrase) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(deviceName, "deviceName");
		Objects.requireNonNull(appName, "appName");
		checkOpen();

		byte[] nonce = Random.randomBytes(AUTH_NONCE_SIZE);
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("deviceId", Id.of(key.publicKey().bytes()));
		body.put("deviceName", deviceName);
		body.put("appName", appName);
		body.put("nonce", nonce);
		body.put("deviceSig", key.privateKey().sign(nonce));
		putIfNotNull(body, "passphrase", passphrase);

		return ContextualFuture.of(call(HttpMethod.POST, "/devices", body, true).mapEmpty());
	}

	/**
	 * Lists the devices registered to the user.
	 *
	 * @return a future completing with the devices
	 */
	public CompletableFuture<List<Device>> listDevices() {
		checkOpen();
		return ContextualFuture.of(call(HttpMethod.GET, "/devices", null, true)
				.compose(res -> res.jsonList(Device.class)));
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
		Objects.requireNonNull(deviceId, "deviceId");
		checkOpen();

		// Always send a body, if only an empty one: the Director parses one whenever it is present.
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		putIfNotNull(body, "passphrase", passphrase);

		return ContextualFuture.of(call(HttpMethod.POST, "/devices/" + deviceId.toBase58String() + "/remove", body, true)
				.mapEmpty());
	}

	// ---- Passphrase ----------------------------------------------------------------------------

	/**
	 * Sets the account passphrase, when the account has none yet. To change an existing passphrase
	 * use {@link #updatePassphrase(String, String)}; calling this instead fails with
	 * {@link io.bosonnetwork.director.client.exceptions.PassphraseRequiredException}.
	 * <p>
	 * There is no passphrase recovery: a forgotten passphrase cannot be reset.
	 *
	 * @param passphrase the new passphrase
	 * @return a future completing when the passphrase is set
	 * @throws IllegalArgumentException if the passphrase is empty
	 */
	public CompletableFuture<Void> setPassphrase(String passphrase) {
		checkPassphrase(passphrase, "passphrase");
		checkOpen();

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("passphrase", passphrase);
		return ContextualFuture.of(call(HttpMethod.PUT, "/passphrase", body, true).mapEmpty());
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
		checkPassphrase(currentPassphrase, "currentPassphrase");
		checkPassphrase(newPassphrase, "newPassphrase");
		checkOpen();

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("passphrase", newPassphrase);
		body.put("currentPassphrase", currentPassphrase);
		return ContextualFuture.of(call(HttpMethod.PUT, "/passphrase", body, true).mapEmpty());
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
		checkPassphrase(currentPassphrase, "currentPassphrase");
		checkOpen();

		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("passphrase", currentPassphrase);
		return ContextualFuture.of(call(HttpMethod.POST, "/passphrase/clear", body, true).mapEmpty());
	}

	// ---- Profile -------------------------------------------------------------------------------

	/**
	 * Gets the user's profile.
	 *
	 * @return a future completing with the profile
	 */
	public CompletableFuture<Profile> getProfile() {
		checkOpen();
		return ContextualFuture.of(fetchProfile());
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
		Objects.requireNonNull(update, "update");
		if (update.isEmpty())
			throw new IllegalArgumentException("The profile update changes nothing");
		checkOpen();

		Map<String, @Nullable Object> body = new LinkedHashMap<>(update.fields());
		putIfNotNull(body, "passphrase", passphrase);
		return ContextualFuture.of(call(HttpMethod.PUT, "/profile", body, true).mapEmpty());
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
		Objects.requireNonNull(image, "image");
		Objects.requireNonNull(contentType, "contentType");
		if (image.length == 0)
			throw new IllegalArgumentException("The avatar image is empty");
		String type = avatarType(contentType);
		checkOpen();

		return ContextualFuture.of(uploadAvatar(Buffer.buffer(image), type));
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
		Objects.requireNonNull(file, "file");
		String type = avatarTypeOf(file);
		checkOpen();

		return ContextualFuture.of(vertx.fileSystem().readFile(file.toString())
				.compose(image -> uploadAvatar(image, type)));
	}

	/**
	 * Downloads the user's current avatar.
	 *
	 * @return a future completing with the avatar, or with {@code null} if the user has none
	 */
	public CompletableFuture<@Nullable Avatar> getAvatar() {
		checkOpen();

		Future<@Nullable Avatar> avatar = call(HttpMethod.GET, "/avatar", null, true)
				.<@Nullable Avatar>map(res -> {
					String type = res.getHeader("Content-Type");
					return new Avatar(type != null ? type : "application/octet-stream", res.body().getBytes());
				})
				.recover(e -> e instanceof NotFoundException ? 
						Future.<@Nullable Avatar>succeededFuture(null) : Future.<@Nullable Avatar>failedFuture(e));
		return ContextualFuture.of(avatar);
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
		Future<Subscription> subscription = call(HttpMethod.GET, "/subscriptions/active", null, true)
				.compose(res -> res.statusCode() == 204 ? Future.<Subscription>succeededFuture(null) :
						res.json(Subscription.class))
				// The API documents a 404 for "no active subscription"; the Director answers 204.
				.recover(e -> e instanceof NotFoundException ? Future.succeededFuture(null) : Future.failedFuture(e));
		Future<List<Plan>> plans = call(HttpMethod.GET, "/plans", null, false)
				.compose(res -> res.jsonList(Plan.class));

		return ContextualFuture.of(Future.all(profile, subscription, plans).map(v -> {
			String name = profile.result().getPlanName();
			Subscription s = subscription.result();
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

	// ---- HTTP ----------------------------------------------------------------------------------

	// Sends a request with an optional JSON body to the client API. Every API call goes through here or
	// the overload below, so adding one to this client is a method that names its path and decodes its
	// answer.
	private Future<DirectorTransport.Response> call(HttpMethod method, String path,
			@Nullable Map<String, ?> json, boolean authenticated) {
		return transport.call(method, path, json, authenticated ? tokens : null);
	}

	private Future<DirectorTransport.Response> call(HttpMethod method, String path, @Nullable Buffer body,
			@Nullable String contentType, boolean authenticated) {
		return transport.call(method, path, body, contentType, authenticated ? tokens : null);
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
	public static class Builder {
		private Vertx vertx;
		private URL directorUrl;
		private Id nodeId;
		private Signature.KeyPair userKey;
		private Id userId;
		private Signature.KeyPair deviceKey;

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
		 * Sets the Boson id of the super node the Director runs on (optional). Access tokens are bound
		 * to this id, and over HTTPS a self-signed Director certificate pinned to it is accepted as
		 * well. Without it, the client binds its tokens to the id the Director reports. Configure it
		 * when the id is known: a Director that reported another node's id could otherwise obtain
		 * tokens valid on that node.
		 *
		 * @param nodeId the super node id
		 * @return this builder
		 */
		public Builder nodeId(Id nodeId) {
			this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
			return this;
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
