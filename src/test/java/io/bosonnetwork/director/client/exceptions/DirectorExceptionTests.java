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

package io.bosonnetwork.director.client.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DirectorExceptionTests {
	@Test
	void mapsStatusToType() {
		assertInstanceOf(InvalidRequestException.class, DirectorException.fromResponse(400, "x", null));
		assertInstanceOf(UnauthorizedException.class, DirectorException.fromResponse(401, "x", null));
		assertInstanceOf(ForbiddenException.class, DirectorException.fromResponse(403, "x", null));
		assertInstanceOf(NotFoundException.class, DirectorException.fromResponse(404, "x", null));
		assertInstanceOf(ConflictException.class, DirectorException.fromResponse(409, "x", null));
		assertInstanceOf(PassphraseRequiredException.class, DirectorException.fromResponse(428, "x", null));
		assertInstanceOf(RateLimitException.class, DirectorException.fromResponse(429, "x", null));
		assertInstanceOf(ServiceBusyException.class, DirectorException.fromResponse(503, "x", null));
		assertInstanceOf(NotEnabledException.class, DirectorException.fromResponse(501, "x", null));

		DirectorException server = DirectorException.fromResponse(502, "x", null);
		assertInstanceOf(DirectorServerException.class, server);
		assertEquals(502, server.getStatus());

		DirectorException other = DirectorException.fromResponse(418, "x", null);
		assertSame(DirectorException.class, other.getClass());
		assertEquals(418, other.getStatus());
	}

	@Test
	void usesThePlainTextBodyAsTheMessage() {
		// What BosonDirector.failureHandler sends: "<reason> - <detail>".
		DirectorException e = DirectorException.fromResponse(409, "Conflict - The user already exists\n", null);
		assertEquals("Conflict - The user already exists", e.getMessage());
		assertEquals(409, e.getStatus());
	}

	@Test
	void readsTheMessageOfAJsonBody() {
		DirectorException e = DirectorException.fromResponse(400, "{\"statusCode\":400,\"message\":\"Bad field\"}", null);
		assertEquals("Bad field", e.getMessage());

		// Not JSON after all: the body is kept as it is.
		e = DirectorException.fromResponse(400, "{broken", null);
		assertEquals("{broken", e.getMessage());
	}

	@Test
	void namesTheStatusWhenThereIsNoBody() {
		assertEquals("HTTP 404", DirectorException.fromResponse(404, null, null).getMessage());
		assertEquals("HTTP 404", DirectorException.fromResponse(404, "  ", null).getMessage());
	}

	@Test
	void truncatesALongBody() {
		String message = DirectorException.fromResponse(502, "x".repeat(10_000), null).getMessage();
		assertEquals(512 + 3, message.length());
		assertTrue(message.endsWith("..."));
	}

	@Test
	void parsesRetryAfter() {
		assertEquals(12, ((RateLimitException) DirectorException.fromResponse(429, "x", "12")).getRetryAfter());
		assertEquals(1, ((ServiceBusyException) DirectorException.fromResponse(503, "x", " 1 ")).getRetryAfter());
		assertEquals(0, ((RateLimitException) DirectorException.fromResponse(429, "x", null)).getRetryAfter());
		// The HTTP-date form is not used by the Director and is ignored.
		assertEquals(0, ((RateLimitException) DirectorException.fromResponse(429, "x",
				"Wed, 21 Oct 2026 07:28:00 GMT")).getRetryAfter());
	}
}
