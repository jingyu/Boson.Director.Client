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

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executor;

import io.vertx.core.Vertx;
import org.jspecify.annotations.NullUnmarked;

import io.bosonnetwork.Id;

/**
 * The settings every Director client shares: the Vert.x instance it runs on, the Director it talks to,
 * how that Director is trusted, and where to connect. Each client's own builder adds what that client
 * acts with. Not thread-safe.
 *
 * @param <B> the concrete builder type, returned by each setter for chaining
 */
@NullUnmarked
public abstract class DirectorBuilder<B extends DirectorBuilder<B>> {
	Vertx vertx;
	URL directorUrl;
	Id nodeId;
	InetSocketAddress resolveToAddress;
	Executor callbackExecutor;

	DirectorBuilder() {
		// Adopt the Vert.x instance of the calling context, if there is one.
		this.vertx = Vertx.currentContext() != null ? Vertx.currentContext().owner() : null;
	}

	@SuppressWarnings("unchecked")
	private B self() {
		return (B) this;
	}

	/**
	 * Sets the Vert.x instance the client runs on. Required unless the builder was created on a Vert.x
	 * context, whose instance is then used.
	 *
	 * @param vertx the Vert.x instance
	 * @return this builder
	 */
	public B vertx(Vertx vertx) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		return self();
	}

	/**
	 * Sets the URL of the Director (required): scheme, host, port and any path prefix the Director is
	 * published under, without the {@code /api/v1} part.
	 *
	 * @param url an {@code http} or {@code https} URL
	 * @return this builder
	 * @throws IllegalArgumentException if the URL is not http(s)
	 */
	public B directorUrl(URL url) {
		Objects.requireNonNull(url, "url");
		if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https"))
			throw new IllegalArgumentException("Invalid Director URL protocol (must be http or https): " + url.getProtocol());
		this.directorUrl = url;
		return self();
	}

	/**
	 * Sets the URL of the Director (required) from a string.
	 *
	 * @param url an {@code http} or {@code https} URL
	 * @return this builder
	 * @throws IllegalArgumentException if the URL is malformed or not http(s)
	 * @see #directorUrl(URL)
	 */
	public B directorUrl(String url) {
		Objects.requireNonNull(url, "url");
		try {
			return directorUrl(new URL(url));
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException("Invalid Director URL: " + url, e);
		}
	}

	/**
	 * Sets the Boson id of the super node the Director runs on (optional). Over HTTPS, a self-signed
	 * Director certificate pinned to this id is accepted as well as a CA-signed one. A client that issues
	 * access tokens binds them to this id; without it, to the id the Director reports. Configure it when
	 * the id is known: a Director that reported another node's id could otherwise obtain tokens valid on
	 * that node.
	 *
	 * @param nodeId the super node id
	 * @return this builder
	 */
	public B nodeId(Id nodeId) {
		this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
		return self();
	}

	/**
	 * Sets the executor the client completes its futures on (optional), so that the stages an app chains on
	 * them run there rather than on the Vert.x event loop the answer arrives on. Meant for apps that do not
	 * run on Vert.x and would block in those stages - UI, database or file work. Without it, a call made on
	 * a Vert.x context completes on that context, and any other call on a Vert.x event loop.
	 *
	 * @param executor the executor to complete futures on
	 * @return this builder
	 */
	public B callbackExecutor(Executor executor) {
		this.callbackExecutor = Objects.requireNonNull(executor, "executor");
		return self();
	}

	/**
	 * Sets the address to connect to instead of looking up the Director URL's host name (optional).
	 * Requests still name the URL's host, and TLS still verifies the certificate against it: this changes
	 * where the client connects, never what it trusts. It reaches a Director over loopback, a LAN address
	 * or a tunnel while the URL keeps the name its certificate was issued for.
	 *
	 * @param address the address to connect to, resolved
	 * @return this builder
	 * @throws IllegalArgumentException if the address is unresolved
	 */
	public B resolveToAddress(InetSocketAddress address) {
		Objects.requireNonNull(address, "address");
		if (address.isUnresolved())
			throw new IllegalArgumentException("Unresolved address: " + address);
		this.resolveToAddress = address;
		return self();
	}
}
