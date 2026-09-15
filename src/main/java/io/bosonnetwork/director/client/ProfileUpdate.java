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

import org.jspecify.annotations.Nullable;

/**
 * The changes to make to a user's profile, for {@link DirectorClient#updateProfile(ProfileUpdate)}.
 * <p>
 * Only the fields set here are changed; setting a field to {@code null} clears it. For example:
 * <pre>{@code
 * director.updateProfile(new ProfileUpdate().name("Alice").bio(null));
 * }</pre>
 * Not thread-safe.
 */
public class ProfileUpdate {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates an update that changes nothing yet.
	 */
	public ProfileUpdate() {
	}

	/**
	 * Sets the display name.
	 *
	 * @param name the new name, or {@code null} to clear it
	 * @return this update
	 */
	public ProfileUpdate name(@Nullable String name) {
		fields.put("name", name);
		return this;
	}

	/**
	 * Sets the email address.
	 *
	 * @param email the new email address, or {@code null} to clear it
	 * @return this update
	 */
	public ProfileUpdate email(@Nullable String email) {
		fields.put("email", email);
		return this;
	}

	/**
	 * Sets the biography.
	 *
	 * @param bio the new biography, or {@code null} to clear it
	 * @return this update
	 */
	public ProfileUpdate bio(@Nullable String bio) {
		fields.put("bio", bio);
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
	Map<String, ?> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
