# Protos TLS Client Library v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-10-29

This document is the primary normative owner of the optional Standard Library
TLS client facility `std:tls/Client` selected by D220–S12. It defines how an
application upgrades an already connected byte duplex to an authenticated,
confidential TLS duplex.

TLS is an optional Standard Library facility built on existing capabilities.
Byte reading/writing, Future commitment, cancellation, close and lifecycle
semantics remain owned by `IO_CORE.md` and `BYTE_IO.md`; TCP, `IpAddress`,
`Network` authority and live-resource transfer remain owned by `NETWORK.md`;
Error construction, freshness, taxonomy and cleanup precedence remain owned by
`../semantics/ERRORS.md`; module instantiation remains owned by
`../semantics/MODULES.md`; Task, Future and structured-concurrency semantics
remain owned by `../concurrency/FUTURES_AND_TASKS.md` and
`../concurrency/ACTORS.md`. This document adds only TLS-specific semantics and
does not restate those contracts.

The TLS protocol referred to below is TLS 1.3 (RFC 8446) and TLS 1.2
(RFC 5246, with the secure renegotiation of RFC 5746). Certificate path
validation is that of RFC 5280. Service identity verification is that of
RFC 9525. ALPN is that of RFC 7301. Where this document is stricter than those RFCs, this document
governs.

## 1. Scope and governing model

`std:tls/Client` performs one TLS client handshake over a caller-supplied
duplex and, only on complete success, delivers a secure duplex through which
application data is read and written. It provides no TCP connection, DNS
resolution, URL parsing, HTTP request, timer, proxy, redirect, connection
pool or other acquisition. The application obtains the transport, for example
with an explicit `network.connectTcp(...)` under `NETWORK.md`, and remains
responsible for its authority.

The facility consists of a small portable frontend, a private TLS
record/cryptographic engine, and a separate private trust-source and
path-verification policy. The engine and the verifier are implementation
components, not public modules, backend registries or normative privileges.

## 2. Module, exports and entry points

The explicit module `std:tls/Client` exports exactly `Client`, `Trust` and
`TlsError`. There is no Prelude binding and no implicit import. Importing the
module confers no `Network` capability, no default trust and no ambient
certificate-authority authority. Each Actor that imports the module obtains
its own module instance under `../semantics/MODULES.md`; no TLS state is
shared between module instances, Actors or Contexts. A program that does not
import `std:tls/Client` pays no TLS allocation, provisioning or state.

```text
Client.handshake(duplex, serverIdentity, trust, options) -> Future<SecureDuplex>
Client.handshake.owning(duplex, serverIdentity, trust, options) -> Future<SecureDuplex>
Trust.anchors(certificates) -> trust descriptor
Trust.javaCacerts() -> trust descriptor
Trust.system() -> trust descriptor
```

`SecureDuplex` is a conceptual name for the delivered object; it is not an
exported prototype.

### 2.1 Handshake admission

Each successfully dispatched `handshake` or `handshake.owning` invocation
returns a fresh standard `Future`. Failures before ordinary dispatch —
lookup, argument evaluation, receiver outside the domain and arity — follow
the general language rules.

After ordinary dispatch, the arguments are validated before any effect. The
returned Future is a fresh already-failed non-Task Future with a fresh
`InvalidIOArgument` occurrence when any of the following holds:

1. `duplex` does not satisfy both `ByteReadable` and `ByteWritable`;
2. for `handshake.owning`, `duplex` does not have an effective `Closable`
   `close`; the inherited structural `Object.close` is insufficient;
3. `serverIdentity` is not an admissible reference identity (§4);
4. `trust` is not a trust descriptor produced by `Trust` (§3); there is no
   default; or
5. `options` is not admissible under §6.

When validation fails, no Task is created, no custody of `duplex` is
acquired, no TLS octet is produced or consumed, and `duplex` is never closed.

When validation succeeds, the logical order is: the options are captured; for
`handshake.owning`, close responsibility for `duplex` is acquired; exactly one
structured handshake Task is created as a child of the invoker's structured
scope, or of the Actor-root asynchronous scope when the invoker has no
current Task, under `../concurrency/FUTURES_AND_TASKS.md` §24; and its Future
is returned. Trust resolution (§3) and backend provisioning occur within that
Task, before any TLS octet is written.

The handshake Future resolves with a fresh secure duplex only after
successful TLS negotiation, certificate-chain verification against the
selected trust (§3), reference-identity verification (§4) and ALPN validation
(§5). A plaintext, partially negotiated or unverified stream is never exposed.

## 3. Trust

Trust is explicit. There is no default trust, no union of trust sources, no
`insecureSkipVerify`, no hidden Authority Information Access fetch, and no
automatic online OCSP or CRL networking.

### 3.1 `Trust.anchors(certificates)`

`certificates` is an Array of DER-encoded X.509 CA certificates as `Bytes`:

- between 1 and 256 certificates;
- each certificate nonempty and at most 65,536 octets; and
- an aggregate of at most 4,194,304 octets.

The Array and each member `Bytes` are snapshotted by the invocation; later
mutation of either cannot alter the descriptor. A peer is verified against
only the explicitly supplied usable CA anchors.

`Trust` constructors reject malformed descriptors and arguments synchronously
under the ordinary standard-object Error rules: an invalid count, size bound,
element shape or DER encoding signals a fresh ordinary `Error`, and no
descriptor or Future is produced.

### 3.2 `Trust.javaCacerts()`

Selects the Java `cacerts` trust material, only by this explicit opt-in. The
selected material is resolved for each admitted handshake. It is never merged
with application anchors or operating-system trust. It is Java runtime
material, not live operating-system trust: under an ahead-of-time compiled
executable, its content may be fixed when the executable is built (§11).
When that material is missing, unreadable, empty or unusable, the handshake
fails with a `TlsError` of kind `trustUnavailable` before any TLS octet is
written and never falls back to another source. When usable selected material
does not verify the peer chain, the kind is `untrustedPeer` (§9).

### 3.3 `Trust.system()`

Is a valid descriptor. This release provides no Linux, Windows, macOS or BSD
operating-system trust adapter, so a handshake selecting it fails with a
`TlsError` of kind `trustUnavailable` before any TLS octet is written. It
never falls back to Java `cacerts` or to any union of trust stores.

## 4. Reference identity and SNI

`serverIdentity` is independent of the TCP destination and of any HTTP `Host`
header. It is admissible when it is either:

- a `String` that is a valid ASCII DNS reference name; or
- a recognized canonical `IpAddress` under `NETWORK.md` §2.

A DNS reference name is an ASCII wire name. Each label is either an ASCII
Non-Reserved LDH (NR-LDH) label or a valid IDNA2008 A-label, both as defined
by RFC 5890. Every label must satisfy the DNS length and character
constraints: ASCII letters, digits and hyphens, no leading or trailing hyphen,
and at most 63 octets per label, 253 octets per name.

Reserved LDH (R-LDH) labels, identified by hyphens in both the third and fourth
positions, are inadmissible unless the label begins with `xn--` (compared
ASCII case-insensitively) and is a valid A-label. A label beginning with
`xn--` is a valid A-label only when all of the following hold:

1. its Punycode payload (RFC 3492) is nonempty and decodes without error, and
   every decoded code point is a Unicode scalar value; a decode that would
   exceed U+10FFFF fails;
2. the decoded U-label contains at least one non-ASCII code point, is in
   Unicode Normalization Form C, does not begin with a combining mark
   (General_Category `Mn`, `Mc` or `Me`), does not begin or end with U+002D,
   and does not have U+002D in both its third and fourth positions
   (RFC 5891 §§4.2.3.1, 4.2.3.2, 5.4);
3. every code point of the U-label has the RFC 5892 derived property value
   PVALID, or is CONTEXTJ or CONTEXTO and satisfies its RFC 5892 Appendix A
   rule; UNASSIGNED and DISALLOWED code points are inadmissible. The derived
   property is computed by the RFC 5892 §3 algorithm, in its precedence, from
   the Unicode Character Database of the implementation's Unicode version;
4. re-encoding the U-label with Punycode yields exactly the lowercase payload,
   so the A-label is canonical.

A DNS name that contains a code point of Bidi_Class `R`, `AL` or `AN` in any
A-label is a bidi domain name; then every label must satisfy the RFC 5893 §2
Bidi Rule, ASCII NR-LDH labels included.

Non-ASCII (U-label) input is rejected, never converted: there is no implicit
Unicode-to-ASCII conversion, UTS #46 mapping, alternate validation scheme or
fallback. The same rules decide whether a presented certificate DNS-ID is
eligible for identity matching; an ineligible DNS-ID never matches and is
skipped without failing the handshake.

A presented `*` is RFC 9525 §6.3 wildcard-pattern syntax, not a DNS label, and
is permitted only as the complete leftmost label of a presented DNS-ID. Every
other label of that DNS-ID is validated as above, including the Bidi Rule when
the DNS-ID is a bidi domain name. The reference identity is always validated
in full, including the label a wildcard matches, before any comparison, so
wildcard matching never admits a name that fails these rules.

A DNS reference has at least two valid labels, is compared ASCII
case-insensitively, and has one optional terminal root dot normalized away.
A numeric IP-literal `String` is rejected; an IP identity is expressed only as
an `IpAddress`.

Certificate identity is RFC 9525 subjectAltName-only:

- a DNS reference matches a subjectAltName DNS-ID;
- an IP reference matches a subjectAltName iPAddress exactly; and
- the subject Common Name is never a fallback.

A DNS-ID wildcard matches only when it occupies the entire leftmost label, and
it matches exactly one reference label. Partial-label, multi-label and
identity-broadening wildcards do not match.

Server Name Indication is sent only for a DNS reference, as its normalized
lowercase ASCII name without the terminal root dot; an A-label is sent in that
form, never as a U-label. An IP reference sends no SNI.

Trust verification (§3) and reference-identity verification must both
succeed. The application remains responsible for aligning the TLS reference
identity with the intended HTTP origin and `Host` authority.

## 5. Protocol versions, cipher suites and ALPN

By default TLS 1.3 is preferred and secure TLS 1.2 is permitted.
`minimumVersion: "1.3"` selects TLS 1.3 only.

SSL, TLS 1.0 and TLS 1.1, static-RSA key exchange, CBC, anonymous, export and
NULL suites, and insecure renegotiation are disabled. The permitted TLS 1.2
suites use ECDHE_RSA or ECDHE_ECDSA key exchange with AES_128_GCM or
AES_256_GCM. The permitted TLS 1.3 suites are TLS_AES_128_GCM_SHA256,
TLS_AES_256_GCM_SHA384 and TLS_CHACHA20_POLY1305_SHA256. The suites in use are
those permitted suites that the backend securely supports. When no compliant
configuration is available, the handshake fails with kind
`backendUnavailable`.

With the default `alpn: "http/1.1"` only HTTP/1.1 is offered; a peer selection
of `http/1.1` or no ALPN selection is accepted, and any other selected protocol
fails with kind `alpn`. `alpn: null` offers no ALPN.

This release has no HTTP/2, no TLS 1.3 0-RTT or early data, no session
resumption and no shared TLS session cache.

## 6. Options

`options` is an ordinary object whose own fields are all optional and limited
to:

| Field | Values | Default |
| --- | --- | --- |
| `minimumVersion` | `"1.2"` or `"1.3"` | `"1.2"` |
| `alpn` | `"http/1.1"` or `null` | `"http/1.1"` |
| `maxHandshakeBytes` | Integer 1 through 2147483647 | `1048576` |
| `maxQueuedWriteBytes` | Integer 1 through 2147483647 | `33554432` |

An unknown own field, a field obtained only by delegation, or an invalid value
makes `options` inadmissible (§2.1).

## 7. Secure duplex byte I/O

The secure duplex satisfies `ByteReadable`, `ByteWritable` and `Closable`
under `BYTE_IO.md` and `IO_CORE.md`. Its `read(maxBytes)` and `write(bytes)`
operations return ordinary non-Task-backed operation Futures; no hidden Task
is created per read or per write.

A read resolves with fresh mutable `Bytes` of length 1 through `maxBytes`, or
with `null` for authenticated clean end of input. Empty `Bytes` never
represent pending input or end of input. Decrypted plaintext that is not yet
returned is retained; cancelling a read after its ciphertext was consumed
never silently drops that plaintext.

A write captures its snapshot before returning and reserves finite retention
for it. When that retention cannot be reserved within `maxQueuedWriteBytes`,
the write is rejected at admission: it contributes no TLS application output
and does not poison an otherwise usable stream. An admitted write that
subsequently fails may have committed an uncertain prefix; no rollback is
promised and no guessed plaintext is replayed. Ordering and end-to-end
backpressure are preserved.

Operations on a retired secure duplex follow the ordinary `IOLifecycleError`
rules of `IO_CORE.md`.

## 8. Closure and custody

A peer `close_notify` establishes authenticated clean read end of input.
Physical end of transport input without authenticated TLS closure is a
`TlsError` of kind `truncated`, never `null`.

`close()` emits the local `close_notify` and does not wait indefinitely for the
peer.

`Client.handshake` borrows `duplex` and never closes it. `Client.handshake.owning`
closes `duplex` exactly once after custody acquisition, on every terminal
path, including handshake failure and cancellation.

When a `handshake.owning` handshake fails with an Error and the required close
of `duplex` then also fails, the handshake Future fails with the original
handshake Error; the close failure does not replace it. When the handshake is
instead cancelled and that close fails, the handshake Future fails with the
close Error, under the cleanup precedence of `../semantics/ERRORS.md`. This
TLS-specific arbitration does not change `ensure`. Closing a secure duplex
follows the wrapper close order and failure precedence of `IO_CORE.md` §9: a
close_notify emission failure prevails over a failure of the owned `duplex`
close, which otherwise is the close failure.

Core Future first-terminal-outcome rules, Actor isolation and the standard I/O
lifecycle errors apply unchanged.

## 9. `TlsError`

`TlsError` is an ordinary Standard Library Error prototype whose immediate
parent is `Error`. Each failure creates a fresh occurrence with the required
own slot `kind`, whose value is exactly one of:

| `kind` | Meaning |
| --- | --- |
| `trustUnavailable` | the selected trust source has no available adapter (§3.3), or the selected Java `cacerts` material is missing, unreadable, empty or unusable (§3.2) |
| `backendUnavailable` | no compliant TLS backend configuration is available (§5) |
| `untrustedPeer` | the peer chain does not verify to the selected anchors |
| `identityMismatch` | the peer certificate does not present the reference identity (§4) |
| `protocolVersion` | no permitted protocol version could be negotiated |
| `alpn` | the peer selected an application protocol other than the accepted ones (§5) |
| `handshakeFailure` | any other handshake failure |
| `protocol` | a TLS protocol violation after the handshake |
| `limit` | a bound of §10 was exceeded |
| `truncated` | transport input ended without authenticated closure (§8) |

When an underlying transport Error is the cause of a failure, that Error is
preserved rather than replaced by a `TlsError`. Invalid admission remains
`InvalidIOArgument` (§2.1). TLS failures are never represented as
`ClientError`, `HttpError` or an HTTP status response.

## 10. Limits

| Bound | Value |
| --- | --- |
| TLS plaintext record | 16,384 octets |
| encrypted record | 18,432 octets |
| receive/unwrap buffer | 65,536 octets |
| handshake accumulation | `maxHandshakeBytes` |
| queued write snapshots | `maxQueuedWriteBytes` |

## 11. Implementation and portability

The initial backend is the host Java TLS engine (`SSLEngine`), privately
provisioned, lazily created and Actor-local. Implementations may use any
engine that preserves the observable contract of this document; the engine,
the trust source and the path verifier remain separate private components.
Mutable live TLS engine or transport state is never shared across Actors or
Contexts.

When the implementation is an ahead-of-time compiled executable, provider
reachability, the content of Java `cacerts` and certificate distrust lists may
be fixed when the executable is built; such an executable is not required to
observe later operating-system certificate additions, removals or distrust
updates. `Trust.javaCacerts()` is never live operating-system trust.

## 12. HTTP composition

A secure duplex composes with the unchanged `std:http/Client`
`Client.exchangeOne(duplex, request, onResponse, limits)` and
`Client.exchangeOne.owning(...)` of `HTTP.md` §16. This facility modifies no
HTTP framing, parses no URL and adds no automatic TLS to any HTTP facility.

## 13. Deferred

The following remain outside this contract and require separate explicit
design: TLS server operation, HTTPS hosting for `std:http/Host`, mutual TLS,
operating-system trust adapters, alternative TLS engines, session resumption,
0-RTT, DNS/URL convenience and HTTP/2. Unicode (U-label) reference input and
any Unicode-to-ASCII conversion are outside this contract (§4).
