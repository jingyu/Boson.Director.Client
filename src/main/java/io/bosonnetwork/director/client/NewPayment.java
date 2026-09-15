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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * A payment for an administrator to record, for {@link DirectorAdmin#addPayment(NewPayment)}.
 * <p>
 * A payment starts out {@linkplain Payment.Status#UNPAID unpaid} unless a status is set. A pending
 * or confirmed payment has to carry the transaction that settles it: the network, token, token
 * amount, wallets and transaction id. For example:
 * <pre>{@code
 * admin.addPayment(NewPayment.renewal(userId, subscriptionId, 12, new BigDecimal("50.00"), "USD")
 *         .status(Payment.Status.CONFIRMED)
 *         .network("tron").token("USDT").tokenAmount(new BigDecimal("50.00"))
 *         .walletTo(recipient).walletFrom(sender).transactionId(txId));
 * }</pre>
 * Not thread-safe.
 */
public class NewPayment {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	private NewPayment(Id userId, long subscriptionId, Payment.Intent intent, BigDecimal amount, String currency) {
		Objects.requireNonNull(userId, "userId");
		if (subscriptionId <= 0)
			throw new IllegalArgumentException("Invalid subscriptionId: " + subscriptionId);
		Objects.requireNonNull(amount, "amount");
		if (amount.signum() <= 0)
			throw new IllegalArgumentException("Invalid amount: " + amount);
		Objects.requireNonNull(currency, "currency");
		if (currency.isEmpty())
			throw new IllegalArgumentException("currency is empty");

		fields.put("userId", userId);
		fields.put("subscriptionId", subscriptionId);
		fields.put("intent", intent);
		fields.put("amount", amount);
		fields.put("currency", currency);
		fields.put("status", Payment.Status.UNPAID);
	}

	/**
	 * Creates the first payment of a new subscription.
	 *
	 * @param userId         the user who pays
	 * @param subscriptionId the subscription the payment is toward
	 * @param billingCycles  the months the payment covers
	 * @param amount         the amount due
	 * @param currency       the currency of the amount, such as {@code USD}
	 * @return the payment
	 * @throws IllegalArgumentException if an id, the billing cycles or the amount is not positive
	 */
	public static NewPayment subscription(Id userId, long subscriptionId, int billingCycles, BigDecimal amount,
			String currency) {
		return new NewPayment(userId, subscriptionId, Payment.Intent.SUBSCRIPTION, amount, currency)
				.billingCycles(billingCycles);
	}

	/**
	 * Creates a payment that extends a subscription.
	 *
	 * @param userId         the user who pays
	 * @param subscriptionId the subscription the payment is toward
	 * @param billingCycles  the months the payment adds
	 * @param amount         the amount due
	 * @param currency       the currency of the amount, such as {@code USD}
	 * @return the payment
	 * @throws IllegalArgumentException if an id, the billing cycles or the amount is not positive
	 */
	public static NewPayment renewal(Id userId, long subscriptionId, int billingCycles, BigDecimal amount,
			String currency) {
		return new NewPayment(userId, subscriptionId, Payment.Intent.RENEW, amount, currency)
				.billingCycles(billingCycles);
	}

	/**
	 * Creates a payment that moves a subscription to another plan.
	 *
	 * @param userId         the user who pays
	 * @param subscriptionId the subscription the payment is toward
	 * @param oldPlanId      the plan the subscription moves from
	 * @param newPlanId      the plan the subscription moves to
	 * @param amount         the amount due
	 * @param currency       the currency of the amount, such as {@code USD}
	 * @return the payment
	 * @throws IllegalArgumentException if an id or the amount is not positive
	 */
	public static NewPayment upgrade(Id userId, long subscriptionId, int oldPlanId, int newPlanId, BigDecimal amount,
			String currency) {
		if (oldPlanId <= 0)
			throw new IllegalArgumentException("Invalid oldPlanId: " + oldPlanId);
		if (newPlanId <= 0)
			throw new IllegalArgumentException("Invalid newPlanId: " + newPlanId);

		NewPayment payment = new NewPayment(userId, subscriptionId, Payment.Intent.UPGRADE, amount, currency);
		payment.fields.put("oldPlanId", oldPlanId);
		payment.fields.put("newPlanId", newPlanId);
		return payment;
	}

	private NewPayment billingCycles(int billingCycles) {
		if (billingCycles <= 0)
			throw new IllegalArgumentException("Invalid billingCycles: " + billingCycles);
		fields.put("billingCycles", billingCycles);
		return this;
	}

	/**
	 * Sets the state the payment is recorded in.
	 *
	 * @param status the status
	 * @return this payment
	 */
	public NewPayment status(Payment.Status status) {
		fields.put("status", Objects.requireNonNull(status, "status"));
		return this;
	}

	/**
	 * Sets the blockchain network the payment was made on.
	 *
	 * @param network the network
	 * @return this payment
	 */
	public NewPayment network(String network) {
		fields.put("network", Objects.requireNonNull(network, "network"));
		return this;
	}

	/**
	 * Sets the token the payment was made in, such as {@code USDT}.
	 *
	 * @param token the token
	 * @return this payment
	 */
	public NewPayment token(String token) {
		fields.put("token", Objects.requireNonNull(token, "token"));
		return this;
	}

	/**
	 * Sets the amount of the token paid.
	 *
	 * @param tokenAmount the token amount
	 * @return this payment
	 * @throws IllegalArgumentException if the amount is not positive
	 */
	public NewPayment tokenAmount(BigDecimal tokenAmount) {
		Objects.requireNonNull(tokenAmount, "tokenAmount");
		if (tokenAmount.signum() <= 0)
			throw new IllegalArgumentException("Invalid tokenAmount: " + tokenAmount);
		fields.put("tokenAmount", tokenAmount);
		return this;
	}

	/**
	 * Sets the wallet the payment was made to.
	 *
	 * @param walletTo the recipient wallet
	 * @return this payment
	 */
	public NewPayment walletTo(String walletTo) {
		fields.put("walletTo", Objects.requireNonNull(walletTo, "walletTo"));
		return this;
	}

	/**
	 * Sets the wallet the payment was made from.
	 *
	 * @param walletFrom the sender wallet
	 * @return this payment
	 */
	public NewPayment walletFrom(String walletFrom) {
		fields.put("walletFrom", Objects.requireNonNull(walletFrom, "walletFrom"));
		return this;
	}

	/**
	 * Sets the id of the transaction that settles the payment.
	 *
	 * @param transactionId the transaction id
	 * @return this payment
	 */
	public NewPayment transactionId(String transactionId) {
		fields.put("transactionId", Objects.requireNonNull(transactionId, "transactionId"));
		return this;
	}

	// The fields to send, keyed by their wire names.
	Map<String, @Nullable Object> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
