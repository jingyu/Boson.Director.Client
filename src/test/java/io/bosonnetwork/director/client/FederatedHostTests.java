/*
 * Copyright (c) 2026 -      bosonnetwork.io
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.bosonnetwork.json.Json;

class FederatedHostTests {
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "203.0.113.7", "2001:db8:1:2::/64" })
	void readsHostObservationsFromJsonAndCbor(@Nullable String address) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("host", address);
		body.put("verifiedNodes", 2);
		body.put("failedNodes", 3);
		body.put("notFederatingNodes", 4);
		body.put("lastCheckedAt", 1_794_000_000_123L);
		body.put("futureField", "ignored");
		assertObservation(address, Json.parse(Json.toString(body), FederatedHost.class));
		assertObservation(address, Json.parse(Json.toBytes(body), FederatedHost.class));
	}

	private static void assertObservation(@Nullable String address, FederatedHost host) {
		assertEquals(Optional.ofNullable(address), host.getHost());
		assertEquals(2, host.getVerifiedNodes());
		assertEquals(3, host.getFailedNodes());
		assertEquals(4, host.getNotFederatingNodes());
		assertEquals(1_794_000_000_123L, host.getLastCheckedAt());
	}
}
