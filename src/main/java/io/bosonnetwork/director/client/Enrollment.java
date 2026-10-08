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

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * An administrator's enrollment request, as the console sees it: its state, how long it has left, and,
 * once a Boson Identity app claimed it, who claimed it. It never holds the number the app shows: the owner
 * reads it on the phone and types it to {@link DirectorAdmin#approveEnrollment(Id, int)}. Immutable.
 *
 * @see DirectorAdmin#createEnrollment()
 * @see DirectorAdmin#awaitEnrollmentClaim(Id)
 */
public final class Enrollment {
	/** Where an enrollment request is in its life. */
	public enum State {
		/** Waiting for an app to claim it. */
		OPEN,
		/** Claimed by an app, waiting for the console's number. */
		CLAIMED,
		/** Approved: the claimant is an administrator. */
		APPROVED,
		/** Denied on the console, or after too many wrong numbers. */
		DENIED,
		/** Ended unanswered. */
		EXPIRED;

		static State of(String value) {
			return valueOf(value.toUpperCase(Locale.ROOT));
		}

		/**
		 * Tells whether the request has ended: approved, denied or expired.
		 *
		 * @return whether the state is final
		 */
		public boolean isFinal() {
			return this == APPROVED || this == DENIED || this == EXPIRED;
		}
	}

	private final Id requestId;
	private final State state;
	private final long expiresAt;
	private final int triesLeft;
	private final @Nullable String url;
	private final @Nullable Id userId;
	private final @Nullable Id deviceId;
	private final @Nullable String deviceName;
	private final @Nullable String app;
	private final boolean member;
	private final @Nullable String requestedFrom;

	private Enrollment(JsonObject json) {
		this.requestId = Id.of(DirectorTransport.requiredString(json, "requestId"));
		String stateText = json.getString("state");
		this.state = stateText == null ? State.OPEN : State.of(stateText);
		this.expiresAt = System.currentTimeMillis() + json.getLong("expiresIn", 0L) * 1000;
		this.triesLeft = json.getInteger("triesLeft", 0);
		this.url = json.getString("url");
		String user = json.getString("userId");
		this.userId = user == null ? null : Id.of(user);
		String device = json.getString("deviceId");
		this.deviceId = device == null ? null : Id.of(device);
		this.deviceName = json.getString("deviceName");
		this.app = json.getString("app");
		this.member = json.getBoolean("member", false);
		this.requestedFrom = json.getString("requestedFrom");
	}

	static Enrollment fromJson(JsonObject json) {
		return new Enrollment(json);
	}

	/**
	 * Returns the request's id.
	 *
	 * @return the request id
	 */
	public Id getRequestId() {
		return requestId;
	}

	/**
	 * Returns the request's state.
	 *
	 * @return the state
	 */
	public State getState() {
		return state;
	}

	/**
	 * Returns when the request expires, by the local clock, in milliseconds since the epoch.
	 *
	 * @return the expiry time
	 */
	public long getExpiresAt() {
		return expiresAt;
	}

	/**
	 * Returns how many numbers the console may still type before the request is denied.
	 *
	 * @return the tries left
	 */
	public int getTriesLeft() {
		return triesLeft;
	}

	/**
	 * Returns the Director's address as apps reach it, for the {@link EnrollmentCode}: in the answer to
	 * {@link DirectorAdmin#createEnrollment()} only, and only when the Director knows its public address.
	 *
	 * @return the URL, if known
	 */
	public Optional<String> getUrl() {
		return Optional.ofNullable(url);
	}

	/**
	 * Returns the user who claimed the request.
	 *
	 * @return the user id, or empty while the request is unclaimed
	 */
	public Optional<Id> getUserId() {
		return Optional.ofNullable(userId);
	}

	/**
	 * Returns the device that claimed the request.
	 *
	 * @return the device id, or empty while the request is unclaimed
	 */
	public Optional<Id> getDeviceId() {
		return Optional.ofNullable(deviceId);
	}

	/**
	 * Returns the name of the device that claimed the request.
	 *
	 * @return the device name, or empty while the request is unclaimed
	 */
	public Optional<String> getDeviceName() {
		return Optional.ofNullable(deviceName);
	}

	/**
	 * Returns the app that claimed the request.
	 *
	 * @return the app name, or empty while the request is unclaimed
	 */
	public Optional<String> getApp() {
		return Optional.ofNullable(app);
	}

	/**
	 * Tells whether the claimant already has an account on the node: approving makes that account an
	 * administrator's, rather than creating one.
	 *
	 * @return whether the claimant is a member
	 */
	public boolean isMember() {
		return member;
	}

	/**
	 * Returns the address the claim came from, as the Director saw it.
	 *
	 * @return the address, if known
	 */
	public Optional<String> getRequestedFrom() {
		return Optional.ofNullable(requestedFrom);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Enrollment that && requestId.equals(that.requestId) && state == that.state &&
				triesLeft == that.triesLeft && Objects.equals(userId, that.userId) &&
				Objects.equals(deviceId, that.deviceId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(requestId, state, triesLeft, userId, deviceId);
	}

	@Override
	public String toString() {
		return "Enrollment{requestId=" + requestId + ", state=" + state + ", userId=" + userId + "}";
	}
}
