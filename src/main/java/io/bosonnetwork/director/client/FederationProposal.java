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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * A federation proposal in this node's record: one it made to another node, or one another node made to
 * it. Obtained from a {@link DirectorAdmin}. Immutable.
 * <p>
 * Proposals are how two nodes in {@code mutual} mode agree to federate. A node that accepts proposals
 * answers at once; one whose operator reviews them holds them {@link Status#PENDING pending} until the
 * operator accepts or declines. A pending offer is sent again until it is answered, and expires after a
 * week.
 */
public class FederationProposal {
	/** Which side of the proposal this node is. */
	public enum Role {
		/** This node proposed. */
		OFFER,
		/** The other node proposed; this node answers. */
		ANSWER;

		String wireName() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Role of(@Nullable String name) {
			if (name == null)
				return null;
			for (Role r : values()) {
				if (r.name().equalsIgnoreCase(name))
					return r;
			}
			return null;
		}
	}

	/** Where a proposal stands. */
	public enum Status {
		/** Not answered yet. */
		PENDING,
		/** Accepted: the relationship is active. */
		ACCEPTED,
		/** Declined. */
		DECLINED,
		/** Withdrawn by this node before an answer. */
		WITHDRAWN,
		/** Not answered within a week. */
		EXPIRED;

		String wireName() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Status of(@Nullable String name) {
			if (name == null)
				return null;
			for (Status s : values()) {
				if (s.name().equalsIgnoreCase(name))
					return s;
			}
			return null;
		}
	}

	private final long id;
	private final Id nodeId;
	private final @Nullable Role role;
	private final @Nullable Status status;
	private final @Nullable String note;
	private final long createdAt;
	private final long updatedAt;
	private final long answeredAt;

	@JsonCreator
	FederationProposal(@JsonProperty(value = "id", required = true) long id,
			@JsonProperty(value = "nodeId", required = true) Id nodeId,
			@JsonProperty("role") @Nullable String role,
			@JsonProperty("status") @Nullable String status,
			@JsonProperty("note") @Nullable String note,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt,
			@JsonProperty("answeredAt") long answeredAt) {
		this.id = id;
		this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
		this.role = Role.of(role);
		this.status = Status.of(status);
		this.note = note;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.answeredAt = answeredAt;
	}

	/**
	 * Returns the proposal's id in this node's record.
	 *
	 * @return the id
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
	 * Returns which side of the proposal this node is.
	 *
	 * @return an {@link Optional} with the role, or empty if the node gave one this version doesn't know
	 */
	public Optional<Role> getRole() {
		return Optional.ofNullable(role);
	}

	/**
	 * Returns where the proposal stands.
	 *
	 * @return an {@link Optional} with the status, or empty if the node gave one this version doesn't know
	 */
	public Optional<Status> getStatus() {
		return Optional.ofNullable(status);
	}

	/**
	 * Tells whether the proposal waits for an answer.
	 *
	 * @return {@code true} if it does
	 */
	public boolean isPending() {
		return status == Status.PENDING;
	}

	/**
	 * Returns the note the proposing node's operator gave.
	 *
	 * @return an {@link Optional} with the note, or empty
	 */
	public Optional<String> getNote() {
		return Optional.ofNullable(note);
	}

	/**
	 * Returns when the proposal was made.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the proposal last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Returns when the proposal was answered, withdrawn or expired.
	 *
	 * @return the time, in epoch milliseconds, or 0 while it is pending
	 */
	public long getAnsweredAt() {
		return answeredAt;
	}

	@Override
	public String toString() {
		return "FederationProposal{id=" + id + ", nodeId=" + nodeId + ", role=" + role + ", status=" + status + "}";
	}
}
