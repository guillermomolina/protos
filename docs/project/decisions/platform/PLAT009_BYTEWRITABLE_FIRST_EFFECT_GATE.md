# PLAT009 — Host-neutral first-effect attempt gate for asynchronous ByteWritable output

Status: **RATIFIED**

Nature: durable non-normative runtime/I/O architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: `I028-E3` and future asynchronous standard-output backends

Normative effect: **none** — `spec/io/BYTE_IO.md` remains authoritative for
observable ByteWritable cancellation, commitment, failure-prefix and ordering
semantics. PLAT009 selects only host/runtime machinery needed to implement those
already-defined semantics when the first irreversible output effect is not
atomically knowable at operation admission.

GitHub coordination provenance: the gate was initially allocated as PLAT008 /
issue #238 while the durable registry still ended at PLAT007. Concurrent I026
work durably ratified the unrelated Truffle replay-site decision as PLAT008
before this gate could publish. This approved architecture is therefore
reallocated mechanically to PLAT009; the identifier correction does not reopen
or alter the project-owner-approved decision.

## Decision boundary

I028-E3 must implement true non-blocking writes over `SocketChannel`. A single
host write attempt may contribute a positive prefix, contribute zero bytes, or
fail, and a cancellation/lifecycle request may race with that physical attempt.

Portable ByteWritable semantics already require:

- cancellation may win only while zero bytes from that write have irreversibly
  contributed;
- the first irreversible output contribution commits the write;
- after commitment, cancellation cannot rewrite the operation as though zero
  output occurred;
- a failed write may retain one hidden contiguous committed prefix; and
- host timing/backend identity is not observable Protos semantics.

The current terminal-only completion bridge cannot represent the interval in
which a host attempt is in flight and its first-effect aftermath is not yet
known. PLAT009 resolves only that internal arbitration gap.

## Selected architecture

The runtime adopts a **transient, host-neutral first-effect attempt gate** owned
by the existing I/O operation/lifecycle machinery.

```text
UNCOMMITTED
    |
    | begin first-effect attempt
    v
ATTEMPTING_FIRST_EFFECT
    |                    |
    | effect proven zero | first positive irreversible contribution
    v                    v
UNCOMMITTED          COMMITTED
```

Durable invariants:

1. Entering the transient attempt state is not commitment.
2. A backend begins such an attempt only while the operation is still eligible
   to perform its first irreversible effect under existing lifecycle rules.
3. While first-effect aftermath is unknown, cancellation, Actor termination,
   lifecycle close/cutover or equivalent existing cutover requests may be
   recorded and may request best-effort host cancellation, but they do not
   publish a semantically terminal zero-effect outcome merely because the host
   request remains unresolved.
4. An attempt proven to have contributed zero irreversible output returns/leaves
   the operation in pre-commit arbitration; any pending cutover is then resolved
   under the existing Protos operation/lifecycle rules.
5. An attempt proven to have made the first positive irreversible contribution
   commits before any competing or pending zero-effect cutover can rewrite that
   operation as cancelled or failure-atomic.
6. Terminal success/failure remains distinct from first-effect commitment. A
   committed write may remain pending across later readiness/completion cycles.
7. Contributed-prefix length remains internal and non-observable to ordinary
   Protos code.
8. Host/backend I/O is not executed while holding the operation/lifecycle
   monitor merely to preserve the gate. Synchronization protects bounded state
   transitions, not the duration of a host request.
9. No selector, thread, event loop, fd, native request, IOCP object, io_uring
   request, libuv request or broker request identity becomes semantic state.
10. The gate is local to one logical operation/resource lifecycle and creates no
    global arbitration lock or thread-per-write requirement.
11. Multiple physical partial contributions may follow the first commitment
    while preserving one ordered logical write.
12. A backend that cannot classify the first-effect aftermath sufficiently to
    preserve standard ByteWritable semantics must not invent a zero-effect
    cancellation result or weaken the contract.
13. PLAT009 defines no new user-visible precedence among cancellation, lifecycle
    close, Actor termination and failure. Existing normative/runtime authority
    remains responsible for that observable arbitration.

## Existing Protos precedent

`ProtosFileFlow.WriteCompletion` already has `commitFirstContribution()` for
positioned writes, while append/change/sync flows similarly distinguish
irreversible-effect commitment from terminal completion.

Those handshakes work where the backend can know immediately before the host
mutation that the first effect will occur. They are insufficient for a
non-blocking socket call that may return zero after the attempt. PLAT009 adds the
missing transient arbitration shape without giving TCP privileged direct access
to `ProtosIoOperation`.

I028-E3 does not migrate unrelated existing File/stream implementations merely
for API uniformity.

## Cross-runtime and Truffle evidence

### Truffle/GraalVM

Truffle does not supply one universal language-level I/O kernel. Languages and
runtimes own external-resource protocols while system threads and safepoint
mechanisms keep host work distinct from guest execution. The gate therefore
belongs to Protos runtime I/O state rather than AST nodes, instrumentation,
poller threads or Context entry.

### GraalJS + Node/libuv

GraalJS supplies ECMAScript execution while Node/libuv owns host I/O. libuv
cancellation may race with write progress and retains transferred-byte
aftermath. A cancellation request is therefore not proof of zero physical
effect. PLAT009 preserves the same distinction without exposing libuv-style
request identity.

### Apple Pkl

Apple Pkl keeps external resource access behind evaluator-provided
readers/providers, including external-process/message-based providers, with host
lifecycle/authority outside language-value identity. Pkl does not define this
ByteWritable race, but strongly supports the architecture boundary: external
mechanism remains runtime state while the language retains one semantic model.

### GraalPy

GraalPy demonstrates one language/runtime surface over selectable Java/native
POSIX backends and runtime-thread/safepoint delivery for host work, supporting a
backend-neutral operation gate.

### TruffleRuby, Espresso, Sulong/NFI

These implementations confirm that JVM/native host machinery remains below
language semantics. Native execution is especially not a suitable semantic owner
of Protos cancellation/commitment; a future native backend reports aftermath to
the same runtime gate.

### GraalWasm / WASI

Async start/finish-style host operations map naturally to a transient attempt
that may outlive submission and resolves only when zero-effect versus positive
contribution is known.

### Yona / SOMns

Non-blocking promise and actor/event-loop runtimes reinforce separation between
physical async scheduling and logical effect/order ownership.

## Wider async-I/O evidence

Rust/Tokio distinguishes pending/no-progress from positive write progress.
Boost.Asio completion reports transferred bytes with the completion aftermath.
libuv retains bytes written around cancellation races. Windows IOCP cancellation
does not make an in-flight request synchronously disappear. io_uring likewise
requires explicit late completion/lifetime custody.

The common lesson is that logical cancellation cannot be inferred solely from a
host cancellation request; first-effect aftermath needs explicit runtime
arbitration.

## Scalability rationale

PLAT009 adds O(1) bounded state to each already-admitted I/O operation and no
independent unbounded payload queue. Independent resources do not contend on one
global lock.

For JDK NIO the transient attempt normally spans one non-blocking
`SocketChannel.write()` call. For completion/native/brokered backends it may stay
pending while the host resolves the request, represented as asynchronous
operation state rather than a blocked Protos thread.

The same architecture scales across many connections, Actors, Processes,
Truffle Contexts, readiness pollers, io_uring/IOCP, FFM/Panama, WASI and
brokered/distributed host-resource implementations.

## Protos design fit

- semantics remain authoritative over backend convenience;
- backend identity remains invisible;
- one general first-effect uncertainty mechanism replaces TCP-specific tricks;
- no global coordination is added;
- simple operations pay only bounded local state;
- independent resources remain independently scalable;
- larger/native/distributed deployments keep the same Future/ByteWritable
  semantic universe; and
- generality is earned by the concrete E3 race plus materially different async
  runtime families.

## Rejected alternatives

### Pre-commit every non-empty write

Rejected because a non-blocking host write may contribute zero bytes or fail
without output.

### Commit only after observing positive progress, without a transient gate

Rejected because cancellation/close could publish a zero-effect terminal result
between physical contribution and runtime commitment.

### Progress callback without arbitration

Rejected because progress evidence alone does not serialize the same race.

### Hold lifecycle synchronization across host I/O

Rejected because it couples semantic arbitration to host-call duration and does
not scale to completion/brokered/remote operations.

### Let the NIO poller arbitrate

Rejected because selector/event-loop scheduling would become semantic authority
and would couple Protos to the initial PLAT006 backend.

### Give TCP direct `ProtosIoOperation` access

Rejected as a backend-specific privileged path that duplicates general
operation/lifecycle authority.

### Force full-write atomicity inside the backend

Rejected because internal buffering/retry cannot undo partial physical output
while the logical Future remains pending.

## Deliberately deferred

PLAT009 does not select exact Java class/method names, exact phase/token
representation, batching/coalescing, buffer sizes, NIO readiness-loop details,
poller count/sharding/affinity, unrelated File/stream migration, native bridge
technology, native backend timing, broker protocol, or any new Protos-visible
cancellation/failure precedence.

A newly exposed substantive decision during E3 must stop the affected slice and
cross the normal explicit approval gate.

## Consumer release

Publication of PLAT009 releases `I028-E3` from the first-effect arbitration
blocker. E3 may implement the connected TcpConnection duplex backend
mechanically under D047/D052, `spec/io/BYTE_IO.md`, PLAT003, PLAT006, PLAT007
and PLAT009 while retaining independent read/write progress, zero-effect
pre-commit cancellation, first-positive-effect commitment, hidden failure-prefix
accounting, bounded snapshots, backend invisibility and existing directional
shutdown/close semantics.

E4/E5 remain dependency-gated behind E3 and later implementation evidence.
