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

import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrustOptions;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.HybridTrustManager;
import io.bosonnetwork.crypto.Signature;
import io.bosonnetwork.director.client.exceptions.DirectorException;
import io.bosonnetwork.json.Json;
import io.bosonnetwork.utils.Base58;
import io.bosonnetwork.utils.Hex;
import io.bosonnetwork.vertx.ContextualFuture;
import io.bosonnetwork.web.PaginatedResult;

/**
 * The HTTP plumbing shared by {@link DirectorClient} and {@link DirectorAdmin}: the connection pool,
 * authenticated requests, error mapping and response decoding.
 * <p>
 * Each client owns one transport, bound to one Director API, and supplies the access tokens for it.
 * Keeping this in one place is what keeps the two clients' transport security, retry and error
 * behaviour identical.
 */
final class DirectorTransport {
	// Every Director API lives under this path of the Director URL.
	static final String API_VERSION_PREFIX = "/api/v1";

	// Seconds an idle connection may be reused from the pool. Kept short for the same reason as in
	// the Ion Store client: mobile platforms and NAT gateways silently drop idle connections, and a
	// dropped pooled connection is indistinguishable from a live one until a request fails on it.
	private static final int KEEP_ALIVE_TIMEOUT = 20;

	// Milliseconds a request may go without a byte in either direction before it fails: how a connection
	// that died mid-request is noticed. Per request, not per connection: a connection idle timeout also
	// closes a connection that is busy with a request the Director deliberately holds open - a long poll -
	// and pooled connections are already retired by the keep-alive timeout above.
	private static final long REQUEST_IDLE_TIMEOUT = TimeUnit.SECONDS.toMillis(60);

	private static final String CONTENT_TYPE_JSON = "application/json";

	static final int NOT_MODIFIED = 304;

	/**
	 * Supplies the access tokens a transport authenticates its requests with.
	 */
	interface TokenSource {
		// Returns a current access token.
		Future<String> token();

		// Called when the Director rejected the token as unauthorized, with its answer. Returns whether
		// a new token could succeed where this one failed, in which case the request is repeated once
		// with it.
		boolean rejected(String token, Response response);
	}

	@FunctionalInterface
	interface BodyParser<T> {
		T parse(Buffer body) throws Exception;
	}

	private final Logger log;
	// Director URL path (sans trailing slash), the API version prefix and the API path; request paths
	// append to it.
	private final String basePath;
	// The URL host and port every request names.
	private final String host;
	private final int port;
	// Where to connect instead of looking up the URL host, or null to look it up.
	private final @Nullable SocketAddress server;
	private final HttpClient httpClient;
	// Where the clients complete the futures they return, if not where the answer arrives.
	private final @Nullable Executor callbackExecutor;

	private volatile boolean closed;

	/**
	 * Creates a transport for one Director API.
	 *
	 * @param vertx          the Vert.x instance the transport runs on
	 * @param directorUrl    the Director URL
	 * @param apiPath        the API path below {@code /api/v1}, such as {@code /client}
	 * @param nodeId         the super node id a self-signed certificate is pinned to, or {@code null}
	 * @param resolveToAddress the address to connect to instead of looking up the URL host, or {@code null}
	 * @param log            the logger of the owning client
	 */
	DirectorTransport(Vertx vertx, URL directorUrl, String apiPath, @Nullable Id nodeId,
			@Nullable InetSocketAddress resolveToAddress, @Nullable Executor callbackExecutor, Logger log) {
		this.log = log;
		this.callbackExecutor = callbackExecutor;
		this.host = directorUrl.getHost();
		this.port = directorUrl.getPort() > 0 ? directorUrl.getPort() : directorUrl.getDefaultPort();
		this.server = resolveToAddress != null ? SocketAddress.inetSocketAddress(resolveToAddress) : null;

		boolean ssl = directorUrl.getProtocol().equals("https");
		this.basePath = directorUrl.getPath().replaceAll("/+$", "") + API_VERSION_PREFIX + apiPath;

		HttpClientOptions options = new HttpClientOptions();
		options.setSsl(ssl);
		options.setDefaultHost(directorUrl.getHost());
		options.setDefaultPort(directorUrl.getPort() > 0 ? directorUrl.getPort() : directorUrl.getDefaultPort());
		options.setKeepAlive(true);
		options.setKeepAliveTimeout(KEEP_ALIVE_TIMEOUT);
		options.setConnectTimeout(10_000);
		// The Director never redirects an API call. Follow no redirect, rather than repeat the request -
		// bearer token included - to wherever it points: a 3xx comes back as a response, and the status
		// check fails it. Set on the client rather than per request, so that no request can opt back in.
		options.setMaxRedirects(0);

		if (ssl) {
			options.setEnabledSecureTransportProtocols(Set.of("TLSv1.2", "TLSv1.3"));
			// CA-signed certificates are validated as usual either way; with the node id known, a
			// self-signed certificate pinned to it is accepted too.
			if (nodeId != null)
				options.setTrustOptions(TrustOptions.wrap(
						new HybridTrustManager(nodeId.toString(), nodeId.bytesUnsafe())));
		}

		this.httpClient = vertx.createHttpClient(options);
	}

	// Hands a result to the caller as the CompletableFuture every client call returns: completed on the
	// configured callback executor, or else where the result arrives - the caller's Vert.x context, or an
	// event loop.
	<T> CompletableFuture<T> deliver(Future<T> result) {
		Executor executor = callbackExecutor;
		if (executor == null)
			return ContextualFuture.of(result);

		Promise<T> promise = Promise.promise();
		result.onComplete(ar -> {
			try {
				executor.execute(() -> promise.handle(ar));
			} catch (RuntimeException e) {
				// A rejecting executor (shut down) still must not leave the caller waiting forever.
				promise.tryFail(e);
			}
		});
		return ContextualFuture.of(promise.future());
	}

	Future<Void> close() {
		if (closed)
			return Future.succeededFuture();

		closed = true;
		return httpClient.close();
	}

	boolean isClosed() {
		return closed;
	}

	void checkOpen() {
		if (closed)
			throw new IllegalStateException("Client is closed");
	}

	// Sends a request with an optional JSON body. See call(HttpMethod, String, Buffer, String, TokenSource).
	//
	// Maps cross classes in this package as Map<String, ?> (or ? super Object where they are written to),
	// never as Map<String, @Nullable Object>: javac before JDK 22 drops type-use annotations on type
	// arguments it reads from class files, so a build that recompiles only some classes - an IDE's
	// incremental build - would see Map<String, Object> and fail NullAway, while a full build passes.
	Future<Response> call(HttpMethod method, String path,
			@Nullable Map<String, ?> json, @Nullable TokenSource tokens) {
		return call(method, path, json, tokens, 0);
	}

	// As above, with the time the request may go without a byte in either direction before it fails, in
	// milliseconds; 0 for the default, REQUEST_IDLE_TIMEOUT. Only a call the Director deliberately holds
	// open - a long poll - needs more.
	Future<Response> call(HttpMethod method, String path,
			@Nullable Map<String, ?> json, @Nullable TokenSource tokens, long idleTimeout) {
		Buffer body = null;
		if (json != null) {
			try {
				// Boson's codec: ids as base58, byte arrays as unpadded base64url - the Director's own.
				body = Buffer.buffer(Json.objectMapper().writeValueAsBytes(json));
			} catch (JsonProcessingException e) {
				return Future.failedFuture(new DirectorException("Cannot encode the request: " + e.getMessage(), e));
			}
		}

		return call(method, path, body, body != null ? CONTENT_TYPE_JSON : null, tokens, idleTimeout, null);
	}

	// Sends a request to the API and fails the result on any non-2xx answer. A request with no token
	// source is sent without credentials. Every API call of both clients goes through here, so adding
	// one is a method that names its path and decodes its answer.
	Future<Response> call(HttpMethod method, String path, @Nullable Buffer body,
			@Nullable String contentType, @Nullable TokenSource tokens) {
		return call(method, path, body, contentType, tokens, 0, null);
	}

	// A conditional GET: asks for a resource the caller holds a copy of, sending that copy's validators
	// (If-None-Match, If-Modified-Since). A 304 answer succeeds like a 2xx - the copy is current.
	Future<Response> conditionalGet(String path, MultiMap conditions, @Nullable TokenSource tokens) {
		return call(HttpMethod.GET, path, null, null, tokens, 0, conditions);
	}

	private Future<Response> call(HttpMethod method, String path, @Nullable Buffer body,
			@Nullable String contentType, @Nullable TokenSource tokens, long idleTimeout,
			@Nullable MultiMap conditions) {
		Future<Response> response;
		if (tokens == null) {
			response = send(method, path, body, contentType, null, idleTimeout, conditions);
		} else {
			TokenSource source = tokens;
			response = source.token().compose(t -> send(method, path, body, contentType, t, idleTimeout, conditions)
					.compose(res -> {
						if (res.statusCode() != 401 || !source.rejected(t, res))
							return Future.succeededFuture(res);

						// Rejected before it was acted on, and the token source can do better: repeat once.
						return source.token().compose(fresh ->
								send(method, path, body, contentType, fresh, idleTimeout, conditions));
					}));
		}

		boolean conditional = conditions != null && !conditions.isEmpty();
		return response.compose(res -> checkStatus(method, path, res, conditional))
				.recover(this::wrapError);
	}

	private Future<Response> send(HttpMethod method, String path, @Nullable Buffer body,
			@Nullable String contentType, @Nullable String accessToken, long idleTimeout,
			@Nullable MultiMap conditions) {
		RequestOptions request = new RequestOptions()
				.setMethod(method)
				.setURI(basePath + path);
		request.setIdleTimeout(idleTimeout > 0 ? idleTimeout : REQUEST_IDLE_TIMEOUT);
		// The host - and with it the Host header, SNI and the certificate check - stays the URL's; only
		// the connection goes elsewhere. Named explicitly, since Vert.x otherwise takes it from the server.
		if (server != null)
			request.setServer(server).setHost(host).setPort(port);
		if (accessToken != null)
			request.putHeader("Authorization", "Bearer " + accessToken);
		if (body != null && contentType != null)
			request.putHeader("Content-Type", contentType);
		if (conditions != null)
			conditions.forEach(request::putHeader);

		// The body is read whatever the status: an error's explanation is in it, and a response left
		// unread would keep its connection out of the pool.
		return httpClient.request(request)
				.compose(req -> body != null ? req.send(body) : req.send())
				.compose(res -> res.body().map(content -> new Response(res.statusCode(), res.headers(), content)));
	}

	private Future<Response> checkStatus(HttpMethod method, String path, Response response, boolean conditional) {
		int status = response.statusCode();
		if ((status >= 200 && status < 300) || (conditional && status == NOT_MODIFIED))
			return Future.succeededFuture(response);

		DirectorException error = DirectorException.fromResponse(status, response.bodyAsString(),
				response.getHeader("Retry-After"));

		// Refusals the caller can act on (bad request, auth, passphrase, conflict, rate limit) are
		// expected and logged at debug; server-side failures at error.
		if (status < 500)
			log.debug("Director request {} {} refused: {} - {}", method, path, status, error.getMessage());
		else
			log.error("Director request {} {} failed: {} - {}", method, path, status, error.getMessage());

		return Future.failedFuture(error);
	}

	private <T> Future<T> wrapError(Throwable e) {
		// Already classified (and, for HTTP errors, logged by checkStatus), or a precondition of the
		// client rather than a failed request.
		if (e instanceof DirectorException || e instanceof IllegalStateException)
			return Future.failedFuture(e);

		// Anything else means no answer: connection, TLS, timeout.
		log.error("Director request failed: {}", e.getMessage(), e);
		return Future.failedFuture(new DirectorException("Director request failed: " + e.getMessage(), e));
	}

	/**
	 * A Director answer with its body read: what the clients check and decode.
	 */
	static final class Response {
		private final int statusCode;
		private final MultiMap headers;
		private final Buffer body;

		private Response(int statusCode, MultiMap headers, Buffer body) {
			this.statusCode = statusCode;
			this.headers = headers;
			this.body = body;
		}

		int statusCode() {
			return statusCode;
		}

		@Nullable String getHeader(String name) {
			return headers.get(name);
		}

		Buffer body() {
			return body;
		}

		String bodyAsString() {
			return body.toString(StandardCharsets.UTF_8);
		}

		// Parses the body. A missing or malformed body fails with a DirectorException, like any other
		// failed call.
		<T> Future<T> decode(BodyParser<T> parser) {
			try {
				if (body.length() == 0)
					throw new IllegalArgumentException("empty response body");

				return Future.succeededFuture(parser.parse(body));
			} catch (Exception e) {
				return Future.failedFuture(new DirectorException(statusCode,
						"Malformed Director response: " + e.getMessage(), e));
			}
		}

		<T> Future<T> json(Class<T> type) {
			return decode(body -> Json.objectMapper().readValue(body.getBytes(), type));
		}

		<T> Future<List<T>> jsonList(Class<T> type) {
			JavaType listType = Json.objectMapper().getTypeFactory().constructCollectionType(List.class, type);
			return decode(body -> Json.objectMapper().readValue(body.getBytes(), listType));
		}

		<T> Future<PaginatedResult<T>> paged(Class<T> type) {
			JavaType pageType = Json.objectMapper().getTypeFactory().constructParametricType(PaginatedResult.class, type);
			return decode(body -> Json.objectMapper().readValue(body.getBytes(), pageType));
		}

		Future<String> stringField(String name) {
			return decode(body -> requiredString(new JsonObject(body), name));
		}

		Future<Id> idField(String name) {
			return decode(body -> Id.of(requiredString(new JsonObject(body), name)));
		}
	}

	// ---- Helpers -------------------------------------------------------------------------------

	// Reads a string field of a Director response that must be present and not empty.
	static String requiredString(JsonObject json, String name) {
		String value = json.getString(name);
		if (value == null || value.isEmpty())
			throw new IllegalArgumentException("missing '" + name + "'");

		return value;
	}

	// Encodes a value for use as one path segment or one query parameter value.
	static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	static void putIfNotNull(Map<String, ? super Object> map, String key, @Nullable Object value) {
		if (value != null)
			map.put(key, value);
	}

	static Signature.KeyPair decodeKey(String privateKey) {
		byte[] sk = privateKey.startsWith("0x") ? Hex.decode(privateKey.substring(2)) : Base58.decode(privateKey);
		return decodeKey(sk);
	}

	static Signature.KeyPair decodeKey(byte[] privateKey) {
		if (privateKey.length != Signature.PrivateKey.BYTES)
			throw new IllegalArgumentException("Invalid private key");

		return Signature.KeyPair.fromPrivateKey(privateKey);
	}
}
