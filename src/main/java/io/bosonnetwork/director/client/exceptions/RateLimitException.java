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

package io.bosonnetwork.director.client.exceptions;

/**
 * The caller exceeded one of the Director's rate limits (HTTP 429).
 * <p>
 * Wait {@link #getRetryAfter()} seconds before trying again: an immediate retry is certain to be
 * refused too.
 */
public class RateLimitException extends DirectorException {
	private static final long serialVersionUID = -1638306021944590842L;

	/**
	 * The HTTP status this exception stands for.
	 */
	public static final int STATUS = 429;

	private final long retryAfter;

	/**
	 * Creates the exception.
	 *
	 * @param message    the detail message
	 * @param retryAfter the seconds to wait before retrying, or {@code 0} if not given
	 */
	public RateLimitException(String message, long retryAfter) {
		super(STATUS, message);
		this.retryAfter = retryAfter;
	}

	/**
	 * Returns how long to wait before retrying.
	 *
	 * @return the wait in seconds, or {@code 0} if the Director did not say
	 */
	public long getRetryAfter() {
		return retryAfter;
	}
}
