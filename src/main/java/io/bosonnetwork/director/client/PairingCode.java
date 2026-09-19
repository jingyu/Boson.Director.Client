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

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import io.bosonnetwork.crypto.CryptoBox;

/**
 * What a new device shows to a device already registered to the user, typically as a QR code, so that
 * the user can approve it joining their account: the id of the registration request, and a public key
 * the approving device seals the user key to. Immutable.
 * <p>
 * Its text form, {@code bosonpair:1:<registration id>:<key>}, is the same for every app, so any Boson app
 * can approve a device of any other. {@link #parse(String)} also reads the first version of the format,
 * prefixed {@code pmpair}, which apps used before the format moved into this library.
 *
 * @see DirectorGuest#requestDeviceRegistration(io.bosonnetwork.crypto.Signature.KeyPair, String, String)
 * @see DirectorClient#approveDeviceRegistration(PairingCode)
 */
public final class PairingCode {
	private static final String SCHEME = "bosonpair";
	private static final String VERSION = "1";

	private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();

	private final String registrationId;
	private final byte[] publicKey;

	PairingCode(String registrationId, byte[] publicKey) {
		this.registrationId = Objects.requireNonNull(registrationId, "registrationId");
		if (registrationId.isEmpty() || registrationId.indexOf(':') >= 0)
			throw new IllegalArgumentException("Invalid registration id: " + registrationId);
		if (publicKey.length != CryptoBox.PublicKey.BYTES)
			throw new IllegalArgumentException("Invalid public key length: " + publicKey.length);
		this.publicKey = publicKey.clone();
	}

	/**
	 * Parses a pairing code from its text form, as scanned or pasted.
	 *
	 * @param text the text form
	 * @return the pairing code
	 * @throws IllegalArgumentException if the text is not a pairing code
	 */
	public static PairingCode parse(String text) {
		Objects.requireNonNull(text, "text");
		String[] parts = text.trim().split(":", 4);
		if (parts.length != 4 || !parts[0].equals(SCHEME) || !parts[1].equals(VERSION))
			throw new IllegalArgumentException("Not a pairing code");

		byte[] key;
		try {
			key = B64URL_DECODER.decode(parts[3]);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Not a pairing code: malformed key", e);
		}
		return new PairingCode(parts[2], key);
	}

	/**
	 * Returns the id of the registration request the code is for.
	 *
	 * @return the registration id
	 */
	public String getRegistrationId() {
		return registrationId;
	}

	// The key the approving device seals the user key to.
	CryptoBox.PublicKey publicKey() {
		return CryptoBox.PublicKey.fromBytes(publicKey);
	}

	/**
	 * Returns the text form of the code, to show or share: {@code bosonpair:1:<registration id>:<key>}.
	 *
	 * @return the text form
	 */
	@Override
	public String toString() {
		return SCHEME + ":" + VERSION + ":" + registrationId + ":" + B64URL.encodeToString(publicKey);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PairingCode that && registrationId.equals(that.registrationId) &&
				Arrays.equals(publicKey, that.publicKey);
	}

	@Override
	public int hashCode() {
		return 31 * registrationId.hashCode() + Arrays.hashCode(publicKey);
	}
}
