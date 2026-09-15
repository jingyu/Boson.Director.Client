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
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * A service a federated node shares with this node. Obtained from a {@link DirectorAdmin}.
 * Immutable.
 */
public class FederatedService {
	private final Id peerId;
	private final long fingerprint;
	private final @Nullable Id nodeId;
	private final @Nullable String endpoint;
	private final @Nullable Object extra;
	private final @Nullable String serviceId;
	private final @Nullable String serviceName;
	private final boolean federationEnabled;

	@JsonCreator
	FederatedService(@JsonProperty(value = "peerId", required = true) Id peerId,
			@JsonProperty("fingerprint") long fingerprint,
			@JsonProperty("nodeId") @Nullable Id nodeId,
			@JsonProperty("endpoint") @Nullable String endpoint,
			@JsonProperty("extra") @Nullable Object extra,
			@JsonProperty("serviceId") @Nullable String serviceId,
			@JsonProperty("serviceName") @Nullable String serviceName,
			@JsonProperty("federationEnabled") boolean federationEnabled) {
		this.peerId = Objects.requireNonNull(peerId, "peerId");
		this.fingerprint = fingerprint;
		this.nodeId = nodeId;
		this.endpoint = endpoint;
		this.extra = extra;
		this.serviceId = serviceId;
		this.serviceName = serviceName;
		this.federationEnabled = federationEnabled;
	}

	/**
	 * Returns the id of the peer the service announces on the DHT.
	 *
	 * @return the peer id
	 */
	public Id getPeerId() {
		return peerId;
	}

	/**
	 * Returns the fingerprint that tells the service's announcements apart from other peers under the
	 * same peer id.
	 *
	 * @return the fingerprint
	 */
	public long getFingerprint() {
		return fingerprint;
	}

	/**
	 * Returns the node the service runs on.
	 *
	 * @return the node id, if reported
	 */
	public Optional<Id> getNodeId() {
		return Optional.ofNullable(nodeId);
	}

	/**
	 * Returns the URL clients reach the service at.
	 *
	 * @return the endpoint, if the service has one
	 */
	public Optional<String> getEndpoint() {
		return Optional.ofNullable(endpoint);
	}

	/**
	 * Returns the service's extra information: a {@code Map} when it is structured, otherwise its raw
	 * bytes as an unpadded base64url string.
	 *
	 * @return the extra information, if any
	 */
	public Optional<Object> getExtra() {
		return Optional.ofNullable(extra);
	}

	/**
	 * Returns the service id, such as {@code io.bosonnetwork.messaging}.
	 *
	 * @return the service id, if reported
	 */
	public Optional<String> getServiceId() {
		return Optional.ofNullable(serviceId);
	}

	/**
	 * Returns the service's display name.
	 *
	 * @return the name, if reported
	 */
	public Optional<String> getServiceName() {
		return Optional.ofNullable(serviceName);
	}

	/**
	 * Tells whether the service takes part in federation.
	 *
	 * @return {@code true} if federation is enabled for the service
	 */
	public boolean isFederationEnabled() {
		return federationEnabled;
	}

	@Override
	public String toString() {
		return "FederatedService{peerId=" + peerId + ", serviceId=" + serviceId + ", endpoint=" + endpoint + "}";
	}
}
