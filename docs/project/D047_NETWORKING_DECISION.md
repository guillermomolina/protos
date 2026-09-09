# D047 — Explicit capability-oriented TCP networking foundation

Status: **RATIFIED**
Specification revision: **`0.1.388`**
Explicit project-owner approval: **2026-09-09**
Nature: normative networking design decision; rationale for `spec/io/NETWORK.md`

## Decision

The project owner explicitly approved the complete D047 decision packet after a
comparative audit spanning operating-system APIs and capability models plus Self,
Io, Smalltalk, Erlang/OTP, Java, Rust, Go, .NET, WASI, Plan 9 and related
networking/runtime designs. The audit was stress-tested for future evolution,
large-server scalability, multi-core/distribution, capability security, host
portability and the published Protos design philosophy.

The selected architecture is intentionally smaller than a BSD-socket clone:

- explicit policy-confined `Network` authority, never ambient or Process-derived;
- ordinary frozen numeric `IpAddress` and structural `IpEndpoint` data;
- materialized `TcpConnection` / `TcpListener` roles instead of one mutable
  universal Socket state machine;
- existing Future, Byte I/O, Closable and half-close semantics reused directly;
- numeric-IP TCP first; DNS and UDP remain independent future designs;
- IPv4/IPv6 explicit, with routing/interface scope owned by Network rather than
  portable address identity;
- exact-shape ordinary local-listen request with `null` for absence of an
  address/port constraint rather than wildcard/port-zero magic;
- multiple pending accepts permitted; no semantic event-loop/thread affinity;
- live Network/TCP capabilities non-transferable across Actor/P in the initial
  contract; addresses/endpoints remain authority-free data;
- shallow portable Error mapping and no generic socket-option/policy DSL.

## Future and scale result

The model can be implemented over epoll, kqueue, io_uring, IOCP, Java NIO,
Network.framework, WASI, containers/network namespaces, VRFs/VNETs, VPC/overlay
networks or a brokered backend without making those mechanisms observable. A
large server may keep multiple accepts and many byte-I/O Futures outstanding
without a one-thread/one-Actor/one-accept semantic bottleneck. Programs that do
not receive Network authority pay no networking conceptual or runtime contract.

## Protos-philosophy result

The decision reuses existing mechanisms rather than creating `NetworkStream`,
`EventLoop`, `Selector`, `SocketOption`, `NetworkPolicy`, `AnyAddress` or similar
parallel institutions. Address data stays ordinary, authority is explicit,
semantic distinctions remain visible, and scale is obtained by composing the
same Future/I/O/capability rules.

## Intentionally deferred

D047 does not approve DNS/resolver APIs, Happy Eyeballs, UDP, public
NetworkInterface APIs, formal transferable Network attenuation, TLS/QUIC/HTTP/
WebSocket, Unix/raw sockets, service discovery, generic socket options, or
socket-local timeout/deadline APIs.

D047 also fixes the semantic state/equality laws of `IpAddress` and `IpEndpoint`
but **does not approve the exact public constructor/factory selector spellings or
exact standard recognition/construction protocol**. That narrow public-API choice
must cross a later explicit design checkpoint before `I028` publishes the
source construction surface. This is why `I028` is allocated `OPEN`, not
`READY`, by the ratification slice.

## Implementation owner

`I028 — Core networking foundation` is allocated as the implementation
owner. The identifier is mechanical; `I027` was already occupied by the time
D047 was ratified. `I028` remains `OPEN` pending the bounded address/endpoint
construction checkpoint, after which its implementation may be decomposed into
address/endpoint foundation, Network provisioning, connect/TCP connection,
listen/accept, production backend scalability and final conformance slices.

`LIB005` remains blocked by that Core implementation. After `I028` closes,
`LIB005-0` should select only the bounded ordinary-Protos convenience surface; it
must not reintroduce native networking authority.
