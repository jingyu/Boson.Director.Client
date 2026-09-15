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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The changes an administrator makes to a user account, for
 * {@link DirectorAdmin#updateUser(io.bosonnetwork.Id, UserUpdate)}.
 * <p>
 * Only the fields set here are changed; setting a nullable field to {@code null} clears it. Not
 * thread-safe.
 */
public class UserUpdate {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates an update that changes nothing yet.
	 */
	public UserUpdate() {
	}

	/**
	 * Sets the display name.
	 *
	 * @param name the new name, or {@code null} to clear it
	 * @return this update
	 */
	public UserUpdate name(@Nullable String name) {
		fields.put("userName", name);
		return this;
	}

	/**
	 * Sets the avatar URI.
	 *
	 * @param avatar the new avatar URI, or {@code null} to clear it
	 * @return this update
	 */
	public UserUpdate avatar(@Nullable String avatar) {
		fields.put("avatar", avatar);
		return this;
	}

	/**
	 * Sets the email address.
	 *
	 * @param email the new email address, or {@code null} to clear it
	 * @return this update
	 */
	public UserUpdate email(@Nullable String email) {
		fields.put("email", email);
		return this;
	}

	/**
	 * Sets the biography.
	 *
	 * @param bio the new biography, or {@code null} to clear it
	 * @return this update
	 */
	public UserUpdate bio(@Nullable String bio) {
		fields.put("bio", bio);
		return this;
	}

	/**
	 * Makes the user an administrator of the node, or no longer one.
	 *
	 * @param admin whether the user administers the node
	 * @return this update
	 */
	public UserUpdate admin(boolean admin) {
		fields.put("admin", admin);
		return this;
	}

	/**
	 * Replaces the account passphrase, without needing the current one. The admin API cannot remove a
	 * passphrase; only the user can.
	 *
	 * @param passphrase the new passphrase
	 * @return this update
	 * @throws IllegalArgumentException if the passphrase is empty
	 */
	public UserUpdate passphrase(String passphrase) {
		Objects.requireNonNull(passphrase, "passphrase");
		if (passphrase.isEmpty())
			throw new IllegalArgumentException("passphrase is empty");
		fields.put("passphrase", passphrase);
		return this;
	}

	/**
	 * Tells whether the update changes nothing.
	 *
	 * @return {@code true} if no field is set
	 */
	public boolean isEmpty() {
		return fields.isEmpty();
	}

	// The fields to send, keyed by their wire names.
	Map<String, @Nullable Object> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
