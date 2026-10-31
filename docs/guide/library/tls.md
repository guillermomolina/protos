# TLS client: `std:tls/Client`

`std:tls/Client` authenticates a TLS client session over a byte duplex you
already have. It is written in Protos over a private runtime-provisioned
backend and composes with the ordinary byte-I/O protocols, Futures and the
HTTP client. The normative contract is [`spec/io/TLS.md`](../../../spec/io/TLS.md)
(D220–S12); if this guide and the specification disagree, the specification
wins.

## Choosing TLS

| Module | Use it when |
|---|---|
| `std:tls/Client` | You already have a connected byte duplex (normally a TCP connection) and want an authenticated secure duplex over it |
| `std:http/Client` | You already have a duplex (plain or secure) and want exactly one HTTP/1.1 exchange on it |
| `std:http/UrlClient` | You want one complete HTTP or HTTPS request to an absolute URL, composed for you from the `network`, `resolver` and `trust` you pass explicitly |

Importing `std:tls/Client` grants nothing. It acquires no network, no name
resolution, no URL parsing, no clock and no default trust store. Every
authority it uses comes from its arguments. See [Explicit networking and name
resolution](networking.md) for where the duplex comes from, and the
[HTTP guide](http.md) for the HTTP modules.

## API

```text
Client.handshake(duplex, serverIdentity, trust, options)        -> Future<secure duplex>
Client.handshake.owning(duplex, serverIdentity, trust, options) -> Future<secure duplex>

Trust.anchors(certificates)
Trust.javaCacerts()
Trust.system()
```

```protos
TLS: import("std:tls/Client")
Client: TLS.Client
Trust: TLS.Trust
```

The module also exports `TlsError`, the prototype of every TLS failure.

Both handshakes validate every argument before any effect, then answer a fresh
Future. The Future resolves to a secure duplex only after the peer certificate
chain verified against `trust` *and* the peer certificate presented
`serverIdentity`. An inadmissible call answers an already failed Future with
`InvalidIOArgument`, without starting a handshake.

- **Borrowing** (`Client.handshake`): `duplex` needs `read` and `write`. The TLS
  client never closes it; closing the transport stays your job, after you have
  closed the secure duplex.
- **Owning** (`Client.handshake.owning`): `duplex` must also be `Closable`. Once
  the call is admitted, the TLS client closes `duplex` exactly once: on a
  failed or cancelled handshake, or when you close the secure duplex.

## Explicit trust

There is no default trust, no hidden fallback and no `insecureSkipVerify`
option. Every handshake names its trust source:

- `Trust.anchors(certificates)` takes an Array of DER-encoded X.509 CA
  certificates, each a `Bytes` (1 to 256 certificates, each at most 65536
  octets, 4194304 in total). The Array and every `Bytes` are snapshotted when
  you call it, so later mutation has no effect. Use it for a private CA or a
  pinned set of roots that you distribute with your application.
- `Trust.javaCacerts()` explicitly selects the Java `cacerts` material of the
  running implementation, read for each handshake. Missing or unusable material
  fails the handshake with `trustUnavailable`.
- `Trust.system()` is accepted, but the current implementation has no
  operating-system trust adapter, so every handshake using it fails with
  `trustUnavailable`.

A malformed trust argument signals an `Error` synchronously. Because trust is
always explicit, deploying a Protos HTTPS client means deciding which roots you
trust: ship anchors with the application, or rely on the `cacerts` of the
runtime you deploy (see [Portability and limitations](#portability-and-limitations)).

## Identity verification

`serverIdentity` is the *reference identity*: either the original DNS hostname
you meant to reach, or a canonical `IpAddress`.

- A DNS name must use the ASCII wire format: NR-LDH labels or strict IDNA2008
  A-labels (`xn--...`). Unicode U-label input is rejected, never converted.
- The peer certificate must present the identity in its Subject Alternative
  Name; there is no Common Name fallback.
- A DNS reference identity is also sent as SNI. A numeric `IpAddress` reference
  identity sends no DNS SNI.
- Resolving a hostname to an `IpAddress` never changes the reference identity:
  connect to the resolved address, but authenticate the original hostname.

## Handshake options

`options` is an ordinary object; every field is optional, and an unknown field,
a field obtained only by delegation, or an invalid value makes the call
inadmissible. Pass `{}` for the defaults.

| Option | Values | Default |
|---|---|---|
| `minimumVersion` | `"1.2"` or `"1.3"` | `"1.2"` |
| `alpn` | `"http/1.1"` or `null` | `"http/1.1"` |
| `maxHandshakeBytes` | Integer 1 through 2147483647 | `1048576` |
| `maxQueuedWriteBytes` | Integer 1 through 2147483647 | `33554432` |

By default TLS 1.3 is preferred and secure TLS 1.2 remains permitted;
`minimumVersion: "1.3"` selects TLS 1.3 only. With the default ALPN only
HTTP/1.1 is offered; `alpn: null` offers no ALPN.

## Secure duplex lifecycle

The secure duplex is an ordinary `ByteReadable`, `ByteWritable` and `Closable`
value with `read(maxBytes)`, `write(bytes)` and `close()`:

- `read` answers fresh `Bytes`, or `null` for an *authenticated* end of input
  (the peer's `close_notify`). A transport end of input without that
  authenticated closure is a `TlsError` with `kind == "truncated"`, never
  `null`.
- Cancelling a read never silently discards plaintext that was already
  decrypted; it is returned by a later read.
- A write snapshots its `Bytes` before returning and is bounded by
  `maxQueuedWriteBytes`. A write that cannot be admitted within that bound is
  rejected without poisoning the stream. An admitted write that later fails
  follows the normal I/O commitment rules: an uncertain prefix may have been
  sent, and nothing is rolled back.
- `close()` sends the local `close_notify` and does not wait indefinitely for
  the peer. Closing a secure duplex from `handshake.owning` also closes the
  underlying transport; one from the borrowing `handshake` leaves it open.
- Operations after close follow the ordinary `IOLifecycleError` rules.

## Errors

A TLS failure is a fresh `TlsError` occurrence whose `kind` is one of:

| `kind` | Meaning |
|---|---|
| `trustUnavailable` | The selected trust source has no adapter, or Java `cacerts` is missing or unusable |
| `backendUnavailable` | No compliant TLS backend configuration is available |
| `untrustedPeer` | The peer chain does not verify to the selected anchors |
| `identityMismatch` | The peer certificate does not present the reference identity |
| `protocolVersion` | No permitted protocol version could be negotiated |
| `alpn` | The peer selected an application protocol other than the accepted ones |
| `handshakeFailure` | Any other handshake failure |
| `protocol` | A TLS protocol violation after the handshake |
| `limit` | A TLS bound was exceeded |
| `truncated` | Transport input ended without authenticated closure |

Keep these distinctions in mind when handling failures:

- When the underlying transport fails, you receive *that transport Error*, not
  a `TlsError`.
- An inadmissible call (wrong duplex, malformed identity, unknown option) is
  `InvalidIOArgument`.
- TLS failures are never `ClientError`, `HttpError` or an HTTP status
  response; an HTTP 4xx or 5xx over a secure duplex is an ordinary response.

## Composition

A direct HTTPS exchange is a sequence of explicit steps, each owned by an
existing facility:

1. Obtain a numeric TCP endpoint: resolve the hostname with an explicitly
   granted `resolver` and build an `IpEndpoint`.
2. Connect with the explicitly granted `network`.
3. Call `Client.handshake` (or `.owning`) with the **original hostname** and
   an explicitly selected trust.
4. Exchange application bytes, or compose `std:http/Client` over the secure
   duplex.
5. Close what you own: the secure duplex first, then a borrowed transport.

The fragment below assumes `connection` is a TCP connection you opened and
`originalHostname` the name you resolved; `request` and `onResponse` are as in
the [HTTP guide](http.md#client-one-exchange-on-one-duplex). It is not a
standalone program:

```protos
TLS: import("std:tls/Client")
HttpClient: import("std:http/Client").Client

received: (() => {
    secure: TLS.Client.handshake(connection, originalHostname, TLS.Trust.javaCacerts(), {}).value()
    (() => {
        HttpClient.exchangeOne(secure, request, onResponse, null).value()
    }).ensure(() => {
        secure.close().value()
    })
}).ensure(() => {
    connection.close().value()   // borrowed: still ours to close
})
```

The complete, host-dependent program is
[`examples/io/tls-client-over-tcp.protos`](../../../protos/examples/io/tls-client-over-tcp.protos).
When you only need one URL-based request,
[`examples/io/http-url-client.protos`](../../../protos/examples/io/http-url-client.protos)
lets `std:http/UrlClient` perform the same composition. Both need a Java host
that grants `network` and `resolver`; the standalone CLI grants neither.

## Portability and limitations

The current backend is the host Java TLS engine, created privately and
Actor-locally. The portable JVM and Native distributions follow the same
contract, but they do not promise identical trust-store freshness: a Native
executable may capture the Java `cacerts` content, provider set and distrust
lists when the image is built, and does not observe later operating-system
certificate updates. `Trust.javaCacerts()` is never live operating-system
trust. When freshness matters, pass explicit `Trust.anchors(...)` or rebuild
and redeploy.

Not provided: TLS server operation and HTTPS hosting in `Server` or `Host`,
mutual TLS, operating-system trust adapters, HTTP/2, automatic DNS resolution,
retries, connection pooling, session resumption, and any implicit networking.

## Normative references

- [`spec/io/TLS.md`](../../../spec/io/TLS.md): the complete TLS client contract.
- [`spec/io/NETWORK.md`](../../../spec/io/NETWORK.md): TCP and Resolver authority.
- [`spec/io/HTTP.md`](../../../spec/io/HTTP.md) §16 and §18: HTTP Client and UrlClient.
- [`spec/io/IO_CORE.md`](../../../spec/io/IO_CORE.md) and
  [`spec/io/BYTE_IO.md`](../../../spec/io/BYTE_IO.md): byte I/O, commitment and close.
