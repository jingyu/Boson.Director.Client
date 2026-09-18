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

import org.jspecify.annotations.Nullable;

/**
 * A user's avatar image, as downloaded by {@link DirectorClient#getAvatar()} or
 * {@link DirectorClient#getUserAvatar(io.bosonnetwork.Id)}. Immutable, apart from the data array.
 * <p>
 * An avatar carries the validators the Director sent with it - its entity tag and last-modified
 * time - so that a copy kept by the caller can later be checked for changes without downloading it
 * again: see {@link DirectorClient#getUserAvatar(io.bosonnetwork.Id, Avatar)}. A caller that keeps
 * avatars across restarts stores the content type, the data and both validators, and restores the copy
 * with {@link #of(String, byte[], String, String)}.
 */
public class Avatar {
	private final String contentType;
	private final byte[] data;
	private final @Nullable String eTag;
	private final @Nullable String lastModified;

	Avatar(String contentType, byte[] data, @Nullable String eTag, @Nullable String lastModified) {
		this.contentType = Objects.requireNonNull(contentType, "contentType");
		this.data = Objects.requireNonNull(data, "data");
		this.eTag = eTag;
		this.lastModified = lastModified;
	}

	/**
	 * Restores an avatar the caller kept, with the validators it was downloaded with.
	 *
	 * @param contentType the image type, as {@link #getContentType()} returned it
	 * @param data the image data; not copied
	 * @param eTag the entity tag, as {@link #getETag()} returned it, or {@code null}
	 * @param lastModified the last-modified time, as {@link #getLastModified()} returned it, or
	 *        {@code null}
	 * @return the avatar
	 */
	public static Avatar of(String contentType, byte[] data, @Nullable String eTag, @Nullable String lastModified) {
		return new Avatar(contentType, data, eTag, lastModified);
	}

	/**
	 * Returns the image type, such as {@code image/png} or {@code image/jpeg}.
	 *
	 * @return the content type
	 */
	public String getContentType() {
		return contentType;
	}

	/**
	 * Returns the image data. The array is not copied; it belongs to the caller.
	 *
	 * @return the image bytes
	 */
	public byte[] getData() {
		return data;
	}

	/**
	 * Returns the entity tag the Director sent with the image, an opaque version of it.
	 *
	 * @return the entity tag, or empty if the Director sent none
	 */
	public Optional<String> getETag() {
		return Optional.ofNullable(eTag);
	}

	/**
	 * Returns when the Director last changed the image, as it stated it: an HTTP date, kept verbatim so
	 * that it can be sent back as is.
	 *
	 * @return the last-modified time, or empty if the Director sent none
	 */
	public Optional<String> getLastModified() {
		return Optional.ofNullable(lastModified);
	}

	/**
	 * Tells whether the avatar carries a validator, so that {@link DirectorClient#getUserAvatar(
	 * io.bosonnetwork.Id, Avatar)} can check it for changes instead of downloading it again.
	 *
	 * @return {@code true} if the avatar has an entity tag or a last-modified time
	 */
	public boolean isRevalidatable() {
		return eTag != null || lastModified != null;
	}

	@Override
	public String toString() {
		return "Avatar{contentType=" + contentType + ", size=" + data.length + ", lastModified=" + lastModified + "}";
	}
}
