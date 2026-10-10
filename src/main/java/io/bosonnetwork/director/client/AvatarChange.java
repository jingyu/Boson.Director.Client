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

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The outcome of changing the avatar: its address in the profile, if it has one now, and the profile's new
 * revision. Immutable.
 */
public final class AvatarChange {
	private final @Nullable String uri;
	private final long revision;

	AvatarChange(@Nullable String uri, long revision) {
		this.uri = uri;
		this.revision = revision;
	}

	/**
	 * Returns the avatar's URI now in the profile.
	 *
	 * @return the URI, or empty once the avatar is removed
	 */
	public Optional<String> getUri() {
		return Optional.ofNullable(uri);
	}

	/**
	 * Returns the profile's revision after the change.
	 *
	 * @return the revision
	 */
	public long getRevision() {
		return revision;
	}

	@Override
	public String toString() {
		return "AvatarChange{uri=" + uri + ", revision=" + revision + "}";
	}
}
