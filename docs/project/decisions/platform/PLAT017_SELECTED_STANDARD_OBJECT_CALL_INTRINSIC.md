# PLAT017 — selected-standard `Object.call` Bytecode intrinsic

Status: **RATIFIED**

Nature: durable non-normative Truffle / Bytecode DSL call-dispatch architecture decision

Approved by project owner: **2026-09-10**

GitHub Issue: **#304**

Primary consumer: `PERF006-B2D3B` under GitHub #276 / PERF006-B under #262

Normative effect: **none**. D013 and the current normative call semantics remain
authoritative. PLAT017 selects only how the JVM/Truffle Bytecode backend realizes
the already-defined standard `Object.call` behavior after ordinary lookup has
selected it.

## Problem

D013 defines parenthesized invocation through the ordinary object model:

1. evaluate the original target and caller-supplied argument vector;
2. perform ordinary lookup of the nearest `call` slot;
3. require the selected value to be a Closure;
4. invoke that selected Closure in method role with the original target as
   `this` and the selected slot owner as `methodHome`; and
5. allow nearer non-Closure `call` values to shadow inherited callable behavior
   and fail as ordinary non-callable selection.

PERF006-B2D3A converged non-Closure targets onto that structural rule. Semantic
Closure targets still had the earlier B2B direct-Closure shortcut.

For a normal Closure with no nearer override, ordinary lookup selects the
standard native `Object.call`. Its Closure branch delegates to
`ProtosClosureInvoker.invoke(...)`. That invoker still belongs to the legacy
AST/replay execution architecture and intentionally rejects Bytecode execution
plans before normal-source cutover. Making it directly Bytecode-aware would also
risk treating a `ContinuationResult` as true invocation completion and closing a
nested ReturnHome too early.

PERF006-B2D3B therefore needs a durable backend topology that completes D013
structural convergence without changing D013, creating a second continuation
authority, or retaining a replay-era bridge in the final Bytecode hot path.

## Existing authority and fixed constraints

The following decisions remain authoritative and are not amended by PLAT017:

- **D013** — `call` is an ordinary Closure-valued lookup protocol. Shadowing,
  selected slot ownership, receiver binding and method role remain ordinary
  semantics.
- **PLAT014** — C-prime is the sole cooperative continuation architecture:
  Bytecode DSL continuations compose across ordinary call roots, suspension is
  not unwind, and completed effects are not replayed.
- **PLAT005** — StatementTag / CallTag instrumentation authority.
- **PLAT008** — logical execution-location identity.
- **PLAT013** — debugger/interop value projection.
- **PLAT015** — debugger scope projection.
- **PLAT016** — one Bytecode Closure activation root owns binding, default
  execution, rest binding and body execution.

PERF006 phase ownership remains unchanged:

- B2 owns Closure/call composition only;
- Task/Future suspension ownership remains B3;
- `ensure`, Error/NLR and loop/control migration remains B4;
- complete tooling/source compatibility remains B5;
- normal-source cutover and replay retirement remain B6.

PLAT017 MUST NOT introduce a global continuation registry, one thread per
suspended Task, a permanent interpreter-transfer hot path, or a second public
callable concept.

## Exhaustive Truffle implementation review

The decision was ratified after reviewing the complete implementation catalogue
published by the Truffle project at the decision checkpoint: **16 principal
implementations plus 21 experimental/historical implementations**. Every
catalogue entry was screened; implementations with a comparable dynamic
call/builtin/continuation problem were then inspected more deeply.

### Principal implementations

The 16 principal catalogue entries reviewed were:

- Enso
- Espresso
- FastR
- GraalJS
- GraalPy
- GraalWasm
- grCUDA
- Pkl
- SimpleLanguage
- SOMns
- Sulong
- TRegex
- TruffleRuby
- TruffleSOM
- TruffleSqueak
- Yona

The strongest architectural precedents were:

- **TruffleSqueak** — method lookup occurs first. Only after one exact method has
  been selected does dispatch attempt its primitive. Primitive failure falls
  back to the CallTarget of that same selected method. This is the closest
  structural analogue to D013 plus an intrinsic implementation of the canonical
  standard behavior.
- **Espresso** — ordinary Java method resolution remains the semantic authority;
  substitutions/intrinsics replace the implementation of an exact selected
  method. Its continuation implementation also demonstrates the architectural
  hazard of leaving unnecessary native/intrinsic frames in a suspendible chain.
- **TruffleRuby** — `InternalMethod` identity and invalidating assumptions select
  one method before an `alwaysInlined` implementation is used. Tooling/backtrace
  observability is preserved when an inlined implementation would otherwise
  hide relevant call structure.
- **SimpleLanguage Bytecode DSL** — the current framework reference implements
  builtins as Bytecode operations and ordinary calls with stable-target
  DirectCallNode specializations plus an indirect fallback. The official
  Bytecode DSL builtin guidance permits direct builtin inlining only when the
  required stack/tooling semantics permit that frame to be physically absent.
- **Sulong** — dispatch first resolves the function/code identity and then chooses
  among LLVM IR, intrinsic and native implementations. A true native/NFI
  boundary is reserved for code that is actually native, rather than used as an
  intermediate protocol for guest-to-guest composition.
- **GraalJS, GraalPy and FastR** — builtins are normalized into ordinary root /
  CallTarget execution structures rather than returning generic requests for
  later guest execution.
- **GraalWasm and Enso** — special result/exception tokens are used for explicit
  control semantics such as tail calls. They are not a general convention for
  an arbitrary host builtin to ask the interpreter to execute another guest
  callable.
- **SOMns / TruffleSOM** — message lookup, specializers/primitives and
  Direct/IndirectCallNode dispatch reinforce the Smalltalk-family pattern:
  ordinary semantic selection comes before implementation specialization.

Pkl, Yona, grCUDA and TRegex were retained in the audit for completeness but
carry less direct decision weight: Pkl reinforces ordinary call-target
composition, Yona's asynchronous model is language-level, grCUDA represents a
real FFI boundary, and TRegex is not a general callable-language runtime.

### Experimental and historical implementations

The 21 experimental/historical catalogue entries screened were:

- BACIL
- bf
- brainfuck-jvm
- Cover
- DynSem
- Heap Language
- hextruffe
- islisp-truffle
- LuaTruffle
- Mozart-Graal
- Mumbler
- PorcE
- ProloGraal
- PureScript
- Reactive Ruby
- shen-truffle
- TruffleBF
- streamblocks-graalvm
- TruffleMATE
- TrufflePascal
- ZipPy

These entries did not reveal a stronger counterexample. Where they use explicit
backtracking, actor/dataflow or structured-concurrency control, that machinery
belongs to the language semantics. Minimal/static languages do not have a
meaningfully comparable D013-style dynamic call selection problem. Historical
Ruby/Python implementations are superseded as evidence by current TruffleRuby
and GraalPy.

## Cross-implementation result

The maintained implementations converge on two useful patterns:

1. **normalize a builtin/function into an ordinary RootCallTarget/callable
   topology**, or
2. **resolve ordinary semantics first and then substitute/intrinsify the exact
   selected implementation**.

PLAT017 uses the second pattern during PERF006 migration and remains compatible
with the first as B6 simplifies the completed Bytecode backend.

No maintained implementation reviewed established a compelling precedent for a
general backend-private protocol where arbitrary native builtins routinely
return a "please execute this guest callable" request to the interpreter.

Request/token-like mechanisms appear when they carry a specific semantic control
transfer (for example a tail call or primitive-failure protocol). PLAT017 does
not create such a Protos semantic operation.

## Reviewed alternatives

### A — selected-standard `Object.call` intrinsic after ordinary lookup

**Selected.**

Always execute D013 selection first. If the selected value is an ordinary
override, invoke it normally. Only when lookup selects the exact canonical
standard root `Object.call`, and that standard behavior would delegate to a
Closure receiver, may Bytecode preparation elide the physical native bridge and
prepare the receiver Closure invocation directly in the existing C-prime call
chain.

### B — generic native-to-Bytecode composed-invocation request

Rejected as the default architecture.

It can be implemented correctly, including retaining both wrapper and nested
ReturnHomes across suspension, but introduces a new dynamic request/result
protocol between arbitrary native Closures and the Bytecode backend. That
protocol would add allocation/retained state and a second cross-layer concept
without current language semantics requiring it.

Its apparent future reuse for `ensure`, `while` or `Future.value()` is not a
benefit under PERF006: B3 and B4 explicitly migrate those responsibilities onto
C-prime suspension/control state rather than preserving their replay-era native
wrapper topology.

B remains only a future fallback if concrete evidence proves that a necessary,
semantically invisible native-wrapper composition cannot be expressed through
the selected call/control architecture without creating worse constraints. Such
evidence would reopen a platform decision; B is not implicitly authorized by
PLAT017.

### C — make `ProtosClosureInvoker` directly Bytecode-aware

Rejected for the migration.

`ProtosClosureInvoker` currently participates in legacy replay and ReturnHome
ownership. Teaching that general invoker to return/resume Bytecode continuation
results would make the replay-era invoker another continuation authority during
B2-B5 and create exactly the mixed backend boundary PERF006 is removing.

A post-B6 implementation may refactor common invocation machinery after C-prime
is the sole production authority. That later internal refactoring does not make
C the correct migration bridge now.

### D — retain the direct-Closure shortcut until B6

Rejected as a closure strategy.

It preserves a fast path temporarily but leaves Closure targets outside D013
structural convergence, forcing B3-B5 to support two parenthesized-invocation
models and postponing B2's core composition responsibility.

## Selected architecture — A

Conceptually:

```text
parenthesized invocation
        |
        v
evaluate target + supplied arguments
        |
        v
ordinary D013 lookup of nearest `call`
        |
        +-- missing --------------------------> ordinary failure
        |
        +-- selected value is not Closure ---> ordinary failure
        |
        +-- selected ordinary override ------> invoke selected Closure
        |                                      in method role
        |
        +-- exact canonical Object.call
                |
                +-- standard non-Closure receiver behavior
                |        -> ordinary standard implementation
                |
                +-- receiver is Closure
                         |
                         v
                 Bytecode intrinsic seam
                         |
                         v
                 prepare receiver Closure
                         |
                         v
                 existing C-prime
                 call / yield / resume
```

The optimization is therefore behind the ordinary semantic mechanism. It does
not establish a privileged Protos-visible callable category.

## Durable constraints

1. **D013 lookup remains authoritative.** No implementation may replace the
   structural rule with a pre-lookup `receiver instanceof Closure` shortcut.

2. **Only exact selected standard behavior may be intrinsified.** The fast path
   must be guarded by the identity/equivalence of the canonical standard
   `Object.call` behavior selected by lookup.

3. **Nearest shadowing remains exact.** A nearer Closure-valued `call` is invoked
   normally. A nearer non-Closure `call` shadows inherited standard behavior and
   fails normally. The intrinsic must not see through either case.

4. **Slot ownership remains exact.** Non-standard selected calls preserve the
   selected slot owner's `methodHome`. Standard-call elision must preserve the
   same observable receiver/home behavior that physical execution of
   `Object.call` would have produced.

5. **Reflection remains ordinary.** `Object.call` remains a real ordinary
   Closure-valued slot. Reading, reflecting, extracting, overriding or shadowing
   it must not expose a second hidden callable concept.

6. **C-prime is the only continuation authority.** Once the standard bridge is
   elided, the target Closure enters the existing PLAT014 Bytecode continuation
   chain; PLAT017 adds no parallel continuation protocol.

7. **`ContinuationResult` is not completion.** ReturnHome completion occurs only
   when the composed invocation actually completes. Yield/suspension must retain
   the same logical activation/ReturnHome ownership until resume completes.

8. **Suspension is not unwind.** PLAT017 must not translate suspension through
   Error, non-local return, cancellation or cleanup semantics.

9. **No replay.** A resumed intrinsic-composed call must not re-evaluate target,
   supplied arguments, lookup, completed defaults, sends, effects or completed
   child calls.

10. **No generic native request protocol.** B2D3B must not add a
    `NativeComposedInvocationRequest`, global callback registry or equivalent
    second backend interaction language.

11. **No global mutable coordination.** Exact standard-behavior recognition may
    use context-local stable identity, immutable descriptors, inline caches and
    invalidating assumptions, but not a global mutable standard-call registry.

12. **Optimizer locality is preserved.** The stable standard path should expose
    the target Closure's stable RootCallTarget to ordinary Bytecode
    specialization / DirectCallNode machinery whenever current Truffle APIs
    permit it.

13. **No unused-concurrency tax.** Non-suspending ordinary calls allocate no
    continuation/request state merely because Protos supports suspension.
    Suspended memory remains proportional to the genuinely live C-prime chain.

14. **Tooling equivalence is mandatory.** PLAT005/008/013/015 remain
    authoritative. CallTag, source location, stack/backtrace and debugger
    projection must remain observably equivalent to the standard behavior.

15. **Physical-frame elision is conditional on observability.** If B2D3B evidence
    proves that the physical native `Object.call` frame itself carries an
    unavoidable observable responsibility that cannot be projected without
    changing semantics/tooling, implementation stops and PLAT017 is reopened.
    It must not silently weaken this constraint.

16. **Internal compiler transformation remains free.** Truffle may inline,
    split, quicken, outline or otherwise transform the intrinsic/callee
    internally when all semantic, tooling and continuation observations above
    remain unchanged.

17. **No B3/B4/B6 ownership creep.** B2D3B does not migrate Task/Future
    scheduling/cancellation, `ensure`/Error/NLR/loop control, normal source
    execution or replay retirement.

18. **Semantic surprises stop implementation.** If implementation discovers a
    required observable call behavior not already determined by D013/current
    specification, that is not a PLAT017 implementation choice and must cross
    the applicable Dxxx/specification approval gate.

## Tooling and stack-observability gate

Candidate A physically removes one implementation-only native bridge in the
canonical standard-Closure case. That elision is valid only if the same logical
call observation required by existing tooling authority is preserved.

B2D3B validation must therefore include, at minimum:

- ordinary CallTag behavior for the parenthesized invocation;
- source/execution-location identity required by PLAT005/008;
- stack/backtrace behavior when completion or failure crosses the intrinsic;
- debugger scope/value projection under PLAT013/015;
- no duplicate call event caused by projecting both the elided standard bridge
  and target Closure as the same semantic call;
- no missing logical invocation event that the physical native implementation
  previously represented normatively.

The exact internal projection mechanism is implementation-local if it satisfies
those authorities. If it requires a new durable architecture, implementation
stops and opens the next PLAT decision.

## Required B2D3B behavioral evidence

Before B2D3B can close, retained conformance must cover at least:

- Closure target inheriting canonical standard `Object.call`;
- Closure target with a nearer Closure-valued local `call` override;
- Closure target with a nearer non-Closure local `call`;
- standard-call selection through delegation without bypassing nearest-owner
  semantics;
- original target preserved as `this`;
- selected owner preserved as `methodHome` wherever the selected behavior is
  physically invoked;
- normal result and ordinary Error transfer;
- non-local return behavior within already-migrated B2 scope;
- composed child suspension/resume without replay;
- ReturnHome not completed on `ContinuationResult`;
- body calls and already-migrated default-expression calls using the same
  standard-call recognition rule;
- source/CallTag/debugger equivalence required by the tooling gate.

Future/Task suspension itself is still B3-owned. B2D3B may use synthetic or
already-authorized continuation fixtures to prove call-chain composition without
taking B3 ownership.

## Scalability assessment

Candidate A has the smallest retained-state surface:

- ordinary non-suspending standard Closure calls use the same prepared-call
  structures required by the Bytecode backend and do not allocate a generic
  bridge request;
- genuinely suspended calls retain only their live C-prime Bytecode
  continuation/call state;
- there is no O(number of Tasks) global bridge table;
- there is no O(number of suspended calls) additional native-request wrapper
  beyond the call state C-prime already requires;
- unrelated Tasks, Actors and Processes do not synchronize to recognize the
  standard behavior;
- stable inline-cache/assumption machinery scales per hot call site rather than
  through a global dispatch lock; and
- future high-core-count execution composes independent call chains using the
  same mechanism.

This preserves the project rule that programs pay for actual suspension and
actual polymorphism, not for capabilities they do not use.

## Multiprocess and future-backend compatibility

PLAT017 recognizes a **selected behavior**, not a global JVM singleton contract.
A conforming JVM implementation may represent the canonical standard
`Object.call` identity per Context/Prelude/runtime domain, provided the
recognition is stable and invalidates correctly when the selected slot behavior
changes.

No live continuation, native request or standard-call registry is shared across
Processes merely to implement this intrinsic.

A future non-JVM backend may realize the same D013 semantics without any concept
corresponding to PLAT017. This is why the decision is a PLAT decision rather than
normative language semantics.

A future completed Bytecode backend may also fold the intrinsic into a more
uniform ordinary dispatch implementation. That does not supersede PLAT017 if
ordinary D013 selection, observability and continuation behavior remain exactly
equivalent.

## Why this is the most Protos option

The selected architecture preserves one conceptual universe:

- `call` remains an ordinary slot;
- Closures remain ordinary objects subject to the same lookup/shadowing rule;
- the standard implementation is optimized only after that ordinary rule has
  selected it;
- no public or backend-wide "native requests guest execution" institution is
  invented;
- suspension remains the one C-prime mechanism;
- state remains local to actual execution;
- simple non-suspending calls do not pay for concurrency machinery; and
- scaling composes the same call/continuation mechanism rather than changing
  models.

The implementation does contain an identity-sensitive optimization for one
canonical standard behavior, but that identity is semantically invisible and
guarded behind ordinary lookup. This satisfies the project "no pets" rule:
`Object.call` is not granted different Protos semantics; only its exact standard
implementation is optimized.

## PERF006 implementation boundary released by this ratification

PLAT017 ratification releases `PERF006-B2D3B` for mechanical implementation of
the selected standard-call intrinsic and the required equivalence tests.

That work may:

- extend Bytecode call preparation/dispatch with exact selected-standard
  recognition;
- reuse existing `PreparedClosureCall`, RootCallTarget, direct/indirect call and
  C-prime yield/resume machinery;
- add context-local immutable/stable identity or invalidating call-site cache
  machinery required to recognize the canonical standard behavior;
- add focused conformance/tooling tests; and
- mechanically subdivide B2D3B if cost/test isolation requires it.

That work may not select a new semantic rule or another durable continuation,
tooling, global-state or control architecture. Any such discovery stops the
affected slice and crosses the normal Dxxx/PLATxxx approval gate.

## Deliberately deferred

- exact Java class, operation and local-variable names;
- exact inline-cache shape and cache depth;
- whether current Truffle compilation ultimately inlines the standard bridge
  recognition or its target call;
- Task/Future production suspension integration owned by B3;
- cancellation scheduling/lost-wakeup integration owned by B3;
- `ensure`, Error/NLR and loop/control migration owned by B4;
- complete source/instrumentation/debugger closure owned by B5 beyond the
  B2D3B equivalence gate;
- normal source cutover and replay retirement owned by B6;
- persistent optimizing-runtime packaging/distribution closure;
- cross-machine migration/serialization of live continuations; and
- any future general native-wrapper composition mechanism not justified by
  concrete requirements and separately approved.
