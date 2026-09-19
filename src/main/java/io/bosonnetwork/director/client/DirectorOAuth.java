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

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.utils.Base58;

/**
 * An asynchronous client for one OAuth sign-in session on a Boson Director.
 * <p>
 * The sign-in starts in a browser at {@link DirectorGuest#authorizeUrl(String, String)}; the Director
 * redirects back with a session token, which this client is built with. A fresh session is not bound to a
 * Boson user: {@link #bindUserIdentity(Signature.KeyPair)} binds one, proving the app holds the user's
 * key. From then on the app can act as that user with a {@link DirectorClient} built with the same key,
 * and needs the session only for what concerns the sign-in itself: its {@linkplain #listIdentities()
 * linked identities}, {@linkplain #refresh() renewal} and {@linkplain #signOut() sign-out}.
 * <p>
 * The session token is the Director's, not this client's: binding and refreshing replace it with the one
 * the Director returns, and {@link #getSessionToken()} gives the current one, for the app to keep.
 * Errors, threading and transport security are as for {@link DirectorClient}. Call {@link #close()} when
 * done.
 */
public class DirectorOAuth {
	private final URL directorUrl;
	private final DirectorTransport transport;
	private volatile String sessionToken;

	private static final Logger log = LoggerFactory.getLogger(DirectorOAuth.class);

	private DirectorOAuth(Builder builder) {
		Vertx vertx = Objects.requireNonNull(builder.vertx, "Vert.x instance must be set");
		this.directorUrl = Objects.requireNonNull(builder.directorUrl, "directorUrl must be set");
		this.sessionToken = Objects.requireNonNull(builder.sessionToken, "sessionToken must be set");
		this.transport = new DirectorTransport(vertx, directorUrl, "/auth", builder.nodeId, builder.resolveToAddress, builder.callbackExecutor, log);
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
	 * Returns the current session token: the one the client was built with, or the one the Director
	 * returned when the session was last bound or refreshed.
	 *
	 * @return the session token
	 */
	public String getSessionToken() {
		return sessionToken;
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

	/**
	 * Gets the session: who signed in, and the Boson user bound to it, if any yet.
	 *
	 * @return a future completing with the session; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.UnauthorizedException} if the session token
	 *         is not valid
	 */
	public CompletableFuture<AuthSession> getSession() {
		transport.checkOpen();
		return transport.deliver(call(HttpMethod.GET, "/me", null).compose(res -> res.json(AuthSession.class)));
	}

	/**
	 * Binds a Boson user to the session, which must have none, proving the app holds the user's key. A user
	 * the node does not know yet is created; a known one gains this sign-in as a way in. Either way the app
	 * can then act as the user with a {@link DirectorClient} built with the same key. The session token is
	 * replaced with the one the Director issues for the bound session.
	 *
	 * @param userKey the key pair of the user to bind; only signatures leave the device
	 * @return a future completing with the id of the bound user; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.ConflictException} if the session is already
	 *         bound to a user
	 */
	public CompletableFuture<Id> bindUserIdentity(Signature.KeyPair userKey) {
		transport.checkOpen();
		Objects.requireNonNull(userKey, "userKey");

		Future<Id> bound = call(HttpMethod.GET, "/user-identity/nonce", null)
				.compose(res -> res.stringField("nonce"))
				.compose(nonce -> {
					Map<String, @Nullable Object> body = new LinkedHashMap<>();
					// This endpoint takes base58 for both, unlike the byte arrays elsewhere in the API.
					body.put("publicKey", Base58.encode(userKey.publicKey().bytes()));
					body.put("signature", Base58.encode(userKey.privateKey().sign(Base58.decode(nonce))));
					return call(HttpMethod.PUT, "/user-identity", body);
				})
				.compose(res -> res.json(BoundSession.class))
				.map(session -> {
					sessionToken = session.token;
					return session.userId;
				});
		return transport.deliver(bound);
	}

	/**
	 * Renews the session: the Director issues a new session token, which replaces the current one.
	 *
	 * @return a future completing with the new session token
	 */
	public CompletableFuture<String> refresh() {
		transport.checkOpen();
		return transport.deliver(call(HttpMethod.POST, "/refresh", null)
				.compose(res -> res.stringField("token"))
				.map(token -> {
					sessionToken = token;
					return token;
				}));
	}

	/**
	 * Lists the OAuth sign-ins linked to the user bound to this session, this one included.
	 *
	 * @return a future completing with the linked identities; it fails with
	 *         {@link io.bosonnetwork.director.client.exceptions.NotFoundException} if no user is bound to the
	 *         session
	 */
	public CompletableFuture<List<LinkedIdentity>> listIdentities() {
		transport.checkOpen();
		return transport.deliver(call(HttpMethod.GET, "/identities", null)
				.compose(res -> res.json(Identities.class))
				.map(identities -> identities.identities));
	}

	/**
	 * Unlinks one of the OAuth sign-ins of the user bound to this session.
	 *
	 * @param sessionId the id of the sign-in to unlink, as {@link #listIdentities()} lists it
	 * @return a future completing when the sign-in is unlinked
	 */
	public CompletableFuture<Void> disconnectIdentity(Id sessionId) {
		transport.checkOpen();
		Objects.requireNonNull(sessionId, "sessionId");
		return transport.deliver(call(HttpMethod.DELETE, "/identities/" + sessionId.toBase58String(), null)
				.<Void>mapEmpty());
	}

	/**
	 * Signs out of the session. The Director's session tokens are self-contained, so this does not revoke
	 * the token; drop it. The client is still open afterwards.
	 *
	 * @return a future completing when the Director has acknowledged the sign-out
	 */
	public CompletableFuture<Void> signOut() {
		transport.checkOpen();
		return transport.deliver(call(HttpMethod.DELETE, "/session", null).<Void>mapEmpty());
	}

	private Future<DirectorTransport.Response> call(HttpMethod method, String path, @Nullable Map<String, ?> body) {
		return transport.call(method, path, body, tokens);
	}

	// Always the current token; it is the Director's to issue, so a rejection is final.
	private final DirectorTransport.TokenSource tokens = new DirectorTransport.TokenSource() {
		@Override
		public Future<String> token() {
			return Future.succeededFuture(sessionToken);
		}

		@Override
		public boolean rejected(String token, DirectorTransport.Response response) {
			return false;
		}
	};

	// The answer to binding a user to the session.
	private static final class BoundSession {
		private final Id userId;
		private final String token;

		@JsonCreator
		BoundSession(@JsonProperty(value = "userId", required = true) Id userId,
				@JsonProperty(value = "token", required = true) String token) {
			this.userId = userId;
			this.token = token;
		}
	}

	// The answer to listing the identities.
	private static final class Identities {
		private final List<LinkedIdentity> identities;

		@JsonCreator
		Identities(@JsonProperty(value = "identities", required = true) List<LinkedIdentity> identities) {
			this.identities = List.copyOf(identities);
		}
	}

	/**
	 * Fluent builder for {@link DirectorOAuth}. The Director URL and the session token are required. Not
	 * thread-safe.
	 */
	@NullUnmarked
	public static class Builder extends DirectorBuilder<Builder> {
		private String sessionToken;

		private Builder() {
		}

		/**
		 * Sets the session token (required): the {@code token} query parameter the Director redirected the
		 * sign-in with, or a token {@link #getSessionToken()} returned earlier.
		 *
		 * @param sessionToken the session token
		 * @return this builder
		 * @throws IllegalArgumentException if the token is empty
		 */
		public Builder sessionToken(String sessionToken) {
			Objects.requireNonNull(sessionToken, "sessionToken");
			if (sessionToken.isEmpty())
				throw new IllegalArgumentException("sessionToken is empty");
			this.sessionToken = sessionToken;
			return this;
		}

		/**
		 * Validates the configuration and builds the client.
		 *
		 * @return the client, ready to use
		 * @throws IllegalStateException if Vert.x, the Director URL or the session token is missing
		 */
		public DirectorOAuth build() {
			try {
				return new DirectorOAuth(this);
			} catch (NullPointerException | IllegalArgumentException e) {
				throw new IllegalStateException("Invalid DirectorOAuth configuration: " + e.getMessage(), e);
			}
		}
	}
}
