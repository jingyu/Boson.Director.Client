/*
 * Copyright (c) 2026 -      bosonnetwork.io
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

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Local descriptor-check observations at a destination, obtained from a {@link DirectorAdmin}.
 * Immutable. IPv4 addresses are counted individually, IPv6 addresses by their /64. An absent host
 * groups checks that failed before a destination address was selected.
 * <p>
 * Counts describe distinct node IDs, not requests or service peers. Verified nodes have retained,
 * unexpired descriptors; unsuccessful observations describe each node's latest completed check
 * within the last hour. A failed refresh can coexist with a valid descriptor, so the columns can
 * overlap. Repeated failures do not add nodes, and a later successful check clears a prior failure.
 * <p>
 * These observations are bounded and kept in memory, so they start empty after a restart. They are
 * not a network-wide catalog or an audit log, and reading them does not start remote lookups.
 */
public class FederatedHost {
	private final @Nullable String host;
	private final int verifiedNodes;
	private final int failedNodes;
	private final int notFederatingNodes;
	private final long lastCheckedAt;

	@JsonCreator
	FederatedHost(@JsonProperty(value = "host", required = true) @Nullable String host,
			@JsonProperty(value = "verifiedNodes", required = true) int verifiedNodes,
			@JsonProperty(value = "failedNodes", required = true) int failedNodes,
			@JsonProperty(value = "notFederatingNodes", required = true) int notFederatingNodes,
			@JsonProperty(value = "lastCheckedAt", required = true) long lastCheckedAt) {
		this.host = host;
		this.verifiedNodes = verifiedNodes;
		this.failedNodes = failedNodes;
		this.notFederatingNodes = notFederatingNodes;
		this.lastCheckedAt = lastCheckedAt;
	}

	/**
	 * Returns the selected IPv4 address or IPv6 /64.
	 *
	 * @return the host, or empty for checks that failed before address selection
	 */
	public Optional<String> getHost() {
		return Optional.ofNullable(host);
	}

	/**
	 * Returns how many nodes have retained, unexpired verified descriptors at this host.
	 *
	 * @return the number of verified nodes; zero for the unresolved-host group
	 */
	public int getVerifiedNodes() {
		return verifiedNodes;
	}

	/**
	 * Returns how many nodes' latest descriptor checks failed within the last hour. This includes
	 * invalid descriptors and checks that could not complete, such as network failures or local
	 * budget refusals; it does not imply that the host is malicious.
	 *
	 * @return the number of nodes with failed checks
	 */
	public int getFailedNodes() {
		return failedNodes;
	}

	/**
	 * Returns how many nodes' latest checks within the last hour reported that they do not federate.
	 * This includes HTTP 404/501 and verified descriptors with an absent or unrecognized mode.
	 *
	 * @return the number of non-federating nodes
	 */
	public int getNotFederatingNodes() {
		return notFederatingNodes;
	}

	/**
	 * Returns the latest check completion represented by this observation.
	 *
	 * @return the time in epoch milliseconds
	 */
	public long getLastCheckedAt() {
		return lastCheckedAt;
	}

	@Override
	public String toString() {
		return "FederatedHost{host=" + host + ", verifiedNodes=" + verifiedNodes + ", failedNodes=" + failedNodes +
				", notFederatingNodes=" + notFederatingNodes + "}";
	}
}
