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
import io.bosonnetwork.service.FederationMode;

/**
 * What another super node says about itself and the services it runs, from its signed descriptor, which
 * this node has fetched and verified. Obtained from a {@link DirectorAdmin}. Immutable.
 */
public class FederatedNodeDescriptor {
	private final Id nodeId;
	private final @Nullable String software;
	private final @Nullable String version;
	private final @Nullable String name;
	private final @Nullable String logo;
	private final @Nullable String website;
	private final @Nullable String contact;
	private final @Nullable FederationMode mode;
	private final List<String> protocols;
	private final List<String> capabilities;
	private final List<FederatedService> services;
	private final long issuedAt;
	private final long expiresAt;

	@JsonCreator
	FederatedNodeDescriptor(@JsonProperty(value = "nodeId", required = true) Id nodeId,
			@JsonProperty("software") @Nullable String software,
			@JsonProperty("version") @Nullable String version,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty("logo") @Nullable String logo,
			@JsonProperty("website") @Nullable String website,
			@JsonProperty("contact") @Nullable String contact,
			@JsonProperty("mode") @Nullable String mode,
			@JsonProperty("protocols") @Nullable List<String> protocols,
			@JsonProperty("capabilities") @Nullable List<String> capabilities,
			@JsonProperty("services") @Nullable List<FederatedService> services,
			@JsonProperty("issuedAt") long issuedAt,
			@JsonProperty("expiresAt") long expiresAt) {
		this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
		this.software = software;
		this.version = version;
		this.name = name;
		this.logo = logo;
		this.website = website;
		this.contact = contact;
		FederationMode m = null;
		if (mode != null) {
			try {
				m = FederationMode.ofWireName(mode);
			} catch (IllegalArgumentException ignore) {
				// A mode this version doesn't know: left out, like any other unknown value.
			}
		}
		this.mode = m;
		this.protocols = protocols != null ? List.copyOf(protocols) : List.of();
		this.capabilities = capabilities != null ? List.copyOf(capabilities) : List.of();
		this.services = services != null ? List.copyOf(services) : List.of();
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
	}

	/**
	 * Returns the node id.
	 *
	 * @return the node id
	 */
	public Id getNodeId() {
		return nodeId;
	}

	/**
	 * Returns the software the node runs.
	 *
	 * @return an {@link Optional} with the software, or empty
	 */
	public Optional<String> getSoftware() {
		return Optional.ofNullable(software);
	}

	/**
	 * Returns the version of the software the node runs.
	 *
	 * @return an {@link Optional} with the version, or empty
	 */
	public Optional<String> getVersion() {
		return Optional.ofNullable(version);
	}

	/**
	 * Returns the node's display name.
	 *
	 * @return an {@link Optional} with the name, or empty
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the node's logo URL.
	 *
	 * @return an {@link Optional} with the logo, or empty
	 */
	public Optional<String> getLogo() {
		return Optional.ofNullable(logo);
	}

	/**
	 * Returns the node's website.
	 *
	 * @return an {@link Optional} with the website, or empty
	 */
	public Optional<String> getWebsite() {
		return Optional.ofNullable(website);
	}

	/**
	 * Returns how to contact the node's operator.
	 *
	 * @return an {@link Optional} with the contact, or empty
	 */
	public Optional<String> getContact() {
		return Optional.ofNullable(contact);
	}

	/**
	 * Returns how the node decides whom it federates with.
	 *
	 * @return an {@link Optional} with the mode, or empty if it says none, or one this version doesn't know
	 *         (a newer node's)
	 */
	public Optional<FederationMode> getMode() {
		return Optional.ofNullable(mode);
	}

	/**
	 * Returns the federation protocols the node speaks.
	 *
	 * @return an unmodifiable list
	 */
	public List<String> getProtocols() {
		return protocols;
	}

	/**
	 * Returns the node's capabilities.
	 *
	 * @return an unmodifiable list
	 */
	public List<String> getCapabilities() {
		return capabilities;
	}

	/**
	 * Returns the services the node lists.
	 *
	 * @return an unmodifiable list
	 */
	public List<FederatedService> getServices() {
		return services;
	}

	/**
	 * Returns when the node signed the descriptor.
	 *
	 * @return the issue time, in epoch milliseconds
	 */
	public long getIssuedAt() {
		return issuedAt;
	}

	/**
	 * Returns when the descriptor expires.
	 *
	 * @return the expiration time, in epoch milliseconds
	 */
	public long getExpiresAt() {
		return expiresAt;
	}

	@Override
	public String toString() {
		return "FederatedNodeDescriptor{nodeId=" + nodeId + ", name=" + name + ", services=" + services.size() + "}";
	}
}
