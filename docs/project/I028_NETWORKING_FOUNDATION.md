# I028 — Core Networking Foundation

Status: **IN_PROGRESS**
Normative dependencies: D047 / specification revision `0.1.388` — RATIFIED; D048 / specification revision `0.1.391` — RATIFIED
Consumer: `LIB005 — Networking`

## Purpose

Implement the D047 portable networking foundation without importing host socket
state machines or backend scheduling identities into Protos semantics.

## Entry checkpoint — CLOSED by D048

D048 / specification revision `0.1.391` resolves the only source-visible
construction/recognition protocol intentionally deferred by D047. The approved
surface is ordinary `IpAddress(version, bits)` / `IpEndpoint(address, port)`
factory invocation over canonical frozen prelude factory/prototypes, transparent
exact frozen structural recognition, and explicit `recognizes(value)` predicates.
No hidden semantic family/brand or textual/DNS coercion is introduced.

`I028-A` is therefore READY. Later slices remain bounded by D047/D048 and by the
normal implementation dependencies established as A-F progresses.

## Planned implementation decomposition after the checkpoint

- **A — address/endpoint ordinary-object foundation — CLOSED:** A1/A2 publish the canonical ordinary data factories and A3 proves those same values traverse Actor/P through the pre-existing ordinary snapshot/rematerialization machinery with no networking-specific transfer path.
  - **A1 — IpAddress local foundation — CLOSED (`0.2.278-SNAPSHOT` / `SAME_COMMIT`):** source-backed canonical ordinary prototype, `IpAddress(version, bits)` through ordinary invocation, exact unbounded-Integer IPv4/IPv6 validation, transparent `recognizes`, structural `==`/`hash`, frozen successful values and frozen Prelude prototype, plus ordinary-Protos conformance. No `IpEndpoint` or Actor/P transfer.
  - **A2 — IpEndpoint local foundation — CLOSED (`0.2.279-SNAPSHOT` / `SAME_COMMIT`):** canonical frozen ordinary Prelude factory/prototype, recognized-IpAddress + exact unbounded-Integer port validation, transparent recognition, structural equality/hash, exact address retention and ordinary-Protos conformance. No Actor/P transfer.
  - **A3 — Actor/P transfer + close A — CLOSED (`0.2.280-SNAPSHOT` / `SAME_COMMIT`; implementation version unchanged):** retained Protos-source conformance proves Actor and P rematerialization preserve canonical IpAddress/IpEndpoint parents, exact recognized frozen state, nested address data, structural equality/hash and fresh destination identities; Actor uses its deterministic harness and P uses existing P runtime-test orchestration because Test Tool direct inspection intentionally requires cooperative idle before Future inspection; no production transfer special case is added.
- **B — Network capability + bootstrap provisioning — IN_PROGRESS:** PLAT002 selects a represented live Network authority below a canonical source-backed prototype; B1 publishes only that prototype/architecture record and B2-B5 implement the capability, bootstrap confinement and closure without selecting the TCP backend.
  - **B1 — canonical Network prototype — CLOSED (`0.2.282-SNAPSHOT` / `SAME_COMMIT`):** publish frozen source-backed standard `Network` in Prelude, register ratified PLAT002, update exact Core inventories and retain zero authority/backend/native-Closure construction.
  - **B2 — represented Network capability + transfer confinement — CLOSED (`0.2.283-SNAPSHOT` / `SAME_COMMIT`):** implement the PLAT002 represented wrapper with canonical Network parent, opaque host target and explicit Actor/P fail-closed transfer, including authority-bearing descendants; no RootActor endowment or TCP operations.
  - **B3 — optional RootActor `network` bootstrap endowment — READY:** consume B2 and provision host-granted Network only in the initial RootActor module context, absent when not granted and never imported/ambient.
  - **B4 — ambient/import/Actor/P confinement conformance — BLOCKED_BY_DEPENDENCIES:** prove prototype possession/ordinary derivation cannot manufacture authority and no implicit import/new-Actor/P recovery path exists.
  - **B5 — close B — BLOCKED_BY_DEPENDENCIES:** reconcile retained evidence and close Network capability/bootstrap provisioning before TCP acquisition implementation proceeds.
- **C — TCP connection + `connectTcp`:** async acquisition, cancellation/late
  resource custody, Byte I/O/close/half-close composition, endpoint observation.
- **D — TCP listener + `listenTcp`:** exact request snapshot/validation,
  `localPort`, concurrent pending accepts, lifecycle/admission behavior.
- **E — production backend portability/scalability:** preserve D047 independently
  of NIO/epoll/kqueue/io_uring/IOCP/Network.framework implementation choices;
  subdivide mechanically if required.
- **F — cross-slice conformance/native-boundary closure:** cancellation races,
  late custody, multiple-accept scale evidence and Actor/P non-transferability.

## I028-B2 closure

Published at implementation version `0.2.283-SNAPSHOT`. The PLAT002 represented capability now exists as
`ProtosNetworkCapabilityValue`: it retains the exact canonical source-backed `Network` prototype and
one opaque non-null host authority target outside ordinary Protos slots. `ProtosPrelude.networkPrototype()`
provides the exact standard parent to host/runtime code. Actor and isolated-P copy paths now reject the
represented capability explicitly, and ordinary descendants whose delegation parent reaches that capability
fail at the same boundary rather than smuggling authority through graph copy. Conversely, an ordinary object
whose parent is merely the authority-free `Network` prototype remains ordinary transferable data, proving
that prototype possession/derivation does not manufacture authority. B2 adds no RootActor `network` slot,
Network operation, TCP resource/backend, native Closure site or normative specification change. B3 is READY.

## I028-B1 / PLAT002 publication

Published at implementation version `0.2.282-SNAPSHOT`. The project-owner-ratified PLAT002 architecture
selects the existing represented-value mechanism for future concrete Network capabilities while
leaving D047 as the sole owner of observable networking semantics. B1 itself publishes only the
canonical source-backed frozen `Network` standard prototype in Prelude and the retained Protos
prototype conformance case. It introduces no `ProtosNetworkCapabilityValue` yet, no RootActor
`network` endowment, no connect/listen/TCP resource, no backend/event-loop/selector state and no
native Closure site. The audited Core native boundary therefore remains 123 sites / 32 providers.
B2 is READY for the represented capability and explicit Actor/P fail-closed transfer evidence.

## I028-A1 closure

Published at implementation version `0.2.278-SNAPSHOT`. The slice is intentionally local:
`IpAddress` is a canonical frozen ordinary Prelude factory/prototype, successful construction
returns a fresh frozen ordinary child with exactly `version` and `bits`, and the bounded
representation bridge enforces exact unbounded-Integer IPv4/IPv6 ranges, transparent
recognition and structural equality/hash without candidate callbacks. Ordinary-Protos
conformance covers construction/freshness/freeze, boundary and fixed-width rejection,
recognition receiver/arity/shape behavior, equality/hash and Map-key coherence. The native
boundary is explicitly re-audited at 119 sites / 31 providers. `IpEndpoint`, Actor/P transfer
and every Network/TCP/backend concern remain untouched.

## I028-A2 closure

Published at implementation version `0.2.279-SNAPSHOT`. `IpEndpoint` is now the second canonical
frozen ordinary D048 data factory/prototype. Successful construction retains the exact supplied
recognized `IpAddress`, validates an exact unbounded Integer port in `1..65535`, publishes exactly
`address` / `port`, and freezes the fresh endpoint. Transparent recognition and structural
endpoint equality/hash inspect only canonical frozen state; equal-but-distinct IpAddress objects
therefore compose into equal endpoint values and coherent Map keys. The bridge reuses A1's direct
IpAddress invariant implementation and advances the audited native boundary to 123 sites /
32 providers. Actor/P transfer remains entirely in A3, which is now READY; every
Network/TCP/backend concern remains untouched.

## I028-A3 / I028-A closure

Closed without a production-runtime or implementation-version change at `0.2.280-SNAPSHOT`.
The existing generic Actor snapshot/materialization and isolated-P copy machinery already handles
these D048 values as ordinary frozen object graphs: canonical Prelude prototypes remain canonical,
ordinary value objects are rematerialized with exact local slots and mutation state, and the nested
IpAddress inside IpEndpoint is copied recursively. Two retained Protos-source cases exercise the
actual boundaries end to end. The Actor case round-trips a max-width IPv6/max-port endpoint through
a real deterministic child Actor and proves recognized frozen shape, exact canonical parents, fresh
destination identities and post-transfer equality/hash. The P case performs the same proof by passing the endpoint explicitly through
`parallel(endpoint)`; the existing Java P runtime-test harness only drives that external-carrier Future
to terminal state because the main Test Tool direct-inspection boundary requires cooperative idle
before it inspects a returned Future. No `ProtosIpAddressValue`/`ProtosIpEndpointValue`, transfer
whitelist, Network authority, TCP/backend machinery, native Closure site or specification change is
introduced. I028-A is CLOSED; I028 remains IN_PROGRESS for B-F.

## Exclusions

DNS, UDP, TLS, QUIC, HTTP/WebSocket, NetworkInterface enumeration, formal policy
attenuation, generic socket options and socket-local deadlines remain outside this
implementation item unless separately approved.
