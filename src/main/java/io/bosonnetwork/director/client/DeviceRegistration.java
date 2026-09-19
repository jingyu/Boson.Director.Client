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
import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.crypto.Signature;

/**
 * A new device's pending request to join an account, from
 * {@link DirectorGuest#requestDeviceRegistration(Signature.KeyPair, String, String)}: show its
 * {@linkplain #getPairingCode() pairing code} to a device registered to the user, and wait for the answer
 * with {@link DirectorGuest#finishDeviceRegistration(DeviceRegistration)}.
 * <p>
 * It holds the secret the user key is sealed to, which never leaves this object: keep it for as long as
 * the request is pending, and only in memory. The Director keeps a request for three minutes.
 */
public final class DeviceRegistration {
	private final String registrationId;
	private final Signature.KeyPair deviceKey;
	private final CryptoBox.KeyPair sealingKey;
	private final PairingCode pairingCode;

	DeviceRegistration(String registrationId, Signature.KeyPair deviceKey, CryptoBox.KeyPair sealingKey) {
		this.registrationId = Objects.requireNonNull(registrationId, "registrationId");
		this.deviceKey = Objects.requireNonNull(deviceKey, "deviceKey");
		this.sealingKey = Objects.requireNonNull(sealingKey, "sealingKey");
		this.pairingCode = new PairingCode(registrationId, sealingKey.publicKey().bytes());
	}

	/**
	 * Returns the id the Director gave the request.
	 *
	 * @return the registration id
	 */
	public String getRegistrationId() {
		return registrationId;
	}

	/**
	 * Returns the id of the device asking to join, derived from its key.
	 *
	 * @return the device id
	 */
	public Id getDeviceId() {
		return Id.of(deviceKey.publicKey().bytes());
	}

	/**
	 * Returns the code to show the approving device.
	 *
	 * @return the pairing code
	 */
	public PairingCode getPairingCode() {
		return pairingCode;
	}

	Signature.KeyPair deviceKey() {
		return deviceKey;
	}

	CryptoBox.KeyPair sealingKey() {
		return sealingKey;
	}

	@Override
	public String toString() {
		return "DeviceRegistration{registrationId=" + registrationId + ", deviceId=" + getDeviceId() + "}";
	}
}
