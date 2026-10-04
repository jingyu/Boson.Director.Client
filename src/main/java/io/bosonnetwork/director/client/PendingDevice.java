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
 * A device asking to join a user's account, as an approving device sees it with
 * {@link DirectorClient#getDeviceRegistration(PairingCode)}: everything the user needs to judge the request.
 * Immutable.
 *
 * @see DirectorGuest#requestDeviceRegistration(io.bosonnetwork.crypto.Signature.KeyPair, String, String, boolean)
 */
public class PendingDevice {
	private final Id deviceId;
	private final String deviceName;
	private final String appName;
	private final String kind;
	private final boolean wantsUserKey;
	private final byte @Nullable [] nonce;
	private final @Nullable String requestedFrom;
	private final long createdAt;
	private final long expiresAt;

	@JsonCreator
	PendingDevice(@JsonProperty(value = "deviceId", required = true) Id deviceId,
			@JsonProperty("deviceName") @Nullable String deviceName,
			@JsonProperty("appName") @Nullable String appName,
			@JsonProperty("kind") @Nullable String kind,
			@JsonProperty("wantsUserKey") boolean wantsUserKey,
			@JsonProperty("nonce") byte @Nullable [] nonce,
			@JsonProperty("requestedFrom") @Nullable String requestedFrom,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("expiresAt") long expiresAt) {
		this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
		this.deviceName = deviceName != null ? deviceName : "";
		this.appName = appName != null ? appName : "";
		this.kind = kind != null ? kind : Device.KIND_APP;
		this.wantsUserKey = wantsUserKey;
		this.nonce = nonce;
		this.requestedFrom = requestedFrom;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/**
	 * Returns the id of the device asking to join.
	 *
	 * @return the device id
	 */
	public Id getDeviceId() {
		return deviceId;
	}

	/**
	 * Returns the name the device gave itself.
	 *
	 * @return the device name
	 */
	public String getDeviceName() {
		return deviceName;
	}

	/**
	 * Returns the name of the app asking to join.
	 *
	 * @return the app name
	 */
	public String getAppName() {
		return appName;
	}

	/**
	 * Returns what holds the device's key: {@link Device#KIND_APP} or {@link Device#KIND_PASSKEY}.
	 *
	 * @return the device kind
	 */
	public String getKind() {
		return kind;
	}

	/**
	 * Returns whether the device asks for the user key. Approving it then hands the key over, sealed to
	 * the pairing code; otherwise the device joins with its own key only.
	 *
	 * @return whether the device wants the user key
	 */
	public boolean wantsUserKey() {
		return wantsUserKey;
	}

	/**
	 * Returns the address the request came from, as the Director saw it.
	 *
	 * @return the address, if known
	 */
	public Optional<String> getRequestedFrom() {
		return Optional.ofNullable(requestedFrom);
	}

	/**
	 * Returns when the request was made.
	 *
	 * @return the time, in epoch milliseconds, or {@code 0} if the Director did not say
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the request expires unanswered.
	 *
	 * @return the time, in epoch milliseconds, or {@code 0} if the Director did not say
	 */
	public long getExpiresAt() {
		return expiresAt;
	}

	// The nonce the device signed, which an approver signs with the device id to make it an administrator's.
	byte @Nullable [] nonce() {
		return nonce;
	}

	@Override
	public String toString() {
		return "PendingDevice{deviceId=" + deviceId + ", deviceName=" + deviceName + ", appName=" + appName +
				", kind=" + kind + ", wantsUserKey=" + wantsUserKey + "}";
	}
}
