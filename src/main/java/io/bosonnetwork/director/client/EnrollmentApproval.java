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

import io.vertx.core.json.JsonObject;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * The answer to {@link DirectorAdmin#approveEnrollment(Id, int)}: approved, with the user made an
 * administrator, or refused for a wrong number, with the tries left. Immutable.
 */
public final class EnrollmentApproval {
	private final boolean approved;
	private final int triesLeft;
	private final @Nullable Id userId;
	private final boolean created;

	private EnrollmentApproval(JsonObject json) {
		this.approved = json.getBoolean("approved", false);
		this.triesLeft = json.getInteger("triesLeft", 0);
		String user = json.getString("userId");
		this.userId = user == null ? null : Id.of(user);
		this.created = json.getBoolean("created", false);
		if (approved && userId == null)
			throw new IllegalArgumentException("missing 'userId'");
	}

	static EnrollmentApproval fromJson(JsonObject json) {
		return new EnrollmentApproval(json);
	}

	/**
	 * Tells whether the number was right and the request approved.
	 *
	 * @return whether the request was approved
	 */
	public boolean isApproved() {
		return approved;
	}

	/**
	 * Returns how many numbers may still be typed, after a wrong one: none means the request is denied.
	 *
	 * @return the tries left; 0 once approved
	 */
	public int getTriesLeft() {
		return triesLeft;
	}

	/**
	 * Returns the user made an administrator.
	 *
	 * @return the user id, or empty if the request was not approved
	 */
	public Optional<Id> getUserId() {
		return Optional.ofNullable(userId);
	}

	/**
	 * Tells whether the approval created the user's account; otherwise a member was made an administrator.
	 *
	 * @return whether the account is new
	 */
	public boolean isCreated() {
		return created;
	}

	@Override
	public String toString() {
		return approved ? "EnrollmentApproval{approved, userId=" + userId + ", created=" + created + "}" :
				"EnrollmentApproval{refused, triesLeft=" + triesLeft + "}";
	}
}
