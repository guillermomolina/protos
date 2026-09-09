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
