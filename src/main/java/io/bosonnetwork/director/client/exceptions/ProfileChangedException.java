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
 * A change of the profile was refused because the profile changed since the revision it was made from
 * (HTTP 412): another device, or another app, changed it meanwhile. Read the profile again, merge, and
 * retry against its current revision.
 */
public class ProfileChangedException extends DirectorException {
	private static final long serialVersionUID = 4180950286135530128L;

	/**
	 * The HTTP status the Director answers a change made against an outdated revision with.
	 */
	public static final int STATUS = 412;

	/**
	 * Creates the exception.
	 *
	 * @param message the detail message
	 */
	public ProfileChangedException(String message) {
		super(STATUS, message);
	}
}
