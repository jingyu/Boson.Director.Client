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

import org.jspecify.annotations.Nullable;

/**
 * The outcome of checking a held avatar against the Director, as returned by
 * {@link DirectorClient#refreshAvatar(Avatar)} and
 * {@link DirectorClient#refreshUserAvatar(io.bosonnetwork.Id, Avatar)}. Immutable.
 */
public class AvatarRefresh {
	/**
	 * What became of the held avatar.
	 */
	public enum Status {
		/** The held avatar is still current; nothing was downloaded. */
		UNCHANGED,
		/** The avatar changed; the new one was downloaded. */
		CHANGED,
		/** The user has no avatar any more, or is not known. */
		REMOVED
	}

	private final Status status;
	private final @Nullable Avatar avatar;

	private AvatarRefresh(Status status, @Nullable Avatar avatar) {
		this.status = status;
		this.avatar = avatar;
	}

	static AvatarRefresh unchanged(Avatar held) {
		return new AvatarRefresh(Status.UNCHANGED, Objects.requireNonNull(held, "held"));
	}

	static AvatarRefresh changed(Avatar current) {
		return new AvatarRefresh(Status.CHANGED, Objects.requireNonNull(current, "current"));
	}

	static AvatarRefresh removed() {
		return new AvatarRefresh(Status.REMOVED, null);
	}

	/**
	 * Returns what became of the held avatar.
	 *
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Returns the current avatar: the held one if it is unchanged, the downloaded one if it changed.
	 *
	 * @return the current avatar, or empty if it was {@linkplain Status#REMOVED removed}
	 */
	public Optional<Avatar> getAvatar() {
		return Optional.ofNullable(avatar);
	}

	@Override
	public String toString() {
		return "AvatarRefresh{status=" + status + ", avatar=" + avatar + "}";
	}
}
