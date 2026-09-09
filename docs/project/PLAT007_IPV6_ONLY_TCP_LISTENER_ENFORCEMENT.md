# PLAT007 — JVM NIO IPv6-only TCP listener enforcement

Status: **RATIFIED**

Nature: durable non-normative JVM/network-backend architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: `I028-E`, then `I028-F`

Normative effect: **none** — D047 remains authoritative for the portable rule
that IPv4 and IPv6 are semantically distinct and that a version-6 listener is
IPv6-only. D052 remains authoritative for live-resource topology; PLAT003 and
PLAT006 remain authoritative for host-neutral TCP resource/operation architecture.

GitHub coordination: `PLAT007` / issue #232.

## Decision boundary

PLAT006 selected a host-neutral I/O operation engine and bounded non-blocking JDK
NIO as the initial production JVM backend. I028-E implementation audit exposed
one portability mismatch below that boundary: the public JDK 21 socket API does
not expose `IPV6_V6ONLY`, while an IPv6 wildcard server socket may be dual-stack
on some supported hosts.

PLAT007 selects how the initial public-JDK backend preserves the already-normative
IPv6-only listener contract. It does not weaken, reinterpret or extend Protos
network semantics.

## Selected architecture

### 1. No dual-stack wildcard implementation for an IPv6-only listener

The initial JVM backend must not implement a semantically IPv6-only Protos
listener by binding one potentially dual-stack IPv6 wildcard socket and then
trusting host defaults.

It also must not satisfy the contract by accepting IPv4 connections first and
filtering them afterward: an IPv4 handshake, backlog occupation or other
listener-visible host effect would already have occurred.

### 2. Composite public-JDK listener for `address: null`

For an IPv6 `listenTcp` request whose address field is canonical `null`, the
backend captures at acquisition time the concrete IPv6 addresses authorized by
the supplied `Network` capability.

It then constructs one logical `TcpListener` backed by one public-JDK
`ServerSocketChannel` opened with the IPv6 protocol family and bound to each
concrete authorized IPv6 address. Those physical channels are multiplexed through
the PLAT006 bounded host I/O engine.

The number and identity of physical channels remain opaque host machinery. They
are not Protos objects, are not reflected as listener identity and do not alter
the one-logical-listener D047/D052 surface.

### 3. One logical acquired port, all-or-nothing construction

Every physical component of one logical composite listener must use the same
non-zero local port exposed by `listener.localPort()`.

For a fixed requested port, every component is bound to that exact port.

For `port: null`, the implementation may let one component acquire a candidate
non-zero port and then bind the remaining components to that exact candidate.
The exact retry policy after a candidate-port collision remains deliberately
deferred.

If the complete authorized component set cannot be established coherently, the
backend releases every already-created component and fails the listener
acquisition. It never publishes a partially bound logical listener.

### 4. IPv6 scope remains Network-authority state

Portable `IpAddress` contains no interface/scope identity. Any required IPv6
scope is resolved inside the supplied `Network` authority domain.

If a requested address has no authorized host interpretation, or if the
authority admits multiple incompatible scope interpretations that cannot be
resolved unambiguously, acquisition fails closed.

No scope id, interface identity or host route becomes part of portable
`IpAddress`, `IpEndpoint` or `TcpListener` identity.

### 5. Canonical null is not an IPv6 bit-pattern

`address: null` is the acquisition request that means no single-address
constraint beyond IP version and Network authority.

No explicit `IpAddress` bit pattern — including `::`, IPv4-mapped IPv6 data or
another host-significant value — is reinterpreted as that acquisition command.
If the JVM/host cannot realize an explicit request while preserving its exact
IPv6-family meaning, the backend fails rather than silently changing semantics.

### 6. Acquisition-time address capture

The concrete authorized IPv6-address set is captured for the listener
acquisition. PLAT007 does not promise dynamic listener rebinding when host
interfaces, addresses or routes subsequently change.

A future explicit facility may define different dynamic behavior without
changing this baseline decision.

## Cross-runtime and Truffle evidence

The approved decision followed comparison across mainstream runtimes and the
Truffle/GraalVM ecosystem.

Go, Rust, .NET, libuv/Node, Python, Erlang/BEAM and Ruby all demonstrate that
IPv6-only behavior belongs below the language/network API boundary and is
normally enforced by explicit socket-family/options machinery when the host API
exposes it.

The Truffle comparison reinforces the same separation:

- GraalPy demonstrates that one language/runtime surface can retain both Java
  and native host backends without turning their physical mechanisms into guest
  semantics.
- GraalJS keeps ECMAScript semantics distinct from the Node/libuv runtime that
  supplies networking.
- TruffleRuby retains JVM/native execution options without making those backends
  Ruby object identity.
- Espresso inherits Java networking constraints, confirming that Truffle itself
  does not solve this JDK socket-option gap.
- Sulong/NFI provide native escape hatches but reinforce why native access must
  remain below the Protos capability/authority boundary rather than becoming a
  generic guest bypass.
- Apple Pkl is a particularly relevant capability precedent: external resources
  are supplied through host-provided readers/providers rather than by embedding
  host resource identity into the language model.

The consistent lesson is that Protos should preserve one Network/TCP semantic
universe while host/runtime implementations vary below a stable authority and
operation boundary.

## Scalability position

The composite public-JDK listener is the **portable conformance baseline**, not a
claim that one physical socket per authorized IPv6 address is the forever-optimal
implementation.

For ordinary hosts the authorized IPv6-address set is small, and many physical
channels can still be multiplexed by the bounded PLAT006 Selector/poller
substrate without introducing one thread/event loop per resource.

Its physical resource cost for unconstrained IPv6 listeners is approximately:

```text
logical unconstrained IPv6 listeners × captured authorized IPv6 addresses
```

This can be expensive on deliberately high-address-cardinality hosts. That is a
known backend cost, not a language-model limitation.

PLAT006 deliberately preserves a future O(1)-socket specialization: a native
backend may create one `AF_INET6` listener and enforce `IPV6_V6ONLY=1` (or the
platform-equivalent mechanism), then feed the same logical operations and
completions into the unchanged Protos boundary.

## Future backend freedom

A conforming future implementation may replace the composite JDK mechanism with,
for example:

- direct epoll/kqueue/io_uring/IOCP transport;
- a native socket backend that can set `IPV6_V6ONLY`;
- an FFM/Panama-based host implementation when the project baseline and measured
  benefit justify it;
- a Netty/native transport;
- WASI capability networking; or
- a brokered/IPC Network authority backend.

Such a backend must preserve D047/D052/PLAT003/PLAT006 observations and pass the
same cancellation, late-custody, listener-close, independently pending accept,
IPv6-family and authority conformance. Backend substitution is not a language
change.

## Rejected alternatives

### Trust `ServerSocketChannel.open(INET6)` + wildcard bind

Rejected because host/JDK dual-stack behavior does not by itself prove the
portable IPv6-only contract.

### Accept IPv4 and reject it after `accept`

Rejected because the IPv4 peer can already complete host-level connection work
and consume listener/backlog resources before filtering.

### Depend on unsupported JDK internals/reflection

Rejected as the portable baseline because it creates JDK-implementation
fragility and module-opening constraints for a requirement that can be satisfied
using public APIs.

### Require JNI/native transport immediately

Rejected as the initial baseline because it adds packaging, ABI, multi-platform
and Native Image obligations before measured cardinality requires that
optimization. Native enforcement remains the preferred high-cardinality future
specialization.

### Disable IPv6 listening entirely

Rejected because the portable contract is coherent and can be implemented
correctly without weakening it.

## Deliberately deferred

PLAT007 does not select:

- exact interface/address enumeration caching;
- exact candidate-port retry count or retry scheduling;
- exact component-to-poller sharding;
- dynamic rebinding after interface/address changes;
- FFM/Panama vs JNI vs framework choice for a future native backend;
- direct epoll, kqueue, io_uring or IOCP implementation;
- poller count, CPU affinity or NUMA policy;
- DNS, UDP, TLS, QUIC or HTTP scope;
- endpoint `===` strengthening; or
- any Protos-visible backend-selection mechanism.

If I028-E implementation exposes a substantive durable choice in one of these
areas, the affected slice must stop and cross the explicit design-approval gate.

## Consumer release

Publication of PLAT007 releases I028-E from the IPv6-listener platform blocker.
I028-E may resume bounded implementation decomposition under PLAT003, PLAT006 and
PLAT007.

The initial production listener implementation must prove:

- IPv4-only requests never acquire IPv6 listener behavior;
- IPv6-only requests never admit IPv4 through a dual-stack wildcard shortcut;
- composite listener construction is all-or-nothing;
- all components expose the same logical local port;
- cancellation and late acquisition retain explicit custody;
- multiple pending accepts compose across the logical listener without exposing
  physical-channel order/identity; and
- close releases the complete logical listener resource set exactly once under
  the existing lifecycle contract.

I028-F retains final integrated conformance and native-boundary closure.
