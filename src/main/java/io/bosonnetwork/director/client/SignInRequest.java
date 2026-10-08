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
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.utils.BosonString;

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

	/** The Boson string namespace of a sign-in code. */
	public static final String CODE_NAMESPACE = "signin";
	/** The version of the sign-in code. */
	public static final int CODE_VERSION = 1;

	private static final Pattern BASE58 = Pattern.compile("[1-9A-HJ-NP-Za-km-z]+");

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
	 * The QR code text a page shows for its request: the Boson string {@code boson:signin:1:<requestId>},
	 * the request id in Base58.
	 *
	 * @param requestId the request id
	 * @return the code
	 * @throws IllegalArgumentException if the request id is not Base58
	 */
	public static String code(String requestId) {
		Objects.requireNonNull(requestId, "requestId");
		if (!BASE58.matcher(requestId).matches())
			throw new IllegalArgumentException("Invalid request id: " + requestId);
		return BosonString.format(CODE_NAMESPACE, CODE_VERSION, requestId);
	}

	/**
	 * Reads the request id out of a scanned code.
	 *
	 * @param text the scanned text
	 * @return the request id, or empty if the text is not a sign-in code
	 */
	public static Optional<String> parseCode(String text) {
		return BosonString.parse(Objects.requireNonNull(text, "text"), CODE_NAMESPACE, CODE_VERSION, 1)
				.map(code -> code.field(0))
				.filter(id -> BASE58.matcher(id).matches());
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
