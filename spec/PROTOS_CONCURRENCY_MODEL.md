# Protos Concurrency Model v0.1

Language version: 0.1
Document revision: 328
Status: Design ledger — non-normative
Last updated: 2026-09-04
# Protos Multithreading Design Ledger


### Status of this design ledger

This file is retained as historical design context and as a home for unresolved
concurrency design work. It is entirely non-normative.

All CLOSED observable concurrency semantics have been migrated to the normative
modules under `concurrency/`, `semantics/`, and `io/`. A `CLOSED` marker that may
appear in historical prose in this ledger does not grant normative authority to
this file after revision 328.

Implementations must use the applicable normative modular specification, not
this ledger, to determine observable behavior. OPEN, PENDING, DIRECTION,
DETAILS OPEN, API OPEN, and other design-qualified material remains design work
only.

## 1. General Principle

The formerly normative contract has moved to `concurrency/ACTORS.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 2. Fundamental Model

The formerly normative contract has moved to `concurrency/ACTORS.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 3. Future Versus Actor

The formerly normative contract has moved to `concurrency/ACTORS.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 4. Actor Isolation

The formerly normative contract has moved to `concurrency/ACTORS.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 5. Actor Turns and Reentrancy

The formerly normative mixed contract has been split among `concurrency/ACTORS.md`, `concurrency/FUTURES_AND_TASKS.md`, `concurrency/PARALLEL_EXECUTION.md`, and `semantics/VALUES_AND_COLLECTIONS.md`. This ledger heading is now historical/navigation material only and defines no observable semantics.

## 6. I/O

The formerly normative contract has moved to `concurrency/ACTORS.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 7. Ordering and Fairness

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 8. Actor Creation

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 9. Actor Initialization and Readiness

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 10. Implicit Event Loop

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 11. Current Behavior

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 11A. Behavior Requirement at the READY Cutover

The normative contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 12. Actor Message Dispatch

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 13. send()

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 14. request()

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 15. send() and request() Share Delivery and Dispatch

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 16. Pass-by-Value Between Actors

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 17. Message Snapshot Time

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 18. Message Transfer Optimizations

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 19. Buffers

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 20. End-to-End Backpressure

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 20A. Same-Host and Shared-Memory Transport Are Semantically Invisible

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 20B. Transport Selection and Switching Are Runtime Policy

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 20C. Physical Locality Discovery Is Runtime Machinery

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 21. Mailbox Bounds

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 22. Timeouts and Deadlines

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 23. Cancellation

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24. Structured Concurrency

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24A. Actor Termination Request and Graceful Stop

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24B. Actor Lifetime Is Not Reachability Garbage Collection

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24C. Unhandled Errors Are Fatal to the Actor Incarnation

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24D. Actor-Local CPU Work Is Cooperatively Non-Preemptive

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24E. Waiting for Multiple Futures with `Future.all(...)`

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24F. No Generic Future Race/Select in Core

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24G. Future Ownership and Actor Lifecycle Matrix

The normative Future/Task contract formerly contained here has moved to `concurrency/FUTURES_AND_TASKS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24H. Non-Local Return Never Crosses an Actor Boundary

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24I. Dynamic Error Handlers Never Cross Actor Boundaries

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24J. Blocking Foreign Calls and Physical Offload

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24K. Foreign Mutable State Does Not Bypass Actor Isolation

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 24L. Non-Transferable Resources Never Auto-Proxy

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 25. Parent Actor Versus Failure Authority

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 26. Supervision and Failure Authority

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 26A. Core Failure-Authority Policy and API Boundary

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 27. Actor Identity Is Incarnation Identity

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 28. Messages Across Actor Failure

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 29. Monitoring Versus Supervision

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 29A. Actor Lifecycle Monitoring API

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 30. Runtime Health and Watchdog

The normative Actor-domain contract formerly contained here has moved to `concurrency/ACTORS.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

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

## 32. Root Actor

The formerly normative contract has moved to `concurrency/ACTORS.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 33. Global State

The formerly normative contract has moved to `concurrency/ACTORS.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 34. Actor Module State

The formerly normative contract has moved to `semantics/MODULES.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 34A. Module Implementation Sharing Is Semantically Invisible

The formerly normative contract has moved to `semantics/MODULES.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

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

## 37. Process Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 37A. Process Failure Knowledge Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 38. Node

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 38A. Node Failure Knowledge Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 38B. Network-Partition Knowledge and Reporting Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 38C. Core Split-Brain Safety Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

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

## 39A. Core Cluster Membership Protocol Boundary

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 40. ActorRef

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

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

If no suitable capacity exists, admission may remain pending and may
produce semantic capacity-demand signals. An external or explicitly
integrated Infrastructure Controller may react by provisioning raw
execution capacity, but that capacity becomes usable by Protos only after
normal runtime bootstrap and membership.

External infrastructure does not directly create Actors, Group members,
Protos Nodes, or Cluster membership merely by creating physical
workloads.

Advanced hard placement constraints and soft placement hints may be
provided later, but physical location is not Actor identity.

The same Protos program should scale from a standalone Process to larger
runtime domains without changing the fundamental Actor programming
model.

## 43. Spawn Backpressure

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 44. Hierarchical Runtime Domains

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 45. Dynamic Actor Capacity and Placement

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 46. Adaptive Admission Control

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 47. Multi-Objective Placement and Rebalancing

**DIRECTION CLOSED, DETAILS OPEN --- REVISED**

Protos does not define one universal PACK or SPREAD placement strategy.

Placement balances multiple objectives such as:

-   Performance
-   Scalability
-   Availability/resilience
-   Resource efficiency
-   Locality
-   Stability

PACK and SPREAD are possible resulting strategies rather than universal
language modes.

Communication-heavy Actors may benefit from physical locality. CPU-heavy
Actors may benefit from spreading. High-availability requirements may
override a pure performance optimum.

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

Exact placement scoring, hysteresis, affinity APIs, and migration
mechanics remain open.

## 48. Failure Domains

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 49. High-Availability Placement Requirements

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 50. Runtime Groups

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 51. Capacity Demand and Infrastructure Integration

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 52. Ephemeral Actor Liveness and Explicit Durability

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 53. Direct ActorRef Versus Stable Group Identity

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 54. Actors Are Replaced, Never Restarted

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 55. Supervision Versus Group Controllers

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 56. Failure Authority Is Pay-As-You-Grow

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 57. Outstanding Direct Interactions Die With the Actor

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 58. Rerouting Belongs to Group Addressing

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 59. Termination, Unreachability, and Unknown State

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 60. Unreachability Applies Backpressure

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 61. Processes Are Ephemeral Capacity

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 62. Nodes Are Ephemeral Runtime Members

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 63. Cluster Identity Is Independent of Node Identity

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 64. Loss of Node Membership Does Not Prove Physical Process Death

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 65. Local Execution May Continue Without Higher-Scope Authority

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 66. Authority

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 67. Authority Is Scoped

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 68. Controllers Are Ephemeral

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 69. Authority and Control State Are Mechanism-Independent

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 70. External Authority and Infrastructure Mechanisms Do Not Define Protos Semantics

The normative distributed-runtime contract formerly contained here has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 71. Isolated Parallel Execution

The normative contract formerly contained here has moved to `concurrency/PARALLEL_EXECUTION.md`. This ledger heading remains only as a compatibility/navigation anchor and is not a second normative owner.

## 72. Standard Prelude Sharing

The formerly normative contract has moved to `semantics/MODULES.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 72A. No Core Application or Service Identity

The formerly normative contract has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 72B. Service Discovery Implementation Is Not Core Semantics

The formerly normative contract has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 72C. ActorRef Routing Implementation Is Runtime Machinery

The formerly normative contract has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.

## 72D. Logical and Physical Topology Are Distinct

The formerly normative contract has moved to `concurrency/DISTRIBUTED_RUNTIME.md`. This heading is retained only for historical/navigation purposes in this non-normative design ledger.
