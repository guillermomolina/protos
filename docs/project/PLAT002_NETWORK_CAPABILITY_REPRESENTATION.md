# PLAT002 — Network capability represented-value architecture

Status: **RATIFIED**

Nature: durable non-normative platform/runtime architecture decision

Approved by project owner: **2026-09-09**

Primary consumer: `I028-B` and later I028 Network/TCP implementation slices

Normative owners: D047 / specification revision `0.1.388` and `spec/io/NETWORK.md`

## Decision boundary

D047 already defines the observable Protos model: `Network` is an explicit live authority
capability, never ambient or Process-derived; addresses/endpoints are authority-free data; and
Network/TCP capabilities have no initial Actor/P transfer contract. PLAT002 does not alter any of
those semantics. It selects only how the current JVM/Truffle implementation physically represents
one concrete Network authority while preserving ordinary Protos delegation.

## Selected architecture

The current implementation selects a dedicated represented capability:

```text
frozen source-backed standard Network prototype
                    ^
                    | ordinary Protos delegation
                    |
      ProtosNetworkCapabilityValue
                    |
                    `-- opaque host-selected authority target
```

`ProtosNetworkCapabilityValue` will implement the existing implementation-only
`ProtosRepresentedValue` bridge. The wrapper supplies its exact standard `Network` delegation
parent for ordinary lookup while keeping host authority outside ordinary Protos slots. Merely
possessing/invoking/deriving from the source-backed `Network` prototype never manufactures such a
represented capability; future Network protocol bridges must require an actual represented Network
receiver before exercising authority.

The represented wrapper is non-transferable through Actor and P in the initial D047 model. Transfer
machinery remains fail-closed: B2 will make that rule explicit and tested rather than introducing a
Network delegation/proxy contract.

## Rejected alternatives

### Ordinary `ProtosObjectValue` authority marker

Rejected as the long-term Network representation. The existing Filesystem marker demonstrates that
this can work, but it places a live host authority inside the ordinary graph-copy universe and then
requires resource-specific exclusions. Network has an explicitly standardized source-backed
prototype and future connect/listen operations whose authority must remain opaque; the represented
pattern used by Process/ActorRef is the cleaner separation.

### Generic HostCapability framework

Rejected for now as premature generalization. Process, Filesystem, Network, TcpConnection and
TcpListener differ in transfer, lifetime, ownership, I/O and delegation contracts. PLAT002 adds no
new public or runtime-wide capability hierarchy. If later implementations reveal a genuinely common
mechanism, internal factoring may be proposed without changing Protos semantics.

## Scalability and future evolution

The wrapper identity is independent of the backend multiplexing architecture. A conforming current
implementation may later use Java NIO, epoll, kqueue, io_uring, IOCP, Network.framework, WASI,
brokered IPC, containers/network namespaces, VRFs/VNETs, VPCs or another backend without making
selector/event-loop/thread/fd/channel identity observable to Protos.

The intended cardinality is one Network wrapper per provisioned authority domain, not one Network
per connection or pending operation. TcpListener, TcpConnection and Future instances scale
independently below it. No B slice may introduce a semantic one-thread, one-Actor, one-event-loop or
one-pending-accept bottleneck.

A future explicitly standardized attenuation/derivation facility may return another represented
Network capability whose opaque authority target is narrower. PLAT002 intentionally defines no
`NetworkPolicy`, CIDR/port DSL, backend interface, socket option model or broker protocol today.

A future superseding implementation may change the physical wrapper/authority machinery if all
D047 observations remain unchanged. Such a durable platform change requires an explicit PLAT
review, not a language-specification revision merely because backend machinery changed.

## I028-B decomposition

| Slice | Initial status after B1 | Scope / exit condition |
|---|---|---|
| I028-B1 | CLOSED | Publish the canonical frozen source-backed `Network` prototype, register PLAT002, and close Core/Prelude/source inventories without creating authority or selecting a TCP backend. |
| I028-B2 | READY | Implement `ProtosNetworkCapabilityValue` as the selected represented wrapper and make Actor/P non-transferability explicit with focused tests; no bootstrap endowment or TCP operations. |
| I028-B3 | BLOCKED_BY_DEPENDENCIES | Add optional RootActor initial-module local `network` provisioning with absence/import/new-Actor confinement; no ambient recovery. |
| I028-B4 | BLOCKED_BY_DEPENDENCIES | Retained Protos/JVM conformance for prototype-vs-capability distinction and bootstrap/import/Actor/P authority confinement. |
| I028-B5 | BLOCKED_BY_DEPENDENCIES | Reconcile B evidence and close the Network capability/bootstrap slice before TCP implementation proceeds. |

## Deliberately deferred

B1/B2 do not select or implement `connectTcp`, `listenTcp`, TcpConnection, TcpListener, NIO or any
other production network backend. Those remain owned by I028-C/D/E under D047. DNS, UDP, TLS, QUIC,
HTTP/WebSocket, generic socket options, timeout/deadline APIs and formal Network attenuation remain
outside the currently ratified networking revision.
