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
 * The changes to make to a feature, for {@link DirectorAdmin#updateFeature(int, FeatureUpdate)}.
 * <p>
 * Only the fields set here are changed. Not thread-safe.
 */
public class FeatureUpdate {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates an update that changes nothing yet.
	 */
	public FeatureUpdate() {
	}

	/**
	 * Moves the feature to another service.
	 *
	 * @param serviceId the new service id
	 * @return this update
	 * @throws IllegalArgumentException if the service id is empty
	 */
	public FeatureUpdate serviceId(String serviceId) {
		Objects.requireNonNull(serviceId, "serviceId");
		if (serviceId.isEmpty())
			throw new IllegalArgumentException("serviceId is empty");
		fields.put("serviceId", serviceId);
		return this;
	}

	/**
	 * Replaces the feature document.
	 *
	 * @param feature the new document, or {@code null} or empty to clear it
	 * @return this update
	 */
	public FeatureUpdate feature(@Nullable Map<String, ?> feature) {
		fields.put("feature", feature != null ? new LinkedHashMap<String, @Nullable Object>(feature) : null);
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
	Map<String, @Nullable Object> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
