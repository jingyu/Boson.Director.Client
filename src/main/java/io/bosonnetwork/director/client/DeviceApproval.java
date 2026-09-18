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
 * The outcome of an approved device registration, as the new device receives it from
 * {@link DirectorAuth#finishDeviceRegistration(io.bosonnetwork.crypto.Signature.KeyPair, String)}:
 * the user the device now belongs to, and the user key the approving device handed over.
 * <p>
 * The Director relays the user key without reading it: how it is protected in transit - typically
 * sealed to a key the new device showed the approving one - is up to the two devices.
 */
public class DeviceApproval {
	private final Id userId;
	private final byte[] userKey;

	DeviceApproval(Id userId, byte[] userKey) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.userKey = Objects.requireNonNull(userKey, "userKey");
	}

	/**
	 * Returns the id of the user the device is now registered to.
	 *
	 * @return the user id
	 */
	public Id getUserId() {
		return userId;
	}

	/**
	 * Returns the user key as the approving device passed it, exactly as it was sent. The array is not
	 * copied; it belongs to the caller.
	 *
	 * @return the user key, as relayed
	 */
	public byte[] getUserKey() {
		return userKey;
	}

	@Override
	public String toString() {
		return "DeviceApproval{userId=" + userId + "}";
	}
}
