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

### Native transport

This library carries no platform-specific native libraries, so it runs on Java NIO wherever it is deployed. To use Netty's native transport (epoll on Linux, kqueue on macOS), add the native jars for the platform the application runs on, and create the Vert.x instance the client runs on with `new VertxOptions().setPreferNativeTransport(true)`. For Linux x86_64:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-epoll</artifactId>
    <classifier>linux-x86_64</classifier>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-unix-common</artifactId>
    <classifier>linux-x86_64</classifier>
    <scope>runtime</scope>
</dependency>
```

The native jars must match the Netty version on the class path. See [Native Transport](https://docs.bosonnetwork.io/build-apps/java-sdk/native-transport) for every platform, Maven profiles and Gradle.

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

A client acts as one user. Configure the **user key** to register the user and to authenticate as
the user, or the **user id with a device key** to authenticate as a device already registered to the
user.
A client configured with neither is rejected when it is built.

There is no sign-in: the client issues its own short-lived access tokens, signed with its key and
bound to the node id - the one configured with `nodeId(...)`, or else the one the Director reports.
Configure the node id when you know it. A client whose clock is far off learns the Director's clock
from the first token the Director rejects, and dates its tokens by it from then on.

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

To reach a Director at another address than its host name resolves to - over loopback, a LAN address
or a tunnel - configure `resolveToAddress(...)`. The client connects there, while requests still name
the URL's host and TLS still verifies the certificate against it.

## Admin client

`DirectorAdmin` covers the Director's admin API: users and their devices, subscriptions and payments,
plans and their per-service features, the node blacklist, and federation with other super nodes. It
follows the conventions of `DirectorClient` - a builder, `CompletableFuture` results, the same
exceptions - with these differences:

- It acts as an administrator: the node's root user, or a user marked as an administrator. Any other
  key is refused with `UnauthorizedException`.
- Its tokens carry the admin role, so the node id matters more: configure it whenever you know it.
  Without it the client binds its tokens to the id the Director reports, and a Director that
  reported another node's id could obtain admin tokens valid on that node.
- A lookup completes with an empty `Optional` when there is nothing to find; changing or removing
  something that does not exist fails with `NotFoundException`.
- A list call returns everything, or one page with the totals (`PaginatedResult`). Where the Director
  supports ordering, it takes `Sort` keys naming fields of the listed objects, such as
  `Sort.desc("createdAt")`; each list call's Javadoc names the fields it accepts.

```java
DirectorAdmin admin = DirectorAdmin.builder()
        .vertx(vertx)
        .directorUrl("https://node.example.com:8443")
        .nodeId(nodeId)
        .userKey(adminKey)
        .build();

admin.addUser(new NewUser(userId, "initial-passphrase").name("Bob"))
        .thenCompose(v -> admin.addSubscription(userId, "Pro", Subscription.Status.ACTIVE, 0, endDate))
        .thenAccept(subscription -> System.out.println("Subscribed: " + subscription.getId()));
```

## Errors

Failures are `DirectorException`s carrying the HTTP status, with subclasses for the conditions
worth handling: `InvalidRequestException` (400), `UnauthorizedException` (401),
`ForbiddenException` (403), `NotFoundException` (404), `ConflictException` (409),
`PassphraseRequiredException` (428), `RateLimitException` (429, with `getRetryAfter()`),
`ServiceBusyException` (503), `NotEnabledException` (501, such as federation on a node that
does not federate) and `DirectorServerException` (other 5xx). A call that gets no answer
fails with status `DirectorException.NO_HTTP_STATUS`.

Only a request rejected as unauthorized while the client's clock was far off is repeated, once,
with a token dated by the Director's clock. Nothing else is retried automatically.

## Adding a Director API

Every call of both clients goes through the package-private `DirectorTransport`, which authenticates,
sends the request and maps errors. A new API is one public method that names its path, builds its body, and decodes the
answer with a model class (a plain class with a `@JsonCreator` constructor; not a record, since
Jackson's record support breaks in Android release builds).

## Testing

```sh
mvn test
```

The tests here cover what needs no Director: the checks a call makes before it sends anything, error
mapping, and an unreachable Director. The end-to-end tests, which drive every call against a real
Director, are maintained with the Director.
