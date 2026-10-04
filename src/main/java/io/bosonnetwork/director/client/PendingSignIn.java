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
 * A page's pending "Sign in with Boson Identity", as {@link DirectorGuest#requestSignIn(String)} made it:
 * the code and number to show, and the secret that collects the session with
 * {@link DirectorGuest#finishSignIn(PendingSignIn)}. The secret stays in memory only.
 */
public final class PendingSignIn {
	private final String requestId;
	private final int number;
	private final byte[] secret;

	PendingSignIn(String requestId, int number, byte[] secret) {
		this.requestId = Objects.requireNonNull(requestId, "requestId");
		this.number = number;
		this.secret = secret.clone();
	}

	/**
	 * Returns the request id.
	 *
	 * @return the request id
	 */
	public String getRequestId() {
		return requestId;
	}

	/**
	 * Returns the QR code text to show.
	 *
	 * @return the code
	 */
	public String getCode() {
		return SignInRequest.code(requestId);
	}

	/**
	 * Returns the number to show beside the code; the user picks it in the app.
	 *
	 * @return the number
	 */
	public int getNumber() {
		return number;
	}

	byte[] secret() {
		return secret;
	}

	@Override
	public String toString() {
		return "PendingSignIn{requestId=" + requestId + "}";
	}
}
