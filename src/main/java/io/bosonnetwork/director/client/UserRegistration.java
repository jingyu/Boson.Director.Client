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
 * The account details to register a user with, for {@link DirectorClient#registerUser(UserRegistration)}.
 * <p>
 * Every field is optional. An initial device registers the client's device key in the same
 * request, so the user can sign in as that device from the start. For example:
 * <pre>{@code
 * director.registerUser(new UserRegistration()
 *         .name("Alice")
 *         .initialDevice("Alice's laptop", "MyApp"));
 * }</pre>
 * Not thread-safe.
 */
public class UserRegistration {
	private @Nullable String name;
	private @Nullable String email;
	private @Nullable String bio;
	private @Nullable String passphrase;
	private @Nullable String deviceName;
	private @Nullable String appName;

	/**
	 * Creates a registration with no details set.
	 */
	public UserRegistration() {
	}

	/**
	 * Sets the display name.
	 *
	 * @param name the name, or {@code null} for none
	 * @return this registration
	 */
	public UserRegistration name(@Nullable String name) {
		this.name = name;
		return this;
	}

	/**
	 * Sets the email address.
	 *
	 * @param email the email address, or {@code null} for none
	 * @return this registration
	 */
	public UserRegistration email(@Nullable String email) {
		this.email = email;
		return this;
	}

	/**
	 * Sets the biography.
	 *
	 * @param bio the biography, or {@code null} for none
	 * @return this registration
	 */
	public UserRegistration bio(@Nullable String bio) {
		this.bio = bio;
		return this;
	}

	/**
	 * Sets an account passphrase from the start. It can also be set later with
	 * {@link DirectorClient#setPassphrase(String)}.
	 *
	 * @param passphrase the passphrase, or {@code null} for none
	 * @return this registration
	 */
	public UserRegistration passphrase(@Nullable String passphrase) {
		this.passphrase = passphrase;
		return this;
	}

	/**
	 * Registers the client's device key as the user's first device, in the same request.
	 *
	 * @param deviceName a name for the device, shown to the user
	 * @param appName the name of the app the device runs
	 * @return this registration
	 */
	public UserRegistration initialDevice(String deviceName, String appName) {
		this.deviceName = Objects.requireNonNull(deviceName, "deviceName");
		this.appName = Objects.requireNonNull(appName, "appName");
		return this;
	}

	@Nullable String getName() {
		return name;
	}

	@Nullable String getEmail() {
		return email;
	}

	@Nullable String getBio() {
		return bio;
	}

	@Nullable String getPassphrase() {
		return passphrase;
	}

	@Nullable String getDeviceName() {
		return deviceName;
	}

	@Nullable String getAppName() {
		return appName;
	}

	boolean hasInitialDevice() {
		return deviceName != null;
	}
}
