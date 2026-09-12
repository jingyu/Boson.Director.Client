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
 * The plan a user is on, as returned by {@link DirectorClient#getPlan()}: its name, its details from
 * the node's plan catalog, and the subscription that grants it. Immutable.
 * <p>
 * A user without an active subscription is on the node's free plan, and then has no subscription.
 */
public class UserPlan {
	private final String name;
	private final @Nullable Plan plan;
	private final @Nullable Subscription subscription;

	UserPlan(String name, @Nullable Plan plan, @Nullable Subscription subscription) {
		this.name = Objects.requireNonNull(name, "name");
		this.plan = plan;
		this.subscription = subscription;
	}

	/**
	 * Returns the name of the plan.
	 *
	 * @return the plan name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the plan's details from the node's catalog. They are absent only if the node no longer
	 * offers the plan, since the catalog lists the plans on offer.
	 *
	 * @return the plan details, if the node lists the plan
	 */
	public Optional<Plan> getPlan() {
		return Optional.ofNullable(plan);
	}

	/**
	 * Returns the subscription that grants the plan.
	 *
	 * @return the active subscription, or empty on the free plan
	 */
	public Optional<Subscription> getSubscription() {
		return Optional.ofNullable(subscription);
	}

	@Override
	public String toString() {
		return "UserPlan{name=" + name + ", plan=" + plan + ", subscription=" + subscription + "}";
	}
}
