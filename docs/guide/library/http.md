# HTTP/1.1: `std:http/Server`, `std:http/Host`, `std:http/Client`, `std:http/Router`, `std:http/UrlClient`

The Standard Library ships three independent HTTP/1.1 modules and two optional
composition modules. Each one is written in Protos over existing byte I/O,
Futures, Tasks, and Actors. None of them grants authority: they work on the
duplexes, listeners, and capabilities you give them. The normative contract is
[`spec/io/HTTP.md`](../../../spec/io/HTTP.md) (D215 for the server, D216 and
D225-A for hosting, D218-B for the client, and D222-C for the router and the
URL client). If this guide and the specification disagree, the specification wins.

## Choosing a module

| Module | Use it when | Needs |
|---|---|---|
| `std:http/Server` | You already have one connection (or any duplex) and want to answer exactly one request on it | A duplex: `ByteReadable` + `ByteWritable` (+ `Closable` for `owning`) |
| `std:http/Host` | You want to serve successive TCP connections continuously with Worker Actors | A live `TcpListener` you acquired with your own `network` |
| `std:http/Client` | You already have one connection (or any duplex) and want to send exactly one request on it | A duplex, as for the server |
| `std:http/Router` | You want one handler that dispatches on method and path to several route handlers | Nothing: it is authority-free |
| `std:http/UrlClient` | You want one HTTP or HTTPS request to an absolute URL, with a buffered response | Your `network`, a `resolver` for host names, and explicit TLS `trust` for `https` |

A short decision rule:

- `Server`: you already own or borrow a duplex and serve once.
- `Host`: you want continuous application hosting with Worker Actors.
- `Client`: you already have the duplex (plain, or secure from
  [`std:tls/Client`](tls.md)).
- `Router`: you need authority-free request dispatch.
- `UrlClient`: you want one URL-based request composed from explicit
  authorities.

Each module is imported by its exact path; no Prelude or ambient binding exists,
and importing one does not import the others:

```protos
http: import("std:http/Server")      // http.Server, http.HttpError
hosting: import("std:http/Host")     // hosting.Host, hosting.HostError
client: import("std:http/Client")    // client.Client, client.ClientError
routing: import("std:http/Router")   // routing.Router
urls: import("std:http/UrlClient")   // urls.UrlClient
```

Every connection carries exactly one request and one *final* response.
Informational `1xx` responses may precede that final response on the wire;
the Client validates and skips them, and the Server never emits one as its
final response. The server always answers with `Connection: close`, and the
client always sends it.
`Host` serves many *connections*, not many requests per connection.

## Authority: the duplex or listener comes from you

`Server` and `Client` use only the byte-I/O (and, for `owning`, `Closable`)
capability of the duplex they are given. Any value with `read`, `write`, and,
where needed, `close` methods that follow the
[byte-I/O protocols](../11-process-io-filesystems-and-authority.md#reads-return-data-eof-or-failure-distinctly)
works, so both run under the standalone CLI over an in-memory duplex. The
tutorials do this.

Real TCP needs the bootstrap-local `network` capability. The standalone CLI
(`protos file.protos`, `protos run`, `protos -e`, and the REPL) does **not**
bind it, and no command-line flag grants it. Today a program obtains it only as
the initial module of a Java Polyglot embedding whose Context allows socket
access. See
[What each launcher grants](../11-process-io-filesystems-and-authority.md#what-each-launcher-grants)
and [Embedding Protos in a Java application](../embedding/java.md#authority-and-thread-policy).

`network.connectTcp` takes only numeric endpoints. Name resolution exists as a
separate, explicitly granted `Resolver` capability (D223,
[`NETWORK.md`](../../../spec/io/NETWORK.md) §14), which, like `network`, the
standalone CLI does not bind. The low-level `Client` never resolves names or
parses URLs; `UrlClient` (below) composes both for you when you pass them.
[Explicit networking and name resolution](networking.md) shows the resolve,
select and connect steps, and the [TLS client guide](tls.md) shows how to
authenticate the connection yourself:

```protos
IpAddress: import("std:network/IpAddresses").IpAddress
IpEndpoint: import("std:network/IpEndpoints").IpEndpoint

loopback: IpAddress(4, 2130706433)    // 127.0.0.1
connection: network.connectTcp(IpEndpoint(loopback, 8080)).value()

listenRequest: {
    ipVersion: 4
    address: loopback
    port: null                        // any free port; see listener.localPort()
}
listener: network.listenTcp(listenRequest).value()
```

## Bytes, not Strings

Request targets, header values, and bodies are `Bytes`. Nothing converts a
`String` for you; a `String` where `Bytes` is required is invalid. Methods,
header names, and the version are Strings. Headers are an `Array` of
`[name, value]` pairs, in order, with duplicates kept. Compare header names
ASCII case-insensitively.

```protos
ascii: (text) => {
    Encoding.Latin1.encode(text)
}
headers: [["Content-Type", ascii("text/plain; charset=utf-8")]]
```

## Server: one request on one duplex

```text
Server.serveOne(duplex, handler, limits)         -> Future<null>
Server.serveOne.owning(duplex, handler, limits)  -> Future<null>
```

The server reads one request, calls `handler(request)` at most once, writes
one final response, and retires the session. The returned Future resolves to
`null` only after all of that. It never listens on a port.

The handler receives one ordinary Request object:

| Slot | Value |
|---|---|
| `method` | String, exactly as received |
| `target` | Bytes, raw and undecoded |
| `version` | `"1.1"` |
| `headers` | Array of `[String, Bytes]` |
| `body` | a `ByteReadable` of the decoded content; `read` answers `null` at its end |

It must *complete with* a Response, an ordinary object:

```protos
handler: (request) => {
    response: {
        status: 200                   // an Integer from 200 through 599
        headers: [["Content-Type", Encoding.Latin1.encode("text/plain")]]
        body: Encoding.UTF8.encode("Hello")    // null, Bytes, or a ByteReadable
    }
    response
}
```

- Do not return a Future: the server does not wait for one, and such a result
  is an `invalidResponse`.
- The server owns framing. A Response header named `Content-Length`,
  `Transfer-Encoding`, `Connection`, `Upgrade`, `Trailer`, `TE`, `Keep-Alive`, or
  `Proxy-Connection` is an `invalidResponse`. A `Bytes` body is sent with
  `Content-Length`; a `ByteReadable` body is streamed with chunked coding and is
  never closed by the server.
- For 204, 205, and 304 the body must be `null` or empty `Bytes`.
- The handler may await `request.body.read(n)` freely; it runs inside the one
  service Task of the session. The body is readable only during the session;
  unread content is not drained or reused.

## Client: one exchange on one duplex

```text
Client.exchangeOne(duplex, request, onResponse, limits)         -> Future<result>
Client.exchangeOne.owning(duplex, request, onResponse, limits)  -> Future<result>
```

The request is ordinary data, captured completely when the call is admitted:

```protos
request: {
    method: "GET"
    target: Encoding.Latin1.encode("/index?x=1")   // origin-form only
    headers: [["Host", Encoding.Latin1.encode("127.0.0.1")]]
    body: null                                     // or Bytes
}
```

Exactly one `Host` field is required and is sent as given. The Client adds
`Content-Length` and `Connection: close`. Caller fields that control framing or
protocol switching are rejected; these are `Content-Length`,
`Transfer-Encoding`, `Connection`, `Expect`, `Upgrade`, `Trailer`, `TE`,
`Keep-Alive`, and `Proxy-Connection`. Informational (1xx) responses are
skipped. The first final response is passed once to `onResponse(response)`,
whose slots are `version`, `status`, `headers`, and `body`.

**Consume `response.body` inside `onResponse`.** The body is a `ByteReadable`
decoded on demand; it is never collected into a String or Bytes for you. The
exchange retires as soon as the callback returns, fails, or is cancelled.
After that, every read of the body, even through a reference that escaped,
fails with a fresh `IOLifecycleError`.

```protos
readText: (response) => {
    received: Encoding.Latin1.encode("")
    piece: response.body.read(1024).value()
    while(piece !== null) {
        piece.each((octet) => {
            received.add(octet)
        })
        piece = response.body.read(1024).value()
    }
    Encoding.UTF8.decode(received)
}
text: Client.exchangeOne.owning(connection, request, readText, null).value()
```

The callback's ordinary result becomes the result of the returned Future. A
404 or 500 is an ordinary response, not a failure. Returning a pending Future
from the callback is adopted as usual, but it does not keep the body readable.

## Router: dispatch by method and path

```text
Router.compile(routes) -> handler
```

`routes` is an Array of `{method, path, handle}` entries. A path is literal
segments and whole-segment `{name}` captures; `"*"` is allowed only for
`OPTIONS`. The table is validated and snapshotted when you compile it:
duplicates, crossing overlaps such as `/a/{x}` with `/{y}/b`, and malformed
paths signal an `Error` right away, never per request.

```protos
routing: import("std:http/Router")
ascii: (text) => { Encoding.Latin1.encode(text) }

handler: routing.Router.compile([
    {
        method: "GET"
        path: "/users/me"
        handle: (request, match) => {
            { status: 200, headers: [], body: ascii("me") }
        }
    },
    {
        method: "GET"
        path: "/users/{id}"
        handle: (request, match) => {
            { status: 200, headers: [], body: ascii("user " + match.params["id"]) }
        }
    }
])
```

The handler suits `Server.serveOne` and `Host` handler modules. A literal
segment beats a capture regardless of declaration order. `match.params` holds
the raw, still percent-encoded segment text (`%2F` is never a separator), and
`match.query` is the raw query String, `""` for a bare `?`, or `null`. A
trailing `/` is significant. `GET` routes also serve `HEAD` unless a `HEAD`
route exists; `OPTIONS` is never invented. Unknown paths answer a bodiless 404,
and known paths with another method a 405 with `Allow`. The Router never reads
the request body and holds no authority.

## UrlClient: one request to a URL

```text
UrlClient.requestOne(authorities, url, request, limits) -> Future<{status, headers, body}>
```

```protos
urls: import("std:http/UrlClient")
Trust: import("std:tls/Client").Trust

authorities: {
    network: network            // bootstrap-local grant
    resolver: resolver          // bootstrap-local grant; may be null for numeric hosts
    trust: Trust.javaCacerts()  // explicit; used only for https
}
request: {
    method: "GET"
    headers: [["Accept", Encoding.Latin1.encode("application/json")]]
    body: null
}
response: urls.UrlClient.requestOne(authorities, "https://example.org/a?b=1", request, null).value()
```

The URL is parsed by `std:uri`; it must be absolute `http` or `https`, without
user information or fragment. The URL host becomes the `Host` field (with an
explicit port kept exactly as written), and the raw path and query become the
origin-form target; do not pass your own `Host`. A host name is looked up once
with your `resolver`, and the returned addresses are tried in order with one
TCP connect each; a numeric host is connected directly. For `https`, the TLS
reference identity and SNI are the URL host name (or the numeric address,
without SNI), never the resolved address. There is no default trust:
`Trust.system()` has no adapter yet, so pass `Trust.javaCacerts()` or
`Trust.anchors(...)`. See the [TLS client guide](tls.md) for trust, identity
and failure kinds, and for the same composition written step by step.

Unlike the low-level `Client`, whose callback-scoped `response.body` must be
read inside `onResponse`, `UrlClient` reads the whole body inside its own
callback and answers fresh `Bytes`, bounded by `maxResponseBodyBytes`
(32 MiB by default). Every status, including redirects and errors, is an
ordinary response: nothing follows redirects, retries, decodes JSON, or
stores cookies. Connections are closed exactly once on every path, including
cancellation.

`Host.owningWithFactory` (D225-A) only lets each Worker build its handler from
transferable initialization data. It does not give a Worker `network` or
`resolver`, so a Worker handler cannot call `UrlClient` with capabilities it was
never granted.

## Borrowed versus owning

Both `serveOne` and `exchangeOne` come in two variants. They follow the same
borrow/own convention as the
[text wrappers](../11-process-io-filesystems-and-authority.md#borrowing-versus-owning-is-a-lifetime-decision):

- **Borrowed** (`serveOne`, `exchangeOne`) never closes or half-closes the
  duplex. Closing it remains your job after the Future completes, ideally with
  `ensure`:

  ```protos
  answer: (() => {
      Client.exchangeOne(connection, request, readText, null).value()
  }).ensure(() => {
      connection.close().value()
  })
  ```

- **Owning** (`serveOne.owning`, `exchangeOne.owning`) takes custody once the
  call is admitted. It closes the duplex exactly once on every terminal path,
  including failure and cancellation, and its Future completes only after that
  close. A close failure takes precedence over the earlier outcome, as in
  [`ensure` cleanup](../07-errors-handlers-ensure-and-resource-lifetime.md#a-later-cleanup-transfer-takes-precedence).

Either way, octets the library has already read are consumed. A duplex is not
safe to reuse for a second message.

## Futures, Tasks, and cancellation

Each admitted call creates exactly one service Task in the calling Actor, as a
structured child of the caller's Task (see
[Futures and structured concurrency](../08-futures-and-structured-concurrency.md#structured-concurrency-is-task-scoped)).
The handler or callback, every body read, and every write run inside that Task.
No extra Task is created per read or per chunk.

- Cancelling the returned Future unwinds the session. For `owning`, the duplex
  is closed before the Future becomes `cancelled`.
- A borrowed duplex is never closed by cancellation.
- Nothing times out. A slow peer can hold a session indefinitely unless you
  cancel it.

## Host: continuous hosting with Workers

```text
Host.owning(listener, handlerModule, handlerBinding, options) -> host
Host.owningWithFactory(listener, factoryModule, factoryBinding,
                       initialization, options)          -> host
host.ready()              -> Future<host>
host.shutdown("drain")    -> Future<host>
host.shutdown("abort")    -> Future<host>
host.termination()        -> Future<host>
```

```protos
Host: import("std:http/Host").Host

options: {
    workers: 2
    maxConnections: 4
}
host: Host.owning(listener, "app:hello", "handle", options)
host.ready().value()
// ... serve ...
host.shutdown("drain").value()
```

**The handler is named, not passed.** `handlerModule` and `handlerBinding` are
Strings. Each [Worker Actor](../10-actors-actorrefs-and-groups.md) imports its
own instance of that module and calls that top-level binding, which receives
the same Request and returns the same Response as with `serveOne`. A Closure is
rejected in their place; this keeps mutable state out of the Workers. The module
specifier is resolved by the Worker under the
[ordinary module rules](../06-modules-and-imports.md#module-instances-are-actor-local),
not relative to your file. In practice, use a specifier every Actor can resolve,
such as an [`app:` module](../embedding/application-modules.md) supplied by the
embedding host. A missing module or binding is not a construction error; it
fails `ready()` with a `HostError` of kind `"startup"`.

**Building each Worker's handler from data you pass.** When the handler needs
configuration or a reference to one of your own service Actors, use
`Host.owningWithFactory`. Instead of naming the handler, you name a *factory*
binding and pass an `initialization` value:

```protos
// app:site-handler
make: (dependencies) => {
    projects: dependencies.projects
    (request) => {
        names: projects.request("publicProjects").value()
        response: {
            status: 200
            headers: []
            body: Encoding.UTF8.encode(names)
        }
        response
    }
}
```

```protos
// projectsService is an ActorRef of a service Actor your program created.
host: Host.owningWithFactory(
    listener,
    "app:site-handler",
    "make",
    { projects: projectsService },
    { workers: 4 }
)
host.ready().value()
```

Each Worker gets its own copy of `initialization`, made by the ordinary
[Actor pass-by-value rules](../10-actors-actorrefs-and-groups.md). It calls
`make` exactly once in its lifetime with that copy, before it accepts anything,
and then uses the returned callable for every request: `handler(request)`,
exactly as with `Host.owning`. The factory is never called per request, and
its result is never awaited. `ready()` resolves only once every Worker has
built and validated its handler. The Worker Actors themselves already exist
while their factories run. `shutdown("abort")` during that time cancels a
suspended factory cooperatively, and no Worker starts accepting afterwards. An `ActorRef` in `initialization` still denotes the same
Actor in every Worker. Mutable objects are copied, so later changes made by you
or by another Worker are not seen.

`initialization` cannot carry live resources: a Closure, a Future, `network`,
a Resolver, a listener, a connection, or a `TcpAcceptor` is rejected exactly
as at any Actor boundary. Like a failing factory, a non-callable result, or a
missing module or binding, a rejected `initialization` is not a construction
error; it fails `ready()` with a `HostError` of kind `"startup"`, and the host
still closes the listener. Invalid `listener`, `options`, or non-String module
and binding arguments signal `InvalidIOArgument` synchronously, as for
`Host.owning`.

The host does not own the service Actors you reference. Shutting a host down
does not stop them. A service that stops makes only the requests that use it
fail, each as an ordinary handler failure. Several hosts may share one
service. Creating a service with outbound `network` or Resolver authority is
your program's business; `Host` provides no mechanism for it.

**The RootActor as the application service (D225-B1).** When a Java host
starts your program through the explicit RootActor message-service entry
(`ProtosRootActorServiceSession`, see [Actors, ActorRefs, and Actor
Groups](../10-actors-actorrefs-and-groups.md#the-rootactor-has-two-entry-modes)),
the RootActor itself can be the service the Workers call. Pass the Workers a
single-member GroupRef to it, which lets them `request` and `send` but not
`stop()` it:

```protos
Host: import("std:http/Host").Host

hits: 0
host: null

// Bootstrap binding: runs once in the RootActor. Host startup awaits, so it
// happens in an ordinary message turn after READY.
service: () => {
    Actor.current().send("start", Actor.group(Actor.current()))
    behavior: {
        start: (self) => {
            // listenRequest: a loopback listen request, as in the Host example above.
            listener: network.listenTcp(listenRequest).value()
            host = Host.owningWithFactory(listener, "app:handler", "make", { service: self }, { workers: 4 })
            host.ready().value()
            null
        }
        hit: () => {
            hits = hits + 1
            hits
        }
    }
    behavior
}

```

```protos
// app:handler, supplied in the session's application module catalog.
// Each Worker imports its own instance and calls make once.
make: (dependencies) => {
    service: dependencies.service
    (request) => {
        service.request("hit").value()
        response: {
            status: 200
            headers: []
            body: Encoding.UTF8.encode("counted")
        }
        response
    }
}
```

A Worker imports its factory module inside its own Actor, so the factory is
named by an exact `app:` identity whose source the Java host passes as the
`applicationModules` argument of `ProtosRootActorServiceSession.open(...)` (see
[Hosting a RootActor message service](../embedding/java.md#hosting-a-rootactor-message-service));
a `./` path would not resolve there. Every Worker reaches the same RootActor
state through its copy of the GroupRef, and the RootActor keeps `network` (and `resolver`, when granted) to
itself: neither can travel in `initialization` or in a message. The Host owns
its listener and Workers but **not** the RootActor service. `shutdown("drain")`
or `shutdown("abort")` follows the usual lifecycle below and leaves the
RootActor running; terminating the RootActor's Process (for example closing the
session, or a fatal RootActor failure) ends the Host and its Workers with it.
A complete version is `protos/examples/http/root-service-host.protos` with its
factory `protos/examples/http/root-service-handler.protos`. This is
a composition pattern, not a REST framework: routing, retries and deadlines
remain your program's business. For the Java side, see
[Hosting a RootActor message service](../embedding/java.md#hosting-a-rootactor-message-service);
the application, its Workers and their handlers stay Protos source.

**The host owns the listener.** After a valid `Host.owning` or
`Host.owningWithFactory`, do not accept from or close the listener yourself.
The host closes it exactly once on every path.
Each Worker accepts by itself and serves each accepted connection with
`Server.serveOne.owning`.

**Options are bounds, not hints.** `options` is `null` or an object with only
these own slots:

| Slot | Default | Meaning |
|---|---|---|
| `workers` | `1` | Worker Actors to start |
| `maxConnections` | `64` | accepted connections not yet fully retired |
| `maxPendingAccepts` | `workers` | outstanding `accept` operations |
| `limits` | `null` | the `serveOne` limits used for every session |

`1 <= workers <= maxPendingAccepts <= maxConnections` must hold. Quotas are
split between Workers up front. At capacity a Worker stops accepting; it never
accepts a connection just to answer 503. Invalid arguments signal a fresh
`InvalidIOArgument` *synchronously* from `Host.owning`, before custody is
taken. This differs from `serveOne` and `exchangeOne`, which report invalid
arguments through an already-failed Future.

**Lifecycle.**

- `ready()` resolves with the same `host` once every Worker is accepting.
- `shutdown("drain")` stops admission, closes the listener, and lets admitted
  sessions finish. It does not wait for new clients.
- `shutdown("abort")` cancels admitted sessions cooperatively (their owning
  cleanup still runs). An abort during a drain escalates. A drain never
  reverses an abort. Repeating a request is harmless.
- `termination()` observes the end, however it was caused.

Each shutdown Future resolves only after the Workers have terminated, every
accepted connection has been closed, and the listener close has settled. Any
other mode string signals `InvalidIOArgument`. Cancelling one of these Futures
only abandons that observation; it does not stop or speed up the host.

Failures of individual requests or handlers stay inside their session and do
not stop the host. A Worker that dies unexpectedly, or a failing listener, makes
the host abort and fails its observers with a `HostError`.

## Limits

Both `serveOne` and `exchangeOne` take `limits`: `null` for every default, or an
object whose own slots override some of them. Values are positive Integers.
Limits are captured at admission. An unknown slot or invalid value makes the
returned Future already failed with `InvalidIOArgument`, before any I/O.

| Server limit | Default | Client limit | Default |
|---|---|---|---|
| `maxRequestLineBytes` | 8192 | `maxStatusLineBytes` | 8192 |
| `maxHeaderBytes` | 32768 | `maxHeaderBytes` | 32768 |
| `maxHeaderFields` | 100 | `maxHeaderFields` | 100 |
| `maxChunkLineBytes` | 8192 | `maxChunkLineBytes` | 8192 |
| `maxTrailerBytes` | 8192 | `maxTrailerBytes` | 8192 |
| `maxTrailerFields` | 100 | `maxTrailerFields` | 100 |
| `maxRequestBodyBytes` | 33554432 | `maxResponseBodyBytes` | 33554432 |
| `maxPrefetchBytes` | 65536 | `maxPrefetchBytes` | 65536 |
| `ioChunkBytes` | 16384 | `ioChunkBytes` | 16384 |
| | | `maxInformationalResponses` | 8 |

Limits constrain message sizes and buffering; they are not timeouts.

## Errors

Each module has its own Error prototype, delegating directly from `Error`.
Each failure is a fresh occurrence with a `kind` String:

| Prototype | `kind` values | Extra |
|---|---|---|
| `HttpError` (server) | `"malformedRequest"`, `"requestLimit"`, `"invalidResponse"`, `"unsupportedFeature"` | `status` (400, 413, 414, 417, 431, 501, 505) when one applies |
| `ClientError` | `"malformedResponse"`, `"responseLimit"`, `"unsupportedFeature"` | no `status` |
| `HostError` | `"startup"`, `"listener"`, `"worker"`, `"shutdown"` | — |

- A request rejected before the handler gets a best-effort error response,
  and the server's Future fails with the `HttpError`. If the handler fails, the
  server may send an empty 500, and the Future fails with the handler's own
  Error.
- A response-body framing or limit violation fails the body read that reaches
  it. The exchange then fails with that same `ClientError` even if the
  callback handled it.
- I/O Errors, `Cancelled`, `IOLifecycleError`, and `InvalidIOArgument` keep
  their own categories; they are never wrapped in an HTTP Error.

Handle them with the ordinary [dynamic handlers](../07-errors-handlers-ensure-and-resource-lifetime.md):

```protos
kind: http.HttpError.handle(
    () => {
        http.Server.serveOne.owning(duplex, handler, null).value()
        "served"
    },
    (error) => {
        error.kind
    }
)
```

## Not supported

None of the following exists in these modules. Do not expect it:

- HTTPS in `Server` or `Host`; the low-level `Client` runs over a secure
  duplex from [`std:tls/Client`](tls.md) but does no TLS itself;
- DNS resolution, URL parsing, or connection acquisition by the low-level
  Client (use `UrlClient` with explicit capabilities);
- keep-alive, pipelining, or more than one request per connection;
- connection pooling, retries, or automatic redirects;
- built-in timeouts, deadlines, or slow-client eviction;
- HTTP/2, HTTP/3, Upgrade, WebSocket, or CONNECT;
- cookies, authentication, middleware, content codings, or exposed trailers;
- streaming request bodies in the Client (the body is `null` or `Bytes`).

The server is meant for development, loopback, trusted networks, or deployment
behind a protective ingress. It is **not** hardened for direct exposure to
hostile Internet traffic.

## Runnable material

| Source | What it shows | Runs under |
|---|---|---|
| [`tutorials/13-http/01-serve-one-response.protos`](../../../protos/tutorials/13-http/01-serve-one-response.protos) | Request and Response shapes with a borrowed `serveOne` | standalone CLI |
| [`tutorials/13-http/02-client-response-body.protos`](../../../protos/tutorials/13-http/02-client-response-body.protos) | Incremental body reads inside `onResponse`, the owning close, the retired body | standalone CLI |
| [`tutorials/13-http/03-host-and-client-over-tcp.protos`](../../../protos/tutorials/13-http/03-host-and-client-over-tcp.protos) | `Host.owning` with Workers, Client GETs over TCP, drain | Java embedding with `network` |
| [`examples/io/http-minimal-response.protos`](../../../protos/examples/io/http-minimal-response.protos) | One fixed response; a rejected request and its `HttpError` | standalone CLI |
| [`examples/io/http-get.protos`](../../../protos/examples/io/http-get.protos) | GET over a connection the application opens, borrowed and closed with `ensure` | Java embedding with `network` |
| [`examples/io/http-loopback-hosting.protos`](../../../protos/examples/io/http-loopback-hosting.protos) | Bounded hosting with limits, concurrent clients, drain | Java embedding with `network` |
| [`examples/io/http-router.protos`](../../../protos/examples/io/http-router.protos) | A compiled Router handler with literal and capture routes, params, and query | standalone CLI |
| [`tutorials/13-http/04-router-dispatch.protos`](../../../protos/tutorials/13-http/04-router-dispatch.protos) | A two-route Router: literal and captured paths, the raw query, a 404 | standalone CLI |
| [`examples/io/http-url-client.protos`](../../../protos/examples/io/http-url-client.protos) | An HTTPS `UrlClient` request with explicit `network`, `resolver`, and trust | Java embedding with `network` and `resolver` (manual) |
| [`examples/io/tls-client-over-tcp.protos`](../../../protos/examples/io/tls-client-over-tcp.protos) | Direct composition: resolve, connect, `Client.handshake`, then `Client.exchangeOne` over the secure duplex | Java embedding with `network` and `resolver` (manual) |
| [`examples/http/root-service-host.protos`](../../../protos/examples/http/root-service-host.protos) with [`root-service-handler.protos`](../../../protos/examples/http/root-service-handler.protos) | The RootActor as the application service of factory-built Workers, through a GroupRef | `ProtosRootActorServiceSession` with `network` and the handler as `app:root-service-handler` (the same composition is tested in `ProtosHttpHostFactoryIntegrationTest`) |

The CLI sources run with `bin/protos <file>` from the repository root. The
embedding sources are not all executed the same way:

- [`ProtosHttpLearningMaterialIntegrationTest.java`](../../../src/test/java/com/guillermomolina/protos/execution/integration/http/ProtosHttpLearningMaterialIntegrationTest.java)
  runs the basic Host/Client learning sources (`03-host-and-client-over-tcp`,
  `http-get`, `http-loopback-hosting`) unchanged. It grants socket access and
  installs the handler modules in the `app:` catalog.
- `ProtosHttpHostFactoryIntegrationTest` covers the RootActor service and Host
  factory composition, `ProtosHttpUrlClientIntegrationTest` the UrlClient
  protocol behavior, `ProtosTlsClientIntegrationTest` the TLS client, and
  `ProtosResolverAuthorityTest` explicit Resolver authority. They use their own
  controlled fixtures; they do not run the manual examples verbatim.
- `http-url-client.protos` and `tls-client-over-tcp.protos` are manual
  examples that reach a real external origin; no automated test runs them.

More exhaustive behavior is exercised by the
[Protos HTTP tests](../../../protos/tests/library/http/) and the
[HTTP integration tests](../../../src/test/java/com/guillermomolina/protos/execution/integration/http/).

## Normative references

- [`spec/io/HTTP.md`](../../../spec/io/HTTP.md): §§1–13 Server, §15 Host
  (§15.13 for `owningWithFactory`), §16 Client, §17 Router, §18 UrlClient.
- [`spec/io/TLS.md`](../../../spec/io/TLS.md): `std:tls/Client` and trust.
- [`spec/io/NETWORK.md`](../../../spec/io/NETWORK.md): TCP, listeners, and
  `network` authority.
- [`spec/io/IO_CORE.md`](../../../spec/io/IO_CORE.md) and
  [`spec/io/BYTE_IO.md`](../../../spec/io/BYTE_IO.md): duplex reads, writes,
  and close.
- [`spec/concurrency/FUTURES_AND_TASKS.md`](../../../spec/concurrency/FUTURES_AND_TASKS.md)
  and [`spec/concurrency/ACTORS.md`](../../../spec/concurrency/ACTORS.md).
- [`spec/semantics/ERRORS.md`](../../../spec/semantics/ERRORS.md) and
  [`spec/semantics/MODULES.md`](../../../spec/semantics/MODULES.md).
