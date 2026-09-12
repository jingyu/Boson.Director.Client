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

/**
 * A user's avatar image, as downloaded by {@link DirectorClient#getAvatar()}.
 */
public class Avatar {
	private final String contentType;
	private final byte[] data;

	Avatar(String contentType, byte[] data) {
		this.contentType = Objects.requireNonNull(contentType, "contentType");
		this.data = Objects.requireNonNull(data, "data");
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

	@Override
	public String toString() {
		return "Avatar{contentType=" + contentType + ", size=" + data.length + "}";
	}
}
