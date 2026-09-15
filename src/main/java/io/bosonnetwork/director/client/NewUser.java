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

import io.bosonnetwork.Id;

/**
 * A user account for an administrator to create, for {@link DirectorAdmin#addUser(NewUser)}.
 * <p>
 * The Director requires an account created this way to have a passphrase; the other details are
 * optional. For example:
 * <pre>{@code
 * admin.addUser(new NewUser(userId, "initial-passphrase").name("Bob").email("bob@example.com"));
 * }</pre>
 * Not thread-safe.
 */
public class NewUser {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates an account with no details beyond its identity and passphrase.
	 *
	 * @param userId     the user id
	 * @param passphrase the account passphrase
	 * @throws IllegalArgumentException if the passphrase is empty
	 */
	public NewUser(Id userId, String passphrase) {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(passphrase, "passphrase");
		if (passphrase.isEmpty())
			throw new IllegalArgumentException("passphrase is empty");

		fields.put("userId", userId);
		fields.put("passphrase", passphrase);
	}

	/**
	 * Sets the display name.
	 *
	 * @param name the name, or {@code null} for none
	 * @return this account
	 */
	public NewUser name(@Nullable String name) {
		fields.put("userName", name);
		return this;
	}

	/**
	 * Sets the email address.
	 *
	 * @param email the email address, or {@code null} for none
	 * @return this account
	 */
	public NewUser email(@Nullable String email) {
		fields.put("email", email);
		return this;
	}

	/**
	 * Sets the biography.
	 *
	 * @param bio the biography, or {@code null} for none
	 * @return this account
	 */
	public NewUser bio(@Nullable String bio) {
		fields.put("bio", bio);
		return this;
	}

	/**
	 * Makes the user an administrator of the node, or not; by default the user is not.
	 *
	 * @param admin whether the user administers the node
	 * @return this account
	 */
	public NewUser admin(boolean admin) {
		fields.put("admin", admin);
		return this;
	}

	// The fields to send, keyed by their wire names.
	Map<String, @Nullable Object> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
