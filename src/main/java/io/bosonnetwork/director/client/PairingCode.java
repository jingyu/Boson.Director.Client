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
import java.util.Optional;
import java.util.regex.Pattern;

import io.bosonnetwork.crypto.CryptoBox;
import io.bosonnetwork.utils.BosonString;

/**
 * What a new device shows to a device already registered to the user, typically as a QR code, so that
 * the user can approve it joining their account: the id of the registration request, and a public key
 * the approving device seals the user key to. Immutable.
 * <p>
 * Its text form is the Boson string {@code boson:pair:1:<registration id>:<key>}: the registration id in
 * Base58, the 32-byte X25519 key in base64url without padding (43 characters). It is the same for every
 * app, so any Boson app can approve a device of any other. {@link #parse(String)} also reads the form
 * before it, {@code bosonpair:1:<registration id>:<key>}, which apps from Boson 3.1 show.
 *
 * @see io.bosonnetwork.utils.BosonString
 * @see DirectorGuest#requestDeviceRegistration(io.bosonnetwork.crypto.Signature.KeyPair, String, String)
 * @see DirectorClient#approveDeviceRegistration(PairingCode)
 */
public final class PairingCode {
	/** The Boson string namespace of a pairing code. */
	public static final String NAMESPACE = "pair";
	/** The version of the pairing code this library writes. */
	public static final int VERSION = 1;

	// The form before the Boson string one, shown by apps from Boson 3.1; read, never written.
	private static final String LEGACY_PREFIX = "bosonpair:1:";

	private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();
	private static final Pattern BASE58 = Pattern.compile("[1-9A-HJ-NP-Za-km-z]+");
	// 32 bytes in base64url without padding.
	private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{43}");

	private final String registrationId;
	private final byte[] publicKey;

	PairingCode(String registrationId, byte[] publicKey) {
		this.registrationId = Objects.requireNonNull(registrationId, "registrationId");
		if (!BASE58.matcher(registrationId).matches())
			throw new IllegalArgumentException("Invalid registration id: " + registrationId);
		if (publicKey.length != CryptoBox.PublicKey.BYTES)
			throw new IllegalArgumentException("Invalid public key length: " + publicKey.length);
		this.publicKey = publicKey.clone();
	}

	/**
	 * Parses a pairing code from its text form, as scanned or pasted: the Boson string, or the form
	 * before it.
	 *
	 * @param text the text form
	 * @return the pairing code
	 * @throws IllegalArgumentException if the text is not a pairing code
	 */
	public static PairingCode parse(String text) {
		Objects.requireNonNull(text, "text");
		String trimmed = text.trim();
		String registrationId;
		String key;
		Optional<BosonString> code = BosonString.parse(trimmed, NAMESPACE, VERSION, 2);
		if (code.isPresent()) {
			registrationId = code.get().field(0);
			key = code.get().field(1);
		} else if (trimmed.startsWith(LEGACY_PREFIX)) {
			String[] parts = trimmed.substring(LEGACY_PREFIX.length()).split(":", 2);
			if (parts.length != 2)
				throw new IllegalArgumentException("Not a pairing code");
			registrationId = parts[0];
			key = parts[1];
		} else {
			throw new IllegalArgumentException("Not a pairing code");
		}

		if (!KEY.matcher(key).matches())
			throw new IllegalArgumentException("Not a pairing code: malformed key");
		return new PairingCode(registrationId, B64URL_DECODER.decode(key));
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
	 * Returns the text form of the code, to show or share: {@code boson:pair:1:<registration id>:<key>}.
	 *
	 * @return the text form
	 */
	@Override
	public String toString() {
		return BosonString.format(NAMESPACE, VERSION, registrationId, B64URL.encodeToString(publicKey));
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
