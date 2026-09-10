# PLAT006 — JVM TCP host I/O operation engine architecture

Status: **RATIFIED**

Nature: durable non-normative JVM/network-backend architecture decision

Approved by project owner: **2026-09-09**

Primary consumers: `I028-E`, then `I028-F`

Normative effect: **none** — D047 / D052 remain authoritative for observable
portable networking behavior; PLAT003 remains authoritative for the existing
JVM live-resource/acquisition/duplex-I/O boundary.

Coordination provenance: the architecture gate was first recorded as D054 /
GitHub #226. Current `AGENTS.md` classifies durable JVM/OS/backend choices that
remain semantically invisible to Protos as `PLATxxx`, so that gate was closed
as misclassified and the approved decision was reallocated to PLAT006 / #230.

## Decision boundary

I028-C and I028-D deliberately closed the portable TCP connection/listener
surface without selecting a production backend. I028-E must now provide a
scalable production JVM implementation while preserving the already-ratified
Future, commitment, cancellation, late-resource custody, listener-close,
full-duplex and Actor/P confinement contracts.

PLAT006 selects only the host/runtime architecture below that semantic boundary.
It does not add a Protos-visible thread, selector, reactor, event loop, file
descriptor, JVM channel, backend identity or scheduling rule.

## Selected architecture

The selected architecture is a **host I/O operation engine whose durable upper
contract is independent of readiness-vs-completion machinery**, with an initial
production JVM backend based on non-blocking JDK NIO.

The durable invariants are:

1. Protos networking submits logical host I/O operations and receives logical
   completions/failures. The engine boundary is expressed in terms of Protos'
   existing resource/operation lifecycle rather than selector/event-loop/native
   transport vocabulary.
2. Existing Protos `Future`, commitment, cancellation, late-resource custody,
   listener-close cutover, Actor/P confinement and duplex lifecycle semantics
   remain authoritative above the engine.
3. Backend threads are host/system threads. They do not execute Protos code and
   do not enter a Protos Truffle `Context` merely to service network I/O.
4. The initial production JVM backend uses non-blocking `SocketChannel` and
   `ServerSocketChannel` with `Selector` readiness multiplexing.
5. The initial backend uses a **bounded poller/worker group**. It does not create
   one platform thread per connection/resource and does not create one
   selector/event loop per Actor, Process or resource.
6. Exact poller cardinality, resource sharding, affinity, migration and tuning
   are operational policy, not Protos semantics and not fixed by PLAT006.
7. Read and write remain independently admissible over one shared
   `TcpConnection` lifecycle. A pending read must not head-of-line block an
   otherwise admissible write.
8. Multiple Protos `accept()` operations remain logically independent. A
   backend may internally serialize physical accept/readiness work, but it must
   not introduce a Protos FIFO guarantee, owner-thread identity or global
   listener-head serialization contract.
9. Cancellation may win only according to the already-ratified pre-commit rules.
   No backend may publish authority or lose ownership outside those rules.
   Every late, duplicate or unmaterializable resource remains under explicit
   custody/release.
10. The durable engine contract must not encode `Selector`, `SelectionKey`,
    `OP_READ`, `OP_WRITE`, epoll, kqueue, io_uring SQE/CQE, IOCP completion
    keys, Netty `Channel`/`EventLoop`, or equivalent backend-specific identity.
11. Future specialized backends such as epoll, kqueue, io_uring, IOCP or a
    Netty-based engine may replace the initial backend only behind the same
    host-neutral operation boundary and only if they preserve
    D047/D052/PLAT003 behavior.
12. Completion-oriented backends with stronger in-kernel submission/lifetime
    hazards require explicit conformance for cancellation races, late
    completion, buffer/resource lifetime, close/reuse races and
    descriptor-generation/ABA-style hazards before production enablement.

## Cross-runtime evidence

The project-owner approval followed an expanded comparison of mature language
and runtime families rather than JVM mechanisms alone.

### Pony

Pony is the closest actor-oriented precedent for the scheduling boundary:
network/asynchronous I/O service threads remain distinct from actor execution.
PLAT006 adopts the same separation principle: backend host threads produce
completion work for the runtime; they are not actor carriers and do not execute
guest code.

### Go and GHC

Go's network poller and GHC's event manager demonstrate that language-level
lightweight concurrency can remain independent from the physical kernel poller.
The runtime may multiplex many logical activities over a bounded host I/O
substrate without exposing that substrate as program identity.

### Rust / Tokio

Tokio's readiness-oriented design is especially relevant to the Protos
pre-commit cancellation model: readiness can be awaited without necessarily
performing the eventual read/write/accept syscall. This makes an initial
readiness backend a strong fit for explicit logical-operation state machines.

io_uring experience also demonstrates why PLAT006 must not equate the durable
engine contract with readiness. Completion-oriented engines can be valuable but
introduce harder operation-lifetime, cancellation, late-completion and
descriptor-reuse hazards that must remain below the Protos contract.

### Boost.Asio, .NET, OCaml Eio and Python asyncio

These ecosystems demonstrate the long-term value of keeping the logical async
operation surface independent from the physical reactor/proactor mechanism.
Different platforms or future implementations can use readiness or completion
without changing the upper resource and operation semantics.

### libuv, SwiftNIO and Netty

These runtimes/frameworks demonstrate practical high-cardinality scaling with a
bounded event/poller substrate serving many channels/resources. PLAT006 adopts
that cardinality principle without adopting their event-loop/channel identities
as Protos architecture.

### Java virtual threads and asynchronous channels

Virtual threads are scalable and remain available as a JVM mechanism elsewhere,
but they are not selected as the networking primitive because Protos already
owns distinct Actor/Process/Future/I/O-operation identity and cancellation.
Coupling each networking operation/resource to a virtual thread would add a
second durable concurrency identity without improving the Protos semantic
boundary.

`AsynchronousSocketChannel` / `AsynchronousServerSocketChannel` are likewise not
selected as the durable host contract. Their concrete cancellation and pending
accept constraints do not map as directly to Protos' independently pending
operations and explicit pre-commit custody rules as the selected operation
engine plus initial readiness backend.

## Why the selected shape is the Protos fit

PLAT006 follows the repository design philosophy:

- **observable behavior matters; machinery does not** — the backend remains
  invisible above the operation boundary;
- **keep platform differences at the boundary** — NIO is the initial JVM
  mechanism, not a language contract;
- **pay only for what you use** — ordinary networking does not require one host
  thread per resource and non-networking programs do not gain a new semantic
  thread/event-loop model;
- **scale by composition** — many connections, listeners, Actors and Processes
  compose over the same bounded engine rather than switching programming
  universes at higher cardinality;
- **prefer independence over coordination** — read/write lanes and independent
  accepts are not serialized merely for backend convenience;
- **minimize shared mutable state** — no single Protos-visible global reactor or
  event-loop object is introduced; and
- **generality is earned** — the engine is neutral only at the boundary proven
  useful by readiness and completion families, without implementing every
  possible backend now.

## Scalability rationale

The selected baseline changes the host-cardinality relationship from an
unbounded platform-thread-per-resource model to many resources multiplexed over
a bounded host I/O substrate.

The exact number of pollers/workers is intentionally not architecture. It can be
measured and tuned for CPU count, workload, operating system and future
implementation without changing Protos semantics or reopening PLAT006.

Actors and Processes do not own pollers. A larger Actor/Process population
therefore does not inherently multiply selectors/threads. Likewise, a future
distributed runtime may give each host/runtime instance its own I/O plane
without changing the language-level networking model.

The initial NIO backend also leaves a clean optimization path: a future native
backend may replace the physical engine while retaining the same logical
operation/completion and conformance boundary.

## Rejected alternatives

### Blocking sockets plus platform-thread-per-resource

Rejected as the production architecture because cardinality scales host threads
with resources/operations and introduces unnecessary scheduling/stack cost.

### Virtual-thread-per-operation or per-resource as the networking primitive

Rejected as the durable foundation, not as a general JVM mechanism. Virtual
threads scale well, but they add a second concurrency identity below Protos'
Actor/Process/Future model and their blocking-socket cancellation/lifetime
behavior is a poorer direct fit for independently cancellable operations sharing
one resource.

### JDK asynchronous channels as the durable engine contract

Rejected because it would make JDK completion-channel constraints own too much
of the runtime boundary. Protos still needs its own logical operation queues,
commit/cancel state and custody handling, so the durable abstraction should
remain Protos-owned.

### NIO reactor/readiness vocabulary as the permanent engine contract

Rejected even though NIO is the initial implementation. Encoding selector keys,
interest sets or readiness flags into the durable engine interface would make a
future completion backend artificially emulate a reactor and would leak today's
mechanism into tomorrow's architecture.

### Netty as the runtime architecture

Rejected as the baseline because Protos already owns Future, lifecycle,
cancellation, custody and resource semantics. Netty may be evaluated later as a
backend implementation, but its Channel/Future/EventLoop model does not become
the Protos runtime model.

### Direct io_uring / IOCP / native transport architecture now

Rejected as the baseline because it would narrow portability and prematurely
couple the engine to completion/lifetime semantics that require stronger
backend-specific conformance. Native transports remain an explicitly preserved
future specialization path.

## Deliberately deferred

PLAT006 does not select:

- exact poller/worker count;
- resource-to-poller sharding or migration;
- CPU affinity or NUMA policy;
- buffer-pool sizing or tuning;
- direct epoll, kqueue, io_uring or IOCP implementation;
- Netty dependency;
- automatic backend selection;
- DNS, UDP, TLS, QUIC or HTTP scope;
- endpoint `===` semantics;
- a Protos-visible thread/event-loop/scheduler concept; or
- any change to `ContextPolicy`.

If implementation of I028-E exposes a new substantive durable architecture
choice in one of these areas, that choice must cross the normal explicit
approval gate before the dependent slice proceeds.

## Consumer release

Publication of this ratified record releases `I028-E` from its platform-decision
blocker to bounded implementation decomposition. I028-E must implement the
host-neutral operation engine and initial JVM NIO backend without expanding the
decision into deferred tuning/native-backend questions.

`I028-F` remains the final cross-slice conformance/native-boundary closure and
must retain cancellation-race, late-custody, multiple-accept scale,
full-duplex-progress and Actor/P isolation evidence across the production
backend.
