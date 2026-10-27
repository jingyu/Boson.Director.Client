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

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;
import io.bosonnetwork.service.Admission;
import io.bosonnetwork.service.FederationContext.Outcome;

/**
 * How working with another node has gone lately, as this node observed it: the outcomes its services
 * report, and its own requests to the node. Obtained from a {@link DirectorAdmin}. Immutable.
 * <p>
 * The node keeps it in memory, for the nodes heard from within the last day, so it starts empty after a
 * restart. It decides nothing: a node is never suspended for its health.
 */
public class FederatedNodeHealth {
	private final Id nodeId;
	private final long lastSeenAt;
	private final long lastSuccessAt;
	private final long lastFailureAt;
	private final @Nullable Outcome lastFailure;
	private final @Nullable String lastFailureDetail;
	private final int consecutiveFailures;
	private final Map<Outcome, Integer> lastHour;
	private final int demand;
	private final long lastDemandAt;
	private final Admission.@Nullable Reason lastDemandReason;

	@JsonCreator
	FederatedNodeHealth(@JsonProperty(value = "nodeId", required = true) Id nodeId,
			@JsonProperty("lastSeenAt") long lastSeenAt,
			@JsonProperty("lastSuccessAt") long lastSuccessAt,
			@JsonProperty("lastFailureAt") long lastFailureAt,
			@JsonProperty("lastFailure") @Nullable String lastFailure,
			@JsonProperty("lastFailureDetail") @Nullable String lastFailureDetail,
			@JsonProperty("consecutiveFailures") int consecutiveFailures,
			@JsonProperty("lastHour") @Nullable Map<String, Integer> lastHour,
			@JsonProperty("demand") int demand,
			@JsonProperty("lastDemandAt") long lastDemandAt,
			@JsonProperty("lastDemandReason") @Nullable String lastDemandReason) {
		this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
		this.lastSeenAt = lastSeenAt;
		this.lastSuccessAt = lastSuccessAt;
		this.lastFailureAt = lastFailureAt;
		this.lastFailure = outcome(lastFailure);
		this.lastFailureDetail = lastFailureDetail;
		this.consecutiveFailures = consecutiveFailures;

		Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
		if (lastHour != null) {
			lastHour.forEach((name, count) -> {
				Outcome o = outcome(name);
				if (o != null && count != null)
					counts.put(o, count);
			});
		}
		this.lastHour = Collections.unmodifiableMap(counts);
		this.demand = demand;
		this.lastDemandAt = lastDemandAt;
		this.lastDemandReason = reason(lastDemandReason);
	}

	// A reason this version doesn't know is left out, as an unknown outcome is.
	private static Admission.@Nullable Reason reason(@Nullable String name) {
		if (name == null)
			return null;
		try {
			return Admission.Reason.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// An outcome this version doesn't know is left out, rather than failing the whole answer.
	private static @Nullable Outcome outcome(@Nullable String name) {
		if (name == null)
			return null;
		try {
			return Outcome.valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
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
	 * Returns when anything about the node was last observed.
	 *
	 * @return the time, in epoch milliseconds
	 */
	public long getLastSeenAt() {
		return lastSeenAt;
	}

	/**
	 * Returns when an exchange with the node last succeeded.
	 *
	 * @return the time, in epoch milliseconds, or 0 if none did within the last day
	 */
	public long getLastSuccessAt() {
		return lastSuccessAt;
	}

	/**
	 * Returns when an exchange with the node last failed.
	 *
	 * @return the time, in epoch milliseconds, or 0 if none did within the last day
	 */
	public long getLastFailureAt() {
		return lastFailureAt;
	}

	/**
	 * Returns how the last failed exchange failed.
	 *
	 * @return an {@link Optional} with the outcome, or empty
	 */
	public Optional<Outcome> getLastFailure() {
		return Optional.ofNullable(lastFailure);
	}

	/**
	 * Returns what was said about the last failure.
	 *
	 * @return an {@link Optional} with the detail, or empty
	 */
	public Optional<String> getLastFailureDetail() {
		return Optional.ofNullable(lastFailureDetail);
	}

	/**
	 * Returns the failures since the last success.
	 *
	 * @return the number
	 */
	public int getConsecutiveFailures() {
		return consecutiveFailures;
	}

	/**
	 * Tells whether the last exchange with the node failed.
	 *
	 * @return {@code true} if it did
	 */
	public boolean isFailing() {
		return consecutiveFailures > 0;
	}

	/**
	 * Returns the outcomes of the last hour, by outcome; an outcome not seen is absent.
	 *
	 * @return the counts
	 */
	public Map<Outcome, Integer> getLastHour() {
		return lastHour;
	}

	/**
	 * Returns how often local services wanted to reach the node and were held or refused for want of a
	 * relationship with it: a node to propose to, if the count grows.
	 *
	 * @return the count, 0 if none
	 */
	public int getDemand() {
		return demand;
	}

	/**
	 * Returns when local services last wanted to reach the node and couldn't.
	 *
	 * @return the time, in epoch milliseconds, or 0 if never within the last day
	 */
	public long getLastDemandAt() {
		return lastDemandAt;
	}

	/**
	 * Returns why local services last couldn't reach the node: {@code PENDING}, {@code NO_RELATIONSHIP}
	 * or {@code DECLINED}.
	 *
	 * @return an {@link Optional} with the reason, or empty
	 */
	public Optional<Admission.Reason> getLastDemandReason() {
		return Optional.ofNullable(lastDemandReason);
	}

	@Override
	public String toString() {
		return "FederatedNodeHealth{nodeId=" + nodeId + ", consecutiveFailures=" + consecutiveFailures + "}";
	}
}
