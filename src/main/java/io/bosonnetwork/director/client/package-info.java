/**
 * A client for the client API of a Boson Director, the account service of a Boson super node.
 * <p>
 * {@link io.bosonnetwork.director.client.DirectorClient}, constructed through its
 * {@linkplain io.bosonnetwork.director.client.DirectorClient#builder() builder}, registers a user
 * with proof-of-work, manages the user's devices, passphrase, profile and avatar, and reports the
 * node's identity and status and the user's plan. Every call is asynchronous and returns a
 * {@link java.util.concurrent.CompletableFuture} that completes on the caller's Vert.x context;
 * signing in, token renewal and the wire encoding are handled by the client.
 *
 * <h2>Errors</h2>
 * Failures are reported as {@link io.bosonnetwork.director.client.exceptions.DirectorException} or
 * one of its subclasses in {@link io.bosonnetwork.director.client.exceptions}, so callers can react
 * to a missing passphrase, a conflict or a rate limit by catching the specific type.
 */
@NullMarked
package io.bosonnetwork.director.client;

import org.jspecify.annotations.NullMarked;
