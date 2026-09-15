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
 * A super node this node has federated with, as this node records it. Obtained from a
 * {@link DirectorAdmin}. Immutable.
 * <p>
 * The descriptive fields (name, logo, website, contact, software and version) are what the other
 * node publishes about itself, and any of them may be absent. The description and reputation are
 * this node's own notes.
 */
public class FederatedNode {
	private final Id id;
	private final List<String> addresses;
	private final @Nullable String apiEndpoint;
	private final @Nullable String software;
	private final @Nullable String version;
	private final @Nullable String name;
	private final @Nullable String logo;
	private final @Nullable String website;
	private final @Nullable String contact;
	private final @Nullable String description;
	private final boolean federated;
	private final int reputation;
	private final long createdAt;
	private final long updatedAt;

	@JsonCreator
	FederatedNode(@JsonProperty(value = "id", required = true) Id id,
			@JsonProperty("addresses") @Nullable List<String> addresses,
			@JsonProperty("apiEndpoint") @Nullable String apiEndpoint,
			@JsonProperty("software") @Nullable String software,
			@JsonProperty("version") @Nullable String version,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty("logo") @Nullable String logo,
			@JsonProperty("website") @Nullable String website,
			@JsonProperty("contact") @Nullable String contact,
			@JsonProperty("description") @Nullable String description,
			@JsonProperty("federated") boolean federated,
			@JsonProperty("reputation") int reputation,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.addresses = addresses != null ? List.copyOf(addresses) : List.of();
		this.apiEndpoint = apiEndpoint;
		this.software = software;
		this.version = version;
		this.name = name;
		this.logo = logo;
		this.website = website;
		this.contact = contact;
		this.description = description;
		this.federated = federated;
		this.reputation = reputation;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the node id.
	 *
	 * @return the node id
	 */
	public Id getId() {
		return id;
	}

	/**
	 * Returns the network addresses the node was found at.
	 *
	 * @return the addresses, possibly empty
	 */
	public List<String> getAddresses() {
		return addresses;
	}

	/**
	 * Returns the URL of the node's Director API.
	 *
	 * @return the API endpoint, if known
	 */
	public Optional<String> getApiEndpoint() {
		return Optional.ofNullable(apiEndpoint);
	}

	/**
	 * Returns the name of the software the node runs.
	 *
	 * @return the software name, if published
	 */
	public Optional<String> getSoftware() {
		return Optional.ofNullable(software);
	}

	/**
	 * Returns the version of the software the node runs.
	 *
	 * @return the version, if published
	 */
	public Optional<String> getVersion() {
		return Optional.ofNullable(version);
	}

	/**
	 * Returns the node's display name.
	 *
	 * @return the name, if published
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the URL of the node's logo.
	 *
	 * @return the logo URL, if published
	 */
	public Optional<String> getLogo() {
		return Optional.ofNullable(logo);
	}

	/**
	 * Returns the node operator's website.
	 *
	 * @return the website URL, if published
	 */
	public Optional<String> getWebsite() {
		return Optional.ofNullable(website);
	}

	/**
	 * Returns how to contact the node operator.
	 *
	 * @return the contact, if published
	 */
	public Optional<String> getContact() {
		return Optional.ofNullable(contact);
	}

	/**
	 * Returns this node's description of the federated node.
	 *
	 * @return the description, if set
	 */
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	/**
	 * Tells whether the federation is in force.
	 *
	 * @return {@code true} if the node is federated
	 */
	public boolean isFederated() {
		return federated;
	}

	/**
	 * Returns the reputation this node gives the federated node.
	 *
	 * @return the reputation
	 */
	public int getReputation() {
		return reputation;
	}

	/**
	 * Returns when the node was recorded.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the record was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "FederatedNode{id=" + id + ", name=" + name + ", apiEndpoint=" + apiEndpoint +
				", federated=" + federated + ", reputation=" + reputation + "}";
	}
}
