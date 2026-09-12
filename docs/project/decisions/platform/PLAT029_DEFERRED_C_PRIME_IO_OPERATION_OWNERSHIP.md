# PLAT029 — Deferred C-prime ownership for non-task-backed asynchronous I/O operations

Status: **RATIFIED — Candidate B′ selected**

Nature: durable non-normative JVM/Truffle/runtime architecture decision

Approved by project owner: **2026-09-12**

GitHub Issue: **#433**

Primary consumer: `PERF006-B` / GitHub #276, specifically the remaining PLAT028 migration of `TextReader`, `TextWriter`, buffered byte/text I/O and equivalent deferred non-task-backed asynchronous operation paths.

Normative effect: **none**. PLAT029 changes no Protos-visible Future, Task, Actor, Process, I/O, cancellation, failure, ordering, lifecycle, ownership, callback or suspension semantics. It selects only which semantically invisible runtime entity owns deferred C-prime execution after a non-task-backed asynchronous operation has already returned its result Future.

## Decision

Select **Candidate B′ — operation-owned C-prime execution record + Actor-domain scheduling; result Future remains outcome/observation only**.

A non-task-backed asynchronous operation that must later re-enter ordinary guest code, may suspend there, and must continue operation-owned work afterwards owns that deferred resumable execution state as part of the operation.

The owning Actor execution domain decides where and when a ready operation segment is resumed.

The operation's result Future remains a Future. It owns its identity, terminal outcome, observation behavior and producer-directed cancellation request. It does **not** become the scheduler, a Task, a hidden structured-concurrency node, or the semantic owner of the operation's continuation.

Conceptually:

```text
non-task asynchronous operation
    owns lifecycle / commit / cancellation state
    owns deferred C-prime resumable state when present
    |
    +-- result Future
    |      owns identity + outcome + observation
    |      cancellation request -> producer operation
    |
    +-- readiness/dependency
           -> mark operation runnable
           -> owning ActorExecutionDomain
           -> resume next C-prime segment
```

The backend or lower-Future completion thread may make the operation runnable. It must not execute arbitrary Protos guest code merely because readiness was detected there.

## Relationship to PLAT028

PLAT028 remains authoritative: control that performs an ordinary guest callback and has more work afterwards must be C-prime-resumable, while Java/native remains a guest-non-reentrant leaf.

PLAT029 resolves the additional lifetime question that appears when the original operation invocation has already returned before that callback is reached. The C-prime continuation still owns resumable guest/control state, but its semantically invisible runtime custodian is the asynchronous operation rather than a `ProtosTask` or its result Future.

PLAT029 therefore extends PLAT028 without weakening it:

```text
PLAT028:
    callback-owning control -> C-prime

PLAT029:
    deferred non-task operation -> owns that C-prime state
    ActorExecutionDomain       -> schedules/resumes it
    result Future              -> outcome/observation only
```

## Required separation of roles

### Operation

The operation may own:

- lifecycle phase;
- semantic commitment state;
- producer-side cancellation state;
- dependencies/readiness registrations;
- private C-prime continuation state required to resume operation-owned control;
- private bookkeeping needed to complete, fail or cancel the result Future.

The operation is not thereby a language-visible continuation object.

### Result Future

The result Future owns only the already-ratified Future responsibilities, including:

- fresh Future identity where required;
- pending/resolved/failed/cancelled outcome;
- observation;
- adoption where independently specified;
- producer cancellation signalling.

The Future must not acquire Task parentage, detach semantics, scheduler identity or continuation-stack authority merely because its producer is a deferred I/O operation.

### Actor execution domain

The owning `ActorExecutionDomain` is the re-entry authority for ready operation work. It must preserve the same Actor/Context isolation as the operation origin.

Readiness notification and guest execution are separate actions:

```text
dependency/backend thread
    -> readiness publication only
    -> Actor domain schedules operation
    -> Actor-local execution resumes C-prime
```

No ordinary guest code is run directly on an arbitrary backend completion thread.

## Minimal non-Task driver invariant

The internal non-Task C-prime driver must remain **minimal and private**.

It may provide only the machinery required to:

- retain a suspended C-prime result for the operation;
- associate it with the exact dependency that can make it runnable;
- publish readiness without a lost-wakeup race;
- resume one Actor-local C-prime segment;
- propagate normal completion, Error/control and cancellation into the operation's already-ratified lifecycle;
- release retained continuation/dependency state at terminality.

It must not silently grow independent Task semantics.

If implementation requires any of the following, the affected slice must stop and reopen platform architecture before proceeding:

- parent/child structured ownership;
- `detach`;
- a new guest-visible execution identity;
- an independently observable Task-like lifecycle;
- a second general-purpose cancellation/unwind model;
- a general scheduler abstraction competing with `ActorExecutionDomain`;
- a generic second host continuation stack.

This guard is part of Candidate B′, not an implementation suggestion.

## Existing Protos architecture fit

The current runtime already separates the relevant concepts.

`ProtosIoOperation` is producer-side asynchronous-operation state: commitment is not Future state, cancellation is producer-directed, and operation terminality is tracked independently.

`ProtosActorExecutionDomain` independently tracks:

- live Tasks;
- Actor-local I/O operations;
- pending non-task Futures.

Candidate B′ preserves and completes that separation instead of collapsing these categories.

The implementation may extend or refactor these private classes, but their Java class names are not normative PLAT029 API.

## Cancellation and terminality

Existing normative I/O commitment/cancellation semantics remain unchanged.

In particular:

- pre-commit cancellation may prevent further operation progress exactly where existing I/O authority says it may;
- post-commit cancellation must not rewrite an outcome that existing semantics require to continue;
- close cutover rules remain authoritative;
- a suspended deferred C-prime segment must release its continuation/dependency state exactly once on terminal completion/cancellation/failure;
- Actor termination continues to address Tasks, I/O operations and non-task Futures according to their distinct existing contracts.

PLAT029 does not convert operation cancellation into Task cancellation. Implementation may reuse internal mechanics only when doing so preserves those different semantics.

## Error, control and unwind

PLAT021 remains authoritative for C-prime Error/handler, ensure, non-local return, cancellation and structured unwind.

Operation-owned C-prime must not invent a parallel host exception/continuation protocol. Any control transfer that is legal across the affected operation boundary must be represented and unwound under existing C-prime rules, then translated into the operation's existing completion/failure/cancellation contract only at the already-defined runtime boundary.

If an affected I/O contract does not permit a particular guest control transfer to escape as such, PLAT029 does not change that semantic fact.

## Scheduling and fairness

PLAT029 does not define a new guest-visible microtask queue or ordering model.

The current Actor-domain FIFO runnable policy remains an implementation policy unless separately made normative. Operation scheduling may share or compose with that Actor-local scheduling substrate, provided:

- it does not create a hidden Task;
- it does not add a new guest-visible ordering guarantee;
- ready operation work cannot execute in another Actor/Context;
- a backend completion thread cannot become an accidental guest carrier.

## Cost and scalability

The selected architecture is pay-for-state rather than pay-for-carrier.

A queued operation that has not entered suspendible C-prime control need not allocate a `ContinuationResult`.

An operation that reaches a genuine C-prime suspension retains only the interpreter continuation/dependency state needed for later resumption. It must not retain:

- a parked platform thread;
- an arbitrary Java stack;
- a hidden Task solely for continuation storage;
- a replay tape;
- a global continuation registry.

Physical carrier usage therefore remains bounded by the existing Actor runtime architecture rather than by the number of pending I/O operations.

## Context and isolation

Operation-owned deferred C-prime is Actor-local and Context-local.

Continuation state must not migrate between independent `Context` instances or Actors. Host readiness may be detected externally, but guest resumption must re-enter through the owning Actor execution domain with the exact runtime/Prelude/module authority already belonging to the operation.

No global current-Actor or process-global continuation registry is introduced.

## Optimizer and AOT boundary

Candidate B′ keeps resumable language control in C-prime, where Truffle Bytecode DSL can reason about interpreter state, and keeps host readiness/lifecycle state in bounded runtime objects.

This is preferable to retaining arbitrary Java frames around guest calls because it:

- keeps hot guest control in the generated interpreter;
- avoids physical-stack dependence;
- avoids per-operation parked carriers;
- permits operation/runtime bookkeeping to remain behind explicit Truffle boundaries when appropriate;
- keeps the durable architecture portable to a future non-Truffle backend that supplies an equivalent resumable execution record.

`ContinuationResult` is the current Truffle representation, not the language-level contract.

## Comparative evidence

The project-owner approval followed an exhaustive review of the current Truffle language catalogue and relevant historical/experimental implementations, including Apple Pkl, plus mature non-Truffle async runtimes.

### Truffle Bytecode DSL
Generated interpreters own resumable state through `ContinuationResult`; arbitrary surrounding Java frames are not the captured guest continuation. Scores: future endurance **5/5**, scalability **5/5**, Protos fit **5/5**.

### GraalJS
Promises and execution scheduling are separated. Promise reaction work is queued through the owning JS Agent rather than making the Promise the saved guest stack or executing arbitrary guest work on the settlement thread. Scores: **5/5**, **5/5**, **4.7/5**.

### Apple Pkl
Pkl external-reader architecture uses explicit evaluator/request/response/process state and request-specific completion Futures instead of preserving a synchronous host evaluator stack across external work. Scores: **4.8/5**, **4.5/5**, **4.9/5**.

### SOMns
Promise resolution schedules explicit Actor work; Promise/result state and Actor execution are separate. Scores: **4.2/5**, **5/5**, **4.8/5**.

### GraalPy
Generators/coroutines retain explicit Python/runtime execution state; asynchronous actions are separately driven. Scores: **4.8/5**, **4.5/5**, **4.1/5**.

### TruffleSqueak
Smalltalk process/context progress is explicit guest/runtime state rather than arbitrary Java caller-stack identity. Scores: **4.5/5**, **3.8/5**, **4.2/5**.

### Espresso
Whole-Java-stack continuation machinery is negative evidence for Protos because native/lock/nesting restrictions make arbitrary host-stack ownership brittle. Scores as architecture to copy: **4.5/5**, **3.8/5**, **2.5/5**.

### TruffleRuby
Thread-backed Fiber costs and `callcc`/JVM mismatch are negative evidence against one carrier/fiber per pending operation. Scores for that model: **4.2/5**, **2/5**, **2.3/5**.

### PorcE / Orc
Futures retain explicit blocked readers and notify them on resolution, demonstrating explicit pending-work representation rather than caller-stack retention. Scores: **3.5/5**, **4.8/5**, **4.3/5**.

### Additional screened Truffle systems
Enso, Yona, streamblocks/CAL, Sulong, GraalWasm, grCUDA, FastR, TruffleSOM, TRegex and the remaining historical/experimental catalogue were screened. They add secondary or negative evidence but no stronger maintained precedent for coupling Future identity to continuation ownership.

## Non-Truffle evidence

Mature systems reinforce the same separation:

- Kotlin transforms suspendible caller control into explicit coroutine state;
- Rust async represents post-await control as an explicit state machine driven by scheduler/waker;
- Swift stores resumable async contexts and bridges callbacks back into async execution explicitly;
- Java CompletionStage materializes post-completion work rather than retaining the synchronous caller;
- BEAM keeps process progress scheduler/runtime-owned;
- Go demonstrates cheap runtime-owned logical stacks but does not justify JVM-host-stack ownership for Protos.

## Candidate comparison

Scores are 1–5 and are decision aids, not normative arithmetic.

| Candidate | Correctness | Future endurance | Scalability | Protos fit | Cancellation/unwind | Resource cost | Optimizer/AOT | Actor isolation | Portability | Migration | Mean |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| A — Future owns C-prime | 4.0 | 3.5 | 4.5 | 2.5 | 3.5 | 4.5 | 4.5 | 4.0 | 4.0 | 3.0 | 3.80 |
| **B′ — Operation owns C-prime, Actor schedules** | **5.0** | **5.0** | **5.0** | **5.0** | **4.8** | **4.7** | **5.0** | **5.0** | **5.0** | **4.5** | **4.90** |
| C — Actor job/microtask object | 4.7 | 4.8 | 5.0 | 4.3 | 4.5 | 4.8 | 4.8 | 5.0 | 4.7 | 4.0 | 4.66 |
| D — generic non-Task C-prime fiber | 4.5 | 5.0 | 4.8 | 3.6 | 4.5 | 4.4 | 4.8 | 4.7 | 4.5 | 3.5 | 4.43 |
| E — hidden Task / `Future.then` | 2.0 | 2.5 | 3.5 | 1.0 | 2.0 | 3.0 | 4.2 | 4.5 | 3.5 | 2.5 | 2.87 |
| F — Java state machine owns suspendible guest control | 3.0 | 2.5 | 4.5 | 1.0 | 2.0 | 4.5 | 3.0 | 4.0 | 2.5 | 4.0 | 3.10 |
| G — physical stack / carrier | 3.5 | 2.5 | 2.0 | 1.5 | 3.0 | 2.0 | 3.0 | 2.5 | 1.0 | 2.0 | 2.30 |
| H — replay/blocking island | 2.5 | 1.5 | 2.0 | 1.0 | 2.5 | 1.5 | 2.0 | 3.0 | 1.5 | 2.5 | 2.00 |

## Strongest argument against B′

The current Actor runnable queue is Task-oriented. A careless implementation could duplicate Task machinery inside `ProtosIoOperation` and create a second Task under another name.

That risk is real. The ratified answer is to constrain the non-Task driver to the smallest operation-specific execution custody needed by existing I/O semantics. Task-like ownership features are an explicit stop condition requiring another platform decision.

## Rejected architectures

PLAT029 rejects as the durable architecture:

- result Future as continuation/scheduler owner;
- hidden Task or hidden standard `Future.then` continuation Task;
- generic Actor microtask/job as a new primary operation identity;
- generic non-Task fiber introduced prematurely;
- Java state machine as the owner of suspendible guest control;
- one parked carrier/thread/fiber per operation;
- arbitrary Java/JVM stack capture;
- replay/blocking islands;
- backend-thread direct guest re-entry;
- a generic second host continuation stack.

## Future evolution

Candidate B′ preserves these future paths without selecting them now:

- a non-Truffle backend may replace `ContinuationResult` with an equivalent resumable execution record;
- repeated evidence from unrelated non-Task subsystems may later justify factoring a generic private execution-record abstraction;
- coarse external/distributed service boundaries may use explicit Pkl-style request/response;
- Actor/remote boundaries may use SOMns-style message materialization;
- physical carrier strategies may evolve independently under existing Actor carrier decisions.

## Implementation release boundary

PLAT029 ratification releases bounded implementation under `PERF006-B` in this order:

1. minimal operation-owned C-prime execution custody + Actor-domain runnable/resume seam;
2. `TextWriter` ordinary guest re-entry;
3. `TextReader` repeated ordinary guest re-entry;
4. buffered byte/text adapters and equivalent remaining surfaces;
5. deferred full `PERF006-B` validation and replay-retirement audit.

Every implementation slice must re-audit execution-time `main`.

If implementation exposes new observable semantics or a new durable architecture, it must stop and open the next Dxxx/PLATxxx before proceeding.

## Ratified invariants

PLAT029 is satisfied only if:

1. C-prime remains the only guest continuation architecture.
2. A deferred non-task-backed asynchronous operation owns its private suspended C-prime state.
3. The result Future remains outcome/observation/cancellation-signalling state only.
4. Readiness never directly re-enters arbitrary guest code on an external/backend completion thread.
5. Guest resumption executes through the exact owning Actor execution domain.
6. No hidden Task or `Future.then` continuation Task is introduced solely to preserve operation control.
7. No parked physical carrier, Java-stack continuation, replay island or global continuation registry is introduced.
8. Existing I/O commitment, close-cutover, cancellation and terminality semantics remain unchanged.
9. The non-Task driver remains private/minimal and acquires no Task ownership/detach/identity semantics.
10. Any need to violate invariant 9 is a new platform decision gate.
11. No public Continuation, async/await syntax or Future subtype is introduced.
12. Context/Actor isolation and existing PLAT014/019/021/027/028 authority remain intact.
