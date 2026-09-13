# PLAT032 — non-canonical standard-wrapper C-prime orchestration boundary

Status: **RATIFIED — Candidate A selected**

Nature: durable non-normative JVM/Truffle/runtime architecture decision

Approved by project owner: **2026-09-13**

GitHub Issue: **#478**

Primary consumer: `PERF006-B` / GitHub #276, specifically the replay-retirement boundary exposed by `PERF006-B6B-F2B1`.

Normative effect: **none**. PLAT032 changes no Protos lookup, delegation, `Object.call`, method identity, receiver, `methodHome`, alias/copy behavior, Error, cancellation, Future, Task, Actor, ordering, suspension or tooling semantics. It selects only the implementation topology used when an already-selected ordinary standard/native wrapper must invoke suspendible guest behavior and continue afterward.

## Decision

Select **Candidate A — preserve the ordinary selected wrapper logically, move suspendible sub-call orchestration into bounded behavior-specific C-prime control**.

For an ordinary selected method whose implementation is a known standard/native wrapper, including a copied, aliased or otherwise non-canonical copy of that standard implementation:

1. ordinary lookup remains authoritative;
2. the exact selected method identity remains authoritative;
3. receiver and `methodHome`/owner provenance remain unchanged;
4. the call remains tooling-visible as the ordinary selected method invocation;
5. exact canonical intrinsic/elision rules remain exactly those already ratified by PLAT017;
6. if the selected wrapper must invoke guest code that may suspend and then has more work to perform, the suspendible sub-call plus post-call sequencing is represented by a **specific finite C-prime orchestration carrier/plan for that behavior family**;
7. the Java/native wrapper is not continuation state and must not require replay, reconstruction or retention across guest suspension.

Conceptually:

```text
ordinary lookup
    -> exact selected non-canonical/aliased/copied wrapper
    -> preserve receiver + methodHome + selected identity
    -> behavior-specific C-prime orchestration
         -> native/host leaf mechanics
         -> ordinary guest sub-call
              -> complete, or
              -> suspend / resume in C-prime
         -> post-call control in C-prime
    -> ordinary selected method result
```

not:

```text
ordinary lookup
    -> Java/native wrapper frame
         -> guest sub-call
              -> suspend
         -> replay or reconstruct Java wrapper frame
         -> continue wrapper
```

and not:

```text
non-canonical copy/alias
    -> pretend it was the canonical intrinsic
    -> bypass ordinary selected-method identity
```

## Relationship to PLAT017

PLAT017 remains authoritative for the exact canonical `Object.call` intrinsic.

PLAT032 **does not widen canonical intrinsic eligibility**. A copied, aliased, rebound or otherwise non-canonical copy of that implementation remains an ordinary selected method. Its lookup result, receiver, selected implementation identity and `methodHome` remain semantically and tooling-visible.

PLAT032 only specifies the backend continuation topology after that ordinary selection has already happened.

Therefore:

```text
exact canonical Object.call
    -> PLAT017 intrinsic/elision may apply

copy/alias/non-canonical Object.call implementation
    -> ordinary method selection remains visible
    -> PLAT032 behavior-specific C-prime orchestration may host suspendible sub-call control
```

## Relationship to PLAT019

PLAT019 remains the native semantic suspension bridge into C-prime.

A behavior-specific PLAT032 orchestration may call a native leaf that uses PLAT019, but it must not install a second continuation owner. Native suspension is allowed; native/Java **post-guest continuation ownership** is not.

## Relationship to PLAT028

PLAT032 is a narrow consumption/refinement of PLAT028, not a competing continuation architecture.

PLAT028 already requires:

```text
runtime/native -> suspendible guest -> more work
```

to place the surviving sequencing state in C-prime and keep Java/native guest-non-reentrant with respect to arbitrary suspendible guest callbacks.

PLAT032 adds the dispatch/identity constraint needed for copied/aliased/non-canonical standard wrappers:

- preserve the selected ordinary wrapper logically;
- preserve receiver and `methodHome`;
- preserve tooling-visible invocation identity;
- move only the suspendible orchestration state into C-prime.

## Bounded orchestration, not a generic host continuation ABI

PLAT032 deliberately rejects a generic `native -> guest -> resume native` continuation ABI.

The permitted carrier/plan is:

- finite;
- behavior-family-specific;
- semantically invisible;
- C-prime-owned;
- introduced only where a known standard/runtime behavior requires suspendible guest composition;
- incapable of becoming a general Java continuation stack.

This keeps the number of backend orchestration forms proportional to the finite standard/runtime behavior families rather than to dynamic calls, Tasks, aliases or user objects.

## Alias/copy rule

Backend convenience must never make copying or aliasing a standard method illegal.

If Protos semantics allow the implementation Closure/value to be stored elsewhere and later selected through ordinary lookup, the backend must preserve that behavior. PLAT032 therefore rejects "canonical-only or fail" as a replay-removal shortcut.

The backend may recognize implementation provenance to choose the correct **C-prime orchestration shape**, but such recognition must not rewrite the semantic selected method into the canonical slot or bypass receiver/home provenance.

## Tooling and observability

PLAT032 must not erase the selected ordinary wrapper from debugger/profiler/instrumentation semantics merely because its suspendible interior is lowered to C-prime control.

Where the surrounding tooling contract exposes call identity, source identity, receiver, selected method or home/provenance, the logical ordinary invocation remains the authority. Backend helper roots/carriers are implementation detail and must not become a new user-visible method identity.

## Cancellation, Error and non-local control

PLAT021 and existing Task/Future control remain authoritative.

A PLAT032 orchestration must compose with:

- Error/handler control;
- `ensure`/cleanup;
- cancellation;
- non-local return;
- Future suspension/resume;
- PLAT027 terminal lifecycle.

It may not introduce wrapper-specific replay, exception tunneling or a second unwind stack.

## Scalability invariants

The selected architecture requires:

- no continuation allocation for a non-suspending path merely because an alias/copy exists;
- no per-call generic host-continuation frame stack;
- no hidden Task;
- no extra scheduler;
- no physical carrier per logical suspended wrapper;
- Context-local executable projection under existing PLAT001 rules;
- behavior-specific C-prime plans may be shared/cached under existing Context isolation rules when semantically safe;
- memory growth is bounded by actual live C-prime suspension state, not by replay history.

For many Tasks/Actors/Processes the architecture therefore scales with actual live suspendible work, while preserving future compiler specialization and Bytecode-DSL partial-evaluation locality.

## Comparative implementation audit

The approval followed a focused review of Truffle implementations relevant to dispatch identity, primitive/builtin specialization and async/resumable control.

### Apple Pkl

Pkl virtual method invocation resolves the method from the receiver class first, preserves the selected `ClassMethod` and owner, then invokes its `CallTarget` using cached `DirectCallNode` or `IndirectCallNode`. `Function.apply` receives a narrower intrinsic path only after the implementation recognizes that exact callable form.

The relevant lesson for PLAT032 is **selection first, specialization second**. Pkl does not provide evidence for replacing an ordinarily selected non-canonical wrapper with a canonical method identity merely because the backend can optimize the implementation.

Evidence weight: **HIGH** for dispatch/provenance structure; **PARTIAL** for cooperative suspension because Pkl does not share Protos C-prime semantics.

### TruffleSqueak

TruffleSqueak resolves the selected Smalltalk method and then chooses an execution form: primitive node, method call target, message fallback or object-as-method handling. Primitive failure falls back to the already-selected method semantics rather than changing lookup identity.

This strongly supports keeping semantic lookup/method identity separate from backend primitive/intrinsic specialization.

Evidence weight: **HIGH** for dynamic method/primitive dispatch identity; **PARTIAL** for C-prime suspension.

### TruffleRuby

TruffleRuby retains `InternalMethod` identity and method assumptions through cached/uncached call machinery, with specialized paths such as always-inlined methods remaining subordinate to Ruby method dispatch semantics.

This supports the same selection/specialization split. It does not establish a reason to retain an arbitrary Java wrapper stack across a suspendible guest call.

Evidence weight: **MEDIUM-HIGH**.

### GraalPy

GraalPy separates builtin-method/function dispatch from ordinary Python callable dispatch through explicit cached call nodes and call targets. Builtin specialization is a dispatch implementation choice, not a replacement for Python-level callable identity.

Its coroutine/generator model further supports explicit language/runtime continuation state rather than arbitrary Java caller-frame continuation.

Evidence weight: **HIGH** as combined dispatch + continuation evidence.

### GraalJS

GraalJS specializes calls and builtin behavior after semantic target resolution, while asynchronous continuation is represented through explicit promise/async execution state. It supplies no precedent for a synchronous Java wrapper remaining the continuation around `await`-like suspension.

Evidence weight: **HIGH** as negative evidence against host-frame continuation and supporting evidence for explicit resumable state.

### SOMns

SOMns actor/promise progress is represented explicitly through messages, promise callbacks and runtime-owned state. Delayed work is scheduled from explicit promise/message state rather than depending on the producer's Java call stack.

This is especially relevant to Protos future Actor/distributed evolution: logical pending work should remain explicit and scheduler/runtime owned.

Evidence weight: **HIGH** for scalability and asynchronous-state ownership.

### Espresso

Espresso demonstrates that resumable guest-stack support is a deliberate VM facility with explicit frame constraints; native/host frames are not automatically guest continuation state. Protos has deliberately selected C-prime instead of whole-JVM-stack continuation.

Evidence weight: **HIGH negative evidence** against Candidate B-style generic host continuation.

### Sulong / LLVM and GraalWasm

Both preserve explicit guest/host/import boundaries and provide no maintained precedent for transparently converting an arbitrary surrounding Java wrapper into resumable guest continuation state.

Evidence weight: **supporting/negative**.

### Experimental/historical Truffle systems

SOM-family, PorcE/Orc, Mozart/Oz, streamblocks/CAL and related concurrent/dataflow experiments reinforce explicit transformed/runtime-owned control state. None supplied a stronger maintained precedent for a generic Java-wrapper continuation ABI.

## Candidate comparison

### Candidate A — ordinary wrapper preserved logically; behavior-specific C-prime orchestration

**Selected.**

- Future endurance: **10/10**
- Scalability: **9.5/10**
- Protos philosophy: **10/10**

Strengths:

- preserves delegation/lookup identity;
- composes with PLAT014/019/021/028;
- removes replay without inventing a second continuation machine;
- keeps canonical intrinsic rules narrow;
- optimizer-friendly and Context-cacheable;
- compatible with future Actor/distributed execution;
- scales with live suspendible C-prime state.

### Candidate B — generic native-to-guest-to-native resumable ABI

Rejected.

- Future endurance: **7/10**
- Scalability: **6/10**
- Protos philosophy: **5/10**

It creates a second host continuation language beside C-prime, complicating unwind, debugger/tooling projection, Context isolation, non-JVM evolution and memory accounting.

### Candidate C — prohibit copied/aliased standard wrappers in Task execution

Rejected.

- Future endurance: **5/10**
- Scalability: **10/10**
- Protos philosophy: **2/10**

It simplifies the backend by restricting otherwise ordinary Protos object/delegation behavior. Backend limitations must not redefine the language object model.

### Candidate D — retain evaluator replay only for these wrappers

Rejected.

- Future endurance: **3/10**
- Scalability: **3/10**
- Protos philosophy: **2/10**

It preserves two continuation architectures indefinitely, blocks PERF006 replay retirement and keeps memory/optimizer costs proportional to replay machinery rather than the selected C-prime architecture.

## Ratified invariants

The following are mandatory:

```text
ordinary lookup preserved                         YES
selected method identity preserved                YES
receiver preserved                                YES
methodHome/owner provenance preserved             YES
tooling-visible ordinary wrapper preserved        YES

PLAT017 canonical eligibility widened             NO
generic native->guest->resume ABI                 NO
Java wrapper frame as continuation                NO
legacy evaluator replay fallback                  NO
hidden Task / second scheduler                     NO
alias/copy prohibition                             NO

suspendible nested guest control owner             C-prime
native suspension bridge                           PLAT019
dynamic unwind/control owner                       PLAT021
callback/native-leaf authority                     PLAT028
```

## Consequence for PERF006-B6B-F2B1

The failed F2B1 guard correctly exposed remaining production reachability through a non-canonical/ordinary standard wrapper path.

F2B1 must not be published merely by weakening the `ProtosEvaluatorBridge` static guard.

Before retrying replay-unreachability publication, affected standard wrappers such as non-canonical/copied/aliased `Object.call` implementation paths must be migrated so that:

- the ordinary selected-method semantics remain intact;
- nested suspendible Closure invocation is orchestrated by a PLAT032-specific C-prime carrier/plan;
- no `ProtosEvaluatorBridge` or evaluator replay path remains reachable from production Task execution.

Only after those migrations prove the production replay graph closed may F2B1 resume.

## Deliberately deferred

PLAT032 does not decide:

- which exact Java class names implement each behavior-specific carrier;
- whether one carrier may safely serve multiple standard behavior families;
- Bytecode root naming;
- cache shape beyond existing Context isolation rules;
- debugger presentation details beyond preserving the logical ordinary invocation;
- non-JVM backend representation;
- removal timing for dead replay-only tests/classes.

Those are implementation details unless later evidence exposes a new observable semantic or durable architecture choice.
