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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;

/** The sign-in code and the super node code, as Boson strings. */
public class CodeFormatsTests {
	@Test
	void theSignInCodeIsABosonString() {
		String requestId = Id.random().toBase58String();
		assertEquals("boson:signin:1:" + requestId, SignInRequest.code(requestId));
		assertEquals(Optional.of(requestId), SignInRequest.parseCode("  boson:signin:1:" + requestId + "\n"));

		assertThrows(IllegalArgumentException.class, () -> SignInRequest.code("a:b"));
		assertThrows(IllegalArgumentException.class, () -> SignInRequest.code(""));
		for (String text : new String[] { "", "boson:signin:1:", "boson:signin:2:" + requestId, "boson:signin:1:a-b",
				"boson:signin:1:" + requestId + ":x", "bosonsignin:1:" + requestId, "boson:pair:1:" + requestId })
			assertTrue(SignInRequest.parseCode(text).isEmpty(), text);
	}

	@Test
	void theSuperNodeCodeCarriesTheIdAndTheUrl() {
		Id nodeId = Id.random();
		SuperNodeCode code = SuperNodeCode.of(nodeId, "https://node.example:8443/director/");
		assertEquals("boson:supernode:1:" + nodeId + ":https://node.example:8443/director", code.toString());

		SuperNodeCode read = SuperNodeCode.parse(code.toString()).orElseThrow();
		assertEquals(Optional.of(nodeId), read.getNodeId());
		assertEquals("https://node.example:8443/director", read.getUrl());
		assertEquals(code, read);
	}

	@Test
	void theJsonFormBeforeIsStillRead() {
		Id nodeId = Id.random();
		SuperNodeCode withId = SuperNodeCode.parse("{\"url\":\"https://node.example\",\"id\":\"" + nodeId + "\"}").orElseThrow();
		assertEquals(Optional.of(nodeId), withId.getNodeId());
		assertEquals("https://node.example", withId.getUrl());
		assertEquals("boson:supernode:1:" + nodeId + ":https://node.example", withId.toString());

		// The JSON form's id was optional; such a code has no text form of its own.
		SuperNodeCode withoutId = SuperNodeCode.parse("{\"url\":\"http://10.0.2.2:8080\"}").orElseThrow();
		assertTrue(withoutId.getNodeId().isEmpty());
		assertThrows(IllegalStateException.class, withoutId::toString);
	}

	@Test
	void whatIsNotASuperNodeCodeIsRejected() {
		String id = Id.random().toBase58String();
		for (String text : new String[] { "", "https://node.example", "boson:supernode:1:" + id,
				"boson:supernode:1::https://node.example", "boson:supernode:2:" + id + ":https://node.example",
				"boson:supernode:1:" + id + ":ftp://node.example", "boson:supernode:1:" + id + ":https://u@node.example",
				"boson:supernode:1:not-base58!:https://node.example", "{\"id\":\"" + id + "\"}", "{not json",
				"{\"url\":\"https://node.example?x=1\"}" })
			assertFalse(SuperNodeCode.parse(text).isPresent(), text);

		assertThrows(IllegalArgumentException.class, () -> SuperNodeCode.of(Id.random(), "https://node.example#x"));
	}
}
