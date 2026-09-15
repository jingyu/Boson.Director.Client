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

import org.jspecify.annotations.Nullable;

/**
 * The changes to make to what this node records about a federated node, for
 * {@link DirectorAdmin#updateFederatedNode(io.bosonnetwork.Id, FederatedNodeUpdate)}.
 * <p>
 * Only the fields set here are changed. Not thread-safe.
 */
public class FederatedNodeUpdate {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates an update that changes nothing yet.
	 */
	public FederatedNodeUpdate() {
	}

	/**
	 * Suspends the federation with the node, or resumes it.
	 *
	 * @param federated whether the federation is in force
	 * @return this update
	 */
	public FederatedNodeUpdate federated(boolean federated) {
		fields.put("federated", federated);
		return this;
	}

	/**
	 * Sets the reputation this node gives the federated node.
	 *
	 * @param reputation the new reputation
	 * @return this update
	 */
	public FederatedNodeUpdate reputation(int reputation) {
		fields.put("reputation", reputation);
		return this;
	}

	/**
	 * Sets this node's description of the federated node.
	 *
	 * @param description the new description, or {@code null} to clear it
	 * @return this update
	 */
	public FederatedNodeUpdate description(@Nullable String description) {
		fields.put("description", description);
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
