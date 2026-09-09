# I028 — Core Networking Foundation

Status: **IN_PROGRESS**
Normative dependencies: D047 / specification revision `0.1.388` — RATIFIED; D048 / specification revision `0.1.391` — RATIFIED
Additional C/D topology dependency: D052 / specification revision `0.1.393` — RATIFIED
Platform architecture dependencies: PLAT002 — RATIFIED; PLAT003 — RATIFIED; PLAT006 — RATIFIED; PLAT007 — RATIFIED; PLAT009 — RATIFIED
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

## I028-C/D live-resource checkpoint — CLOSED by D052 + PLAT003

D052 / specification revision `0.1.393` closes the previously unspecified observable object
topology of acquired TcpConnection/TcpListener resources: ordinary OPEN identity-bearing objects,
canonical frozen authority-free family protocol parents, no required public TCP family Prelude
bindings, ordinary local-slot/shadowing behavior and receiver-domain enforcement that prevents
delegation from manufacturing authority. Exact endpoint `===` identity remains intentionally
unspecified.

PLAT003 separately selects only durable JVM implementation architecture: ProtosObjectValue-derived
resource wrappers with opaque host state, shared protocol installation, reuse of the existing
I/O-operation acquisition/cancellation/late-custody machinery, and independent read/write progress
lanes with one shared lifecycle. No production backend is selected by this checkpoint. I028-C remains
READY to implement `connectTcp`; I028-D will consume the same topology for listener/accept work.

## Planned implementation decomposition after the checkpoint

- **A — address/endpoint ordinary-object foundation — CLOSED:** A1/A2 publish the canonical ordinary data factories and A3 proves those same values traverse Actor/P through the pre-existing ordinary snapshot/rematerialization machinery with no networking-specific transfer path.
  - **A1 — IpAddress local foundation — CLOSED (`0.2.278-SNAPSHOT` / `SAME_COMMIT`):** source-backed canonical ordinary prototype, `IpAddress(version, bits)` through ordinary invocation, exact unbounded-Integer IPv4/IPv6 validation, transparent `recognizes`, structural `==`/`hash`, frozen successful values and frozen Prelude prototype, plus ordinary-Protos conformance. No `IpEndpoint` or Actor/P transfer.
  - **A2 — IpEndpoint local foundation — CLOSED (`0.2.279-SNAPSHOT` / `SAME_COMMIT`):** canonical frozen ordinary Prelude factory/prototype, recognized-IpAddress + exact unbounded-Integer port validation, transparent recognition, structural equality/hash, exact address retention and ordinary-Protos conformance. No Actor/P transfer.
  - **A3 — Actor/P transfer + close A — CLOSED (`0.2.280-SNAPSHOT` / `SAME_COMMIT`; implementation version unchanged):** retained Protos-source conformance proves Actor and P rematerialization preserve canonical IpAddress/IpEndpoint parents, exact recognized frozen state, nested address data, structural equality/hash and fresh destination identities; Actor uses its deterministic harness and P uses existing P runtime-test orchestration because Test Tool direct inspection intentionally requires cooperative idle before Future inspection; no production transfer special case is added.
- **B — Network capability + bootstrap provisioning — CLOSED:** PLAT002 selects a represented live Network authority below a canonical source-backed prototype; B1 publishes only that prototype/architecture record and B2-B5 implement the capability, bootstrap confinement and closure without selecting the TCP backend.
  - **B1 — canonical Network prototype — CLOSED (`0.2.282-SNAPSHOT` / `SAME_COMMIT`):** publish frozen source-backed standard `Network` in Prelude, register ratified PLAT002, update exact Core inventories and retain zero authority/backend/native-Closure construction.
  - **B2 — represented Network capability + transfer confinement — CLOSED (`0.2.283-SNAPSHOT` / `SAME_COMMIT`):** implement the PLAT002 represented wrapper with canonical Network parent, opaque host target and explicit Actor/P fail-closed transfer, including authority-bearing descendants; no RootActor endowment or TCP operations.
  - **B3 — optional RootActor `network` bootstrap endowment — CLOSED (`0.2.284-SNAPSHOT` / `SAME_COMMIT`):** host-granted represented Network is an optional exact local `network` only on the initial RootActor module/standalone entry; absence is a missing slot, imports/new Actors receive no ambient grant, and Process exposes no Network accessor.
  - **B4 — ambient/import/Actor/P confinement conformance — CLOSED (`0.2.284-SNAPSHOT` / `SAME_COMMIT`; implementation version unchanged):** retained integrated evidence proves the authority-free Network prototype and Process protocol cannot recover a concrete grant, imports/non-root Actors cannot resolve RootActor `network` ambiently, and the exact B3 bootstrap capability is rejected by Actor/P transfer; no production or TCP/backend change.
  - **B5 — close B — CLOSED (`0.2.284-SNAPSHOT` / `SAME_COMMIT`; implementation version unchanged):** reconcile B1-B4 retained evidence, close the represented Network capability/bootstrap boundary, and release I028-C without selecting a TCP backend.
- **C — TCP connection + `connectTcp` — CLOSED under D052/PLAT003:** ordinary live-resource topology, host-neutral async acquisition, cancellation/late resource custody, independent read/write progress with shared close/half-close lifecycle, endpoint observation, transfer confinement and integrated C-level conformance are complete without strengthening endpoint `===` identity or selecting a production backend.
  - **C1 — ordinary TcpConnection resource/prototype + transfer foundation — CLOSED (`0.2.285-SNAPSHOT` / publication commit):** source-owned runtime-only frozen authority-free protocol parent, ordinary OPEN `ProtosTcpConnectionValue` with opaque host state and ordinary application slots, exact canonical parent preservation, no public TcpConnection Prelude binding, plus explicit Actor/P rejection of the live capability and authority-bearing descendants. No TCP selector, acquisition or backend yet; native boundary remains 123/32.
  - **C2 — TcpConnection protocol + duplex lifecycle foundation — CLOSED (`0.2.286-SNAPSHOT` / publication commit):** install the five D052 Byte I/O/lifecycle selectors once on the shared hidden protocol parent; compose two independent existing Byte-I/O lanes over one shared Closable lifecycle so read and write progress do not head-of-line block each other; retain bounded write snapshots, cancellation, close and directional-shutdown contracts without selecting a network backend. Endpoint observations remain C3.
  - **C3 — endpoint observations — CLOSED (`0.2.288-SNAPSHOT` / publication commit):** complete `localEndpoint` / `remoteEndpoint` as synchronous recognized `IpEndpoint` observations over runtime-held logical snapshots, with receiver-domain/arity validation and no network/backend work; no endpoint `===` identity relation is selected.
  - **C4 — host-neutral `connectTcp` acquisition — CLOSED (`0.2.289-SNAPSHOT` / publication commit):** install the single standard Network `connectTcp` selector, require an actual represented Network capability plus recognized endpoint before authority exercise, reuse ordinary I/O Future commitment/cancellation, materialize the fresh TcpConnection only at successful handoff, and explicitly release late/duplicate/unmaterializable backend resources; no concrete production backend or endpoint identity strengthening.
  - **C5 — integrated C conformance + closure — CLOSED (`0.2.289-SNAPSHOT` / publication commit; implementation version unchanged):** an acquired C4 connection is retained as the same ordinary C1-C3 resource family, preserves ordinary local-slot/shadowing behavior, rejects authority manufacture through descendants, remains non-transferable through Actor/P, and preserves pre-commit cancellation/late-resource custody; retained native-boundary evidence remains 131/34. I028-C is CLOSED.
- **D — TCP listener + `listenTcp` — CLOSED under D047/D052/PLAT003:** exact request snapshot/validation, `localPort`, concurrent pending accepts, lifecycle/admission behavior, transfer confinement and integrated D-level conformance are complete without selecting a production backend or strengthening endpoint `===` identity.
  - **D1 — ordinary TcpListener resource/prototype + transfer foundation — CLOSED (`0.2.290-SNAPSHOT` / publication commit):** source-owned runtime-only frozen authority-free protocol parent, ordinary OPEN `ProtosTcpListenerValue` with opaque host state and ordinary application slots, exact canonical parent preservation, no public TcpListener Prelude binding, plus explicit Actor/P rejection of the live capability and authority-bearing descendants. No listener selector, acquisition or backend yet; native boundary remains 131/34.
  - **D2 — `localPort` + `close` + shared listener lifecycle — CLOSED (`0.2.292-SNAPSHOT` / publication commit):** install `localPort` and resource `close` exactly once on the shared hidden listener protocol; retain acquired-port observation as synchronous/no-backend work and reuse the standard I/O lifecycle for one close cutover without changing ordinary structural OPEN/CLOSED/FROZEN state. No `accept`, `listenTcp` or production backend yet; native boundary becomes 133/35.
  - **D3 — `accept()` + concurrent pending accepts + cancellation/late custody — CLOSED (`0.2.294-SNAPSHOT` / publication commit):** install one shared `accept` selector; admit each call as an independent operation on the D2 lifecycle, support multiple pending Futures without a semantic FIFO/owner-thread rule, reuse pre-commit/Actor cancellation and close cutover, materialize fresh accepted TcpConnections only from recognized logical endpoint snapshots, and explicitly release late/duplicate/unmaterializable resources. No `listenTcp` or production backend yet; native boundary becomes 134/35.
  - **D4 — `Network.listenTcp(localRequest)` + exact request validation/capture + listener acquisition — CLOSED (`0.2.298-SNAPSHOT` / publication commit):** install the second shared Network acquisition selector; validate/capture exactly local `ipVersion`/`address`/`port` before backend effect, preserve canonical-null address/port request semantics, reuse ordinary Future cancellation/commitment and late-resource custody, and materialize one fresh accept-enabled D1-D3 TcpListener with its acquired non-zero port. No production backend or host wildcard/ephemeral convention is selected; native boundary becomes 135/35.
  - **D5 — integrated D conformance + closure — CLOSED (`0.2.300-SNAPSHOT` / publication commit; implementation version unchanged):** an actual D4-acquired listener retains the ordinary D1 family, D2 observation/close lifecycle, D3 concurrent accept/custody and Actor/P confinement; accepted resources remain the existing C-family TcpConnections. Native-boundary evidence remains 135/35. I028-D is CLOSED.
- **E — production backend portability/scalability — IN_PROGRESS:** PLAT006/PLAT007/PLAT009 are RATIFIED. E1/E2/E3A are CLOSED; E3B is READY. E4/E5 remain dependency-gated.
  - **E1 — bounded JDK-NIO poller primitive — CLOSED (`0.2.305-SNAPSHOT` / `SAME_COMMIT`):** internal Selector-owning daemon platform thread, concurrent control queue + wakeup, readiness dispatch, poller-thread-only registration/interest mutation, isolated callback failure and idempotent registered-channel release. No TCP operation, Network provisioning, poller-count/sharding policy or native transport.
  - **E2 — NIO endpoint bridge + `connectTcp` acquisition — CLOSED (`0.2.306-SNAPSHOT` / `SAME_COMMIT`):** decode recognized numeric endpoints without DNS, preserve exact IPv4/IPv6 family and Network-owned IPv6 scope, perform non-blocking `SocketChannel` immediate/`OP_CONNECT` acquisition, materialize the actual recognized local endpoint, and hand opaque channel custody through the existing C4 commit/cancel/late-release contract. No duplex byte I/O or production Network wiring yet.
  - **E3 — production TcpConnection duplex backend — IN_PROGRESS under PLAT009:** E3A closes the generic first-effect gate; E3B is READY for the NIO read lane, E3C remains gated on E3B for non-blocking partial writes, and E3D retains directional shutdown/close plus integrated E3 closure.
    - **E3A — host-neutral first-effect attempt gate — CLOSED (`0.2.309-SNAPSHOT` / `SAME_COMMIT`):** add transient `ATTEMPTING_FIRST_EFFECT` arbitration to `ProtosIoOperation`, preserve first-arrival cancellation-vs-close cutover ordering, and expose it to asynchronous ByteWritable backends through a richer `WriteCompletion` subtype without migrating existing backends. No socket readiness or TCP byte transfer is implemented by this slice.
    - **E3B — NIO read lane + independent readiness — READY:** implement non-blocking `SocketChannel` read readiness, EOF/failure handling and bounded rebuffer-compatible cancellation while leaving write readiness untouched.
    - **E3C — NIO write lane + PLAT009 partial-write arbitration — BLOCKED_BY_E3B:** implement ordered partial `SocketChannel.write` progress with `OP_WRITE` and the E3A first-effect gate.
    - **E3D — directional shutdown/close + integrated E3 closure — BLOCKED_BY_E3B_E3C:** implement physical half-close coordination, retain shared connection close custody and close E3 with integrated duplex evidence.
  - **E4 — production TcpListener/accept + PLAT007 composite IPv6-only backend — BLOCKED_BY_E3:** implement listener acquisition, concurrent accepts and the ratified all-or-nothing concrete-IPv6 composite listener baseline.
  - **E5 — production Network provisioning/wiring + integrated E closure — BLOCKED_BY_E2_E3_E4:** provision the production authority target through applicable host entry points, retain backend lifecycle/custody evidence and close E before final I028-F conformance.
- **F — cross-slice conformance/native-boundary closure:** cancellation races,
  late custody, multiple-accept scale evidence and Actor/P non-transferability.

## I028-E production-backend checkpoint — RELEASED by PLAT006

PLAT006 is RATIFIED after explicit project-owner approval following an expanded
cross-language/runtime review. The durable JVM architecture is a host I/O
operation engine whose upper contract is independent of readiness-vs-completion
machinery. The initial production implementation is bounded non-blocking JDK NIO
using `SocketChannel` / `ServerSocketChannel` plus `Selector` readiness
multiplexing.

Backend host/system threads never execute Protos code or enter a Protos Truffle
Context merely to service network I/O. Existing D047/D052/PLAT003
Future/commit/cancel/late-custody, listener-close, independent accept,
full-duplex and Actor/P confinement behavior remains authoritative above the
engine. No thread, selector, event-loop, fd, JVM-channel or backend identity
becomes observable Protos semantics.

Exact poller cardinality, sharding, affinity, buffer tuning and any future
epoll/kqueue/io_uring/IOCP/Netty backend remain deliberately deferred. A future
completion/native backend must preserve the same logical operation boundary and
pass equivalent cancellation/lifetime/late-completion/custody conformance.

I028-E is released to bounded implementation decomposition. If implementation
exposes another substantive durable architecture decision, the affected slice
must stop and cross the normal explicit approval gate before proceeding.

## I028-E IPv6-only listener checkpoint — RELEASED by PLAT007

PLAT007 is RATIFIED after explicit project-owner approval following mainstream
runtime plus Truffle/GraalVM review including Apple Pkl and future-backend
scalability analysis.

The initial public-JDK NIO backend must not use a potentially dual-stack IPv6
wildcard socket to implement D047's IPv6-only listener. For an IPv6
`address: null` request it captures the concrete IPv6 addresses authorized by
the supplied Network at acquisition time and builds one logical TcpListener from
individually bound IPv6 `ServerSocketChannel` components multiplexed by the
PLAT006 engine.

All components must share one acquired non-zero local port and construction is
all-or-nothing: failure releases every partial component before the acquisition
fails. Scope remains Network-authority state; explicit `IpAddress` values,
including `::`, are never reinterpreted as canonical-null wildcard requests.
Filtering IPv4 only after `accept`, relying on unsupported JDK internals, and
requiring JNI as the portable baseline are rejected.

The composite mechanism is the portable conformance baseline, not a permanent
high-address-cardinality optimum. A future native/FFM/framework/WASI/brokered
backend may replace it — including an O(1)-socket `IPV6_V6ONLY` implementation —
behind the unchanged PLAT006 operation boundary if it preserves all portable
semantics and conformance.

I028-E is released again to bounded implementation decomposition. Exact address
enumeration caching, candidate-port retry policy, poller sharding/affinity,
dynamic rebinding and native-backend mechanism remain deliberately deferred. If
implementation exposes a new substantive durable choice, stop that slice and
cross the explicit approval gate before proceeding.

## I028-E3A host-neutral first-effect attempt gate implementation

Closed at implementation version `0.2.309-SNAPSHOT` as the first mechanical consumer of
ratified PLAT009.

`ProtosIoOperation` now has a transient `ATTEMPTING_FIRST_EFFECT` phase that is
neither uncommitted terminal eligibility nor irreversible commitment. A
cancellation or whole-resource close arriving during that phase records the same
first-arrival cutover precedence that an ordinary uncommitted operation already
had, but leaves the Future pending until the backend classifies the physical
attempt. Zero effect applies that pending pre-commit cutover; positive first
effect commits before the pending zero-effect cutover can rewrite the result.

The operation/lifecycle monitor protects only these bounded transitions. E3A
performs no host I/O under that monitor and adds no global coordinator, thread,
poller or backend identity.

`ProtosByteIoFlow` continues to expose the existing `WriteCompletion` contract
to ordinary backends and additionally supplies a `FirstEffectWriteCompletion`
subtype for async backends that need PLAT009 arbitration. Existing File, process
stream and test backends therefore require no migration. Terminal success/failure
also defensively settles a still-open first-effect attempt from its known
non-empty-write aftermath.

Focused evidence covers cancellation and close during the uncertainty window,
positive-effect commitment, zero-effect failure, preservation of
cancellation-vs-close first-arrival ordering, and continued ordered-flow
usability after zero-effect cancellation.

E3A adds no NIO read/write readiness, no socket half-close behavior, no
production Network provisioning, no poller count/sharding/affinity choice and no
native-boundary change. E3B is READY.

## I028-E3 first-effect arbitration checkpoint — RELEASED by PLAT009

PLAT009 is RATIFIED after explicit project-owner approval following the concrete
E3 partial-NIO-write race analysis and expanded review across mainstream async
runtimes plus the Truffle/GraalVM ecosystem, including Apple Pkl.

The normative ByteWritable contract is unchanged. The runtime may transiently
mark an operation as attempting its first irreversible effect without committing
it. During that uncertainty window, cancellation/Actor termination/lifecycle
cutover may be recorded and may request best-effort backend cancellation, but a
zero-effect terminal outcome cannot be published until the host aftermath is
known sufficiently to preserve the existing contract.

An attempt proven to have zero irreversible effect returns to pre-commit
arbitration, where existing cancellation/lifecycle rules decide any pending
cutover. An attempt proven to have made the first positive irreversible
contribution commits before any competing zero-effect cutover can rewrite its
outcome. Terminal success/failure remains separate and later partial output may
continue under the existing hidden contiguous-prefix rules.

Host I/O is never performed while holding lifecycle synchronization merely to
preserve this gate. State is local to the already-admitted operation/resource
lifecycle, adds no global coordinator/thread, and exposes no selector/thread/fd/
native-request/backend identity. The same boundary remains usable by readiness,
completion, native, FFM, WASI and brokered backends.

PLAT009 defines no new observable precedence among cancellation, lifecycle
close, Actor termination and failure. Exact Java representation, batching,
buffer policy, poller/sharding/affinity and native-backend choice remain
deliberately deferred.

I028-E3 is released to bounded implementation. If implementation exposes another
substantive semantic or durable architecture choice, stop that slice and cross
the explicit approval gate before proceeding.

## I028-E2 NIO endpoint bridge + non-blocking connect acquisition

Closed at implementation version `0.2.306-SNAPSHOT` under D047/D052 and ratified
PLAT003/PLAT006/PLAT007. E2 implements only the production NIO acquisition path
consumed by the existing C4 `Network.connectTcp` flow.

The backend decodes exact numeric `IpEndpoint` state without DNS or textual
round-tripping, opens the matching JDK `INET`/`INET6` `SocketChannel`, and uses
non-blocking immediate connect or `OP_CONNECT` + `finishConnect`. IPv6 scope
interpretation is provided only by host-internal Network authority. The resolver
may not change the requested 128 bits, and link-local IPv6 fails unless the
authority supplies explicit scope; the transport never guesses an interface.

Successful acquisition snapshots the actual local IP address and non-zero port
as a fresh recognized standard `IpEndpoint` under the canonical prototypes
captured from the already-validated request. The supplied endpoint remains the
logical remote snapshot, so E2 does not strengthen endpoint `===`.

The physical channel crosses C4 only through its existing commitment/cancellation
bridge with explicit untransferred-release custody. Post-connect selector
interest is zero until E3 adds duplex readiness. E2 implements physical close for
resource custody but deliberately leaves read/write/half-close, listener/PLAT007,
production Network provisioning, poller count/sharding/affinity and native
transport work deferred. E3 is READY.

## I028-E1 bounded JDK-NIO poller primitive

Closed at implementation version `0.2.305-SNAPSHOT` under ratified PLAT006/PLAT007. E1 adds only the reusable internal readiness substrate consumed by later E slices: one `ProtosNioHostIoPoller` owns one JDK `Selector`, one daemon platform thread, a concurrent host-control queue and every channel registered with that poller. Cross-thread control admission wakes a blocked selector; channel registration and interest mutation are enforced on the owning poller thread; selected handlers are isolated so one backend callback failure does not terminate unrelated polling; shutdown is idempotent, stops new admission, drains already-admitted control work and releases every registered channel.

E1 deliberately does **not** choose how many pollers a production engine creates, how resources are sharded or migrated, or which Actor/Process/resource maps to which poller. It introduces no public or Protos-visible thread/event-loop/selector/channel identity and performs no guest Truffle execution. It also adds no `connectTcp`, `listenTcp`, accept, byte-I/O or native transport implementation. E2 is released to translate validated numeric endpoints to inert host address state synchronously before poller submission and implement non-blocking connection acquisition over this primitive.

Focused evidence uses a real JDK `Pipe` to prove readiness delivery, verifies control work executes on the dedicated non-virtual platform poller rather than the caller, exercises concurrent exactly-once control submissions, proves a failed control callback does not kill the shared poller, and proves idempotent close releases registered channels and rejects later work. Full-suite validation remains required before publication.

## I028-D5 / I028-D closure

Closed at implementation version `0.2.300-SNAPSHOT` without an implementation-version, production-runtime, specification or native-boundary change. D5 adds one integrated retained harness starting from the actual D4 `Network.listenTcp` acquisition result. The acquired value remains the ordinary OPEN D1 TcpListener family, D2 `localPort` and resource-close behavior compose on that same capability, D3 admits multiple independent accepts and materializes existing C-family TcpConnections from recognized endpoint snapshots, and the live listener plus authority-bearing descendants remain rejected by Actor/P transfer. Closing the listener cuts over a pending accept through the shared lifecycle and explicit late-resource custody releases any connection that arrives after that cutover. A cancelled listen acquisition likewise never publishes listener authority and releases a late listener descriptor.

D5 re-runs the retained D1-D4/C/native-boundary evidence. The Core boundary remains **135 sites / 35 providers**: D5 adds no production native Closure, selector, backend, socket/channel/reactor identity or endpoint `===` contract. I028-D is CLOSED. I028-E is released only for backend design/audit; no backend architecture is selected by this closure.

## I028-D4 Network.listenTcp acquisition

Published at implementation version `0.2.298-SNAPSHOT`. D4 completes the host-neutral listener-acquisition front. `ProtosStandardNetworkProtocol.listenTcp` requires the actual represented Network authority and snapshots exactly three own request fields before the authority target is invoked: ordinary unbounded Integer IP version 4/6, canonical-null or recognized matching-version IpAddress, and canonical-null or ordinary unbounded Integer port in 1..65535. Delegated fields, missing/extra own slots, fixed-width/coincidental values and version mismatch fail as `InvalidIOArgument` without attributable backend effect. The captured request carries no caller object or host wildcard/ephemeral convention. `ProtosNetworkListenFlow` reuses `ProtosIoOperation` commitment, Future/Actor cancellation and the C4 cancellation-registration bridge; every late, duplicate or unmaterializable listener descriptor has explicit release custody. Successful handoff materializes one fresh ordinary D1-D3 TcpListener with an actual non-zero local port and the existing accept materializer. A fixed requested port must match the acquired port before transfer. No production backend, socket/channel/reactor identity or endpoint `===` strengthening is introduced. The audited native boundary becomes 135 sites across 35 providers. D5 is READY.

## I028-D3 TcpListener concurrent accept/custody

Published at implementation version `0.2.294-SNAPSHOT`. D3 completes the listener's three-selector D052 protocol by adding one shared `accept` bridge. Every invocation admits one independent `ProtosIoOperation` on the same D2 `ProtosIoLifecycle`; there is no runtime-global or listener-head FIFO that serializes otherwise-independent pending accepts, and completion order is whatever the logical listener/backend establishes. Pre-commit Future cancellation, Actor termination and listener-close cutover reuse the existing cancellation/commitment machinery. A successful backend descriptor commits once, reuses the existing D048 endpoint recognizer before constructing the already-standardized TcpConnection family, and explicitly releases every late, duplicate, cancelled or malformed untransferred connection. D3 adds no `listenTcp`, socket/channel/reactor identity, production backend or endpoint `===` strengthening. The audited native boundary becomes 134 sites across 35 providers. D4 is READY.

## I028-D2 TcpListener localPort/close lifecycle

Published at implementation version `0.2.292-SNAPSHOT`. D2 installs `localPort` and `close` once on the hidden frozen TcpListener protocol parent. Only an operational acquired-family wrapper with that exact parent may exercise the behavior; ordinary descendants can inherit lookup but fail the receiver-domain check before resource state is touched. The acquired non-zero local port is retained as opaque runtime state and `localPort()` returns an ordinary unbounded Integer synchronously without backend work. Resource `close()` reuses one `ProtosIoLifecycle`, commits at invocation, shares one backend release outcome across fresh follower Futures, maps release failure through portable `IOError`, and does not call structural `Object.close()`, so ordinary mutation state remains orthogonal. D2 adds no `accept`, `listenTcp`, socket/channel/reactor identity or production backend. The audited native boundary becomes 133 sites across 35 providers. D3 is READY.

## I028-D1 TcpListener ordinary-resource foundation

Published at implementation version `0.2.290-SNAPSHOT`. D1 consumes the already-ratified D052/PLAT003 listener
topology without selecting a backend or installing listener operations. Core now owns one runtime-only
`_coreTcpListenerPrototype`: it delegates directly to Object, is removed before public Prelude
publication, is frozen with the shared standard graph, and is retained only by `ProtosPrelude` for
runtime resource construction/transfer identity. `ProtosTcpListenerValue` is an ordinary structurally
OPEN `ProtosObjectValue` child of that exact prototype, stores opaque non-slot runtime resource state,
and remains fully available for ordinary application local slots. Actor/P transfer rejects the
concrete live resource explicitly and rejects an ordinary descendant by recursively reaching that
parent authority, while an authority-free ordinary descendant of the hidden frozen protocol prototype
remains transferable and keeps the canonical prototype parent. The public Prelude gains no
`TcpListener` binding; `accept`, `localPort`, `close`, `listenTcp` and the production backend remain
outside D1. No native Closure is introduced, so the retained Core native boundary remains 131 sites
across 34 providers. D2 is READY.

## I028-C5 / I028-C closure

Closed at implementation version `0.2.289-SNAPSHOT` without an implementation-version, production-runtime,
specification or native-boundary change. C5 adds one integrated retained harness over the actual
C4 acquisition result: the acquired value remains the ordinary OPEN C1 TcpConnection family, C3
endpoint observations preserve recognized structural equality without an identity strengthening,
ordinary application local slots may shadow inherited protocol names, a descendant cannot use
inherited standard TCP selectors to manufacture resource authority, and the acquired capability
plus authority-bearing descendants remain rejected by Actor/P transfer. A pre-commit-cancelled
connect remains cancelled when a resource arrives late and the explicit release callback owns that
late custody.

C5 also re-runs the retained C1-C4 focal suites and the executable Core native-boundary guard. The
Core boundary remains **131 sites / 34 providers**: C5 adds no production native Closure, selector,
backend, socket/channel/reactor identity or endpoint `===` contract. I028-C is CLOSED and I028-D is
READY to consume the already-ratified TcpListener topology under D047/D052/PLAT003.

## I028-C4 host-neutral `connectTcp` acquisition

Published at implementation version `0.2.289-SNAPSHOT`. C4 installs `connectTcp` once on the canonical frozen
Network prototype while requiring the original receiver to be an actual `ProtosNetworkCapabilityValue`
whose opaque authority target supplies the host-neutral acquisition contract. The complete D048
endpoint is recognized before the backend is invoked, so invalid requests exercise no network
authority. Each valid invocation starts one independent `ProtosIoOperation`; ordinary pre-commit
Future cancellation and Actor termination can win, while a successful backend resource commits only
at result handoff. Late, duplicate, cancelled or unmaterializable acquired resources carry an explicit
release callback and are never abandoned to GC/finalization. Backend failures and invalid backend
descriptors map through the existing portable `IOError` boundary.

Successful materialization creates one fresh operational `TcpConnection` with its backend-supplied
recognized logical local endpoint and the captured recognized request as its logical remote endpoint.
C4 relies only on D047 structural remote-endpoint equality and deliberately adds no `===` identity
contract. `ProtosIoLifecycle` is generalized internally from a `ProtosObjectValue` owner field to
`Object` solely so represented Network authority can reuse the established I/O-operation machinery;
this changes no Protos-visible lifecycle rule. No socket, channel, selector, reactor, event loop or
production backend is selected. Native Closure inventory becomes 131 sites across 34 providers. C5
is READY.

## I028-C3 TcpConnection endpoint observations

Published at implementation version `0.2.288-SNAPSHOT`. C3 completes the seven-selector D052 TcpConnection
protocol by adding synchronous `localEndpoint` and `remoteEndpoint` observation on the shared hidden
family parent. Operational runtime construction may retain recognized logical endpoint snapshots
outside ordinary Protos slots; the standard observation bridge revalidates D048 recognized
`IpEndpoint` shape before exposure and performs no backend/network operation. Ordinary descendants,
wrong arity and runtime-only endpointless fixtures fail the standard receiver-domain boundary. C3
makes no assertion about endpoint object identity across repeated calls or relative to a future
`connectTcp` argument; only the D047 structural remote-endpoint equality requirement remains
normative. Native Closure inventory is 130 sites across 33 providers. C4 is READY.

## I028-C2 TcpConnection shared protocol + duplex lifecycle foundation

Published at implementation version `0.2.286-SNAPSHOT`. C2 installs `read`, `write`, `close`,
`shutdownRead`, and `shutdownWrite` exactly once on the hidden frozen TcpConnection protocol parent.
An inherited standard selector accepts only an operational `ProtosTcpConnectionValue` whose immediate
parent is that exact prototype; ordinary descendants may inherit lookup but fail the standard
receiver-domain check before resource state is exercised. Semantic I/O argument validation remains in
the existing Future-returning Byte-I/O path, while receiver/arity failure remains ordinary synchronous
invocation failure.

The host-neutral `ProtosTcpConnectionFlow` composes two distinct `ProtosByteIoFlow` instances, one
read lane and one write lane, over one injected `ProtosIoLifecycle`. Reads remain ordered only with
reads and writes with writes, so a pending read cannot block an admissible write; both lanes reuse the
existing bounded write-snapshot, cancellation/rebuffering, Future commitment and directional-shutdown
machinery. The shared lifecycle establishes one close cutover across both lanes and one backend-release
outcome. Resource close never invokes structural `Object.close()`, so ordinary OPEN/CLOSED/FROZEN state
remains independent. No `localEndpoint`/`remoteEndpoint`, `connectTcp`, socket/channel/event-loop or
production network backend is included. The audited native boundary is 128 construction sites across
33 providers. C3 is READY.

## I028-C1 TcpConnection ordinary-resource foundation

Published at implementation version `0.2.285-SNAPSHOT`. C1 consumes D052/PLAT003 without selecting a backend or
installing TCP operations. Core now owns one runtime-only `_coreTcpConnectionPrototype`: it delegates
directly to Object, is removed before public Prelude publication, is frozen with the shared standard
graph, and is retained only by `ProtosPrelude` for runtime resource construction/transfer identity.
`ProtosTcpConnectionValue` is an ordinary structurally OPEN `ProtosObjectValue` child of that exact
prototype, stores opaque non-slot runtime resource state, and remains fully available for ordinary
application local slots. Actor/P transfer rejects the concrete live resource explicitly and rejects an
ordinary descendant by recursively reaching that parent authority, while an authority-free ordinary
descendant of the hidden frozen protocol prototype remains transferable and keeps the canonical
prototype parent. The public Prelude gains no `TcpConnection` binding, no TCP selector or `connectTcp`
exists yet, and no socket/channel/event-loop/backend is selected. Native Closure inventory remains
123 sites across 32 providers. C2 is READY.

## I028-B5 / I028-B closure

Closed at implementation version `0.2.284-SNAPSHOT` without an implementation-version, production-runtime,
specification or native-boundary change. B5 reconciles the retained B1-B4 evidence under D047/D048 and
ratified PLAT002: `Network` is a frozen authority-free standard prototype; concrete host-granted authority
is represented only by `ProtosNetworkCapabilityValue`; the optional exact RootActor initial-module
`network` endowment is absent when not granted and never becomes ambient through Prelude, Process,
imports or hosted non-root Actors; and the represented authority remains rejected by Actor and P. The
audited Core native boundary remains 123 sites / 32 providers. No connect/listen/TCP resource, backend,
event-loop, selector, socket or host-network implementation is introduced by B. I028-B is CLOSED and
I028-C is READY to implement the already-ratified D047 `connectTcp` surface while preserving the backend
separation deferred to later I028 work.

## I028-B4 closure

Closed at implementation version `0.2.284-SNAPSHOT` without an implementation-version or production-runtime
change. B4 retains one integrated JVM/Protos-source harness over the complete B boundary established by
B1-B3: the frozen `Network` prototype remains an authority-free Prelude value; the standard `Process`
prototype retains exactly its existing eight Process-I/O selectors and no Network recovery selector;
an imported module that actually tries to resolve lower-case `network` fails despite the importing
RootActor owning a grant; a hosted non-root Actor attempting the same lookup also fails; and the exact
`ProtosNetworkCapabilityValue` installed by B3 is rejected at both Actor and isolated-P transfer
boundaries. The previously retained B2/B3 tests remain focal evidence for prototype-vs-capability shape,
ordinary prototype-descendant transferability, optional-grant absence, exact RootActor timing and
standalone provisioning. No specification, production source, native Closure, connect/listen/TCP or
backend identity changes. B5 is READY for B-level reconciliation/closure.

## I028-B3 closure

Published at implementation version `0.2.284-SNAPSHOT`. B3 consumes the already-ratified D047/PLAT002 authority
model without adding a backend: one host-granted `ProtosNetworkCapabilityValue` may be retained as
bootstrap-stable Process-host state and is exposed only as the exact local `network` slot of the
initial RootActor module (or equivalent standalone initial activation) before its first source
expression. A missing grant produces no slot rather than `null`. Imported modules and newly hosted
Actors receive no implicit `network` local. The public Process protocol is unchanged and provides no
Network recovery path. The standalone host-neutral bootstrap preserves its prior signature and adds
an overload for an already-provisioned Network capability. No connect/listen/TCP/backend/native
Closure/specification change is included; B4 is released for retained confinement closure evidence.

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
