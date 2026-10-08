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

import io.bosonnetwork.Id;

/**
 * An app's claim on an enrollment request, as {@link DirectorGuest#claimEnrollment(EnrollmentCode,
 * io.bosonnetwork.crypto.Signature.KeyPair, io.bosonnetwork.crypto.Signature.KeyPair, String, String)} made
 * it: the number to show, which the owner types on the node's console, and what
 * {@link DirectorGuest#finishEnrollment(PendingEnrollment)} waits with. Immutable.
 */
public final class PendingEnrollment {
	private final EnrollmentCode code;
	private final Id deviceId;
	private final int number;
	private final long expiresAt;

	PendingEnrollment(EnrollmentCode code, Id deviceId, int number, long expiresAt) {
		this.code = Objects.requireNonNull(code, "code");
		this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
		this.number = number;
		this.expiresAt = expiresAt;
	}

	/**
	 * Returns the code the claim was made for.
	 *
	 * @return the enrollment code
	 */
	public EnrollmentCode getCode() {
		return code;
	}

	/**
	 * Returns the claiming device's id.
	 *
	 * @return the device id
	 */
	public Id getDeviceId() {
		return deviceId;
	}

	/**
	 * Returns the number to show: the owner types it on the node's console to approve.
	 *
	 * @return the number, two digits
	 */
	public int getNumber() {
		return number;
	}

	/**
	 * Returns when the request expires, by the local clock, in milliseconds since the epoch.
	 *
	 * @return the expiry time
	 */
	public long getExpiresAt() {
		return expiresAt;
	}

	@Override
	public String toString() {
		return "PendingEnrollment{requestId=" + code.getRequestId() + ", deviceId=" + deviceId + "}";
	}
}
