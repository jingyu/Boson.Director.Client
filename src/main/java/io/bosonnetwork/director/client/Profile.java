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
 * A user account as the Director records it.
 * <p>
 * A {@link DirectorClient} obtains the profile of the user it acts as with
 * {@link DirectorClient#getProfile()}, and changes it with
 * {@link DirectorClient#updateProfile(ProfileUpdate)}. A {@link DirectorAdmin} lists, looks up and
 * changes every user's account. Immutable.
 */
public class Profile {
	private final Id id;
	private final boolean admin;
	private final @Nullable String name;
	private final @Nullable String avatar;
	private final @Nullable String email;
	private final @Nullable String bio;
	private final long createdAt;
	private final long updatedAt;
	private final String planName;
	private final boolean passphraseProtected;

	@JsonCreator
	Profile(@JsonProperty(value = "id", required = true) Id id,
			@JsonProperty("admin") boolean admin,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty("avatar") @Nullable String avatar,
			@JsonProperty("email") @Nullable String email,
			@JsonProperty("bio") @Nullable String bio,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt,
			@JsonProperty(value = "planName", required = true) String planName,
			@JsonProperty("passphraseProtected") boolean passphraseProtected) {
		this.id = Objects.requireNonNull(id, "id");
		this.admin = admin;
		this.name = name;
		this.avatar = avatar;
		this.email = email;
		this.bio = bio;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.planName = Objects.requireNonNull(planName, "planName");
		this.passphraseProtected = passphraseProtected;
	}

	/**
	 * Returns the user id.
	 *
	 * @return the user id
	 */
	public Id getId() {
		return id;
	}

	/**
	 * Tells whether the user administers the node.
	 *
	 * @return {@code true} for an administrator
	 */
	public boolean isAdmin() {
		return admin;
	}

	/**
	 * Returns the display name.
	 *
	 * @return the name, if set
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the avatar URI, a {@code bnr://} URI naming the node that hosts the avatar. Download
	 * the image itself with {@link DirectorClient#getAvatar()}.
	 *
	 * @return the avatar URI, if the user has an avatar
	 */
	public Optional<String> getAvatar() {
		return Optional.ofNullable(avatar);
	}

	/**
	 * Returns the email address.
	 *
	 * @return the email address, if set
	 */
	public Optional<String> getEmail() {
		return Optional.ofNullable(email);
	}

	/**
	 * Returns the biography.
	 *
	 * @return the biography, if set
	 */
	public Optional<String> getBio() {
		return Optional.ofNullable(bio);
	}

	/**
	 * Returns when the account was created.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the profile was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Returns the name of the user's current plan. See {@link DirectorClient#getPlan()} for its
	 * details.
	 *
	 * @return the plan name
	 */
	public String getPlanName() {
		return planName;
	}

	/**
	 * Tells whether the account has a passphrase, which the gated operations then require.
	 *
	 * @return {@code true} if a passphrase is set
	 */
	public boolean isPassphraseProtected() {
		return passphraseProtected;
	}

	@Override
	public String toString() {
		return "Profile{id=" + id + ", name=" + name + ", email=" + email + ", plan=" + planName +
				", admin=" + admin + ", passphraseProtected=" + passphraseProtected + "}";
	}
}
