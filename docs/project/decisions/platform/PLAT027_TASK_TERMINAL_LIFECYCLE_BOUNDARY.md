# PLAT027 — Task-owned terminal lifecycle composition around C-prime guest execution

Status: **RATIFIED — Candidate A′ selected**

Nature: durable non-normative JVM/Truffle/Task-hosting architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#378**

Primary consumer: `PERF006-B6A6A` under GitHub #276 and later Task-owned runtime operations that must finalize host lifecycle state after one C-prime guest computation truly terminates.

Normative effect: **none**. PLAT027 changes no Protos call, Task, Actor, Future, Process, cancellation, failure, return, module, scheduling or continuation semantics. It selects only how the JVM/runtime implementation retains semantically invisible host finalization work when a Task-owned guest computation may suspend under the already-ratified C-prime backend.

## Decision

Select **Candidate A′ — Task-owned one-shot terminal lifecycle boundary**.

A runtime operation may, before starting one top-level guest computation in an existing Task, install one backend-private terminal lifecycle boundary on that same Task. C-prime remains the only guest continuation authority. If guest execution suspends, only C-prime continuation state is retained. The Java/native caller stack is abandoned normally and is never a continuation.

When the Task later reaches its **true terminal state** — after any C-prime resume, structured cancellation unwind and existing child-drain obligations — the Task invokes the terminal lifecycle boundary exactly once with the terminal outcome needed by the owning runtime operation.

Conceptually:

```text
host setup
    -> install optional terminal lifecycle boundary on existing Task
    -> start one guest C-prime computation in that same Task
          -> complete, or
          -> yield / resume through C-prime only
          -> cancellation unwind / child drain as already defined
    -> Task reaches true terminal state
    -> terminal lifecycle finalizer(outcome), exactly once
```

The finalizer is **not a continuation**. It cannot suspend, yield, request another guest invocation, restart a Java frame, replace the Task continuation or form a second scheduler.

## Trigger

PERF006-B6A6 production-cutover validation exposed Bytecode-plan Closures escaping public parse into ordinary runtime/host dispatch. Some consumers such as `Closure.future()` and exact inspectors need the same Task-owned C-prime execution but no additional post-call lifecycle. Other production consumers do.

`ProtosActorRequest.executeAcceptedTurn`, for example, performs host admission/setup, invokes guest behavior in the Task, and after the guest handler truly finishes must still transfer the reply, mark delivery complete, or update failure/cancellation state. Under the replay evaluator, re-entry reconstructed that Java lifecycle. Under C-prime, only guest Bytecode continuation state survives suspension, so silently relying on Java-stack re-entry would be incorrect.

PLAT017 deliberately rejected a generic native-to-guest composed-call request ABI and stated that concrete later evidence for unavoidable semantically invisible host-wrapper composition must reopen platform review. PERF006-B6A6 provided that evidence. The resulting requirement is narrower than the generic protocol PLAT017 rejected: it needs terminal host finalization, not arbitrary alternating host/guest stages.

## Existing authority

The following remain authoritative and are not amended:

- **PLAT014** — C-prime / Truffle Bytecode DSL `ContinuationResult` is the sole cooperative guest continuation architecture.
- **PLAT019** — Java/native stack frames are never guest continuation state; sufficient resumable state must exist before abandoning Java activation.
- **PLAT021** — structured control, cancellation and unwind remain C-prime/Task-owned according to the existing control architecture.
- **PLAT025** — module initialization keeps its dedicated lifecycle carrier. PLAT027 does not replace it merely for uniformity.
- **PLAT001** — Context-local execution-plan projection and sharing-layer rules remain independent.
- **PLAT017** — the generic arbitrary native-to-guest composed-call request ABI remains rejected.

## Comparative implementation survey

The decision was ratified after screening all current principal Truffle catalogue implementations and the historical/experimental catalogue, then comparing materially similar non-Truffle async/task/actor runtimes.

### Principal Truffle implementations

The 16 principal implementations reviewed were Enso, Espresso, FastR, GraalJS, GraalPy, GraalWasm, grCUDA, Apple Pkl, SimpleLanguage, SOMns, Sulong, TRegex, TruffleRuby, TruffleSOM, TruffleSqueak and Yona.

Strongest evidence:

- **Espresso** materializes guest continuation state but disallows suspension across native/VM frames and locks. It strongly argues against treating an arbitrary Java host lifecycle as part of the guest continuation.
- **GraalJS** stores promise reaction state explicitly and executes it later via promise-reaction jobs. Completion work is durable state, not a retained Java call stack.
- **GraalPy / Bytecode DSL** keeps coroutine/await resumability in Python/bytecode execution state rather than a generic surrounding Java-frame continuation protocol.
- **SOMns** represents promise callbacks and actor work as explicit messages carrying callback/resolver state.
- **TruffleSqueak** represents logical process/context state explicitly and uses explicit process-switch control rather than Java-stack identity.
- **TruffleRuby** deliberately avoids general `callcc`-style JVM continuation assumptions and uses explicit runtime mechanisms such as Fibers.
- **SimpleLanguage Bytecode DSL** confirms that Bytecode continuations capture generated guest interpreter state, not arbitrary surrounding host Java work.
- **Sulong / GraalWasm** keep guest/native/function boundaries explicit and provide no stronger precedent for a generic arbitrary host-wrapper continuation ABI.
- **Apple Pkl** uses Future-backed external transport but ultimately blocks at evaluator-facing boundaries. That is a useful contrast, not a model for Protos, because blocking would violate the already-ratified Protos Task/C-prime scaling architecture.
- Enso, FastR and grCUDA provide secondary evidence for explicit host/runtime state; TRegex is not materially comparable as a general Task/guest lifecycle runtime.

The 21 historical/experimental entries screened were BACIL, bf, brainfuck-jvm, Cover, DynSem, Heap Language, hextruffe, islisp-truffle, LuaTruffle, Mozart-Graal, Mumbler, PorcE, ProloGraal, PureScript, Reactive Ruby, shen-truffle, TruffleBF, streamblocks-graalvm, TruffleMATE, TrufflePascal and ZipPy. Informative cases with coroutine, actor or dataflow machinery likewise rely on explicit runtime state. None established a stronger maintained precedent for retaining an arbitrary host Java frame around suspendible guest work.

### Non-Truffle evidence

- **Rust `Future` / Tokio** represents async computation as explicit Task-owned pollable state; a Waker schedules the Task rather than preserving a native stack.
- **Swift concurrency** stores resumable task context explicitly in `AsyncTask`/async contexts and transitions between running, suspended and completed states without making the physical caller stack authoritative.
- **Erlang/BEAM** materializes delayed reply and multi-stage server work as explicit request/state transitions rather than preserving callback stacks.
- **Java `CompletionStage` / `CompletableFuture`** provides a close terminal-completion analogue through completion actions such as `whenComplete`; structured child tasks are a separate topology with different lifetime semantics.
- **Go goroutines** can retain a language/runtime-managed goroutine stack, but that evidence is not transferable to Protos because C-prime deliberately captures Bytecode guest continuation state rather than arbitrary JVM host frames.

## Candidate set

### A′ — Task-owned terminal lifecycle boundary

**Selected.** One optional Task-owned, backend-private, terminal-only finalizer observes true terminal outcome after existing C-prime and child-drain rules. Operation-specific state remains owned by the operation itself.

### B — operation-specific lifecycle carriers

Correct but rejected as the default common boundary. ActorRequest, GroupRequest and every later consumer would duplicate true-terminal, cancellation and child-drain coordination. PLAT025 remains an intentional operation-specific carrier because module initialization has its own stronger cache lifecycle semantics.

### C — generic host→guest→host state-machine ABI

Rejected. It is broader than the evidenced requirement, recreates the generic cross-layer protocol rejected by PLAT017 and risks becoming a second interaction/continuation language beside C-prime.

### D — child Task per guest invocation

Rejected. It changes Task parentage, cancellation propagation, child-drain timing, associated-Future ownership and scheduling/memory costs solely to preserve host implementation control flow.

### E — move host lifecycle into Bytecode/guest structured operations

Rejected as the general architecture. Actor delivery, reply transfer and host-resource lifecycle are legitimately runtime-owned concerns and should not enlarge the guest Bytecode instruction/control surface merely to preserve Java sequencing.

### F — AST/replay normal-dispatch island

Rejected because it blocks PERF006-B6 replay retirement and makes runtime semantics/backend behavior depend on how a Closure escaped.

### G — block/park one physical carrier

Rejected by PLAT010/011/014 scalability and carrier-utilization constraints.

### H — capture the whole JVM/host stack

Rejected. It would require a fundamentally different VM continuation substrate and contradicts the deliberate C-prime guest-continuation architecture.

## Required GITHUB010 scorecard

Scores are 1–5. Confidence: H high, M medium.

| Criterion | **A′ Task terminal** | B specific carriers | C generic ABI | D child Task | E Bytecode host lifecycle | F replay island | G blocking carrier | H host-stack capture |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | **5/H** | 5/H | 5/M | 2/H | 4/M | 4/H short-term | 3/H | 2/H |
| Protos alignment | **5/H** | 4/H | 2/H | 2/H | 3/M | 1/H | 1/H | 1/H |
| Future-option resilience | **5/H** | 4/H | 5/M | 3/M | 3/M | 1/H | 2/H | 3/M |
| Scalability | **5/H** | 4/H | 4/M | 2/H | 4/M | 2/H | 1/H | 2/M |
| Conceptual simplicity | 4/H | 3/H | 2/H | 3/H | 2/M | 3/H | 4/H | 2/H |
| Portability / implementation freedom | **5/H** | **5/H** | 3/M | 4/H | 3/M | 2/H | 2/H | 1/H |
| Runtime / resource cost | **5/H** | 4/H | 3/M | 2/H | 4/M | 2/H | 1/H | 2/M |
| Failure / operability | **5/H** | 4/H | 3/M | 2/H | 3/M | 3/H | 2/H | 2/M |
| Reversibility / migration cost | **5/H** | 4/H | 2/M | 2/H | 2/M | 1/H | 2/H | 1/H |
| Evidence maturity / implementation risk | **5/H** | **5/H** | 3/M | **5/H** | 3/M | **5/H** | **5/H** | 4/H |
| **Total / 50** | **49** | **42** | **32** | **27** | **31** | **24** | **23** | **20** |

### Owner-focus scoring

| Candidate | Future endurance | Scalability | Protos philosophy | Mean |
| --- | ---: | ---: | ---: | ---: |
| **A′ Task terminal boundary** | **9.8** | **9.8** | **10.0** | **9.87** |
| B specific carriers | 8.5 | 8.5 | 8.5 | 8.50 |
| C generic state-machine ABI | 9.0 | 8.0 | 5.0 | 7.33 |
| D child Task | 6.0 | 6.0 | 3.0 | 5.00 |
| E host lifecycle in Bytecode | 7.0 | 8.0 | 5.0 | 6.67 |
| F replay island | 2.0 | 3.0 | 1.0 | 2.00 |
| G blocking carrier | 3.0 | 1.0 | 1.0 | 1.67 |
| H whole host-stack capture | 5.0 | 3.0 | 2.0 | 3.33 |

## Durable invariants

1. C-prime / `ContinuationResult` remains the sole guest continuation representation.
2. A terminal lifecycle boundary is Task-owned backend/runtime state, never a Protos value and never a Java-frame continuation.
3. The boundary is installed before the guest execution whose terminal outcome it owns.
4. It executes at most once and only when the owning Task reaches a true terminal state after existing child-drain and cancellation-unwind completion.
5. It receives only terminal outcome information needed for finalization: completed value, failed Error value or cancelled state.
6. The finalizer cannot suspend, yield, request another guest invocation, replace the Task continuation or re-enter arbitrary guest execution.
7. Host finalization executes on the owning Task/domain execution path; no global lifecycle dispatcher or registry is introduced.
8. No new Task is created solely to preserve host post-call control flow.
9. Tasks without such lifecycle state allocate no continuation/state-machine object merely because the capability exists. Exact nullable/sentinel representation is implementation-local.
10. Existing associated-Future terminalization and parent/child Task semantics remain authoritative. Observable ordering must remain equivalent to the current semantics.
11. Operation-specific state — Actor delivery attempt, request reply destination, Group reservation and similar data — remains owned by the concrete operation. PLAT027 supplies only the true-terminal notification boundary.
12. Expected operation-level failure handling belongs to the owning lifecycle finalizer. An escaping Java failure from that finalizer is a runtime invariant failure, not a new guest Error or second Task result.
13. Context-local Closure projection and ReturnHome completion remain separate PERF006-B6A6A implementation concerns.
14. PLAT025 module initialization keeps its dedicated lifecycle carrier and cache lifecycle.
15. PLAT027 does not authorize a generic native/host-to-guest composed-call request protocol.
16. If a future operation needs alternating `host → guest → host → guest`, a suspendible finalizer, or multiple non-terminal lifecycle callbacks, platform architecture must be reopened rather than widening PLAT027 silently.
17. A future non-Truffle backend may implement the same terminal lifecycle contract without Java callbacks or `ContinuationResult` as long as observable semantics and the single guest-continuation authority remain equivalent.

## Scaling and future stress

- **Millions of ordinary Tasks:** no finalizer object is required unless a host lifecycle actually needs one; true-terminal work remains O(1).
- **Many Actors/Processes:** state remains Task/local-operation owned; there is no cross-Actor or global registry/lock.
- **Deep C-prime suspension:** retained guest state remains C-prime only; PLAT027 adds at most bounded terminal lifecycle state to applicable Tasks, not to every guest frame.
- **Cancellation/unwind:** the finalizer observes CANCELLED only after existing C-prime cleanup and child-drain obligations settle the Task, preventing duplicate host cleanup.
- **Nested child Tasks:** existing child-drain authority naturally delays terminal lifecycle finalization until the owner really terminates.
- **Context projection:** the terminal lifecycle boundary contains no Truffle frame and does not itself move CallTargets between Contexts; PLAT001 remains authoritative.
- **Native Image/AOT:** the selected architecture needs ordinary closed-world runtime state only, with no reflection-based registry or host-stack capture.
- **Bytecode DSL evolution:** future generated-interpreter changes can replace the guest driver without changing the Task terminal lifecycle contract.
- **Alternative/non-Truffle backend:** backend-specific execution can expose the same true-terminal notification without adopting JVM callback mechanics.
- **Distributed Actors:** remote protocol state remains with the route/operation authority; a distributed backend need not serialize JVM callbacks.

## Regret trigger and escape path

The main plausible regret trigger is evidence that many future runtime operations genuinely need multiple alternating suspendible guest and host stages rather than one guest computation followed by terminal finalization.

**Escape path:** collect those concrete consumers and explicitly review a narrow host-lifecycle state-machine abstraction. PLAT027 keeps the current state backend-private and non-semantic, so it can be generalized later without changing Protos values or continuation semantics. The generalization is not implicitly approved here.

## Strongest argument against A′

A terminal hook on the central Task abstraction could become a dumping ground for unrelated runtime callbacks.

That risk is constrained normatively at the platform layer: terminal-only, one-shot, non-suspending, no guest re-entry, no general listener API, and operation-specific state remains outside Task. A consumer that does not fit those constraints must reopen architecture instead of stretching this mechanism.

## Deliberately deferred

PLAT027 does not select:

- exact Java interface/class names or callback representation;
- nullable field versus compact terminal carrier representation;
- exact internal ordering between already-existing Task terminal bookkeeping steps when externally equivalent;
- generic multi-stage host/guest state machines;
- guest re-entry from terminal finalization;
- suspension from terminal finalization;
- module lifecycle replacement;
- a new Task/Actor/Process semantic;
- distributed serialization of terminal lifecycle state; or
- replay retirement details outside the released B6A6A boundary.

## PERF006-B6A6A release boundary

Publication of this ratification releases bounded implementation work needed to make normal Task-backed Bytecode Closure execution survive suspension while preserving required host lifecycle finalization.

That implementation may:

- add one optional backend-private Task terminal lifecycle boundary consistent with the invariants above;
- teach existing Task-owned Bytecode execution entrypoints to drive C-prime rather than route Bytecode-plan Closures through the replay-era invoker;
- migrate ActorRequest/GroupRequest and other evidenced terminal-only host wrappers to install operation-owned finalizers before guest execution;
- preserve exact Context-local Closure projection, ReturnHome ownership, Error/NLR/cancellation behavior and associated-Future semantics;
- add focal and full regression evidence, including no completed-prefix replay; and
- mechanically subdivide B6A6A if implementation/test isolation requires it.

It may not:

- create a generic host→guest→host request/state-machine ABI;
- create child Tasks merely to preserve host control flow;
- block platform carriers on guest completion;
- make the Java/native stack a continuation;
- retain an AST/replay execution island as the final production architecture;
- broaden PLAT025 module carriers onto unrelated operations merely for uniformity; or
- change observable Protos semantics.

If implementation discovers a need for a suspendible finalizer, guest re-entry from the finalizer, multiple alternating host/guest stages or another durable architecture not determined above, PERF006-B6A6A stops and crosses the normal PLAT/D approval gate.

## Approval record

The project owner explicitly approved **Candidate A′ — Task-owned one-shot terminal lifecycle boundary** on 2026-09-11 after the exhaustive Truffle and non-Truffle comparative review, scorecard, future stress analysis and strongest-counterargument review recorded in GitHub #378.
