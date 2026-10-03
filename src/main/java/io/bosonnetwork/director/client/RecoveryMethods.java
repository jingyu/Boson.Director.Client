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

/**
 * The ways a user can reset a forgotten passphrase without the user key: linked OAuth sign-ins,
 * passkeys that can recover, and unused recovery codes. With none, a forgotten passphrase is lost.
 */
public final class RecoveryMethods {
	private final int oauth;
	private final int passkeys;
	private final int recoveryCodes;

	RecoveryMethods(int oauth, int passkeys, int recoveryCodes) {
		this.oauth = oauth;
		this.passkeys = passkeys;
		this.recoveryCodes = recoveryCodes;
	}

	/** @return how many OAuth sign-ins are linked to the user */
	public int oauth() {
		return oauth;
	}

	/** @return how many of the user's passkeys can reset the passphrase */
	public int passkeys() {
		return passkeys;
	}

	/** @return how many unused recovery codes the user has */
	public int recoveryCodes() {
		return recoveryCodes;
	}

	/** @return whether a forgotten passphrase could be reset at all */
	public boolean any() {
		return oauth + passkeys + recoveryCodes > 0;
	}

	@Override
	public String toString() {
		return "RecoveryMethods[oauth=" + oauth + ", passkeys=" + passkeys + ", recoveryCodes=" + recoveryCodes + "]";
	}
}
