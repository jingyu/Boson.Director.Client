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
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * Which federation proposals to list, for {@link DirectorAdmin#listFederationProposals(ProposalFilter)}
 * and its paged overload. A filter with nothing set matches every proposal. Not thread-safe.
 */
public class ProposalFilter {
	private @Nullable Id nodeId;
	private FederationProposal.@Nullable Role role;
	private final Set<FederationProposal.Status> statuses = EnumSet.noneOf(FederationProposal.Status.class);

	/**
	 * Creates a filter that matches every proposal.
	 */
	public ProposalFilter() {
	}

	/**
	 * Matches the proposals with one node.
	 *
	 * @param nodeId the other node's id
	 * @return this filter
	 */
	public ProposalFilter nodeId(Id nodeId) {
		this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
		return this;
	}

	/**
	 * Matches the proposals this node made, or those it received.
	 *
	 * @param role the side of the proposal this node is on
	 * @return this filter
	 */
	public ProposalFilter role(FederationProposal.Role role) {
		this.role = Objects.requireNonNull(role, "role");
		return this;
	}

	/**
	 * Matches the proposals in any of the given states, replacing the states set before.
	 *
	 * @param statuses the states to match
	 * @return this filter
	 */
	public ProposalFilter statuses(FederationProposal.Status... statuses) {
		this.statuses.clear();
		for (FederationProposal.Status status : statuses)
			this.statuses.add(Objects.requireNonNull(status, "status"));
		return this;
	}

	@Nullable Id getNodeId() {
		return nodeId;
	}

	FederationProposal.@Nullable Role getRole() {
		return role;
	}

	Set<FederationProposal.Status> getStatuses() {
		return Collections.unmodifiableSet(statuses);
	}
}
