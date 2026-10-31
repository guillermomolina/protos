# Explicit networking and name resolution

This guide is the practical companion of
[chapter 11](../11-process-io-filesystems-and-authority.md#name-resolution-is-a-separate-capability),
which defines the authority model. It shows how the pieces compose in a Protos
program. The normative contract is [`spec/io/NETWORK.md`](../../../spec/io/NETWORK.md)
(§14 for name resolution); if this guide and the specification disagree, the
specification wins.

## Capability model

Protos networking is a set of separate values, each with one job:

| Value | What it is | Where it comes from |
|---|---|---|
| `network` | Live authority for TCP: `connectTcp` and `listenTcp` | A bootstrap-local host grant |
| `resolver` | Live authority to turn a hostname into addresses | A separate bootstrap-local host grant |
| `IpAddress` | A transferable, immutable address value | `std:network/IpAddresses`, or a Resolver result |
| `IpEndpoint` | A numeric address plus a port | `std:network/IpEndpoints` |
| TLS trust | Which certificate authorities a TLS client accepts | An explicit `std:tls/Client` `Trust` choice |

A `network` grant does not grant Resolver authority, and a `resolver` grant
does not grant `network`. Neither selects any TLS trust.

## Module imports

```protos
IpAddress: import("std:network/IpAddresses").IpAddress
IpEndpoint: import("std:network/IpEndpoints").IpEndpoint
Resolvers: import("std:network/Resolvers")
```

`Resolvers` exports the authority-free `Resolver` protocol prototype and
`ResolutionError`. It does not manufacture a live Resolver: importing it
starts no name service. The live `resolver` you call exists only as a
bootstrap-local slot of the RootActor initial module, and only when the host
granted it.

## Resolve, select, connect

```text
resolver.lookupAddresses(name) -> Future<Array<IpAddress>>
resolver.close()               -> Future<Resolver>
```

A successful lookup is a fresh frozen Array of 1 to 64 distinct `IpAddress`
values. Their order is advisory: it promises neither reachability nor a
preferred address family. Resolution never connects. `network.connectTcp`
accepts only a numeric `IpEndpoint`, never a hostname, so the application
chooses an address, or decides explicitly how to try several, and connects:

```protos
IpEndpoint: import("std:network/IpEndpoints").IpEndpoint

originalHostname: "example.org"
addresses: (() => {
    resolver.lookupAddresses(originalHostname).value()
}).ensure(() => {
    resolver.close().value()
})
connection: network.connectTcp(IpEndpoint(addresses[0], 443)).value()
```

This fragment is an incomplete composition: it uses the host-granted `network`
and `resolver` bindings and leaves the connection open. Your application owns
that connection and must close it when finished, preferably with `ensure`, as
[`hostname-resolution.protos`](../../../protos/examples/io/hostname-resolution.protos)
and [`tls-client-over-tcp.protos`](../../../protos/examples/io/tls-client-over-tcp.protos)
do. Keep
`originalHostname`: it remains the TLS reference identity and the HTTP `Host`
value after you connect to a numeric address. The complete program is
[`examples/io/hostname-resolution.protos`](../../../protos/examples/io/hostname-resolution.protos).

## Name restrictions and failures

`lookupAddresses` accepts a String of ASCII NR-LDH labels or strict IDNA2008
A-labels (`xn--...`), compared case-insensitively, optionally ending with one
root dot. One-label names such as `localhost` are allowed; labels have 1 to 63
octets and the name at most 253. Unicode U-labels, numeric address text such as
`"127.0.0.1"`, and any other name fail with `InvalidIOArgument` before any
name-service work. No search domain is ever appended.

A resolution failure is a fresh `ResolutionError` whose `kind` is:

| `kind` | Meaning |
|---|---|
| `noAddress` | The name has no usable address |
| `unavailable` | The name service could not answer |
| `denied` | The host's resolver policy refused the lookup |
| `limit` | More than 64 distinct addresses; the result is never truncated |

The lookup Future is an ordinary operation Future, not a Task: cancelling it
before its result is published suppresses that result. `close()` is
idempotent, makes later lookups fail with `IOLifecycleError`, and closes no
Network, connection, HTTP or TLS resource.

## Authority and Actors

- The standalone CLI (`protos file.protos`, `protos -e`, the REPL) grants
  neither `network` nor `resolver`, and no flag grants them. A Java host grants
  them; see [Embedding Protos in a Java application](../embedding/java.md).
- Imported modules do not inherit those bootstrap-local slots. Pass the
  capabilities as arguments, as `std:http/UrlClient` expects.
- Live `network` and `resolver` capabilities are not transferable Actor
  messages and cannot enter P. Resolved `IpAddress` and `IpEndpoint` values
  can.
- A RootActor started as a message service can keep its host-granted
  authority and answer Worker requests by message. Handing a Worker a GroupRef
  to that service is not handing it `network`: the Worker can only ask.

See [chapter 11](../11-process-io-filesystems-and-authority.md#the-rootactor-service-entry-receives-the-same-grants),
the [HTTP guide](http.md#host-continuous-hosting-with-workers) for the hosting pattern,
and the [TLS guide](tls.md) for authenticating the connection you opened.

## What's deliberately missing

There is no general DNS record API, no automatic connect-by-name, no Happy
Eyeballs, no implicit timeout, no DNSSEC and no implicit capability
acquisition. Retry policy and the choice among several resolved addresses
belong to your application's composition. Futures alone provide no clock or
timer: a timeout needs an explicitly available timing mechanism, and the
current Network and Resolver API offers no implicit timeout or deadline
facility.

## Normative references

- [`spec/io/NETWORK.md`](../../../spec/io/NETWORK.md): TCP, listeners, `network`
  and Resolver authority.
- [`spec/io/PROCESS_IO.md`](../../../spec/io/PROCESS_IO.md): bootstrap-local
  grants.
- [`spec/concurrency/ACTORS.md`](../../../spec/concurrency/ACTORS.md): transfer
  across Actors.
- [`spec/io/TLS.md`](../../../spec/io/TLS.md): reference identity.
