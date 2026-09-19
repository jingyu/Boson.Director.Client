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

/**
 * A client for the client API of a Boson Director, the account service of a Boson super node.
 * <p>
 * {@link io.bosonnetwork.director.client.DirectorClient}, constructed through its
 * {@linkplain io.bosonnetwork.director.client.DirectorClient#builder() builder}, registers a user
 * with proof-of-work, manages the user's devices, passphrase, profile and avatar, and reports the
 * node's identity and status and the user's plan. Every call is asynchronous and returns a
 * {@link java.util.concurrent.CompletableFuture} that completes on the caller's Vert.x context;
 * signing in, token renewal and the wire encoding are handled by the client.
 * <p>
 * {@link io.bosonnetwork.director.client.DirectorGuest} covers what a Director offers without signing in:
 * the node's identity, status and plans, how it signs users up, where an OAuth sign-in starts, and a new
 * device asking to join an existing account. {@link io.bosonnetwork.director.client.DirectorOAuth} carries
 * an OAuth sign-in on: binding a Boson identity to it, its linked identities, renewal and sign-out.
 * <p>
 * {@link io.bosonnetwork.director.client.DirectorAdmin} is its counterpart for the admin API: users
 * and devices, subscriptions and payments, plans and their features, the node blacklist, and
 * federation with other super nodes. It shares the client's conventions and its transport.
 *
 * <h2>Errors</h2>
 * Failures are reported as {@link io.bosonnetwork.director.client.exceptions.DirectorException} or
 * one of its subclasses in {@link io.bosonnetwork.director.client.exceptions}, so callers can react
 * to a missing passphrase, a conflict or a rate limit by catching the specific type.
 */
@NullMarked
package io.bosonnetwork.director.client;

import org.jspecify.annotations.NullMarked;
