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
 * The changes to make to a plan, for {@link DirectorAdmin#updatePlan(int, PlanUpdate)}.
 * <p>
 * Only the fields set here are changed; setting a nullable field to {@code null} clears it. A plan's
 * currency cannot be changed. Not thread-safe.
 */
public class PlanUpdate {
	private final Map<String, @Nullable Object> fields = new LinkedHashMap<>();

	/**
	 * Creates an update that changes nothing yet.
	 */
	public PlanUpdate() {
	}

	/**
	 * Renames the plan.
	 *
	 * @param name the new name
	 * @return this update
	 * @throws IllegalArgumentException if the name is empty
	 */
	public PlanUpdate name(String name) {
		Objects.requireNonNull(name, "name");
		if (name.isEmpty())
			throw new IllegalArgumentException("name is empty");
		fields.put("name", name);
		return this;
	}

	/**
	 * Sets the short description.
	 *
	 * @param description the new description, or {@code null} to clear it
	 * @return this update
	 */
	public PlanUpdate description(@Nullable String description) {
		fields.put("description", description);
		return this;
	}

	/**
	 * Sets the detailed description.
	 *
	 * @param detail the new detail, or {@code null} to clear it
	 * @return this update
	 */
	public PlanUpdate detail(@Nullable String detail) {
		fields.put("detail", detail);
		return this;
	}

	/**
	 * Sets the monthly price.
	 *
	 * @param price the new price
	 * @return this update
	 * @throws IllegalArgumentException if the price is negative
	 */
	public PlanUpdate price(BigDecimal price) {
		Objects.requireNonNull(price, "price");
		if (price.signum() < 0)
			throw new IllegalArgumentException("Invalid price: " + price);
		// Sent as a string, which carries the exact decimal.
		fields.put("price", price.toPlainString());
		return this;
	}

	/**
	 * Sets the discount for each full year billed.
	 *
	 * @param annuallyDiscount the new annual discount
	 * @return this update
	 * @throws IllegalArgumentException if the discount is negative
	 */
	public PlanUpdate annuallyDiscount(BigDecimal annuallyDiscount) {
		Objects.requireNonNull(annuallyDiscount, "annuallyDiscount");
		if (annuallyDiscount.signum() < 0)
			throw new IllegalArgumentException("Invalid annuallyDiscount: " + annuallyDiscount);
		fields.put("annuallyDiscount", annuallyDiscount.toPlainString());
		return this;
	}

	/**
	 * Sets whether the plan is offered to new subscribers.
	 *
	 * @param active whether the plan is active
	 * @return this update
	 */
	public PlanUpdate active(boolean active) {
		fields.put("active", active);
		return this;
	}

	/**
	 * Tells whether the update changes nothing.
	 *
	 * @return {@code true} if no field is set
	 */
	public boolean isEmpty() {
		return fields.isEmpty();
	}

	// The fields to send, keyed by their wire names.
	Map<String, ?> fields() {
		return Collections.unmodifiableMap(fields);
	}
}
