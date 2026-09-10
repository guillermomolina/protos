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
| I028-B2 | CLOSED | Implemented represented Network wrapper with canonical parent/opaque host target plus explicit Actor/P rejection for the capability and authority-bearing descendants; no bootstrap/TCP. |
| I028-B3 | CLOSED | Optional exact RootActor initial-module `network` endowment from an already-provisioned represented capability; absent grant means absent slot, imports/new Actors receive no ambient grant, and Process has no Network accessor. |
| I028-B4 | CLOSED | Retained integrated authority-confinement evidence now proves no ambient recovery through Prelude/Process/import/new-Actor plus Actor/P rejection of the exact RootActor bootstrap grant; no production/TCP change. |
| I028-B5 | CLOSED | Reconciled B1-B4 retained evidence and closed the represented Network capability/bootstrap boundary; I028-C may proceed without altering PLAT002 or selecting a backend. |

## B2 implementation evidence

I028-B2 closes in `0.2.283-SNAPSHOT`. `ProtosNetworkCapabilityValue` implements the existing
`ProtosRepresentedValue` bridge, stores the exact standard Network prototype selected from the
provisioning Prelude and retains one opaque non-null host authority target outside the ordinary object graph.
Actor transfer names the wrapper in its explicit non-transferable resource set; P names it in the explicit
NonParallel set. Because both copiers traverse delegation parents before copying ordinary descendants, an
authority-bearing ordinary child is rejected as well. An ordinary child of the authority-free Network prototype
remains copyable, preserving the PLAT002 prototype/capability distinction. No generic HostCapability base,
backend interface, RootActor endowment or TCP mechanism is introduced.

## B3 implementation evidence

I028-B3 closes in `0.2.284-SNAPSHOT`. `ProtosProcessRuntime` may retain one optional already-provisioned
`ProtosNetworkCapabilityValue` solely as RootActor bootstrap state alongside the pre-existing
optional Filesystem grant. `ProtosActorBootstrap` validates that the capability delegates to the
exact Network prototype of the active Prelude and places it only in the initial RootActor module's
local `network` slot. Missing authority means no slot. Ordinary imports and non-root Actor bootstrap
continue through paths that never receive root bootstrap locals. The host-neutral standalone
assembler retains its existing API and adds an overload accepting the exact represented Network
capability. Process protocol selectors, Actor/P transfer rules, Network representation, native
Closure inventory and backend identity remain unchanged.

## B4 retained confinement evidence

I028-B4 closes at `0.2.284-SNAPSHOT` with the implementation version unchanged. The retained integration
harness composes the already-selected represented Network architecture with the actual B3 RootActor
grant: capital-`Network` remains authority-free Prelude state; `Process` exposes no Network accessor;
imports and hosted non-root Actors cannot resolve the RootActor-local lower-case `network`; and the
exact provisioned represented capability fails the existing Actor `NonTransferableValue` and P
`NonParallel` boundaries. This evidence adds no backend, resource proxy or authority derivation rule.
B5 is READY for reconciliation only; PLAT002 itself is unchanged.

## B5 / I028-B closure evidence

I028-B5 closes at `0.2.284-SNAPSHOT` with the implementation version unchanged. The B front now has retained
evidence for the authority-free `Network` prototype, represented host authority, optional exact RootActor
bootstrap endowment, no Prelude/Process/import/non-root-Actor ambient recovery, and explicit Actor/P
non-transferability. The B closure introduces no new platform architecture: PLAT002 remains RATIFIED and
unchanged, and backend/multiplexing identity remains deliberately deferred. I028-C is released to consume
this closed authority boundary for the already-ratified `connectTcp` semantics.

## Deliberately deferred

The closed I028-B front does not select or implement `connectTcp`, `listenTcp`, TcpConnection, TcpListener, NIO or any
other production network backend. Those remain owned by I028-C/D/E under D047. DNS, UDP, TLS, QUIC,
HTTP/WebSocket, generic socket options, timeout/deadline APIs and formal Network attenuation remain
outside the currently ratified networking revision.
