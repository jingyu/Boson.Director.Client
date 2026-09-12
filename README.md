# Boson Director Client

A Java client for the client API of a [Boson](https://github.com/bosonnetwork) Director, the
account service of a Boson super node. It wraps the Director's REST API (`/api/v1/client/*`) in a
small asynchronous API: callers deal in `Id`s, keys and model objects, never in HTTP requests,
tokens or wire encodings.

## Features

| Area | Calls |
|---|---|
| Registration | `registerUser` - proof-of-work registration, optionally with an initial device |
| Devices | `registerDevice`, `listDevices`, `removeDevice` |
| Passphrase | `setPassphrase`, `updatePassphrase`, `clearPassphrase` |
| Profile | `getProfile`, `updateProfile` |
| Avatar | `updateAvatar` (bytes or file), `getAvatar` |
| Node | `getNodeId`, `getNodeStatus` |
| Plan | `getPlan` - the plan name, its catalog entry and the active subscription |

Every call returns a `CompletableFuture` that completes on the caller's Vert.x context: a call made
on a Vert.x context completes on that context, and so do the continuations chained on it. A Vert.x
caller can convert one back with `Future.fromCompletionStage`. Cancellation is not supported.

## Dependency

```xml
<dependency>
    <groupId>io.bosonnetwork</groupId>
    <artifactId>boson-director-client</artifactId>
    <version>${boson.version}</version>
</dependency>
```

## Usage

```java
DirectorClient director = DirectorClient.builder()
        .vertx(vertx)
        .directorUrl("https://node.example.com:8443")
        .userKey(userKey)       // Signature.KeyPair of the user
        .deviceKey(deviceKey)   // Signature.KeyPair of this device (optional)
        .build();

// Create the account, proving the registration with proof-of-work, and register this device.
director.registerUser(new UserRegistration()
                .name("Alice")
                .initialDevice("Alice's laptop", "MyApp"))
        .thenCompose(v -> director.getPlan())
        .thenAccept(plan -> System.out.println("Plan: " + plan.getName()))
        .whenComplete((v, e) -> director.close());
```

A client acts as one user. Configure the **user key** to register the user and to sign in as the
user, or the **user id with a device key** to sign in as a device already registered to the user.
A client with no identity can still call `getNodeId` and `getNodeStatus`.

Signing in is automatic: the client signs a nonce, exchanges it for an access token, caches the
token until shortly before it expires, and renews it as needed.

### Passphrase

Once an account has a passphrase, the Director requires it to register or remove a device and to
update the profile. Those calls take an optional passphrase argument; pass `null` when the account
has none. A missing passphrase fails with `PassphraseRequiredException`, a wrong one with
`ForbiddenException`.

### Registration policy

`registerUser` is the permissionless path: it works on nodes whose `registrationPolicy` is `pow` or
`either`. On an `oauth` node it fails with `RegistrationDisabledException`. A node on the legacy
`open` policy does not accept proof-of-work, and refuses the registration as an invalid request.

Solving the challenge is memory-hard by design. It runs on a Vert.x worker thread, typically for
about a second, and longer when the node raises the effort under load.

### Transport security

Use an `https` Director URL for any Director that is not on the local machine. A certificate from
a public CA is validated as usual. Configure `nodeId(...)` to also accept a self-signed Director
certificate pinned to the node's id.

## Errors

Failures are `DirectorException`s carrying the HTTP status, with subclasses for the conditions
worth handling: `InvalidRequestException` (400), `UnauthorizedException` (401),
`ForbiddenException` (403), `NotFoundException` (404), `ConflictException` (409),
`PassphraseRequiredException` (428), `RateLimitException` (429, with `getRetryAfter()`),
`ServiceBusyException` (503) and `DirectorServerException` (other 5xx). A call that gets no answer
fails with status `DirectorException.NO_HTTP_STATUS`.

Only a request rejected as unauthorized is repeated, once, after signing in again. Nothing else is
retried automatically.

## Adding a Director API

Every call goes through one private helper in `DirectorClient` that signs in, sends the request and
maps errors. A new API is one public method that names its path, builds its body, and decodes the
answer with a model class (a plain class with a `@JsonCreator` constructor; not a record, since
Jackson's record support breaks in Android release builds).

## Testing

```sh
mvn test
```

The tests here cover what needs no Director: the checks a call makes before it sends anything, error
mapping, and an unreachable Director. The end-to-end tests, which drive every call against a real
Director, are maintained with the Director.
