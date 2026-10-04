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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * A web page asking to be signed in by the user's app ("Sign in with Boson Identity"), as the app sees it
 * with {@link DirectorClient#getSignInRequest(String)}. The page shows its request as a QR code
 * ({@link #code(String)}) and a two-digit number; the user approves by picking that number out of
 * {@link #getChoices()}. Immutable.
 */
public class SignInRequest {
	/** The {@linkplain #getApp() app} of a request to sign in to the user portal. */
	public static final String APP_PORTAL = "portal";
	/** The {@linkplain #getApp() app} of a request to sign in to the admin dashboard. */
	public static final String APP_ADMIN = "admin";

	private static final String CODE_PREFIX = "bosonsignin:1:";

	private final String requestId;
	private final String app;
	private final List<Integer> choices;
	private final @Nullable String requestedFrom;
	private final long createdAt;
	private final long expiresAt;

	@JsonCreator
	SignInRequest(@JsonProperty(value = "requestId", required = true) String requestId,
			@JsonProperty(value = "app", required = true) String app,
			@JsonProperty(value = "choices", required = true) List<Integer> choices,
			@JsonProperty("requestedFrom") @Nullable String requestedFrom,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("expiresAt") long expiresAt) {
		this.requestId = Objects.requireNonNull(requestId, "requestId");
		this.app = Objects.requireNonNull(app, "app");
		this.choices = List.copyOf(choices);
		this.requestedFrom = requestedFrom;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/**
	 * The QR code text a page shows for its request.
	 *
	 * @param requestId the request id
	 * @return the code
	 */
	public static String code(String requestId) {
		return CODE_PREFIX + Objects.requireNonNull(requestId, "requestId");
	}

	/**
	 * Reads the request id out of a scanned code.
	 *
	 * @param text the scanned text
	 * @return the request id, or empty if the text is not a sign-in code
	 */
	public static Optional<String> parseCode(String text) {
		String trimmed = Objects.requireNonNull(text, "text").trim();
		if (!trimmed.startsWith(CODE_PREFIX))
			return Optional.empty();
		String id = trimmed.substring(CODE_PREFIX.length());
		return id.isEmpty() || id.indexOf(':') >= 0 || id.indexOf('/') >= 0 ? Optional.empty() : Optional.of(id);
	}

	/**
	 * Returns the request id.
	 *
	 * @return the request id
	 */
	public String getRequestId() {
		return requestId;
	}

	/**
	 * Returns what the page signs in to: {@link #APP_PORTAL} or {@link #APP_ADMIN}.
	 *
	 * @return the app
	 */
	public String getApp() {
		return app;
	}

	/**
	 * Returns the numbers the user chooses from; one of them is the number the page shows.
	 *
	 * @return three two-digit numbers
	 */
	public List<Integer> getChoices() {
		return choices;
	}

	/**
	 * Returns the address the page's request came from, as the Director saw it.
	 *
	 * @return the address, if known
	 */
	public Optional<String> getRequestedFrom() {
		return Optional.ofNullable(requestedFrom);
	}

	/**
	 * Returns when the request was made.
	 *
	 * @return the time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the request expires unanswered.
	 *
	 * @return the time, in epoch milliseconds
	 */
	public long getExpiresAt() {
		return expiresAt;
	}

	@Override
	public String toString() {
		return "SignInRequest{requestId=" + requestId + ", app=" + app + "}";
	}
}
