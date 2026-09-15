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

/**
 * The new state of a payment, for {@link DirectorAdmin#updatePayment(long, PaymentUpdate)}.
 * <p>
 * The status is always set. A pending or confirmed payment also has to carry the transaction that
 * settles it: the network, token, token amount, sender wallet and transaction id. The other fields
 * are changed only when set. Not thread-safe.
 */
public class PaymentUpdate {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates an update that sets the payment's status.
	 *
	 * @param status the new status
	 */
	public PaymentUpdate(Payment.Status status) {
		fields.put("status", Objects.requireNonNull(status, "status"));
	}

	/**
	 * Sets the blockchain network the payment was made on.
	 *
	 * @param network the network
	 * @return this update
	 */
	public PaymentUpdate network(String network) {
		fields.put("network", Objects.requireNonNull(network, "network"));
		return this;
	}

	/**
	 * Sets the token the payment was made in, such as {@code USDT}.
	 *
	 * @param token the token
	 * @return this update
	 */
	public PaymentUpdate token(String token) {
		fields.put("token", Objects.requireNonNull(token, "token"));
		return this;
	}

	/**
	 * Sets the amount of the token paid.
	 *
	 * @param tokenAmount the token amount
	 * @return this update
	 * @throws IllegalArgumentException if the amount is not positive
	 */
	public PaymentUpdate tokenAmount(BigDecimal tokenAmount) {
		Objects.requireNonNull(tokenAmount, "tokenAmount");
		if (tokenAmount.signum() <= 0)
			throw new IllegalArgumentException("Invalid tokenAmount: " + tokenAmount);
		// Sent as a string, which carries the exact decimal.
		fields.put("tokenAmount", tokenAmount.toPlainString());
		return this;
	}

	/**
	 * Sets the wallet the payment was made to.
	 *
	 * @param walletTo the recipient wallet
	 * @return this update
	 */
	public PaymentUpdate walletTo(String walletTo) {
		fields.put("walletTo", Objects.requireNonNull(walletTo, "walletTo"));
		return this;
	}

	/**
	 * Sets the wallet the payment was made from.
	 *
	 * @param walletFrom the sender wallet
	 * @return this update
	 */
	public PaymentUpdate walletFrom(String walletFrom) {
		fields.put("walletFrom", Objects.requireNonNull(walletFrom, "walletFrom"));
		return this;
	}

	/**
	 * Sets the id of the transaction that settles the payment.
	 *
	 * @param transactionId the transaction id
	 * @return this update
	 */
	public PaymentUpdate transactionId(String transactionId) {
		fields.put("transactionId", Objects.requireNonNull(transactionId, "transactionId"));
		return this;
	}

	// The fields to send, keyed by their wire names.
	Map<String, @Nullable Object> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
