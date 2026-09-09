# I028 — Core Networking Foundation

Status: **OPEN**
Normative dependency: D047 / specification revision `0.1.388` — RATIFIED
Consumer: `LIB005 — Networking`

## Purpose

Implement the D047 portable networking foundation without importing host socket
state machines or backend scheduling identities into Protos semantics.

## Entry checkpoint

D047 intentionally leaves one public protocol detail unsettled: the exact
source-visible constructor/factory selector spellings and recognition/construction
protocol for standard `IpAddress` and `IpEndpoint` data. Because selector names
and public receiver-domain rules are observable API, this item remains `OPEN`
until that bounded design checkpoint receives explicit project-owner approval.

No implementation slice may silently choose those public names or encode a
hidden semantic family as an implementation convenience.

## Planned implementation decomposition after the checkpoint

- **A — address/endpoint ordinary-object foundation:** construction/recognition
  protocol selected by the checkpoint, validation, freezing, structural equality
  and hashing, IPv4/IPv6 exact-bit invariants, transfer conformance.
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
