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

import io.vertx.core.json.JsonObject;

import io.bosonnetwork.BosonException;

/**
 * A failed call to the Director.
 * <p>
 * When the Director answered, {@link #getStatus()} is the HTTP status of its answer and the message
 * is its explanation; the conditions a caller is likely to handle have their own subclasses. When it
 * did not (a connection, TLS or timeout failure), the status is {@link #NO_HTTP_STATUS} and the cause
 * is the underlying error.
 */
public class DirectorException extends BosonException {
	private static final long serialVersionUID = 4061851672318309212L;

	/**
	 * The {@linkplain #getStatus() status} of a failure that got no answer from the Director.
	 */
	public static final int NO_HTTP_STATUS = 0;

	// Error bodies are quoted in the message up to this length; a proxy's error page can be long.
	private static final int MAX_MESSAGE_LENGTH = 512;

	private final int status;

	/**
	 * Creates an exception for an answer from the Director.
	 *
	 * @param status  the HTTP status of the answer
	 * @param message the detail message
	 */
	public DirectorException(int status, String message) {
		super(message);
		this.status = status;
	}

	/**
	 * Creates an exception for an answer from the Director, with a cause.
	 *
	 * @param status  the HTTP status of the answer
	 * @param message the detail message
	 * @param cause   the underlying cause
	 */
	public DirectorException(int status, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
	}

	/**
	 * Creates an exception for a call that got no answer; the status is {@link #NO_HTTP_STATUS}.
	 *
	 * @param message the detail message
	 * @param cause   the underlying cause
	 */
	public DirectorException(String message, Throwable cause) {
		this(NO_HTTP_STATUS, message, cause);
	}

	/**
	 * Returns the HTTP status of the Director's answer.
	 *
	 * @return the status, or {@link #NO_HTTP_STATUS} if the call got no answer
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * Builds the exception for an error answer from the Director, choosing the most specific subclass
	 * for its status.
	 *
	 * @param status     the HTTP status of the answer
	 * @param body       the body of the answer, may be {@code null}
	 * @param retryAfter the {@code Retry-After} header of the answer, may be {@code null}
	 * @return the exception
	 */
	public static DirectorException fromResponse(int status, String body, String retryAfter) {
		String message = messageOf(status, body);
		return switch (status) {
			case InvalidRequestException.STATUS -> new InvalidRequestException(message);
			case UnauthorizedException.STATUS -> new UnauthorizedException(message);
			case ForbiddenException.STATUS -> new ForbiddenException(message);
			case NotFoundException.STATUS -> new NotFoundException(message);
			case ConflictException.STATUS -> new ConflictException(message);
			case PassphraseRequiredException.STATUS -> new PassphraseRequiredException(message);
			case RateLimitException.STATUS -> new RateLimitException(message, parseRetryAfter(retryAfter));
			case ServiceBusyException.STATUS -> new ServiceBusyException(message, parseRetryAfter(retryAfter));
			case NotEnabledException.STATUS -> new NotEnabledException(message);
			default -> status >= 500 ? new DirectorServerException(status, message) : new DirectorException(status, message);
		};
	}

	// The Director explains an error in a plain-text body, "<reason> - <detail>". A JSON body with a
	// "message" field, as the API documentation describes, is understood too.
	private static String messageOf(int status, String body) {
		if (body == null || body.isBlank())
			return "HTTP " + status;

		String message = body.trim();
		if (message.startsWith("{")) {
			try {
				String m = new JsonObject(message).getString("message");
				if (m != null && !m.isBlank())
					message = m.trim();
			} catch (RuntimeException ignore) {
				// Not JSON after all; keep the body as it is.
			}
		}

		return message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) + "..." : message;
	}

	// Retry-After in seconds; the HTTP-date form is not used by the Director.
	private static long parseRetryAfter(String value) {
		if (value == null)
			return 0;

		try {
			return Math.max(0, Long.parseLong(value.trim()));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
