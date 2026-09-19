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

import static io.bosonnetwork.director.client.DirectorTransport.encode;
import static io.bosonnetwork.director.client.DirectorTransport.requiredString;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.CryptoException;
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.director.client.exceptions.RegistrationDeniedException;
import io.bosonnetwork.director.client.exceptions.RegistrationExpiredException;
import io.bosonnetwork.json.Json;

/**
 * An asynchronous client for what a Boson Director offers to anyone, without signing in: the node's
 * identity, status and plans, how the node signs users up, where an OAuth sign-in starts, and a new
 * device asking to join an existing account.
 * <p>
 * Acting as a user takes a {@link DirectorClient}; an OAuth sign-in in progress, a {@link DirectorOAuth}.
 *
 * <h2>Joining an account from a new device</h2>
 * The new device, which holds only its own key, asks to join with
 * {@link #requestDeviceRegistration(Signature.KeyPair, String, String)} and shows the
 * {@linkplain DeviceRegistration#getPairingCode() pairing code} of the request to a device already
 * registered to the user, typically as a QR code. That device approves with
 * {@link DirectorClient#approveDeviceRegistration(PairingCode)}, sealing the user key to the code, and the
 * new device - waiting in {@link #finishDeviceRegistration(DeviceRegistration)} - receives the user key.
 * The Director relays the key without being able to read it.
 *
 * <h2>Transport security, errors and threading</h2>
 * As for {@link DirectorClient}: the node id, when configured, pins a self-signed certificate; a refused
 * call fails with a {@link DirectorException} carrying the HTTP status; invalid arguments throw at once; a
 * call made on a Vert.x context completes on it. Call {@link #close()} when done.
 */
public class DirectorGuest {
	// Size of the random nonce a device signs to prove it holds its key.
	private static final int NONCE_SIZE = 32;

	// The scope of the sessions an OAuth sign-in started here gets: the client API.
	private static final String OAUTH_SCOPE = "client";

	// How long finishDeviceRegistration waits for the user to answer. The Director holds the request
	// until the registration is approved, denied or expired - three minutes after it was made - so this
	// only has to outlast that.
	private static final long REGISTRATION_WAIT = TimeUnit.MINUTES.toMillis(4);

	private final URL directorUrl;
	private final DirectorTransport transport;

	private static final Logger log = LoggerFactory.getLogger(DirectorGuest.class);

	private DirectorGuest(Builder builder) {
		Vertx vertx = Objects.requireNonNull(builder.vertx, "Vert.x instance must be set");
		this.directorUrl = Objects.requireNonNull(builder.directorUrl, "directorUrl must be set");
		// Spans two APIs, the sign-in (/auth) and the client one (/client): paths name their API.
		this.transport = new DirectorTransport(vertx, directorUrl, "", builder.nodeId, builder.resolveToAddress, builder.callbackExecutor, log);
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
	 * Closes the client and releases its connections. Calls made after closing throw
	 * {@link IllegalStateException}.
	 *
	 * @return a future completing when the client is closed
	 */
	public CompletableFuture<Void> close() {
		return transport.deliver(transport.close());
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
	 * Gets the Boson id of the super node the Director runs on.
	 *
	 * @return a future completing with the node id
	 */
	public CompletableFuture<Id> getNodeId() {
		transport.checkOpen();
		return transport.deliver(transport.call(HttpMethod.GET, "/client/id", null, null)
				.compose(res -> res.idField("id")));
	}

	/**
	 * Gets the status of the super node: what it is, what it runs and which services it offers.
	 *
	 * @return a future completing with the node status
	 */
	public CompletableFuture<NodeStatus> getNodeStatus() {
		transport.checkOpen();
		return transport.deliver(transport.call(HttpMethod.GET, "/client/node", null, null)
				.compose(res -> res.json(NodeStatus.class)));
	}

	/**
	 * Lists the plans the node offers.
	 *
	 * @return a future completing with the active plans
	 */
	public CompletableFuture<List<Plan>> getPlans() {
		transport.checkOpen();
		return transport.deliver(transport.call(HttpMethod.GET, "/client/plans", null, null)
				.compose(res -> res.jsonList(Plan.class)));
	}

	// ---- Sign-up and OAuth ---------------------------------------------------------------------

	/**
	 * Gets how the node signs users up: whether it accepts proof-of-work registration, and whether users
	 * can sign up through OAuth. Answering it has no side effect on the node.
	 *
	 * @return a future completing with the registration options; it fails with {@link NotFoundException}
	 *         on a Director too old to report them
	 */
	public CompletableFuture<RegistrationOptions> getRegistrationOptions() {
		transport.checkOpen();
		return transport.deliver(transport.call(HttpMethod.GET, "/client/registration", null, null)
				.compose(res -> res.json(RegistrationOptions.class)));
	}

	/**
	 * Lists the OAuth providers the node signs users in with. Empty when the node has none configured.
	 *
	 * @return a future completing with the providers
	 */
	public CompletableFuture<List<AuthProvider>> getProviders() {
		transport.checkOpen();
		return transport.deliver(transport.call(HttpMethod.GET, "/auth/providers", null, null)
				.compose(res -> res.jsonList(AuthProvider.class)));
	}

	/**
	 * Returns the URL that starts an OAuth sign-in with a provider, to open in a browser. After the
	 * provider signs the user in, the Director redirects the browser to {@code redirectUri}, adding the
	 * session token as the {@code token} query parameter, or an {@code error} parameter if the sign-in
	 * failed. Continue the sign-in with a {@link DirectorOAuth} built with that token. Makes no request.
	 *
	 * @param provider the provider id, as {@link #getProviders()} lists it
	 * @param redirectUri where the Director sends the browser back to, such as an app's own URI scheme
	 * @return the URL to open
	 * @throws IllegalArgumentException if the provider or the redirect URI is empty
	 */
	public String authorizeUrl(String provider, String redirectUri) {
		Objects.requireNonNull(provider, "provider");
		Objects.requireNonNull(redirectUri, "redirectUri");
		if (provider.isEmpty())
			throw new IllegalArgumentException("provider is empty");
		if (redirectUri.isEmpty())
			throw new IllegalArgumentException("redirectUri is empty");

		return directorUrl.getProtocol() + "://" + directorUrl.getAuthority() +
				directorUrl.getPath().replaceAll("/+$", "") + DirectorTransport.API_VERSION_PREFIX +
				"/auth/oauth/" + encode(provider) + "/authorize" +
				"?redirect_uri=" + encode(redirectUri) + "&scope=" + OAUTH_SCOPE;
	}

	// ---- Joining an account from a new device --------------------------------------------------

	/**
	 * Asks to register a new device to an existing account. The request names no user: the user who
	 * approves it decides which account the device joins. Show the returned registration's
	 * {@linkplain DeviceRegistration#getPairingCode() pairing code} to a device registered to the user,
	 * then wait for the answer with {@link #finishDeviceRegistration(DeviceRegistration)}. The request
	 * expires after three minutes.
	 *
	 * @param deviceKey the key pair of the new device; it signs the request and is not sent
	 * @param deviceName a name for the device, shown to the user
	 * @param appName the name of the app the device runs
	 * @return a future completing with the pending registration; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the device is already
	 *         registered
	 */
	public CompletableFuture<DeviceRegistration> requestDeviceRegistration(Signature.KeyPair deviceKey,
			String deviceName, String appName) {
		transport.checkOpen();
		Objects.requireNonNull(deviceKey, "deviceKey");
		Objects.requireNonNull(deviceName, "deviceName");
		Objects.requireNonNull(appName, "appName");

		Map<String, @Nullable Object> body = signedByDevice(deviceKey);
		body.put("deviceName", deviceName);
		body.put("appName", appName);
		return transport.deliver(transport.call(HttpMethod.POST, "/client/devices/registrations", body, null)
				.compose(res -> res.stringField("registrationId"))
				.map(id -> new DeviceRegistration(id, deviceKey, CryptoBox.KeyPair.random())));
	}

	/**
	 * Waits for the user to answer a registration request. The Director holds this call until the request
	 * is approved, denied or expired, so it may take minutes to complete. On approval the device is
	 * registered to the user, and receives the user key the approving device sealed to the request.
	 *
	 * @param registration the pending registration
	 * @return a future completing with the approval; it fails with {@link RegistrationDeniedException} if the
	 *         user denied the request, {@link RegistrationExpiredException} if it expired, or
	 *         {@link NotFoundException} if the Director has no such request
	 */
	public CompletableFuture<DeviceApproval> finishDeviceRegistration(DeviceRegistration registration) {
		transport.checkOpen();
		Objects.requireNonNull(registration, "registration");

		Map<String, @Nullable Object> body = signedByDevice(registration.deviceKey());
		Future<DeviceApproval> approval = transport.call(HttpMethod.POST,
						"/client" + DirectorClient.registrationPath(registration.getRegistrationId()), body, null,
						REGISTRATION_WAIT)
				.recover(e -> Future.failedFuture(registrationFailure(e)))
				.compose(res -> res.decode(content -> openApproval(registration, new JsonObject(content))));
		return transport.deliver(approval);
	}

	// The statuses the Director finishes a registration with mean other things elsewhere: typed here only.
	private static Throwable registrationFailure(Throwable e) {
		if (e instanceof DirectorException de && !(de instanceof NotFoundException)) {
			if (de.getStatus() == RegistrationDeniedException.STATUS)
				return new RegistrationDeniedException(de.getMessage());
			if (de.getStatus() == RegistrationExpiredException.STATUS)
				return new RegistrationExpiredException(de.getMessage());
		}
		return e;
	}

	// Opens the user key sealed to the registration, and checks it is the key of the user the Director
	// names: a key that is not would make the device act as someone else.
	private static DeviceApproval openApproval(DeviceRegistration registration, JsonObject json)
			throws CryptoException {
		Id userId = Id.of(requiredString(json, "userId"));
		byte[] sealed = Json.BASE64_DECODER.decode(requiredString(json, "userPrivateKey"));
		CryptoBox.KeyPair sealingKey = registration.sealingKey();
		byte[] privateKey = CryptoBox.decryptSealed(sealed, sealingKey.publicKey(), sealingKey.privateKey());
		Signature.KeyPair userKey = Signature.KeyPair.fromPrivateKey(privateKey);
		if (!Id.of(userKey.publicKey().bytes()).equals(userId))
			throw new IllegalArgumentException("the relayed key is not the key of user " + userId);

		return new DeviceApproval(userId, userKey);
	}

	// The device id with a fresh nonce signed by the device: how a device without an account proves it
	// holds its key.
	private static Map<String, @Nullable Object> signedByDevice(Signature.KeyPair deviceKey) {
		byte[] nonce = Random.randomBytes(NONCE_SIZE);
		Map<String, @Nullable Object> body = new LinkedHashMap<>();
		body.put("deviceId", Id.of(deviceKey.publicKey().bytes()));
		body.put("nonce", nonce);
		body.put("sig", deviceKey.privateKey().sign(nonce));
		return body;
	}

	/**
	 * Fluent builder for {@link DirectorGuest}. The Director URL is required. Not thread-safe.
	 */
	@NullUnmarked
	public static class Builder extends DirectorBuilder<Builder> {
		private Builder() {
		}

		/**
		 * Validates the configuration and builds the client.
		 *
		 * @return the client, ready to use
		 * @throws IllegalStateException if Vert.x or the Director URL is missing
		 */
		public DirectorGuest build() {
			try {
				return new DirectorGuest(this);
			} catch (NullPointerException | IllegalArgumentException e) {
				throw new IllegalStateException("Invalid DirectorGuest configuration: " + e.getMessage(), e);
			}
		}
	}
}
