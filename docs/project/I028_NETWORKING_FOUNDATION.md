# I028 — Core Networking Foundation

Status: **READY**
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

- **A — address/endpoint ordinary-object foundation — READY:** implement the D048 construction/recognition protocol, validation, freezing, structural equality and hashing, IPv4/IPv6 exact-bit invariants and transfer conformance.
- **B — Network capability + bootstrap provisioning:** host-neutral live
  capability shape and optional initial-module `network` endowment; no ambient
  recovery.
- **C — TCP connection + `connectTcp`:** async acquisition, cancellation/late
  resource custody, Byte I/O/close/half-close composition, endpoint observation.
- **D — TCP listener + `listenTcp`:** exact request snapshot/validation,
  `localPort`, concurrent pending accepts, lifecycle/admission behavior.
- **E — production backend portability/scalability:** preserve D047 independently
  of NIO/epoll/kqueue/io_uring/IOCP/Network.framework implementation choices;
  subdivide mechanically if required.
- **F — cross-slice conformance/native-boundary closure:** cancellation races,
  late custody, multiple-accept scale evidence and Actor/P non-transferability.

## Exclusions

DNS, UDP, TLS, QUIC, HTTP/WebSocket, NetworkInterface enumeration, formal policy
attenuation, generic socket options and socket-local deadlines remain outside this
implementation item unless separately approved.
