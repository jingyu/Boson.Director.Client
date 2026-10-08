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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import io.bosonnetwork.Id;
import io.bosonnetwork.utils.BosonString;

/**
 * What a node's console shows so that a Boson Identity app can enroll its user as an administrator of the
 * node, as a QR code: the enrollment request's id, the node's id and its Director's address. It carries no
 * credential: the app claims the request with its own keys, and the owner approves on the console. The node
 * id pins the node, as in a {@link SuperNodeCode}. Immutable.
 * <p>
 * Its text form is the Boson string {@code boson:enroll:1:<request id>:<node id>:<url>}: the ids in Base58,
 * then the Director's http(s) URL, without user info, query or fragment. The URL is the last field, so it
 * may contain {@code :}.
 *
 * @see DirectorAdmin#createEnrollment()
 * @see DirectorGuest#claimEnrollment(EnrollmentCode, io.bosonnetwork.crypto.Signature.KeyPair,
 *      io.bosonnetwork.crypto.Signature.KeyPair, String, String)
 * @see io.bosonnetwork.utils.BosonString
 */
public final class EnrollmentCode {
	/** The Boson string namespace of an enrollment code. */
	public static final String NAMESPACE = "enroll";
	/** The version of the enrollment code this library writes. */
	public static final int VERSION = 1;
	/**
	 * The label the claim signatures start with: each key signs it, then the node id, the request id, its
	 * own id and the claim's nonce (the ids as their 32 bytes).
	 */
	public static final String CLAIM_LABEL = "boson:sig:1:enroll-claim";

	private final Id requestId;
	private final Id nodeId;
	private final String url;

	private EnrollmentCode(Id requestId, Id nodeId, String url) {
		this.requestId = requestId;
		this.nodeId = nodeId;
		this.url = url;
	}

	/**
	 * An enrollment code for the request {@code requestId} of the node {@code nodeId}, whose Director is at
	 * {@code url}.
	 *
	 * @param requestId the enrollment request's id
	 * @param nodeId    the node's id
	 * @param url       the Director's http(s) URL, as apps reach it; a trailing slash is dropped
	 * @return the code
	 * @throws IllegalArgumentException if the URL is not an http(s) URL without user info, query or fragment
	 */
	public static EnrollmentCode of(Id requestId, Id nodeId, String url) {
		return new EnrollmentCode(Objects.requireNonNull(requestId, "requestId"),
				Objects.requireNonNull(nodeId, "nodeId"), SuperNodeCode.checkUrl(url));
	}

	/**
	 * Reads an enrollment code, as scanned or pasted.
	 *
	 * @param text the text
	 * @return the code, or empty if the text is not one
	 */
	public static Optional<EnrollmentCode> parse(String text) {
		Objects.requireNonNull(text, "text");
		try {
			return BosonString.parse(text, NAMESPACE, VERSION, 3).map(code -> new EnrollmentCode(
					Id.ofBase58(code.field(0)), Id.ofBase58(code.field(1)), SuperNodeCode.checkUrl(code.field(2))));
		} catch (RuntimeException e) {
			// Not a well-formed code.
			return Optional.empty();
		}
	}

	// What a key signs to claim this code's request: bound to the node, the request and the signer.
	byte[] claimMessage(Id signerId, byte[] nonce) {
		byte[] label = CLAIM_LABEL.getBytes(StandardCharsets.US_ASCII);
		byte[] message = new byte[label.length + Id.BYTES * 3 + nonce.length];
		int offset = 0;
		System.arraycopy(label, 0, message, offset, label.length);
		offset += label.length;
		for (Id id : new Id[] { nodeId, requestId, signerId }) {
			System.arraycopy(id.bytesUnsafe(), 0, message, offset, Id.BYTES);
			offset += Id.BYTES;
		}
		System.arraycopy(nonce, 0, message, offset, nonce.length);
		return message;
	}

	/**
	 * Returns the enrollment request's id.
	 *
	 * @return the request id
	 */
	public Id getRequestId() {
		return requestId;
	}

	/**
	 * Returns the node's id.
	 *
	 * @return the node id
	 */
	public Id getNodeId() {
		return nodeId;
	}

	/**
	 * Returns the Director's URL, without a trailing slash.
	 *
	 * @return the URL
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Returns the node this code names, as a super node code: the node an enrolled identity joins.
	 *
	 * @return the super node code
	 */
	public SuperNodeCode toSuperNodeCode() {
		return SuperNodeCode.of(nodeId, url);
	}

	/**
	 * Returns the text form of the code, to show or share: {@code boson:enroll:1:<request id>:<node id>:<url>}.
	 *
	 * @return the text form
	 */
	@Override
	public String toString() {
		return BosonString.format(NAMESPACE, VERSION, requestId.toBase58String(), nodeId.toBase58String(), url);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof EnrollmentCode that && requestId.equals(that.requestId) && nodeId.equals(that.nodeId) &&
				url.equals(that.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestId, nodeId, url);
	}
}
