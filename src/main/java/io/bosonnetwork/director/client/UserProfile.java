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
 * The public profile of any user, as returned by {@link DirectorClient#getUserProfile(Id)}: what the
 * user shows to others, without the private parts of the account. Immutable.
 * <p>
 * A user of another super node is resolved through that node, and carries the node it lives on.
 */
public class UserProfile {
	private final Id id;
	private final @Nullable String name;
	private final @Nullable String avatar;
	private final @Nullable String bio;
	private final @Nullable Id homeNode;
	private final @Nullable Id messagingHomePeer;

	@JsonCreator
	UserProfile(@JsonProperty(value = "id", required = true) Id id,
			@JsonProperty("name") @Nullable String name,
			@JsonProperty("avatar") @Nullable String avatar,
			@JsonProperty("bio") @Nullable String bio,
			@JsonProperty("homeNode") @Nullable Id homeNode,
			@JsonProperty("messagingHomePeer") @Nullable Id messagingHomePeer) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = name;
		this.avatar = avatar;
		this.bio = bio;
		this.homeNode = homeNode;
		this.messagingHomePeer = messagingHomePeer;
	}

	/**
	 * Returns the user id.
	 *
	 * @return the user id
	 */
	public Id getId() {
		return id;
	}

	/**
	 * Returns the user's display name.
	 *
	 * @return the name, or empty if the user has none
	 */
	public Optional<String> getName() {
		return Optional.ofNullable(name);
	}

	/**
	 * Returns the URI of the user's avatar. Its presence tells that the user has one; the image itself
	 * is downloaded with {@link DirectorClient#getUserAvatar(Id)}.
	 *
	 * @return the avatar URI, or empty if the user has no avatar
	 */
	public Optional<String> getAvatar() {
		return Optional.ofNullable(avatar);
	}

	/**
	 * Returns the user's bio.
	 *
	 * @return the bio, or empty if the user has none
	 */
	public Optional<String> getBio() {
		return Optional.ofNullable(bio);
	}

	/**
	 * Returns the id of the super node the user is registered on.
	 *
	 * @return the home node id, or empty if the Director did not report it
	 */
	public Optional<Id> getHomeNode() {
		return Optional.ofNullable(homeNode);
	}

	/**
	 * Returns the peer id of the messaging service on the user's home node.
	 *
	 * @return the messaging peer id, or empty if the Director did not report it
	 */
	public Optional<Id> getMessagingHomePeer() {
		return Optional.ofNullable(messagingHomePeer);
	}

	@Override
	public String toString() {
		return "UserProfile{id=" + id + ", name=" + name + ", homeNode=" + homeNode + "}";
	}
}
