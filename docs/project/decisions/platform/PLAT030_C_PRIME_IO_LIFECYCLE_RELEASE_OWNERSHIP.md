# PLAT030 — C-prime ownership for asynchronous I/O lifecycle release/close

Status: **RATIFIED — Candidate A′ selected**

Nature: durable non-normative JVM/Truffle/runtime architecture decision

Approved by project owner: **2026-09-12**

GitHub Issue: **#436**

Primary consumer: `PERF006-B` / GitHub #276, initially the remaining `TextWriter.close` C-prime migration and later equivalent owning-close/release paths.

Normative effect: **none**. PLAT030 changes no Protos-visible Future, Task, Actor, Process, I/O, close, cancellation, failure, ordering, ownership, callback or suspension semantics. It selects only the semantically invisible runtime owner of deferred C-prime execution during one shared asynchronous lifecycle release/close phase after ordinary resource operations have drained.

## D112 semantic prerequisite — RESOLVED

D112 / GitHub #438 is ratified as Candidate A′ at specification revision
`0.1.411`.

PLAT030 lifecycle-release-owned C′ may therefore execute required guest release
while the originating Actor remains `TERMINATING` when the lifecycle close was
already committed before termination cutover. This execution is termination
cleanup of the existing lifecycle, not a new ordinary Actor turn or Task.
`TERMINATED` remains a hard boundary: no lifecycle guest callback may execute
after it.

Backend-only residual release that requires no guest execution does not by itself
keep the Actor alive. D112 adds no force-kill/timeout policy and does not change
PLAT030's private release-record representation.

## Decision

Select **Candidate A′ — lifecycle-release-owned private C-prime execution record + Actor-domain scheduling; close Futures remain outcome followers only**.

One logical lifecycle close has one semantically invisible release execution record whenever suspendible guest release work is required.

The lifecycle remains the authority for:

- `OPEN` / `CLOSING` / terminal closed state;
- admission and close cutover;
- draining ordinary operations before release;
- one shared logical close outcome;
- all repeated-close Future followers.

The release execution record owns only the resumable release control required after release begins:

- the release program counter represented by C-prime state;
- a retained `ContinuationResult` only while genuinely suspended;
- the exact readiness/dependency needed to resume;
- the primary Error/failure state required by the already-defined release contract;
- exact Actor/Context affinity;
- the completion callback back into the lifecycle.

The owning `ActorExecutionDomain` is the sole scheduler/re-entry authority for ready release segments.

Close Futures remain ordinary result observers of the one lifecycle outcome. No close Future becomes the scheduler, the continuation owner, a Task, or the semantic identity of the release.

Conceptually:

```text
ProtosIoLifecycle
    owns close cutover / operation drain / one shared close outcome / N followers
            |
            | release becomes eligible
            v
private adapter/resource ReleaseExecution
    owns release C-prime PC / suspension / dependency / primary error / affinity
            |
            | readiness publication only
            v
ActorExecutionDomain
    schedules/resumes one Actor-local release segment
            |
            v
release C-prime
    final encoder/resource work
    -> ordinary guest write/flush/close callback as required
    -> await lower Future
    -> resume
    -> preserve primary-error precedence
    -> ReleaseCompletion
            |
            v
ProtosIoLifecycle
    settles all close-Future followers with the one shared outcome
```

## Relationship to PLAT028 and PLAT029

PLAT028 remains authoritative: if release control invokes arbitrary ordinary guest behavior and must continue afterwards, that control belongs in C-prime rather than in a resumable Java/native state machine.

PLAT029 remains authoritative for ordinary non-task-backed asynchronous I/O operations: an ordinary `ProtosIoOperation` owns its private C-prime state while its result Future remains outcome/observation only.

PLAT030 covers the distinct lifetime that begins only after close cutover has drained ordinary operations and there is no ordinary per-operation result Future to own the remaining release work.

Therefore:

```text
ordinary async I/O operation
    -> PLAT029 operation-owned C-prime

single shared lifecycle release
    -> PLAT030 release-phase-owned C-prime

Future
    -> outcome/observation only in both cases

ActorExecutionDomain
    -> scheduler/re-entry authority in both cases
```

PLAT030 does not reinterpret the release phase as an ordinary operation merely to reuse a class name.

## Required separation of roles

### Lifecycle

The lifecycle owns semantic resource lifecycle state and follower publication.

It may coordinate the existence/start/completion of the private release execution record, but it must not become a generic continuation interpreter or generic Actor job scheduler.

### Release execution record

The release execution record is private runtime machinery scoped to exactly one logical release.

It may own only:

- C-prime resumable release state;
- exact dependency/readiness registration;
- the minimal release phase/PC needed to sequence already-defined release work;
- primary release Error state required by existing precedence;
- Actor/Context affinity;
- one completion edge back to the lifecycle.

It has:

- no result Future of its own;
- no guest-visible identity;
- no parent/child structured ownership;
- no `detach`;
- no independent Task lifecycle;
- no generic arbitrary-job submission API;
- no second cancellation/unwind model;
- no physical carrier ownership.

### Close Futures

Repeated `close()` Futures are followers of one shared lifecycle result.

They may retain their already-defined ordinary Future identity and observation/cancellation behavior, but they do not own:

- release execution;
- release continuation state;
- scheduling;
- structured ownership;
- a physical carrier.

The first close Future has no special execution status merely because it was created first.

### Actor execution domain

The owning Actor domain is the only authority that may resume release C-prime guest execution.

A lower Future, backend callback or completion thread may make release work ready and enqueue/wake the Actor domain. It must never directly run arbitrary guest release code merely because readiness was observed.

## Minimal release-driver invariant

The release driver must remain **minimal, private and lifecycle-specific**.

It may provide only the machinery required to:

1. start one release execution after the lifecycle says ordinary operations have drained;
2. retain the C-prime continuation at a genuine suspension;
3. associate it with the exact dependency that can make it runnable;
4. close ready-before-retain / retain-before-ready lost-wakeup races;
5. resume one Actor-local C-prime segment;
6. maintain already-defined primary-failure precedence across ordered release steps;
7. report the final release outcome exactly once to the lifecycle;
8. release continuation/dependency state exactly once at terminality.

If implementation requires any of the following, the affected slice must stop and reopen platform architecture:

- parent/child structured ownership;
- `detach`;
- a result Future for the release record itself;
- a guest-visible execution identity;
- an independently observable Task-like lifecycle;
- arbitrary generic Actor jobs unrelated to one release;
- a new cancellation/unwind model;
- a generic second host continuation stack;
- physical thread/fiber ownership per release.

This guard is part of Candidate A′.

## Why a synthetic ordinary ProtosIoOperation is not selected

A release starts only after the lifecycle's ordinary operation set has drained.

Manufacturing a normal `ProtosIoOperation` for release would either:

- re-enter the same ordinary operation set and risk blocking the release's own eligibility condition; or
- require special cases so the object is not really an ordinary operation.

Once enough exceptions are added — no ordinary result Future, no normal admission, different membership/terminality — the object is a release execution record with the wrong semantic name.

Implementation may reuse private low-level continuation/scheduling mechanics from PLAT029, but must not counterfeit ordinary operation identity.

## Why a close Future is not the owner

A resource may have multiple `close()` callers observing one logical close.

Choosing the first Future as the continuation owner would make arbitrary observer creation order determine execution ownership:

```text
close() -> Future A
close() -> Future B
close() -> Future C

one release, but Future A accidentally owns execution
```

That directly conflicts with PLAT029's outcome-vs-execution separation and makes repeated-close behavior unnecessarily asymmetric.

Therefore all close Futures remain equal followers of lifecycle outcome.

## Why a generic Actor job/microtask is not selected

GraalJS and actor runtimes provide strong evidence that explicit queued jobs can scale well, but Protos does not yet need a new generic job identity.

The semantic owner is already known: one resource lifecycle release.

PLAT030 therefore chooses the narrower release execution record. If future unrelated subsystems independently need the same scheduling abstraction, a later platform decision may factor shared private mechanics without retroactively turning release into a generic job.

## Error, control, unwind and failure precedence

PLAT021 remains authoritative for C-prime Error/handler, ensure, non-local return, cancellation and structured unwind.

PLAT030 introduces no parallel host exception protocol.

Existing I/O release semantics remain authoritative, including:

- release starts only after the lifecycle allows it;
- required release steps still occur in their already-defined order;
- a pre-existing primary output/release Error remains primary when later mandatory close/release work also fails, where the existing contract says so;
- lower-Future failure identity is preserved or translated only at the already-defined I/O boundary;
- release completion is reported to the lifecycle exactly once;
- repeated close followers all observe the same logical lifecycle outcome.

PLAT030 does not grant close-Future cancellation authority over the shared lifecycle merely because a follower exists.

## Scheduling and readiness

Readiness and guest execution remain separate:

```text
backend / lower Future
    -> publish readiness
    -> enqueue/wake owning ActorExecutionDomain
    -> return

ActorExecutionDomain
    -> select ready release record
    -> resume one C-prime segment
```

No backend completion thread becomes an accidental guest carrier.

PLAT030 adds no guest-visible microtask ordering guarantee. Any shared internal FIFO policy remains implementation detail unless separately standardized.

## Cost and scalability

Candidate A′ is pay-for-state, not pay-for-carrier.

For one logical resource close:

- repeated `close()` calls add follower Futures but do not add release executions;
- at most one private release execution exists;
- C-prime continuation state exists only at genuine suspension;
- no platform thread is parked per close;
- no hidden Task is allocated merely for continuation custody;
- no replay tape or global continuation registry is introduced.

Thus:

```text
100 close() followers on one resource
    -> 100 follower Futures
    -> 1 lifecycle close
    -> at most 1 release execution

10,000 simultaneously closing resources
    -> O(resources with active release state)
    -> not O(waiting physical threads)
```

Physical carrier use remains bounded by the existing Actor runtime architecture.

## Context and isolation

Release execution is bound permanently to the exact resource's owning Actor/Context authority.

Continuation state must not migrate between independent Actors or Truffle `Context` instances.

Host readiness may be observed externally, but guest release execution must resume only through the owning Actor execution domain.

No process-global release registry or current-Actor singleton is introduced.

## Optimizer, AOT and backend portability

C-prime remains the guest continuation representation under the Truffle Bytecode DSL. Java/native objects retain only bounded lifecycle/readiness metadata.

This preserves optimizer/AOT friendliness by avoiding arbitrary Java frame retention around guest callbacks.

`ContinuationResult` is the current Truffle representation, not the durable PLAT030 contract. A future non-Truffle backend may replace it with an equivalent resumable execution record while preserving:

```text
lifecycle release owns execution state
Actor domain schedules it
Future remains outcome-only
```

## Comparative evidence

The project-owner approval followed an exhaustive review of the current Truffle implementation catalogue plus relevant historical/experimental implementations and secondary non-Truffle evidence.

### Apple Pkl

Pkl is the closest lifecycle precedent.

Its external-reader/process/evaluator infrastructure gives process/evaluator-manager objects explicit ownership of close state, transports, outstanding requests/handler work and release ordering. Request-specific Futures remain request outcomes; they do not become the execution owner of one shared close.

Useful PLAT030 lesson: **the lifecycle/resource manager owns closure; requests/Futures remain separate**.

Focused scores as precedent: future endurance **5/5**, scalability **4.7/5**, Protos fit **5/5**.

PLAT030 adopts the ownership separation, not Pkl's blocking process-wait implementation details.

### GraalJS

`JSAgent` owns Promise-job, async-wait and finalization queues. Promises do not become the scheduler or saved execution stack, and readiness can wake the Agent without running arbitrary guest code inline.

Useful lesson: **outcome object != execution owner; execution resumes in the owning execution domain**.

Scores: future **5/5**, scalability **5/5**, Protos fit **4.7/5**.

PLAT030 does not adopt a generic JS-style microtask identity because one lifecycle release already supplies the exact owner.

### SOMns

Promise handlers are scheduled on the appropriate Actor rather than running on the resolving thread, reinforcing Actor-local execution authority and outcome/execution separation.

Scores: future **4.5/5**, scalability **5/5**, Protos fit **4.8/5**.

### GraalPy

GraalPy represents finalization/async actions explicitly and can schedule/poll them independently of the resources whose results they affect.

Useful lesson: deferred cleanup is explicit runtime work rather than an arbitrary retained caller stack.

Scores: future **4.5/5**, scalability **4.2/5**, Protos fit **3.8/5**.

### grCUDA

Explicit computation/dependency records and scheduler ownership reinforce the scalable split between logical deferred work and physical carriers.

Scores: future **4.5/5**, scalability **5/5**, Protos fit **4.1/5**.

### Yona

Promise/non-blocking runtime machinery provides additional evidence for explicit deferred-work state and runtime scheduling without exposing physical carrier identity.

Scores: future **3.8/5**, scalability **4.6/5**, Protos fit **4.4/5**.

### TruffleSqueak

Explicit process/context execution state reinforces the principle that resumable language work is represented as runtime/guest state rather than arbitrary Java caller frames.

Scores: future **4.3/5**, scalability **4.0/5**, Protos fit **4.2/5**.

### Espresso

Espresso's stackful continuation machinery can preserve Java guest stacks, but lock/native/nesting restrictions make it negative evidence for making arbitrary Java/host stacks the Protos release continuation architecture.

Scores as a PLAT030 architecture to copy: future **4.3/5**, scalability **3.2/5**, Protos fit **2.0/5**.

### TruffleRuby

Thread-backed Fiber costs and JVM `callcc` mismatch are negative evidence against a physical fiber/carrier per pending release.

Scores for that pattern: future **3.2/5**, scalability **2.0/5**, Protos fit **1.8/5**.

### PorcE / Orc

Explicit future-reader/task records and runtime work scheduling provide good evidence for materialized pending work, but using Task-like continuation ownership for PLAT030 would violate the already-selected no-hidden-Task boundary.

Scores as conceptual evidence: future **3.8/5**, scalability **4.8/5**, Protos fit **4.3/5**.

### Additional screened implementations

Enso, Sulong, GraalWasm, FastR, TruffleSOM, SimpleLanguage, TRegex, streamblocks/CAL and the remaining historical/experimental catalogue were screened. They add supporting or negative evidence but no stronger maintained precedent than Pkl/GraalJS/SOMns for assigning one shared lifecycle release to one private execution record while keeping outcome handles separate.

## Candidate comparison

Scores are 1–5 and are decision aids, not normative arithmetic.

| Candidate | Correctness | Future endurance | Scalability | Protos fit | Cancellation/unwind | Resource cost | Optimizer/AOT | Actor isolation | Portability | Migration | Total /50 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **A′ — lifecycle-release-owned private C-prime record** | **5.0** | **5.0** | **5.0** | **5.0** | **4.8** | **4.8** | **5.0** | **5.0** | **5.0** | 4.5 | **49.1** |
| B — synthetic ordinary `ProtosIoOperation` | 3.8 | 3.7 | 4.5 | 3.0 | 3.0 | 4.5 | 4.8 | 5.0 | 4.0 | **5.0** | 41.3 |
| C — first close Future owns C-prime | 2.5 | 2.5 | 4.5 | 1.5 | 2.0 | 4.7 | 4.5 | 4.2 | 3.5 | 3.5 | 33.4 |
| D — generic Actor job/microtask | 4.5 | 4.8 | **5.0** | 4.0 | 4.0 | 4.8 | 4.8 | **5.0** | 4.7 | 3.5 | 45.1 |
| E — hidden Task | 2.5 | 3.0 | 3.5 | 1.0 | 2.0 | 3.0 | 4.5 | 4.5 | 3.0 | 4.0 | 31.0 |
| F — Java/native release state machine + C-prime fragments | 3.0 | 2.5 | 4.5 | 1.0 | 2.5 | 4.5 | 3.0 | 4.0 | 2.5 | **5.0** | 32.5 |
| G — physical stack/carrier continuation | 3.5 | 3.0 | 2.0 | 1.5 | 3.0 | 1.5 | 3.0 | 3.5 | 1.0 | 2.5 | 24.5 |

Focused owner-requested scores for A′:

- future endurance: **10/10**;
- scalability: **10/10**;
- Protos philosophy: **10/10**;
- GITHUB010 total: **49.1/50**.

## Strongest argument against A′

A careless implementation could create a third Task-like runtime species:

```text
ProtosTask
ProtosIoOperation
ReleaseExecution
...
```

and gradually duplicate scheduling, cancellation and lifecycle machinery.

The selected guard is strict:

- release execution has no Future of its own;
- no parent/child ownership;
- no detach;
- no guest-visible identity;
- no general cancellation model;
- no arbitrary job submission;
- no independent scheduler.

Shared low-level mechanics with PLAT029 may later be factored only if they remain limited to continuation custody/readiness/resume and do not create a generic second Task abstraction.

If that boundary cannot be preserved, implementation must stop and reopen PLAT.

## Regret scenario and escape path

The plausible regret case is discovering several unrelated non-Task subsystems that all require the exact same private resumable execution-record mechanics.

A′ deliberately leaves that evolution open.

A future PLAT decision may factor a private common execution-custody substrate implemented by ordinary operations and lifecycle release records, provided semantic ownership remains with the real domain object and no new guest-visible Task/fiber/job abstraction appears.

That refactoring does not require changing Future, close, Actor or I/O semantics.

## Rejected architectures

PLAT030 rejects as the durable architecture:

- making an arbitrary close-Future follower the continuation owner;
- manufacturing a semantically ordinary `ProtosIoOperation` solely to host release C-prime;
- creating a hidden Task or `Future.then` Task;
- introducing a generic Actor microtask/job identity solely for release;
- keeping release sequencing in a resumable Java/native state machine around guest callbacks;
- retaining or blocking a physical Java/OS/Loom/Fiber carrier per release;
- arbitrary Java/JVM stack capture;
- replay/blocking islands;
- backend-thread direct guest re-entry;
- turning `ProtosIoLifecycle` into a generic continuation interpreter.

## Implementation release boundary

PLAT030 ratification releases bounded implementation of lifecycle-release C-prime under `PERF006-B`.

The first consumer is `TextWriter.close`:

1. preserve current close cutover and ordinary-operation drain;
2. start exactly one private writer release execution;
3. perform encoder finalization;
4. if final bytes exist, execute ordinary guest `target.write(finalBytes)` in C-prime and await its returned Future;
5. preserve primary-failure precedence;
6. when owning, execute ordinary guest `target.close()` in the same release C-prime control and await its returned Future;
7. report one final release outcome to the lifecycle;
8. settle every close follower through the existing lifecycle authority.

Equivalent TextReader/buffered-I/O owning-close paths may use the same approved ownership pattern after re-auditing their existing semantics.

Every implementation slice must re-audit execution-time `main`.

A new observable semantic choice still requires Dxxx. Any need for Task-like/generic-job release execution requires another PLAT decision.

## Ratified invariants

PLAT030 is satisfied only if:

1. C-prime remains the sole guest continuation architecture.
2. One logical lifecycle release owns at most one private release execution record.
3. Close Futures remain outcome followers only.
4. The first close Future receives no special continuation/scheduler ownership.
5. The lifecycle remains close-cutover/drain/shared-outcome/follower authority.
6. Adapter/resource release execution owns only minimal resumable release state and required primary-error bookkeeping.
7. Ready release work resumes only through the exact owning Actor execution domain.
8. Backend/lower-Future completion never directly re-enters arbitrary guest release code.
9. No synthetic ordinary `ProtosIoOperation` is created merely to host release execution.
10. No hidden Task, parent/child topology, detach, guest-visible execution identity or independent Task-like lifecycle is introduced.
11. No parked physical carrier, Java-stack continuation, replay island or global release-continuation registry is introduced.
12. Existing close ordering, primary-error precedence, follower behavior, Actor/Context isolation and PLAT014/019/021/028/029 authority remain intact.
13. Any need to violate invariant 10 is a new platform decision gate.
