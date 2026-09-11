# PLAT028 — C-prime-owned suspendible callback orchestration and native-leaf boundary

Status: **RATIFIED — Candidate C selected**

Nature: durable non-normative JVM/Truffle/runtime architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#386**

Primary consumer: `PERF006-B` / GitHub #276, beginning with the production-cutover callback surfaces exposed after `PERF006-B6A6A1`.

Normative effect: **none**. PLAT028 changes no Protos call, callback, Array/Map, Future, Task, Actor, Process, I/O, failure, cancellation, control, ordering or suspension semantics. It selects only where the implementation must retain semantically invisible control state when runtime/standard-library machinery invokes ordinary guest code that may suspend and then has more work to perform.

## Decision

Select **Candidate C — C-prime-owned suspendible callback orchestration + native-leaf invariant**.

C-prime remains the sole owner of suspendible guest control and post-callback continuation state.

If an implementation operation has this shape:

```text
runtime / standard operation
    -> invoke ordinary guest code
          -> guest may suspend
    -> perform more operation-owned work after the guest call
```

then the control that must survive the guest suspension — including the program counter, loop phase, next callback, post-call branch or other sequencing state — must be represented inside the same C-prime-resumable execution domain. It may be expressed as source-backed Protos code, generated/specialized Bytecode, a Bytecode intrinsic/operation, or another implementation representation that is genuinely part of the C-prime continuation machine.

A Java/native operation may remain a **leaf with respect to arbitrary guest re-entry**. The leaf may perform host mechanics, mutate backend-private state, allocate values, access a platform service, or use an already-ratified native suspension mechanism such as PLAT019. What it may not do is synchronously invoke arbitrary ordinary guest code, rely on that guest call being suspendible, and then treat the surrounding Java/native frame as the continuation that must resume afterward.

Conceptually, the selected shape is:

```text
C-prime control
    -> native/host leaf mechanics
    -> C-prime control
    -> ordinary guest callback
          -> complete, or
          -> suspend / resume through C-prime
    -> C-prime post-callback control
    -> optional native/host leaf mechanics
```

not:

```text
Java/native wrapper frame
    -> ordinary guest callback
          -> suspend
    -> retain/reconstruct wrapper Java frame
    -> continue Java/native post-call control
```

The decision is about **continuation ownership**, not source-language spelling. PLAT028 does not require every affected standard operation to live in a `.protos` file.

## Trigger and corrected scope

PLAT028 was initially opened while auditing Future-backed native adapters such as TextReader/TextWriter. The production-cutover evidence after `PERF006-B6A6A1` proved the boundary is broader.

The retained full suite exposed `IllegalStateException: Bytecode Closure plan requires composed Bytecode invocation until PERF006-B2 normal dispatch cutover` on callback-capable paths. The audit found the same structural pattern across ordinary standard/runtime operations:

- `Array.each` owns a Java loop and invokes an ordinary polymorphic guest block;
- `Bytes.each` has an equivalent host-loop / guest-callback shape;
- Environment and ProcessArguments iteration invoke ordinary guest callbacks from native standard protocols;
- Map hashing/equality/iteration and IdentityMap-related callback paths contain host-owned sequencing around guest invocation;
- Boolean/control helpers include native-to-guest callback composition;
- TextReader, TextWriter and buffered adapters invoke structurally supplied ordinary protocol operations and then continue host-owned state machines;
- Package Tool `ContentIdentity.protos` exercises these surfaces heavily through `each`, sorting callbacks, loops and Future-backed file reads.

`ProtosClosureInvoker` intentionally rejects a projected Bytecode Closure plan on the old synchronous replay-era invocation path. That rejection is correct: after a C-prime guest callback suspends, an arbitrary surrounding Java frame is not resumable guest state.

Therefore PLAT027 terminal finalization alone cannot restore these paths. PLAT027 runs once after a Task truly terminalizes; PLAT028 concerns **intermediate post-callback control before Task terminality**.

## Existing authority and composition

The following remain authoritative.

### PLAT014 — C-prime continuation authority

Truffle Bytecode DSL / C-prime continuation state is the sole cooperative guest continuation architecture. PLAT028 does not create a parallel host continuation language.

### PLAT019 — native semantic suspension bridge

PLAT028's native-leaf invariant is about **guest re-entry**, not a blanket ban on native suspension. A native leaf may use PLAT019 to expose a genuine native semantic suspension boundary into C-prime, provided it does not retain a Java caller frame that must continue after an arbitrary guest callback.

### PLAT021 — dynamic control/unwind

Error/handler, ensure, non-local return, cancellation and structured unwind remain C-prime/Task-owned. Migrated callback orchestration must compose with that control machinery rather than bypassing it through host callbacks.

### PLAT025 — module initialization

The ratified module-lifecycle carrier remains a dedicated bounded composition mechanism. PLAT028 does not replace it merely for uniformity.

### PLAT027 — terminal lifecycle finalization

A PLAT027 terminal finalizer remains valid because it is terminal-only, one-shot, non-suspendible and does not preserve a Java frame across an intermediate guest suspension. PLAT028 must not widen PLAT027 into a generic host continuation stack.

### PLAT017 — generic composed-call ABI

PLAT028 is the concrete reconsideration point that PLAT017 reserved, but the selected answer is **not** the generic arbitrary native-to-guest composed-call ABI. Instead, callback-owning control migrates into the already-selected C-prime continuation domain.

### PLAT001 — Context isolation

Context-local executable projection and sharing-layer rules remain unchanged. C-prime callback orchestration must execute with the same owning Context/Process authority as the containing Task; PLAT028 introduces no cross-Context continuation transfer.

## Exhaustive Truffle implementation survey

The project-owner approval followed an expanded survey of the current principal Truffle catalogue and the relevant historical/experimental catalogue, with systems separated by evidentiary relevance rather than counted equally.

### 1. Truffle Bytecode DSL / SimpleLanguage — strongest direct precedent

Bytecode DSL-generated interpreters own their resumable state. `ContinuationResult` retains interpreter continuation state and resumes that generated execution machine; arbitrary surrounding Java frames are not part of the captured guest continuation.

This is the closest architectural precedent because Protos already selected C-prime on Bytecode DSL. It supports keeping the caller that has post-suspension work inside the resumable interpreter domain.

Owner-focus score as precedent for PLAT028: future endurance **5/5**, scalability **5/5**, Protos fit **5/5**.

### 2. Espresso — strongest negative evidence against host-stack assumptions

Espresso can materialize and resume Java guest frames, a substantially stronger stack-capture facility than Protos intends to provide. Even so, suspension is constrained by native/non-Java frames and other VM conditions; native boundaries are not magically made resumable as part of the guest continuation.

Espresso therefore argues strongly against treating an arbitrary Java standard-library wrapper as continuation state around a suspending Protos guest callback.

As an architecture to copy directly: future endurance **4/5**, scalability **4/5**, Protos fit **2.5/5**. As negative evidence against whole-host-stack capture: confidence **HIGH**.

### 3. GraalJS

GraalJS async/promise interop represents pending completion explicitly through Promise/thenable reaction state. Host integration resolves/rejects explicit async objects; it does not depend on retaining a synchronous Java frame through JavaScript `await`.

This supports explicit resumable guest/runtime state and clear async boundaries rather than host-stack continuation.

Precedent score: future endurance **5/5**, scalability **5/5**, Protos fit **4.5/5**.

### 4. GraalPy

Python coroutine/generator resumability is owned by Python/runtime execution state. GraalPy materializes language-level frame/control state when needed and maintains explicit indirect-call/runtime context across non-Python boundaries rather than presenting arbitrary Java wrapper frames as Python continuations.

Precedent score: future endurance **4.5/5**, scalability **4.5/5**, Protos fit **4/5**.

### 5. Apple Pkl

Pkl's external readers are materially relevant at coarse external-service boundaries. Evaluator work is represented with explicit requests/responses and pending evaluator state; language bindings can perform reader work asynchronously and later deliver a response that lets evaluation continue.

The useful lesson is not to turn every local Protos callback into a message. It is that a potentially asynchronous host boundary materializes explicit state instead of preserving the synchronous caller stack.

For Protos, a Pkl-style request/response boundary remains compatible for future filesystem/network/service adapters when the boundary is genuinely external/coarse. Local callback orchestration stays C-prime-owned.

Precedent score: future endurance **4.8/5**, scalability **4.6/5**, Protos fit **4.7/5**.

### 6. SOMns

SOMns represents promises/callbacks and actor work as explicit runtime entities/messages scheduled back onto the owning actor. A promise resolution schedules pending work rather than depending on the producer's host stack.

This is particularly strong evidence for future Actor/distributed Protos boundaries: Future/message state should be explicit at actor/remote boundaries, while local same-Task callback sequencing remains inside C-prime.

Precedent score: future endurance **5/5**, scalability **5/5**, Protos fit **4.5/5**.

### 7. TruffleRuby

TruffleRuby's documented limitations around general `callcc`-style continuations and the cost model of stackful Fiber implementation provide negative evidence against making physical JVM stacks or one carrier per logical suspendible callback the Protos architecture.

Precedent score as a model to copy: future endurance **3/5**, scalability **2.5/5**, Protos fit **2.5/5**.

### 8. TruffleSqueak

TruffleSqueak preserves Smalltalk process/context execution identity in guest/runtime structures rather than making the Java interpreter stack the semantic process identity. Its stackful Smalltalk model is not directly transferable to C-prime, but it reinforces guest-owned logical execution state.

Precedent score: future endurance **4.5/5**, scalability **3.5/5**, Protos fit **3.5/5**.

### 9. Sulong / LLVM

Sulong keeps LLVM/native/foreign execution boundaries explicit. The public architecture supplies no stronger maintained precedent for making an arbitrary surrounding Java wrapper transparently resumable when guest work suspends.

Contribution: negative/partial evidence. Model score: future endurance **3.5/5**, scalability **3/5**, Protos fit **2.5/5**.

### 10. GraalWasm

GraalWasm likewise has explicit import/host boundaries but no public generic facility that turns an arbitrary Java caller around a guest invocation into resumable guest control state.

Contribution: partial evidence. Model score: future endurance **3.5/5**, scalability **4/5**, Protos fit **3/5**.

### 11. FastR

FastR is useful mainly as boundary-cost evidence. Managed/native transitions and foreign extension paths are explicit and can be expensive in hot code. A design that repeatedly bounces callback control through Java/native wrappers would work against Truffle partial-evaluation locality and increase boundary complexity.

Contribution: secondary evidence. Model score: future endurance **2.5/5**, scalability **3/5**, Protos fit **2.5/5**.

### 12. grCUDA

grCUDA has strong asynchronous scheduling/data-dependency concerns, but those concern GPU work and dependency progression rather than retaining a native wrapper around a suspendible guest callback. It supports explicit operation state but is not a direct continuation precedent.

Contribution: secondary evidence. Model score: future endurance **3.5/5**, scalability **4.5/5**, Protos fit **3/5**.

### 13. Enso

Enso provides useful optimizer/runtime evidence for keeping semantic execution inside language/runtime representations that Truffle can reason about, but no equally direct public host→guest-suspend→host continuation mechanism was found. It therefore receives lower evidentiary weight.

Contribution: partial evidence. Model score: future endurance **3.5/5**, scalability **3.5/5**, Protos fit **3.5/5**.

### 14. TruffleSOM

TruffleSOM is historically informative for object-language execution on Truffle; SOMns is the stronger member of that family for concurrency/promises and therefore carries more weight for PLAT028.

Contribution: historical/partial. Model score: future endurance **2.5/5**, scalability **2.5/5**, Protos fit **3/5**.

### 15. Yona

Yona's parallel/non-blocking design is conceptually relevant, but current public implementation evidence is less complete and mature than Bytecode DSL, GraalJS, Espresso, Pkl or SOMns for this exact boundary.

Contribution: low-confidence supporting evidence. Model score: future endurance **4/5**, scalability **4/5**, Protos fit **3.5/5**, confidence **LOW**.

### 16. TRegex

TRegex is an internal regular-expression engine rather than a general Task/guest lifecycle runtime. It was screened but is not materially comparable to PLAT028 and is intentionally not scored as if it solved the same problem.

## Experimental/historical Truffle screening

The survey also screened the 21 catalogue entries used by earlier PLAT reviews: BACIL, bf, brainfuck-jvm, Cover, DynSem, Heap Language, hextruffe, islisp-truffle, LuaTruffle, Mozart-Graal, Mumbler, PorcE, ProloGraal, PureScript, Reactive Ruby, shen-truffle, TruffleBF, streamblocks-graalvm, TruffleMATE, TrufflePascal and ZipPy.

The materially informative subset was:

- **PorcE / Orc** — structured concurrent computations use explicit runtime/control representation; useful supporting evidence for transformed/managed continuation ownership rather than arbitrary Java-stack capture.
- **Mozart-Graal / Oz** — dataflow/thread progress is represented in language/runtime state; historically informative but less mature as current implementation evidence.
- **streamblocks-graalvm / CAL** — actor/dataflow execution represents actor scheduling/state explicitly and scales without making caller stacks the logical operation identity.
- **LuaTruffle and related coroutine experiments** — useful mainly as evidence of the portability/cost tension of physical-stack coroutine approaches on JVM/Truffle.
- **Reactive Ruby** — historically relevant explicit reactive state, but insufficiently strong current evidence to drive the decision.

None of the experimental entries established a stronger maintained precedent for transparent arbitrary Java/native-wrapper resumption around a suspendible guest callback.

## Non-Truffle comparative evidence

The approval packet also checked mature systems outside Truffle to avoid overfitting to current GraalVM machinery.

### Kotlin coroutines

Suspendible callers are CPS/state-machine transformed. If a caller has work after a suspending callee, that caller's post-call state is part of the coroutine state machine. This is a close conceptual analogue of Candidate C.

### Rust `Future` / async

An async computation is an explicit pollable state machine. Post-`await` control belongs to that future's state; the synchronous caller stack is not retained as the continuation.

### Swift concurrency

Async functions/tasks maintain explicit resumable async contexts. Checked/unsafe continuations bridge callback APIs back into a Task but do not turn arbitrary synchronous caller frames into persistent continuations.

### Java `CompletionStage` / `CompletableFuture`

Post-completion behavior is expressed as explicit stages such as `thenApply`/`whenComplete`. This is a weaker but useful analogue for materializing work that must occur after asynchronous completion.

### Erlang/BEAM

Logical process progress and delayed replies are scheduler/runtime-owned state. Native code that blocks is a scheduler hazard rather than a magically resumable part of a BEAM process continuation.

### Go

Goroutines demonstrate that a runtime can own stackful logical execution cheaply when the runtime itself controls those stacks. That does not justify making an arbitrary JVM Java frame part of Protos C-prime; Protos already selected a different continuation substrate.

## Candidate set

### A — generic Task-owned host continuation stack/ABI

Every host wrapper may push an explicit resumable host frame around a guest call, and a Task alternates C-prime guest continuation and host continuation frames.

Rejected. It is powerful but creates a second control/continuation language beside C-prime, complicating Error/unwind/cancellation, debugger projection, Context isolation and non-JVM portability.

### B — operation-specific host state carriers

Array iteration, Map callbacks, TextReader and each other family keep a custom Java state machine/resume path.

Rejected as the general architecture. It avoids one generic host stack but duplicates composition, failure and resume rules per operation and still splits suspendible control between C-prime and Java.

### C — C-prime-owned callback orchestration + native-leaf invariant

**Selected.** Callback-owning control is represented in C-prime. Java/native remains guest-non-reentrant leaf mechanics; PLAT019 native suspension and PLAT027 terminal finalization remain separately valid.

### D — C-prime-first plus bounded generic host-continuation escape hatch

Credible future fallback, but rejected now. It installs a second continuation mechanism before evidence shows an operation that cannot be factored into C-prime orchestration plus host leaf mechanics.

### E — child Task per guest callback

Rejected. It changes Task topology, scheduling, cancellation, child drain, Future ownership and memory costs solely to preserve implementation control flow.

### F — block/park a physical carrier

Rejected by PLAT010/011/014 scaling constraints. It converts logical suspension into carrier retention and scales with pending callbacks.

### G — preserve an AST/replay island for callback-capable wrappers

Rejected. It would keep the architecture PERF006-B exists to retire and make backend behavior depend on which runtime wrapper a Closure reaches.

### H — capture the JVM/host stack

Rejected. Espresso's stronger facility still exposes native-frame limits; JVM-stack capture would be backend-specific, costly and contrary to C-prime as the selected guest continuation representation.

### I — declare native-invoked ordinary callbacks non-suspendible

Rejected because it would change observable language/library behavior to accommodate the implementation and create a privileged callback class not selected by the Protos semantics.

## GITHUB010 scorecard

Scores are 1–5. Confidence is HIGH unless marked otherwise.

| Criterion | A host stack | B per-op | **C C-prime-owned** | D hybrid | E child Task | F block | G replay | H JVM capture | I forbid |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | 4.5 | 4.5 | **5.0** | 5.0 | 2.0 | 2.0 | 3.0 | 3.5 | 1.0 |
| Protos alignment | 3.0 | 3.5 | **5.0** | 4.0 | 2.0 | 1.5 | 2.0 | 2.0 | 1.0 |
| Future-option resilience | 5.0 | 3.5 | **5.0** | 5.0 | 3.0 | 1.0 | 1.5 | 2.0 | 1.5 |
| Scalability | 4.0 | 4.0 | **5.0** | 4.5 | 3.0 | 1.0 | 3.0 | 2.5 | 4.0 |
| Conceptual simplicity | 2.0 | 3.0 | **4.5** | 3.5 | 3.0 | 3.0 | 3.0 | 2.0 | 4.0 |
| Portability / implementation freedom | 3.5 | 4.0 | **5.0** | 4.5 | 4.0 | 2.0 | 2.0 | 1.5 | 5.0 |
| Runtime / resource cost | 3.5 | 4.0 | **4.5** | 4.0 | 2.5 | 1.0 | 3.0 | 2.5 | 5.0 |
| Failure / operability | 3.0 | 4.0 | **4.5** | 4.5 | 2.5 | 2.0 | 3.0 | 2.0 | 2.0 |
| Reversibility / migration | 3.0 | 4.0 | **4.0** | 4.0 | 2.5 | 3.0 | 2.0 | 1.5 | 2.0 |
| Evidence maturity / risk | 4.0 | 4.0 | **5.0** | 4.0/M | 3.0 | 4.0 | 4.0 | 2.5 | 4.0 |

Comparison means: A 3.55, B 3.85, **C 4.75**, D 4.30, E 2.75, F 2.05, G 2.65, H 2.20, I 2.95. Arithmetic is supporting evidence only; hard Protos/runtime constraints independently reject E/F/G/H/I.

### Owner-focus score

| Candidate | Future endurance | Scalability | Protos philosophy |
| --- | ---: | ---: | ---: |
| **C — C-prime-owned callback orchestration** | **5/5** | **5/5** | **5/5** |
| D — C-prime + host escape hatch now | 5/5 | 4.5/5 | 4/5 |
| A — generic host continuation stack | 4.5/5 | 4/5 | 3/5 |
| B — operation-specific host state | 3.5/5 | 4/5 | 3.5/5 |
| E — child Task | 3/5 | 3/5 | 2/5 |
| H — JVM stack capture | 2.5/5 | 2.5/5 | 2/5 |
| G — replay island | 1.5/5 | 3/5 | 2/5 |
| I — forbid suspension | 1.5/5 | 4/5 | 1/5 |
| F — blocking carrier | 1/5 | 1/5 | 1.5/5 |

## Durable invariants

1. **Single suspendible-control authority.** C-prime remains the sole general suspendible guest/control continuation architecture.
2. **Post-guest work is continuation state.** If runtime/standard machinery invokes ordinary guest code that may suspend and must later continue, the remaining control must be C-prime-resumable state.
3. **Native-leaf means guest-non-reentrant.** Java/native leaf mechanics may perform host work and may use PLAT019 native suspension, but must not retain a host frame around arbitrary ordinary guest invocation expecting post-suspension re-entry.
4. **No semantic callback downgrade.** PLAT028 does not make ordinary callbacks non-suspendible or introduce a privileged callback category.
5. **No hidden Task topology.** A child Task is not created merely to preserve a runtime wrapper's call stack.
6. **No physical-carrier retention.** Guest callback suspension cannot require parking one carrier/thread per logical suspended callback.
7. **No replay escape island.** Callback-capable standard/runtime paths do not remain on replay merely because migration is inconvenient.
8. **No generic host continuation stack selected.** Task does not acquire a second general stack of Java/host continuation frames under PLAT028.
9. **PLAT027 remains terminal-only.** Terminal lifecycle finalizers stay valid but cannot be reused as intermediate callback continuations.
10. **PLAT019 remains valid.** Native semantic suspension leafs remain valid and are not reclassified as callback orchestration.
11. **Context authority is unchanged.** Callback orchestration remains in the owning Task/Process Context; no CallTarget or continuation crosses Context ownership implicitly.
12. **Control/unwind equivalence is mandatory.** Error, ensure, NLR, cancellation and later-transfer behavior must remain equivalent when orchestration migrates from Java wrappers to C-prime.
13. **Ordinary lookup/call remains authoritative.** Migration must not bypass D013/ordinary polymorphic lookup merely to reach a convenient intrinsic.
14. **Implementation spelling remains free.** A C-prime-owned standard operation may be source-backed Protos, generated/specialized Bytecode, or another C-prime representation; `.protos` source is not mandated.
15. **Host services may use explicit async boundaries.** Pkl-style request/response is compatible for external service boundaries; SOMns-style Future/message state is compatible at Actor/distributed boundaries. These do not create a second local host continuation stack.
16. **Partial evaluation remains a design goal.** Repeated hot callback control should remain visible to Truffle/C-prime rather than bounce unnecessarily through opaque host re-entry.
17. **Alternative backends may realize the invariant differently.** A non-Truffle implementation need not use `ContinuationResult`, but must keep suspendible callback-owning control in its language/runtime continuation domain rather than relying on an incidental host caller stack.

## Scaling and future stress

### Millions of Tasks / callbacks

Candidate C does not allocate a generic host-frame stack per Task or retain one platform thread per suspended callback. A Task pays for the C-prime state of execution that actually suspends, matching PLAT014's pay-only-at-suspension direction.

### Deep nested callbacks

Nested ordinary callbacks compose in one C-prime logical execution stack/control machine. They do not alternate between two independently unwound guest and host continuation stacks.

### Cancellation and unwind

Because callback-owning control remains in C-prime, PLAT021 Error/ensure/NLR/cancellation transfers cross the same resumable control representation. A Java host frame cannot swallow or duplicate a later transfer simply because it was reconstructed incorrectly.

### Many Actors / Processes

No global registry or cross-Actor continuation map is introduced. Local callback state belongs to the owning Task/Context. Actor/remote boundaries may materialize Future/message state explicitly according to their own authority.

### Multi-Context execution

C-prime executable projection remains Context-local under PLAT001. PLAT028 does not authorize moving continuation state or executable plans between Contexts.

### Native Image / AOT

The selected invariant needs no JVM stack walking, reflection-driven continuation registry, or Loom dependency. Generated C-prime state and explicit native leaf operations remain amenable to closed-world/AOT analysis.

### Truffle optimizer / partial evaluation

Moving hot callback-owning loops/control into C-prime keeps the sequencing visible to generated Bytecode and Truffle partial evaluation. Leaf host mechanics remain bounded calls rather than opaque control owners surrounding guest callbacks.

### Alternative scheduler

No carrier identity is part of the continuation contract. A later scheduler can resume the same Task/C-prime state on another permitted carrier.

### Distributed execution

Local C-prime continuation state is not proposed as a network-serialized continuation. Crossing an Actor/node boundary should continue to use explicit messages/Futures/protocol state, consistent with SOMns/Pkl-style evidence where appropriate.

### Non-Truffle backend

The durable statement is not "use Truffle `ContinuationResult` everywhere". It is that suspendible callback-owning control belongs to the language/runtime execution machine. A future backend may use an async state machine, CPS, explicit frames or another mechanism while preserving observable Protos semantics.

## Migration consequences

Candidate C deliberately accepts more migration work now to avoid a second permanent continuation architecture.

Likely affected implementation families include:

- Array/Bytes iteration callbacks;
- Environment/ProcessArguments iteration;
- Map hashing/equality/iteration callbacks;
- Boolean/control callbacks still owned by native Java sequencing;
- TextReader/TextWriter and buffered adapter lower-protocol calls;
- Package Tool paths that exercise these operations;
- other runtime/native code found by a complete `ProtosInvocation.invoke*` callback-owning audit.

For each operation, implementation may:

1. move orchestration into source-backed standard Protos code and expose only leaf host primitives;
2. add a generated/specialized C-prime Bytecode operation/intrinsic after ordinary semantic selection;
3. split existing Java state into leaf mechanics plus C-prime-owned sequencing;
4. retain an existing native implementation unchanged only when it does not own a suspendible guest callback continuation.

The migration must be sliced by bounded callback family and validated against the retained semantics/full suite. PLAT028 does not authorize a broad rewrite with unreviewed semantic changes.

## Regret trigger and escape path

The strongest plausible regret scenario is discovery of an operation that genuinely requires repeated:

```text
host -> guest -> host -> guest -> host
```

stages where substantial host-owned state cannot reasonably be factored into leaf mechanics plus C-prime orchestration without unacceptable cost or semantic distortion.

**Escape path:** reopen platform architecture with that concrete operation set and reconsider Candidate D or another narrow explicit host-state-frame abstraction. Because Candidate C does not expose a public continuation object or persisted ABI, a bounded future host-continuation escape hatch can still be added without changing Protos semantics.

The escape is intentionally **not** preinstalled. PLAT028 requires evidence of an irreducible host-owned case before a second continuation mechanism is admitted.

## Strongest argument against Candidate C

Migration breadth and near-term engineering cost.

The current JVM runtime contains many native standard-protocol implementations that synchronously invoke guest code. Some, especially Map callback machinery and text/buffered I/O adapters, own non-trivial Java state. A generic Task-owned host continuation stack could restore many of these surfaces with fewer immediate rewrites.

That short-term advantage is not enough to select it. A generic host continuation stack would become a second control architecture that every future Error/unwind/cancellation/debugger/Context/optimizer/backend feature would have to understand. Candidate C pays migration cost once in order to keep one durable continuation universe.

## Deliberately deferred

PLAT028 does not select:

- exact Java class/interface names for migrated leaf primitives;
- exact Bytecode operation/intrinsic names or generated representation;
- whether each affected operation is best expressed in `.protos` or generated Bytecode;
- a generic host-continuation frame ABI;
- serialization of local C-prime continuations for distributed execution;
- a new public `async`/`await`/Continuation syntax or value;
- new Task/Future/Actor semantics;
- resource scheduling changes;
- blocking/offload-lane policy for genuinely blocking foreign APIs;
- exact performance thresholds for choosing source-backed versus generated standard operations; or
- replay-retirement details outside the bounded PERF006-B consumer work.

If implementation reveals an irreducible need for a general host continuation stack, guest re-entry from PLAT027 finalizers, host frames that themselves suspend through arbitrary guest calls, or a semantic callback restriction, the affected slice stops and reopens the normal PLAT/D approval gate.

## PERF006-B release boundary

Publication of this ratification releases bounded implementation required to restore production C-prime callback-capable paths and continue replay retirement.

Implementation may:

- inventory current callback-owning native/runtime surfaces completely;
- migrate callback-owning sequencing into source-backed or generated C-prime control;
- introduce semantically invisible native leaf primitives where needed;
- specialize exact standard operations only after ordinary semantic lookup/selection when prior decisions require that discipline;
- preserve PLAT019 native suspension leafs and PLAT027 terminal lifecycle boundaries;
- add focal/differential/full-suite evidence proving suspension/resume without completed-prefix replay;
- decompose work into bounded publishable callback families; and
- retire corresponding replay-era host orchestration only after equivalent behavior is proven.

Implementation may not:

- add a generic Task-owned host continuation stack;
- create child Tasks merely to preserve Java control flow;
- block/park one physical carrier per suspended callback;
- retain an AST/replay island as the durable production answer;
- capture arbitrary JVM host stacks;
- make ordinary callbacks non-suspendible;
- bypass ordinary Protos lookup/call semantics; or
- change observable Protos semantics to reduce migration work.

## Approval record

The project owner explicitly approved **Candidate C — C-prime-owned suspendible callback orchestration + native-leaf invariant** on **2026-09-11** after the initial decision packet and the requested expanded survey of all principal Truffle implementations, relevant experimental/historical implementations, Apple Pkl, outside-Truffle async runtimes, future endurance, scalability and Protos-philosophy scoring recorded in GitHub #386.
