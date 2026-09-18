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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An OAuth provider a Director signs users in with, as listed by {@link DirectorAuth#getProviders()}.
 * Immutable.
 */
public class AuthProvider {
	private final String id;
	private final String name;

	@JsonCreator
	AuthProvider(@JsonProperty(value = "id", required = true) String id,
			@JsonProperty(value = "name", required = true) String name) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
	}

	/**
	 * Returns the provider id, as {@link DirectorAuth#authorizeUrl(String, String)} takes it.
	 *
	 * @return the provider id, such as {@code github}
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the provider's display name.
	 *
	 * @return the provider name
	 */
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return "AuthProvider{id=" + id + ", name=" + name + "}";
	}
}
