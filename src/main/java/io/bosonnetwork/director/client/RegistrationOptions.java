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

/**
 * How a Director signs users up, as {@link DirectorGuest#getRegistrationOptions()} reports it. Immutable.
 */
public class RegistrationOptions {
	private final String policy;
	private final boolean proofOfWork;
	private final boolean oauth;

	@JsonCreator
	RegistrationOptions(@JsonProperty(value = "policy", required = true) String policy,
			@JsonProperty("proofOfWork") boolean proofOfWork,
			@JsonProperty("oauth") boolean oauth) {
		this.policy = Objects.requireNonNull(policy, "policy");
		this.proofOfWork = proofOfWork;
		this.oauth = oauth;
	}

	/**
	 * Returns the node's registration policy, as the Director names it: {@code open}, {@code oauth},
	 * {@code pow} or {@code either}. The two flags below say what it means for a client.
	 *
	 * @return the policy name
	 */
	public String getPolicy() {
		return policy;
	}

	/**
	 * Tells whether the node accepts permissionless registration proven with proof-of-work - the
	 * registration {@link DirectorClient#registerUser(UserRegistration)} makes.
	 *
	 * @return {@code true} if proof-of-work registration is accepted
	 */
	public boolean isProofOfWorkEnabled() {
		return proofOfWork;
	}

	/**
	 * Tells whether users can sign up through OAuth: the node has at least one provider, which
	 * {@link DirectorGuest#getProviders()} lists.
	 *
	 * @return {@code true} if OAuth sign-up is possible
	 */
	public boolean isOAuthEnabled() {
		return oauth;
	}

	@Override
	public String toString() {
		return "RegistrationOptions{policy=" + policy + ", proofOfWork=" + proofOfWork + ", oauth=" + oauth + "}";
	}
}
