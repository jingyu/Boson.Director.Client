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

/**
 * Something that happened to the user's account, as {@link DirectorClient#listSecurityEvents()} lists it:
 * a passphrase set, changed, removed or reset, recovery codes made, a device added or removed, a sign-in
 * linked or unlinked, or a web page signed in by the app. Immutable.
 */
public class SecurityEvent {
	private final String kind;
	private final String method;
	private final long at;
	private final @Nullable String address;

	@JsonCreator
	SecurityEvent(@JsonProperty(value = "kind", required = true) String kind,
			@JsonProperty("method") @Nullable String method,
			@JsonProperty("at") long at,
			@JsonProperty("address") @Nullable String address) {
		this.kind = Objects.requireNonNull(kind, "kind");
		this.method = method != null ? method : "";
		this.at = at;
		this.address = address;
	}

	/**
	 * Returns what happened: {@code set}, {@code changed}, {@code removed}, {@code reset} or
	 * {@code recovery-codes} for the passphrase; {@code device-added} or {@code device-removed};
	 * {@code linked} or {@code unlinked} for an OAuth sign-in; {@code signed-in} for a web page.
	 *
	 * @return the kind of event
	 */
	public String getKind() {
		return kind;
	}

	/**
	 * Returns how, or with what: the reset method, the device ({@code kind: name}), the provider, or the
	 * web app signed in to.
	 *
	 * @return the method, possibly empty
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * Returns when it happened.
	 *
	 * @return the time, in epoch milliseconds
	 */
	public long getAt() {
		return at;
	}

	/**
	 * Returns the address it came from.
	 *
	 * @return the address, if known
	 */
	public Optional<String> getAddress() {
		return Optional.ofNullable(address);
	}

	@Override
	public String toString() {
		return "SecurityEvent{kind=" + kind + ", method=" + method + ", at=" + at + "}";
	}
}
