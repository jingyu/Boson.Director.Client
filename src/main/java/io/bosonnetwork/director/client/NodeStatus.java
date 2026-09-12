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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * What a super node is, what it runs and which services it offers, as reported by
 * {@link DirectorClient#getNodeStatus()}. Immutable.
 * <p>
 * The descriptive fields (name, logo, website, contact, software and version) are whatever the node
 * operator configured, and any of them may be absent.
 */
public class NodeStatus {
	private final Id nodeId;
	private final @Nullable String software;
	private final @Nullable String version;
	private final @Nullable String name;
	private final @Nullable String logo;
	private final @Nullable String website;
	private final @Nullable String contact;
	private final long startedAt;
	private final boolean running;
	private final List<Service> services;

	@JsonCreator
	NodeStatus(@JsonProperty(value = "nodeId", required = true) Id nodeId,
			@JsonProperty("software") @Nullable String software,
			@JsonProperty("version") @Nullable String version,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty("logo") @Nullable String logo,
			@JsonProperty("website") @Nullable String website,
			@JsonProperty("contact") @Nullable String contact,
			@JsonProperty("startedAt") long startedAt,
			@JsonProperty("running") boolean running,
			@JsonProperty("services") @Nullable List<Service> services) {
		this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
		this.software = software;
		this.version = version;
		this.name = name;
		this.logo = logo;
		this.website = website;
		this.contact = contact;
		this.startedAt = startedAt;
		this.running = running;
		this.services = services != null ? List.copyOf(services) : List.of();
	}

	/**
	 * Returns the Boson id of the node.
	 *
	 * @return the node id
	 */
	public Id getNodeId() {
		return nodeId;
	}

	/**
	 * Returns the name of the software the node runs.
	 *
	 * @return the software name, if reported
	 */
	public Optional<String> getSoftware() {
		return Optional.ofNullable(software);
	}

	/**
	 * Returns the version of the software the node runs.
	 *
	 * @return the version, if reported
	 */
	public Optional<String> getVersion() {
		return Optional.ofNullable(version);
	}

	/**
	 * Returns the node's display name.
	 *
	 * @return the name, if configured
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the URL of the node's logo.
	 *
	 * @return the logo URL, if configured
	 */
	public Optional<String> getLogo() {
		return Optional.ofNullable(logo);
	}

	/**
	 * Returns the node operator's website.
	 *
	 * @return the website URL, if configured
	 */
	public Optional<String> getWebsite() {
		return Optional.ofNullable(website);
	}

	/**
	 * Returns how to contact the node operator.
	 *
	 * @return the contact, if configured
	 */
	public Optional<String> getContact() {
		return Optional.ofNullable(contact);
	}

	/**
	 * Returns when the node started.
	 *
	 * @return the start time, in epoch milliseconds
	 */
	public long getStartedAt() {
		return startedAt;
	}

	/**
	 * Tells whether the node is running; it is not while it is still starting or is shutting down.
	 *
	 * @return {@code true} if the node is running
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Returns the services the node offers.
	 *
	 * @return the services, possibly empty
	 */
	public List<Service> getServices() {
		return services;
	}

	@Override
	public String toString() {
		return "NodeStatus{nodeId=" + nodeId + ", name=" + name + ", version=" + version +
				", running=" + running + ", services=" + services + "}";
	}

	/**
	 * A service offered by a super node. Immutable.
	 */
	public static class Service {
		private final String serviceId;
		private final @Nullable String serviceName;
		private final Id peerId;
		private final @Nullable String endpoint;

		@JsonCreator
		Service(@JsonProperty(value = "serviceId", required = true) String serviceId,
				@JsonProperty("serviceName") @Nullable String serviceName,
				@JsonProperty(value = "peerId", required = true) Id peerId,
				@JsonProperty("endpoint") @Nullable String endpoint) {
			this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
			this.serviceName = serviceName;
			this.peerId = Objects.requireNonNull(peerId, "peerId");
			this.endpoint = endpoint;
		}

		/**
		 * Returns the service id, such as {@code io.bosonnetwork.messaging}.
		 *
		 * @return the service id
		 */
		public String getServiceId() {
			return serviceId;
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
		 * Returns the id of the peer the service announces on the DHT.
		 *
		 * @return the peer id
		 */
		public Id getPeerId() {
			return peerId;
		}

		/**
		 * Returns the URL clients reach the service at.
		 *
		 * @return the endpoint, if the service has one
		 */
		public Optional<String> getEndpoint() {
			return Optional.ofNullable(endpoint);
		}

		@Override
		public String toString() {
			return "Service{serviceId=" + serviceId + ", peerId=" + peerId + ", endpoint=" + endpoint + "}";
		}
	}
}
