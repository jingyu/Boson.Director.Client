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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The blockchain transaction that pays for a payment, for
 * {@link DirectorClient#submitPayment(long, PaymentTransaction)}. Every field is required. The Director
 * accepts USDT on the networks it supports ({@code ethereum}, {@code tron}). Immutable.
 */
public final class PaymentTransaction {
	private final String network;
	private final String token;
	private final BigDecimal tokenAmount;
	private final String walletFrom;
	private final String walletTo;
	private final String transactionId;

	/**
	 * Creates the transaction.
	 *
	 * @param network the blockchain network, such as {@code tron}
	 * @param token the token paid in, such as {@code usdt}
	 * @param tokenAmount the amount of the token paid
	 * @param walletFrom the wallet the payment was sent from
	 * @param walletTo the wallet the payment was sent to
	 * @param transactionId the id of the transaction on the network
	 */
	public PaymentTransaction(String network, String token, BigDecimal tokenAmount, String walletFrom,
			String walletTo, String transactionId) {
		this.network = Objects.requireNonNull(network, "network");
		this.token = Objects.requireNonNull(token, "token");
		this.tokenAmount = Objects.requireNonNull(tokenAmount, "tokenAmount");
		this.walletFrom = Objects.requireNonNull(walletFrom, "walletFrom");
		this.walletTo = Objects.requireNonNull(walletTo, "walletTo");
		this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
		if (tokenAmount.signum() <= 0)
			throw new IllegalArgumentException("tokenAmount must be positive");
	}

	Map<String, Object> fields() {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("network", network);
		fields.put("token", token);
		fields.put("tokenAmount", tokenAmount);
		fields.put("walletFrom", walletFrom);
		fields.put("walletTo", walletTo);
		fields.put("transactionId", transactionId);
		return fields;
	}

	@Override
	public String toString() {
		return "PaymentTransaction{network=" + network + ", token=" + token + ", tokenAmount=" + tokenAmount +
				", transactionId=" + transactionId + "}";
	}
}
