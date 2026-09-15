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
 * A plan for an administrator to add to the node's catalog, for {@link DirectorAdmin#addPlan(NewPlan)}.
 * <p>
 * Plans are billed monthly. A new plan is inactive, and so not offered to subscribers, unless
 * {@link #active(boolean)} says otherwise. For example:
 * <pre>{@code
 * admin.addPlan(new NewPlan("Team", new BigDecimal("12.50"), "USD").description("For small teams").active(true));
 * }</pre>
 * Not thread-safe.
 */
public class NewPlan {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates a plan with its name and price.
	 *
	 * @param name     the plan name
	 * @param price    the monthly price
	 * @param currency the currency of the price, such as {@code USD}
	 * @throws IllegalArgumentException if the name or currency is empty, or the price is negative
	 */
	public NewPlan(String name, BigDecimal price, String currency) {
		Objects.requireNonNull(name, "name");
		if (name.isEmpty())
			throw new IllegalArgumentException("name is empty");
		Objects.requireNonNull(price, "price");
		if (price.signum() < 0)
			throw new IllegalArgumentException("Invalid price: " + price);
		Objects.requireNonNull(currency, "currency");
		if (currency.isEmpty())
			throw new IllegalArgumentException("currency is empty");

		fields.put("name", name);
		fields.put("price", price);
		fields.put("currency", currency);
	}

	/**
	 * Sets a short description of the plan.
	 *
	 * @param description the description, or {@code null} for none
	 * @return this plan
	 */
	public NewPlan description(@Nullable String description) {
		fields.put("description", description);
		return this;
	}

	/**
	 * Sets the detailed description of the plan.
	 *
	 * @param detail the detail, or {@code null} for none
	 * @return this plan
	 */
	public NewPlan detail(@Nullable String detail) {
		fields.put("detail", detail);
		return this;
	}

	/**
	 * Sets the discount for each full year billed.
	 *
	 * @param annuallyDiscount the annual discount
	 * @return this plan
	 * @throws IllegalArgumentException if the discount is negative
	 */
	public NewPlan annuallyDiscount(BigDecimal annuallyDiscount) {
		Objects.requireNonNull(annuallyDiscount, "annuallyDiscount");
		if (annuallyDiscount.signum() < 0)
			throw new IllegalArgumentException("Invalid annuallyDiscount: " + annuallyDiscount);
		fields.put("annuallyDiscount", annuallyDiscount);
		return this;
	}

	/**
	 * Sets whether the plan is offered to new subscribers.
	 *
	 * @param active whether the plan is active
	 * @return this plan
	 */
	public NewPlan active(boolean active) {
		fields.put("active", active);
		return this;
	}

	// The fields to send, keyed by their wire names.
	Map<String, ?> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
