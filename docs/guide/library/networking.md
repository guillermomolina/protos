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
| `network` | Live authority for TCP: `connectTcp(endpoint)` and `listenTcp(ipVersion:, address:, port:)` | A bootstrap-local host grant |
| `resolver` | Live authority to turn a hostname into addresses | A separate bootstrap-local host grant |
| `IpAddress` | A transferable, immutable address value | `std:network/ip_addresses`, or a Resolver result |
| `IpEndpoint` | A numeric address plus a port | `std:network/ip_endpoints` |
| TLS trust | Which certificate authorities a TLS client accepts | An explicit `std:tls/client` `Trust` choice |

A `network` grant does not grant Resolver authority, and a `resolver` grant
does not grant `network`. Neither selects any TLS trust.

## Module imports

```protos
IpAddress: import("std:network/ip_addresses").IpAddress
IpEndpoint: import("std:network/ip_endpoints").IpEndpoint
Resolvers: import("std:network/resolvers")
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
IpEndpoint: import("std:network/ip_endpoints").IpEndpoint

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

Manual commands (real DNS and connectivity depend on the host):

```sh
bin/protos --allow-resolver protos/examples/io/resolver-only.protos
bin/protos --allow-network --allow-resolver protos/examples/io/hostname-resolution.protos
bin/protos --allow-network --allow-resolver protos/examples/io/http-url-client.protos
bin/protos --allow-network --allow-resolver protos/examples/io/tls-client-over-tcp.protos
```

These examples are outside mandatory deterministic tests. Importing a module
never grants either capability.

## Ending a TCP connection

Choose the operation for the boundary you need:

| Operation | Effect |
|---|---|
| `pending.cancel()` | Requests cancellation of that operation; a committed write cannot become zero-effect cancellation |
| `connection.close()` | Stops admission and waits for already committed operation aftermath before ordinary release |
| `connection.shutdownRead()` | Permanently ends input; uncommitted earlier reads become local `null` |
| `connection.shutdownWrite()` | Ends output after the preceding clean output frontier; input remains usable |
| `connection.abort()` | Irreversibly stops new effects and starts transport retirement, including escalation of pending close |

For example, an application holding its own connection may explicitly retire
it when it decides pending output is no longer useful:

```protos
pending: connection.write(Encoding.UTF8.encode("request"))
retirement: connection.abort()
retirement.value()    // The exact connection, after safe release and required aftermath
```

The write may already have contributed a prefix. Abort does not undo it, and a
previously admitted final attempt that completes the write successfully keeps
that success. An incomplete committed write fails with `IOError`; uncommitted
work terminated by abort fails with `IOLifecycleError`. Do not blindly retry
failed output. Every close/abort call returns a fresh Future observing the same
retirement; cancelling one cannot reverse retirement. Endpoint snapshots and
ordinary object slots remain available.

Abort promises neither TCP RST nor remote receipt or a real-time completion
bound. It is an explicit application action, with no automatic timeout policy.
It belongs only to authentic local TcpConnections. Borrowed HTTP/TLS services
receive no destructive authority, owning cleanup does not automatically abort,
and `host.shutdown("abort")` retains its cooperative service-cancellation
contract. See [`NETWORK.md` §9.1](../../../spec/io/NETWORK.md#91-explicit-transport-abort-d270-d-d270-d1-b).

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

- The standalone CLI grants neither `network` nor `resolver` by default.
  `--allow-network` grants only initial RootActor host TCP authority for
  direct-file, `-e`, REPL, `run` and `debug` application execution. There is
  an independent `--allow-resolver` flag for name resolution in those same
  application modes and SOURCE/MODULE services.
  See [CLI network and service options](../tools/cli.md#network-authority-and-rootactor-service-mode-d247-c--i154)
  and [Embedding Protos in Java](../embedding/java.md).
- Imported modules do not inherit those bootstrap-local slots. Pass the
  capabilities as arguments, as `std:http/url_client` expects.
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
