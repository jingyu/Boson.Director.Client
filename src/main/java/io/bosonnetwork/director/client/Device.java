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
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * A device registered to a user, as listed by {@link DirectorClient#listDevices()}. Immutable.
 */
public class Device {
	private final Id id;
	private final Id userId;
	private final String name;
	private final String app;
	private final long createdAt;
	private final long updatedAt;
	private final long lastSeen;
	private final @Nullable String lastAddress;

	@JsonCreator
	Device(@JsonProperty(value = "id", required = true) Id id,
			@JsonProperty(value = "userId", required = true) Id userId,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty("app") @Nullable String app,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt,
			@JsonProperty("lastSeen") long lastSeen,
			@JsonProperty("lastAddress") @Nullable String lastAddress) {
		this.id = Objects.requireNonNull(id, "id");
		this.userId = Objects.requireNonNull(userId, "userId");
		// The Director leaves empty values out of the JSON.
		this.name = name != null ? name : "";
		this.app = app != null ? app : "";
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.lastSeen = lastSeen;
		this.lastAddress = lastAddress;
	}

	/**
	 * Returns the device id, derived from the device key.
	 *
	 * @return the device id
	 */
	public Id getId() {
		return id;
	}

	/**
	 * Returns the id of the user the device belongs to.
	 *
	 * @return the user id
	 */
	public Id getUserId() {
		return userId;
	}

	/**
	 * Returns the device name given at registration.
	 *
	 * @return the device name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the name of the app the device registered with.
	 *
	 * @return the app name
	 */
	public String getApp() {
		return app;
	}

	/**
	 * Returns when the device was registered.
	 *
	 * @return the registration time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the device record was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Returns when the device was last seen by the node.
	 *
	 * @return the time, in epoch milliseconds, or {@code 0} if never
	 */
	public long getLastSeen() {
		return lastSeen;
	}

	/**
	 * Returns the address the device was last seen from.
	 *
	 * @return the address, if known
	 */
	public Optional<String> getLastAddress() {
		return Optional.ofNullable(lastAddress);
	}

	@Override
	public String toString() {
		return "Device{id=" + id + ", name=" + name + ", app=" + app + ", lastSeen=" + lastSeen + "}";
	}
}
