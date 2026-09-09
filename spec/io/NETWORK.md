# Protos Networking v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-09

This document is the primary normative owner of portable network authority,
numeric IP-address/endpoint semantics, and the initial TCP connection/listener
capability model introduced by D047 / specification revision `0.1.388`.
General byte-I/O, Future commitment/cancellation, lifecycle, and half-close
semantics remain owned by `IO_CORE.md` and `BYTE_IO.md`; Process bootstrap-local
provisioning remains owned by `PROCESS_IO.md`; Actor and P transfer boundaries
remain owned by their concurrency specifications.

## 1. Scope and governing model

Networking is capability-oriented. Knowing an address or endpoint is data and
never grants authority to communicate with it. A concrete `Network` object is a
live authority capability whose effective routing/namespace/policy domain is
selected by the host or by a future explicitly standardized derivation
facility.

The initial portable model standardizes numeric IP data and TCP only. DNS/name
resolution, Happy Eyeballs, UDP/datagrams, NetworkInterface enumeration, formal
Network-policy introspection/attenuation, TLS, QUIC, HTTP, WebSocket, Unix-domain
sockets, raw sockets, service discovery, generic socket options, and
socket-local deadline/timeout APIs are outside this revision.

No network authority is ambient by implication. Importing a module, knowing an
`IpAddress`, possessing `Process`, creating an Actor, or creating P work does not
confer a `Network` capability.

## 2. `IpAddress` semantic data

An `IpAddress` is ordinary frozen Protos data. Its canonical semantic state is:

```text
version   // exactly Integer 4 or Integer 6
bits      // exact Integer address bits
```

For version 4, `bits` is in `0 .. 2^32-1`. For version 6, `bits` is in
`0 .. 2^128-1`.

D047 creates no new Core value-identity family. Two independently materialized
standard IP-address objects with the same version and bits may be structurally
equal without being identical under `===`. Standard `IpAddress` equality and
hashing depend only on version and bits and execute no network, DNS, routing, or
host-interface operation.

The address is data, not authority. It contains no hostname, resolver, native
socket address, interface index/name, route handle, Network capability, or host
resource identity.

D048 / specification revision `0.1.391` closes the bounded public
construction/recognition checkpoint. `IpAddress` is a standard frozen prelude
factory/prototype that carries no Network authority. Its canonical construction
uses ordinary polymorphic invocation:

```text
IpAddress(version, bits) -> IpAddress
```

The standard factory behavior requires the invocation receiver to be exactly the
canonical standard `IpAddress` factory/prototype and requires exactly two supplied
arguments. `version` must belong to the ordinary unbounded `Integer` family and
be exactly 4 or 6. `bits` must belong to that same ordinary unbounded `Integer`
family and must be in `0 .. 2^32-1` for version 4 or `0 .. 2^128-1` for version
6. A fixed-width numeric value is not accepted merely because its mathematical
value is in range. Invalid construction signals a fresh ordinary `Error`
synchronously and performs no DNS, routing, Network or host I/O.

Every successful invocation returns one fresh ordinary frozen object whose
immediate delegation parent is exactly the canonical standard `IpAddress`
factory/prototype and whose own local slot names are exactly `version` and
`bits`, containing the validated semantic values. These are ordinary public
member-readable data slots. Factory provenance is not itself semantic membership:
an ordinary program object constructed through the normal object/delegation/freeze
mechanisms is a recognized standard `IpAddress` exactly when it is frozen, its
immediate parent is exactly the canonical standard `IpAddress` factory/prototype,
its own local slot names are exactly `version` and `bits`, and those slot values
satisfy the same canonical numeric invariants. Extra own slots, merely transitive
ancestry, an open or closed-but-not-frozen object, or coincidental shape under a
different parent is not recognized.

The standard factory/prototype additionally exposes:

```text
IpAddress.recognizes(value) -> true | false
```

For the exact canonical receiver and exactly one supplied argument, this
predicate accepts any candidate and returns canonical `true` exactly for the
recognized standard shape above, otherwise canonical `false`. Recognition
observes the frozen state, immediate parent, exact own-slot names and required
semantic-family/range state directly; it invokes no candidate getter, callback,
equality or hashing behavior and performs no network/host operation. Invalid
receiver or arity follows the ordinary standard receiver/arity Error rule.

Standard `IpAddress` equality/hash behavior requires a recognized receiver,
compares/hashes only canonical `version` plus `bits`, and yields canonical
`false` rather than signaling merely because the other argument is not a
recognized `IpAddress`. `===` remains ordinary object identity, so independently
constructed equal addresses remain distinct identities.

## 3. `IpEndpoint` semantic data

An `IpEndpoint` is ordinary frozen Protos data whose canonical semantic state is:

```text
address   // IpAddress
port      // Integer 1..65535
```

Standard endpoint equality/hash are structural by address plus port. `===`
remains ordinary object identity. An endpoint contains no hostname, DNS result
cache, interface/scope identifier, transport-protocol tag, Network authority,
TLS identity, proxy identity, or native handle.

Port zero is not a standard `IpEndpoint` value in this initial model. Automatic
local-port selection is an acquisition request and is represented separately by
the listen request below rather than by overloading endpoint data with a verb.

D048 / specification revision `0.1.391` also closes the endpoint
construction/recognition surface. `IpEndpoint` is a standard frozen prelude
factory/prototype carrying no Network authority and is constructed through
ordinary invocation:

```text
IpEndpoint(address, port) -> IpEndpoint
```

The standard factory behavior requires the invocation receiver to be exactly the
canonical `IpEndpoint` factory/prototype and exactly two supplied arguments.
`address` must be a recognized standard `IpAddress` under section 2. `port` must
belong to the ordinary unbounded `Integer` family and be in `1 .. 65535`; a
fixed-width numeric value is not accepted merely because its mathematical value
is in range. Invalid construction signals a fresh ordinary `Error` synchronously
and performs no network/DNS/host effect.

Each success returns one fresh ordinary frozen object whose immediate delegation
parent is exactly canonical `IpEndpoint` and whose own local slot names are
exactly `address` and `port`. The `address` slot retains the exact supplied
recognized `IpAddress` object; `port` retains the validated Integer. Both are
ordinary public member-readable data slots.

A candidate is recognized as a standard `IpEndpoint` exactly when it is an
ordinary frozen object, its immediate parent is exactly canonical `IpEndpoint`,
its own local slots are exactly `address` and `port`, its `address` is a
recognized standard `IpAddress`, and its `port` is an ordinary unbounded Integer
in `1 .. 65535`. Factory provenance is unnecessary; extra own state,
transitive-only ancestry, mutable state or coincidental shape under another
parent is insufficient.

The standard factory/prototype exposes:

```text
IpEndpoint.recognizes(value) -> true | false
```

with the same direct, callback-free, host-effect-free recognition rule as
`IpAddress.recognizes`. Standard endpoint equality/hash requires a recognized
receiver and depends only on recognized `address` plus `port`; an unrecognized
other argument compares false and `===` remains ordinary object identity.

`IpAddress` and `IpEndpoint` are data suitable for ordinary value transfer under
the applicable Actor/P snapshot rules once their standard construction contract
is implemented. Such transfer never transfers Network routing state or
communication authority.

## 4. IPv4, IPv6, and scope

IPv4 and IPv6 remain semantically distinct. Core performs no implicit
IPv4-mapped-IPv6 normalization and defines no host-default dual-stack listener
behavior. A version-6 listen request is semantically IPv6-only; a version-4
request is IPv4-only.

IPv6 routing/interface scope belongs to the `Network` authority domain rather
than to portable `IpAddress` or `IpEndpoint` identity. For an address that
requires scope, an operation may proceed only when the supplied `Network`
capability provides one unambiguous authorized interpretation. If the capability
admits multiple incompatible interpretations, the operation fails rather than
silently selecting a host interface.

A future interface/scoped-Network facility may narrow authority without changing
`IpAddress` or `IpEndpoint` representation/equality.

## 5. `Network` capability

`Network` is the standard prototype of live network-authority capabilities. A
concrete Network may represent a complete network domain, a VPC/VRF/namespace,
one interface-scoped domain, one service, one permitted destination set, or
another non-amplifying policy domain. The base portable API does not require the
policy to be enumerable as CIDRs, ports, interfaces, or a bitmask.

The effective authority of a Network never grows merely because one of its
standard operations is invoked. D047 introduces no public `NetworkPolicy`,
`permissions()`, CIDR/port introspection, generic `restrict()` primitive, or
firewall DSL. Hosts may provision already-restricted capabilities. Ordinary
Protos encapsulation may expose a narrower application mechanism around a
broader capability without granting the wrapped capability itself.

The initial TCP acquisition operations are conceptually:

```text
network.connectTcp(endpoint)     -> Future<TcpConnection>
network.listenTcp(localRequest)  -> Future<TcpListener>
```

After successful ordinary dispatch, argument/request semantic invalidity follows
the existing Future-returning I/O validation rule: the operation result is a
failed Future and no backend network effect attributable to that invalid request
occurs.

No hostname/String-to-endpoint coercion or DNS lookup is performed by either
operation.

## 6. TCP connect acquisition

`connectTcp(endpoint)` accepts a concrete numeric `IpEndpoint` under the standard
recognition contract selected by D048 / specification revision `0.1.391`.
The endpoint version remains explicit.

Successful resolution transfers one fresh live `TcpConnection` capability to
the caller. The connection's logical remote endpoint equals the supplied endpoint
under standard structural endpoint equality; the logical local endpoint is the
endpoint selected within the Network authority domain for that established flow.

Cancellation of a still-acquiring Future may produce the ordinary Future
`cancelled` outcome only under the general I/O cancellation/commitment rules.
A cancelled acquisition guarantees that no `TcpConnection` capability is
transferred to the caller. It does **not** promise that no physical preparatory
network activity was externally observable; address resolution at lower host
layers, ARP/neighbor discovery, routing probes, TCP SYN traffic, or equivalent
backend preparation may already have occurred.

If backend work later obtains a live connection after the caller can no longer
receive it, producer/runtime custody must release that untransferred resource.
No late resource may be abandoned merely because the Future already terminalized.

## 7. TCP local-listen request

`listenTcp(localRequest)` accepts an ordinary Protos object whose relevant local
slots are exactly:

```text
ipVersion   // exactly Integer 4 or Integer 6
address     // matching-version IpAddress or canonical null
port        // Integer 1..65535 or canonical null
```

All three slots are required and extra local slots make the request invalid.
Delegated slots do not supply these option fields. `address` when non-null must
match `ipVersion`. `address: null` means that the request supplies no single
local-address constraint beyond the requested IP version and the authority of
the supplied Network. `port: null` requests selection of an available non-zero
local port.

The ordinary values are validated and captured before acquisition effects
attributable to the request. Neither unspecified IP bit-patterns nor integer zero
are reinterpreted as acquisition commands. Host wildcard/ephemeral conventions
are backend details.

Successful resolution transfers one fresh live `TcpListener` capability.
Cancellation/late-resource custody follows the same acquisition rule as TCP
connect.

The initial portable model defines no backlog option. Implementations may use
bounded admission/queues/backpressure while preserving the observable Future,
accept, close and resource-custody contracts.

## 8. `TcpListener`

A `TcpListener` is a live identity-bearing capability and satisfies `Closable`.
Its initial operations include:

```text
listener.accept()     -> Future<TcpConnection>
listener.localPort()  -> Integer
```

`localPort()` is synchronous observation of the actual acquired non-zero local
port and performs no network acquisition.

The initial Core deliberately does not require a single
`listener.localEndpoint()`: a listener request with `address: null` may cover
multiple local addresses, so inventing one endpoint would misrepresent the
logical listening domain.

Multiple `accept()` Futures may be pending concurrently on one logical listener.
Core imposes no one-pending-accept restriction, no owner-thread affinity, and no
portable global acceptance order beyond the ordering actually established by
the logical listener/backend. Each successful accept transfers one fresh
`TcpConnection` capability exactly once.

Listener close/cancellation composes with the existing `Closable` lifecycle and
Future rules. No host file descriptor, selector registration, event-loop token,
carrier identity, or queue implementation becomes Protos-visible identity.

## 9. `TcpConnection`

A `TcpConnection` is a live identity-bearing capability satisfying exactly the
initial portable I/O capability surface required by TCP:

```text
ByteReadable
ByteWritable
Closable
ReadShutdown
WriteShutdown
```

It does not thereby satisfy `Flushable`, `Syncable`, `ByteSeekable`, `ByteSized`,
or `Truncatable`. TCP is an ordered byte flow, so the existing byte-I/O ordering,
write contribution, cancellation, close, and half-close semantics apply without
a parallel network-stream universe.

It additionally exposes synchronous data snapshots:

```text
connection.localEndpoint()   -> IpEndpoint
connection.remoteEndpoint()  -> IpEndpoint
```

These describe the logical TCP flow presented by the Network capability. They do
not promise discovery of an underlay address, NAT predecessor, proxy client
identity, route, interface, fd/socket handle, or physical transport endpoint that
the backend does not expose as the logical flow.

A successful byte write means only the contribution guarantee already defined by
Core byte I/O; it does not imply remote-application receipt and adds no portable
`flush()` delivery promise.

## 10. Error portability

The initial TCP surface uses the existing portable I/O Error categories. It does
not expose POSIX errno values, Winsock status codes, Java exception classes, or
native backend types as standard Protos Error ancestry.

This does not freeze network errors forever. A future narrower network Error
category must be justified by a portable semantic distinction useful to Protos
programs, not merely by a host status-code taxonomy.

## 11. Actor, P, and distributed-runtime boundary

`Network`, `TcpConnection`, and `TcpListener` are live resource capabilities and
have no Actor-transfer or P-transfer contract in this initial model. Attempting
to move them through a boundary that accepts only ordinary transferable values
fails under that boundary's existing non-transferable-value rules.

Core defines no Erlang-style active socket mode that injects TCP bytes directly
into an Actor mailbox. An Actor reads a connection using ordinary byte-I/O Future
operations and may then send application messages explicitly.

The distributed Protos runtime may use local IPC or network transports
internally. Those mechanisms do not constitute a user `Network` capability and
do not grant user networking authority. Future resource proxies/delegation must
separately define semantic identity, authority, ordering, cancellation, lifetime
and failure behavior before live networking capabilities may cross such a
boundary.

## 12. Implementation freedom and scale

The semantics do not expose or require epoll, kqueue, io_uring, IOCP, Java NIO,
Network.framework, one thread per connection, one Actor per connection, one event
loop, or any other host scheduling architecture. An implementation may use any
of them while preserving the same Future/I/O/lifecycle/authority observations.

A listener must not require a semantic single-accept bottleneck. A trivial
program that never receives Network authority pays no conceptual requirement to
create DNS caches, interface registries, event-loop objects, distributed proxies,
TLS state, UDP state, or network policy structures.

## 13. Deferred facilities

D048 / specification revision `0.1.391` resolves the former
`IpAddress` / `IpEndpoint` construction and recognition checkpoint. The following
remain separate explicit future designs:

- DNS/name resolution and resolver authority;
- Happy Eyeballs and general time/delay composition;
- UDP/datagram operations and truncation/size rules;
- public NetworkInterface discovery/selection;
- formal transferable Network attenuation/policy algebra;
- TLS, QUIC, HTTP and WebSocket;
- Unix-domain/raw sockets;
- service discovery;
- generic socket options and socket-local deadlines/timeouts.
