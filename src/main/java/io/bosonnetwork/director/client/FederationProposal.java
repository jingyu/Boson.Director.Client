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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * A proposal to federate two super nodes, made by this node or received from another. Obtained from
 * a {@link DirectorAdmin}. Immutable.
 * <p>
 * The descriptive fields are what the other node publishes about itself, and any of them may be
 * absent.
 */
public class FederationProposal {
	private final long id;
	private final Id nodeId;
	private final List<String> addresses;
	private final @Nullable String apiEndpoint;
	private final @Nullable String software;
	private final @Nullable String version;
	private final @Nullable String name;
	private final @Nullable String logo;
	private final @Nullable String website;
	private final @Nullable String contact;
	private final Role role;
	private final Status status;
	private final long proposedAt;
	private final long confirmedAt;
	private final long createdAt;
	private final long updatedAt;

	/**
	 * Which side of a proposal this node is on.
	 */
	public enum Role {
		/** This node made the proposal. */
		OFFER,
		/** This node received the proposal. */
		ANSWER;

		@JsonCreator
		static Role of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}

		@JsonValue
		@Override
		public String toString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * The state of a proposal.
	 */
	public enum Status {
		/** Recorded, not sent yet. */
		CREATED,
		/** Sent, waiting for an answer. */
		PROPOSED,
		/** Accepted: the nodes are federated. */
		ACCEPTED,
		/** Declined by the node it was made to. */
		DECLINED,
		/** Could not be completed, for instance because the other node could not be reached. */
		FAILED;

		@JsonCreator
		static Status of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}

		@JsonValue
		@Override
		public String toString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	@JsonCreator
	FederationProposal(@JsonProperty(value = "id", required = true) long id,
			@JsonProperty(value = "nodeId", required = true) Id nodeId,
			@JsonProperty("addresses") @Nullable List<String> addresses,
			@JsonProperty("apiEndpoint") @Nullable String apiEndpoint,
			@JsonProperty("software") @Nullable String software,
			@JsonProperty("version") @Nullable String version,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty("logo") @Nullable String logo,
			@JsonProperty("website") @Nullable String website,
			@JsonProperty("contact") @Nullable String contact,
			@JsonProperty(value = "role", required = true) Role role,
			@JsonProperty(value = "status", required = true) Status status,
			@JsonProperty("proposedAt") long proposedAt,
			@JsonProperty("confirmedAt") long confirmedAt,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt) {
		this.id = id;
		this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
		this.addresses = addresses != null ? List.copyOf(addresses) : List.of();
		this.apiEndpoint = apiEndpoint;
		this.software = software;
		this.version = version;
		this.name = name;
		this.logo = logo;
		this.website = website;
		this.contact = contact;
		this.role = Objects.requireNonNull(role, "role");
		this.status = Objects.requireNonNull(status, "status");
		this.proposedAt = proposedAt;
		this.confirmedAt = confirmedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the proposal id.
	 *
	 * @return the proposal id
	 */
	public long getId() {
		return id;
	}

	/**
	 * Returns the other node.
	 *
	 * @return the node id
	 */
	public Id getNodeId() {
		return nodeId;
	}

	/**
	 * Returns the network addresses the other node was found at.
	 *
	 * @return the addresses, possibly empty
	 */
	public List<String> getAddresses() {
		return addresses;
	}

	/**
	 * Returns the URL of the other node's Director API.
	 *
	 * @return the API endpoint, if known
	 */
	public Optional<String> getApiEndpoint() {
		return Optional.ofNullable(apiEndpoint);
	}

	/**
	 * Returns the name of the software the other node runs.
	 *
	 * @return the software name, if published
	 */
	public Optional<String> getSoftware() {
		return Optional.ofNullable(software);
	}

	/**
	 * Returns the version of the software the other node runs.
	 *
	 * @return the version, if published
	 */
	public Optional<String> getVersion() {
		return Optional.ofNullable(version);
	}

	/**
	 * Returns the other node's display name.
	 *
	 * @return the name, if published
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the URL of the other node's logo.
	 *
	 * @return the logo URL, if published
	 */
	public Optional<String> getLogo() {
		return Optional.ofNullable(logo);
	}

	/**
	 * Returns the other node operator's website.
	 *
	 * @return the website URL, if published
	 */
	public Optional<String> getWebsite() {
		return Optional.ofNullable(website);
	}

	/**
	 * Returns how to contact the other node's operator.
	 *
	 * @return the contact, if published
	 */
	public Optional<String> getContact() {
		return Optional.ofNullable(contact);
	}

	/**
	 * Returns which side of the proposal this node is on.
	 *
	 * @return the role
	 */
	public Role getRole() {
		return role;
	}

	/**
	 * Returns the state of the proposal.
	 *
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Returns when the proposal was sent or received.
	 *
	 * @return the time, in epoch milliseconds, or {@code 0} if not sent yet
	 */
	public long getProposedAt() {
		return proposedAt;
	}

	/**
	 * Returns when the proposal was answered.
	 *
	 * @return the time, in epoch milliseconds, or {@code 0} if not answered
	 */
	public long getConfirmedAt() {
		return confirmedAt;
	}

	/**
	 * Returns when the proposal was recorded.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the proposal was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "FederationProposal{id=" + id + ", nodeId=" + nodeId + ", role=" + role + ", status=" + status + "}";
	}
}
