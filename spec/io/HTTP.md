# Protos HTTP/1.1 Server, Hosting and Client Library v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-10-30

This document is the primary normative owner of the Standard Library HTTP/1.1
single-request origin-server facility `std:http/Server` selected by D215
(D215-F3–F8: Option A, A3-minimal, Q0, F3 = M1, F4 = H1, F5 = B1, F6 = L1,
F7 = W1, F8 = S1), of the separate optional continuous-hosting facility
`std:http/Host` selected by D216 and extended by D225-A, and of the separate
optional single-exchange client facility `std:http/Client` selected by D218-B.
It therefore owns three independent Standard Library facilities:

- `std:http/Server` — D215, §§1–13;
- `std:http/Host` — D216, §15, with the Worker-local handler factory of
  D225-A in §15.13; and
- `std:http/Client` — D218-B, §16.

It also owns two optional composition modules selected by D222-C:

- `std:http/Router` — authority-free request routing, §17; and
- `std:http/UrlClient` — explicit-authority one-shot URL requests, §18.

D216 does not revise D215. D218-B revises none of D215, D216 or D217. D225-A
adds one `Host` construction operation and revises no other D215, D216 or
D218-B rule. D222-C adds §17 and §18 and revises no rule of §§1–16.

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
optional continuous hosting without changing any `std:http/Server` behavior;
§16 separately defines the optional single-exchange client without changing
any `std:http/Server` or `std:http/Host` behavior.

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
facility; §16 defines the separately optional, bounded, explicit-duplex,
single-exchange HTTP/1.1 client. The following remain outside the approved HTTP
contract and require separate explicit design:

- higher-level HTTP client facilities beyond §16: URL interpretation,
  connection acquisition, proxies, redirects, cookies, content codings,
  retries, connection pooling and reuse, and streaming request bodies;
- TLS within the HTTP facilities, including HTTPS hosting; client TLS over an
  application-supplied duplex is owned separately by `TLS.md`;
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
Host.owningWithFactory(listener, factoryModule, factoryBinding,
                       initialization, options)          -> host
host.ready()                                            -> Future<host>
host.shutdown("drain")                                  -> Future<host>
host.shutdown("abort")                                  -> Future<host>
host.termination()                                      -> Future<host>
```

`Host.owningWithFactory` is the D225-A construction variant of §15.13; except
where §15.13 says otherwise, every rule of this section stated for
`Host.owning` applies to it, with `factoryModule` and `factoryBinding` in
place of `handlerModule` and `handlerBinding`. `Host` has exactly these two
construction operations.

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
Under `Host.owningWithFactory` each Worker instead builds its handler with its
own factory binding as §15.13 specifies. Neither the handler Closure, a
mutable module instance, the listener, nor an accepted `TcpConnection`
crosses an Actor boundary.

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
`Actor.spawn` initialization, and only as the acceptor derived by the host.
Under §15.13 each Worker's copy of the application initialization, and the
handler built from it, likewise remain confined to that Worker. Completion
coordination may observe `ActorRef.termination()`, but creation genealogy
does not grant a new failure-authority API or implicit Actor lifetime
ownership.

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

### 15.13 Worker-local handler factory (D225-A)

`Host.owningWithFactory(listener, factoryModule, factoryBinding,
initialization, options)` is the second construction operation of `Host`. It
differs from `Host.owning` only in how each Worker obtains its handler.
Everything else in §§15.1–15.12 applies unchanged, including listener
custody, Worker count and placement, quotas and admission, connection
custody, `ready()`, `shutdown("drain")`, `shutdown("abort")`,
`termination()`, cancellation, failure classification and precedence, and
the use of `Server.serveOne.owning` for every admitted connection.
`Host.owning` is unaffected by this section.

**Construction.** `listener`, `factoryModule`, `factoryBinding` and
`options` are validated exactly as the corresponding arguments of
`Host.owning` (§15.2): `factoryModule` and `factoryBinding` must be semantic
Strings, and an invalid argument signals a fresh `InvalidIOArgument`
synchronously, without listener custody, Workers or I/O. `initialization`
may be any value. It is not a structural argument and is not examined before
valid construction has acquired listener custody.

**Initialization transfer.** For each Worker, `initialization` crosses into
that Worker under the ordinary Actor pass-by-value rules
(`../concurrency/ACTORS.md` §16), exactly as an argument of `ActorRef.send` or
`ActorRef.request` crosses:

- each Worker receives its own independent destination-local copy; no
  mutable object is shared between the constructing Actor and a Worker or
  between two Workers, and aliases and cycles inside `initialization` are
  preserved within each copy;
- the copy is formed during construction, so a mutation made by the caller
  after `Host.owningWithFactory` returns is never observed by a Worker;
- an `ActorRef` keeps its destination identity and its existing
  communication and stop authority, without attenuation; any other value
  with an explicit Actor-transfer contract keeps that contract; and
- every value that is non-transferable at an ordinary Actor boundary is
  rejected, including a Closure, a Future, `Network`, a Resolver, a
  `TcpListener`, a `TcpConnection` and a `TcpAcceptor`.

The `Actor.spawn`-only `TcpAcceptor` delegation (`../concurrency/ACTORS.md`
§8, `NETWORK.md` §11) is used solely for the acceptor the host itself derives
(§15.3). It never applies to `initialization`: a caller-supplied
`TcpAcceptor` anywhere in it is non-transferable. A non-transferable
`initialization` is a synchronous failure while creating the Workers (§15.2):
`Host.owningWithFactory` still returns `host`, no Worker receives any part of
`initialization`, no factory is invoked, and the hosting Futures report a
`HostError` of kind `"startup"` after the mandatory cleanup of §§15.7 and
15.10.

`initialization` is delivered to a Worker after that Worker Actor has been
created, not as an `Actor.spawn` initialization argument. A Worker Actor may
therefore complete its Actor bootstrap and reach Actor `READY`
(`../concurrency/ACTORS.md` §9) before it receives `initialization`, and a
rejected transfer may occur after the bootstrap of a Worker already created.
Such a Worker is an ordinary Worker of the host: the mandatory cleanup aborts,
stops and awaits it like any other.

**Worker construction.** Inside each Worker, before it enters its acceptance
service:

1. it imports its own instance of `factoryModule` and resolves its own local
   binding `factoryBinding`, under exactly the rules §15.4 states for a
   handler binding;
2. it invokes that factory exactly once as `factory(localInitialization)`,
   with its own copy as the only argument; and
3. it requires the result to be callable under `../semantics/CALLABLES.md`,
   "Callability inspection".

That result is the Worker's handler. Every session the Worker admits invokes
it as `handler(request)` exactly as §2.2 specifies. The factory is invoked at
most once during the whole lifetime of a Worker incarnation: no later message
to the Worker invokes it again or starts a second acceptance service. It is not
invoked per request, in the constructing Actor, or with any other argument,
and the caller never supplies a Closure or module instance. The result is neither
awaited nor adopted: a Future is subject to the same callability check as any
other value. A missing module, a missing local binding, a non-callable binding,
an Error escaping the factory invocation, and a non-callable result are each a
startup failure (§15.10).

**Readiness boundary.** The readiness this section constrains is hosting
readiness, not Actor `READY`. For `Host.owningWithFactory`, a Worker has
"successfully initialized" in the sense of §15.7 only when its factory
invocation has returned and its result has passed the callability check. A
Worker enters its acceptance service only after that, and the host enters
`RUNNING`, and `ready()` resolves, only after every Worker has done so. A
Worker Actor that is already `READY` but still constructing its handler is in
hosting state `STARTING`. This section does not change the Actor lifecycle of
`../concurrency/ACTORS.md`.

**Shutdown during construction.** A shutdown request made while the host is
`STARTING` (§§15.7, 15.8) also reaches Workers that are still constructing
their handler. From that cutover on, such a Worker never enters its acceptance
service and never invokes its factory if it has not done so yet. `abort`
requests cooperative cancellation of a factory invocation still in progress
(`../concurrency/FUTURES_AND_TASKS.md` §23). That cancellation is part of the
requested shutdown, not a startup failure, and the shutdown completes as §15.8
specifies once the Worker has stopped. As for handler code (§15.8), a factory
invocation that never reaches a cancellation boundary still runs to its end
first.

**Authority and independent services.** The factory module is an ordinary
module instance of the Worker. It receives no ambient authority, and the
factory receives no value other than its copy of `initialization`. A host
does not own, stop, monitor or supervise an Actor denoted by an `ActorRef` in
`initialization`, and that Actor's lifetime is not tied to any host. Its
termination does not by itself fail or terminate a host. A handler that
communicates with it observes the ordinary Actor communication outcomes
inside its own session, where failures are retired as handler failures under
§15.10. Shutting down a host neither stops such an Actor nor adds any retry,
timeout, cancellation propagation or rollback to the Actor communication
rules. A reply that arrives after its session or Worker ended has no effect
on the host lifecycle. Several hosts may hold references to the same Actor
and remain independently controllable.

D225-A defines no mechanism for creating such service Actors or for granting
them `Network` or Resolver authority. It also defines no dependency container
or service locator, no factory invocation per request or per connection, no
module instance shared between Actors, no Closure transfer, and no capability
attenuation.

## 16. Single-exchange HTTP/1.1 Client (D218-B)

D218-B selects a distinct, opt-in Standard Library facility that performs
exactly one HTTP/1.1 request/response exchange over a byte duplex supplied by
the application. This section is the sole normative owner of its public
contract. It does not revise D215 (§§1–13), D216 (§15) or D217, and it adds no
`Network`, `TcpConnection`, `TcpListener` or `TcpAcceptor` operation. Byte
I/O, Future, Task, cancellation, Error and `ensure` semantics remain owned by
the documents named in the introduction. RFC 9110 and RFC 9112 apply only
where this section does not state a stricter rule.

### 16.1 Independent module and exports

The explicit module `std:http/Client` exports exactly `Client` and
`ClientError`. There is no Prelude binding and no implicit import. Importing
`std:http/Client` does not import, initialize or require `std:http/Server` or
`std:http/Host`, and importing either of those does not make `Client`
available. Each Actor that imports the module obtains its own module instance
under `../semantics/MODULES.md`; no exchange state is shared between module
instances or Actors.

`ClientError` delegates immediately from `Error`. It is independent of
`HttpError` (§12.1) and `HostError` (§15.10): neither delegates from the
other, and `ClientError` is not a Core or Prelude Error prototype.

### 16.2 Public operations and argument admission

```text
Client.exchangeOne(duplex, request, onResponse, limits) -> Future<result>
Client.exchangeOne.owning(duplex, request, onResponse, limits) -> Future<result>
```

Each successfully dispatched invocation returns a fresh standard `Future`.
Failures before ordinary dispatch — lookup, argument evaluation, receiver
outside the domain and arity — follow the general language rules.

After ordinary dispatch, the arguments are validated before any effect. The
returned Future is already failed with a fresh `InvalidIOArgument` occurrence
when any of the following holds:

1. `duplex` does not satisfy both `ByteReadable` and `ByteWritable`;
2. for `exchangeOne.owning`, `duplex` does not satisfy `Closable`;
3. `onResponse` does not satisfy the invocability protocol;
4. `limits` is neither `null` nor an ordinary object, has an own slot that is
   not a limit name of §16.12, or has a recognized limit whose value is not an
   Integer in the range 1 through 2147483647; or
5. `request` is not admissible under §16.4 and §16.5.

When validation fails, no Task is created, no exchange session is
established, no ownership of `duplex` is acquired, no octet is read or
written, `onResponse` is not invoked, and `duplex` is never closed. No
synchronous Error is signaled for these conditions after dispatch, and they
are never represented by `ClientError`.

When validation succeeds, the logical order is: the limits and the request
are captured (§16.4, §16.12); the exchange session is established and, for
`exchangeOne.owning`, close responsibility for `duplex` is acquired; exactly
one cooperative service Task is created; and its Future is returned. The
service Task belongs to the invoker's Actor and is a structured child of the
invoker's structured scope, or of the Actor-root asynchronous scope when the
invoker has no current Task, under `../concurrency/FUTURES_AND_TASKS.md` §24.
The entire exchange — request emission, response parsing, the callback, body
reads, retirement and cleanup — runs in that one Task. No additional Task is
created for the callback, a body read, a write or cleanup; I/O Futures are not
Tasks.

### 16.3 Duplex authority and borrowed/owning custody

The Client exercises no authority beyond the byte-I/O capability (and, for the
owning variant, the `Closable` capability) of the duplex it is given. It does
not acquire a `Network` capability, resolve names, open TCP connections,
establish TLS, interpret URLs, create Actors, or use clocks or timers. The
application obtains the duplex, for example by an explicit
`network.connectTcp(...)` under `NETWORK.md`, and passes the resulting
connection, or the secure duplex delivered for it by `std:tls/Client`
(`TLS.md` §12), unchanged.

`exchangeOne` borrows `duplex`: it never invokes `close`, `shutdownRead` or
`shutdownWrite` on it, and on every terminal path the duplex remains owned by
the caller in whatever state the exchange left it. Octets read by the Client
beyond what the callback consumed are not returned to the caller.

`exchangeOne.owning` takes custody of `duplex` at admission and invokes
`duplex.close()` exactly once on every admitted terminal path: normal
completion, a `ClientError`, a callback failure, an I/O failure and
cancellation, including cancellation requested before the service Task
executes its first instruction. The close is awaited, through a concurrent
cancellation, before the service Future becomes terminal. The owning variant
performs no half-close.

### 16.4 Request object, snapshots and serialization

`request` is ordinary structural data: it is admissible only when it is an
ordinary object with the own slots `method`, `target`, `headers` and `body`.
Other own slots are ignored, and inherited slots are never read. The four
slots must satisfy:

```text
{
    method: String       // an HTTP token (RFC 9110 §9.1); not "CONNECT"
    target: Bytes        // origin-form, or "*" when method is "OPTIONS"
    headers: Array<[String, Bytes]>
    body: null | Bytes
}
```

- `method` is a `String` whose characters are all `tchar`. Method names are
  case-sensitive: exactly `"CONNECT"` is rejected.
- `target` is `Bytes` containing no CTL, SP, DEL, non-ASCII octet or `#`. It is
  either the single octet `*`, admissible only with method `"OPTIONS"`, or an
  origin-form target (`absolute-path [ "?" query ]`) whose path and query octets
  are unreserved, sub-delimiter, `:`, `@`, `/` (and `?` in the query) or a
  complete percent-encoding of two hexadecimal digits. Absolute-form,
  authority-form and any proxy-oriented target are inadmissible, even though
  the server request parser (§7.1) admits absolute-form.
- `headers` is an `Array` whose every element is an `Array` of exactly two
  elements: a `String` name and a `Bytes` value (§16.5).
- `body` is `null` or `Bytes`.

No `String` is converted to `Bytes`, nor `Bytes` to `String`, implicitly: a
`String` target, header value or body is inadmissible.

Admission takes a complete private snapshot of the method, the target octets,
every header name and value in order, and the body octets before any I/O.
Later mutation of the request object, its `headers` Array, any pair, or any
`Bytes` it referenced has no effect on the exchange.

The request is serialized exactly as:

1. `method SP target SP "HTTP/1.1" CRLF`;
2. every caller field, in the original order, with the original name casing,
   as `name ": " value CRLF`, duplicates preserved;
3. `"Content-Length: " length CRLF`, where `length` is the decimal octet count
   of the body snapshot, `0` when `body` is `null`;
4. `"Connection: close" CRLF`; and
5. a final `CRLF`,

followed by the body snapshot octets when it is non-empty. `Host` is never
injected or altered. No `Expect`, `Upgrade`, `TE`, `Transfer-Encoding`,
compression, cookie, authentication, proxy or redirect field is added.

The head is written by one `write` and a non-empty body by one later `write`;
each is awaited before the next operation, and response parsing begins only
after every request write has completed. A failed or partially committed
write is never retried; its Error is the exchange outcome (§16.10). The Client
does not monitor the input for an early response while writing.

### 16.5 Host and header validation

Each caller field must satisfy:

- the name is a non-empty `String` whose characters are all `tchar`;
- the value octets are field-value octets (SP, HTAB, VCHAR or obs-text), so no
  CR, LF, NUL or other CTL can be emitted; values are binary, are never
  decoded, and are serialized exactly as supplied, including any outer SP or
  HTAB, without trimming or normalization; and
- the name is none of `Content-Length`, `Transfer-Encoding`, `Connection`,
  `Expect`, `Upgrade`, `Trailer`, `TE`, `Keep-Alive` or `Proxy-Connection`,
  compared ASCII case-insensitively. Framing, connection management and
  protocol switching are controlled only by the Client.

Exactly one field named `Host` (ASCII case-insensitively) must be present, and
its value must be `host [ ":" port ]` with `host` an `IP-literal` (IPv6 or
IPvFuture in brackets), an IPv4 address or a non-empty `reg-name`, and `port`
zero or more digits — the same syntax as §7.2. The value is not resolved.
Outer SP or HTAB is permitted on `Host` as on any other field: the Host syntax
is checked against the semantic value obtained by excluding the leading and
trailing SP/HTAB octets. That exclusion is for validation only; the original
value octets, including the outer whitespace, are serialized exactly as
supplied. An empty or syntactically invalid semantic Host value is
inadmissible.

### 16.6 Response status/head parsing and informational responses

After the request is written, the Client reads the duplex with
`duplex.read(readSize)`, where `readSize` is the lesser of `ioChunkBytes` and
`maxPrefetchBytes`, and parses response heads incrementally. A response head
is:

```text
status-line = HTTP-version SP 3DIGIT SP reason-phrase CRLF
header-section = *( field-line CRLF ) CRLF
```

- Every line ends with CRLF; a bare CR or LF is malformed.
- `HTTP-version` must be well-formed (`"HTTP/" DIGIT "." DIGIT`); any other
  version text is malformed. A well-formed version other than `HTTP/1.1` is an
  unsupported feature.
- The status code is exactly three digits in the range 100 through 599; a
  missing space, a non-digit or a value outside that range is malformed. The
  reason-phrase may be empty, must contain only field-value octets, and is
  discarded.
- Each field line is `field-name ":" OWS field-value OWS` with a non-empty
  token name and field-value octets. Whitespace before `:` and obs-fold (a line
  beginning with SP or HTAB) are malformed. Only the outer OWS is removed.
- Field names keep their original casing; fields keep their order and
  duplicates; values are binary `Bytes`.
- `Content-Length` and `Transfer-Encoding` are recognized ASCII
  case-insensitively. More than one `Content-Length` field, a value that is
  not `1*DIGIT` (including a comma list), or a value above
  9223372036854775807 is malformed.
- `Transfer-Encoding` is supported only in a strict single-coding subset:
  exactly one `Transfer-Encoding` field whose value is exactly one list
  element, the coding `chunked` (ASCII case-insensitive, surrounding OWS
  permitted) without parameters. The list elements follow RFC 9110:

  ```text
  transfer-coding    = token *( OWS ";" OWS transfer-parameter )
  transfer-parameter = token BWS "=" BWS ( token / quoted-string )
  BWS                = *( SP / HTAB )
  ```

  BWS around `=` is syntactically valid, but it does not make a parameterized
  coding supported. A value that is not syntactically a list of
  `transfer-coding` elements (for example a missing parameter name, `=` or
  value, or an unterminated quoted-string), or that contains no coding at all,
  is malformed. A
  syntactically valid value outside the subset — another coding, several
  codings, a coding with parameters, any empty list element, or more than one
  `Transfer-Encoding` field — is an unsupported feature; empty elements are
  never silently discarded. Both `Transfer-Encoding` and `Content-Length`
  together is malformed, and the malformed checks precede the unsupported
  classification.
- Status `101 Switching Protocols` is an unsupported feature.

The status-line, header-byte and header-field limits of §16.12 apply to each
response head separately. The parser stops immediately after the CRLF CRLF
that ends a head, and every octet already received after it is retained, in
order, for the next head or for the body.

A head with status 100 through 199 (other than 101) is informational: it is
validated completely and discarded, and the Client then parses the next head.
At most `maxInformationalResponses` informational heads are accepted; one
more is a `responseLimit` failure. The first head with status 200 through 599
is the final response. Physical EOF before a complete final head is
malformed.

HTTP status codes, including 404 and 500, are ordinary protocol results: they
invoke `onResponse` and never by themselves fail the exchange.

### 16.7 Response framing, close-delimited bodies and trailers

The body framing of the final response is, in order of precedence:

1. none, when the request method is `HEAD` or the status is 204, 205 or 304;
   the framing fields such a head may carry were validated by §16.6 and by the
   status-specific rules below, and never frame content;
2. chunked, when `Transfer-Encoding: chunked` is present;
3. fixed, when `Content-Length` is present; a declared length above
   `maxResponseBodyBytes` is a `responseLimit` failure before `onResponse` is
   invoked; or
4. close-delimited otherwise: the body is every octet until clean physical
   EOF.

Status-specific framing fields are validated when the head is completed,
after the rules of §16.6 and before the head is accepted:

- an informational (1xx) or 204 response must contain neither
  `Content-Length` (not even `Content-Length: 0`) nor `Transfer-Encoding`;
- a 205 response may omit `Content-Length` or carry exactly
  `Content-Length: 0`; this client rejects a positive `Content-Length` on 205;
- a response to `HEAD` and a 304 response may carry a syntactically valid
  `Content-Length` describing the selected representation rather than
  transferred content. It remains subject to the strict syntax,
  duplicate-field and framing-conflict rules of §16.6, is preserved in
  `response.headers`, and is never compared with `maxResponseBodyBytes`.

A forbidden or contradictory framing field fails the exchange with a
`ClientError` of kind `"malformedResponse"` without invoking `onResponse`;
`101 Switching Protocols` and a `Transfer-Encoding` outside the strict subset
keep their `"unsupportedFeature"` classification where §16.6 assigns it. The
declared-length budget of rule 3 (`"responseLimit"` before `onResponse`)
applies only to a response whose content is framed by that `Content-Length`.
Every no-body response is at its logical end immediately.

Chunked decoding uses the same chunk grammar as §7.3: a hexadecimal
chunk-size (overflow beyond 9223372036854775807 is malformed) with optional
chunk extensions, which are validated and discarded; chunk data followed by
CRLF; a last chunk `0`; and a trailer section of field lines validated as
header fields and discarded. Trailers are never exposed.

A fixed or chunked body that is not complete at physical EOF is malformed.
For a close-delimited body physical EOF is normal completion. A decoded body
that would exceed `maxResponseBodyBytes` is a `responseLimit` failure: for a
chunked body when a chunk-size line declares it, for a close-delimited body
when the first octet beyond the limit is received; octets up to the limit
are delivered first. Chunk-line and trailer octet or field limits beyond
`maxChunkLineBytes`, `maxTrailerBytes` or `maxTrailerFields` are
`responseLimit` failures.

The Client never consumes octets beyond the logical end of the final
response, never parses a second response, never reuses the connection and
never restores read-ahead to the duplex.

### 16.8 Incremental `ByteReadable` response-body contract

The final response passed to `onResponse` is a fresh ordinary object:

```text
{
    version: "1.1"
    status: Integer
    headers: Array<[String, Bytes]>
    body: ByteReadable
}
```

`headers` and its pairs and value `Bytes` are fresh copies owned by the
callback; mutating them has no effect on the exchange.

`response.body.read(maxBytes)` follows `BYTE_IO.md`: it returns a Future; a
non-positive or non-Integer `maxBytes` is an `InvalidIOArgument`; a resolved
result is non-empty `Bytes` of at most `maxBytes` octets or `null` at the
logical end of the body; short reads are permitted; octet order is preserved;
and empty `Bytes` never substitutes for end of body. A body with framing
"none" or with zero length is at its logical end immediately.

The body is decoded on demand. The Client never reads ahead more than one
lower read of `readSize` octets beyond what decoding requires, never buffers
the whole response, and never allocates storage proportional to the body
size. A slow consumer applies backpressure to the duplex. A body read does
not create a Task.

When a body read reaches malformed framing or an exceeded limit, the session
terminalizes: that read fails with a fresh `ClientError` that the exchange
records as its late framing failure, and every later read of `response.body`
fails with a fresh `IOLifecycleError`. An Error of a lower `duplex.read` is
reported once, by the body read that reached it, with its identity
preserved.

### 16.9 Callback lifetime, result adoption and session retirement

`onResponse(response)` is invoked exactly once, inside the service Task, for
the final response, and only after the complete final head was accepted. It
is never invoked when the exchange fails earlier.

`response.body` is usable only while `onResponse` runs. The session retires
immediately when `onResponse` returns, fails or is cancelled; afterwards
every read of an escaped `response.body` fails with a fresh
`IOLifecycleError`. Unread body octets are not drained.

The ordinary result of `onResponse` is not awaited by the Client. After
retirement and cleanup it becomes the result of the service Task, so the
service Future resolves to it under the ordinary Future adoption rules of
`../concurrency/FUTURES_AND_TASKS.md`. A returned pending Future is adopted
as such, but it never extends the life of `response.body`.

### 16.10 Cancellation, cleanup and failure precedence

The service outcome, before mandatory cleanup, is:

1. if a late framing failure was recorded (§16.8), that original
   `ClientError`, even when the callback handled it and returned normally or
   failed with another Error;
2. otherwise, if `onResponse` failed, its Error, with identity preserved;
3. otherwise, the ordinary callback result (§16.9).

An Error of a request `write`, a lower `read` reached while parsing a head,
or a `ClientError` of head parsing, terminates the exchange with that Error,
with identity preserved, without invoking `onResponse`. Ordinary I/O Errors
are never converted to `ClientError`, and `ClientError` never represents an
I/O failure.

Cancellation of the service Future follows
`../concurrency/FUTURES_AND_TASKS.md`. Cancellation during a pending read or
write, during the callback, or before the service Task's first instruction
unwinds the exchange through retirement and, for `exchangeOne.owning`, the
awaited close. Mandatory cleanup completes before the service Future becomes
terminal. A failure of the owning `close` supersedes the earlier outcome,
including a cancellation, a callback Error and a recorded late framing
failure, under the general `ensure` precedence of `../semantics/ERRORS.md`.

### 16.11 `ClientError` taxonomy and freshness

Every protocol failure of the Client is a fresh `ClientError` occurrence whose
`kind` slot is exactly one of:

- `"malformedResponse"` — response syntax or framing violates §16.6 or
  §16.7, including a forbidden framing field on a 1xx, 204 or 205 response
  and physical EOF before the end of a head, a fixed body or a
  chunked body;
- `"responseLimit"` — a limit of §16.12 is exceeded, including a declared
  `Content-Length` above `maxResponseBodyBytes` for a body-bearing response
  (§16.7); and
- `"unsupportedFeature"` — a well-formed non-`HTTP/1.1` version (§16.6), `101 Switching Protocols`, or a `Transfer-Encoding` outside the
  strict subset of §16.6.

A `ClientError` has no `status` slot; no HTTP status is manufactured. No two
failures share an occurrence.

### 16.12 Limits, memory bounds, portability and exclusions

`limits` is `null` or an ordinary object whose own slots are limit names
below; absent names take their defaults. Every supplied value is an Integer
from 1 through 2147483647. The effective limits are captured at admission;
later mutation of `limits` has no effect.

| Limit | Default | Bound |
|---|---:|---|
| `maxStatusLineBytes` | 8192 | octets of one status line, including CRLF |
| `maxHeaderBytes` | 32768 | octets of one header section after the status line, including the final CRLF |
| `maxHeaderFields` | 100 | field lines of one response head |
| `maxChunkLineBytes` | 8192 | octets of one chunk-size line, including CRLF |
| `maxTrailerBytes` | 8192 | octets of the trailer section, including the final CRLF |
| `maxTrailerFields` | 100 | trailer field lines |
| `maxResponseBodyBytes` | 33554432 | decoded body octets |
| `maxInformationalResponses` | 8 | informational heads before the final head |
| `maxPrefetchBytes` | 65536 | octets read ahead by one lower read |
| `ioChunkBytes` | 16384 | octets requested by one lower read |

These limits are independent of the server limits of §10, whose names and
signed-64-bit range they do not change. Limits bound per-exchange memory; the
Client defines no timeout, and an indefinitely slow peer may occupy the
exchange indefinitely unless the application cancels it.

Portable behavior excludes: DNS and URL acquisition, implicit TCP or TLS,
proxies and CONNECT, request-body streaming, `Expect: 100-continue`,
`Upgrade`, HTTP/2 and HTTP/3, persistent connections, pipelining and
connection pooling, redirects, cookies, authentication, content codings,
retries, timers and timeouts, and exposed trailers. The implementation is
Protos source (`protos/lib/http/Client.protos` and
`protos/lib/http/internal/Client*.protos`) over the shared internal HTTP wire
syntax and framing modules and the existing private asynchronous bridge; no
host HTTP engine is involved. These organization names are not additional
public modules.

### 16.13 Conformance examples

*Example (GET):* `request` `{method: "GET", target: "/a?b" as Bytes, headers:
[["Host", "example.org" as Bytes]], body: null}` writes exactly
`GET /a?b HTTP/1.1\r\nHost: example.org\r\nContent-Length: 0\r\nConnection:
close\r\n\r\n`.

*Example (informational and 404):* input `HTTP/1.1 100 Continue\r\n\r\nHTTP/1.1
404 Not Found\r\nContent-Length: 2\r\n\r\nno` invokes `onResponse` once with
`status` 404 and a body yielding `no` then `null`; the service Future resolves
to the callback result.

*Example (close-delimited):* `HTTP/1.1 200 OK\r\n\r\nabc` followed by physical
EOF yields the body `abc` then `null`.

*Counterexample (premature EOF):* `HTTP/1.1 200 OK\r\nContent-Length:
5\r\n\r\nab` followed by EOF fails the body read reaching EOF with a fresh
`ClientError` of kind `"malformedResponse"`; the service Future fails with
that same occurrence even if the callback handled it.

*Counterexample (caller framing):* a request header `["Content-Length", "0"
as Bytes]` or `["transfer-encoding", "chunked" as Bytes]` makes the returned
Future already failed with `InvalidIOArgument`; nothing is written.

*Counterexample (escaped body):* reading `response.body` after `onResponse`
returned fails with a fresh `IOLifecycleError`, also when `onResponse`
returned a pending Future.

*Example (outer whitespace on Host):* a request header `["Host", "\t
example.org \t" as Bytes]` is admitted and written as `Host: \t example.org
\t\r\n`; `["Host", " " as Bytes]` is inadmissible.

*Counterexample (parameterized coding with BWS):* a final head with
`Transfer-Encoding: gzip; q = 1` fails with a `ClientError` of kind
`"unsupportedFeature"`; `Transfer-Encoding: chunked; x =` fails as
`"malformedResponse"`. `onResponse` is not invoked.

*Example (representation metadata):* a response to `HEAD`, or a 304, with
`Content-Length: 9223372036854775807` and `maxResponseBodyBytes` 4 invokes
`onResponse`; its body yields `null` at once.

*Counterexample (forbidden framing):* `HTTP/1.1 204 No Content\r\nContent-Length:
0\r\n\r\n`, `HTTP/1.1 100 Continue\r\nTransfer-Encoding: chunked\r\n\r\n` and
`HTTP/1.1 205 Reset Content\r\nContent-Length: 1\r\n\r\n` each fail with a
`ClientError` of kind `"malformedResponse"`; `onResponse` is not invoked.

*Counterexample (conflicting framing):* a final head with both
`Transfer-Encoding: chunked` and `Content-Length: 3` fails the exchange with
a `ClientError` of kind `"malformedResponse"` without invoking `onResponse`.

## 17. Authority-free request Router (D222-C)

D222-C selects a distinct, opt-in Standard Library module that builds a
handler for §2.2 from a declared route table. This section is the sole
normative owner of its public contract. It revises no rule of §§1–16. The
Router is ordinary application logic above the handler contract: it is not
part of the Server, the Host or the wire protocol.

### 17.1 Module and exports

The explicit module `std:http/Router` exports exactly `Router`, whose only
operation is `compile`. There is no Prelude binding, no implicit import and no
import effect: importing the module registers nothing, installs no global
state and does not import `std:http/Server` or `std:http/Host`. Each Actor
that imports the module obtains its own module instance under
`../semantics/MODULES.md`. No public `RouterError` prototype exists.

### 17.2 Route table and compilation

```text
Router.compile(routes) -> handler
```

`routes` is an `Array` of ordinary structural entries with the own slots
`method`, `path` and `handle`; other own slots are ignored and inherited
slots are never read:

```text
{
    method: String   // an HTTP token (RFC 9110 §9.1), compared case-sensitively
    path: String     // a route path, below
    handle: callable // invoked as handle(request, match)
}
```

A route path is either exactly `"*"`, admitted only when `method` is
`"OPTIONS"`, or ASCII text beginning with `/` whose `/`-separated segments
are each one of:

- a *literal segment*: `pchar` text (RFC 3986 §3.3; unreserved, sub-delims,
  `:`, `@` and well-formed `pct-encoded` triplets), compared octet-for-octet
  and case-sensitively with the raw target segment; a literal segment may be
  empty only as the last segment, so `/` and a trailing `/` are expressible;
- a *capture*: exactly `{name}` occupying the whole segment, where `name` is
  an ASCII identifier `[A-Za-z_][A-Za-z0-9_]*`, distinct within its route.

There are no wildcard, glob, regular-expression, optional or multi-segment
captures, and no implicit normalization of the route path: `/a` and `/a/` are
different routes.

For two routes with the same `method`, compilation compares them segment by
segment. They are *disjoint* when their segment counts differ, when one is
`"*"` and the other is not, or when at some position two literals differ or a
capture faces an empty literal (a capture never matches an empty segment,
§17.4). Otherwise they overlap and are *ordered* when one of them has a literal
at every position where they differ; they are *duplicates* when no position
differs; and they *cross* when each has a literal at some position where the
other has a capture — for example `/a/{x}` and `/{y}/b`, which both match
`/a/b`. Duplicate or crossing routes of the same method make `routes`
inadmissible. Routes of different methods never conflict.

`compile` signals a fresh `Error` synchronously when `routes` is not an
`Array`, an entry is not an ordinary object with the three slots, `method` is
not a token `String`, `path` is not an admissible route path, `handle` does
not satisfy the invocability protocol, or two routes are duplicate or
crossing. Every such failure is detected at compilation, never at request
time.

On success, `compile` snapshots each method, parsed path, declaration order and
`handle` reference. Later mutation of `routes` or of any entry never changes
dispatch. `handle` Closures are referenced, never copied, and are neither
cloned nor shared across Actors by `compile`.

### 17.3 The handler

`compile` answers an ordinary one-argument callable `handler(request)` that
satisfies §2.2. `request` must be an ordinary object whose `method` is a
`String` and whose `target` is `Bytes`; otherwise the handler signals a fresh
`Error`. Dispatch (§17.5) reads only `request.method` and `request.target`.

When a route is selected, the handler invokes `route.handle(request, match)`
exactly once with the unchanged `request` and answers its result unchanged.
`match` is a fresh ordinary object:

```text
{
    params: Map      // capture name -> raw segment String
    query: null | String
}
```

`params` maps each capture name of the selected route to the raw, still
percent-encoded spelling of its target segment, as a `String` of the Latin-1
projection of those octets; no value is ever decoded. `query` is the raw query
after the first `?` of the target, exactly as received (including repeated and
unordered parameters), `""` when `?` is present without a query, and `null`
when the target has no `?`. No query Map is built.

### 17.4 Request-target projection

The handler interprets `request.target` byte-exactly under the target forms of
§7.1:

- *origin-form* (beginning `/`): the path is the octets before the first `?`;
- *absolute-form*: the path is the octets after the authority (after the
  scheme when there is no `//` authority) and before the first `?`; an empty
  path routes as `/`, and a path not beginning with `/` matches no route;
- *asterisk-form* `*`: matches only `"*"` routes and has empty `params` and a
  `null` query.

Any other `target`, including one with an octet that §7.1 does not admit,
matches no route. The path is split at every `/` into raw segments; `/` is one
empty segment and a trailing `/` adds a final empty segment. Nothing is
percent-decoded: `%2F` never separates segments, `%2E` is never a dot segment,
`%252F` stays literal, and `.` and `..` segments are ordinary literal text.
The query is excluded from matching and preserved verbatim. A request-target
never carries a fragment.

A route matches a target when both are `"*"`, or when they have equal segment
counts and at every position a literal equals the segment or a capture faces a
nonempty segment.

### 17.5 Selection, method fallback and automatic responses

Among the routes that match the target, the *effective candidates* are those
whose `method` equals `request.method`; when there is none and the request
method is `HEAD`, the matching `GET` routes are the candidates. An explicit
`HEAD` route therefore wins over the `GET` fallback; the Server suppresses
`HEAD` content on the wire (§8). `OPTIONS` is served only by explicit routes.
Compilation guarantees that matching candidates are ordered (§17.2); the most
specific one, regardless of declaration order, is selected.

When no route matches the target, the handler answers
`{status: 404, headers: [], body: null}`. When routes match the target but
none is a candidate, it answers
`{status: 405, headers: [["Allow", value]], body: null}` where `value` is the
Latin-1 Bytes of the distinct methods of the matching routes in declaration
order, joined by `", "`, with `HEAD` inserted immediately after `GET` unless
already listed. Neither response carries a field prohibited by §5; the Server
owns framing and `Connection`.

### 17.6 Authority and effects

The Router never reads `request.body`, acquires `Network`, `Resolver`, clock,
credential or other authority, creates a Task or Actor, serializes data,
adds or modifies Server-owned header fields, or alters Host provisioning. Its
dispatch is deterministic for a given table, method and target. The
implementation is Protos source (`protos/lib/http/Router.protos` and its
`protos/lib/http/internal/Route*.protos` helpers) over the shared internal
HTTP wire syntax; these organization names are not public modules.

### 17.7 Conformance examples

*Example (specificity):* routes `GET /users/{id}` and `GET /users/me`, in
that order, select `/users/me` for target `/users/me` and the capture route
with `params` `{id: "42"}` for `/users/42?x=1&x=2`, whose `query` is
`"x=1&x=2"`.

*Example (raw captures):* route `GET /files/{name}` and target
`/files/a%2Fb` select the route with `params` `{name: "a%2Fb"}`; target
`/files/a/b` matches no route and answers 404.

*Example (405):* routes `GET /items` and `POST /items` and request
`DELETE /items` answer 405 with `Allow: GET, HEAD, POST`.

*Counterexample (crossing):* compiling `GET /a/{x}` with `GET /{y}/b`
signals an `Error`; `GET /a/{x}` with `POST /{y}/b` compiles.

## 18. Explicit-authority one-shot URL client (D222-C)

D222-C selects a distinct, opt-in Standard Library module that composes the
explicitly granted `Resolver` and `Network` capabilities (`NETWORK.md`),
`std:tls/Client` (`TLS.md`) and `std:http/Client` (§16) to perform one request
addressed by an absolute URL. This section is the sole normative owner of its
public contract. It revises no rule of §§1–17, `NETWORK.md` or `TLS.md`.

### 18.1 Module, operation and data shapes

The explicit module `std:http/UrlClient` exports exactly `UrlClient`, whose
only operation is:

```text
UrlClient.requestOne(authorities, url, request, limits) -> Future<BufferedResponse>
```

There is no Prelude binding and no import effect. Importing the module grants
no capability.

```text
authorities: { network, resolver: null | Resolver, trust: null | Trust }
url:         String
request:     { method: String, headers: Array<[String, Bytes]>, body: null | Bytes }
limits:      null | the std:http/Client limits object (§16.12)
BufferedResponse: { status: Integer, headers: Array<[String, Bytes]>, body: Bytes }
```

`authorities` and `request` are ordinary objects with the listed own slots;
other own slots are ignored and inherited slots are never read. The response
has no `version` slot.

### 18.2 URL and request admission

`url` is parsed only by `std:uri.parse`. It is admissible only when it is an
absolute URI whose scheme is `http` or `https` (ASCII case-insensitive), with
an authority whose host is nonempty, with no user information and no
fragment. The host is one of:

- a bracketed IPv6 literal, parsed by `std:network/IpAddresses.parse`;
  `IPvFuture` literals are rejected;
- IPv4 dotted-decimal text, parsed by `std:network/IpAddresses.parse`; or
- an ASCII host name of `.`-separated labels of letters, digits and `-`, each
  1 through 63 octets, at most 253 octets, with at most one terminal root
  dot. No percent escape is admitted. Whether each label is NR-LDH or a strict
  IDNA2008 A-label is decided by `resolver.lookupAddresses` (`NETWORK.md`
  §14) and, for `https`, by the TLS reference identity (`TLS.md` §4); no
  conversion is performed.

An explicit port is 1 through 5 decimal digits with a value in 1..65535; an
empty port is rejected. The default port is 80 for `http` and 443 for
`https`.

`authorities.network` must satisfy the `connectTcp` protocol. A host name
requires a non-null `authorities.resolver` satisfying `lookupAddresses`; a
numeric host does not use it. `https` requires a non-null `authorities.trust`,
which is passed unchanged to `std:tls/Client`; `http` never uses it.

The lower request (§16.4) is built as:

- `method`: `request.method`;
- `target`: the Latin-1 octets of the raw URI path, or `/` when it is empty,
  followed by `?` and the raw query when the URI has a query; no
  normalization, encoding, decoding or reordering occurs;
- `headers`: first exactly one `Host` field whose value is the URI host
  spelling, followed by `:` and the explicit port text exactly as written
  when the URI has a port (including an explicit default port); then every
  caller field in order with its value octets copied; and
- `body`: a copy of `request.body`.

A caller field named `Host` (ASCII case-insensitive) makes the call
inadmissible. Every other field, the method and the body must be admissible
for `std:http/Client` under §16.4 and §16.5.

### 18.3 Admission and the service Task

After ordinary dispatch, `limits` (§16.12), `authorities`, `url` and `request`
are validated and snapshotted before any effect. When validation fails, the
returned Future is already failed with a fresh `InvalidIOArgument`; no Task is
created, no name is looked up and no connection is opened. When it succeeds,
exactly one cooperative service Task is created as for §16.2, and its Future
is returned. The Resolver lookup, the TCP attempts, the TLS handshake and the
HTTP exchange keep their own documented Futures and Tasks. No automatic
redirect, host selection, proxy, authentication, retry, cookie, content
decoding or connection pooling is performed.

### 18.4 Name resolution and TCP acquisition

For a numeric host, the single parsed `IpAddress` is the only candidate and no
lookup occurs. For a host name, `authorities.resolver.lookupAddresses(host)`
is invoked exactly once and its ordered candidates are used. For each
candidate in order, one `network.connectTcp(IpEndpoint(candidate, port))`
attempt is made until the first succeeds. A failed attempt leads to the next
candidate; a cancellation never does. When every attempt fails, the request
fails with the Error of the last attempt, with identity preserved. There are
no concurrent attempts, timers or delays. A lookup failure is the request's
failure, with identity preserved.

### 18.5 TLS, exchange and custody

For `http`, the connection is passed to
`std:http/Client` `Client.exchangeOne.owning(connection, request, onResponse,
limits)`. For `https`, it is first passed to `std:tls/Client`
`Client.handshake.owning(connection, reference, trust, {})`, where
`reference` is the original URL host name or the parsed `IpAddress`, never a
resolved candidate; the delivered secure duplex is then passed to
`Client.exchangeOne.owning`. After a TCP connection exists, no other candidate
is tried, whatever the later outcome.

Exactly one owner is responsible for closing each resource at every moment:
the service until an owning lower operation admits it, then that operation.
A lower admission that fails with `InvalidIOArgument` takes no custody, and
the service closes the resource. On every terminal path — success, a DNS, TCP,
TLS or HTTP failure, or cancellation, including cancellation before the
service Task's first instruction — every acquired resource is closed exactly
once, and every close is awaited before the returned Future becomes terminal.
A cancellation during a pending stage cancels and awaits that stage; a
connection or secure duplex the stage delivers nevertheless is closed.

The outcome is the buffered response, or the first failure with identity and
the cleanup precedence of §16.10, `TLS.md` and `../semantics/ERRORS.md`
preserved. An HTTP status, including 3xx, 4xx and 5xx, is never a failure.

### 18.6 Buffered response

Inside the exchange callback, while `response.body` is live (§16.9), the
service copies `status`, every header pair in order with fresh value Bytes,
and every decoded body octet, read with bounded `read(ioChunkBytes)` calls
until `null`, into fresh Bytes. The callback answers the buffered response
directly; no reader, deferred Future or callback-scoped object escapes. The
decoded body is bounded by `maxResponseBodyBytes` of the lower Client (§16.7,
§16.12); exceeding it fails the request with that Client's `ClientError`.

### 18.7 Exclusions and implementation

Portable behavior excludes HTTP/2 and HTTP/3, request-body streaming,
redirects, retries, cookies, content codings, connection pooling, proxies,
implicit or operating-system trust, and any implicit capability. Worker
provisioning of `std:http/Host` is unaffected. The implementation is Protos
source (`protos/lib/http/UrlClient.protos` and its
`protos/lib/http/internal/Url*.protos` helpers) over `std:uri`,
`std:network`, `std:tls/Client`, `std:http/Client` and the existing private
asynchronous bridge; no host HTTP or URL engine is involved.

### 18.8 Conformance examples

*Example (target and Host):* `url` `"http://127.0.0.1:8080/a%2Fb?x=1&x=1"`
connects to `127.0.0.1:8080` without a lookup and writes `GET
/a%2Fb?x=1&x=1 HTTP/1.1\r\nHost: 127.0.0.1:8080\r\n...`.

*Example (status):* a final `HTTP/1.1 302 Found` with `Location` resolves the
Future with `status` 302 and the `Location` field; no second request is made.

*Counterexample (caller Host):* a request header `["host", "x" as Bytes]`
makes the returned Future already failed with `InvalidIOArgument`; no lookup
or connection occurs.

*Counterexample (URL):* `"ftp://h/"`, `"http://u@h/"`, `"http://h/#f"`,
`"http://h:/"`, `"http://h:65536/"` and `"http://[v1.x]/"` are inadmissible.
