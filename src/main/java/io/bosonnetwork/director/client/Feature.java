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
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * What a plan grants on one service: a feature document that the service reads, such as its limits
 * for the plan's subscribers. Obtained from a {@link DirectorAdmin}. Immutable.
 */
public class Feature {
	private final int id;
	private final int planId;
	private final @Nullable String planName;
	private final String serviceId;
	private final Map<String, @Nullable Object> feature;
	private final long createdAt;
	private final long updatedAt;

	@JsonCreator
	Feature(@JsonProperty(value = "id", required = true) int id,
			@JsonProperty(value = "planId", required = true) int planId,
			@JsonProperty("planName") @Nullable String planName,
			@JsonProperty(value = "serviceId", required = true) String serviceId,
			@JsonProperty("feature") @Nullable Map<String, @Nullable Object> feature,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt) {
		this.id = id;
		this.planId = planId;
		this.planName = planName;
		this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
		Map<String, @Nullable Object> copy = new LinkedHashMap<>();
		if (feature != null)
			copy.putAll(feature);
		this.feature = Collections.unmodifiableMap(copy);
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the feature id.
	 *
	 * @return the feature id
	 */
	public int getId() {
		return id;
	}

	/**
	 * Returns the id of the plan the feature belongs to.
	 *
	 * @return the plan id
	 */
	public int getPlanId() {
		return planId;
	}

	/**
	 * Returns the name of the plan the feature belongs to.
	 *
	 * @return the plan name, if reported
	 */
	public Optional<String> getPlanName() {
		return Optional.ofNullable(planName);
	}

	/**
	 * Returns the id of the service the feature applies to, such as {@code io.bosonnetwork.ionstore}.
	 *
	 * @return the service id
	 */
	public String getServiceId() {
		return serviceId;
	}

	/**
	 * Returns the feature document, as the service reads it.
	 *
	 * @return the feature document, possibly empty
	 */
	public Map<String, @Nullable Object> getFeature() {
		return feature;
	}

	/**
	 * Returns when the feature was created.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the feature was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "Feature{id=" + id + ", planId=" + planId + ", serviceId=" + serviceId + ", feature=" + feature + "}";
	}
}
