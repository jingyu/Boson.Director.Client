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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The changes to make to a subscription, for {@link DirectorAdmin#updateSubscription(long, SubscriptionUpdate)}.
 * <p>
 * Only the fields set here are changed. Not thread-safe.
 */
public class SubscriptionUpdate {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates an update that changes nothing yet.
	 */
	public SubscriptionUpdate() {
	}

	/**
	 * Sets the state of the subscription.
	 *
	 * @param status the new status
	 * @return this update
	 */
	public SubscriptionUpdate status(Subscription.Status status) {
		fields.put("status", Objects.requireNonNull(status, "status"));
		return this;
	}

	/**
	 * Sets when the subscription ends. The Director requires a time in the future.
	 *
	 * @param endDate the new end time, in epoch milliseconds
	 * @return this update
	 * @throws IllegalArgumentException if the time is not positive
	 */
	public SubscriptionUpdate endDate(long endDate) {
		if (endDate <= 0)
			throw new IllegalArgumentException("Invalid endDate: " + endDate);
		fields.put("endDate", endDate);
		return this;
	}

	/**
	 * Moves the subscription to another plan. The free plan needs no subscription, so the Director
	 * refuses to move one to it.
	 *
	 * @param planId the id of the new plan
	 * @return this update
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public SubscriptionUpdate planId(int planId) {
		if (planId <= 0)
			throw new IllegalArgumentException("Invalid planId: " + planId);
		fields.put("planId", planId);
		return this;
	}

	/**
	 * Tells whether the update changes nothing.
	 *
	 * @return {@code true} if no field is set
	 */
	public boolean isEmpty() {
		return fields.isEmpty();
	}

	// The fields to send, keyed by their wire names.
	Map<String, ?> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
