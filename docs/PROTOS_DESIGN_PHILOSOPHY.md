# Protos Design Philosophy

> **Status:** Non-normative design philosophy  
> **Purpose:** Explain the architectural principles, ambitions, and reasoning that guide Protos design.  
> **Authority:** This document does not define language semantics. Normative behavior is defined by the applicable specifications under `spec/`.

## 1. Why this document exists

Protos is intended to be more than a collection of individually attractive language features.

The project aims to build a small, coherent semantic universe that can grow from tiny programs to large concurrent and distributed systems without forcing programmers to abandon the model they learned at the beginning.

Many of the most important design decisions are therefore not local decisions. A choice about objects can affect closures. A choice about closures can affect concurrency. A choice about concurrency can affect I/O, resource lifetime, distribution, security, scalability, and implementation freedom.

The specifications define what Protos means. `AGENTS.md` and `spec/AGENTS.md` define how design work should be performed. This document records the broader philosophy that explains why those rules exist and what kind of language they are intended to produce.

It is deliberately non-normative. An attractive idea written here does not become a language guarantee until the relevant normative specification closes it explicitly.

---

## 2. The central ambition: a small universe that scales

Protos should feel like one language from beginning to end.

A programmer should not begin with one simple language and later discover that concurrency, parallelism, distribution, resource management, or scale require entering unrelated semantic universes.

The ambition is:

> Start small. Grow by composition. Pay for complexity only when the problem actually requires it.

A trivial program should be trivial.

An ordinary application should not need distributed-systems concepts.

A highly concurrent service should not need manual memory-order reasoning.

A CPU-parallel algorithm should not require pretending that every unit of parallel work is a persistent distributed entity.

A distributed system should gain identity, isolation, messaging, failure, and lifecycle semantics because it needs them, not because every Protos program had to carry them from the beginning.

The same underlying language should remain recognizable at every scale.

---

## 3. Build a small universe, not a collection of features

A language can have few keywords and still have enormous conceptual complexity.

Conversely, a language can introduce an additional abstraction and become simpler overall if that abstraction cleanly separates responsibilities that were previously entangled.

Protos therefore minimizes **total conceptual complexity**, not merely the number of named concepts.

A new abstraction earns its place when it removes more independent rules, special cases, overloaded responsibilities, or scaling failures than it introduces.

This leads to a recurring design question:

> Is this a new law of the Protos universe, or can it be built naturally from laws that already exist?

The preferred answer is usually composition.

But composition is not a religion. If an existing abstraction is being forced to span genuinely different semantic boundaries, adding a new layer may be simpler than continuing to overload the old one.

---

## 4. Mechanisms over institutions

Protos prefers general mechanisms from which useful structures can emerge.

A common pattern is not automatically a language primitive.

A familiar abstraction is not automatically fundamental.

A feature used by many programs is not automatically something the language core must know by name.

Libraries and programs should be able to build institutions inside the Protos universe whenever ordinary objects, closures, messages, values, Futures, Actors, protocols, or other existing mechanisms are sufficient.

The core should provide what cannot be implemented safely, portably, or semantically correctly at a higher level.

This distinction is especially important for concurrency. The runtime may need to provide isolation, scheduling, or simultaneous CPU eligibility, while parallel map, parallel sort, batching policies, work partitioning strategies, and similar algorithms may remain library concerns.

---

## 5. No pets

Protos should resist privileged entities.

A design becomes suspicious when its explanation depends on:

- this particular object being special;
- this particular instance being recognized by the runtime;
- one globally distinguished mutable object;
- one magical context;
- one special library value that bypasses normal rules;
- one identity-based exception.

Prefer:

> Any object satisfying property X behaves according to rule Y.

over:

> This particular object behaves according to rule Y.

Some privileged concepts may be unavoidable. `Object`, for example, is the unique root of the delegation hierarchy because the object model requires a root. Such exceptions must be justified by fundamental semantics, not convenience.

Internal implementations may recognize or optimize specific objects, representations, constants, or operations, but those optimizations must not create new observable semantics.

---

## 6. Ordinary things should remain ordinary

Language facilities should participate in the ordinary Protos model wherever practical.

Objects remain objects.

Closures remain closures.

Messages remain messages.

Execution contexts remain objects.

A method is not a second executable value kind merely because a closure is invoked through a receiver.

A parallel computation should not acquire persistent Actor identity merely because it may execute on another CPU core.

An implementation artifact should not become a semantic object merely because the runtime needs it internally.

This principle keeps the universe understandable and allows existing rules to compose instead of creating parallel semantic systems.

---

## 7. General rules beat special cases

A strong rule explains many cases.

A weak rule accumulates exceptions.

Phrases such as these are design warnings:

- except when;
- normally, but;
- only for this object;
- unless the runtime detects;
- because this implementation needs;
- users probably intended.

Exceptions can be necessary, but each one consumes conceptual budget and creates interaction surface with the rest of the language.

Protos should prefer structural explanations over exception tables.

---

## 8. Semantic distinctions should remain visible

Uniformity is valuable only when it does not erase real semantic differences.

Two operations should not be merged merely because their implementations look similar.

Two values should not be treated as identical merely because a host runtime represents them similarly.

Two concurrency mechanisms should not be conflated merely because both eventually return a Future.

If two mechanisms have different invariants, authority, lifecycle, ordering, isolation, failure, or scaling properties, those differences should remain visible at the appropriate semantic boundary.

The runtime should not guess programmer intent where the language can express the distinction directly.

---

## 9. Fail where the invariant is violated

Errors should occur where a semantic invariant is broken.

Absence is not failure.

`null` is not a universal fallback.

A missing slot is not `null`.

A failed isolation requirement should not silently become shared mutable access.

A malformed value should not silently become a platform-dependent approximation.

A runtime should not hide an invalid operation by inventing behavior that happens to be convenient.

Predictability comes partly from knowing that invariants are enforced rather than heuristically repaired.

---

## 10. Semantics before syntax

Syntax is the last part of a language feature, not the first.

Before adding syntax, Protos should understand:

1. the capability being requested;
2. the semantic boundary involved;
3. the invariants;
4. the failure behavior;
5. interactions with existing mechanisms;
6. concurrency and resource consequences;
7. scaling behavior;
8. portability requirements;
9. implementation freedom;
10. whether the feature belongs in the language at all.

Only after that should syntax be designed.

Parser convenience is not sufficient justification for expanding the language.

Implementation convenience is not sufficient justification for changing semantics.

A good syntax should reveal a good semantic model rather than compensate for an unclear one.

---

## 11. The specification defines Protos

The implementation is not the language.

Truffle is not the language.

GraalVM is not the language.

The JVM is not the language.

A test suite is not the language.

Historical behavior is not the language.

The normative specifications define observable Protos semantics.

Implementation machinery may vary radically while remaining a valid Protos implementation.

This is intentional. A language specification should constrain what programs can observe, not unnecessarily constrain how an implementation achieves it.

---

## 12. Observable behavior matters; machinery does not

Protos should specify enough to make independent implementations compatible while preserving maximum implementation freedom behind the semantic boundary.

An implementation may use:

- tagged values;
- specialized layouts;
- caching;
- JIT compilation;
- interpretation;
- bytecode;
- native compilation;
- work stealing;
- worker pools;
- copy-on-write;
- page remapping;
- shared immutable storage;
- locks;
- atomics;
- lock-free algorithms;
- different garbage collectors;
- different scheduling strategies;

provided those choices do not change observable Protos semantics.

This separation is one of the project's most important freedoms.

The language should define **logical truth**.

The runtime chooses **physical strategy**.

---

## 13. Keep platform differences at the boundary

Portable semantics should be defined wherever a coherent portable abstraction exists.

Linux, Windows, POSIX, the JVM, Truffle, filesystems, network stacks, CPU architectures, and host runtimes inevitably differ.

Those differences should not leak randomly into ordinary Protos semantics.

The goal is not the weakest common denominator.

The goal is:

> Define the strongest coherent portable abstraction Protos can guarantee, and make genuine platform-specific capabilities explicit at the boundary.

This preserves portability without pretending that all platforms are identical.

---

## 14. Pay only for what you use

A program should not pay for a capability it does not use.

The cost considered here is broader than execution time.

Unused capability should avoid unnecessary:

- runtime initialization;
- memory;
- threads;
- synchronization;
- coordination;
- network infrastructure;
- metadata;
- conceptual complexity;
- syntax;
- failure modes;
- deployment requirements;
- operational machinery.

A command-line program should not need a distributed runtime.

A single-threaded program should not need a memory-order model in the programmer's head.

A cooperative task should not need Actor identity.

A CPU-parallel operation should not need a mailbox.

A local Actor should not require cluster discovery.

A standalone process should not require membership consensus.

This principle applies both to implementation cost and to mental cost.

---

## 15. Pay as you grow

"Pay only for what you use" describes the absence of unused cost.

**Pay as you grow** describes the desired path as requirements increase.

Protos should allow a program to acquire stronger capabilities incrementally:

```text
ordinary sequential computation
        |
        v
cooperative concurrency
        |
        v
isolated CPU parallelism
        |
        v
persistent isolated Actors
        |
        v
multi-process / distributed Actor systems
        |
        v
cluster coordination where actually required
```

Each step should add the minimum new semantic machinery required by the new problem.

Lower levels should remain understandable without knowing the details of higher ones.

Higher levels should reuse lower-level concepts rather than replace them with unrelated ones.

The programmer should not have to rewrite the conceptual model merely because the program grew.

This is stronger than backwards compatibility.

It is an architectural requirement:

> Growth should add capability more quickly than it adds conceptual burden.

---

## 16. Scale by composition, not by changing universes

Scaling should not require abandoning ordinary Protos.

The same objects, closures, messages, values, and protocols should continue to matter as systems grow.

A new layer is justified when scale reveals a genuine new boundary:

- lifecycle;
- identity;
- isolation;
- authority;
- persistence;
- failure;
- distribution;
- coordination.

The layer should solve that boundary without infecting simpler layers with its machinery.

The best scaling abstraction feels like a missing piece of the existing universe, not a foreign subsystem bolted onto it.

---

## 17. Lower layers must remain protected

A capability added to a stronger layer must not silently weaken or complicate the layers below it.

This is a general design rule:

> Strengthening one layer must preserve the guarantees and cost model of weaker layers unless a deliberate language-wide change has been justified.

For concurrency in particular:

- adding isolated parallel execution must not change ordinary Future semantics;
- adding Actors must not make ordinary cooperative code pay for Actor messaging or lifecycle;
- adding distribution must not make local Actors depend on network availability;
- adding cluster coordination must not prevent unrelated local computation from progressing;
- adding a physical optimization must not make its representation observable.

A useful mental test is:

> Can we gain capability here without losing simplicity, guarantees, or performance somewhere that does not use it?

---

## 18. Prefer independence over coordination

Coordination is expensive technically and conceptually.

When multiple valid designs exist, Protos should prefer the one that allows independent components to make progress with the least necessary coordination.

This favors:

- local state over global state;
- immutable data over shared mutable data;
- isolation over locking when appropriate;
- message passing over shared coordination when it fits;
- bounded ownership over global ownership;
- explicit synchronization boundaries over pervasive synchronization;
- local failure over unnecessary failure propagation.

This does not mean that everything must be asynchronous or lock-free.

Sometimes a local synchronous operation is the simplest and best design.

The objective is not to minimize the number of locks in an implementation.

The objective is to minimize **semantic coupling and required coordination**.

---

## 19. Minimize shared mutable state

Shared mutable state combines two difficult properties:

- several parties can observe the same identity;
- several parties can change it.

That combination creates coordination requirements, races, memory-order concerns, failure interactions, and scaling pressure.

Protos therefore treats shared mutable state as something to minimize rather than normalize.

Prefer, where suitable:

- local mutable state;
- immutable sharing;
- isolated state;
- value transfer;
- snapshot semantics;
- exclusive authority;
- message passing;
- ownership transfer;
- copy-on-write;
- partitioned mutable authority.

The default question is:

> Can this state remain local?

If yes, keep it local.

---

## 20. Concurrency should hide machinery, not semantics

Protos aims to hide concurrency machinery in much the same spirit that garbage collection hides manual memory management.

The programmer should not normally need to reason about:

- OS threads;
- worker pools;
- carrier threads;
- mutexes;
- memory barriers;
- volatile variables;
- atomic ordering;
- scheduler queues;
- work stealing;
- ownership graphs.

But hiding machinery does not mean hiding semantic consequences.

Suspension must remain observable where it affects reasoning.

Isolation boundaries must remain real.

Ordering guarantees must be specified.

Failure must have defined behavior.

Mutable authority must not become ambiguous.

The runtime should absorb mechanical complexity while the language exposes the semantic distinctions necessary for correct reasoning.

---

## 21. The concurrency ladder

One of the clearest expressions of the Protos philosophy is its emerging concurrency architecture.

The model separates three fundamentally different needs:

```text
C — Cooperative concurrency
P — Isolated parallel computation
A — Actors
```

These are not merely three performance settings.

They solve different semantic problems.

A compact summary is:

```text
C
    share state
    share references
    serialize execution

P
    isolate an operation
    pass values/snapshots
    parallelize execution

A
    isolate a persistent entity
    pass values/capabilities
    parallelize/distribute entities
```

Another useful formulation is:

> C shares mutable identity and separates execution in time.  
> P shares computation, but separates mutable authority.  
> A persistently separates mutable identity and execution.

The exact normative semantics and APIs are defined, or remain to be defined, in the concurrency specification. This section explains the architecture rather than closing unresolved design questions.

---

## 22. C — Cooperative concurrency

Cooperative concurrency addresses the need for many logically concurrent activities that share the same mutable domain.

Conceptually:

```text
same state
same references
different turns
```

Actor-local Futures/tasks may interleave at explicit suspension points while ordinary mutable Actor-local Protos execution remains serialized.

This is appropriate for enormous amounts of asynchronous work:

- network services;
- user interfaces;
- I/O pipelines;
- orchestration;
- timers;
- protocol state machines;
- application workflows.

The important insight is that concurrency does not automatically require simultaneous mutation.

A system can support huge numbers of concurrent activities while retaining simple local reasoning between explicit suspension points.

C should be cheap enough to use pervasively.

---

## 23. P — Isolated parallel computation

Cooperative concurrency does not solve CPU parallelism inside one logical computation.

Creating persistent Actors merely to use several CPU cores would overload Actor semantics with a responsibility that does not require identity, mailbox, supervision, independent lifecycle, or distribution.

P fills that gap.

Conceptually:

```text
different mutable authority
same time
```

P exists for explicit CPU-parallel work whose pieces may execute simultaneously while preserving logical isolation.

Examples include:

- sorting;
- image processing;
- video processing;
- compiler passes;
- build work;
- numerical kernels;
- data-oriented processing;
- ECS workloads;
- map/filter/reduce patterns;
- partitionable graph algorithms;
- simulations.

The critical idea is that CPU parallelism does not require arbitrary simultaneously shared mutable Protos identity.

Logical isolation and physical representation are separate.

A runtime may safely use physical sharing when it can preserve the logical model.

---

## 24. A — Actors

Actors address a different need: persistent independent entities.

Conceptually:

```text
different state domains
different identities
different lifetimes
same time
```

An Actor provides a durable logical boundary for:

- mutable state;
- identity;
- execution;
- messaging;
- mailbox behavior;
- lifecycle;
- failure relationships;
- placement;
- potentially distribution.

Actors are therefore appropriate for persistent components of a system:

- services;
- sessions;
- game entities;
- devices;
- workers with long-lived state;
- distributed components;
- fault-isolated domains.

An Actor is not "a thread".

The runtime may multiplex many Actors over bounded physical execution resources.

Actor identity is semantic.

Thread identity is machinery.

---

## 25. Why C, P, and A are separate

The three layers exist because combining them would force unrelated responsibilities together.

If every Future were parallel, ordinary asynchronous code would acquire race and isolation problems.

If every parallel operation were an Actor, fine-grained CPU work would acquire unnecessary identity, mailbox, lifecycle, and distribution machinery.

If Actors shared mutable memory freely, persistent isolation would collapse into synchronization and memory-order reasoning.

The separation therefore follows actual semantic boundaries:

| Layer | Primary problem | Mutable relationship | Execution | Persistent identity |
| --- | --- | --- | --- | --- |
| C | concurrency | shared within domain | serialized/interleaved | no new identity |
| P | CPU parallelism | isolated/exclusive authority | simultaneous eligible | no |
| A | persistent independent state | isolated domains | simultaneous eligible | yes |

The ladder is not about making the language more complicated.

It exists to prevent one abstraction from becoming complicated enough to solve three unrelated problems badly.

---

## 26. Logical isolation does not imply physical copying

A crucial Protos principle is:

> Logical semantics must not unnecessarily prescribe physical representation.

An isolation boundary may semantically transfer a value or snapshot without requiring an eager deep copy.

An implementation may use:

- immutable sharing;
- copy-on-write;
- structural sharing;
- shared immutable backing storage;
- page remapping;
- zero-copy techniques;
- semantically invisible ownership transfer;
- other equivalent mechanisms.

The programmer reasons about logical isolation.

The runtime is free to eliminate physical copying whenever the optimization is unobservable.

This is essential for making safe semantics compatible with large data and high performance.

---

## 27. Separate mutable authority, not necessarily bytes

For parallel computation, the important boundary is not whether physical memory is shared.

The important boundary is whether two simultaneous computations can mutate the same logical state.

A useful governing idea is:

> Physical storage may be shared while mutable authority remains exclusive.

This allows efficient partitioned algorithms without opening the entire shared-memory concurrency problem.

For example, a large byte buffer may be divided into disjoint writable regions that can be processed simultaneously.

The semantic requirement is disjoint mutable authority, not a particular memory layout.

This distinction gives implementations room for aggressive optimization while preserving a simple programming model.

---

## 28. Partition logical state, not merely addresses or indexes

Disjoint physical ranges do not automatically imply disjoint mutable state.

Consider an array whose two different elements reference the same mutable object.

Splitting the array by index does not make the referenced object graph disjoint.

Therefore safe partitioning must be defined over the abstract mutable state whose independence can actually be guaranteed.

This naturally favors values with meaningful partition structure, such as:

- byte buffers;
- dense numeric storage;
- suitably defined arrays;
- data-oriented containers;
- other runtime-enforceable partitionable values.

Protos should not pretend that arbitrary mutable object graphs can be safely parallelized merely because their top-level references occupy different slots.

---

## 29. Avoid a pervasive ownership language unless it is truly necessary

General ownership, borrowing, lifetime, and reference-capability systems can express powerful guarantees.

They also impose pervasive conceptual and syntactic cost.

Protos should not adopt such machinery merely because one concurrency feature could use it.

If the required safety can be obtained through narrower isolation boundaries, explicit value transfer, runtime-enforced partitioning, or ordinary composition, those approaches better preserve the pay-as-you-grow goal.

A general ownership system should be considered only if realistic workloads demonstrate that the simpler universe is fundamentally insufficient.

Generality must be earned.

---

## 30. Shared mutable parallelism is deliberately not the default universe

There is an important family of algorithms built around arbitrary simultaneous shared mutable memory:

- lock-free structures;
- fine-grained atomic algorithms;
- CAS-heavy graph algorithms;
- explicit memory-order protocols;
- some NUMA algorithms;
- some HPC workloads;
- kernel and runtime structures.

Protos does not need to pretend these problems do not exist.

But it also does not need to make their machinery the ordinary programming model.

The current design direction deliberately prefers isolation, value semantics, partitioned authority, Actors, and runtime-managed mechanisms over exposing general-purpose locks, atomics, volatile state, or memory-order annotations in normal Protos code.

The burden of proof is intentionally reversed:

> Programmer-visible synchronization machinery should be added only when a realistic workload demonstrates that the simpler model is fundamentally insufficient.

---

## 31. Effects and authority are part of isolation

Isolation is not only about memory.

A computation may have authority to:

- perform I/O;
- send Actor messages;
- access files;
- access the network;
- spawn processes;
- observe clocks;
- obtain randomness;
- interact with native code;
- mutate external systems.

Therefore a safe parallel model cannot reason only about object aliases.

Capabilities and observable side effects matter too.

In particular, isolated CPU parallelism should not accidentally become a second Actor-like effects domain merely because it runs simultaneously.

The architecture should preserve the distinction:

```text
C / Actor-local execution
    coordinates application effects

P
    performs isolated computation

A
    owns persistent independent state and effects
```

The exact normative capability rules for P remain specification work. The philosophical requirement is that adding CPU parallelism must not accidentally import Actor identity, mailbox semantics, I/O authority, or other persistent-domain behavior without deliberate justification.

---

## 32. Publication should be a boundary

An isolated computation is easiest to reason about when partial internal state does not leak while the computation is in progress.

This suggests a powerful architectural property:

> Work may be physically incremental while publication remains semantically controlled.

For isolated parallel computation, the desirable model is that a completed result crosses the boundary, while failed or cancelled partial state does not become accidentally visible as mutation of the caller's ordinary mutable objects.

This is not necessarily transactional rollback.

It is separation between internal work and externally visible publication.

Such boundaries simplify failure, cancellation, aliasing, and implementation freedom simultaneously.

Where this idea becomes normative, the concurrency specification must define it precisely.

---

## 33. Determinism should not depend accidentally on scheduling

Parallel execution introduces a recurring danger: allowing physical scheduling decisions to become semantic decisions.

If two failures happen concurrently, "whichever worker happens to report first" may produce implementation-dependent behavior.

If a reduction tree depends on worker count, floating-point results may change merely because the machine has a different number of cores.

If chunk boundaries affect observable ordering, runtime optimization freedom becomes program behavior.

Protos should distinguish:

- intentional nondeterminism that is part of an abstraction;
- accidental nondeterminism caused by implementation scheduling.

When deterministic semantics are promised, worker count, scheduling order, queue structure, or work-stealing policy should not silently change the result.

This is part of the independent implementation test.

---

## 34. Bounded physical resources, abundant logical work

Logical concurrency should not imply one physical thread per logical activity.

Logical parallel work should not imply one physical thread per parallel operation.

Actors should not imply one OS thread per Actor.

The runtime should be able to multiplex abundant logical work over bounded physical resources.

This enables:

- millions of logical tasks without millions of threads;
- many Actors without one-thread-per-Actor scaling failure;
- nested parallel algorithms without unbounded thread creation;
- multi-Actor parallel workloads without oversubscription explosions.

The semantic model should describe what may execute concurrently or simultaneously.

The runtime decides how to map that permission onto available hardware.

---

## 35. Nested parallelism must compose

A parallel abstraction is incomplete if it works only at the top level.

Divide-and-conquer algorithms naturally create parallel work recursively.

A useful model is:

```text
parent owns mutable authority R
        |
        +-- child owns disjoint R1
        |
        `-- child owns disjoint R2
```

Child authority must derive safely from parent authority.

The parent must not simultaneously mutate state delegated exclusively to live children.

When children finish, authority may conceptually recombine.

The scheduler must also avoid turning bounded carrier resources into a deadlock or starvation mechanism when parent work waits for nested child work.

This is an example of why semantics, authority, and scheduling consequences must be designed together even though the exact scheduling machinery remains implementation-defined.

---

## 36. Distribution should not infect local computation

A distributed system has unavoidable concerns:

- reachability;
- partitions;
- routing;
- membership;
- failure detection;
- authority;
- placement;
- retries;
- durability;
- duplicate delivery;
- stale knowledge.

Those concerns are real where distribution exists.

They should not become requirements for local computation that does not need them.

A local Future should not care about cluster membership.

A local CPU-parallel sort should not care about network partitions.

A local Actor should not stop merely because unrelated higher-scope coordination is unavailable.

This is pay-as-you-grow applied to failure domains.

---

## 37. Local capability should survive higher-scope failure where valid

Loss of a stronger or broader capability should not unnecessarily destroy weaker local capability.

Examples of the principle include:

- loss of cluster authority need not stop unrelated local computation;
- loss of remote reachability need not imply local Actor termination;
- inability to perform an authoritative distributed operation need not disable ordinary local work;
- a network partition should not magically prove physical process death.

This preserves independence and prevents global coordination from becoming a hidden dependency of local progress.

---

## 38. Identity must be earned

Persistent identity is expensive.

Identity creates questions about:

- lifetime;
- equality;
- addressing;
- failure;
- replacement;
- reincarnation;
- routing;
- persistence;
- ownership;
- discovery.

Therefore not every piece of work should have persistent identity.

A Future represents an eventual result.

Parallel work represents computation.

An Actor represents a persistent isolated entity.

A Process represents execution capacity.

A Node represents runtime membership.

A Cluster represents a coordination domain.

These concepts should not be collapsed merely because an implementation could assign each one an internal ID.

Semantic identity should exist only where the model needs it.

---

## 39. Cattle by default, pets only when semantics demand them

Infrastructure capacity should normally be replaceable.

Processes and nodes are execution resources, not automatically durable application identities.

An application-level Actor may have meaningful persistent identity.

The machine currently hosting it usually should not.

The container currently running it usually should not.

The worker thread currently executing it certainly should not.

This distinction allows Protos to cooperate naturally with modern infrastructure without turning Kubernetes pods, VMs, hostnames, or runtime workers into language-level pets.

---

## 40. The Protos/native boundary

Protos aims to cover a very large programming territory, but not by pretending that every problem belongs at the same abstraction level.

A useful boundary is:

> Use Protos when the important semantics are behavior, state, interaction, isolation, composition, and scalable coordination.

> Use lower-level/native facilities when physical representation, hardware control, exact layout, explicit memory ordering, strict execution timing, or machine-level resource control are themselves part of the problem semantics.

Typical lower-level territory includes:

- kernels;
- boot code;
- interrupt handling;
- memory-mapped I/O;
- page tables;
- DMA;
- device drivers;
- tiny bare-metal environments;
- constant-time cryptographic primitives;
- explicit SIMD/HPC inner kernels where representation is semantic;
- lock-free runtime internals;
- garbage collectors;
- scheduler internals.

Typical Protos territory can include:

- applications;
- web services;
- enterprise systems;
- command-line tools;
- automation;
- network servers;
- distributed services;
- Actor systems;
- data pipelines;
- GUI applications;
- compilers;
- IDEs;
- build systems;
- game logic;
- scientific orchestration;
- ML orchestration;
- database planning, coordination, and service layers.

The boundary is not based on prestige or difficulty.

It is based on what must remain observable.

A useful test is:

> Could two semantically equivalent implementations use radically different physical representations without the program caring?

If yes, the problem likely belongs naturally above the low-level boundary.

If exact representation or machine behavior is itself required semantics, a lower-level implementation boundary may be more appropriate.

---

## 41. Native code is an implementation boundary, not an escape from semantics

Using native code underneath Protos does not mean abandoning Protos guarantees at the language boundary.

A runtime or library may implement an operation using Rust, C, assembly, SIMD, GPU kernels, operating-system APIs, or specialized accelerators.

The Protos-facing abstraction must still define:

- inputs;
- outputs;
- failure;
- authority;
- isolation;
- resource lifetime;
- observable ordering;
- concurrency behavior.

Low-level machinery belongs behind a semantic boundary.

It should not leak into ordinary Protos merely because the implementation needs it.

---

## 42. Safety and performance are not opposites

A recurring mistake in language design is to assume that safe logical semantics necessarily require naive physical implementation.

Protos rejects that assumption.

Isolation does not imply eager copying.

Immutability does not imply duplication.

Actors do not imply one thread each.

Futures do not imply one stack each.

Value transfer does not imply serialization.

Partitioning does not imply byte-by-byte copying.

A strong semantic model can often give the runtime **more** optimization freedom because the runtime knows which observations are impossible.

Safety should constrain observable behavior while leaving physical strategy open.

---

## 43. Generality must be earned by unrelated workloads

A design is not general because it has many parameters.

A design is general when the same small semantics survive very different realistic workloads.

When evaluating a major abstraction, Protos should attack it with workloads outside the motivating example.

For concurrency, useful stress cases include:

- high-concurrency HTTP servers;
- enterprise backends;
- web crawlers;
- GUIs;
- chat systems;
- multiplayer games;
- ECS/data-oriented engines;
- parallel sorting;
- image/video processing;
- compilers;
- build systems;
- MapReduce-style processing;
- database systems;
- distributed services;
- scientific computing;
- ML orchestration;
- exchanges and event processing;
- multimedia pipelines;
- large graph workloads;
- irregular shared-memory algorithms.

The goal is not to make every workload equally perfect.

The goal is to expose hidden cliffs and unnecessary exclusions.

---

## 44. Gain stars without losing stars

During design exploration it is useful to score how naturally an abstraction supports different workloads.

The literal score is not important.

The principle is:

> Improve weak workloads without degrading strong workloads that do not need the new capability.

If a new parallel facility makes sorting dramatically better but forces ordinary HTTP services to reason about shared-memory races, the language did not simply gain capability. It traded one strength for another.

If a new distributed abstraction improves cluster operation but makes small CLI startup depend on distributed infrastructure, it failed pay-as-you-grow.

A successful design tends to increase the reachable workload space while preserving the simplicity of already well-served workloads.

This is a practical form of monotonic architectural growth.

---

## 45. Design by attempted falsification

Protos design should not ask only:

> Can this work?

It should ask:

> Where does this fail?

Every substantial proposal should be attacked for:

- scalability cliffs;
- hidden copying;
- hidden retention;
- blocking;
- deadlock;
- starvation;
- oversubscription;
- aliasing;
- authority leakage;
- resource leaks;
- platform dependence;
- scheduler-dependent behavior;
- nondeterministic failure;
- accidental identity;
- global coordination;
- interaction explosions;
- implementation assumptions becoming observable;
- security consequences;
- future abstraction conflicts.

Counterexamples are valuable design tools.

Finding a case that breaks an abstraction before standardizing it is progress, not failure.

---

## 46. Trace second-order consequences

A local change can alter global reasoning.

Before accepting a semantic rule, follow its consequences through:

- lookup;
- delegation;
- slots;
- assignment;
- object construction;
- evaluation order;
- closures;
- lexical contexts;
- `this`;
- `super`;
- non-local return;
- errors;
- cleanup;
- cancellation;
- identity;
- equality;
- hashing;
- reflection;
- ordering;
- mutability;
- aliasing;
- ownership;
- concurrency;
- suspension;
- Actor isolation;
- I/O;
- resource lifetime;
- memory retention;
- scalability;
- security;
- portability;
- optimization freedom.

A design is not complete when the motivating example works.

It is complete when its consequences are understood well enough that unrelated language properties are not being changed accidentally.

---

## 47. Respect qualitative thresholds

Some "small" changes alter the nature of a language.

The first instance of a mechanism can be more important than the next ten instances.

Examples include introducing the first:

- reserved word;
- privileged runtime entity;
- implicit conversion;
- global mutable singleton;
- hidden suspension;
- shared mutable concurrency mechanism;
- platform-dependent semantic branch;
- general ownership system;
- mandatory distributed service.

The conceptual distance from zero to one can be larger than the distance from one to ten.

Crossing such a threshold should be treated as an architectural decision, not a minor convenience.

---

## 48. Preserve global properties

Protos has global characteristics that emerge from many local decisions.

Examples include:

- a small reserved-word set;
- ordinary object participation;
- explicit semantic distinctions;
- isolation rather than pervasive synchronization;
- implementation independence;
- portability;
- pay-as-you-grow;
- minimal shared mutable state;
- compositional scaling.

A local feature can damage one of these properties without looking dramatic in isolation.

Design review should therefore ask:

> What property of Protos stops being true if we accept this?

Sometimes the answer is worth the trade.

But the trade must be visible.

---

## 49. Independent implementation is a design test

A language specification succeeds when two competent independent implementers can read it and produce observably compatible systems.

If they must guess:

- which concurrent error wins;
- when a snapshot occurs;
- whether a result preserves identity;
- whether an operation blocks;
- whether scheduling order is observable;
- whether host overflow behavior leaks through;
- whether platform-specific behavior is permitted;

then the specification is unfinished.

This test prevents "the implementation obviously does X" from becoming accidental language semantics.

---

## 50. Deterministic where promised, nondeterministic where meaningful

Determinism is not always desirable or possible.

Distributed systems contain uncertainty.

Concurrent systems may intentionally expose nondeterministic selection.

Randomness exists.

External I/O exists.

The goal is not universal determinism.

The goal is to ensure that nondeterminism appears because the abstraction semantically permits it, not because the specification forgot to define something.

Intentional nondeterminism is a feature.

Accidental implementation dependence is ambiguity.

---

## 51. Resource lifetime is semantic when observable

Garbage collection can manage memory, but not every resource is memory.

Files, sockets, processes, transactions, mappings, external handles, and other resources may require explicit lifetime protocols.

Protos should distinguish:

- memory reachability;
- logical resource ownership;
- explicit close/release;
- unwind-safe cleanup;
- cancellation;
- process or Actor lifetime.

Finalization should not be treated as a reliable substitute for deterministic resource protocols.

Resource semantics should compose with errors, cancellation, Futures, Actors, and I/O.

---

## 52. I/O is a boundary, not a loophole

External systems introduce platform differences, blocking behavior, partial failure, resource ownership, buffering, and authority.

I/O should therefore be designed as a semantic boundary rather than allowing host behavior to leak directly into Protos.

Normal Actor-oriented I/O should compose with the concurrency model instead of secretly blocking an Actor's execution carrier.

Suspension should remain explicit in the language-level reasoning model.

Filesystem, process, network, and other authority should be represented deliberately rather than inherited accidentally from ambient host state where a stronger model is practical.

---

## 53. Authority should be explicit enough to reason about

Security and concurrency often meet at authority boundaries.

Possessing a reference or value may imply permission to perform an operation.

Crossing an isolation boundary may therefore transfer more than data.

Design should ask:

- what authority crosses;
- whether it is transferable;
- whether it is copyable;
- whether it is persistent;
- whether it can create external effects;
- whether it carries identity;
- whether it survives failure or migration.

This is especially important for Actor references, I/O resources, process handles, filesystem authority, and future parallel execution facilities.

A language that carefully isolates memory but casually leaks authority has not achieved meaningful isolation.

---

## 54. Avoid ambient global state

Global mutable state creates invisible coupling.

It complicates:

- tests;
- concurrency;
- Actors;
- embedding;
- multi-tenancy;
- security;
- deterministic reasoning;
- module isolation;
- distribution.

Where state can belong to a module, context, Actor, resource, explicit capability, or ordinary object, it should normally live there.

Shared immutable standard facilities may be physically shared when semantically safe.

Mutable runtime or library state should have a clear owner.

---

## 55. Abstractions should preserve optimization freedom

A semantic guarantee has a cost: implementations must preserve it forever within that language version.

Therefore Protos should specify exactly the guarantees programmers need and avoid exposing irrelevant machinery.

For example, promising one OS thread per Actor would destroy scheduler freedom without adding useful semantic meaning.

Promising eager deep copying across every isolation boundary would prevent copy-on-write and zero-copy strategies.

Promising arbitrary parallel reduction chunking as observable behavior would make hardware topology semantic.

Good abstraction design hides facts that programs should not depend on.

---

## 56. Do not optimize the specification for the current implementation

The current implementation matters as an engineering project.

It does not get veto power over language design.

A clean semantic model should not be rejected merely because Truffle makes another model easier.

Likewise, an implementation optimization should not be standardized merely because it currently exists.

The intended order is:

```text
semantic model
    ↓
normative specification
    ↓
semantic architecture
    ↓
implementation strategy
    ↓
optimization
```

Not the reverse.

---

## 57. Syntax has a conceptual budget

Every new token, keyword, punctuation role, implicit conversion, or contextual rule adds cognitive and grammar cost.

Protos should spend that budget deliberately.

When an ambiguity can be resolved structurally, prefer structural resolution.

When a library protocol suffices, avoid syntax merely for convenience.

When syntax genuinely reveals an important semantic distinction, it may be justified.

The objective is not syntactic minimalism for its own sake.

The objective is for syntax to carry semantic weight.

---

## 58. Explicit boundaries, simple interiors

A recurring Protos pattern is:

> Make important boundaries explicit so that code inside each boundary can remain simple.

Examples include:

- object delegation boundaries;
- slot creation versus modification;
- explicit suspension;
- Actor isolation;
- value/snapshot crossing;
- resource close protocols;
- authority boundaries;
- I/O boundaries.

Complexity that is unavoidable should be concentrated where the underlying problem actually changes.

It should not be diffused through every ordinary operation.

---

## 59. Simple things must stay simple

A language can become sophisticated without making elementary code sophisticated.

This is a hard requirement, not marketing.

A beginner writing:

```text
print("hello")
```

should not need to understand:

- Actors;
- Futures;
- isolation;
- cluster authority;
- snapshot semantics;
- resource transfer;
- memory ordering;
- worker pools.

A programmer using asynchronous I/O should learn cooperative suspension, not distributed membership.

A programmer using CPU parallelism should learn the P isolation boundary, not the entire Actor failure model.

A programmer building a distributed Actor system will necessarily learn more, because the problem itself contains more semantics.

Complex problems may require complex reasoning.

Simple problems should not.

---

## 60. Complex things must remain possible

Simplicity must not be achieved by forbidding serious systems.

Protos should aim to support:

- high concurrency;
- multicore execution;
- persistent isolated state;
- distribution;
- backpressure;
- failure;
- cancellation;
- structured concurrency;
- large data;
- resource control;
- native acceleration;
- long-running services.

The challenge is to expose these capabilities through composable semantic boundaries rather than forcing their complexity into the language's simplest layer.

---

## 61. Do not confuse convenience with simplicity

A shortcut can make one line shorter while making the language harder to reason about.

Implicit behavior often moves complexity rather than removing it.

Examples include:

- hidden suspension;
- silent conversion;
- implicit deep sharing;
- automatic failure swallowing;
- magical retry;
- scheduler-dependent winner selection;
- hidden capture transformation.

Protos should prefer local explicitness when it preserves global simplicity.

The relevant measure is not keystrokes.

It is the amount of state and semantics a programmer must keep in mind to predict behavior.

---

## 62. Make illegal or unsafe states difficult to express through normal mechanisms

Safety is strongest when it follows from the structure of the model.

Actor isolation is better than asking every Actor programmer to remember locks.

Exclusive partitioning is better than asking every parallel loop to prove manually that ranges do not overlap.

Explicit resource protocols are better than hoping garbage collection closes external resources in time.

A good abstraction turns a large class of mistakes into states that ordinary code cannot naturally construct.

This should be achieved without making simple code carry unrelated proof machinery.

---

## 63. Prefer semantic capability over implementation prescription

When designing a feature, first state what the program must be able to express.

For example:

Bad starting point:

> Protos needs a thread pool API.

Better starting point:

> Protos needs explicit isolated computation that may execute simultaneously on multiple CPU cores without weakening Actor-local mutable-state guarantees.

The second statement leaves room to discover a better abstraction and preserves implementation freedom.

This discipline applies across the language.

---

## 64. Libraries should carry policy; the core should carry guarantees

The runtime and core language should provide guarantees that libraries cannot safely manufacture themselves.

Libraries should carry policies that can vary without changing the semantic universe.

For parallel computation, the core/runtime may need to guarantee:

- isolation;
- safe crossing;
- simultaneous CPU eligibility;
- bounded scheduling;
- cancellation integration.

Libraries can then choose:

- chunk sizes;
- sorting algorithms;
- reduction structures where semantics permit;
- batching;
- pipeline strategies;
- domain-specific partitioning.

This keeps the core small without making libraries powerless.

---

## 65. Composition is the scalability strategy

A good Protos program should be able to combine:

- ordinary objects for behavior and state;
- closures for executable behavior;
- Futures for eventual results;
- cooperative tasks for abundant concurrency;
- isolated parallel computation for CPU work;
- Actors for persistent isolated domains;
- explicit I/O/resource capabilities;
- native libraries for machine-level kernels;
- distributed coordination only where required.

No single abstraction should be stretched until it becomes all of these things.

The architecture scales because the pieces compose at real boundaries.

---

## 66. The workload map is a design instrument, not a promise

It is useful to reason about where each concurrency level naturally fits:

```text
                          C        P        A
HTTP / async server      strong   minor    useful
GUI                      strong   minor    useful
chat / sessions          strong   minor    strong
parallel sort            weak     strong   unnecessary
image/video              useful   strong   minor
compiler                  useful   strong   useful
build system              strong   strong   useful
ECS/data-oriented         useful   strong   useful
MapReduce                 useful   strong   strong
database system           strong   strong   strong
distributed services      useful   minor    strong
scientific host logic     minor    strong   useful
ML orchestration          minor    strong   useful
large graph processing    limited  strong*  useful
irregular CAS-heavy work  weak     limited  minor
```

`strong*` depends on whether the workload can be expressed through safe partitioned or isolated state rather than arbitrary shared mutation.

This table is not normative and does not promise performance.

Its purpose is adversarial: expose where the model is elegant, where it is merely adequate, and where a deliberate native boundary may remain appropriate.

---

## 67. Not every workload must be native Protos all the way down

A language becomes less coherent if it absorbs every machine-level mechanism merely to claim universal coverage.

A Protos application may call a highly optimized native numeric kernel.

A database may implement its buffer manager or lock-free internals below the Protos semantic boundary while expressing planning, transactions, distribution, APIs, and orchestration in Protos.

A game may use Protos for world logic and a native SIMD kernel for a hot physical calculation.

This is not failure.

It is layered engineering.

The question is whether the boundary is explicit, safe, composable, and semantically well defined.

---

## 68. Research precedent without inheriting its assumptions

Other languages and runtimes contain decades of valuable evidence.

They reveal:

- successful abstractions;
- failed abstractions;
- performance cliffs;
- usability problems;
- safety techniques;
- implementation strategies.

But a precedent carries the assumptions of the system that created it.

Rust ownership, Pony reference capabilities, Swift isolation regions, Erlang Actors, Smalltalk closures, Java threads, C++ atomics, OpenCilk reducers, and other designs solve problems within different semantic universes.

Protos should learn from them without importing their entire worldview.

The right question is:

> What problem did this mechanism solve, what did it cost, and does Protos already have a simpler way to obtain the same guarantee?

---

## 69. Design for decades, implement for today

A specification should not encode today's hardware topology as tomorrow's semantics.

CPU counts will change.

Memory architectures will change.

Operating systems will change.

Accelerators will change.

Runtime techniques will change.

Deployment environments will change.

Protos should define durable logical abstractions that can exploit new machinery without changing program meaning unnecessarily.

This is another reason to distinguish:

- logical concurrency from threads;
- logical isolation from copying;
- Actor identity from process identity;
- authority from infrastructure placement;
- values from representation.

---

## 70. A feature is not finished when its happy path works

A serious feature must define or deliberately delegate:

- success;
- failure;
- cancellation;
- cleanup;
- lifetime;
- ordering;
- aliasing;
- identity;
- authority;
- reentrancy;
- concurrency;
- resource exhaustion;
- portability;
- implementation freedom.

It must also survive composition with existing features.

This is why Protos design work can be slower before implementation and simpler afterwards.

Ambiguity removed from the specification is ambiguity that every implementation, library, tool, and user does not have to rediscover independently.

---

## 71. The Protos tests

A proposal should be challenged with questions such as:

> Does this make the Protos universe more general, or add another special thing the universe must know about?

> Is this a fundamental rule, or something that should be built inside the existing universe?

> Does the abstraction eliminate more complexity than it introduces?

> Does a program that does not use this capability avoid paying its runtime and conceptual costs?

> Can independent components progress without unnecessary shared state or coordination?

> Does this cross a qualitative zero-to-one threshold?

> Does it preserve implementation freedom?

> Does it remain coherent under failure, cancellation, and scale?

> Can two independent implementers produce compatible observable behavior?

> Can we gain a star without losing stars elsewhere?

No single question is an absolute veto.

Together they form a pressure test for architectural coherence.

---

## 72. The long-term shape

The ambition for Protos can be summarized as a progression of explicit semantic boundaries:

```text
objects
    compose behavior and state

closures
    compose executable behavior

contexts
    provide lexical execution state

Futures / C
    compose abundant cooperative concurrency

P
    adds isolated multicore computation

Actors / A
    add persistent isolated identity and lifecycle

distribution
    extends Actor interaction across failure and locality boundaries

cluster mechanisms
    add coordination only where global or scoped authority is genuinely needed

native boundaries
    provide machine-level capabilities without forcing machine-level reasoning
    into ordinary Protos
```

Each level should earn its existence.

Each level should preserve the useful simplicity of the levels below it.

Each level should expose the semantic difference that made the level necessary.

---

## 73. What Protos should feel like

A successful Protos should feel unsurprising after its small set of principles is understood.

Not because every operation imitates another language.

Not because every difficult problem has been hidden.

But because new behavior follows from rules the programmer already knows.

Objects should behave like objects.

Closures should remain closures.

Isolation should mean isolation.

Explicit suspension should be the place where reentrancy becomes relevant.

Values should not acquire hidden mutable aliases merely because an optimization is possible.

Actors should represent entities, not threads.

Parallelism should represent computation, not identity.

Distribution should introduce distributed concerns where distribution actually begins.

Machine-level concerns should remain behind machine-level boundaries unless the program explicitly chooses to cross them.

The language should reward local reasoning.

---

## 74. The ambition in one page

Protos aims to be a language where:

- everything observable fits a coherent object-oriented universe based on prototypes and delegation;
- ordinary mechanisms are preferred over privileged special cases;
- semantics are designed before syntax and implementation;
- specifications define observable behavior precisely enough for independent implementations;
- implementations retain broad freedom over physical representation and execution strategy;
- simple programs remain genuinely simple;
- capability grows incrementally with problem complexity;
- unused features impose minimal runtime and conceptual cost;
- shared mutable state is minimized;
- independence is preferred over unnecessary coordination;
- concurrency does not automatically mean simultaneous mutation;
- CPU parallelism does not automatically mean Actors;
- Actors do not mean threads;
- distribution does not infect local execution;
- logical isolation does not require naive physical copying;
- safe partitioning can expose multicore performance without opening arbitrary shared mutable memory;
- low-level machine concerns can live behind explicit native boundaries;
- safety and performance reinforce rather than automatically oppose each other;
- major abstractions are tested against unrelated workloads and actively falsified;
- new capability should improve weak areas without degrading strong ones;
- qualitative zero-to-one changes receive architectural scrutiny;
- global language properties are protected from accidental local erosion;
- failure, cancellation, authority, lifetime, and scalability are considered part of design rather than afterthoughts;
- complexity appears at real semantic boundaries instead of leaking everywhere.

The shortest version is:

> **Build a small universe. Keep ordinary things ordinary. Make real boundaries explicit. Pay only for what you use. Pay as you grow. Scale by composition. Preserve independence. Separate logical semantics from physical machinery. Gain capability without taxing what already works.**

And for concurrency:

```text
C
    share state
    share references
    serialize execution

P
    isolate an operation
    separate mutable authority
    pass values/snapshots
    parallelize execution

A
    isolate a persistent entity
    separate mutable identity
    add lifecycle and messaging
    parallelize/distribute entities
```

The objective is not to make every problem trivial.

The objective is to ensure that Protos adds complexity only when the problem itself has become more complex, and that the language remains one coherent universe while it grows.