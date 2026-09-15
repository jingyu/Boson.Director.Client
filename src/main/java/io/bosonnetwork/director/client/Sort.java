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
import java.util.regex.Pattern;

/**
 * A sort key for the {@link DirectorAdmin} list calls that support ordering: a field of the listed
 * objects and a direction. Immutable.
 * <p>
 * The field is named as in the objects the list returns, such as {@code createdAt}. Which fields a list
 * can be ordered by is documented on the list call; the Director refuses any other.
 */
public final class Sort {
	// A field name. The dot separates the field from the direction in the orderBy value, so a field
	// never contains one.
	private static final Pattern FIELD = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

	private final String field;
	private final boolean ascending;

	private Sort(String field, boolean ascending) {
		Objects.requireNonNull(field, "field");
		if (!FIELD.matcher(field).matches())
			throw new IllegalArgumentException("Invalid sort field: " + field);

		this.field = field;
		this.ascending = ascending;
	}

	/**
	 * Orders by a field, ascending.
	 *
	 * @param field the field
	 * @return the sort key
	 * @throws IllegalArgumentException if the field is not a valid field name
	 */
	public static Sort asc(String field) {
		return new Sort(field, true);
	}

	/**
	 * Orders by a field, descending.
	 *
	 * @param field the field
	 * @return the sort key
	 * @throws IllegalArgumentException if the field is not a valid field name
	 */
	public static Sort desc(String field) {
		return new Sort(field, false);
	}

	/**
	 * Returns the field to order by.
	 *
	 * @return the field
	 */
	public String getField() {
		return field;
	}

	/**
	 * Tells whether the order is ascending.
	 *
	 * @return {@code true} for ascending, {@code false} for descending
	 */
	public boolean isAscending() {
		return ascending;
	}

	// The value of one orderBy query parameter, as the Director parses it.
	String toParam() {
		return field + (ascending ? ".asc" : ".desc");
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof Sort that && field.equals(that.field) && ascending == that.ascending;
	}

	@Override
	public int hashCode() {
		return Objects.hash(field, ascending);
	}

	@Override
	public String toString() {
		return toParam();
	}
}
