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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.service.FederatedNodeDescriptor;
import io.bosonnetwork.service.FederationMode;

class FederatedNodeDescriptorTests {
	private static FederatedNodeDescriptor decode(String modeField) throws Exception {
		String json = "{\"nodeId\":\"" + Id.random() + "\"" + modeField +
				",\"services\":[],\"issuedAt\":1,\"expiresAt\":2}";
		return Json.objectMapper().readValue(json, FederatedNodeDescriptor.class);
	}

	@Test
	void aKnownModeIsRead() throws Exception {
		assertEquals(Optional.of(FederationMode.MUTUAL), decode(",\"mode\":\"mutual\"").getMode());
	}

	@Test
	void anUnknownOrMissingModeIsLeftOut() throws Exception {
		// A newer node's mode doesn't fail the whole answer.
		assertTrue(decode(",\"mode\":\"federated-by-carrier-pigeon\"").getMode().isEmpty());
		assertTrue(decode("").getMode().isEmpty());
	}
}
