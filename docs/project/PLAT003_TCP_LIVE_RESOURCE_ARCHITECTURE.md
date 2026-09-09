# PLAT003 — JVM TCP live-resource and duplex-I/O architecture

Status: **RATIFIED**

Nature: durable non-normative JVM/Truffle runtime architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: `I028-C`, `I028-D`, and later `I028-E`

Normative owners: D047, D052 / specification revision `0.1.393`, `spec/io/NETWORK.md`,
`spec/io/IO_CORE.md`, and `spec/io/BYTE_IO.md`

## Decision boundary

D052 owns the observable fact that acquired TCP resources are ordinary OPEN Protos objects with
specific canonical standard protocol parents. PLAT003 does not own or modify that topology. It selects
how the current JVM/Truffle implementation can realize it at scale while preserving D047/D052,
Future, Byte I/O, lifecycle, half-close and Actor/P observations.

## Selected representation

The current JVM implementation will represent concrete resources with ordinary-object runtime
subclasses, initially:

```text
ProtosTcpConnectionValue extends ProtosObjectValue
ProtosTcpListenerValue   extends ProtosObjectValue   // consumed by I028-D
```

Host/backend resource identity, acquisition handles, directional I/O state, endpoint backing and
other native/runtime custody remain opaque Java/runtime fields, never ordinary Protos slots merely
because the object is live. Each concrete object delegates to the exact standard frozen protocol
prototype required by D052.

Standard TCP selectors are installed once per applicable shared/context-local standard protocol
prototype rather than materialized as per-resource local closures. This is an implementation scaling
choice under the D052 observable topology, not a new language method category.

Actor/P transfer machinery will explicitly reject concrete TCP live-resource values and authority-
bearing ordinary graphs reaching them, preserving the D047 fail-closed boundary. PLAT003 adds no
resource proxy/delegation contract.

## Connect acquisition architecture

`connectTcp` reuses the established host-neutral acquisition machinery rather than creating a second
network-specific Future/cancellation universe. The implementation should reuse/generalize the
existing `ProtosIoOperation` commitment model and the `ProtosFilesystemOpenFlow` pattern:

1. validate/capture the recognized endpoint before backend acquisition attributable to that request;
2. create one ordinary Future/operation for the acquisition;
3. permit cancellation to win only while the operation is still semantically uncommitted;
4. transfer a newly acquired TCP resource only when the operation can publish that result;
5. release backend/native custody when an acquired resource arrives after cancellation/terminalization
   or after another completion has already won; and
6. map backend failure through the existing portable I/O Error boundary.

The backend completion interface must therefore make release of an untransferred acquired resource
explicit. It must not depend on garbage collection/finalization to satisfy D047 late-resource custody.

## Full-duplex I/O architecture

PLAT003 explicitly rejects literal reuse of the current single-head FIFO scheduling shape of
`ProtosByteIoFlow` as the permanent TCP connection scheduler. One pending read must not prevent an
otherwise admissible write from starting merely because both operations share one queue.

One logical TcpConnection instead uses independent directional admission/progress lanes with a shared
resource lifecycle:

```text
                    one TcpConnection
                         |
            +------------+------------+
            |                         |
       input/read lane            output/write lane
       ordered reads              ordered writes
       read shutdown              write shutdown
            |                         |
            +------------+------------+
                         |
                  shared close/custody
```

This is not a new TCP stream semantics. Each lane must preserve the already-normative ByteReadable or
ByteWritable ordering, snapshot, bounded-admission, cancellation and commitment rules; shared close
and half-close use the existing I/O lifecycle contracts. The implementation may later pipeline or
overlap operations to the degree those normative protocols permit.

## Scalability and backend freedom

The intended cardinality is:

- shared protocol behavior per standard runtime/context publication domain;
- one lightweight ordinary capability object plus necessary logical resource/flow state per live TCP
  resource;
- Futures/requests proportional to actually admitted outstanding operations; and
- backend reactors/pollers/completion engines selected independently of Protos object identity.

No connection requires a semantic thread, Actor, event loop, selector or JVM channel. I028-E may map
the same architecture to Java NIO, epoll, kqueue, io_uring, IOCP, brokered IPC, WASI or another backend
without changing D047/D052 observations.

If the ordinary object implementation's eager empty slot-map cost later matters at very high
cardinality, the runtime may optimize `ProtosObjectValue` storage (for example lazy empty slot storage
or shapes) provided ordinary slot/reflection semantics remain unchanged. PLAT003 does not justify a
less-ordinary Protos representation merely as a memory optimization.

## Rejected alternatives

- Per-resource duplicated standard Closure/slot installation: correctable but needlessly scales
  protocol representation with connection count.
- `ProtosRepresentedValue` for TCP resources: the current bridge does not participate fully in general
  ordinary object structural operations, so selecting it would risk an accidental observable
  pseudo-object category.
- One FIFO serializing read and write progress: creates avoidable full-duplex head-of-line blocking.
- Generic `HostResource` / `HostCapability` hierarchy: still premature across Process, Filesystem,
  Network, File, TcpConnection and TcpListener lifetime/transfer contracts.
- Backend-specific socket/channel objects in Protos semantics: violates D047 portability and D052
  ordinary-object topology.

## I028-D4 implementation evidence

D4 realizes the already-specified `listenTcp` acquisition contract at implementation version `0.2.298-SNAPSHOT` without changing PLAT003 or selecting a production backend. `ProtosStandardNetworkProtocol` snapshots the exact local listen-request shape before exercising the represented Network target, then `ProtosNetworkListenFlow` reuses the C4-style `ProtosIoOperation` commitment/cancellation/late-custody boundary. The host-neutral completion descriptor contains only opaque listener state, the acquired non-zero local port, the D3 listener backend and explicit untransferred-resource release custody. Successful materialization creates the existing ordinary accept-enabled TcpListener; fixed requested-port mismatch and malformed backend descriptors fail before transfer. The existing Network provider grows by one native Closure, so the total native boundary becomes 135/35. Backend choice remains I028-E work.

## I028-D3 implementation evidence

D3 consumes the ratified listener accept contract at implementation version `0.2.294-SNAPSHOT` without changing PLAT003 or selecting a production backend. `ProtosTcpListenerFlow` admits every `accept()` as a separate `ProtosIoOperation` on the D2 shared lifecycle rather than imposing one pending slot or an implementation-visible accept queue. The backend completion descriptor carries explicit untransferred-resource release custody plus logical local/remote endpoint snapshots and a host-neutral TcpConnection backend; the standard execution bridge validates those endpoints through the existing D048 recognizer before materializing the C-family TcpConnection. Cancellation-handle registration races, Actor termination, listener close cutover, late/duplicate success and backend failure reuse the established Future/I/O machinery. The shared listener protocol grows by one native Closure to three sites; total native boundary becomes 134/35. `listenTcp` and backend selection remain D4/E work.

## I028-D2 implementation evidence

D2 consumes the ratified listener topology at implementation version `0.2.292-SNAPSHOT` without changing PLAT003 or choosing a network backend. `ProtosStandardTcpListenerProtocol` installs only `localPort` and `close` on the shared hidden family prototype. Operational listeners retain the acquired port and one `ProtosTcpListenerFlow` as opaque runtime state; that flow reuses `ProtosIoLifecycle` for the whole-resource close cutover and is intentionally ready for D3 to admit multiple independent accept operations on the same lifecycle. Observation has no backend effect, resource close remains orthogonal to structural Object close, and no per-listener Closure, event-loop/thread/selector/channel identity, `accept`, or `listenTcp` is introduced. The native boundary becomes 133 sites across 35 providers.

## I028-C5 integrated closure evidence

C5 closes the I028-C consumer at implementation version `0.2.289-SNAPSHOT` without changing PLAT003. One integrated
conformance harness starts from the real host-neutral `Network.connectTcp` acquisition path and verifies
that the published result retains the ordinary TcpConnection topology, application-local slot/shadowing
semantics, strict live-family receiver domain, Actor/P confinement, endpoint structural observations and
pre-commit cancellation/late-resource custody already established by C1-C4. The retained native-boundary
guard remains 131 sites across 34 providers. No production backend, new runtime category, scheduling
identity, endpoint object-identity rule or additional native Closure is introduced. I028-D may now reuse
the same ratified live-resource architecture for TcpListener/accept work.

## I028-C4 implementation evidence

C4 realizes the ratified connect-acquisition architecture at implementation version `0.2.289-SNAPSHOT` without
choosing a production network backend. `ProtosNetworkConnectFlow` reuses `ProtosIoOperation` and the
existing cancellation-registration race bridge pattern: recognized endpoint preflight precedes the
host-neutral backend call, operation commitment occurs only when an acquired resource can be handed
off, and every late/duplicate/unmaterializable acquired descriptor has explicit release custody.
`ProtosStandardNetworkProtocol` adds one shared native `connectTcp` bridge to the authority-free
Network prototype, but exercises it only for the represented Network capability and interprets its
opaque host target only through the C4 host-neutral Backend interface. `ProtosIoLifecycle`'s internal
receiver type is widened to `Object` so the same operation machinery can own a represented capability;
its semantics and all existing ordinary-object consumers are unchanged. Fresh successful connections
reuse the C1-C3 ordinary-object/duplex/endpoint machinery. No socket/channel/reactor/thread/event-loop
identity or endpoint-identity strengthening is introduced.

## I028-C3 implementation evidence

C3 completes the shared TcpConnection protocol surface at implementation version `0.2.288-SNAPSHOT` by adding the
two endpoint-observation Closures to the existing provider. Endpoint backing is retained as opaque
runtime state on operational resources and validated against the existing D048 representation bridge
before synchronous exposure; no backend query, network effect, new resource category or endpoint
identity contract is introduced. The provider therefore grows from five to seven native Closure
construction sites while the provider count remains unchanged. C4 consumes these observations during
host-neutral connect acquisition.

## I028-C2 implementation evidence

C2 closes at implementation version `0.2.286-SNAPSHOT` with the selected duplex architecture realized
without choosing a network backend. `ProtosStandardTcpConnectionProtocol` installs five shared
I/O/lifecycle Closures on the hidden family prototype, never per resource. Each operational
`ProtosTcpConnectionValue` owns one host-neutral `ProtosTcpConnectionFlow`; that flow injects one
`ProtosIoLifecycle` into two independent existing `ProtosByteIoFlow` instances. The input lane therefore
retains ByteReadable ordering/cancellation/rebuffering while the output lane independently retains
ByteWritable snapshot/admission/ordering, and neither lane's pending head serializes the other.
Directional shutdown stays lane-local while whole-resource close cuts over the shared lifecycle and
performs one backend-release action only after the shared logical operation set permits it. Resource
close does not mutate ordinary Protos structural state. C2 adds no endpoint observation, acquisition,
NIO/socket/reactor identity, or production backend; C3 remains the endpoint-observation slice.

## I028-C1 implementation evidence

C1 publishes the first concrete consumer of this architecture at implementation version `0.2.285-SNAPSHOT`. The
JVM wrapper is `ProtosTcpConnectionValue extends ProtosObjectValue`; its immediate parent is one
source-owned runtime-only standard protocol prototype retained outside public Prelude bindings, and
its opaque resource-state field is not a Protos slot. The protocol prototype is frozen and shared
canonically across Actor/P snapshot boundaries, while the concrete wrapper and ordinary graphs whose
parent chain reaches it are rejected explicitly. No selector is installed yet, so C1 does not create
per-resource protocol closures or prematurely choose the duplex scheduler/backend. C2 is the bounded
consumer that will attach the ratified protocol/lifecycle surface.

## Deliberately deferred

PLAT003 selects no concrete production network backend, reactor count, polling/completion API, thread
pool, buffer pool, broker wire protocol or OS-specific socket options. Those remain I028-E work.
Endpoint object-identity semantics remain outside PLAT003 and outside this architecture checkpoint.
