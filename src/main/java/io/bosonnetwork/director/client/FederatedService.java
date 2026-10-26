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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * A service another super node lists in its descriptor. Obtained from a {@link FederatedNodeDescriptor}.
 * Immutable.
 * <p>
 * The endpoint is the one the node lists, a hint: where the service is now is its own announcement in
 * the DHT.
 */
public class FederatedService {
	private final String type;
	private final @Nullable String name;
	private final Id peerId;
	private final String endpoint;
	private final boolean federation;
	private final Map<String, Object> extra;

	@JsonCreator
	FederatedService(@JsonProperty(value = "type", required = true) String type,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty(value = "peerId", required = true) Id peerId,
			@JsonProperty(value = "endpoint", required = true) String endpoint,
			@JsonProperty("federation") boolean federation,
			@JsonProperty("extra") @Nullable Map<String, Object> extra) {
		this.type = Objects.requireNonNull(type, "type");
		this.name = name;
		this.peerId = Objects.requireNonNull(peerId, "peerId");
		this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
		this.federation = federation;
		this.extra = extra != null ? Map.copyOf(extra) : Map.of();
	}

	/**
	 * Returns the service type id, such as {@code io.bosonnetwork.photonmessaging}.
	 *
	 * @return the type
	 */
	public String getType() {
		return type;
	}

	/**
	 * Returns the service's display name.
	 *
	 * @return an {@link Optional} with the name, or empty
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the service's peer id.
	 *
	 * @return the peer id
	 */
	public Id getPeerId() {
		return peerId;
	}

	/**
	 * Returns the endpoint the node lists for the service.
	 *
	 * @return the endpoint
	 */
	public String getEndpoint() {
		return endpoint;
	}

	/**
	 * Tells whether other nodes may use the service.
	 *
	 * @return {@code true} if they may
	 */
	public boolean isFederation() {
		return federation;
	}

	/**
	 * Returns the service's extra data.
	 *
	 * @return an unmodifiable map, empty if none
	 */
	public Map<String, Object> getExtra() {
		return extra;
	}

	@Override
	public String toString() {
		return "FederatedService{type=" + type + ", peerId=" + peerId + ", endpoint=" + endpoint + "}";
	}
}
