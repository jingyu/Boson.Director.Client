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

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Which features to list, for {@link DirectorAdmin#listFeatures(FeatureFilter, Sort...)}. A filter
 * with nothing set matches every feature. Not thread-safe.
 */
public class FeatureFilter {
	private @Nullable String plan;
	private @Nullable String serviceId;

	/**
	 * Creates a filter that matches every feature.
	 */
	public FeatureFilter() {
	}

	/**
	 * Matches the features of one plan, named by id.
	 *
	 * @param planId the plan id
	 * @return this filter
	 * @throws IllegalArgumentException if the id is not positive
	 */
	public FeatureFilter plan(int planId) {
		if (planId <= 0)
			throw new IllegalArgumentException("Invalid planId: " + planId);
		this.plan = Integer.toString(planId);
		return this;
	}

	/**
	 * Matches the features of one plan, named by name. The Director reads a name made only of digits
	 * as a plan id.
	 *
	 * @param planName the plan name
	 * @return this filter
	 * @throws IllegalArgumentException if the name is empty
	 */
	public FeatureFilter plan(String planName) {
		Objects.requireNonNull(planName, "planName");
		if (planName.isEmpty())
			throw new IllegalArgumentException("planName is empty");
		this.plan = planName;
		return this;
	}

	/**
	 * Matches the features of one service.
	 *
	 * @param serviceId the service id
	 * @return this filter
	 * @throws IllegalArgumentException if the service id is empty
	 */
	public FeatureFilter service(String serviceId) {
		Objects.requireNonNull(serviceId, "serviceId");
		if (serviceId.isEmpty())
			throw new IllegalArgumentException("serviceId is empty");
		this.serviceId = serviceId;
		return this;
	}

	@Nullable String getPlan() {
		return plan;
	}

	@Nullable String getServiceId() {
		return serviceId;
	}
}
