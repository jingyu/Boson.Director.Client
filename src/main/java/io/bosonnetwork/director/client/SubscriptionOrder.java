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
 * A subscription the user ordered and the payment that settles it, as
 * {@link DirectorClient#subscribe(int, int)}, {@link DirectorClient#renewSubscription(long, int)} and
 * {@link DirectorClient#upgradeSubscription(long, int)} return them. The subscription is pending until
 * the payment is made; see {@link DirectorClient#submitPayment(long, PaymentTransaction)}. Immutable.
 */
public class SubscriptionOrder {
	private final Subscription subscription;
	private final Payment payment;

	@JsonCreator
	SubscriptionOrder(@JsonProperty(value = "subscription", required = true) Subscription subscription,
			@JsonProperty(value = "payment", required = true) Payment payment) {
		this.subscription = Objects.requireNonNull(subscription, "subscription");
		this.payment = Objects.requireNonNull(payment, "payment");
	}

	/**
	 * Returns the subscription ordered.
	 *
	 * @return the subscription
	 */
	public Subscription getSubscription() {
		return subscription;
	}

	/**
	 * Returns the payment that settles it: its amount, currency and id.
	 *
	 * @return the payment
	 */
	public Payment getPayment() {
		return payment;
	}

	@Override
	public String toString() {
		return "SubscriptionOrder{subscription=" + subscription + ", payment=" + payment + "}";
	}
}
