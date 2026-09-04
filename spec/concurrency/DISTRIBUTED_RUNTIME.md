# Protos Distributed Runtime v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of the migrated Core distributed
runtime semantics: Process/Node/Cluster identity and lifecycle, distributed
reachability/knowledge, Groups, placement/routing infrastructure boundaries,
membership, partition/split-brain safety, and distributed Authority where those
contracts were normatively CLOSED in the former mixed concurrency ledger.

Legacy section numbers are retained for citation continuity. Unresolved design
topics remain in `docs/design/CONCURRENCY_DESIGN.md`, which is entirely
non-normative. Nothing in those design notes supplements or overrides the
contracts in this document.

## 37. Process Boundary

**CLOSED --- REVISED**

A Process is a Protos execution, isolation, and failure domain containing
one RootActor and any additional Actors hosted in that domain.

A Protos Process is not normatively an operating-system process or an
address-space boundary. A standalone launcher may map one Protos Process
to one operating-system process, while a managed runtime may host Protos
Processes differently, provided that all Protos isolation and failure
semantics are preserved.

Actor-to-Actor and Process-to-Process communication use the same
fundamental pass-by-value message semantics.

Two Protos Processes with established physical locality may communicate
using optimized mechanisms such as shared memory, mmap, IPC, local
sockets, or in-runtime transports. Different machines may use network
transports.

The transport and physical hosting arrangement must not change observable
message value semantics or expose cross-Process mutable references.

A Process is ephemeral execution capacity, not a durable application
identity.

If a Protos Process terminates, the Actors hosted by that Process
terminate. Terminating that Process does not semantically require
terminating an operating-system process that may host other Protos runtime
services or execution domains.

A later Process is new capacity, not a reincarnation of the previous
Process.

Failure of a remote Process or communication channel does not
automatically terminate a local Process.
## 37A. Process Failure Knowledge Boundary

**CLOSED**

Core v0.1 does not define or require an automatic distributed failure detector
for remote Processes.

A runtime may know that a Process has terminated when that fact is established
by direct lifecycle authority already inside the runtime's custody, such as the
runtime completing termination of a Process it hosts. That is lifecycle
knowledge, not failure detection by inference.

For a Process that is only remotely reachable, loss of communication is not
proof of Process termination. In particular, none of the following by itself
permits Core to conclude `TERMINATED`:

- heartbeat silence;
- connection closure or reset;
- routing failure;
- request or communication timeout;
- failure to reconnect;
- DNS, transport, or host reachability failure;
- observation that the containing Node is currently unreachable; or
- a local suspicion threshold derived from elapsed time or missed traffic.

Such evidence may justify `UNREACHABLE` or `UNKNOWN` according to the existing
distributed-state distinction, but it must not fabricate terminal lifecycle
knowledge.

Therefore Core v0.1 has no implementation-selectable timeout, heartbeat
interval, phi threshold, retry count, host-probe rule, or operating-system
heuristic whose expiry turns a remote Process into `TERMINATED`.

This rule composes with Actor lifecycle monitoring. Loss of a remote Process or
its transport does not resolve `ActorRef.termination()` for Actors hosted there
merely because communication was lost. Without independently authoritative
termination knowledge, those Actors remain non-`TERMINATED` from the observer's
point of view.

A future distributed-runtime facility may define Process failure detection,
leases, epochs, quorum-backed membership, fencing, explicit downing, or another
mechanism. Such a facility must distinguish suspicion/unreachability from any
decision that makes termination authoritative, and must specify the scope and
observable consequences of that decision.

Until such a facility is defined, the absence of a Core Process failure
detector is normative rather than an implementation gap. Implementations may
collect health telemetry internally, but they must not let implementation-
specific heuristics change Core lifecycle, ActorRef, monitoring, messaging, or
replacement behavior.
## 38. Node

**CLOSED --- REVISED**

A Node is a logical, ephemeral member of the Protos distributed runtime
capable of coordinating one or more Protos Processes.

Conceptually:

    Node
        |
        +-- Process
        +-- Process
        `-- Process

A Protos Node does not semantically correspond to a physical machine,
VM, container host, Kubernetes Node, or other infrastructure identity.

Multiple Protos Nodes may exist on one physical host, and infrastructure
may map Protos Nodes to physical resources in different ways.

Node is primarily a logical scope for:

-   Process coordination
-   Placement
-   Resources
-   Membership
-   Runtime health
-   Routing and transport coordination

Physical host topology and host-level failure are separate
topology/failure-domain information and must not be inferred from Node
identity.

Node identity does not survive loss, removal, or replacement.

If a Node disappears and later equivalent capacity joins, the new member
is another Node.

Infrastructure identity does not imply Protos Node identity.

If a Node becomes unreachable, remote entities become UNKNOWN or
UNREACHABLE before the runtime can legitimately conclude that they are
terminated.
## 38A. Node Failure Knowledge Boundary

**CLOSED**

Core v0.1 does not define or require an automatic distributed failure detector
that turns loss of contact with a remote Node into authoritative Node
termination.

A runtime may know that a Node incarnation has ended when that fact is
established by lifecycle authority already inside the runtime's custody. For
example, a runtime that is itself terminating a local Node incarnation may know
when that termination has completed. This is direct lifecycle knowledge, not
failure detection inferred from remote silence.

For a Node known only through distributed communication, the following evidence
is insufficient by itself to establish `TERMINATED`:

- missed heartbeats or probes;
- transport disconnect, reset, or refusal;
- routing failure;
- timeout;
- inability to reconnect;
- host, VM, container, pod, or infrastructure reachability failure;
- loss of every currently known Process connection hosted by that Node; or
- any implementation-selected elapsed-time, retry-count, or suspicion threshold.

Such evidence may establish or maintain `UNREACHABLE` or `UNKNOWN` under the
existing distributed-state distinction. It must not fabricate terminal
lifecycle knowledge.

Core therefore defines no implementation-selectable heartbeat interval, phi
threshold, retry count, grace period, lease duration, infrastructure probe, or
host-specific rule whose expiry changes a remote Node incarnation to
`TERMINATED`.

Node identity remains incarnation identity. If connectivity is later restored
to the same still-live Node incarnation, Core must not have killed that
incarnation merely because a detector timeout expired. If equivalent capacity
appears as a new Node incarnation, that new Node remains distinct under the
existing identity rule.

This boundary deliberately does not decide Cluster membership removal,
partition downing, quorum policy, fencing, split-brain resolution, or authority
for declaring a member permanently removed. Those are separate distributed
coordination mechanisms and remain governed by their own sections and open
design topics.

A future normative Cluster/distributed-runtime facility may define a failure
detector and a separate authoritative downing/removal decision. If it does, it
must specify at least:

- what evidence produces suspicion or `UNREACHABLE`;
- what authority may convert suspicion into an irreversible membership or
  lifecycle decision;
- the incarnation/epoch being decided;
- partition and stale-observer behavior;
- fencing or equivalent protection against two live sides both acting as the
  same authority; and
- consequences for hosted Processes, ActorRefs, monitoring, routing, and
  replacement.

Until such a facility exists, the absence of automatic Node failure detection in
Core is normative rather than an implementation gap. Internal health telemetry
may use arbitrary heuristics provided those heuristics do not alter Core-visible
Node, Process, ActorRef, monitoring, messaging, or replacement semantics.
## 38B. Network-Partition Knowledge and Reporting Boundary

**CLOSED**

Core v0.1 does not expose `NETWORK_PARTITION` as a distinct proven lifecycle or
reachability state.

From one distributed runtime domain, loss of communication with another domain
is not sufficient to distinguish a network partition from remote pause,
transport failure, routing failure, overload, local connectivity failure, or
remote failure. Core therefore reports only the knowledge it can justify:
`UNREACHABLE` or `UNKNOWN`, subject to the existing reachability rules.

An implementation may internally diagnose that a partition is likely, and
administrative telemetry may describe transport observations or suspicion.
Such diagnostics must not change Core-visible lifecycle, membership, Authority,
ActorRef, monitoring, messaging, or replacement semantics merely because the
implementation labels the condition a partition.

Core also defines no implementation-specific partition timeout after which
`UNREACHABLE` becomes a stronger state. Elapsed time does not turn inability to
communicate into proof of the cause of that inability.

This rule is symmetric with respect to partitions: two sides may simultaneously
observe the other as unreachable. Neither observation grants Authority over the
other side, proves that the other side terminated, or permits a conflicting
exclusive decision that the observer could not otherwise authorize.

If communication is restored without an authoritative membership/lifecycle
decision having ended the relevant incarnation, ordinary communication may
resume with that same still-live identity. Core does not create a new Node,
Process, or Actor incarnation merely because reachability was temporarily lost.

A future distributed coordination facility may expose richer partition
reporting or topology diagnostics. If it claims that a partition is
authoritatively identified rather than merely suspected, it must define the
evidence, observation scope, staleness rules, and consequences. Split-brain
resolution, membership removal, fencing, quorum decisions, and Authority
acquisition remain separate mechanisms and are not implied by partition
reporting.

Thus Core's portable observable report for ambiguous communication loss is
reachability knowledge, not guessed network topology.
## 38C. Core Split-Brain Safety Boundary

**CLOSED**

Core v0.1 does not define an automatic split-brain resolver, partition winner,
majority side, oldest side, static quorum, lease-majority strategy, or
implementation-selected downing policy.

When distributed communication is lost, Core safety follows the existing
Authority rule rather than inventing a universal partition policy:

```text
operation does not require Authority
    -> may continue if all of its other semantic prerequisites hold

operation requires Authority(scope)
    and currently valid Authority(scope) can be demonstrated
    -> may proceed

operation requires Authority(scope)
    and currently valid Authority(scope) cannot be demonstrated
    -> must not proceed
```

Consequently, a partitioned runtime domain does not become authoritative merely
because it is still executing, has more Nodes, has an older Node, retains a
particular infrastructure identity, or has waited for an implementation-defined
timeout.

Core also does not automatically terminate, down, fence, restart, replace, or
remove the opposite side of an ambiguous partition. Those are authoritative
control decisions when correctness depends on exclusivity and therefore require
a separately defined mechanism capable of demonstrating the required Authority.

This deliberately favors portable safety over hidden availability policy. A
partition may make some authoritative operations unavailable while unrelated
local computation continues under the existing rule. Core provides no guarantee
that every partition can continue making progress for operations whose
correctness requires distributed exclusivity.

When connectivity heals, the same still-live incarnations may resume ordinary
communication if no independent authoritative membership or lifecycle decision
has removed or terminated them. Core itself performs no partition-healing
reconciliation that rewrites identity, replays uncertain messages, or merges
divergent mutable Actor state.

This rule does not claim that arbitrary application effects on two isolated
sides can be automatically reconciled. Applications and future distributed
facilities that require exclusive external side effects, singleton writers,
durable replicated state, or other non-convergent invariants must use an
Authority/fencing mechanism whose semantics cover those effects.

A future Cluster facility may standardize a split-brain resolver or one or more
explicit policies. Such a facility must define its decision scope, evidence,
Authority acquisition, fencing, stale-holder behavior, membership consequences,
recovery after healing, and interaction with Processes, Actors, Groups,
messages, and external effects. Until then, implementations must not silently
choose a split-brain/downing strategy as Core semantics.
## 39A. Core Cluster Membership Protocol Boundary

**CLOSED**

Core v0.1 defines the semantic meaning and consequences of Cluster membership,
but it does not standardize a distributed membership protocol, wire format,
gossip algorithm, consensus algorithm, discovery transport, join handshake, or
failure-detector algorithm.

A Node is a member of a Cluster only when the active runtime has established
that membership through a mechanism provided by the runtime configuration or a
future/extended distributed facility. Merely discovering an address, opening a
transport connection, sharing infrastructure, or observing another Node does
not itself create Cluster membership.

Membership is distinct from Node identity, reachability, physical existence,
and Authority. In particular:

```text
member && unreachable
    -> still the same Node incarnation unless an authoritative membership
       or lifecycle decision says otherwise

not-member
    -> not usable as capacity in that Cluster view
    -> does not by itself prove that the Node or its Processes terminated

reachable
    -> does not by itself imply membership

member
    -> does not by itself grant Authority for an exclusive decision
```

Core does not define a portable application-level operation such as
`cluster.join()`, `cluster.leave()`, `down(node)`, or an automatic bootstrap
rule. Core also defines no portable ordering, latency, convergence, or
simultaneous-view guarantee for implementation-specific membership dissemination.

Consequently, two independent Core implementations are not required to
interoperate at the Cluster-membership wire-protocol level. A runtime that
provides distributed Cluster membership in v0.1 does so as an implementation or
extension facility, but that facility must not alter any already-closed Core
semantic rule for Actor/Process/Node identity, reachability, message uncertainty,
monitoring, Authority, or split-brain safety.

Portable Core behavior must therefore not depend on which unspecified
membership algorithm a runtime happens to use. Where a Core semantic operation
depends on Cluster membership, it operates against the membership knowledge that
the active runtime has validly established; it must not infer stronger facts
than that knowledge supports.

Voluntary membership removal, administrative removal, failure-driven removal,
and replacement capacity are also not conflated:

- voluntary or administrative removal changes Cluster membership;
- removal does not resurrect, retarget, or reuse Node identity;
- removal alone does not prove physical Node/Process death;
- equivalent capacity joining later is a new Node incarnation; and
- any removal decision whose correctness requires exclusivity must satisfy the
  scoped Authority rules before it is treated as authoritative.

A future standardized Cluster facility may define interoperable membership,
join/leave APIs, dissemination, epochs, quorum/consensus, convergence, and
failure-driven removal. Such a facility must make those properties normative
and must compose with the already-closed failure-knowledge, partition,
split-brain, and Authority boundaries.

The `Cluster membership protocol` topic is therefore outside Core v0.1 rather
than an implementation-selectable hole in Core semantics.
## 40. ActorRef

**CLOSED --- REVISED**

ActorRef is an opaque communication capability identifying exactly one
concrete Actor incarnation.

ActorRef is not a pointer, host:port, URL, Process address, Node address,
or stable service name.

Its semantics are independent of physical placement or transport while
the same live Actor incarnation continues to exist.

If the Actor dies, the ActorRef remains associated with that terminated
Actor and is never rebound or retargeted to a replacement.

Node membership loss, routing failure, or temporary unreachability does
not by itself terminate the ActorRef because those observations do not
prove Actor termination.

Live migration is optional runtime optimization rather than a semantic
requirement. It may preserve an ActorRef only when the same live Actor
incarnation and observable execution state are preserved.

ActorRef is a communication capability. Possessing an ActorRef does not
by itself demonstrate Authority for a Group, Cluster, exclusive role, or
other authoritative scope.
## 43. Actor Creation Admission and Backpressure

**CLOSED --- REVISED**

The public Actor-creation contract is owned by `ACTORS.md` §8:

```text
Actor.spawn(...) -> ActorRef
```

After that section's synchronous validation, module-identity resolution, and
initialization-value transfer have succeeded, the creation cutover has already
established exactly one Actor incarnation, its identity, and its `ActorRef`.
Placement and admission must not replace that result with a `SpawnOperation`,
Future, wait handle, or other public coordination object, and
`Actor.spawn(...)` does not remain pending merely because execution capacity is
not currently available.

Admission is instead a prerequisite for progress of the already-created
incarnation through `INITIALIZING`. If no currently usable placement satisfies
the active hard constraints and admission policy, that same incarnation remains
`INITIALIZING` while the runtime waits for or re-evaluates schedulable capacity.
It is not `READY`, and `ACTORS.md` §9 prohibits dispatch of external application
messages before `READY`.

Before concrete Process hosting has been established, the runtime may reconsider
candidate Processes or Nodes as feasibility, reachability, membership, or
capacity changes. Such candidate changes do not create another Actor, change the
`ActorRef`, or constitute Actor migration. Once the incarnation has been admitted
to and is hosted by a Process, the ordinary Process/Actor lifecycle rules apply;
in particular, known termination of that hosting Process terminates the Actor.

Capacity shortage therefore applies backpressure to Actor initialization
progress. It does not, by itself:

- fail `Actor.spawn(...)`;
- authorize unlimited oversubscription;
- create another Actor incarnation when capacity later appears;
- impose an implementation-selected spawn timeout or deadline; or
- cancel or terminate the incarnation because admission has taken too long.

Core v0.1 defines no creation-specific wait, cancellation, timeout, or deadline
surface. `ActorRef.stop()` remains the ordinary public way to request termination
of the returned incarnation, including while it is `INITIALIZING`. If compatible
capacity never appears and no independent lifecycle/failure event terminates the
Actor, the incarnation may remain `INITIALIZING` indefinitely.

A live incarnation waiting for Actor admission is **continuously
admission-eligible** when, from some point onward, every semantic prerequisite
other than the occurrence of a compatible admission opportunity remains
satisfied, the incarnation remains live, and compatible admission opportunities
in its applicable scheduling scope continue to occur. Such an incarnation must
eventually either advance through admission or become terminal for an
independently defined semantic reason. Later Actor-creation demand must not
consume recurring compatible opportunities so as to postpone that continuously
eligible incarnation forever.

This is weak admission fairness, not a latency, ordering, reservation, or
throughput guarantee. It provides no progress guarantee while no compatible
admission opportunity occurs and does not require a particular queue, scheduler,
placement algorithm, or resource-accounting representation.

Actor capacity does not imply one CPU or other fixed resource reservation per
Actor. Many Actors may share the same Process. The runtime uses capacity that has
become available through the normal Process/Node/runtime lifecycle.

Live `INITIALIZING` incarnations waiting for placement/admission may contribute
to semantic capacity-demand signals. Implementations may represent that demand
internally with queues, tickets, counters, scheduler records, or other machinery,
but no such representation is a Protos-visible creation-operation identity.

An external or explicitly integrated Infrastructure Controller may react to
capacity demand by provisioning additional raw capacity. `Actor.spawn(...)`
itself does not create Processes, Nodes, Clusters, Pods, VMs, or machines.
New capacity becomes usable only through the normal bootstrap, discovery, and
membership rules of this document.

## 44. Hierarchical Runtime Domains

**CLOSED**

The Protos execution hierarchy is intrinsic to the runtime:

    Actor
        -> Process
        -> Node
        -> Cluster

Each level coordinates multiple units of the level below when such
coordination is required.

A Process is a concrete Protos runtime execution, isolation, and failure domain. It is not required to map one-to-one to an operating-system process or address space.

A Node coordinates one or more Protos Processes.

A Cluster coordinates multiple Protos Nodes.

External infrastructure managers may provision, place, restart, or scale
physical workloads, but they do not replace Protos runtime membership,
ActorRef routing, health, or logical coordination.

Physical co-location must never be inferred solely from Protos logical
topology.

Optimizations depending on physical locality must only be used when the
runtime can establish that the required locality or capability actually
exists.

The hierarchy is pay-for-what-you-use: levels unnecessary for the active
execution domain need not incur distributed-runtime machinery.
## 45. Dynamic Actor Capacity and Placement

**CLOSED**

Actor capacity is primarily dynamic rather than based on fixed
declarative resource reservations.

Normal Actors are not expected to declare Kubernetes-like CPU or memory
requests.

Placement occurs conceptually in two stages:

1.  Hard feasibility filtering.
2.  Scoring of feasible destinations using dynamic runtime information.

Hard constraints may include unavailable/draining Process, hard
memory/resource limit, incompatible runtime/code, required special
resource, or isolation/placement constraint.

Scoring may consider CPU/runnable pressure, memory pressure, scheduler
latency, mailbox pressure, communication locality, affinity, resource
locality, and failure domains.

Manual resource requirements and placement constraints may exist when
genuinely required but are exceptional.

The runtime may learn from observed Actor behaviour instead of requiring
the programmer to predict resource consumption in advance.
## 46. Adaptive Admission Control

**CLOSED --- REVISED**

The ability to technically fit another Actor and the desirability of
admitting more work are distinct concepts.

Soft resource pressure normally affects placement scoring and
contributes to proactive capacity-demand signals.

When pressure becomes sufficiently severe, the runtime may temporarily
stop advancing newly created Actor incarnations through admission. Those
incarnations remain `INITIALIZING` under §43, so the backpressure delays their
initialization progress rather than changing the public result or synchronously
failing `Actor.spawn(...)`.

Admission decisions are adaptive and multidimensional.

No fixed CPU, memory, or utilization threshold is part of the language
semantics.

The runtime separates conceptually:

    placement pressure
    capacity-demand pressure
    admission pressure

while allowing all three to use common runtime observations.

Capacity demand should be observable before admission is necessarily
refused, allowing infrastructure mechanisms to react proactively.
## 48. Failure Domains

**CLOSED**

Protos models failure domains explicitly but generically.

A failure domain represents a known shared-fate relationship: failure of
the domain may simultaneously affect all runtime entities placed within
it.

Failure domains are not required to form a strict hierarchy and may
overlap.

Process is intrinsically a failure domain because failure of a Process
affects the Actors hosted by that Process.

Additional physical or infrastructure failure domains may include host,
rack, power domain, availability zone, site, or infrastructure-specific
domains.

General topology information and failure-domain information are related
but distinct concepts.

Logical Protos topology must not imply physical failure independence.

Unknown topology means unknown failure independence, not independent
failure.

Protos only claims availability guarantees that can be demonstrated from
failure-domain information currently known to the runtime.

The exact mechanisms for failure-domain discovery, configuration, and
topology providers remain open.
## 49. High-Availability Placement Requirements

**CLOSED --- REVISED**

High-availability placement requirements belong primarily to logical
groups of Actors representing a common service or responsibility rather
than to individual Actors.

A Group expresses availability intent declaratively, for example:

    desired members = 3
    tolerate one host failure
    tolerate one availability-zone failure

Application code normally expresses required redundancy or failure
tolerance rather than selecting concrete Processes, Nodes, hosts, zones,
or physical locations.

The scheduler uses known topology/failure-domain information for
placement.

The Protos capacity-demand model may express additional independent
capacity required to satisfy availability objectives.

Availability objectives and mandatory availability requirements are
distinct.

Availability status is conceptually:

    SATISFIED
    UNSATISFIED
    UNKNOWN

The runtime must never report an availability guarantee as SATISFIED
unless it can demonstrate that guarantee from available information.

High-availability placement does not by itself provide replicated Actor
state, persistence, consensus, transactional replication, failover of
mutable state, or exactly-once processing.

Actor liveness is ephemeral by default; durability is explicit.
## 50. Runtime Groups

**CLOSED --- REVISED**

Groups are stable logical policy and management units over homogeneous
runtime entities.

A Group is not an additional level in the intrinsic runtime hierarchy:

    Actor -> Process -> Node -> Cluster

Conceptually, groups may exist for entities at different levels:

    Group<Actor>
    Group<Process>
    Group<Node>

This notation is conceptual and does not imply generic type syntax.

Each group-managed entity belongs to one management Group at its
corresponding level. Each grouping level may provide an implicit default
Group.

Specialized Groups are introduced only when a set of entities requires
policy different from the default, such as availability, affinity,
placement, scaling/capacity policy, routing, or resource policy.

### Group Identity and Lifecycle

Group identity is independent of its current membership and of the
controller instance currently managing it.

A Group may remain valid with zero current members, and complete
membership replacement does not create a new Group.

Group lifetime is determined by an owning runtime/control scope, not by
global reachability of GroupRef values. Remote GroupRefs do not keep a
Group alive by themselves.

A Group may outlive the runtime scope that originally created it only
when durable or external ownership is explicitly configured. Such
continuity requires preservation or reconstruction of the defining Group
identity, policy, and control state.

Stable does not mean durable. If the state defining a non-durable Group
is lost, that Group identity ends. A later Group created under the same
discovery name is a new Group.

### Group and Group Controller

Group and Group Controller are distinct concepts.

The Group identifies the logical managed set, policy, and stable Group
identity.

A Group Controller is an ephemeral control-plane executor that may
reconcile Group policy and membership. Controller replacement or
Authority transfer does not create a new Group.

The Group Controller is not a mandatory data path for every message.
Runtime routing may use cached or locally available routing views while
controllers reconcile policy and membership separately.

Not every Group requires active membership management or distributed
Authority.

Groups do not intrinsically require Authority. Authority is introduced
only where conflicting decisions could violate an explicit authoritative
invariant.

### Desired Cardinality

A Group may define desired cardinality.

Desired cardinality is a convergent control objective, not a strict
instantaneous global invariant.

It does not imply identical state, state replication, shared history,
transparent failover, consensus, persistent service state, or an exact
physical Actor count at every instant.

During partitions, delayed observations, concurrent reconciliation, or
membership uncertainty, physical membership may temporarily be below or
above the desired cardinality. Normal reconciliation converges toward the
desired state when sufficient coordination becomes available.

A strict singleton or maximum-authoritative-role invariant applies to
valid Authority, not necessarily to the number of physically existing
Actors, and requires the corresponding Authority/fencing mechanism.

Stopping one concrete Group member terminates that Actor incarnation. It
does not implicitly reduce Group desired cardinality. If desired state
still requires capacity, the Group Controller may create a replacement
Actor.

A live Actor incarnation already created by Group reconciliation to satisfy a
known membership deficit remains an in-flight candidate while it awaits
admission or initialization. It is not an eligible routing member merely because
it exists: normal readiness and Group-membership eligibility still apply.
However, reconciliation must not treat that known in-flight candidate as
nonexistent solely because it has not reached `READY`, then repeatedly create
additional incarnations for the same observed deficit. If that candidate
terminates, fails initialization, is deliberately withdrawn, or otherwise ceases
to be a viable candidate, the unsatisfied desired state remains and ordinary
reconciliation may create a fresh incarnation.

This accounting rule does not turn desired cardinality into an instantaneous
exact-count invariant. Concurrent controllers, delayed observations, partitions,
or membership uncertainty may still produce the temporary under/over-cardinality
already permitted above. An implementation may represent in-flight reconciliation
intent however it chooses; Core exposes no `SpawnOperation` for that purpose.

Failure policy and Group desired-state reconciliation are independent. A
failure policy such as Stop determines what happens to the failed Actor
incarnation; it does not suppress Group reconciliation. Where multiple
policies request equivalent replacement capacity, the runtime may
reconcile those intents rather than creating redundant replacements.

### ActorGroup Communication

Only an Actor Group is an application communication target by default.
Process and Node Groups are management, placement, capacity,
availability, monitoring, or administrative constructs unless future
explicit APIs define otherwise.

An ActorGroup may optionally act as a location-transparent communication
destination. This capability is lazy and follows the Protos
pay-as-you-grow principle.

Conceptually:

    ActorRef -> one concrete Actor
    GroupRef -> one concrete ActorGroup identity; routing selects a member

ActorRef and GroupRef are both communication targets and expose the same
fundamental send/request communication model while retaining different
destination semantics.

Multiple GroupRefs may refer to the same Group. Group identity therefore does
not determine GroupRef identity. Two independently acquired GroupRefs may denote
the same Group, and may even carry equivalent effective communication
permissions, while remaining distinct identity-bearing objects for `===`. They
are not collapsed merely because their target Group is the same.

A GroupRef may carry a restricted communication capability for its Group.
Group identity is independent of the permissions carried by a particular
GroupRef. Authority remains a separate capability. Distinct effective capability
or restriction state necessarily denotes distinct GroupRef semantic identity;
Core does not infer capability sameness from target Group identity alone.

### GroupRef semantic identity and transfer

A particular GroupRef is an identity-bearing communication-capability object.
Its **semantic GroupRef identity** is distinct both from the identity of its
target Group and from any physical wrapper, proxy, handle, address, routing
entry, cache entry, or serialized representation used to realize it.

GroupRef is transferable through ordinary Actor/Process message value transfer.
Unlike an ordinary mutable-object logical copy, transferring a GroupRef preserves
the same semantic GroupRef identity. The transferred value therefore denotes the
same concrete Group identity, carries the same effective communication
capability and restrictions, and compares `===` with any other materialization
of that same transferred semantic GroupRef when the values are observed in one
execution context. Transfer cannot grant broader communication permission and
cannot implicitly grant Group, Cluster, role, fencing, or other Authority.

Consequently, if Actor A transfers GroupRef `g` to B and a value descended only
through transfers of that same capability later returns to A as `k`, `g === k`
is `true`. If A transfers the same `g` to B more than once, the resulting values
`h1` and `h2` represent the same semantic GroupRef and `h1 === h2` is `true`.
These results do not depend on proxy caching, transport path, or wrapper reuse.
By contrast, two independently acquired GroupRefs `g1` and `g2` to the same
Group remain distinct when their acquisition did not itself specify preservation
of one existing GroupRef identity; in that case `g1 === g2` is `false`.

The general identity-hash contract follows automatically. Every materialization
of the same semantic GroupRef identity must produce the same
`identityHashOf(...)` result for the applicable Protos execution, so an
`IdentityMap` lookup made with a transferred, returned, or rematerialized form of
the same GroupRef finds the same identity key. Distinct GroupRef identities may
collide numerically and remain distinct because identity is never defined by hash
comparison. An ordinary overridable `identityHash()` member, if observed, does
not replace the primitive `identityHashOf`/`===` contract.

Transfer of a GroupRef does not copy or expose mutable Group membership,
controller, routing-cache, policy, or control-plane state. A receiving runtime
may materialize a distinct local GroupRef physical representation, discard it,
and later rematerialize another one. Such representation changes do not create a
new semantic GroupRef identity. No global interning, globally unique live
wrapper, stable address, permanent proxy cache, Group-to-GroupRef registry, or
wrapper lifetime tied to Group lifetime is required. An implementation need only
preserve the semantic identity observations required above, using any
unobservable token or mechanism it chooses.

Receiving or retaining a transferred GroupRef does not extend the Group's
lifetime. The existing rule that remote GroupRefs do not keep a Group alive
continues to apply. If that Group later terminates, every transferred GroupRef
remains bound to that terminated Group identity and never retargets to a Group
later created or discovered under the same name.

A GroupRef is location-transparent and remains associated with the same
Group across member creation, removal, replacement, temporary empty
membership, controller replacement, and routing-path changes.

Discovery names may rebind; a concrete GroupRef never rebinds to another
Group identity.

Normal `send` or `request` directed to an ActorGroup selects exactly one
eligible concrete Actor by default. Broadcast/multicast is a distinct
explicit operation.

There is no Group-wide FIFO guarantee. If multiple operations happen to
route to the same concrete Actor, that Actor's normal ordering guarantees
apply.

### Routing, Membership, and Authority

Group routing may use locally available, cached, or eventually consistent
membership information when stronger consistency is not required for
correctness.

Routing eligibility does not imply Authority. Receiving a Group-routed
message does not grant, renew, or demonstrate Authority for an exclusive
role or decision scope.

Operations whose correctness requires Authority must independently
demonstrate valid Authority for the relevant scope.

Liveness, Group membership, routing eligibility, and Authority are
separate concepts. An Actor may remain alive and useful for
non-authoritative work even when it is no longer part of the current
authoritative membership or role view.

### Routing and Acceptance

While the Group still owns routing responsibility and no concrete Actor
has accepted an operation, it may wait for capacity, select another
eligible member, or re-route according to its routing policy.

Acceptance occurs when a concrete Actor takes ownership of the message
for processing. Acceptance does not imply handler start or completion.

Once accepted, normal concrete-Actor delivery and failure semantics
apply. Group communication does not imply transparent retry, replay, or
exactly-once delivery after acceptance.

If the runtime cannot determine whether acceptance occurred, the
operation becomes uncertain. Uncertainty does not authorize transparent
replay.

The logical message snapshot remains the snapshot taken at the original
`send()` or `request()` invocation across all pre-acceptance waiting and
rerouting.

### Empty, Unreachable, and Terminated Groups

Group lifetime, member availability, routing reachability, controller
reachability, and Authority availability are distinct.

If a live ActorGroup currently has no eligible member, communication
applies bounded backpressure and may remain pending according to normal
timeout, deadline, cancellation, and capacity-demand semantics.

Temporary inability to reach Group routing/control information does not
by itself prove that the Group has terminated.

If the Group is known to have terminated before a pending operation has
been accepted by a concrete Actor, that operation fails.

If a concrete Actor already accepted the operation, subsequent Group
termination does not revoke ownership of that accepted operation; normal
Actor semantics apply.

Demand for a live Group with insufficient eligible membership may
contribute to capacity-demand signals.

Sending to a Group does not itself provision infrastructure. Group
reconciliation may create Actors when policy requires them, and external
Infrastructure Controllers may separately provision Process/Node
capacity in response to semantic capacity demand.
## 51. Capacity Demand and Infrastructure Integration

**CLOSED --- REVISED**

The core Protos runtime does not provision infrastructure capacity by
default.

Protos observes runtime state, performs semantic placement/admission and
Group-control decisions, and exposes semantic capacity demand.

Capacity demand may reflect conditions such as:

-   Live Actor incarnations waiting for placement/admission during `INITIALIZING`
-   Group demand with insufficient eligible members
-   Resource pressure
-   Hard placement/resource constraints
-   Unsatisfied availability objectives
-   Missing independent failure-domain capacity

Capacity demand is information, not an imperative provisioning order. It has no
required one-object-per-demand representation and does not imply a public
creation-operation handle. A runtime may aggregate, coalesce, split, or otherwise
represent demand internally provided that doing so does not change Actor
identity, lifecycle, admission fairness, Group reconciliation, or any other
portable observable behavior.

An external or explicitly integrated Infrastructure Controller decides
whether and how to satisfy that demand according to the capabilities,
policy, cost, timing, and topology of its environment.

Conceptually:

    application/runtime intent
            |
            v
    Protos semantic control
            |
            v
    capacity demand
            |
            v
    external infrastructure
            |
            v
    new raw capacity
            |
            v
    Protos bootstrap/discovery/membership
            |
            v
    available scheduling capacity
            |
            v
    Actors

Metrics are one possible representation of Protos demand. They are not
the semantic model itself.

Newly provisioned capacity is incorporated into Protos only through the
runtime's normal bootstrap, discovery, and membership mechanisms.

The Infrastructure Controller does not directly mutate Protos logical
topology or define Actor, Group, Process, Node, Cluster, routing,
membership, failure, or Authority semantics.

A Kubernetes Deployment replica is not a Protos ActorGroup member merely
because the workload may host Protos runtime capacity. One Process may
host many Actors, so infrastructure replica cardinality is distinct from
ActorGroup desired cardinality.

Processes and Nodes may disappear at any time.

Correctness must not depend on graceful removal.

Draining is advisory and opportunistic: it may reduce disruption during
planned removal, but the runtime must remain correct if a Process or Node
disappears immediately without draining.

Exact Capacity Demand APIs, infrastructure adapters, scale-up/down policy,
and draining mechanics remain open.
## 52. Ephemeral Actor Liveness and Explicit Durability

**CLOSED --- REVISED**

Actor liveness is ephemeral by default; durability is explicit.

An Actor may die at any time.

The programming model must not assume that an Actor incarnation, its
hosting Process, or its hosting Node will live indefinitely.

If an Actor dies, its private mutable state dies with it unless that
state was explicitly externalized through a durability mechanism.

Possible future durability mechanisms may include:

-   Persistent state
-   Checkpointing
-   Event logs
-   Replication
-   External databases
-   Other recoverable storage

These are not implicit properties of an ordinary Actor.

Protos does not attempt to make every Actor fault tolerant by default.

Higher-level runtime mechanisms may restore required service capacity by
creating replacement Actors, but that does not constitute transparent
continuation of the failed Actor.

If durable state is later used for recovery, recovery initializes a new
Actor incarnation from that durable state. It does not resurrect the dead
Actor identity or ActorRef.

Principle:

> Actor incarnations are disposable. Durability, when required, must live
> outside the ephemeral incarnation.
## 53. Direct ActorRef Versus Stable Group Identity

**CLOSED --- REVISED**

An ActorRef addresses one concrete Actor incarnation.

If that Actor dies, the ActorRef remains associated with that terminated
Actor and is not rebound to a replacement.

If application code keeps a direct ActorRef, it has explicitly chosen to
communicate with that individual Actor and therefore observes that
Actor's mortality.

Stable service continuity belongs to a higher-level abstraction such as
an ActorGroup.

Conceptually:

    ActorRef
        -> individual, ephemeral destination

    GroupRef
        -> one stable Group identity
        -> changing membership

    discovery name
        -> may later resolve to another identity

Communication directed to a live Group may continue across member
replacement according to Group routing rules. Communication directed to
an individual Actor does not.

Group identity is stable across membership/controller changes but is not
intrinsically durable across loss of the state that defines the Group.
## 54. Actors Are Replaced, Never Restarted

**CLOSED**

`restart` is not a distinct semantic continuation of an Actor.

When policy requires continued service after an Actor dies, the runtime
creates another Actor.

Conceptually:

    Actor A
        X

    replacement policy
        |
        v

    Actor B

Actor B may use the same behavior, bootstrap code, configuration, or
role, but it is another Actor.

It has:

-   A new Actor identity
-   A new ActorRef
-   Fresh private state
-   Fresh initialization
-   No inherited mailbox
-   No inherited pending interactions

Administrative tooling may use the word "restart" informally, but
semantically the operation is termination followed by creation of
another Actor.

Principle:

> Actors are replaced, never restarted.
## 55. Supervision Versus Group Controllers

**CLOSED --- REVISED**

Supervision/failure authority reacts to the failure of one Actor
incarnation.

Group Controllers maintain desired Group state.

These responsibilities are distinct.

A failure authority may observe an Actor failure and apply failure
policy such as Replace, Stop, Escalate, or Ignore.

A Group Controller independently observes Group policy and current
membership and reconciles when desired state is not satisfied.

For example:

    desired members = 4
    actual members = 3
        -> Group Controller may create one new Actor

A failure policy such as Stop terminates or leaves terminated the failed
Actor incarnation. It does not mean `desired cardinality -= 1` and does
not suppress independent Group reconciliation.

Conversely, a Group membership deficit does not alter the failure policy
of the Actor that terminated.

If both failure policy and Group policy request equivalent replacement
capacity, the runtime may reconcile those intents rather than creating
redundant replacements.

The replacement is not a continuation of the failed member.

The runtime may optimize supervision and Group control together
internally, but the semantic responsibilities remain distinct.
## 56. Failure Authority Is Pay-As-You-Grow

**CLOSED**

Every Actor has a failure authority, but this does not imply a dedicated
Supervisor Actor or separate runtime machinery.

The failure authority may be provided by the nearest
runtime/root/controller relationship already required by the active
execution domain.

For the minimal program:

    Process
        |
        `-- RootActor

the default fatal-failure behavior is:

    RootActor fatal failure
        -> Process terminates

No additional Actor, mailbox, controller, or distributed service is
required merely to supervise the RootActor.

As applications introduce more Actors, Groups, Nodes, or Cluster
coordination, richer failure-policy machinery may become active only
where required.

Supervisor is therefore a semantic role, not necessarily an Actor.

This follows the pay-as-you-grow principle.
## 57. Outstanding Direct Interactions Die With the Actor

**CLOSED**

Actor death terminates direct outstanding interactions with that Actor.

Replacement does not inherit them.

For a direct `request`, if Protos can determine that the destination Actor
terminated before accepting the request, the returned Future fails according to
the ordinary communication failure semantics.

If acceptance occurred, or may have occurred and Protos cannot prove otherwise,
termination before a normal reply makes the returned Future fail with
`RequestOutcomeUncertain`. This includes an Actor turn that ends in an unhandled fatal
failure after accepting the request: the caller is not automatically given the
destination's internal failure object as though it were a normal reply, and the
runtime does not pretend that the accepted request had no effects.

The runtime does not transparently find a replacement and replay the
request.

For Group-addressed communication, routing may choose another member
only while the Group still owns routing responsibility and no concrete
Actor has accepted the operation.

Once a concrete Actor has accepted an operation, its subsequent death
does not cause transparent replay against another Actor.
## 58. Rerouting Belongs to Group Addressing

**CLOSED**

Messages addressed directly to a terminated Actor are never retargeted
to another Actor.

This remains true even if the runtime can prove that a particular
message had not yet been accepted by the Actor before termination.

The destination of that operation was the concrete ActorRef, not
"whoever replaces it."

The SendOperation may distinguish states such as
definitely-not-delivered, delivery-unknown, accepted, failed, or other
future statuses, but none of those states authorize implicit destination
substitution.

For Actor Group communication, the Group may choose or re-choose an
eligible member while routing responsibility still belongs to the Group
and no concrete Actor has accepted the operation.

Principle:

> Rerouting is a property of Group addressing, never of ActorRef failure
> recovery.
## 59. Termination, Unreachability, and Unknown State

**CLOSED**

Actor termination is a known fact.

Unreachability is an observation.

Unknown means that the runtime lacks sufficient information to determine
the Actor's current state.

Conceptually:

    TERMINATED
        The runtime has sufficient evidence that this Actor incarnation no longer exists.

    UNREACHABLE
        The runtime currently cannot communicate with the Actor.

    UNKNOWN
        The runtime lacks sufficient information to determine its state.

Therefore:

    UNREACHABLE != TERMINATED

and:

    UNKNOWN != TERMINATED

A timeout, network partition, routing failure, or missing heartbeat does
not by itself prove Actor termination.

Protos never equates unreachability with termination without sufficient
evidence.

The exact failure-detection and membership mechanisms used to establish
such evidence remain open.
## 60. Unreachability Applies Backpressure

**CLOSED**

Temporary unreachability applies backpressure; it does not by itself
terminate or retarget an operation.

For a direct send to an Actor that is currently unreachable:

    send
        -> PENDING / backpressured
        -> target reachable again: delivery may continue
        -> target known TERMINATED: operation fails
        -> deadline expires: deadline/failure semantics apply
        -> caller cancels: cancellation semantics apply

This does not imply unbounded buffering.

End-to-end bounded queues and backpressure remain in force.

Unreachability does not authorize retry against a replacement or
redirection to another Actor.

For Group communication, another member may be selected only while the
Group still owns routing responsibility and no concrete Actor has
accepted the operation.
## 61. Processes Are Ephemeral Capacity

**CLOSED --- REVISED**

Processes are ephemeral execution capacity, not durable application
identities.

If a Process is known to have terminated, every Actor still hosted by
that Process terminates with it.

This inference requires actual Process termination evidence. Node
unreachability, loss of Cluster membership, routing failure, or partition
does not by itself prove Process death.

If live Actor migration had already completed before Process death, the
migrated Actor is no longer hosted by the failed Process and therefore is
not terminated by that Process failure.

Higher-level mechanisms react independently:

-   Group Controllers may restore desired membership
-   Failure authorities may apply policy
-   Capacity-demand signals may increase
-   External Infrastructure Controllers may provision additional capacity

A newly created Process is new capacity.

It is not semantically the reincarnation of the Process that disappeared,
even if external infrastructure considers it a replacement workload.

Protos does not require resurrection of a particular Process identity.
## 62. Nodes Are Ephemeral Runtime Members

**CLOSED**

Nodes are ephemeral runtime members.

Node identity does not survive loss, removal, or replacement.

Infrastructure identity does not imply Protos Node identity.

If Node N1 disappears and later equivalent capacity joins as N2, N2 is
another Protos Node.

A physical VM, host, container, Kubernetes resource, or other
infrastructure identity may be long-lived or pet-like without making the
corresponding Protos Node identity durable.

Before membership/failure detection has sufficient evidence of loss, an
unreachable Node remains distinct from a known removed/lost Node.

Principle:

> Node is cattle too.
## 63. Cluster Identity Is Independent of Node Identity

**CLOSED --- REVISED**

Cluster identity is a logical coordination-domain identity, independent
of the identity or current number of its Nodes.

Nodes may enter and leave while the Cluster remains the same logical
domain. A Cluster may conceptually have zero current Nodes.

No individual Node is required to be the durable identity anchor for the
Cluster.

Persistence of Cluster identity and authoritative/control state across
complete loss of runtime membership is explicit/configurable rather than
intrinsic.

If the defining state is durably preserved, later Nodes may participate
in the same logical Cluster. If all defining state is lost, a later
bootstrap creates a new Cluster identity rather than resurrecting the old
one.

The Cluster is therefore different from Actor, Process, and Node
identities: it represents the logical coordination field in which
ephemeral members participate.
## 64. Loss of Node Membership Does Not Prove Physical Process Death

**CLOSED --- REVISED**

Loss of Node membership removes the Processes hosted by that Node from
the usable capacity of the affected Cluster view.

It does not prove that those Processes have physically terminated.

A Node or its Processes may continue executing while isolated by a
network partition or membership disagreement.

Protos therefore keeps separate:

-   Membership
-   Reachability
-   Physical existence
-   Authority

A Cluster view must not treat Processes behind a lost/unreachable Node as
usable Cluster capacity unless the relevant membership, routing, and
Authority rules permit it.

Known Process death may establish termination of Actors still hosted by
that Process; Node membership loss alone may not.
## 65. Local Execution May Continue Without Higher-Scope Authority

**CLOSED --- REVISED**

Loss of Authority in one scope does not imply loss of unrelated local
execution.

A partitioned or isolated runtime domain may continue operations that do
not require the lost Authority, including purely local Actor execution,
local Futures, local I/O, and other valid local computation.

However, a runtime domain must not perform an operation whose correctness
requires Authority unless it can demonstrate the required currently valid
Authority for that operation's scope.

Conceptually:

    Authority(scope X) lost
        |
        +-- unrelated execution may continue
        +-- non-authoritative work may continue
        |
        `-- operations requiring Authority(scope X) are disabled

Protos does not choose either extreme:

-   A partition does not automatically kill all local computation.
-   Every partition does not automatically become independently
    authoritative.

Being alive or reachable does not imply being authoritative.
## 66. Authority

**CLOSED --- REVISED**

Authority is the exclusive capability required to make an authoritative
decision within a particular decision scope.

For one authoritative invariant and scope, conflicting participants must
not simultaneously possess valid Authority to make incompatible
exclusive decisions.

Authority is distinct from communication capability:

    ActorRef / GroupRef
        = communication capability

    Authority
        = exclusive capability for authoritative decisions

Possessing an ActorRef or GroupRef does not by itself demonstrate
Authority.

An operation requires Authority only when conflicting concurrent
decisions within the same scope could violate an authoritative invariant.

Membership observation, Group membership management, replacement,
placement, routing, and topology operations are not intrinsically
authoritative. Particular forms of those operations require Authority
only when their correctness depends on exclusivity or another explicit
authoritative invariant.

Examples that may require Authority depending on the policy include:

-   Granting an exclusive writer role
-   Enforcing a strict singleton or maximum-authoritative-role invariant
-   Committing a definitive membership decision when conflicting commits
    would be invalid
-   Transferring an exclusive lease or fencing token
-   Other decisions whose conflicting simultaneous validity would violate
    correctness

Observation and ordinary convergent reconciliation need not require
exclusive Authority.

If a runtime domain cannot demonstrate Authority for an operation that
requires it, it must refrain from performing that authoritative operation.
Failure to prove Authority does not automatically stop unrelated work.

Protos does not prescribe the mechanism used to establish Authority.
Possible mechanisms may include quorum, consensus, leader election,
leases, witnesses, external coordination services, or other mechanisms
providing the required exclusivity properties.
## 67. Authority Is Scoped

**CLOSED --- REVISED**

Authority is scoped to the smallest runtime/control domain in which
conflicting authoritative decisions are possible.

Authority is not necessarily Cluster-global, and using the same
abstraction in a Cluster does not force local uses to pay Cluster-level
coordination costs.

Conceptually:

    local Group entirely inside one Process
        -> Process-local Authority if exclusivity is required

    Group coordinated across Processes of one Node
        -> Node-scoped Authority if exclusivity is required

    Group coordinated across Nodes
        -> distributed/Cluster-scoped Authority if exclusivity is required

Different Authority scopes may exist independently for different
responsibilities.

Exclusivity is required within an Authority scope, not across unrelated
responsibilities.

The runtime may therefore distribute or shard control responsibilities
while preserving the rule that conflicting authorities cannot
simultaneously make the same exclusive decision.

Principle:

> Authority cost grows with the coordination scope actually required.
## 68. Controllers Are Ephemeral

**CLOSED --- REVISED**

Controllers are ephemeral control-plane executors.

Correctness must not depend on the lifetime of the particular controller
instance currently performing reconciliation or exercising Authority.

A Group Controller is not part of Group identity.

If a controller disappears, another eligible controller may continue
control operations, acquiring any required Authority according to the
rules of that Authority scope.

Control state needed for continued control must survive or be
reconstructible independently of the controller instance that previously
held it.

For example, Group desired cardinality, availability policy, Group
identity, or other control information that must survive controller
replacement must not exist solely in irreplaceable controller RAM.

Such control information is not intrinsically authoritative merely
because it must survive controller replacement.

The exact mechanism by which control state survives or is reconstructed
remains open.

Principle:

> The controller is cattle too.
## 69. Authority and Control State Are Mechanism-Independent

**CLOSED --- REVISED**

Protos specifies the consistency, exclusivity, and survivability
properties required by Authority and control state, not a mandatory
storage or consensus implementation.

Depending on the active runtime domain and invariant, the required
mechanism may range from ordinary local state to distributed
coordination.

Conceptually:

    single Process
        -> local state may be sufficient

    multiple Processes / one Node
        -> Node-level coordination may be sufficient

    distributed Cluster
        -> stronger coordination may be required

Possible implementations may include replicated consensus state, a
durable store, leases, an external authority service, or a Protos-native
mechanism.

No specific technology such as Raft, etcd, or another consensus/store
implementation is part of Protos semantics at this stage.

No distributed coordination mechanism is required merely because Groups
or multiple Actors exist. Distributed coordination is paid for only when
an active invariant or durability requirement actually needs it.

This preserves pay-as-you-grow.
## 70. External Authority and Infrastructure Mechanisms Do Not Define Protos Semantics

**CLOSED --- REVISED**

Protos may delegate mechanisms used to establish, demonstrate, or persist
Authority and control state to external infrastructure.

For example, an implementation may use:

-   Kubernetes Lease or API primitives
-   Nomad coordination facilities
-   Consul or another coordination service
-   A cloud coordination service
-   A Protos-native mechanism

However, external infrastructure does not define the meaning of Protos
Authority or logical topology.

Conceptually:

    external mechanism
        may establish evidence / persist state / provide raw capacity

    Protos semantics
        define Actor, Group, membership, routing, failure and Authority

An external orchestrator may help discover instances, establish leases,
persist control metadata, or provision capacity.

It does not thereby become the semantic definition of:

-   Actor
-   ActorRef
-   ActorGroup / GroupRef
-   Process
-   Protos Node
-   Protos Cluster
-   Group membership
-   Protos routing
-   Protos failure semantics
-   Protos Authority

For example, a Kubernetes Deployment is not semantically a Protos
ActorGroup merely because it may provision workloads that host Group
members.

External infrastructure disappearance is an observed capacity/failure
event from Protos' perspective, not a direct Protos logical mutation
performed by the orchestrator.

The same Protos program and runtime model should remain valid across
standalone execution, Kubernetes, Nomad, or future infrastructure
environments.

## 72A. No Core Application or Service Identity

**CLOSED**

Core v0.1 defines no intrinsic application identity, service identity,
`ApplicationRoot`, deployment identity, or mandatory execution-hierarchy level
above Process/RootActor, Node, Cluster, Group, and the other already-defined
runtime identities.

This is a deliberate semantic boundary rather than a missing hidden runtime
object.

An implementation, deployment system, orchestrator, observability platform, or
administrative tool may associate Protos runtime entities with an external
application/service identifier for purposes such as deployment, configuration,
observability, metrics, ownership, tenancy, rollout grouping, or operational
inventory.

Such an identifier is administrative metadata unless a future normative facility
explicitly promotes it into the Protos semantic universe.

In Core v0.1, administrative application/service identity therefore must not:

- become an implicit Actor sender identity;
- create a mailbox, mutable object graph, execution domain, or lifecycle domain;
- become a parent of Process RootActors;
- alter ActorRef or GroupRef identity;
- imply shared mutable state between Processes or Actors;
- change same-sender FIFO, routing, placement, failure, supervision, or
  cancellation semantics;
- grant Process, Node, Cluster, resource, or capability authority merely by
  association;
- keep Actors, Processes, Groups, Nodes, or Clusters alive;
- cause two otherwise distinct runtime identities to compare as the same Protos
  identity;
- impose distributed-runtime startup or coordination cost on programs that do
  not use a separately defined facility.

The same external application/service label may be associated with multiple
Processes, Nodes, Clusters, Groups, or deployments without creating a new Core
continuity guarantee among them. Conversely, one Protos Process or Cluster may be
classified differently by external tooling over time without changing its Core
identity.

Distributed service continuity remains expressed through the already-defined
semantic mechanisms such as Group identity, discovery, Cluster/runtime control
state, and explicit durable state. An administrative label is not a substitute
for those mechanisms.

A future standard facility may introduce a first-class application/service
identity only if a workload demonstrates a semantic boundary that cannot be
expressed cleanly by the existing universe. Such a facility would need to define
identity, lifetime, authority, topology relationships, transferability,
persistence, failure behavior, and observable operations explicitly.

This closes the former open ledger item `Optional administrative
application/service identity for deployment, configuration, observability, or
ownership` for Core v0.1. External administrative metadata remains permitted but
non-semantic.

## 72B. Service Discovery Implementation Is Not Core Semantics

**CLOSED**

Core v0.1 defines the observable meaning of discovery identities and rebinding
where those semantics are already specified, but it does not standardize one
service-discovery implementation.

A conforming runtime or distributed facility may implement discovery through
mechanisms such as:

- in-process or per-Process registries;
- Cluster control state;
- external registries or naming services;
- DNS-like systems;
- orchestrator/service-platform APIs;
- replicated metadata stores;
- static configuration;
- another mechanism that preserves the normative discovery semantics.

The choice of implementation must not change the identity represented by a
resolved `ActorRef` or `GroupRef`, make an existing concrete reference retarget
after name rebinding, manufacture lifetime or durability, or grant authority not
already carried by the resolved capability.

Discovery implementation also must preserve the pay-as-you-grow rule. A program
that does not use a discovery/distributed facility must not require a network
listener, external registry, Cluster membership, background discovery protocol,
or equivalent distributed runtime merely because an implementation supports
such machinery.

Core v0.1 therefore does not standardize:

- registry protocol or wire format;
- storage engine or replication algorithm;
- cache topology or cache invalidation strategy;
- polling versus push/watch implementation;
- backend selection or fallback order;
- registry server placement;
- health-probe implementation;
- implementation-specific TTL bookkeeping;
- implementation-specific retry/backoff policy.

Those mechanisms may affect performance and availability of the implementation,
but they must not retroactively choose otherwise-unfixed portable semantics.

This closure does not define a new public discovery API, namespace model,
consistency level, TTL contract, watch/notification semantics, federation model,
persistence guarantee, security model, or schema/versioning rule. Any such
portable facility remains subject to its own normative design.

This closes the former open ledger item `Service discovery implementation`.

## 72C. ActorRef Routing Implementation Is Runtime Machinery

**CLOSED**

Core v0.1 defines the semantic identity and communication behavior of an
`ActorRef`, but it does not standardize one internal mechanism for locating the
concrete Actor incarnation denoted by that reference.

A runtime may route an `ActorRef` through mechanisms such as:

- direct in-Process tables;
- Process- or Node-local routing tables;
- Cluster directories;
- distributed location metadata;
- cached routes;
- indirection through runtime-owned routing services;
- transport-specific endpoint metadata;
- another mechanism that preserves the normative ActorRef semantics.

The routing mechanism is not part of ActorRef identity. Updating, invalidating,
or replacing an internal route to the same live incarnation must not create a
new ActorRef identity or make two distinct Actor incarnations compare as one.

Routing machinery must preserve the existing rules that:

- an `ActorRef` denotes exactly one concrete Actor incarnation;
- the reference never retargets to a replacement Actor;
- destination death does not authorize transparent replay to another Actor;
- unreachability, unknown state, and known termination remain distinct;
- same-sender FIFO, snapshot, acceptance, backpressure, cancellation, failure,
  and delivery-uncertainty semantics do not depend on route-cache layout;
- Group routing remains a separate semantic layer and cannot be simulated by
  silently retargeting a concrete ActorRef.

A stale or missing internal route may lead only to outcomes already permitted by
the communication and reachability semantics. It must not cause a runtime to
invent a replacement destination, duplicate an accepted operation, erase
delivery uncertainty, or reinterpret one ActorRef as another.

Core v0.1 therefore does not standardize:

- ActorRef directory protocol;
- route-cache shape or eviction policy;
- route-refresh algorithm;
- routing-table replication;
- endpoint-address format;
- route lookup batching;
- implementation-specific forwarding hops;
- location-cache consistency mechanism.

These choices may affect performance and availability, but not portable ActorRef
semantics.

This closure does not define ActorRef persistence, serialization, external
capability encoding, or durable resolution across runtime restarts. Those remain
separate open design topics.

This closes the former open ledger item `ActorRef routing implementation`.

## 72D. Logical and Physical Topology Are Distinct

**CLOSED**

Core v0.1 distinguishes Protos logical topology from the physical topology used
to host or optimize an implementation.

The logical entities defined by the concurrency model retain only their
normatively specified meanings. In particular, a Protos `Process` is execution
capacity, a `Node` is runtime membership, and a `Cluster` is a coordination
domain. None of those meanings, by itself, creates a portable one-to-one mapping
to an operating-system process, host, virtual machine, container, pod, CPU
package, NUMA domain, rack, availability zone, region, subnet, or equivalent
infrastructure unit.

An implementation or infrastructure adapter may map logical entities onto
physical resources in any conforming way. It may co-locate multiple logical
entities, separate them physically, move implementation resources, or change
internal placement over time, provided every existing Protos-observable
identity, isolation, lifetime, authority, communication, failure, placement, and
continuity rule remains satisfied.

Therefore physical co-location does not:

- merge Actor, Process, Node, Group, or Cluster identity;
- create cross-domain mutable Protos references;
- grant authority or capability;
- make a non-transferable value transferable;
- imply same failure fate, shared lifecycle, or durable continuity;
- authorize ActorRef retargeting or Actor identity migration;
- imply that a particular transport must be used.

Conversely, physical separation does not by itself create a new Protos identity,
failure domain, message semantic, serialization contract, or distributed
authority boundary beyond those explicitly defined by the normative model.

Moving an implementation artifact or changing physical placement is not an Actor
migration unless a normative Actor-migration facility explicitly preserves the
Actor incarnation across that operation. Recreating an Actor elsewhere under the
ordinary lifecycle rules remains a new Actor incarnation with a new ActorRef.

Infrastructure topology may be used internally for placement, scheduling,
transport selection, failure avoidance, resource accounting, and administrative
diagnostics. Such use is implementation or policy machinery unless a separate
portable facility explicitly exposes a topology concept.

This closure does not decide the still-open APIs and policies for affinity,
anti-affinity, hard placement constraints, failure-domain configuration,
placement scoring, rebalancing, migration, capacity provisioning, or external
infrastructure adapters. Those facilities may deliberately refer to selected
physical concepts, but must define their own observable contracts.

This closes the former open ledger item `Relationship between logical Protos
topology and physical infrastructure topology`.

## Open Design Topics

The following topics remain intentionally open. Items whose fundamental
semantics are already closed are listed only for the API, policy,
mechanism, or implementation detail that still requires design.

-   Failure-domain discovery and configuration
-   Exact HA policy API and syntax
-   Exact Group/GroupRef API and syntax
-   Group creation/termination/durability API and ownership mechanics
-   Group controller API and controller-election mechanics
-   Group routing policy API
-   Advanced Group routing policies
-   Group broadcast and multicast semantics
-   Group membership transition protocol
-   Exact current-behavior installation/replacement API
-   Exact SendOperation API
-   Exact SendOperation states
-   Definition of what SendOperation `.value()` means
-   Delivery acknowledgement levels
-   Delivery guarantees
-   Retry API and policies
-   Message IDs and attempt IDs
-   Deduplication
-   Idempotency support
-   Persistent messaging
-   Special mailbox policies
-   Drop policies
-   Latest-only policies
-   Batching
-   Streaming
-   Async streams
-   Generators and suspendable iteration
-   Pub/sub
-   Advanced routers and load-balancing policies
-   Actor capacity policy
-   Process capacity provisioning policy
-   Node capacity provisioning policy
-   Capacity Demand API
-   Scale-up policy in infrastructure adapters/controllers
-   Scale-down policy in infrastructure adapters/controllers
-   Proactive capacity-demand signals
-   Draining policy and mechanics
-   Infrastructure Controller integration APIs
-   External infrastructure adapters such as Kubernetes or Nomad
-   Authority-scope API/model representation
-   Authority acquisition and transfer
-   Authority leases/election/consensus implementation
-   Authoritative/control-state storage and reconstruction
-   Fencing-token/API design for strict authoritative roles
-   Cluster authentication
-   Placement scoring algorithm
-   Placement stability and hysteresis
-   Placement policy priorities
-   Actor affinity and anti-affinity API
-   Hard placement constraints
-   Rebalancing algorithms and policy
-   Optional live Actor migration mechanism
-   Actor persistence
-   Actor checkpointing
-   Durable-state recovery API/mechanism
-   State replication
-   Replicated Actor/service semantics
-   ActorRef persistence/serialization semantics, if any
-   GroupRef persistence/serialization and capability semantics, if any
-   Message serialization format
-   Serialization versioning
-   Schema evolution
-   Timers
-   Clock semantics
-   Resource limits and quotas
-   Runtime resource-pressure model
-   Actor resource-cost estimation and learning
-   ActorRef/GroupRef capability security and authorization
-   Remote authentication
-   Cluster configuration UX
-   Cluster lazy startup
-   Node lazy activation
-   Durable Cluster bootstrap after zero active Nodes
-   Runtime metrics architecture
-   Scheduler/advisor interaction
-   Scheduler/capacity-demand interaction
-   Code identity for remote Actor bootstrap
-   Code availability and versioning across Nodes
-   Hot code update
