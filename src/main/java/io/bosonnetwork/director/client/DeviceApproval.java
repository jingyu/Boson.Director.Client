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
import io.bosonnetwork.crypto.Signature;

/**
 * The outcome of an approved device registration, as the new device receives it from
 * {@link DirectorGuest#finishDeviceRegistration(DeviceRegistration)}: the user the device now belongs to,
 * and that user's key, handed over by the approving device. Immutable.
 * <p>
 * The approving device sealed the key to the new device's registration, so the Director relayed it
 * without being able to read it; it is opened here, and checked to be the named user's key.
 */
public class DeviceApproval {
	private final Id userId;
	private final Signature.KeyPair userKey;

	DeviceApproval(Id userId, Signature.KeyPair userKey) {
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
	 * Returns the user's key pair, to act as the user with a {@link DirectorClient}.
	 *
	 * @return the user key pair
	 */
	public Signature.KeyPair getUserKey() {
		return userKey;
	}

	@Override
	public String toString() {
		return "DeviceApproval{userId=" + userId + "}";
	}
}
