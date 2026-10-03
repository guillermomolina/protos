# Protos Concurrency Design Notes

Status: Non-normative design material
Last updated: 2026-09-04

This document preserves unresolved and directional concurrency-design material
from the retired legacy concurrency ledger formerly stored at
`spec/PROTOS_CONCURRENCY_MODEL.md`.

It is not a language specification and defines no observable Protos behavior.
Normative concurrency semantics are owned by the modular specifications under
`spec/concurrency/`, together with the applicable semantic and I/O owners.

Historical migration-only redirections were intentionally discarded when the
ledger was retired. Git history remains the authoritative record of those moves.

When a design item here becomes a normative decision, update its real owning
specification document and record that change in `spec/PROTOS_SPEC_CHANGELOG.md`.
Do not make this design document a second normative authority.

## 5. Actor Turns and Reentrancy

The formerly normative mixed contract has been split among `concurrency/ACTORS.md`, `concurrency/FUTURES_AND_TASKS.md`, `concurrency/PARALLEL_EXECUTION.md`, and `semantics/VALUES_AND_COLLECTIONS.md`. This ledger heading is now historical/navigation material only and defines no observable semantics.

## 31. Runtime Advisor

**CLOSED AS DIRECTION**

The runtime may provide an advisor capable of detecting problematic
patterns such as:

-   Long-running Actor turns
-   Mailbox saturation
-   Stuck Futures
-   Resource saturation
-   CPU-heavy work inside an I/O-oriented Actor
-   Excessive large-value transfers
-   Poor Actor placement
-   Communication hotspots

When an optimization is semantically invisible, the runtime should
prefer applying it automatically.

If repeated transfers or placement remain architecturally expensive, the
advisor may recommend changing Actor boundaries or placement.

## 35. Scope Roots

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

The runtime has one Process execution domain for every Protos execution
and may expose conceptual root capabilities corresponding to larger active
execution scopes:

    ClusterRoot
        |
        v
    NodeRoot
        |
        v
    Process
        |
        v
    RootActor

Only RootActor is an ordinary Protos Actor.

Process, NodeRoot, and ClusterRoot are runtime entities/capabilities rather
than ordinary Actors. Process exists even in the minimal standalone case;
NodeRoot and ClusterRoot may be absent when their scopes are not active.

Conceptual responsibilities include:

ClusterRoot:

-   Cluster membership
-   Discovery
-   Distributed placement coordination
-   Cluster health
-   Distributed authority facilities when required

NodeRoot:

-   Node resources
-   Local Processes
-   Node health
-   Local coordination

Process:

-   Process lifecycle
-   Actor management
-   Root failure authority
-   Runtime services
-   Custody of Process-local application-facing host authority, including
    arguments, environment, and standard I/O when provisioned

RootActor:

-   Application-level mutable state
-   Application code
-   Normal Protos execution

Process-local host facilities may be lazy and need not be materialized if
the application never requests them. Their I/O-facing semantics are defined
in `io/IO_CORE.md`.

A standalone Process does not need active distributed Cluster
infrastructure.

NodeRoot and ClusterRoot facilities should be lazy or absent when they
are not needed.

No intrinsic ApplicationRoot exists above Process RootActors. A higher
level application/service identity may be introduced later for
administrative purposes only if it provides semantics not already
covered by Groups, discovery, durability, and runtime coordination.

## 36. Actor Runtime Context References

**CLOSED AS DIRECTION --- REVISED**

An Actor may have access to a minimal set of implicit runtime references
or capabilities without inheriting mutable state.

These may conceptually include:

-   `selfActor`
-   `parentActor`
-   `rootActor`
-   Failure-authority or supervisor capability
-   Node root capability
-   Cluster root capability

The exact names and APIs remain open.

The Process capability is deliberately not part of this implicit-runtime-reference list. An Actor has Process authority only when that capability was explicitly provisioned to the Actor at creation or explicitly delegated later according to the applicable capability-transfer semantics. Provisioning a subordinate Process-local facility does not implicitly grant the whole Process capability.

These values do not provide direct shared-memory access to another
Actor's mutable object graph.

## 39. Cluster

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

A Cluster is the logical distributed coordination domain across Protos
Nodes.

Cluster identity is independent of the identity and current count of its
Nodes.

Nodes may join, leave, fail, or be replaced while the logical Cluster
remains the same coordination domain. A Cluster may conceptually have
zero current Nodes.

Cluster functionality may include:

-   Membership
-   Discovery
-   Routing coordination
-   Placement coordination
-   Node health
-   Resource awareness
-   Failure detection
-   Group/controller coordination
-   Authority facilities when required
-   Rebalancing

Cluster identity is logically independent of current Node membership,
but persistence of that identity and its defining authoritative/control
state across complete loss of runtime membership is not intrinsic.

If the state defining the Cluster is explicitly preserved, later Nodes
may rejoin the same logical Cluster. If all defining runtime/control
state is lost, a later bootstrap creates a new Cluster identity rather
than magically reconstructing the old one.

Cluster functionality must remain lazy and must not impose distributed
runtime costs on ordinary standalone programs.

## 41. Actor and Service Discovery

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

Concrete identity-bearing references and discovery names are separate
concepts.

Conceptually:

    ActorRef(A)       -> one concrete Actor A
    GroupRef(G)       -> one concrete Group G
    discovery name    -> may resolve to an ActorRef or GroupRef

Discovery names may be rebound over time.

Concrete ActorRef and GroupRef values are not rebound when a discovery
name later resolves to a replacement identity.

For example, if `"workers"` resolves to Group G1 and a caller obtains a
GroupRef for G1, that GroupRef remains G1 even if G1 later terminates and
the name `"workers"` is rebound to G2.

Discovery remains pluggable. Protos does not hard-code DNS, Consul,
Kubernetes, or another external service as the semantic discovery model.

Exact discovery APIs, naming rules, persistence, authentication, and
resolution mechanisms remain open.

## 42. Automatic Actor Placement and Capacity

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

Normal Actor creation expresses the intent to create an Actor, not a
physical placement command.

The programmer does not normally choose separate APIs for local Process,
Node, or Cluster placement.

The scheduler selects among currently available Protos execution capacity
according to the active runtime domain, feasibility, resource state,
locality, affinity, availability policy, and placement policy.

`spawn()` itself does not create Processes, Nodes, Clusters, Pods, VMs,
or machines.

Scheduling and infrastructure provisioning are separate responsibilities.

The scheduler uses capacity already incorporated into Protos through
normal bootstrap/discovery/membership mechanisms.

If suitable capacity is unavailable, admission may remain pending: the
same Actor remains initializing and capacity shortage alone is not an
automatic spawn failure. The runtime may observe or expose
implementation-specific capacity or pressure feedback, but Protos does
not require a semantic capacity-shortage signal, value, or stream, nor any
particular signal shape, cadence, aggregation model, or proactive timing.

External infrastructure may independently add or remove raw execution
capacity. No infrastructure-provisioning controller is required as a
Protos semantic institution, and external infrastructure is not Protos
semantic authority. Raw capacity becomes usable by Protos only through the
normal Protos runtime lifecycle (bootstrap, discovery, and membership as
applicable). `spawn()` does not provision infrastructure, and correctness
does not depend on graceful draining of removed capacity.

External infrastructure does not directly create Actors, Group members,
Protos Nodes, or Cluster membership merely by creating physical
workloads.

Advanced hard placement constraints and soft placement hints may be
provided later, but physical location is not Actor identity.

The same Protos program should scale from a standalone Process to larger
runtime domains without changing the fundamental Actor programming
model.

## 47. Multi-Objective Placement and Rebalancing

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

Protos does not define one universal PACK or SPREAD placement strategy,
and Core does not require a universal objective function, a placement
score, a scheduler pipeline, or any particular ordering or priority among
placement objectives.

Hard feasibility is separate from optimization and remains mandatory
where applicable: a hard constraint is not a soft preference, and soft
optimization never makes an infeasible destination valid.

Within the feasible set, implementations MAY optimize placement using one
or more objectives. Illustrative implementation/policy examples include:

-   Locality
-   Load spreading
-   Resource efficiency
-   Stability
-   Availability evidence

PACK and SPREAD are possible resulting implementation strategies rather
than universal language modes. For example, an implementation may favor
locality for communication-heavy Actors or spreading for CPU-heavy
Actors. Availability-oriented placement may only be claimed satisfied on
demonstrable evidence of failure independence; HA placement is not
persistence, replication, consensus, or exactly-once delivery.

Rebalancing is the process of improving runtime placement or distribution
over time. It does not imply live Actor migration.

Rebalancing may occur by:

-   Choosing better placement for newly created Actors
-   Choosing better placement for replacement Actors
-   Changing Group membership or cardinality over time
-   Avoiding new placement on capacity being retired
-   Retiring excess or poorly placed members
-   Optionally using live Actor migration if a future implementation can
    preserve the same live Actor incarnation and observable execution
    state

The runtime should avoid churn: rebalancing should occur only when the
expected benefit justifies its cost and disruption.

Exact rebalancing algorithms and policies, hysteresis, affinity APIs,
and migration mechanics remain open; any placement optimization strategy
is implementation freedom.
