# LIB005 — Networking Standard Library design

Status: **LIB005-0 DESIGN CLOSED — bounded initial surface approved**

Owning work item: GitHub Issue `#54` — `LIB005 — Networking`

Nature: project Standard Library design record; **non-normative**

Explicit project-owner approval: **2026-09-10**

Normative dependencies: D047 / specification revision `0.1.388`; D048 /
specification revision `0.1.391`; D052 / specification revision `0.1.393`;
`spec/io/NETWORK.md` and the existing Core String/Error/Object/Module contracts.

Implementation prerequisite: `I028 — Core Networking Foundation` — **CLOSED**.

## Purpose

This record closes the `LIB005-0` design/selection checkpoint for the first
ordinary-Protos networking convenience surface.

The selected library is deliberately a **data/representation convenience layer**,
not a second socket API. Core already owns explicit `Network` authority,
`Network.connectTcp(IpEndpoint)`, `Network.listenTcp(localRequest)`,
`TcpConnection`, `TcpListener`, Future/cancellation behavior, byte I/O,
resource lifecycle, IPv4/IPv6 distinction and Actor/P transfer confinement.

LIB005 must preserve those boundaries rather than hiding them behind a parallel
`Socket`, `Tcp`, `Client`, `Server`, stream, resolver, or implicit-network
universe.

The normative Protos specification under `spec/` remains authoritative. This
record does not add or redefine Core networking, String, Error, Future, Actor,
Process, transfer, resource, module or I/O semantics.

## Selected initial surface

Official Standard Library module identities:

```text
std:network/IpAddresses
std:network/IpEndpoints
```

Physical distribution paths:

```text
protos/lib/network/IpAddresses.protos
protos/lib/network/IpEndpoints.protos
```

Selected public selectors:

```text
std:network/IpAddresses

    v4(a, b, c, d)              -> IpAddress
    v6(a, b, c, d, e, f, g, h) -> IpAddress
    parse(text)                 -> IpAddress
    format(address)             -> String


std:network/IpEndpoints

    parse(text)                 -> IpEndpoint
    format(endpoint)            -> String
```

The plural module names intentionally denote collections of ordinary
convenience operations over the existing singular Core `IpAddress` and
`IpEndpoint` data concepts. They do not create new address/endpoint semantic
families.

## Boundary preserved from Core

### Data is not authority

`IpAddress` and `IpEndpoint` remain authority-free ordinary frozen Core data.
Parsing or formatting them performs no DNS, routing, interface discovery,
Network acquisition or host I/O.

`Network` remains the explicit authority used for network effects. Possessing or
importing either LIB005 module grants no Network authority.

### TCP remains Core

The selected library does not add aliases or wrappers for:

```text
network.connectTcp(endpoint)
network.listenTcp(localRequest)
listener.accept()
connection.read(...)
connection.write(...)
connection.close()
```

Those operations already have their own Future, cancellation, commitment,
lifecycle and authority semantics. LIB005 does not create a second ordering,
waiting, error, resource or socket model around them.

### IPv4 and IPv6 remain semantically distinct

LIB005 never normalizes an IPv4-mapped IPv6 address into a version-4
`IpAddress`. Textual representation may use a mixed IPv6/IPv4 spelling where
selected below, but the resulting semantic value remains version 6.

IPv6 zone/scope identifiers are not part of `IpAddress` data. Core assigns
routing/interface scope to the `Network` authority domain. LIB005 therefore
rejects zone identifiers instead of storing, interpreting or silently discarding
them.

## `IpAddresses.v4`

Selected contract:

```text
v4(a, b, c, d) -> IpAddress
```

Each component must be an ordinary unbounded Core `Integer` in `0..255`.
No fixed-width numeric family is accepted merely because its mathematical value
is in range.

The helper computes the exact 32-bit address value and delegates canonical
construction to the existing Core:

```text
IpAddress(4, bits)
```

A successful call therefore returns an ordinary recognized standard
`IpAddress`, with no alternative family, hidden brand or host representation.

## `IpAddresses.v6`

Selected contract:

```text
v6(a, b, c, d, e, f, g, h) -> IpAddress
```

Each hextet must be an ordinary unbounded Core `Integer` in `0..65535`.
No fixed-width family is accepted by implicit widening.

The helper computes the exact 128-bit address value and delegates canonical
construction to:

```text
IpAddress(6, bits)
```

No interface/scope state is accepted or synthesized.

## `IpAddresses.parse`

`parse(text)` is synchronous, deterministic, authority-free and host-I/O-free.
Its input is semantic Core `String`; invalid input signals an ordinary Error.

The parser accepts **numeric IP literals only**. It never interprets its input as
a hostname and never invokes a resolver.

### IPv4 input

IPv4 uses strict dotted decimal:

```text
dec-octet "." dec-octet "." dec-octet "." dec-octet
```

Rules:

- exactly four components;
- ASCII decimal digits only;
- mathematical value `0..255` for each component;
- no hexadecimal or octal spelling;
- no abbreviated one-, two-, or three-component legacy forms;
- no leading zero unless the complete component is exactly `0`;
- no sign;
- no whitespace.

Examples:

```text
0.0.0.0          accepted
192.0.2.10       accepted
255.255.255.255  accepted

01.2.3.4         rejected
127.1            rejected
0x7f.0.0.1       rejected
1.2.3.256        rejected
 192.0.2.1       rejected
```

This intentionally follows the strict modern direction used by Rust and current
Python rather than carrying forward historical permissive IPv4 syntaxes whose
compatibility burden is visible in older socket APIs and .NET.

### IPv6 input

IPv6 parsing accepts the standard numeric forms defined by RFC 4291, including:

- one through eight hexadecimal hextets as permitted by the RFC grammar;
- one `::` zero-compression occurrence where legal;
- an IPv4 dotted-decimal final 32-bit component where legal.

Hexadecimal input is case-insensitive. Parsing does not preserve source spelling;
the result is only the canonical Core numeric `IpAddress(6, bits)` data.

Examples include:

```text
2001:db8::1
::
::1
2001:0DB8:0:0:0:0:0:1
::ffff:192.0.2.1
```

Zone/scope forms are rejected:

```text
fe80::1%eth0
fe80::1%3
```

Rejecting them is required by the already-selected Protos authority boundary:
scope belongs to `Network`, not portable `IpAddress` identity.

## `IpAddresses.format`

`format(address)` is synchronous and authority-free. The argument must be a
recognized standard Core `IpAddress`; invalid input signals ordinary Error.

IPv4 formatting always emits canonical dotted decimal with no unnecessary
leading zeros.

IPv6 formatting uses one deterministic RFC-5952-oriented canonical spelling:

- lowercase hexadecimal;
- no unnecessary leading zeros in a hextet;
- compress the longest eligible consecutive run of zero hextets;
- do not compress a single zero hextet;
- when equal longest runs exist, compress the first;
- never append or infer a zone identifier.

The recognized IPv4-mapped IPv6 form keeps version-6 semantics while using the
readable mixed notation selected for that known mapping, for example:

```text
::ffff:192.0.2.1
```

No other parsed source spelling is retained merely for round-trip text identity.

Required semantic round-trip:

```text
IpAddresses.parse(IpAddresses.format(address)) == address
```

for every recognized standard address.

Text canonicalization is intentional:

```text
IpAddresses.format(IpAddresses.parse(text))
```

returns the selected canonical spelling, not necessarily the exact source text.

## `IpEndpoints.parse`

`parse(text)` is synchronous, deterministic, authority-free and numeric-only.

Accepted outer forms:

```text
IPv4-address ":" port
"[" IPv6-address "]" ":" port
```

Examples:

```text
192.0.2.10:443
[2001:db8::10]:443
[::ffff:192.0.2.10]:443
```

Unbracketed IPv6 endpoint text is rejected. Hostnames and service names are
rejected:

```text
example.com:443
192.0.2.10:https
```

The address portion follows the selected `IpAddresses` parsing contract.

The port:

- consists only of ASCII decimal digits;
- has mathematical value `1..65535`;
- has no sign or whitespace;
- may contain decimal leading zeros on input, so `00443` denotes decimal 443;
- is materialized as the ordinary unbounded Core `Integer` required by
  `IpEndpoint`.

Port `0` is rejected. Core intentionally does not use an `IpEndpoint` with port
zero as an acquisition command; local automatic port selection belongs to the
separate `listenTcp` request semantics.

A successful parse delegates canonical construction to:

```text
IpEndpoint(address, port)
```

and introduces no hostname/resolver state.

## `IpEndpoints.format`

`format(endpoint)` requires a recognized standard Core `IpEndpoint` and returns
a semantic String.

IPv4 endpoints use:

```text
address:port
```

IPv6 endpoints use:

```text
[address]:port
```

The nested address spelling is the selected canonical
`IpAddresses.format(address)` form. The port is emitted as ordinary base-10
digits with no unnecessary leading zeros.

Required semantic round-trip:

```text
IpEndpoints.parse(IpEndpoints.format(endpoint)) == endpoint
```

## Error and effect model

All selected functions are ordinary synchronous library calls.

They do not:

- create or wait on a Future;
- perform Network or host effects;
- perform DNS;
- inspect routes/interfaces;
- acquire a listener or connection;
- consult a process-global registry/cache;
- create a thread, event loop, selector or poller;
- add a native/Java Standard Library bridge.

Malformed text, wrong argument families, out-of-range components/ports and
unrecognized Core address/endpoint arguments signal ordinary Error according to
the existing language/library error model.

## Prior-art audit

The selected shape follows architectural lessons rather than copying one API.

### Go

Modern `net/netip` strongly validates separating compact IP/address-port data
from network operations, and provides explicit parsing and component
construction. LIB005 adopts that separation.

It does not adopt zone-bearing address identity or the wider `net` package's
convenience paths that can combine names, resolution and connection policy.

### Rust and Tokio

Rust validates strict modern IPv4 parsing and explicit `Ipv4Addr`/`Ipv6Addr`
data, but `ToSocketAddrs` permits one convenience protocol to mean either
already-numeric endpoint conversion or hostname resolution. The synchronous
standard implementation may block; Tokio makes resolution asynchronous but
retains the conceptual overloading.

Protos keeps the semantic distinction instead: parsing numeric endpoint text is
pure data conversion; future name resolution is a separate operation.

### Java

`InetAddress` and hostname-oriented socket construction demonstrate the long-term
coupling that appears when address values, DNS, resolver/cache policy and
connection convenience share one family. `InetSocketAddress` additionally carries
resolved/unresolved state.

LIB005 does not introduce an endpoint whose semantic state depends on whether
DNS happened.

### Python

Python's `ipaddress` module is good evidence for separating pure address
manipulation from `socket`, and current IPv4 parsing is strict after historical
leading-zero permissiveness was removed.

LIB005 does not copy IPv6 scope-zone participation in address equality because
Protos already places scope in Network authority.

### Erlang/OTP

`inet`/`gen_tcp` demonstrates excellent concurrency but also shows how a mature
connection API accumulates hostname-or-address input plus a broad option surface.
LIB005 takes the concurrency lesson without copying that coupling.

### Boost.Asio

Boost.Asio provides the strongest precedent for later Protos growth: resolver
work can be explicit, produce one or more endpoints, and feed asynchronous
connection attempts.

A future Protos Resolver/Happy-Eyeballs layer can therefore compose conceptually
as:

```text
name
  -> explicit Resolver authority/operation
  -> one or more IpEndpoint values
  -> explicit Network.connectTcp(...)
```

without changing this LIB005 data API.

### .NET

The continued acceptance of historical abbreviated/permissive IPv4 forms in
parts of `System.Net` illustrates the compatibility cost of permissive parsing.
LIB005 starts strict while the API is new.

## Future-evolution evaluation

The design was explicitly reviewed for long-term compatibility.

### DNS / Resolver

A future resolver is not forced into `IpAddress`, `IpEndpoint`, String conversion
or `Network.connectTcp`.

It may own its own authority, cache/TTL policy, cancellation semantics and
multi-result behavior, and return explicit ordinary endpoint data.

### Happy Eyeballs / multi-address connection policy

A later algorithm may consume an explicit ordered/set-like collection of
resolved endpoints and coordinate multiple `connectTcp` attempts. LIB005 does
not preselect ordering, fallback timing, DNS family preference or cancellation
policy.

### TLS / QUIC / HTTP / WebSocket

Higher protocol layers remain independent of numeric textual parsing. They can
be designed over the existing byte/network/resource capabilities without
changing the meaning of `IpAddress` or `IpEndpoint`.

### Interfaces / IPv6 scope

A later explicit interface/scoped-Network facility can narrow or interpret
authority without changing portable address identity or LIB005 text conversion.

### Distribution

Addresses/endpoints remain authority-free ordinary data. LIB005 adds no
process/JVM/machine-local resource identity and therefore does not constrain
future Actor, P, Process or distributed placement semantics.

## Scalability evaluation

The selected initial library adds no shared mutable state and no coordination
mechanism.

Each call is local deterministic computation over bounded-size address/endpoint
text or fixed-count integer components. There is:

- no lock;
- no cache;
- no global registry;
- no thread affinity;
- no event-loop affinity;
- no I/O wait;
- no Network allocation;
- no listener/connection allocation.

Large numbers of Actors/Processes can use the helpers independently. The design
therefore adds no networking bottleneck to the scalable I028 backend and obeys
the project principle that programs pay only for mechanisms they use.

Implementation should prefer a bounded direct scan rather than general regex or
backtracking machinery. String access strategy is an implementation concern, but
the accepted grammar itself has small protocol-defined bounds and must not cause
unbounded retained parser state.

## Protos-philosophy evaluation

The selected surface is intentionally aligned with the project design
philosophy:

- **small universe:** no second socket/stream/resolver universe;
- **mechanisms over institutions:** ordinary modules compose existing Core data;
- **ordinary things remain ordinary:** no new privileged address or endpoint
  representation;
- **semantic distinctions remain visible:** text parsing, DNS, authority,
  acquisition, waiting and resource lifecycle are not conflated;
- **pay only for what you use:** importing/using parsing helpers creates no
  network machinery;
- **scale by composition:** future resolver/TLS/HTTP layers can build on the same
  data, Future, Byte I/O and Network mechanisms;
- **minimize shared mutable state:** the selected helpers require none;
- **keep platform differences at the boundary:** no host resolver/socket syntax
  becomes Standard Library semantics.

The review therefore selected this approach as both future-safe and the
best-fitting Protos direction among the compared alternatives.

## Explicitly deferred

The initial LIB005 surface does **not** approve or implement:

- listen-request helper objects;
- `Tcp`, `Socket`, `TcpStream`, `Client`, `Server` or equivalent I/O facades;
- DNS or Resolver;
- Happy Eyeballs;
- CIDR/prefix/network-range abstractions;
- NetworkInterface enumeration;
- zone/scope data in `IpAddress`;
- Network authority derivation, attenuation, policy introspection or registries;
- UDP/datagrams;
- generic socket options;
- deadlines/timeouts;
- TLS;
- QUIC;
- HTTP;
- WebSocket;
- proxies;
- service discovery;
- Unix-domain/raw sockets.

These are not rejected forever. They remain separate future design questions.

## Dxxx / PLATxxx classification

No new Dxxx is required for this approved initial surface because it does not
change normative Core semantics. It consumes already-standardized Core values and
operations through ordinary Standard Library code.

No PLATxxx is required because no durable host/runtime architecture is selected.

If implementation exposes a missing observable Core semantic, the affected
LIB005 slice must stop and route that question through the next appropriate
Dxxx decision. If it exposes a durable platform-specific architecture choice,
the affected slice must stop for the applicable PLATxxx review.

## Implementation decomposition

The approved design may now proceed through dependency-ordered implementation
slices. This decomposition is mechanical and does not grant authority to expand
the API.

### LIB005-A1 — component constructors

Implement `std:network/IpAddresses` with:

```text
v4(a, b, c, d)
v6(a, b, c, d, e, f, g, h)
```

and focused real-`std:` conformance for family/range validation and resulting
recognized Core values.

### LIB005-A2 — strict IPv4 parse/format

Add IPv4 `parse` and `format` behavior to `IpAddresses`, including strict input,
canonical output, round-trip, malformed input and bounded-parser evidence.

### LIB005-B — IPv6 parse/format

Add RFC-4291 parsing and selected RFC-5952-oriented canonical formatting,
including compression, tie-breaking, mixed IPv4 tails, IPv4-mapped output,
zone rejection and round-trip evidence.

### LIB005-C — endpoint parse/format

Implement `std:network/IpEndpoints`, reuse the selected address contract, enforce
IPv4/unbracketed versus IPv6/bracketed outer syntax, decimal port rules and
semantic round-trip.

### LIB005-D — integrated closure

Re-run the complete LIB005 conformance surface together, verify the module
resolver/naming and Core native-boundary guards, confirm that no Network/native
authority or specification change was introduced, and close the bounded initial
LIB005 work item.

No slice may silently absorb any explicitly deferred feature.

## Closure rule

`LIB005-0` design is closed by explicit project-owner approval and publication
of this record.

The parent `LIB005` remains open until A1/A2/B/C/D implementation, conformance
and publication are complete. GitHub Issue #54 remains the live coordination and
work-log surface; this document is durable design authority for the selected
non-normative Standard Library API.

## Implementation progress

### LIB005-A1 — implementation CLOSED

Published implementation version: `0.2.333-SNAPSHOT`
Closure evidence: `SAME_COMMIT`
Live work item: GitHub Issue `#274`

A1 publishes `protos/lib/network/IpAddresses.protos` with exactly the approved
`v4(a, b, c, d)` and `v6(a, b, c, d, e, f, g, h)` component constructors.

The implementation remains ordinary Protos. Exact-domain validation reuses the
already-established strict `Integer.div(1)` receiver domain as a validation
probe, matching the prior LIB003 Standard Library pattern and deliberately
keeping family validation separate from numeric conversion. Fixed-width values
and delegated numeric lookalikes may resolve the selector through delegation,
but the Core protocol rejects any receiver that is not an actual ordinary
unbounded Integer value. Range validation then enforces `0..255` or `0..65535`
before exact positional arithmetic assembles the Core address bits.

Successful construction still goes exclusively through the existing
`IpAddress(4, bits)` / `IpAddress(6, bits)` Core factory. A1 adds no parser,
formatter, Network authority, DNS/resolver, Future, TCP operation, native
Standard Library bridge or specification behavior.

Focused real-`std:` Protos conformance validates constructor behavior. The
Java harness separately checks the imported module's exact local-slot surface
because slot-name enumeration is not currently guest-visible; that harness does
not implement or compute networking behavior. The current adaptive publication
gate validates the complete executable delta. `LIB005-A2 — strict IPv4 parse/format` is the
next dependency-ordered implementation slice.

### LIB005-A2 — implementation CLOSED

Published implementation version: `0.2.335-SNAPSHOT`
Closure evidence: `SAME_COMMIT`
Live work item: GitHub Issue `#277`

A2 extends the existing ordinary-Protos `std:network/IpAddresses` module with
the approved IPv4 `parse(text)` and `format(address)` selectors while leaving
the A1 `v4` / `v6` constructors unchanged.

Parsing exercises the strict String receiver domain, rejects accepted-shape
lengths outside `7..15`, scans the semantic String directly, classifies only
ASCII `0` through `9` plus the literal `.` separator, accumulates each component
with exact ordinary Integer arithmetic, rejects empty/extra components,
leading-zero ambiguity, components above `255`, signs, whitespace, Unicode
digits, legacy spellings, hostnames and IPv6 text, then delegates successful
construction to the already-published `v4(a,b,c,d)` helper.

Formatting first requires `IpAddress.recognizes(address)` and version `4`.
It decomposes the canonical Core `bits` value into four octets with ordinary
`div` / `mod`, emits ASCII decimal digits without unnecessary leading zeros and
joins the components with `.`. A2 intentionally rejects version-6 addresses;
the already-approved LIB005-B slice owns adding IPv6 parse/format behavior.

All parser state is call-local. The implementation uses no regex/backtracking
engine, cache, global mutable state, Encoding/Bytes staging, DNS, Resolver,
Network authority, Future, host I/O or production Java/native bridge. Focused
real-`std:` conformance covers positive boundaries, canonical output, semantic
round-trip, malformed input and wrong-domain rejection. The Java harness remains
test-only and observes the exact local module surface because guest slot-name
enumeration is not currently available.

`LIB005-B — IPv6 parse/format` is the next dependency-ordered implementation
slice.
