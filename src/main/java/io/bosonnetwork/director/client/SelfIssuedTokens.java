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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.function.Supplier;

import io.vertx.core.Future;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.bosonnetwork.Id;
import io.bosonnetwork.Identity;
import io.bosonnetwork.cwt.SignedCwt;

/**
 * The access tokens of a client that issues its own, signed with its own key, instead of obtaining
 * them from the Director. The Director accepts a token whose issuer is its subject (the user's key)
 * or its client id (a device's key), and grants what that user or device is entitled to, not what
 * the token claims. Shared by {@link DirectorClient} and {@link DirectorAdmin}.
 * <p>
 * A self-issued token is only as good as the clock it is dated by. The Director tolerates some skew,
 * and each token is backdated a little on top of that. Beyond both, a Director that rejects a token
 * reveals its own clock in the {@code Date} header of the answer: if that clock is far from ours,
 * tokens are dated by it from then on, and the rejected request is repeated once.
 */
final class SelfIssuedTokens implements DirectorTransport.TokenSource {
	// Lifetime of a token. Kept short: it is a bearer credential, and issuing a new one costs a
	// signature rather than a round trip.
	static final long TOKEN_LIFETIME = 10 * 60 * 1000;

	// A token is renewed this long before it expires, so that no request carries a token that expires
	// while the request is in flight.
	private static final long REFRESH_MARGIN = 60 * 1000;

	// A token is dated this far back, so that a Director whose clock is a little behind ours does not
	// see it as issued in the future.
	static final long BACKDATE = 60 * 1000;

	// A Director clock further than this from ours is taken as the reason a token was rejected. Less
	// than the backdating, so that any skew the backdating does not absorb gets corrected.
	static final long MAX_CLOCK_SKEW = 30 * 1000;

	private final Identity issuer;
	private final Id subject;
	private final @Nullable Id clientId;
	private final String scope;
	// The id of the node the tokens are for; the Director accepts only tokens addressed to it.
	private final Supplier<Future<Id>> audience;
	private final Logger log;

	private final Object lock = new Object();
	// All guarded by lock: the cached token, and when it expires by our clock in epoch milliseconds;
	// how far the Director's clock is ahead of ours; and the token whose rejection last corrected that.
	private @Nullable String token;
	private long expiresAt;
	private long clockOffset;
	private @Nullable String skewedToken;

	/**
	 * Creates a token source.
	 *
	 * @param issuer the key that signs the tokens: the user's, or a device's
	 * @param subject the user the tokens act for
	 * @param clientId the device the tokens act as, whose key must then be the issuer; {@code null} for
	 *        the user's own tokens
	 * @param scope the access scope claimed
	 * @param audience supplies the id of the node the tokens are for
	 * @param log the owning client's logger
	 */
	SelfIssuedTokens(Identity issuer, Id subject, @Nullable Id clientId, String scope,
			Supplier<Future<Id>> audience, Logger log) {
		this.issuer = issuer;
		this.subject = subject;
		this.clientId = clientId;
		this.scope = scope;
		this.audience = audience;
		this.log = log;
	}

	@Override
	public Future<String> token() {
		return audience.get().map(this::current);
	}

	// Returns the cached token, or a new one if it is about to expire.
	private String current(Id nodeId) {
		synchronized (lock) {
			long now = System.currentTimeMillis();
			String current = token;
			if (current != null && now < expiresAt - REFRESH_MARGIN)
				return current;

			long directorNow = now + clockOffset;
			SignedCwt.Builder builder = SignedCwt.builder(issuer)
					.subject(subject)
					.audience(nodeId)
					.issuedAt(new Date(directorNow - BACKDATE))
					.notBefore(new Date(directorNow - BACKDATE))
					.expiration(new Date(directorNow + TOKEN_LIFETIME))
					.scope(scope);
			if (clientId != null)
				builder.clientId(clientId);

			current = builder.buildToString();
			token = current;
			expiresAt = now + TOKEN_LIFETIME;
			return current;
		}
	}

	@Override
	public boolean rejected(String rejected, DirectorTransport.Response response) {
		long now = System.currentTimeMillis();
		Long directorTime = directorTime(response);

		synchronized (lock) {
			// Most rejections are for the key, not the token, and a new token would fare no better; it is
			// dropped anyway, since issuing another costs only a signature.
			if (rejected.equals(token))
				token = null;

			if (directorTime != null) {
				long offset = directorTime - now;
				if (Math.abs(offset - clockOffset) > MAX_CLOCK_SKEW) {
					log.warn("The Director's clock is {} seconds ahead of the local clock; dating access tokens by the Director's clock",
							offset / 1000);
					clockOffset = offset;
					token = null;
					skewedToken = rejected;
					return true;
				}
			}

			// Calls that carried the token that revealed the skew were rejected for the same reason, and
			// deserve the same repeat; they find the clock already corrected.
			return rejected.equals(skewedToken);
		}
	}

	// The Director's clock when it answered, from the Date header; null without a readable one.
	private static @Nullable Long directorTime(DirectorTransport.Response response) {
		String date = response.getHeader("Date");
		if (date == null)
			return null;

		try {
			return ZonedDateTime.parse(date.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
