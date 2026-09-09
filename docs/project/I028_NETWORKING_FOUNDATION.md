# I028 — Core Networking Foundation

Status: **IN_PROGRESS**
Normative dependencies: D047 / specification revision `0.1.388` — RATIFIED; D048 / specification revision `0.1.391` — RATIFIED
Additional C/D topology dependency: D052 / specification revision `0.1.393` — RATIFIED
Platform architecture dependencies: PLAT002 — RATIFIED; PLAT003 — RATIFIED
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
- **D — TCP listener + `listenTcp` — IN_PROGRESS:** exact request snapshot/validation,
  `localPort`, concurrent pending accepts, lifecycle/admission behavior.
  - **D1 — ordinary TcpListener resource/prototype + transfer foundation — CLOSED (`0.2.290-SNAPSHOT` / publication commit):** source-owned runtime-only frozen authority-free protocol parent, ordinary OPEN `ProtosTcpListenerValue` with opaque host state and ordinary application slots, exact canonical parent preservation, no public TcpListener Prelude binding, plus explicit Actor/P rejection of the live capability and authority-bearing descendants. No listener selector, acquisition or backend yet; native boundary remains 131/34.
  - **D2 — `localPort` + `close` + shared listener lifecycle — READY:** install the bounded observation/lifecycle surface on the shared hidden listener protocol without selecting the production backend.
- **E — production backend portability/scalability:** preserve D047 independently
  of NIO/epoll/kqueue/io_uring/IOCP/Network.framework implementation choices;
  subdivide mechanically if required.
- **F — cross-slice conformance/native-boundary closure:** cancellation races,
  late custody, multiple-accept scale evidence and Actor/P non-transferability.

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
