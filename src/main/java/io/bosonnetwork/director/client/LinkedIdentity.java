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

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * An OAuth sign-in linked to a user's account, as listed by {@link DirectorOAuth#listIdentities()}: one
 * provider account through which the user signs in. Immutable.
 */
public class LinkedIdentity {
	private final Id sessionId;
	private final @Nullable String provider;
	private final @Nullable String name;
	private final @Nullable String email;
	private final boolean emailVerified;
	private final @Nullable String avatar;
	private final long createdAt;

	@JsonCreator
	LinkedIdentity(@JsonProperty(value = "sessionId", required = true) Id sessionId,
			@JsonProperty("provider") @Nullable String provider,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty("email") @Nullable String email,
			@JsonProperty("emailVerified") boolean emailVerified,
			@JsonProperty("avatar") @Nullable String avatar,
			@JsonProperty("createdAt") long createdAt) {
		this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
		this.provider = provider;
		this.name = name;
		this.email = email;
		this.emailVerified = emailVerified;
		this.avatar = avatar;
		this.createdAt = createdAt;
	}

	/**
	 * Returns the id of the sign-in, which {@link DirectorOAuth#disconnectIdentity(Id)} takes.
	 *
	 * @return the session id
	 */
	public Id getSessionId() {
		return sessionId;
	}

	/**
	 * Returns the id of the provider.
	 *
	 * @return the provider id, or empty if the Director did not report it
	 */
	public Optional<String> getProvider() {
		return Optional.ofNullable(provider);
	}

	/**
	 * Returns the name the provider reported.
	 *
	 * @return the name, or empty if the provider reported none
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the email address the provider reported.
	 *
	 * @return the email address, or empty if the provider reported none
	 */
	public Optional<String> getEmail() {
		return Optional.ofNullable(email);
	}

	/**
	 * Tells whether the provider verified the email address.
	 *
	 * @return {@code true} if the email address is verified
	 */
	public boolean isEmailVerified() {
		return emailVerified;
	}

	/**
	 * Returns the URL of the picture the provider reported.
	 *
	 * @return the picture URL, or empty if the provider reported none
	 */
	public Optional<String> getAvatar() {
		return Optional.ofNullable(avatar);
	}

	/**
	 * Returns when the sign-in was first linked.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	@Override
	public String toString() {
		return "LinkedIdentity{sessionId=" + sessionId + ", provider=" + provider + "}";
	}
}
