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

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.utils.BosonString;

/**
 * What a super node shows so that an app can join it, typically as a QR code: the node's id and its
 * Director's address. The id pins the node: an app that scans the code accepts the Director only if it
 * answers with that id. Immutable.
 * <p>
 * Its text form is the Boson string {@code boson:supernode:1:<node id>:<url>}: the node id in Base58, then
 * the Director's http(s) URL, without user info, query or fragment. The URL is the last field, so it may
 * contain {@code :}. {@link #parse(String)} also reads the form before it, the JSON object
 * {@code {"url": ..., "id": ...}} that apps and portals from Boson 3.1 show, whose id is optional.
 *
 * @see io.bosonnetwork.utils.BosonString
 */
public final class SuperNodeCode {
	/** The Boson string namespace of a super node code. */
	public static final String NAMESPACE = "supernode";
	/** The version of the super node code this library writes. */
	public static final int VERSION = 1;

	private final @Nullable Id nodeId;
	private final String url;

	private SuperNodeCode(@Nullable Id nodeId, String url) {
		this.nodeId = nodeId;
		this.url = url;
	}

	/**
	 * A super node code for the node {@code nodeId}, whose Director is at {@code url}.
	 *
	 * @param nodeId the node's id
	 * @param url    the Director's http(s) URL; a trailing slash is dropped
	 * @return the code
	 * @throws IllegalArgumentException if the URL is not an http(s) URL without user info, query or fragment
	 */
	public static SuperNodeCode of(Id nodeId, String url) {
		return new SuperNodeCode(Objects.requireNonNull(nodeId, "nodeId"), checkUrl(url));
	}

	/**
	 * Reads a super node code, as scanned or pasted: the Boson string, or the JSON form before it.
	 *
	 * @param text the text
	 * @return the code, or empty if the text is not one
	 */
	public static Optional<SuperNodeCode> parse(String text) {
		Objects.requireNonNull(text, "text");
		String trimmed = text.trim();
		try {
			Optional<BosonString> code = BosonString.parse(trimmed, NAMESPACE, VERSION, 2);
			if (code.isPresent())
				return Optional.of(new SuperNodeCode(Id.ofBase58(code.get().field(0)), checkUrl(code.get().field(1))));

			if (trimmed.startsWith("{")) {
				Map<String, Object> json = Json.parse(trimmed);
				if (!(json.get("url") instanceof String url) || url.isBlank())
					return Optional.empty();
				Id nodeId = json.get("id") instanceof String id && !id.isBlank() ? Id.of(id.trim()) : null;
				return Optional.of(new SuperNodeCode(nodeId, checkUrl(url.trim())));
			}
		} catch (RuntimeException e) {
			// Not a well-formed code of either form.
		}
		return Optional.empty();
	}

	private static String checkUrl(String url) {
		Objects.requireNonNull(url, "url");
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid URL: " + url, e);
		}
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null ||
				uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
			throw new IllegalArgumentException("Not a Director URL: " + url);
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	/**
	 * Returns the node's id. A code in the form before the Boson string one may lack it.
	 *
	 * @return the node id, if the code has one
	 */
	public Optional<Id> getNodeId() {
		return Optional.ofNullable(nodeId);
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
	 * Returns the text form of the code, to show or share: {@code boson:supernode:1:<node id>:<url>}.
	 *
	 * @return the text form
	 * @throws IllegalStateException if the code has no node id (read from the form before)
	 */
	@Override
	public String toString() {
		if (nodeId == null)
			throw new IllegalStateException("A super node code without a node id has no text form");
		return BosonString.format(NAMESPACE, VERSION, nodeId.toBase58String(), url);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof SuperNodeCode that && Objects.equals(nodeId, that.nodeId) && url.equals(that.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nodeId, url);
	}
}
