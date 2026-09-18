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

import static io.bosonnetwork.director.client.DirectorClient.checkRegistrationId;
import static io.bosonnetwork.director.client.DirectorClient.registrationPath;
import static io.bosonnetwork.director.client.DirectorTransport.encode;
import static io.bosonnetwork.director.client.DirectorTransport.requiredString;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
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
import io.bosonnetwork.crypto.Random;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.director.client.exceptions.NotFoundException;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.vertx.ContextualFuture;

/**
 * An asynchronous client for what comes before an app can act as a user of a Boson Director: finding
 * out how the node signs users up, signing in through OAuth and binding a Boson identity to that
 * sign-in, and joining an existing account from a new device.
 * <p>
 * Once the app holds a key to act with - the user key, or the key of a device registered to the
 * user - it uses a {@link DirectorClient} instead.
 *
 * <h2>OAuth</h2>
 * The OAuth sign-in happens in a browser: open {@link #authorizeUrl(String, String)}, and the Director
 * redirects to the given URI with the session token in its {@code token} query parameter (or an
 * {@code error} parameter). The session calls here take that token. A fresh session is not bound to
 * a Boson user; {@link #bindUserIdentity(String, Signature.KeyPair)} binds one, proving the app holds
 * the user's key. The app then acts as that user with a {@link DirectorClient}, and needs the session
 * token no more.
 *
 * <h2>Joining an account from a new device</h2>
 * The new device, which holds only its own key, asks to join with
 * {@link #requestDeviceRegistration(Signature.KeyPair, String, String)} and passes the registration
 * id to a device already registered to the user - typically in a QR code, together with a key to seal
 * the user key to. That device approves with
 * {@link DirectorClient#approveDeviceRegistration(String, byte[], String)}, and the new device, waiting
 * in {@link #finishDeviceRegistration(Signature.KeyPair, String)}, receives the user key it was given.
 *
 * <h2>Transport security, errors and threading</h2>
 * As for {@link DirectorClient}: the node id, when configured, pins a self-signed certificate; a
 * refused call fails with a {@link DirectorException} carrying the HTTP status; invalid arguments throw
 * at once; a call made on a Vert.x context completes on it. Call {@link #close()} when done.
 */
public class DirectorAuth {
	// Size of the random nonce a device signs to prove it holds its key.
	private static final int NONCE_SIZE = 32;

	// The scope of the sessions this client signs in to: the client API.
	private static final String OAUTH_SCOPE = "client";

	// How long finishDeviceRegistration waits for the user to answer. The Director holds the request
	// until the registration is approved, denied or expired - three minutes after it was made - so this
	// only has to outlast that.
	private static final long REGISTRATION_WAIT = TimeUnit.MINUTES.toMillis(4);

	/**
	 * The HTTP status {@link #finishDeviceRegistration(Signature.KeyPair, String)} fails with when the
	 * user denied the registration.
	 */
	public static final int REGISTRATION_DENIED = 412;

	/**
	 * The HTTP status {@link #finishDeviceRegistration(Signature.KeyPair, String)} fails with when the
	 * registration expired before the user answered it.
	 */
	public static final int REGISTRATION_EXPIRED = 408;

	private final URL directorUrl;
	private final DirectorTransport transport;

	private static final Logger log = LoggerFactory.getLogger(DirectorAuth.class);

	private DirectorAuth(Builder builder) {
		Vertx vertx = Objects.requireNonNull(builder.vertx, "Vert.x instance must be set");
		this.directorUrl = Objects.requireNonNull(builder.directorUrl, "directorUrl must be set");
		// Spans two APIs, the sign-in (/auth) and the client one (/client): paths name their API.
		this.transport = new DirectorTransport(vertx, directorUrl, "", builder.nodeId, builder.resolveToAddress, log);
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

	// ---- Sign-up -------------------------------------------------------------------------------

	/**
	 * Tells whether the node accepts permissionless registration, proven with proof-of-work - the
	 * registration {@link DirectorClient#registerUser(UserRegistration)} makes. A node that does not
	 * registers users through OAuth only.
	 * <p>
	 * It asks the Director for a registration challenge, and discards it.
	 *
	 * @return a future completing with {@code true} if proof-of-work registration is accepted
	 */
	public CompletableFuture<Boolean> isProofOfWorkRegistrationEnabled() {
		transport.checkOpen();
		return ContextualFuture.of(transport.call(HttpMethod.GET, "/client/users/challenge", null, null)
				.map(res -> true)
				.recover(e -> e instanceof NotFoundException ? Future.succeededFuture(false) : Future.failedFuture(e)));
	}

	/**
	 * Lists the OAuth providers the node signs users in with. Empty when the node has none configured.
	 *
	 * @return a future completing with the providers
	 */
	public CompletableFuture<List<AuthProvider>> getProviders() {
		transport.checkOpen();
		return ContextualFuture.of(transport.call(HttpMethod.GET, "/auth/providers", null, null)
				.compose(res -> res.jsonList(AuthProvider.class)));
	}

	// ---- OAuth ---------------------------------------------------------------------------------

	/**
	 * Returns the URL that starts an OAuth sign-in with a provider, to open in a browser. After the
	 * provider signs the user in, the Director redirects the browser to {@code redirectUri}, adding
	 * the session token as the {@code token} query parameter, or an {@code error} parameter if the
	 * sign-in failed. Makes no request.
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

	/**
	 * Gets an OAuth session: who signed in, and the Boson user bound to it, if any yet.
	 *
	 * @param sessionToken the session token the sign-in redirected with
	 * @return a future completing with the session; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.UnauthorizedException} if the token is
	 *         not valid
	 */
	public CompletableFuture<AuthSession> getSession(String sessionToken) {
		transport.checkOpen();
		DirectorTransport.TokenSource tokens = sessionTokens(sessionToken);
		return ContextualFuture.of(transport.call(HttpMethod.GET, "/auth/me", null, tokens)
				.compose(res -> res.json(AuthSession.class)));
	}

	/**
	 * Binds a Boson user to an OAuth session that has none, proving the app holds the user's key. A user
	 * the node does not know yet is created; a known one gains this sign-in as a way in. Either way the
	 * app can then act as the user with a {@link DirectorClient} built with the same key.
	 *
	 * @param sessionToken the session token the sign-in redirected with
	 * @param userKey the key pair of the user to bind; only signatures leave the device
	 * @return a future completing with the id of the bound user; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the session is
	 *         already bound to a user
	 */
	public CompletableFuture<Id> bindUserIdentity(String sessionToken, Signature.KeyPair userKey) {
		transport.checkOpen();
		Objects.requireNonNull(userKey, "userKey");
		DirectorTransport.TokenSource tokens = sessionTokens(sessionToken);

		Future<Id> bound = transport.call(HttpMethod.GET, "/auth/user-identity/nonce", null, tokens)
				.compose(res -> res.stringField("nonce"))
				.compose(nonce -> {
					Map<String, @Nullable Object> body = new LinkedHashMap<>();
					// This endpoint takes base58 for both, unlike the byte arrays elsewhere in the API.
					body.put("publicKey", Base58.encode(userKey.publicKey().bytes()));
					body.put("signature", Base58.encode(userKey.privateKey().sign(Base58.decode(nonce))));
					return transport.call(HttpMethod.PUT, "/auth/user-identity", body, tokens);
				})
				// The answer also carries a new session token, bound to the user; the app acts as the user
				// with its key from here on, so it is not needed.
				.compose(res -> res.idField("userId"));
		return ContextualFuture.of(bound);
	}

	// A session token is the Director's to issue, so there is nothing to renew: a rejection is final.
	private static DirectorTransport.TokenSource sessionTokens(String sessionToken) {
		Objects.requireNonNull(sessionToken, "sessionToken");
		if (sessionToken.isEmpty())
			throw new IllegalArgumentException("sessionToken is empty");

		return new DirectorTransport.TokenSource() {
			@Override
			public Future<String> token() {
				return Future.succeededFuture(sessionToken);
			}

			@Override
			public boolean rejected(String token, DirectorTransport.Response response) {
				return false;
			}
		};
	}

	// ---- Joining an account from a new device --------------------------------------------------

	/**
	 * Asks to register a new device to an existing account. The request names no user: the user who
	 * approves it decides which account the device joins. Pass the returned registration id to a device
	 * already registered to the user, which approves or denies it; then wait for the answer with
	 * {@link #finishDeviceRegistration(Signature.KeyPair, String)}. The request expires after three
	 * minutes.
	 *
	 * @param deviceKey the key pair of the new device; it signs the request and is not sent
	 * @param deviceName a name for the device, shown to the user
	 * @param appName the name of the app the device runs
	 * @return a future completing with the registration id; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the device is
	 *         already registered
	 */
	public CompletableFuture<String> requestDeviceRegistration(Signature.KeyPair deviceKey, String deviceName,
			String appName) {
		transport.checkOpen();
		Objects.requireNonNull(deviceKey, "deviceKey");
		Objects.requireNonNull(deviceName, "deviceName");
		Objects.requireNonNull(appName, "appName");

		Map<String, @Nullable Object> body = signedByDevice(deviceKey);
		body.put("deviceName", deviceName);
		body.put("appName", appName);
		return ContextualFuture.of(transport.call(HttpMethod.POST, "/client/devices/registrations", body, null)
				.compose(res -> res.stringField("registrationId")));
	}

	/**
	 * Waits for the user to answer a registration request made with
	 * {@link #requestDeviceRegistration(Signature.KeyPair, String, String)}. The Director holds this call
	 * until the request is approved, denied or expired, so it may take minutes to complete. On approval
	 * the device is registered to the user, and receives the user key the approving device handed over.
	 *
	 * @param deviceKey the key pair of the device that made the request
	 * @param registrationId the id of the registration request
	 * @return a future completing with the approval; it fails with a {@link DirectorException} of status
	 *         {@link #REGISTRATION_DENIED} if the user denied the request, {@link #REGISTRATION_EXPIRED}
	 *         if it expired, or with {@link NotFoundException} if there is no such request
	 */
	public CompletableFuture<DeviceApproval> finishDeviceRegistration(Signature.KeyPair deviceKey,
			String registrationId) {
		transport.checkOpen();
		Objects.requireNonNull(deviceKey, "deviceKey");
		checkRegistrationId(registrationId);

		Map<String, @Nullable Object> body = signedByDevice(deviceKey);
		return ContextualFuture.of(transport.call(HttpMethod.POST, "/client" + registrationPath(registrationId),
						body, null, REGISTRATION_WAIT)
				.compose(res -> res.decode(content -> {
					JsonObject json = new JsonObject(content);
					return new DeviceApproval(Id.of(requiredString(json, "userId")),
							Json.BASE64_DECODER.decode(requiredString(json, "userPrivateKey")));
				})));
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
	 * Fluent builder for {@link DirectorAuth}. The Director URL is required. Not thread-safe.
	 */
	@NullUnmarked
	public static class Builder {
		private Vertx vertx;
		private URL directorUrl;
		private Id nodeId;
		private InetSocketAddress resolveToAddress;

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
		 * Sets the Boson id of the super node the Director runs on (optional). Over HTTPS, a
		 * self-signed Director certificate pinned to it is accepted as well as a CA-signed one.
		 *
		 * @param nodeId the super node id
		 * @return this builder
		 */
		public Builder nodeId(Id nodeId) {
			this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
			return this;
		}

		/**
		 * Sets the address to connect to instead of looking up the Director URL's host name (optional).
		 * See {@link DirectorClient.Builder#resolveToAddress(InetSocketAddress)}.
		 *
		 * @param address the address to connect to, resolved
		 * @return this builder
		 * @throws IllegalArgumentException if the address is unresolved
		 */
		public Builder resolveToAddress(InetSocketAddress address) {
			Objects.requireNonNull(address, "address");
			if (address.isUnresolved())
				throw new IllegalArgumentException("Unresolved address: " + address);
			this.resolveToAddress = address;
			return this;
		}

		/**
		 * Validates the configuration and builds the client.
		 *
		 * @return the client, ready to use
		 * @throws IllegalStateException if Vert.x or the Director URL is missing
		 */
		public DirectorAuth build() {
			try {
				return new DirectorAuth(this);
			} catch (NullPointerException | IllegalArgumentException e) {
				throw new IllegalStateException("Invalid DirectorAuth configuration: " + e.getMessage(), e);
			}
		}
	}
}
