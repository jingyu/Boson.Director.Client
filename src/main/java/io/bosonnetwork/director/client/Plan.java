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

/**
 * A plan from a super node's plan catalog: what it costs and how it is billed. Part of the
 * {@link UserPlan} returned by {@link DirectorClient#getPlan()}, and managed with a
 * {@link DirectorAdmin}. Immutable.
 */
public class Plan {
	private final int id;
	private final String name;
	private final @Nullable String description;
	private final @Nullable String detail;
	private final BigDecimal price;
	private final String currency;
	private final Cycle cycle;
	private final BigDecimal annuallyDiscount;
	private final boolean active;
	private final long createdAt;
	private final long updatedAt;

	/**
	 * How a plan is billed.
	 */
	public enum Cycle {
		/** Billed every month. */
		MONTHLY,
		/** Billed every year. */
		ANNUALLY;

		@JsonCreator
		static Cycle of(String name) {
			return valueOf(name.toUpperCase(Locale.ROOT));
		}

		@JsonValue
		@Override
		public String toString() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	@JsonCreator
	Plan(@JsonProperty(value = "id", required = true) int id,
			@JsonProperty(value = "name", required = true) String name,
			@JsonProperty("description") @Nullable String description,
			@JsonProperty("detail") @Nullable String detail,
			@JsonProperty(value = "price", required = true) BigDecimal price,
			@JsonProperty(value = "currency", required = true) String currency,
			@JsonProperty(value = "cycle", required = true) Cycle cycle,
			@JsonProperty("annuallyDiscount") @Nullable BigDecimal annuallyDiscount,
			@JsonProperty("active") boolean active,
			@JsonProperty("createdAt") long createdAt,
			@JsonProperty("updatedAt") long updatedAt) {
		this.id = id;
		this.name = Objects.requireNonNull(name, "name");
		this.description = description;
		this.detail = detail;
		this.price = Objects.requireNonNull(price, "price");
		this.currency = Objects.requireNonNull(currency, "currency");
		this.cycle = Objects.requireNonNull(cycle, "cycle");
		this.annuallyDiscount = annuallyDiscount != null ? annuallyDiscount : BigDecimal.ZERO;
		this.active = active;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * Returns the plan id.
	 *
	 * @return the plan id
	 */
	public int getId() {
		return id;
	}

	/**
	 * Returns the plan name.
	 *
	 * @return the plan name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns a short description of the plan.
	 *
	 * @return the description, if any
	 */
	public Optional<String> getDescription() {
		return Optional.ofNullable(description);
	}

	/**
	 * Returns the detailed description of the plan, as the operator wrote it.
	 *
	 * @return the detail, if any
	 */
	public Optional<String> getDetail() {
		return Optional.ofNullable(detail);
	}

	/**
	 * Returns the monthly price.
	 *
	 * @return the price, in {@link #getCurrency() the currency}
	 */
	public BigDecimal getPrice() {
		return price;
	}

	/**
	 * Returns the currency of the price, such as {@code USD}.
	 *
	 * @return the currency code
	 */
	public String getCurrency() {
		return currency;
	}

	/**
	 * Returns how the plan is billed.
	 *
	 * @return the billing cycle
	 */
	public Cycle getCycle() {
		return cycle;
	}

	/**
	 * Returns the discount for each full year billed.
	 *
	 * @return the annual discount, in {@link #getCurrency() the currency}
	 */
	public BigDecimal getAnnuallyDiscount() {
		return annuallyDiscount;
	}

	/**
	 * Tells whether the plan is offered to new subscribers.
	 *
	 * @return {@code true} if the plan is active
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * Returns when the plan was created.
	 *
	 * @return the creation time, in epoch milliseconds
	 */
	public long getCreatedAt() {
		return createdAt;
	}

	/**
	 * Returns when the plan was last changed.
	 *
	 * @return the update time, in epoch milliseconds
	 */
	public long getUpdatedAt() {
		return updatedAt;
	}

	/**
	 * Tells whether the plan costs nothing.
	 *
	 * @return {@code true} for a free plan
	 */
	public boolean isFree() {
		return price.signum() == 0;
	}

	@Override
	public String toString() {
		return "Plan{id=" + id + ", name=" + name + ", price=" + price.toPlainString() + " " + currency +
				"/" + cycle + "}";
	}
}
