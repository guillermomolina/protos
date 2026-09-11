# PLAT021 — Bytecode C-prime dynamic control/unwind representation

Status: **RATIFIED**

Nature: durable non-normative JVM / Truffle / Bytecode DSL control/unwind architecture decision

Approved by project owner: **2026-09-11**

GitHub Issue: **#328**

Primary consumer: `PERF006-B4` under GitHub #276 / PERF006-B under #262

Normative effect: **none**. Existing Protos-visible Error/handler, `ensure`,
non-local return, cancellation, `while`, Future, Task, Closure and call semantics
remain authoritative.

## Selected architecture — Candidate F

Select **Bytecode-local structured control + original transfer lanes + narrow EH
bridge**.

The production C-prime backend keeps three concepts separate:

1. **Normal values** remain ordinary raw Protos values.
2. **Suspension** remains the PLAT014/PLAT019 C-prime continuation mechanism and
   is not unwind.
3. **Real control/unwind** uses rare backend-private transfer carriers while
   resumable structured-control phase/state lives in the Bytecode
   frame/continuation that owns execution.

Ownership follows semantic responsibility:

- Bytecode frame/continuation state owns resumable `ensure` phase, loop/control
  PC, pending transfer while cleanup executes, and values needed after cleanup.
- Task state owns only genuinely Task-wide authority: cooperative cancellation
  state and the dynamic handler-selection/token state required by existing
  semantics.
- Activation/`ProtosReturnHome` ownership remains authoritative for non-local
  return target identity.

Guest Error remains a guest-error lane. Non-local return and cancellation remain
internal control-transfer lanes. When an internal control transfer must cross a
Bytecode EH region so active cleanup runs, the backend may use a **narrow private
EH bridge** to an `AbstractTruffleException`-compatible envelope. That envelope
retains the exact original transfer object, is transport only, is not a Protos
Error, is not semantic authority, and must not become a public/tooling-visible
language concept.

## Why this decision is needed

PLAT014 selected C-prime Bytecode DSL continuation composition and explicitly
deferred the exact production representation/adaptation for Error, cancellation
and non-local return. Its A2b adapter was feasibility evidence, not durable
architecture.

PERF006-B1 through B3 now provide the Bytecode execution substrate, Closure/call
composition and real Future/Task suspension. B4 must move dynamic control/unwind
off replay-owned state while preserving current semantics.

The remaining replay implementation still couples control behavior to evaluator
history:

- `ensure` keeps replay-owned phase/cursor state and rewinds/compacts replay
  state when cleanup suspends;
- `while` retains replay callback checkpoints;
- `Error.handle` owns Task-local dynamic handler frames but invokes guest body
  and handler through replay machinery;
- Closure invocation coordinates replay-stable activations with ReturnHome
  completion; and
- canonical return is not yet lowered through the production Bytecode path.

Treating `ensure`, `handle` or `while` as generalized resumable native Java
calls would create a second continuation engine because those protocols invoke
arbitrary guest Closures and may themselves suspend or transfer control.

## Fixed authority and invariants

The following constraints remain authoritative:

1. Suspension is **not** unwind. A pending `Future.value()` does not execute
   surrounding `ensure` cleanup merely because the Task suspends.
2. Cleanup executes on normal protected-region exit or real Error/NLR/cancellation
   unwind.
3. Cleanup may itself suspend and resume without re-entering or replaying the
   protected body.
4. A pending transfer survives cleanup suspension with exact identity; a later
   transfer produced by cleanup supersedes it.
5. Error occurrence identity, innermost matching, handler delegation and dynamic
   selection remain exact.
6. The selected handler is inactive before crossed cleanup executes, so a cleanup
   Error cannot be caught again by that same selected handler.
7. NLR preserves exact payload + `ProtosReturnHome` identity and is consumed only
   by its owning live return home.
8. Cancellation remains Task-owned and cooperative; it is not host-thread
   interruption.
9. D044 `while` remains an ordinary Closure-specific protocol with strict Boolean
   decision and ordinary Closure invocation.
10. Ordinary lookup/extraction/aliasing/rebinding remains authoritative; an
    optimized standard-control path may activate only from implementation
    provenance after normal lookup has selected the canonical implementation.
11. No global control/continuation registry, semantic global lock, semantic
    `ThreadLocal` Task authority, carrier affinity, parked carrier per suspended
    Task, replay tape on the normal hot path, or second native continuation VM.
12. Normal non-suspending execution remains compilation-friendly and does not pay
    a mandatory outcome/tag allocation or propagation tax.

## Exhaustive Truffle implementation survey

The audit used the current public Truffle catalogue as the inventory boundary.

### Principal implementations — 16

Enso; Espresso; FastR; GraalJS; GraalPy; GraalWasm; grCUDA; Apple Pkl;
SimpleLanguage; SOMns; Sulong; TRegex; TruffleRuby; TruffleSOM;
TruffleSqueak; Yona.

The catalogue's experimental/historical section was also screened for additional
architecture families: BACIL, bf, brainfuck-jvm, Cover, DynSem, Heap Language,
hextruffe, islisp-truffle, LuaTruffle, Mozart-Graal, Mumbler, PorcE,
ProloGraal, PureScript, Reactive Ruby, shen-truffle, TruffleBF,
streamblocks-graalvm, TruffleMATE, TrufflePascal and ZipPy. These entries did not
add a stronger durable architecture family than the candidates below; historical
coroutine/continuation experiments remain evidence rather than a production
direction.

### Bytecode DSL and GraalPy — strongest placement evidence

Current GraalPy compiles structured exception handling to the Bytecode DSL and
stores exception state needed across generator yield/resume in frame-local state.
The key lesson is ownership: resumable structured-control phase belongs to the
interpreter/frame continuation that executes it, not to a parallel replay tape or
Task-wide mini-interpreter.

The Bytecode DSL supplies structured `TryCatch`, `TryFinally`,
`TryCatchOtherwise`, continuation/yield support and root-level control-flow
interception. Its yield/finally tests establish the required separation:
suspending inside a protected region does not itself execute `finally`, while a
`finally` may itself suspend and later continue before the pending exit completes.

### GraalJS — strongest suspendible-cleanup precedent

GraalJS distinguishes yield from real control and guest exceptions. Its resumable
`try/finally` path does not execute `finally` on yield. For real transfer it keeps
the pending transfer while the `finally` body executes; if `finally` yields, that
pending transfer is retained in resumable state and rethrown only after cleanup
completes.

This directly supports Protos' `suspension != unwind`, suspendible cleanup,
pending-transfer retention and later-transfer supersession requirements.

### TruffleSqueak, TruffleSOM and SOMns — strongest NLR precedent

The Smalltalk-family implementations model non-local return as optimized control
transfer carrying the returned value plus exact target/home identity. The target
is compared by identity at the owning frame/context boundary.

That shape closely matches existing `ProtosNonLocalReturnException` carrying
`ProtosReturnHome + value`. The durable Protos design should preserve that
minimal identity-bearing transfer rather than replace it with a universal
result algebra.

TruffleSqueak also uses a separate control transfer for process switching,
supporting the broader principle that distinct rare control mechanisms should
remain distinct rather than become one guest Error category.

### TruffleRuby — mature unwind precedent, rejected suspension topology

TruffleRuby separates guest exceptions and internal control transfers and runs
`ensure` before repropagating a pending transfer. This is strong evidence for the
unwind shape.

Its Fiber implementation has host-thread/virtual-thread and queue/latch
relationships required by Ruby compatibility. That physical suspension topology
is deliberately rejected for Protos because PLAT010/011/014 require bounded
reusable carriers rather than one parked carrier/resource per suspended Task.

### Espresso — continuation lifecycle precedent

Espresso demonstrates a mature capture-on-real-suspension lifecycle: live guest
state is made resumable only when suspension occurs, and execution is later
reconstructed through a dedicated resume path.

Protos does not copy Espresso's Java-stack/frame-record representation. C-prime
already owns guest continuation state. The useful precedent is that ordinary
calls stay ordinary and suspension state is paid only at actual suspension
boundaries.

### Sulong / LLVM — structured EH and resume precedent

LLVM/Sulong make exceptional edges, cleanup regions and resume explicit. A
pending exception is carried through cleanup and resumed/rethrown afterward;
normal instruction results are not universally wrapped in an `Outcome`.

This is strong evidence for keeping pending transfer state at the structured
control boundary and paying transfer cost only when a transfer occurs.

### Apple Pkl — narrow category-separation precedent

Apple Pkl uses Truffle guest exceptions for guest failures and narrow
`ControlFlowException` mechanisms for internal control. Pkl does not expose a
control surface comparable to Protos' Task + Future suspension + NLR + dynamic
handlers + suspendible ensure, so it is not a complete B4 template.

Its relevant evidence is nevertheless important: guest failure and internal rare
control need not be collapsed merely because both can be transported by Java
exceptions.

### Enso

Enso uses `AbstractTruffleException` for guest panic and `ControlFlowException`
for internal tail-call control carrying the continuation inputs needed by its
tail-call loop. This independently reinforces guest-failure/internal-control
separation.

### GraalWasm

GraalWasm represents traps/failures as guest Truffle exceptions while its normal
structured control belongs to the interpreter's WebAssembly control machine.
The lesson is placement: control PC that naturally belongs to the bytecode
interpreter should remain there.

### FastR

FastR demonstrates that Truffle can host a much richer dynamic condition/context
model. It is useful mainly as a complexity warning: Protos should retain only the
Task-local handler authority required by its already-defined semantics rather
than introduce a general dynamic-control context institution.

### SimpleLanguage

SimpleLanguage models `return` as a narrow `ControlFlowException` carrying the
result, caught at the function boundary. It is the minimal canonical example that
rare control transfer need not impose a tagged result on every ordinary call.

### Yona

Yona supplies historical evidence for guest exceptions in a parallel/non-blocking
Truffle language, but its Truffle repository is archived and its control surface
is less comparable. It receives low architecture weight.

### grCUDA and TRegex

These are in the catalogue but are not materially comparable to Protos B4:
grCUDA is a polyglot CUDA integration and TRegex is an internal regular-expression
engine. Neither owns general language-level NLR + dynamic handlers + suspendible
cleanup. They are retained in the inventory to avoid cherry-picking but do not
drive the decision.

## Non-Truffle corroboration

Mature bytecode/VM systems converge on the same broad shape:

- CPython 3.11+ uses exception tables so ordinary instructions do not carry a
  per-expression handler/result wrapper; exception/finally state is materialized
  at transfer boundaries.
- JVM bytecode uses per-method exception tables and compiler-generated cleanup
  paths; normal return values are not universally boxed as outcomes.
- CLI/.NET uses protected-region EH plus `leave`/`finally` semantics rather than a
  universal result wrapper.
- LLVM IR makes unwind/cleanup edges explicit and resumes the pending exception
  after cleanup.

These are corroborating implementation precedents only; Protos semantics remain
defined by Protos authority.

## Candidate set

### A — retain replay control until B6 / defer

Keep replay-owned dynamic-control cursors while only Future suspension uses
C-prime.

Rejected because B4/B6 specifically require retiring the replay architecture from
the production control path.

### B — universal tagged `ControlOutcome`

Every C-prime call returns a tagged form such as
`Normal(value) | Error(e) | Return(home,value) | Cancelled`.

Portable and explicit, but it puts propagation checks/tagging pressure on every
ordinary call/result, duplicates VM exception machinery, broadens the backend
ABI and violates pay-only-for-what-you-use.

### C — Task-local external control state machine

Move ensure/loop/handler PCs into a new Task-owned control stack while Bytecode
continuations own expression/call state.

Potentially correct, but it splits one logical continuation across two state
machines, increases synchronization/debugging/migration complexity and makes Task
a side interpreter rather than only Task-wide authority.

### D — generalized resumable-native control closures

Generalize PLAT019 so `ensure`, `handle` and `while` become Java native state
machines capable of invoking and resuming arbitrary guest Closures.

Rejected because it creates a second continuation engine alongside Bytecode DSL
and must manually reproduce nested guest-call, cleanup-suspension and transfer
supersession behavior.

### E — one unified `AbstractTruffleException` transfer envelope

Represent guest Error, NLR and cancellation with one handler-visible Truffle
exception kind + payload.

This has good runtime characteristics and is the strongest alternative, but it
collapses guest failure and successful/internal control into one durable category,
raising tooling/instrumentation leakage risk and making accidental guest-handler
observation easier.

### F — Bytecode-local structured control + original transfer lanes + narrow EH bridge

Keep normal values, suspension and real unwind distinct. Structured control phase
lives in C-prime Bytecode continuation state; Error remains a guest-error lane;
NLR/cancellation remain internal control transfers. Only when an internal control
transfer must participate in Bytecode EH does a private bridge envelope adapt it
for the handler table and preserve the exact original transfer.

This is selected.

## GITHUB010 comparative scoring

`H/M/L` is confidence. Scores are 1–5. Totals are comparison aids only.

| Criterion | A Replay | B Outcome | C Task VM | D Native VM | E Unified envelope | **F Selected** |
|---|---|---|---|---|---|---|
| Correctness / invariants | 5/H — current semantics | 4/M — explicit, many sites | 4/M — encodable | 3/L — nested control risk | 4/M — category collapse risk | **5/H — exact lanes + ownership** |
| Protos alignment | 1/H — wrong destination | 2/H — institution everywhere | 3/M — Task becomes interpreter | 2/H — duplicate engine | 4/M — compact but over-unified | **5/H — ordinary stays ordinary** |
| Future-option resilience | 1/H — replay debt | 3/M — ABI spreads widely | 2/M — Task ABI entrenched | 2/H — Java mini-runtime entrenched | 4/M — isolated carrier | **5/H — backend seam isolated** |
| Scalability | 2/H — replay/history cost | 3/M — per-call checks/tags | 3/M — side-state coordination | 3/M — native machine state | 5/H — rare-transfer cost | **5/H — live-state proportional** |
| Conceptual simplicity | 2/H — dual execution | 3/M — one algebra everywhere | 2/H — two coupled machines | 1/H — most machinery | 4/H — one transport | **4/M — one narrow bridge seam** |
| Portability / freedom | 2/M — evaluator-specific | 5/H — host-neutral algebra | 4/M — mostly host-neutral | 3/M — Java/native shaped | 4/M — Truffle-localized | **4/M — Truffle isolated behind C-prime** |
| Runtime / resource cost | 1/H — known pathology | 2/M — ordinary path pays | 3/M — side-state lookup | 3/M — state/callback objects | 5/M — transfer-only | **5/H — raw fast path** |
| Failure / operability | 4/H — heavily known | 4/M — explicit states | 3/M — split-state desync | 2/M — hardest debugging | 4/M — single carrier but leakage risk | **5/M — categories remain diagnosable** |
| Reversibility / migration | 1/H — blocks cutover | 2/M — pervasive ABI | 2/M — Task structure spreads | 2/H — protocol spreads | 4/M — envelope can be replaced | **5/M — bridge/Bytecode local** |
| Evidence maturity / risk | 5/H — current code | 4/M — common algebra pattern | 3/M — plausible, no close precedent | 2/L — weak precedent | 4/M — Truffle-friendly | **5/H — convergent runtime evidence** |

The arithmetic does not ratify F by itself. A, C and D fail the selected C-prime
architecture boundary; B imposes a pervasive ordinary-path institution; E is
credible but weakens the semantic separation between guest failure and internal
control. F is the only candidate that simultaneously preserves the fixed
semantics, C-prime ownership and pay-only-for-use runtime shape.

## Focused project-owner criteria

| Architecture | Future durability | Scalability | Protos philosophy |
|---|---:|---:|---:|
| A — replay | 1/5 | 2/5 | 1/5 |
| B — universal outcome | 3/5 | 3/5 | 2/5 |
| C — Task control VM | 2/5 | 3/5 | 3/5 |
| D — native control VM | 2/5 | 3/5 | 2/5 |
| E — unified exception envelope | 4/5 | 5/5 | 4/5 |
| **F — selected** | **5/5** | **5/5** | **5/5** |

The runtime precedents contribute different parts rather than defining one model:
Smalltalk/Squeak supplies the closest NLR meaning; GraalPy/Bytecode DSL supplies
state placement; GraalJS supplies suspendible-finally behavior; Sulong supplies
structured unwind/resume; Espresso supplies capture/resume lifecycle discipline;
Apple Pkl and Enso reinforce guest-error/internal-control separation.

## Selected durable transfer model

### Normal value

A normal result remains exactly a normal Protos value. No wrapper is required.

### Suspension

PLAT019 suspension capability produces a C-prime continuation only on real
semantic suspension. Suspension leaves active handler/ensure regions logically
active and does not traverse cleanup.

### Error

Guest Error remains the guest-error transfer category and preserves the exact
Error occurrence required by existing semantics. Dynamic handler selection stays
Task-local because selection is Task-wide semantic authority.

The selected handler token is deactivated before crossed cleanup. A cleanup Error
therefore cannot be caught again by that same selected handler.

### Non-local return

Canonical return lowers to a rare internal control transfer preserving exact
`ProtosReturnHome + value`. Bytecode cleanup regions execute before the transfer
reaches the owning live home. The owner consumes only its exact ReturnHome;
mismatches continue outward.

### Cancellation

Task remains the sole cooperative cancellation authority. Once cancellation is
observed as an unwind condition, active cleanup regions execute through the
internal control-transfer lane. Cancellation is not host-thread interrupt
authority and does not create carrier affinity.

### EH bridge

A `ControlFlowException`-family internal transfer is not permanently reclassified
as guest failure. Only where Bytecode EH requires a guest-exception-compatible
transfer to enter a handler/cleanup table may the backend wrap the exact original
transfer in a private envelope. The appropriate backend boundary unwraps and
rethrows/consumes the original object.

The wrapper:

- is backend-private;
- carries exact original transfer identity;
- is not observable as a Protos Error;
- is not matched by ordinary guest Error handlers;
- is not a debugger/public interop concept;
- is not Task-wide semantic authority; and
- must not be used to turn suspension into unwind.

## Standard control-protocol specialization

`ensure`, `Error.handle` and `while` remain ordinary Protos protocol entries.
Bytecode control specialization may occur only after ordinary lookup/binding has
selected the exact canonical standard implementation.

Implementation provenance, rather than selector spelling, may carry a private
Bytecode-control capability. Extraction, aliasing and rebinding preserve that
implementation provenance according to ordinary Closure behavior. A nearer
override using the same selector must not trigger the standard intrinsic.

This extends PLAT017's post-lookup principle and avoids selector-specific backend
pets.

## Scaling and concurrency

Selected retained state is proportional to logical live work:

`O(live C-prime continuation depth + active dynamic handler scopes + O(1) Task cancellation state)`

It is not proportional to all executed expressions, replay history or physical
carrier count.

Independent Tasks/Actors/Processes do not coordinate through a global control
registry or global lock. A suspended Task owns logical continuation state while
the bounded PLAT010/011 carrier substrate is immediately reusable by other work.

Context isolation remains compatible because control state belongs to the
execution/Task/frame objects within the owning RuntimeHost/Context rather than a
JVM-global registry or semantic `ThreadLocal`.

## Partial evaluation, AOT and Bytecode DSL evolution

The ordinary path remains raw values and direct calls. Rare control transfers use
Truffle-supported control/exception mechanisms; structured phase lives in
Bytecode state. This keeps normal paths friendly to partial evaluation and avoids
mandatory result boxing merely to support uncommon unwind.

The architecture intentionally isolates Truffle-specific EH adaptation behind the
C-prime backend. If Bytecode DSL APIs evolve, the lowering/bridge can change
without redefining Protos Error, ReturnHome, cancellation, Closure or Task
semantics.

Native Image/AOT does not require reflective control registries or dynamically
generated public transfer types. Private transfer classes and generated
Bytecode-DSL machinery remain statically discoverable implementation code.

## Future-regret stress test

### What plausible future requirement could make this choice regrettable?

A future non-Truffle backend may not provide exception-table/control-transfer
facilities with characteristics comparable to Truffle/JVM EH. A future Protos
feature could also require first-class resumable algebraic effects whose control
semantics are broader than today's Error/NLR/cancellation/ensure set.

### Escape path

The durable decision does **not** make Java exception classes part of Protos
semantics or public ABI. The semantic invariants are lane separation, ownership
and structured cleanup behavior. Another backend may realize the same invariants
with explicit CFG edges, tagged internal machine states, native EH or another
mechanism.

If Protos later selects first-class effect semantics, that would be a new
language decision. It can supersede this backend architecture without preserving
the narrow bridge as a language institution.

## Strongest argument against F

Candidate E is simpler at the immediate Bytecode EH boundary: one
`AbstractTruffleException` envelope for Error, NLR and cancellation would allow
uniform handler-table transport and could reduce bridge-specific code.

That simplification is real. It is rejected because it makes the transport
category easier to confuse with guest failure, increases tooling/interop leakage
risk and unnecessarily commits three semantically distinct concepts to one
durable representation. F accepts one narrow private seam to keep the semantic
categories and ownership boundaries cleaner.

## PERF006-B4 implementation decomposition released by ratification

Ratification releases B4 implementation in this order, subject to the normal
rule that any newly exposed semantic/durable architecture choice stops for a new
Dxxx/PLATxxx gate:

1. **B4A — transfer substrate**: establish the private Error/internal-control
   transfer categories, EH bridge and architecture guards without migrating
   higher-level control protocols.
2. **B4B — non-local return**: lower canonical return and exact ReturnHome
   consumption through C-prime.
3. **B4C — `ensure`**: structured cleanup, suspension through cleanup, pending
   transfer retention and supersession.
4. **B4D — Error handlers**: dynamic selection/token consumption and crossed
   cleanup.
5. **B4E — cancellation unwind**: Task-owned cancellation through active cleanup.
6. **B4F — `while`**: move loop/callback phase off replay checkpoints while
   preserving D044 ordinary protocol semantics.
7. **B4G — closure evidence**: retained cross-product evidence for normal exit,
   Error/NLR/cancellation, nested cleanup, suspension and no replay.

B4A must not smuggle B4C-D-E-F protocol migration into the substrate slice.

## Deliberately deferred choices

Ratification does not select:

- exact Java class names for bridge envelopes beyond their private role;
- exact generated Bytecode DSL operation names or local-slot layout;
- optimization thresholds or profiling strategy;
- B5 source/instrumentation/debugger migration details;
- B6 replay-removal mechanics;
- a public continuation/effect/coroutine concept;
- a general algebraic-effect system; or
- any change to observable Protos Error, cancellation, ReturnHome, `ensure`,
  handler or `while` semantics.

A local implementation choice may fill in representation detail only when it
cannot alter the durable constraints above. A newly substantive choice requires a
new decision gate.

## Approval record

The project owner explicitly approved Candidate F on **2026-09-11** after
requesting an exhaustive comparison of the Truffle implementation catalogue,
including Apple Pkl, and explicit scoring for future durability, scalability and
Protos philosophy. GitHub #328 records the research packet, extended audit and
approval.
