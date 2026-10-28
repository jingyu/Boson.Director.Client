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
import io.bosonnetwork.service.Admission;

/**
 * Another super node as this node sees it: its relationship with this node, if any, and whether it is
 * admitted now and why. Obtained from a {@link DirectorAdmin}. Immutable.
 * <p>
 * A relationship is made by this node's operator, or by the two nodes agreeing through a
 * {@link FederationProposal proposal}. A node needs no relationship to be admitted where this node
 * federates openly. {@link State#ACTIVE} admits the node and {@link State#SUSPENDED} refuses it, whatever
 * the mode; the pending and declined states count only where admission needs agreement. The blacklist
 * refuses a node whatever its relationship.
 */
public class FederatedNode {
	/**
	 * Where the relationship with a node stands.
	 */
	public enum State {
		/** Admitted, whatever the mode. */
		ACTIVE,
		/** Refused, whatever the mode. */
		SUSPENDED,
		/** This node proposed, and waits for the answer. */
		PENDING_OUT,
		/** The node proposed, and waits for this node's operator. */
		PENDING_IN,
		/** A proposal either way was declined, or the relationship revoked. */
		DECLINED;

		String wireName() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable State of(@Nullable String name) {
			if (name == null)
				return null;
			for (State s : values()) {
				if (s.name().equalsIgnoreCase(name))
					return s;
			}
			return null;
		}
	}

	private final Id id;
	private final @Nullable State state;
	private final @Nullable String origin;
	private final @Nullable String note;
	private final Admission.@Nullable Reason admission;
	private final Admission.@Nullable Reason reach;
	private final long createdAt;
	private final long updatedAt;
	private final @Nullable FederatedNodeHealth health;

	@JsonCreator
	FederatedNode(@JsonProperty(value = "nodeId", required = true) Id id,
			@JsonProperty("state") @Nullable String state,
			@JsonProperty("origin") @Nullable String origin,
			@JsonProperty("note") @Nullable String note,
			@JsonProperty("admission") @Nullable String admission,
			@JsonProperty("reach") @Nullable String reach,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt,
			@JsonProperty("health") @Nullable FederatedNodeHealth health) {
		this.id = Objects.requireNonNull(id, "id");
		this.state = State.of(state);
		this.origin = origin;
		this.note = note;
		this.admission = reason(admission);
		this.reach = reason(reach);
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.health = health;
	}

	// A reason this version doesn't know is reported as none, rather than failing the whole answer.
	private static Admission.@Nullable Reason reason(@Nullable String name) {
		if (name == null)
			return null;
		try {
			return Admission.Reason.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
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
	 * Returns where the relationship with the node stands.
	 *
	 * @return an {@link Optional} with the state, or empty if no relationship was made, or it is in a
	 *         state this version doesn't know
	 */
	public Optional<State> getState() {
		return Optional.ofNullable(state);
	}

	/**
	 * Returns who made the relationship: {@code admin} for this node's operator, {@code protocol} for the
	 * two nodes' agreement, {@code auto} for an agreement from a proposal this node made on its own because
	 * a local service wanted to reach the other node.
	 *
	 * @return an {@link Optional} with the origin, or empty if no relationship was made
	 */
	public Optional<String> getOrigin() {
		return Optional.ofNullable(origin);
	}

	/**
	 * Returns the note about the node: the operator's, or the proposal's.
	 *
	 * @return an {@link Optional} with the note, or empty
	 */
	public Optional<String> getNote() {
		return Optional.ofNullable(note);
	}

	/**
	 * Returns why the node is admitted, pending or refused now.
	 *
	 * @return an {@link Optional} with the reason, or empty if the node gave one this version doesn't know
	 */
	public Optional<Admission.Reason> getAdmission() {
		return Optional.ofNullable(admission);
	}

	/**
	 * Tells whether the node is admitted now.
	 *
	 * @return {@code true} if it is
	 */
	public boolean isAdmitted() {
		return admission != null && admission.decision() == Admission.Decision.ALLOW;
	}

	/**
	 * Returns what this node's services are told about sending to the node now. It may differ from the
	 * admission: an open node admits a node in mutual mode, but holds its traffic to it until the two
	 * agree.
	 *
	 * @return an {@link Optional} with the reason, or empty from a server that doesn't say, or with a
	 *         reason this version doesn't know
	 */
	public Optional<Admission.Reason> getReach() {
		return Optional.ofNullable(reach);
	}

	/**
	 * Returns when the relationship was made.
	 *
	 * @return the creation time, in epoch milliseconds, or 0 if no relationship was made
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the relationship last changed.
	 *
	 * @return the update time, in epoch milliseconds, or 0 if no relationship was made
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Returns how working with the node has gone lately.
	 *
	 * @return an {@link Optional} with the health, or empty if nothing about the node was observed
	 *         within the last day
	 */
	public Optional<FederatedNodeHealth> getHealth() {
		return Optional.ofNullable(health);
	}

	@Override
	public String toString() {
		return "FederatedNode{id=" + id + ", state=" + state + ", admission=" + admission + "}";
	}
}
