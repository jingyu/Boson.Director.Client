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

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * An entry of the node blacklist: a node, by id, or a host, by name or address, that this node
 * refuses to deal with. Obtained from a {@link DirectorAdmin}. Immutable.
 */
public class BlacklistedNode {
	private final long id;
	private final @Nullable Id nodeId;
	private final @Nullable String nodeHost;
	private final boolean auto;
	private final @Nullable String reason;
	private final long createdAt;
	private final long updatedAt;

	@JsonCreator
	BlacklistedNode(@JsonProperty(value = "id", required = true) long id,
			@JsonProperty("nodeId") @Nullable Id nodeId,
			@JsonProperty("nodeHost") @Nullable String nodeHost,
			@JsonProperty("auto") boolean auto,
			@JsonProperty("reason") @Nullable String reason,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt) {
		this.id = id;
		this.nodeId = nodeId;
		this.nodeHost = nodeHost;
		this.auto = auto;
		this.reason = reason;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the entry id.
	 *
	 * @return the entry id
	 */
	public long getId() {
		return id;
	}

	/**
	 * Returns the blacklisted node.
	 *
	 * @return the node id, if the entry names a node
	 */
	public Optional<Id> getNodeId() {
		return Optional.ofNullable(nodeId);
	}

	/**
	 * Returns the blacklisted host.
	 *
	 * @return the host name or address, if the entry names a host
	 */
	public Optional<String> getNodeHost() {
		return Optional.ofNullable(nodeHost);
	}

	/**
	 * Tells whether the node blacklisted the entry itself, for misbehaviour it detected, rather than an
	 * administrator. Automatic entries are lifted after a while.
	 *
	 * @return {@code true} for an automatic entry
	 */
	public boolean isAuto() {
		return auto;
	}

	/**
	 * Returns why the entry was blacklisted.
	 *
	 * @return the reason, if given
	 */
	public Optional<String> getReason() {
		return Optional.ofNullable(reason);
	}

	/**
	 * Returns when the entry was created.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the entry was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "BlacklistedNode{id=" + id + ", nodeId=" + nodeId + ", nodeHost=" + nodeHost +
				", auto=" + auto + ", reason=" + reason + "}";
	}
}
