# Protos HTTP/1.1 Server Library v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-10-27

This document is the primary normative owner of the Standard Library HTTP/1.1
single-request origin-server facility `std:http/Server` selected by D215
(D215-F3–F8: Option A, A3-minimal, Q0, F3 = M1, F4 = H1, F5 = B1, F6 = L1,
F7 = W1, F8 = S1), and of the separate optional continuous-hosting facility
`std:http/Host` selected by D216. D216 does not revise D215.

HTTP is an optional Standard Library built entirely on existing capabilities.
Byte reading/writing, Future commitment, cancellation, and half-close semantics
remain owned by `IO_CORE.md` and `BYTE_IO.md`; TCP, `Network` authority, and
live-resource transfer boundaries remain owned by `NETWORK.md`; Error
construction, freshness, taxonomy, and cleanup precedence remain owned by
`../semantics/ERRORS.md`; Task, Future, and structured-concurrency semantics
remain owned by `../concurrency/FUTURES_AND_TASKS.md` and
`../concurrency/ACTORS.md`. This document adds only HTTP-specific semantics and
does not restate those contracts.

The HTTP/1.1 message syntax and framing referred to below are those of RFC 9110
(HTTP Semantics) and RFC 9112 (HTTP/1.1). Where this document is stricter than
those RFCs, this document governs.

## 1. Scope and governing model

`std:http/Server` processes exactly one HTTP/1.1 request on a caller-supplied
duplex byte flow, produces exactly one final response, and retires its HTTP
session. §§1–13 describe that single-request facility; §15 separately defines
optional continuous hosting without changing any `std:http/Server` behavior.

The single-request facility:

- does not listen on ports, accept connections, acquire or use a `Network`
  capability, resolve names, create Actors, or acquire clock, timer, TLS, or any
  other ambient host authority;
- exercises no authority beyond the byte-I/O (and, for the owning variant,
  `Closable`) capability of the duplex it is given;
- introduces no new Core type, Prelude binding, Core error mechanism, nominal
  class requirement, public response-writer object, or public HTTP event
  alphabet; and
- defines no Java/host service-provider interface. Any conforming
  implementation may realize it over any duplex satisfying §2.

The approved concurrency model is an Actor that owns the connection and runs
cooperative Tasks. Each admitted service runs in exactly one cooperative
service Task of that Actor (§2.3). Admission control, connection concurrency,
and the number of concurrently served connections are application
responsibilities; the library defines no global connection or Task limit.

A program that does not import `std:http/Server` pays no conceptual or runtime
cost for this facility.

## 2. Module and entry points

The explicit Standard Library module `std:http/Server` exports the members
`Server` and `HttpError`. They are reachable only through an explicit import,
for example:

```text
http: import("std:http/Server")
Server: http.Server
HttpError: http.HttpError
```

No Prelude or ambient binding exists for either member.

`Server` exposes exactly two service operations:

```text
Server.serveOne(duplex, handler, limits)         -> Future<null>
Server.serveOne.owning(duplex, handler, limits)  -> Future<null>
```

The naming follows the existing borrow/own factory convention of
`std:io/BufferedReader` and `std:io/BufferedWriter` (`IO_CORE.md` §9).

### 2.1 Duplex admission

`duplex` is any value that satisfies both `ByteReadable` and `ByteWritable`.
`serveOne.owning` additionally requires `Closable`. The facility does not
require `TcpConnection` identity, `ReadShutdown`, `WriteShutdown`, or any other
capability, and does not inspect host identity of the duplex.

### 2.2 Handler

`handler` is an ordinary callable. The driver invokes it at most once per
`serveOne` operation: exactly once when processing of the request reaches the
handler-invocation phase, and not at all when the operation fails or the
request is rejected before that phase. The invocation has the form:

```text
handler(request)
```

with exactly one argument, the Request object (§3). No `context`,
`connection`, `server`, capability, or other argument is injected. Lookup,
invocability, and call semantics are those of `../semantics/CALLABLES.md`.

The value the call completes with must itself be a Response (§4). The driver
does not await a returned `Future`, does not convert a `Future` or any other
value into a Response, and does not invoke the result to obtain a Response. A
result that is not a Response is `invalidResponse` (§4, §12).

The handler is invoked within the service Task (§2.3). When the handler
awaits a pending `Future` (for example one returned by `request.body.read()`),
that service Task suspends cooperatively, and the continuation of the same
Task resumes when the awaited operation allows it, under the general Task
rules of `../concurrency/FUTURES_AND_TASKS.md`. The driver never invokes the
handler from a synchronous host callback that would introduce a hidden
suspension, never fabricates continuations outside those general rules, and
defines no HTTP-specific callback, reentrancy, or scheduling model.

### 2.3 Service Task and service Future

Both entry points return `Future<null>`. Each successfully dispatched
invocation returns a fresh standard `Future` whose identity follows
`../concurrency/FUTURES_AND_TASKS.md`.

The following order is a logical precedence, not a scheduling strategy:

1. ordinary dispatch of the entry point (§2.4);
2. validation of the arguments after dispatch (§2.4);
3. if that validation fails, the returned Future is already failed with a
   fresh `InvalidIOArgument` occurrence, and none of the remaining steps
   occurs;
4. otherwise, the service is admitted: the operative HTTP session is
   established and, for `serveOne.owning`, close responsibility for `duplex`
   is acquired (§9.2);
5. exactly one cooperative service Task is created for the admitted service;
6. the `Future<null>` representing that Task's result is returned; and
7. HTTP processing proceeds in that service Task.

Establishing the session and acquiring close responsibility are part of
successful admission. The Future of an admitted service is therefore never
exposed to cancellation before the applicable close responsibility has been
established. This precedence is logical and does not impose a scheduling
strategy.

The service Task:

- belongs to the same Actor as the invoker;
- is created as a structured child of the invoker's structured execution
  scope, or, when the invoker has no current Task, of the root asynchronous
  scope of the invoker's Actor incarnation, under
  `../concurrency/FUTURES_AND_TASKS.md` §24; it is not detached;
- involves no transfer across Actors and no P work; and
- is responsible for processing the request, validating its framing,
  handling `Expect` (§7.4), invoking the handler (§2.2), emitting the
  response (§7.5, §8), completing the session (§9), and performing its
  cleanup under the general rules.

The returned Future represents the result of the service Task. Normal
termination of that Task, after the session has completed normally (§9),
resolves it to `null`; a failure of the service fails it under §12 and the
cleanup-precedence rules of `../semantics/ERRORS.md`. Cancellation and
closure of the structured scope follow the general rules of
`../concurrency/FUTURES_AND_TASKS.md`.

No additional Task is created solely to invoke the handler, read
`request.body`, emit `100 Continue`, emit the final response, write chunks,
perform cleanup, or perform any individual I/O operation. I/O operations use
the ordinary `Future` and I/O-producer mechanisms of `IO_CORE.md` and
`../concurrency/FUTURES_AND_TASKS.md`; an I/O `Future` is not the creation of
another Task. Each admitted service has exactly one service Task.

### 2.4 Argument validation

Failures before ordinary dispatch of the entry point — lookup, argument
evaluation, a receiver outside the operation's domain, and ordinary arity
failure — follow the general language rules.

After ordinary dispatch has succeeded, semantic validation of the HTTP
arguments uses only the returned Future, following the general rule of
`IO_CORE.md` ("Portable I/O Error categories"). The returned Future fails with
a fresh `InvalidIOArgument` occurrence when:

1. `duplex` does not satisfy both `ByteReadable` and `ByteWritable`;
2. for `serveOne.owning`, `duplex` does not satisfy `Closable`;
3. `handler` does not satisfy the invocability protocol;
4. `limits` is of the wrong type (§10);
5. `limits` has an own slot naming an unknown limit (§10); or
6. `limits` has an invalid value for a recognized limit (§10).

This validation occurs before any HTTP I/O attributable to the operation: when
it fails, no service Task (§2.3) and no operative HTTP session are created, no
duplex ownership is acquired (§9.2), no input is consumed, no output octet is
contributed, and no HTTP response is emitted. These failures are never
represented by `HttpError`. Because they depend only on already-evaluated
arguments, the returned Future may already be failed when it is returned. No synchronous error is signaled for these
conditions after successful dispatch. The borrow/own naming convention shared
with other I/O facilities (§2) does not import their synchronous-error rules
into this contract; failures before ordinary dispatch follow the general
language rules stated above.

## 3. Request object

The Request passed to the handler is a fresh ordinary structural object with
these own slots:

| Slot | Value |
| --- | --- |
| `method` | `String`: the request method token exactly as received |
| `target` | `Bytes`: the raw request-target octets exactly as received |
| `version` | `String`: exactly `"1.1"` |
| `headers` | `Array` of two-element `Array` pairs `[name, value]` (§5) |
| `body` | `ByteReadable` bounded to the request message content (§6) |

Request is not an instance of a required nominal prototype. Its slots and the
`headers` Array and pair Arrays are ordinary mutable values owned by the
handler. The request-target is not decoded, normalized, or converted to
`String`.

Mutation of the Request object, its `headers`, or any pair by the handler never
affects the driver's private framing state. The framing decision for the
request body (§7) is fixed by the driver from the received octets before the
handler is invoked.

## 4. Response object

The handler must complete with a Response: an ordinary structural object with
these own slots:

| Slot | Value |
| --- | --- |
| `status` | `Integer` in the inclusive range 200 through 599 |
| `headers` | `Array` of two-element `Array` pairs `[name, value]`, `name` a `String`, `value` a `Bytes` |
| `body` | `null`, `Bytes`, or a `ByteReadable` |

Response is not an instance of a required nominal prototype. A 1xx status
(including 101) is never an acceptable final response.

The facility does not implicitly convert a returned `Future` into a Response by
waiting for it, and does not convert `String` bodies or `String` header values
to `Bytes`. A handler result that is not a valid Response under this section is
an `invalidResponse` failure (§12).

Response header names must be valid HTTP field-name tokens and values must
contain only octets valid in an HTTP field value (in particular no CR, LF, or
NUL); otherwise the result is `invalidResponse`.

For status 204, 205, and 304, `body` must be `null` or a `Bytes` of size zero.
A non-empty `Bytes` body, or any `ByteReadable` body regardless of the content
it would produce, is `invalidResponse` (a fresh `HttpError` of kind
`invalidResponse`), detected before the final response is committed. The
driver never invokes `read()` on, inspects, or consumes a `ByteReadable` body
to determine whether it is empty, and never silently discards an incompatible
body. This status/body validation precedes every emission rule, framing
selection, and hypothetical-length computation of §7.5, including the `HEAD`
rules.

### 4.1 Response capture

Before emitting any octet of the final response, the driver logically captures
the Response's `status`, its header pairs in order, and, when `body` is
`Bytes`, a snapshot of that Bytes' size and contents. This capture follows the
same logical-snapshot rule as `ByteWritable.write` (`BYTE_IO.md` §6). Later
mutation of the Response, its `headers`, any pair, or the body `Bytes` does not
change the emitted response. A `ByteReadable` body is captured by reference
only; its content is read during emission (§8).

## 5. Header fields

### 5.1 Request header representation

Request `headers` preserves, in received order:

- every field line, including duplicate field names; `Set-Cookie` and every
  other repeated field remain separate pairs and are never merged;
- each field name's ASCII spelling (case) exactly as received, as a `String`;
- each field value as `Bytes` containing the original octets, with leading and
  trailing optional whitespace (OWS: SP and HTAB) removed and all valid
  interior octets retained.

Header values are never implicitly converted to `String`.

Field-name comparison performed by the driver is ASCII case-insensitive.
Applications that compare names should use the same rule.

### 5.2 Response control fields

The driver exclusively controls message framing and connection management. A
Response header list containing any of the following field names, compared ASCII
case-insensitively, is `invalidResponse`:

```text
Content-Length
Transfer-Encoding
Connection
Upgrade
Trailer
TE
Keep-Alive
Proxy-Connection
```

The driver emits `Content-Length`, `Transfer-Encoding`, and `Connection` itself
as required by §8 and §9. It does not add `Server`, `Date`, or any other field
automatically. Other response header pairs are emitted in order with their
names and value octets unchanged.

## 6. Request body

`request.body` is a `ByteReadable` whose logical sequence is exactly the
request message content after removal of transfer coding, not the physical
remainder of the duplex.

`read(maxBytes)` follows `BYTE_IO.md` §5 and §5.1 in full: it returns a
`Future`; a successful non-EOF result is a fresh non-empty `Bytes` of at most
`maxBytes` octets; `null` reports the end of the message content. Ordering,
suspension, short reads, cancellation, zero-consumption-on-failure, and
read-ahead preservation are those of `BYTE_IO.md` applied to the logical
content sequence. Awaiting a pending read suspends the service Task (§2.3,
§8); no Task is created per read.

A request with no content returns `null` on its first read.

Chunked framing (chunk-size lines, chunk extensions, CRLF delimiters, the last
chunk, and trailer fields) is consumed and validated beneath `request.body`
and is never visible as content.

A physical EOF of the duplex before the framing-determined end of the content
is not valid content: the read that reaches it fails with a fresh `HttpError`
of kind `malformedRequest` rather than returning `null`. A framing error or a
body-limit violation discovered while reading likewise fails the applicable
read with a fresh `HttpError` (kind `malformedRequest` or `requestLimit`).
Under the ordering rules of `BYTE_IO.md` §5, content octets that precede such
a condition are delivered before it. Which read is the applicable read is
fixed by §6.2 and §6.3.

### 6.1 Body lifetime

`request.body` is valid only during the HTTP session. Once the session is
retired (§9), every later `read` fails with a fresh `IOLifecycleError`
occurrence, including when the body had already reported `null`.

If the handler does not consume the entire request body, the driver does not
drain it, does not interpret any following octets as another request, and does
not restore unread or read-ahead octets to the duplex.

### 6.2 Late framer failures

A `malformedRequest` or `requestLimit` failure detected by the HTTP
parser/framer while reading the body (including premature EOF) is reported by
the logical read of `request.body` that reaches it. That read fails with a
fresh `HttpError` whose `kind` is `malformedRequest` or `requestLimit`
according to its origin and whose `status` is 400 or 413 respectively (§12.1).
The failure terminalizes the HTTP session. The driver retains the original
`HttpError` occurrence and may record it in private session state.

Once the session is terminalized by such a failure:

- the service Future fails with that original `HttpError`, subject only to the
  general cleanup-precedence rules (§12.2);
- if no final response has been committed, the driver attempts a single
  best-effort 400 (`malformedRequest`) or 413 (`requestLimit`) final response
  (§7.6); and
- if the final response has already been committed, no further final response
  is attempted.

Terminalization and these consequences do not depend on the Error escaping
the handler: if the handler handles the failed read and continues, the session
remains terminalized and the outcome of §12.2 for late framer failures
applies.

The driver never reads beyond the end of the request message for the purpose
of interpreting another request.

### 6.3 Reads after terminalization and read-ahead

After the session has been terminalized by a late framer failure (§6.2), every
later invocation of `request.body.read()` — an invocation distinct from the
read that reached the failure — fails with a fresh `IOLifecycleError`
occurrence (`IO_CORE.md`), reported through that read's returned `Future`
under `BYTE_IO.md` §5. Such a read consumes no octet, does not re-expose or
re-signal the original `HttpError`, does not reopen the session, does not
restart the parser/framer, and does not cause a second HTTP response to be
attempted. The service Future still fails with the original `HttpError`,
subject to cleanup precedence.

Handling the `HttpError` does not allow the handler to resume the body, to
recover the session, to have a Response (for example a 200) emitted in place
of the driver's error response, or to prevent the original failure of the
service Future. This document imposes no requirement on whether or how the
handler handles or propagates that Error.

The logical reads requested by the handler are distinct from read-ahead
managed internally by the HTTP layer (bounded by `maxPrefetchBytes`, §10).
Read-ahead does not move the logical point at which a late framer failure
becomes observable: when the driver has internally observed, through
read-ahead, octets that will produce such a failure, logical reads of content
that precedes the failure position behave as they otherwise would, and the
failure is reported by the logical read that reaches it (§6.2). An earlier
logical read is never presented as having failed because of that internal
observation. This does not affect rejection before the handler is invoked for
limits that are checkable from the header section (§7.4, §10). No rewind,
recovery of read-ahead, or guarantee about physical or operating-system
buffering is implied.

Consequently, when read-ahead has observed octets containing a framing error
that no logical read requested by the handler reaches, that error is not
reported to the handler, does not terminalize the session early, and does not
replace the handler's valid result: a valid Response (for example a 200) may be
emitted. After the handler returns, the driver is not required to consume or
validate the rest of the request body, and no HTTP error response is attempted
because of octets that were never logically reached. This does not affect
limits checkable from the header section, rejections before the handler,
request-line or header-section validation, or failures reached by logical reads
that do occur, to which §6.2 and the rules above apply in full.

## 7. HTTP/1.1 wire contract

The driver is a strict HTTP/1.1 origin server.

### 7.1 Request line and target

- Lines are terminated by exactly CRLF. A bare CR or bare LF is
  `malformedRequest`.
- The request-line version must be exactly `HTTP/1.1`. `HTTP/1.0` and every
  other version are rejected as an unsupported HTTP version (kind
  `unsupportedFeature`, status 505; best-effort 505).
- The HTTP/2 connection preface `PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n` is
  classified as an unsupported HTTP version (kind `unsupportedFeature`, status
  505). This classification takes precedence over its method and target: it is
  not classified as an unsupported HTTP/1.1 method (501) or as a malformed
  target.
- Accepted request-target forms are origin-form, absolute-form, and asterisk-form
  only for `OPTIONS *`.
- `CONNECT` with authority-form is recognized but not supported (best-effort
  501, kind `unsupportedFeature`). Any other authority-form or malformed target
  is `malformedRequest`.

### 7.2 Header section

- Exactly one `Host` field with a valid value is required, for every target
  form. A missing, repeated, or invalid `Host` is `malformedRequest`.
- Obsolete line folding (obs-fold) is rejected.
- Whitespace between a field name and `:` is rejected.
- Upgrade, WebSocket, tunnelling, and HTTP/2 are not supported. An otherwise
  valid HTTP/1.1 request that requests an unsupported feature through
  `Upgrade` (including `Upgrade: h2c` and WebSocket) is `unsupportedFeature`
  with status 501 (best-effort 501). No protocol negotiation or switch is
  performed. The HTTP/2 connection preface is governed by §7.1 (505).

### 7.3 Request framing

- A request containing both `Transfer-Encoding` and `Content-Length` is
  rejected.
- `Content-Length` appearing more than once, or as a comma-separated list, is
  rejected even when every value is equal.
- A `Content-Length` that is not a sequence of decimal digits, or whose value
  is not representable without overflow, is rejected.
- `Transfer-Encoding` is supported only as exactly `chunked`; any other transfer
  coding is `unsupportedFeature` (best-effort 501).
- Chunked framing is validated exactly: chunk-size is hexadecimal without
  overflow, every chunk is followed by CRLF, the last chunk is `0`, and the
  trailer section terminates with CRLF.
- Without `Content-Length` and `Transfer-Encoding`, the request content is
  empty. Content length is never inferred from duplex EOF.
- Trailer fields are validated under the trailer limits (§10) and then
  discarded. They are never merged into `request.headers` and are not exposed.

### 7.4 Expect

When the request carries `Expect: 100-continue` and has non-empty content
under §7.3, the expectation is admitted once initial validation and the limits
known from the header section have passed. For an admitted expectation the
driver proceeds in this order:

1. parse and validate the header section;
2. perform the early limit checks;
3. emit the interim response `HTTP/1.1 100 Continue`;
4. invoke the handler;
5. during that handler invocation, read request content only as, and when,
   the handler's reads of `request.body` require.

Steps 4 and 5 are not sequential phases: request content is read only while
the handler is running, in response to its reads of `request.body`. There is
no mandatory body-reading phase after the handler returns.

The driver never waits for the handler to invoke `read()`, nor for
request-content octets, before emitting `100 Continue`.

All of these steps are performed by the service Task (§2.3); no additional
Task is created for them, and no timer is involved.

A rejection already determined before that point (for example a
`Content-Length` exceeding `maxRequestBodyBytes`) emits its final response
directly, without `100 Continue`.

Any other `Expect` value is unsupported: best-effort 417.

No preflight callback is invoked before `100 Continue`.

### 7.5 Final response

Framing is selected only after the Response, including the status/body rule
of §4, has been validated. Rules are applied in this precedence: status 204,
205, and 304 first; then `HEAD`; then the general rules.

- Every final response includes `Connection: close`. The driver never processes
  a second request on the same session.
- `Content-Length` and `Transfer-Encoding` are never both emitted.
- Status 204, 205, and 304 (overriding the `HEAD` and general rules):

  | Status | `Content-Length` | `Transfer-Encoding` | Content |
  | --- | --- | --- | --- |
  | 204 | omitted | omitted | none |
  | 205 | `0`, always emitted | omitted | none |
  | 304 | omitted | omitted | none |

- `HEAD` request (no content octets are emitted in any case):
  - `body` `Bytes`: `Content-Length` is emitted with exactly the captured size
    of the `Bytes` that would have been sent;
  - `body` `ByteReadable`: the driver does not invoke `read()`, does not
    consume the stream, does not compute its length by reading or speculative
    buffering, and omits `Content-Length`;
  - `body` `null`: `Content-Length: 0` is emitted.
- General rules:
  - `body` `null` emits zero content octets with `Content-Length: 0`;
  - `body` `Bytes` emits exactly the captured octets with a `Content-Length`
    equal to the captured size;
  - `body` `ByteReadable` emits `Transfer-Encoding: chunked` (§8).

### 7.6 Request-error responses

When a request is rejected before the handler is invoked, the driver attempts
a best-effort final response with an empty body and `Connection: close`, using
the status associated with its cause. The status and `HttpError.kind` follow
the classification of §12.1:

| Status | `kind` | Condition |
| --- | --- | --- |
| 400 | `malformedRequest` | malformed request line, target, header section, framing, or `Host` |
| 413 | `requestLimit` | declared content exceeds `maxRequestBodyBytes` |
| 414 | `requestLimit` | request line exceeds `maxRequestLineBytes` |
| 417 | `unsupportedFeature` | unsupported `Expect` |
| 431 | `requestLimit` | header section exceeds `maxHeaderBytes` or `maxHeaderFields` |
| 501 | `unsupportedFeature` | unsupported method form or protocol feature (including `CONNECT` and `Upgrade`, §7.2) or transfer coding |
| 505 | `unsupportedFeature` | unsupported HTTP version (including the HTTP/2 connection preface, §7.1) |

When a late framer failure (§6.2) terminalizes the session before the final
response is committed, the driver attempts a single best-effort final error
response with an empty body and `Connection: close`: 400 for
`malformedRequest`, 413 for `requestLimit`. Such a failure is never turned into
a generic 500. After commitment no further final response is attempted.

A handler failure not originating from the parser/framer, and an
`invalidResponse`, are governed by §12.2: before commitment the driver may
attempt a best-effort empty 500 response; it is not required to. Invalid
`serveOne` arguments detected after ordinary dispatch (§2.4) are not a
rejected HTTP request and never produce an HTTP response.

"Best-effort" means the driver attempts the write once and the outcome of the
service is then determined as follows (§12.2):

- For a request rejected during its initial processing (the statuses of the
  table above), if the best-effort write succeeds the service Future fails with
  the rejection `HttpError`; if the write fails, the I/O Error produced by that
  write prevails over the rejection `HttpError`.
- For a handler failure or an `invalidResponse`, if the driver does not attempt
  the optional empty 500 response, or the attempt succeeds, the service Future
  fails with the original error; if the attempted write fails, the I/O Error
  produced by that write prevails over the original error.
- For a late framer failure (§6.2), and only for it, the service Future fails
  with the original `HttpError` even when the best-effort 400 or 413 write
  fails; the write failure does not replace that `HttpError`.

Once the write of a final response has been initiated, no other final response
is attempted, even if that write fails; the driver does not assume that a
failed write emitted zero octets. This guard against retries does not redefine
commitment (§7.7, `BYTE_IO.md` §6.2). In every case the outcome remains subject
to the general cleanup precedence of `ensure`, under which a cleanup Error,
including one raised by the owning `close()` (§9.2), may replace the preceding
outcome.

### 7.7 Commitment of the final response

The final response is committed when the first octet of its status line
contributes to the duplex output under `BYTE_IO.md` §6.2. After commitment the
driver never attempts a second final response, including a 500.

Completing writes and closing the duplex do not imply delivery to, or receipt
by, the remote peer (`BYTE_IO.md` §6, `NETWORK.md` §9).

## 8. Response body emission

A `ByteReadable` response body is streamed with chunked transfer coding. The
driver repeatedly invokes `read(ioChunkBytes)` on it, waits for each result,
writes each non-empty result as one or more chunks, and waits for each write
to complete before issuing the next read. On `null` it writes the last chunk
and the empty trailer section. Backpressure from the duplex therefore
propagates to the response body; no unbounded queue is created, and the whole
body is never materialized.

The suspensions required to read the request body, write the response,
stream response content, and await I/O `Future`s occur in the service Task
(§2.3), which suspends and resumes under the general rules of
`../concurrency/FUTURES_AND_TASKS.md`. No Task is created per chunk, per
read, or per write, and `ByteReadable` / `ByteWritable` are used unchanged.

The driver does not compress, re-encode, retry, or convert response content.

If a response-body read fails after commitment, the driver does not write the
last chunk, so the response is not presented as complete; the service fails
under §12.

## 9. Ownership and session retirement

### 9.1 Borrowed duplex

`Server.serveOne` borrows `duplex`. It never invokes `close`, `shutdownRead`,
or `shutdownWrite` on it. Because every response carries `Connection: close`,
the caller remains responsible for closing or shutting down the duplex after
the service Future completes.

Octets the driver has read from the duplex, including read-ahead bounded by
`maxPrefetchBytes`, are consumed and are not returned. The number of octets
consumed beyond the end of the request message is not portable. `serveOne`
does not guarantee that read-ahead can be recovered, does not guarantee any
rewind, and does not guarantee that a second message can be safely processed
on the same duplex.

### 9.2 Owning duplex

`Server.serveOne.owning` acquires custody of `duplex` for the session and
closes it on every terminal path — normal completion, request rejection,
handler failure, invalid response, I/O failure, and cancellation — under the
existing `Closable` (`IO_CORE.md` §8) and cleanup-precedence rules
(`../semantics/ERRORS.md`, "Error precedence during `ensure` cleanup"). The
service Future does not complete before that close completes. A close failure
supersedes an earlier failure under those rules.

`serveOne.owning` acquires that close responsibility only after its arguments
have been admitted (§2.3, §2.4) and an operative HTTP session has been created. When
the validation after ordinary dispatch fails with `InvalidIOArgument`, no
operative session is created, close responsibility is not acquired, and the
duplex is not closed as an effect of that failure. Once acquired, the closing
rules above apply to every terminal path, including cancellation of the service
Task before it executes its first ordinary instruction (§9.5). The close is
performed by the service Task's own cleanup; no additional Task is created to
perform it, and no second owner of `duplex` exists.

### 9.3 Response body stream

A `ByteReadable` response body is borrowed. The driver never closes it,
including on failure, cancellation, or `HEAD`.

### 9.4 Session retirement

The session is retired when the final response emission has completed or
terminally failed, before the service Future completes. The session is likewise
retired, before the service Future completes, when the service reaches a
terminal path before emitting a final response, including cancellation before
the handler is invoked, rejection of the request during initial processing, a
parsing failure, an I/O failure, a handler failure, an `invalidResponse`, and
cancellation while an I/O operation is awaited.

Retirement ends the validity of `request.body` (§6.1). It creates no authority
to close a borrowed duplex, performs no rewind, guarantees no recovery of
read-ahead, and does not alter cleanup precedence. For `serveOne.owning`, the
close obligation of §9.2 is discharged before the service Future becomes
terminal.

No operation is provided to detach or hijack the raw duplex, return read-ahead
octets, reuse the connection, transfer the session to another Actor, or restore
consumed duplex octets.

### 9.5 Structured concurrency

Tasks started by the handler are started within the service Task (§2.3) and
follow the existing structured-concurrency completion and cancellation rules.
Termination of the service Task, cancellation of the service, and closure of
the invoker's structured scope follow the existing Future/Task rules and the
cleanup precedence above; committed I/O effects are not rolled back. The
service Task is never a second owner of the duplex: its cancellation or
termination gives `serveOne` no authority to close or shut down a borrowed
duplex (§9.1), and `serveOne.owning` closes only under §9.2.

When the service Task fails or is cancelled, its pending structured children,
including Tasks started by the handler, are handled by the abnormal
owner-termination rules of `../concurrency/FUTURES_AND_TASKS.md` §24; this
document adds no HTTP-specific rule for them.

If the service Task is cancelled before it executes its first ordinary
instruction, the handler is not invoked and the session is retired (§9.4).
`serveOne.owning` performs its mandatory close (§9.2), and the service Future
does not become terminal before that close and the required cleanup have
completed; a close failure follows the general cleanup precedence. For the
borrowed `serveOne`, the duplex is never closed, cancellation creates no
authority to close it, and no rewind or recovery of read-ahead is guaranteed.

## 10. Limits

`limits` is `null` or an ordinary object whose own slots are a subset of:

| Slot | Default | Bounds |
| --- | --- | --- |
| `maxRequestLineBytes` | 8192 | request line including CRLF |
| `maxHeaderBytes` | 32768 | header section including field lines and terminating CRLF |
| `maxHeaderFields` | 100 | number of request header field lines |
| `maxChunkLineBytes` | 8192 | one chunk-size line including extensions and CRLF |
| `maxTrailerBytes` | 8192 | trailer section |
| `maxTrailerFields` | 100 | number of trailer field lines |
| `maxRequestBodyBytes` | 33554432 | decoded request content octets |
| `maxPrefetchBytes` | 65536 | octets read from the duplex but not yet consumed by framing or content |
| `ioChunkBytes` | 16384 | `maxBytes` for driver duplex and response-body reads |

`null` and each absent slot select the default. Inherited slots are ignored.

The service is rejected under §2.4 (failed Future with a fresh
`InvalidIOArgument`, before any I/O on the duplex) when `limits` is neither
`null` nor an object, or has an own slot not listed above, or a listed slot
whose value is not an `Integer`, is zero or negative, or is not representable
for the driver's arithmetic.

`maxPrefetchBytes` bounds only read-ahead managed by the HTTP layer. It is not
a guarantee about physical socket buffering, operating-system buffering,
internal buffering of lower layers, or the exact number of octets physically
read or consumed by lower abstractions.

Limits are captured when the service is admitted; later mutation of `limits`
has no effect on that service. All driver counters are maintained without
overflow. The framing, body, prefetch, and I/O-chunk limits are independent of
each other and of buffers belonging to other facilities.

Not every limit is a ground for rejecting an HTTP request. The request
admission and framing limits (`maxRequestLineBytes`, `maxHeaderBytes`,
`maxHeaderFields`, `maxChunkLineBytes`, `maxTrailerBytes`,
`maxTrailerFields`, and `maxRequestBodyBytes`) bound the request message.
`maxPrefetchBytes` bounds HTTP-managed read-ahead, and `ioChunkBytes`
configures the working size of the driver's I/O operations; neither, by
itself, produces a `requestLimit` failure or a 413, 414, or 431 rejection.

Exceeding a request admission or framing limit before the handler is invoked
is a `requestLimit` failure with the status of §7.6. Exceeding `maxRequestBodyBytes`,
`maxChunkLineBytes`, `maxTrailerBytes`, or `maxTrailerFields` while the
handler reads the body fails that read with a fresh `HttpError` of kind
`requestLimit` and terminalizes the session (§6.2).

## 11. Security posture

This first server is suitable for development, loopback, trusted networks, and
deployment behind a genuinely protective ingress.

It is **not** declared safe for direct exposure to hostile Internet traffic.
Protos does not yet define a portable clock, deadline, timeout, or Future-race
contract sufficient to bound slow-client (slowloris) attacks or indefinite
resource retention. Conforming implementations must not hide this limitation
with implicit host timers or ambient host authority.

## 12. Errors

### 12.1 `HttpError`

`std:http/Server.HttpError` is an ordinary prototype whose immediate
delegation parent is `Error`. It is a module member, not a Prelude binding and
not a Core error mechanism (`../semantics/ERRORS.md`, "Core Error Taxonomy").

Every HTTP failure creates a fresh `HttpError` occurrence with:

- `kind`: a `String` whose only normative values are `"malformedRequest"`,
  `"requestLimit"`, `"invalidResponse"`, and `"unsupportedFeature"`; no enum,
  nominal type, sealed family, or additional error hierarchy is defined;
- `status`: when present, an `Integer` giving the associated HTTP status;
  present only when one applies.

`status` is the HTTP status associated with the error, not necessarily a
response actually transmitted. Every request error carries `status` according
to this classification, including when its best-effort final response is only
attempted or is never transmitted:

| `status` | `kind` |
| --- | --- |
| 400 | `"malformedRequest"` |
| 413 | `"requestLimit"` |
| 414 | `"requestLimit"` |
| 417 | `"unsupportedFeature"` |
| 431 | `"requestLimit"` |
| 501 | `"unsupportedFeature"` |
| 505 | `"unsupportedFeature"` |

The conditions mapped to each status are those of §7.6, §6.2, and §10; request
errors are not collapsed into `malformedRequest`.

`HttpError` of kind `"invalidResponse"` reports that the Response produced by
the handler failed validation (§2.2, §4, §5.2). Its `status` slot is absent. A
subsequent best-effort 500 attempt (§12.2) does not turn it into an
`HttpError` with `status` 500; the original `invalidResponse` occurrence keeps
its identity and classification.

`HttpError` is not a Core-mandated category, a Prelude binding, a subtype of
`IOError`, or a host class. Error construction, identity, delegation, and
signaling follow `../semantics/ERRORS.md` unchanged.

I/O errors, `Cancelled`, `IOLifecycleError`, `InvalidIOArgument` (§2.4), and
errors signaled by the handler keep their existing categories; they are never
wrapped in `HttpError`.

### 12.2 Service outcomes

- An HTTP request rejected during its initial processing, before the handler
  is invoked (request line, header section, framing, `Expect`, or early limit
  checks): best-effort response (§7.6); if that write does not fail, the service Future
  fails with the fresh `HttpError`; if that write fails, the service Future
  fails with the corresponding I/O Error. Both outcomes are subject to cleanup
  precedence.
- Invalid `serveOne` arguments detected after dispatch (§2.4) are not a
  rejected HTTP request: the service Future fails with a fresh
  `InvalidIOArgument`; no service Task is created, no session exists, and no
  HTTP response is attempted.
- A late framer failure (§6.2), whether or not the `HttpError` escapes the
  handler: before commitment the driver attempts a single best-effort 400
  (`malformedRequest`) or 413 (`requestLimit`) and never a 500; after
  commitment no further final response is attempted and the session is not
  restarted. The service Future fails with the original `HttpError`, subject
  to cleanup precedence; the original `HttpError` prevails even if the
  best-effort 400 or 413 write fails.
- A handler failure not originating from the parser/framer, before
  commitment: the driver may attempt a best-effort empty 500 response; it is not required to. If no write is attempted, or the
  attempted write succeeds, the service Future fails with the handler's
  original error; if the attempted write fails, the I/O Error of that write
  prevails over the handler's error. Both outcomes are subject to cleanup
  precedence. Provenance is classified from the driver's private session
  state, not merely from whether an Error escaped or was handled.
- An `invalidResponse` before commitment: the driver may attempt a best-effort
  empty 500 response; it is not required to. If no write is attempted, or the
  attempted write succeeds, the service Future fails with a fresh `HttpError`
  of kind `invalidResponse` without `status` (§12.1); if the attempted write
  fails, the I/O Error of that write prevails over `invalidResponse`. Both
  outcomes are subject to cleanup precedence.
- Any failure after commitment: no further response is attempted; the service
  Future fails with that failure.
- A duplex write or read failure: the service Future fails with that I/O error,
  subject to cleanup precedence, except that a failed best-effort 400 or 413
  write following a late framer failure (§6.2) does not replace the original
  `HttpError`, as stated above. This exception covers only that write.
- Cancellation of the service, including before the handler is invoked: the
  session is retired (§9.4) and the service Future follows the general
  cancellation-unwind rules (§9.5), becoming `cancelled` only after the
  required cleanup, including the owning close (§9.2), has completed, or
  `failed` with a cleanup Error under cleanup precedence.

## 13. Conformance examples

The following illustrate the rules above; CRLF is shown as `\r\n`. Sequences
are descriptive, not Protos syntax.

**Invalid argument.** `serveOne` with a `duplex` that is not `ByteWritable`:
the returned Future is already failed with a fresh `InvalidIOArgument`; no
service Task, no session, no ownership acquired, and no HTTP I/O.
*Counterexample:* creating a Task that then fails, or an `HttpError`.

**Owning service cancelled before start.** A valid `serveOne.owning`
invocation is admitted: the session is established and close responsibility
acquired, then the Task is created and its Future returned. That Future is
cancelled before the Task executes its first ordinary instruction: the handler
is not invoked, the session is retired, the duplex is closed, and the Future
does not become terminal before the close completes. *Counterexample:*
invoking the handler, leaving the duplex open, or a cancelled Future before
the close.

**Unreached malformed read-ahead.** The driver's read-ahead has observed a
malformed chunk later in the body, but the handler never reads that far and
returns a valid 200 Response: the 200 may be emitted and no `HttpError` is
created solely because of the read-ahead. *Counterexample:* failing an earlier
read, emitting 400, or failing the service Future.

**Admitted service.** A valid `serveOne` invocation creates exactly one
cooperative service Task in the invoker's Actor, as a structured child of the
invoker's scope; the returned `Future<null>` represents that Task's result.
*Counterexample:* a detached Task, a Task in another Actor, or running the
service in the invoking Task.

**Suspending handler.** The handler, invoked as `handler(request)`, awaits
`request.body.read(1024)` while content has not yet arrived: the service Task
suspends; when octets arrive, the same Task resumes with the read's result.
*Counterexample:* creating a Task per read, or a synchronous host callback
that blocks.

**Scope cancellation.** The invoker's structured scope is cancelled while the
handler is suspended: the service Task is cancelled under the general
Task/cancellation rules; `serveOne` does not close the borrowed duplex.
*Counterexample:* treating cancellation as authority to close the borrowed
duplex.

**Normal completion.** After the final response has been emitted and the
session retired (§9.4), the service Task terminates normally and the service
Future resolves to `null`. *Counterexample:* resolving before session
retirement, or with a value other than `null`.

**Duplicate headers.** `Set-Cookie: a=1\r\nset-cookie: b=2\r\n` yields two
pairs `["Set-Cookie", <a=1>]` and `["set-cookie", <b=2>]` in that order.
*Counterexample:* one merged pair, lower-cased names, or `String` values.

**TE + CL.** A request with both `Transfer-Encoding: chunked` and
`Content-Length: 5` is rejected with best-effort 400; the handler is not
invoked. *Counterexample:* honouring either field.

**Repeated Content-Length.** `Content-Length: 5\r\nContent-Length: 5\r\n` or
`Content-Length: 5, 5` is rejected. *Counterexample:* accepting because the
values match.

**Expect 100.** `Expect: 100-continue` with `Content-Length: 10` within limits:
after header validation and early limit checks, `100 Continue` is written,
then the handler is invoked; neither waits for the handler's first `read()` or
for content octets. With `Content-Length` above `maxRequestBodyBytes`: 413 is
written directly. *Counterexample:* writing 100 then 413; deferring 100 until
the handler reads.

**Logical EOF.** With `Content-Length: 3` and duplex octets `abcGET /…`, body
reads deliver `abc` then `null`; `GET /…` is never interpreted.
*Counterexample:* delivering `abcGET` or serving a second request.

**Premature EOF.** With `Content-Length: 10` and the duplex reaching EOF after
4 content octets, those 4 octets are delivered and the next read fails with
`HttpError` `malformedRequest`. *Counterexample:* returning `null`.

**Malformed chunk during read.** A chunk-size line `zz\r\n` discovered by a
body `read()`: that read fails with a fresh `HttpError` `malformedRequest`,
the session is terminalized, and, if nothing is committed, a best-effort 400
is attempted; the service Future fails with that `HttpError`.
*Counterexample:* a 500.

**Body limit during read.** Chunked content exceeding `maxRequestBodyBytes`
during `read()`: `HttpError` `requestLimit`, session terminalized, best-effort
413 if uncommitted. *Counterexample:* delivering octets past the limit.

**Handled framer error.** A handler that handles the failed read of either
case above and then completes with a 200 Response: the session stays
terminalized, the 200 is not emitted, the best-effort 400/413 is attempted, and
the service Future fails with the original `HttpError`. *Counterexample:*
emitting the 200, or replacing 400/413 with 500.

**Read after a framer error** (descriptive sequence, not Protos syntax):

1. the handler reads `request.body`;
2. that read reaches a malformed chunk;
3. the read fails with a fresh `HttpError` (`kind` `"malformedRequest"`,
   `status` 400);
4. the handler handles that Error;
5. the handler invokes `request.body.read()` again;
6. the new read fails with a fresh `IOLifecycleError`;
7. no further octet is consumed;
8. the session remains terminalized;
9. if no final response has been committed, the driver attempts a single
   best-effort 400; and
10. the service Future fails with the original `HttpError`.

The same sequence with chunked content exceeding `maxRequestBodyBytes` yields
`kind` `"requestLimit"`, `status` 413, and a single best-effort 413.
*Counterexample:* the second read re-signaling the original `HttpError`,
returning data or `null`, restarting the parser, or attempting a second
response.

**Escaped body.** A handler that stores `request.body` and reads it after the
service Future completes gets a fresh `IOLifecycleError`, even if the body had
reported `null`. *Counterexample:* `null` or data.

**Borrowed vs owning.** After `serveOne` completes, the duplex is still open
and the caller must close it; octets read ahead beyond the request are not
restored, and serving a second message on it is not guaranteed safe. After
`serveOne.owning` completes or fails, the duplex has been closed.
*Counterexample:* `serveOne` shutting down the write side, or rewinding
read-ahead.

**Handler failure before commitment.** A handler signaling its own Error
before returning a Response: the driver may attempt a best-effort empty 500;
the service Future fails with that Error. **After commitment**, a failure yields no second final
response. *Counterexample:* a second status line.

**204/205/304.** A 204 with a 3-octet `Bytes` body, or a 205 with a
`ByteReadable` body: `invalidResponse` before commitment, and the
`ByteReadable` is never read. A valid 205 emits `Content-Length: 0`; valid 204
and 304 emit neither `Content-Length` nor `Transfer-Encoding`; none emits
content, including for `HEAD`. *Counterexample:* silently dropping the body;
reading the stream to test emptiness.

**HEAD.** `HEAD` with a 200 `Bytes` body of 5 octets: `Content-Length: 5`,
no content. `HEAD` with a `ByteReadable` body: no content, no `read()`, no
`Content-Length`. *Counterexample:* omitting the length for `Bytes`; reading
the stream to measure it.

**Chunked output.** A `ByteReadable` body yielding `hello` then `null` emits
`Transfer-Encoding: chunked`, a chunk carrying `hello`, and `0\r\n\r\n`; the
stream is not closed. *Counterexample:* buffering the whole body to compute
`Content-Length`.

**Post-commit error.** A response-body read failing after the status line was
written: no last chunk, no 500; the service Future fails with that error.
*Counterexample:* appending a second status line.

**Security limits.** A header section of 101 field lines under default limits
is rejected with best-effort 431 before the handler. A `limits` object whose
`maxHeaderFields` slot is `0`, or that has an own slot named `maxHeadrFields`,
makes the entry point return a failed Future with a fresh `InvalidIOArgument`
before any I/O; no `HttpError` is created, no HTTP response is attempted, and
`serveOne.owning` does not close the duplex.
*Counterexample:* silently ignoring the unknown slot.

**Request-error classification.** Before the handler is invoked:

| Request | `HttpError` |
| --- | --- |
| request line longer than `maxRequestLineBytes` | `kind` `"requestLimit"`, `status` 414 |
| header section above `maxHeaderBytes` or `maxHeaderFields` | `kind` `"requestLimit"`, `status` 431 |
| `Expect: something-else` | `kind` `"unsupportedFeature"`, `status` 417 |
| `CONNECT example.org:443 HTTP/1.1` | `kind` `"unsupportedFeature"`, `status` 501 |
| `GET / HTTP/1.0` | `kind` `"unsupportedFeature"`, `status` 505 |
| HTTP/2 connection preface `PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n` | `kind` `"unsupportedFeature"`, `status` 505 |
| valid HTTP/1.1 request with `Upgrade: h2c` | `kind` `"unsupportedFeature"`, `status` 501 |

*Counterexample:* classifying any of these as `malformedRequest`; classifying
the preface as 501; omitting `status` because the best-effort response was not
transmitted.

**Invalid response.** A handler completing with a Response whose header list
contains `Content-Length`: a fresh `HttpError` of kind `"invalidResponse"`
with no `status` slot; the driver may attempt a best-effort empty 500.
*Counterexample:* an `HttpError` with `status` 500.

## 14. Approved contract versus future work

Sections 1–13 define the single-request, `Connection: close`, HTTP/1.1
origin-server facility; §15 defines the separately optional continuous-hosting
facility. The following remain outside the approved HTTP contract and require
separate explicit design:

- HTTP client;
- TLS;
- DNS and name resolution;
- persistent connections / keep-alive and pipelining;
- HTTP/2 and HTTP/3;
- Upgrade, WebSocket, and CONNECT tunnelling;
- exposed request or response trailers;
- routing;
- authentication;
- general-purpose listener supervision outside §15; and
- timeouts, deadlines, and clock-based resource bounds.

## 15. Continuous HTTP/1.1 hosting (D216)

D216 selects a distinct, opt-in Standard Library facility above the unchanged
single-request `std:http/Server`. This section is the sole normative owner of
its HTTP-hosting-specific public contract. Its network, Actor, Future, Task and
resource obligations compose with `NETWORK.md`, `IO_CORE.md`, `BYTE_IO.md`,
`../concurrency/ACTORS.md` and
`../concurrency/FUTURES_AND_TASKS.md`; it introduces no alternative mechanism
for any of those domains.

### 15.1 Module and complete portable public surface (D216-1)

The explicit module `std:http/Host` exports `Host` and `HostError`. There is no
Prelude binding, ambient HTTP server, or implicit import. Its portable public
operations are:

```text
Host.owning(listener, handlerModule, handlerBinding, options) -> host
host.ready()                                            -> Future<host>
host.shutdown("drain")                                  -> Future<host>
host.shutdown("abort")                                  -> Future<host>
host.termination()                                      -> Future<host>
```

`Host.owning` is a synchronous construction operation. The returned `host` is
an ordinary Actor-local hosting control object with identity and the methods
above. The Future-returning methods are local asynchronous observations, not
cross-Actor-transferable Futures. There is no `Server.serve` operation and no
borrowed-listener hosting variant. Calling any operation with an invalid
receiver follows the usual receiver/dispatch rules; it does not manufacture
hosting authority.

An HTTP host services successive independent TCP connections, not successive
HTTP requests on one connection. Each admitted connection is serviced exactly
once by the existing `Server.serveOne.owning` operation; its HTTP parsing,
framing, limits, body, response, cancellation, and error precedence remain
exactly those of §§2–12. Every session sends `Connection: close`. No host
operation alters the public surface or semantics of `Server.serveOne` or
`Server.serveOne.owning`.

### 15.2 Construction, options and start (D216-2)

`listener` must be an existing live `TcpListener` held in the constructing
Actor. It is supplied explicitly by the caller, ordinarily after acquisition
using its own `Network` authority; `Host` itself never acquires `Network`,
calls `listenTcp`, chooses an address or port, or inherits a capability.
`handlerModule` and `handlerBinding` are semantic Strings identifying the
module and its local callable top-level binding for each destination Actor;
the worker does not receive a Closure or module instance from the caller.
Module identity and binding lookup obey the `Actor.spawn` bootstrap and
module-resolution rules. An invalid module or bootstrap may fail
asynchronously after construction.

`options` is `null` or an ordinary object. Only these own slots are admitted:

| Own slot | Default | Contract |
| --- | --- | --- |
| `workers` | `1` | positive Integer in signed 32-bit range |
| `maxConnections` | `64` | positive Integer in signed 32-bit range |
| `maxPendingAccepts` | `workers` | positive Integer in signed 32-bit range |
| `limits` | `null` | `Server.serveOne` limits object under §10 |

Inherited option slots do not supply values; absent slots select defaults;
unknown own slots are rejected. The required inequalities are
`1 <= workers <= maxPendingAccepts <= maxConnections`. No coercion, clamping,
auto-detected CPU count, hidden unbounded queue, or alternative backlog option
is defined. Invalid construction arguments signal a fresh
`InvalidIOArgument` synchronously, without acquiring listener custody,
creating Workers or initiating I/O. Valid construction acquires custody of
the supplied listener and begins asynchronous startup. A later startup
failure is reported by hosting Futures, not by retroactively failing
`Host.owning`.

### 15.3 Listener authority and ownership (D216-3)

The host's constructing Actor remains the sole Protos owner of the listener
and its `close` authority. Once valid construction transfers logical custody
to `host`, the caller must not concurrently accept from, close, or otherwise
operate that listener independently. The host closes it exactly once as a
logical release obligation on all terminal paths, including failed startup.
Listener closure follows `IO_CORE.md` §8; a successful host terminal outcome
cannot precede completion of required resource-release work.

Each Worker receives only an attenuated `TcpAcceptor` made with
`listener.acceptCapability()` and provisioned as an initialization argument
of `Actor.spawn`. It may call `accept` but cannot close the listener, recover
`Network` authority, or transfer a `TcpConnection`. `TcpAcceptor` itself is not
a second listener; all Workers compete in the one logical acceptance domain
owned by `NETWORK.md` §8. No central Actor accepts connections on their behalf.

### 15.4 Worker organization and HTTP session ownership (D216-4)

The host starts exactly `workers` Worker Actor incarnations, all constrained
to the same Process by the supplied `TcpAcceptor`. Their bootstrap code lives
in the Standard Library; each Worker resolves `handlerModule` and
`handlerBinding` within its own Actor-local module instance. A missing
module, missing local binding or non-callable binding is a startup failure.
Neither the handler Closure, a mutable module instance, the listener, nor
an accepted `TcpConnection` crosses an Actor boundary.

Workers independently issue `acceptor.accept()` and run one owning HTTP
service Task per accepted connection. Multiple connections within one Worker
interleave cooperatively; distinct Workers may execute simultaneously on
different CPU cores. One Worker owns each accepted connection from successful
delivery through `Server.serveOne.owning` terminal cleanup. Control messages
and lifecycle observations may coordinate shutdown and outcome reporting,
but may not relay acceptance or transfer live connections.

The hosting module uses ordinary Protos Actors, messages, Futures and Tasks,
including `ActorRef.stop()` and `ActorRef.termination()` where appropriate.
It does not install a new scheduler, privileged Actor kind, global worker
registry, or Java HTTP engine.

### 15.5 Admission and deterministic bounds (D216-5)

The three option limits are hard global maxima for one host:

- `workers` bounds Worker Actor incarnations created by this host;
- `maxConnections` bounds accepted connections whose owning service has
  not fully retired and released the connection; and
- `maxPendingAccepts` bounds outstanding acceptance operations.

Reservation must prevent the sum of live accepted connections and pending
accepts from exceeding `maxConnections` at any instant. There is no
oversubscription window and no unbounded hidden guest queue.

The host assigns disjoint, fixed quotas to Workers before starting them.
For a limit `L` and `W` Workers, Worker indexes `0..W-1` receive
`floor(L / W)`, with one extra quota unit for each index below
`L mod W`; this applies independently to `maxConnections` and
`maxPendingAccepts`. No connection or pending-accept quota is lent between
Workers. A Worker may have no more than its pending-accept quota outstanding,
and issues a new accept only while its accepted connections plus its
outstanding accepts are below its assigned connection quota.

At capacity a Worker stops issuing new `accept` calls and resumes when a
connection has been fully retired and its close obligation settled. It does
not accept an extra connection merely to emit HTTP 503; it does not drop a
previously delivered connection because another Worker is full. Selection,
fairness and operating-system TCP listen-backlog behavior remain subject to
`NETWORK.md`; fixed quotas provide bounds, not load-balancing or starvation
freedom. No portable wall-clock timeout or slow-client eviction is implied.

### 15.6 Accepted-connection custody and close races (D216-6)

An accepted connection is delivered only to the Worker that invoked
`acceptor.accept()`. That Worker establishes `Server.serveOne.owning` custody
and runs the session inside its own Actor. A connection delivered during a
concurrent shutdown cutover, but no longer admitted to HTTP service, must be
closed by that same Worker without invoking the handler. No delivered
connection may be abandoned, transferred, serviced twice, or counted twice.

Pending `accept` operations, cancellation races, listener close, delivered
connections and late backend resources obey `NETWORK.md` and `IO_CORE.md`:
resources not deliverable to an Actor remain under producer/backend cleanup.
Each owning session's own close and cleanup precedence is §§9 and 12;
hosting does not mask a close failure as an ordinary client disconnection.

### 15.7 Host state and readiness (D216-7)

The hosting lifecycle has these conceptual states:

```text
STARTING -> RUNNING -> DRAINING -> TERMINATED
                   \-> ABORTING -> TERMINATED
STARTING -----------> ABORTING -> TERMINATED
DRAINING -----------> ABORTING -> TERMINATED
```

`STARTING` begins after valid construction. `RUNNING` begins only after all
configured Workers have successfully initialized and entered their hosting
acceptance service. `ready()` resolves with the identical `host` on that
cutover. A startup failure instead fails `ready()` with `HostError`, closes
the listener and causes abort cleanup. A shutdown invoked during startup
prevents later entry to `RUNNING` and does not resolve readiness
successfully.

The first shutdown cutover prevents new HTTP session admissions.
`DRAINING` waits for already admitted sessions; `ABORTING` requests their
cooperative cancellation. `TERMINATED` is reached only after all Worker
Actor incarnations are known terminated, accepted connections have
completed mandatory custody cleanup, and the listener close has settled.
No state transition may reopen acceptance or reuse a terminated Worker.
The public API does not expose a mutable lifecycle-state slot.

### 15.8 Drain, abort and escalation (D216-8)

`host.shutdown("drain")` and `host.shutdown("abort")` are the only public
shutdown requests. Any other argument signals a fresh
`InvalidIOArgument` and does not change lifecycle state.

On the first accepted shutdown request the host stops admission and commits
listener closure, preventing further successful accepts after the listener
close cutover. Drain lets already admitted `serveOne.owning` services
complete normally and waits for their close and cleanup before stopping
the Workers. It does not wait to accept more clients. Abort requests
cooperative cancellation of outstanding accepts and accepted services,
ensures their owning cleanup and closes the listener; it does not preempt
ordinary guest execution or guarantee a bounded shutdown duration.

`shutdown("abort")` during `DRAINING` escalates irreversibly to `ABORTING`.
`shutdown("drain")` cannot reverse an abort. Repeated requests of an already
selected mode are idempotent as lifecycle requests; they never perform a
second listener close, duplicate service cancellation, or restart Workers.
After `TERMINATED` both modes merely observe the established result. There
is no implicit grace period, deadline, timer, forced kill, or retry.

### 15.9 Hosting Futures and cancellation (D216-9)

`ready()`, `shutdown(mode)` and `termination()` return local ordinary
Futures with the four Core states `pending`, `resolved`, `failed` and
`cancelled`. Their normal resolved value is the exact `host` object.

- `ready()` observes successful entry to `RUNNING`; if startup fails or
  shutdown precludes readiness, it cannot resolve successfully.
- `shutdown(mode)` observes completion of the requested host shutdown,
  including mandatory resource and Worker cleanup. It cannot resolve on
  mere request acceptance or listener-close initiation.
- `termination()` observes final host termination whether shutdown was
  requested or failure triggered it. It may be obtained before, during,
  or after termination.

An internal listener, Worker, or cleanup failure that prevents successful
hosting completes the relevant non-cancelled observers as failed with
`HostError`. A successful drain or abort resolves the relevant observers.
Each observer obeys first-terminal-transition-wins; cancelling an observer
absolves only that caller's wait and neither cancels another observer nor
requests, reverses, accelerates, or abandons the host's underlying lifecycle
and cleanup. No public hosting Future acts as a cross-Actor supervision
capability.

### 15.10 Failure classification and isolation (D216-10)

`HostError` is a Standard Library Error prototype directly delegating from
`Error`. It represents hosting infrastructure failure, not an alternate
`HttpError`, TCP Error, or mechanism for exposing a Worker's private Error
across Actor boundaries. Each hosting-level failure delivered to a caller
uses a fresh `HostError`. The portable distinction is its `kind` String:

| `kind` | Meaning |
| --- | --- |
| `"startup"` | Worker/bootstrap/readiness failure before `RUNNING` |
| `"listener"` | Listener/acceptance-domain failure outside normal close |
| `"worker"` | Unexpected Worker termination after start |
| `"shutdown"` | Mandatory shutdown/connection/listener cleanup failure |

Failures of individual client requests, rejected HTTP messages, response
encoding, application handlers and ordinary client I/O are handled and
retired by their respective owning sessions under §§7–12. They do not
automatically fail or terminate the host; their failure Futures remain
Worker-local. A fatal Worker error or unexpected known Worker termination
is not isolated as an ordinary client failure: the host commits abort
cleanup and fails the hosting observers. Listener failure does likewise.
Worker failure is observed through the existing Actor lifecycle observation
contract; its private unhandled Error is not transferred to the host.
No Worker is silently restarted, replaced, or rebalanced.

If a fatal hosting failure races with requested shutdown, the host still
performs the required shutdown and cleanup, and the final non-cancelled
hosting result reports failure rather than disguising it as a successful
drain. Mandatory cleanup Error precedence remains governed by the
general `ensure` and `Closable` contracts.

### 15.11 Scope, coordination and portability (D216-11)

Host control and the `TcpListener` remain confined to their constructing
Actor. Each Worker, its `TcpAcceptor` representation, accepted connections,
handler module instance and session Tasks remain confined to that Worker.
Only transferable control data and `ActorRef` values traverse ordinary
Actor communication; `TcpAcceptor` traverses only permitted same-Process
`Actor.spawn` initialization. Completion coordination may observe
`ActorRef.termination()`, but creation genealogy does not grant a new
failure-authority API or implicit Actor lifetime ownership.

One Worker is useful on a single CPU (C). Multiple Workers may execute on
separate CPU cores in the same Process (A), without one mandatory
accept-serializing Actor. No multicore throughput guarantee, fairness
guarantee, CPU pinning, specific scheduler, or OS-backend dependence is part
of the contract. Multiple Processes/nodes (P), migration and distributed
listener placement are not introduced.

### 15.12 Opt-in implementation and exclusions (D216-12)

The intended Standard Library implementation is primarily Protos:
`protos/lib/http/Host.protos` owns the public facility;
`protos/lib/http/internal/HostConfig.protos` validates configuration;
`HostLifecycle.protos` coordinates readiness, shutdown and failures;
`HostWorker.protos` owns Actor bootstrap and acceptance loops; and
`HostConnection.protos` owns connection admission, session bookkeeping and
cleanup. These are implementation organization names, not additional public
modules or normative privileges. They reuse the existing TCP, Actor, Future,
Task and `Server.serveOne.owning` primitives. No new Java primitive or
Java HTTP engine is selected by D216.

The scope expressly excludes HTTP keep-alive/pipelining, HTTP/2, HTTP/3,
TLS, WebSockets, Upgrade/CONNECT tunnelling, an HTTP client, routing
frameworks, authentication, `TcpConnection` transfer, cross-Process
`TcpAcceptor` delegation, multinode distribution, speculative proxy Actors
and non-portable timer/deadline facilities. Trusted/protected ingress and
explicit bounded admission remain prerequisites for practical deployment;
an indefinitely slow admitted client may indefinitely occupy its quota
and delay drain. Importing neither `std:http/Host` nor `std:http/Server`
creates no hosting Workers, listening authority, global state, or runtime
cost.
