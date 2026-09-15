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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import io.bosonnetwork.Id;

/**
 * A payment toward a user's subscription, as the Director records it. Obtained from a
 * {@link DirectorAdmin}. Immutable.
 * <p>
 * A crypto payment carries the transaction that settles it: the network, the token and its amount,
 * the two wallets and the transaction id. They are absent until the payment has been submitted.
 */
public class Payment {
	private final long id;
	private final Id userId;
	private final long subscriptionId;
	private final Intent intent;
	private final int billingCycles;
	private final int oldPlanId;
	private final int newPlanId;
	private final BigDecimal amount;
	private final String currency;
	private final Status status;
	private final @Nullable String paymentMethod;
	private final @Nullable String gateway;
	private final @Nullable String gatewayPaymentId;
	private final @Nullable String gatewayStatus;
	private final @Nullable String network;
	private final @Nullable String token;
	private final @Nullable BigDecimal tokenAmount;
	private final @Nullable String walletTo;
	private final @Nullable String walletFrom;
	private final @Nullable String transactionId;
	private final long confirmedAt;
	private final long expiredAt;
	private final long createdAt;
	private final long updatedAt;

	/**
	 * What a payment is for.
	 */
	public enum Intent {
		/** The first payment of a new subscription. */
		SUBSCRIPTION,
		/** Extends a subscription. */
		RENEW,
		/** Moves a subscription to another plan. */
		UPGRADE;

		@JsonCreator
		static Intent of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}

		@JsonValue
		@Override
		public String toString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * The state of a payment.
	 */
	public enum Status {
		/** Created, not paid yet. */
		UNPAID,
		/** Paid, waiting for the transaction to be confirmed. */
		PENDING,
		/** Confirmed. */
		CONFIRMED,
		/** Not paid in time. */
		EXPIRED,
		/** Cancelled. */
		CANCELED,
		/** The transaction could not be confirmed. */
		FAILED;

		@JsonCreator
		static Status of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}

		@JsonValue
		@Override
		public String toString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	@JsonCreator
	Payment(@JsonProperty(value = "id", required = true) long id,
			@JsonProperty(value = "userId", required = true) Id userId,
			@JsonProperty(value = "subscriptionId", required = true) long subscriptionId,
			@JsonProperty(value = "intent", required = true) Intent intent,
			@JsonProperty("billingCycles") int billingCycles,
			@JsonProperty("oldPlanId") int oldPlanId,
			@JsonProperty("newPlanId") int newPlanId,
			@JsonProperty(value = "amount", required = true) BigDecimal amount,
			@JsonProperty(value = "currency", required = true) String currency,
			@JsonProperty(value = "status", required = true) Status status,
			@JsonProperty("paymentMethod") @Nullable String paymentMethod,
			@JsonProperty("gateway") @Nullable String gateway,
			@JsonProperty("gatewayPaymentId") @Nullable String gatewayPaymentId,
			@JsonProperty("gatewayStatus") @Nullable String gatewayStatus,
			@JsonProperty("network") @Nullable String network,
			@JsonProperty("token") @Nullable String token,
			@JsonProperty("tokenAmount") @Nullable BigDecimal tokenAmount,
			@JsonProperty("walletTo") @Nullable String walletTo,
			@JsonProperty("walletFrom") @Nullable String walletFrom,
			@JsonProperty("transactionId") @Nullable String transactionId,
			@JsonProperty("confirmedAt") long confirmedAt,
			@JsonProperty("expiredAt") long expiredAt,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt) {
		this.id = id;
		this.userId = Objects.requireNonNull(userId, "userId");
		this.subscriptionId = subscriptionId;
		this.intent = Objects.requireNonNull(intent, "intent");
		this.billingCycles = billingCycles;
		this.oldPlanId = oldPlanId;
		this.newPlanId = newPlanId;
		this.amount = Objects.requireNonNull(amount, "amount");
		this.currency = Objects.requireNonNull(currency, "currency");
		this.status = Objects.requireNonNull(status, "status");
		this.paymentMethod = paymentMethod;
		this.gateway = gateway;
		this.gatewayPaymentId = gatewayPaymentId;
		this.gatewayStatus = gatewayStatus;
		this.network = network;
		this.token = token;
		this.tokenAmount = tokenAmount;
		this.walletTo = walletTo;
		this.walletFrom = walletFrom;
		this.transactionId = transactionId;
		this.confirmedAt = confirmedAt;
		this.expiredAt = expiredAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the payment id.
	 *
	 * @return the payment id
	 */
	public long getId() {
		return id;
	}

	/**
	 * Returns the id of the user who pays.
	 *
	 * @return the user id
	 */
	public Id getUserId() {
		return userId;
	}

	/**
	 * Returns the id of the subscription the payment is toward.
	 *
	 * @return the subscription id
	 */
	public long getSubscriptionId() {
		return subscriptionId;
	}

	/**
	 * Returns what the payment is for.
	 *
	 * @return the intent
	 */
	public Intent getIntent() {
		return intent;
	}

	/**
	 * Returns the months the payment covers, for a new subscription or a renewal.
	 *
	 * @return the billing cycles, or {@code 0} for an upgrade
	 */
	public int getBillingCycles() {
		return billingCycles;
	}

	/**
	 * Returns the plan an upgrade moves from.
	 *
	 * @return the old plan id, or {@code 0} if the payment is not an upgrade
	 */
	public int getOldPlanId() {
		return oldPlanId;
	}

	/**
	 * Returns the plan an upgrade moves to.
	 *
	 * @return the new plan id, or {@code 0} if the payment is not an upgrade
	 */
	public int getNewPlanId() {
		return newPlanId;
	}

	/**
	 * Returns the amount due.
	 *
	 * @return the amount, in {@link #getCurrency() the currency}
	 */
	public BigDecimal getAmount() {
		return amount;
	}

	/**
	 * Returns the currency of the amount, such as {@code USD}.
	 *
	 * @return the currency code
	 */
	public String getCurrency() {
		return currency;
	}

	/**
	 * Returns the state of the payment.
	 *
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Returns how the payment is made, such as {@code crypto}.
	 *
	 * @return the payment method, if recorded
	 */
	public Optional<String> getPaymentMethod() {
		return Optional.ofNullable(paymentMethod);
	}

	/**
	 * Returns the payment gateway that handles the payment.
	 *
	 * @return the gateway, if any
	 */
	public Optional<String> getGateway() {
		return Optional.ofNullable(gateway);
	}

	/**
	 * Returns the gateway's id for the payment.
	 *
	 * @return the gateway payment id, if any
	 */
	public Optional<String> getGatewayPaymentId() {
		return Optional.ofNullable(gatewayPaymentId);
	}

	/**
	 * Returns the gateway's status for the payment.
	 *
	 * @return the gateway status, if any
	 */
	public Optional<String> getGatewayStatus() {
		return Optional.ofNullable(gatewayStatus);
	}

	/**
	 * Returns the blockchain network the payment was made on.
	 *
	 * @return the network, if submitted
	 */
	public Optional<String> getNetwork() {
		return Optional.ofNullable(network);
	}

	/**
	 * Returns the token the payment was made in, such as {@code USDT}.
	 *
	 * @return the token, if submitted
	 */
	public Optional<String> getToken() {
		return Optional.ofNullable(token);
	}

	/**
	 * Returns the amount of the token paid.
	 *
	 * @return the token amount, if submitted
	 */
	public Optional<BigDecimal> getTokenAmount() {
		return Optional.ofNullable(tokenAmount);
	}

	/**
	 * Returns the wallet the payment was made to.
	 *
	 * @return the recipient wallet, if submitted
	 */
	public Optional<String> getWalletTo() {
		return Optional.ofNullable(walletTo);
	}

	/**
	 * Returns the wallet the payment was made from.
	 *
	 * @return the sender wallet, if submitted
	 */
	public Optional<String> getWalletFrom() {
		return Optional.ofNullable(walletFrom);
	}

	/**
	 * Returns the id of the transaction that settles the payment.
	 *
	 * @return the transaction id, if submitted
	 */
	public Optional<String> getTransactionId() {
		return Optional.ofNullable(transactionId);
	}

	/**
	 * Returns when the payment was confirmed.
	 *
	 * @return the time, in epoch milliseconds, or {@code 0} if not confirmed
	 */
	public long getConfirmedAt() {
		return confirmedAt;
	}

	/**
	 * Returns when the payment expired.
	 *
	 * @return the time, in epoch milliseconds, or {@code 0} if it has not expired
	 */
	public long getExpiredAt() {
		return expiredAt;
	}

	/**
	 * Returns when the payment was created.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the payment was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public String toString() {
		return "Payment{id=" + id + ", subscriptionId=" + subscriptionId + ", intent=" + intent +
				", amount=" + amount.toPlainString() + " " + currency + ", status=" + status + "}";
	}
}
