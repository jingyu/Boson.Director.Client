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
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

/**
 * A user's subscription to a paid plan. Part of the {@link UserPlan} returned by
 * {@link DirectorClient#getPlan()}. Immutable.
 */
public class Subscription {
	private final long id;
	private final int planId;
	private final @Nullable String planName;
	private final Status status;
	private final long startDate;
	private final long endDate;
	private final long createdAt;
	private final long updatedAt;

	/**
	 * The state of a subscription.
	 */
	public enum Status {
		/** Created, waiting for its first payment. */
		PENDING,
		/** Paid and in force. */
		ACTIVE,
		/** In force, but a renewal payment is overdue. */
		PAST_DUE,
		/** Ran out. */
		EXPIRED,
		/** Cancelled. */
		CANCELED;

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
	Subscription(@JsonProperty(value = "id", required = true) long id,
			@JsonProperty(value = "planId", required = true) int planId,
			@JsonProperty("planName") @Nullable String planName,
			@JsonProperty(value = "status", required = true) Status status,
			@JsonProperty("startDate") long startDate,
			@JsonProperty("endDate") long endDate,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt) {
		this.id = id;
		this.planId = planId;
		this.planName = planName;
		this.status = Objects.requireNonNull(status, "status");
		this.startDate = startDate;
		this.endDate = endDate;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the subscription id.
	 *
	 * @return the subscription id
	 */
	public long getId() {
		return id;
	}

	/**
	 * Returns the id of the subscribed plan.
	 *
	 * @return the plan id
	 */
	public int getPlanId() {
		return planId;
	}

	/**
	 * Returns the name of the subscribed plan.
	 *
	 * @return the plan name, if reported
	 */
	public Optional<String> getPlanName() {
		return Optional.ofNullable(planName);
	}

	/**
	 * Returns the state of the subscription.
	 *
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Returns when the subscription started.
	 *
	 * @return the start time, in epoch milliseconds
	 */
	public long getStartDate() {
		return startDate;
	}

	/**
	 * Returns when the subscription ends.
	 *
	 * @return the end time, in epoch milliseconds, or {@code 0} if it has no end date
	 */
	public long getEndDate() {
		return endDate;
	}

	/**
	 * Returns when the subscription was created.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the subscription was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "Subscription{id=" + id + ", planId=" + planId + ", status=" + status +
				", startDate=" + startDate + ", endDate=" + endDate + "}";
	}
}
