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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * A device asking to join a user's account, as an approving device sees it with
 * {@link DirectorClient#getDeviceRegistration(PairingCode)}. Immutable.
 *
 * @see DirectorGuest#requestDeviceRegistration(io.bosonnetwork.crypto.Signature.KeyPair, String, String)
 */
public class PendingDevice {
	private final Id deviceId;
	private final String deviceName;
	private final String appName;

	@JsonCreator
	PendingDevice(@JsonProperty(value = "deviceId", required = true) Id deviceId,
			@JsonProperty("deviceName") @Nullable String deviceName,
			@JsonProperty("appName") @Nullable String appName) {
		this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
		this.deviceName = deviceName != null ? deviceName : "";
		this.appName = appName != null ? appName : "";
	}

	/**
	 * Returns the id of the device, derived from its key.
	 *
	 * @return the device id
	 */
	public Id getDeviceId() {
		return deviceId;
	}

	/**
	 * Returns the name the device asked to be registered under.
	 *
	 * @return the device name, empty if none was given
	 */
	public String getDeviceName() {
		return deviceName;
	}

	/**
	 * Returns the name of the app the device runs.
	 *
	 * @return the app name, empty if none was given
	 */
	public String getAppName() {
		return appName;
	}

	@Override
	public String toString() {
		return "PendingDevice{deviceId=" + deviceId + ", deviceName=" + deviceName + ", appName=" + appName + "}";
	}
}
