# PLAT016 — Bytecode Closure default-parameter execution topology

Status: **RATIFIED**

Nature: durable non-normative Truffle / Bytecode DSL execution-architecture decision

Approved by project owner: **2026-09-10**

GitHub Issue: **#295**

Primary consumer: `PERF006-B2C3B` under GitHub #276 / PERF006-B under #262

Normative effect: **none**. Existing Protos default-parameter semantics remain
authoritative through the existing language/specification decisions and semantic
reference implementation. PLAT016 selects only the Truffle Bytecode backend
topology used to realize those already-selected semantics.

## Problem

The AST backend already defines the required observable behavior:

- parameters bind left-to-right;
- a supplied argument wins and suppresses the corresponding default entirely;
- an omitted default expression executes in the exact invocation activation
  after earlier parameters have been bound;
- defaults may therefore read earlier parameters;
- `args` remains the original supplied argument vector;
- rest remains the already-selected fresh frozen suffix Array; and
- ordinary guest Error behavior is preserved.

PERF006 is migrating Closure execution from AST/replay-oriented machinery to the
PLAT014 C-prime Bytecode DSL continuation backend. The durable platform question
was therefore where arbitrary default-expression execution belongs once defaults
may themselves contain ordinary guest calls/sends and eventually suspension.

The implementation must not solve that question by changing Protos semantics,
replaying completed default effects, adding a permanent interpreter island, or
making each suspended invocation occupy a physical carrier.

## Existing authority and constraints

PLAT014 remains authoritative for cooperative suspension:

- ordinary execution stays optimizer-eligible;
- continuation capture occurs only at genuine suspension;
- each Bytecode root can own a resumable continuation;
- continuation state composes across logical Protos calls without replay; and
- physical carriers are released rather than parked for suspended Tasks.

The current Closure activation ABI also remains authoritative:
`ProtosActivation` is frame argument 0 and owns the invocation's lexical,
receiver, argument, ReturnHome, method-home, module, execution-domain and dynamic
control state.

PLAT005, PLAT013 and PLAT015 remain authoritative for source/instrumentation and
debugger value/scope projection. PLAT016 does not create a second tooling
universe.

## Cross-language Truffle review

The decision was made after an exhaustive review of the maintained Truffle
language implementations listed by GraalVM, with experimental/historical
implementations screened for materially different precedents.

The strongest evidence was:

- **TruffleRuby** — optional argument defaults are ordinary guest AST children
  executed against the same `VirtualFrame` as the invocation. Parameter loading
  is an ordered part of function activation, not a separate semantic function
  per default.
- **GraalJS** — parameter initialization is part of function execution and is
  ordered before/wrapping the body, including generator-specific handling.
  JavaScript's exact default/yield semantics differ from Protos, but the
  activation topology is directly relevant.
- **GraalPy** — current Bytecode DSL execution provides production evidence that
  a guest frame can be captured in `ContinuationResult` and resumed without
  turning the suspended guest computation into a permanently parked Java stack.
  Python's default-value timing differs from Protos, so this is continuation
  evidence rather than default-semantic evidence.
- **Espresso** — normal and continuable method execution treat the method/frame as
  the resumable execution unit, reinforcing activation-sized continuation state.
- **Sulong/LLVM** — function-root construction includes argument-to-frame
  preparation together with the body under one function execution unit.
- **GraalWasm, SimpleLanguage (including Bytecode DSL), TruffleSqueak,
  TruffleSOM/SOMns and Yona** — provide convergent evidence that function/method
  activation is normally the execution/root unit rather than each internal
  syntactic phase.
- **Apple Pkl** — Pkl intentionally does not provide method default parameters,
  so it is not direct evidence for default semantics. Its runtime nevertheless
  reinforces the ordinary root/body activation model and, importantly, the
  design principle that runtime institutions should not be invented where the
  language does not require them.
- **FastR and Enso** — useful counterexamples. Their substantially richer
  promise/thunk/named-argument/currying semantics justify correspondingly richer
  argument machinery. Protos does not adopt that machinery without the language
  semantics that require it.
- **TRegex and grCUDA** — reviewed for completeness but carry near-zero decision
  weight because they do not expose a comparable general-language
  default-parameter activation problem.

No strong maintained implementation was found that models every ordinary default
expression as an independently meaningful function/root solely because it is a
default expression.

## Reviewed alternatives

### A — AST default-expression bridge before Bytecode body

Rejected.

It preserves current semantics cheaply but creates a mixed AST/Bytecode boundary
at the exact suspension boundary PERF006 is eliminating. A suspending default
would depend on evaluator/replay machinery and require another retirement
migration.

### B — one separate Bytecode root / CallTarget per default

Not selected, but retained as a technically viable fallback if a future verified
Bytecode DSL limitation makes the selected topology impossible.

It reuses C-prime root-to-root continuation composition, but fragments one
semantic invocation into O(number of defaults) additional roots, CallTargets,
frames, instrumentation boundaries and continuation layers. The extra entities
have no independent Protos identity.

### C — one Bytecode Closure activation root

**Selected.**

Required binding, omitted-default evaluation, rest binding and body execution
form one ordinary Bytecode flow over the same invocation activation.

A default is guest code executed at a particular point in a Closure invocation;
it is not a distinct semantic Closure merely because it appears in a parameter
declaration.

### D — Java helper / canonical mini-interpreter inside one root

Rejected.

It superficially keeps one outer root but recreates a permanent
interpreter/helper island inside the optimizing backend, complicating source
ownership, instrumentation, continuation behavior and compiler optimization.

## Selected architecture — C

Conceptually:

```text
caller
  |
  v
Closure invocation
  |
  v
+--------------------------------------+
| one Bytecode Closure activation root |
|                                      |
| required argument binding            |
| omitted default #1 guest code        |
| omitted default #2 guest code        |
| rest binding                         |
| ordinary Closure body                |
+--------------------------------------+
  |
  +--> normal result
  |
  +--> genuine yield -> ContinuationResult
```

`ProtosActivation` remains the semantic activation authority and frame argument
0. Bytecode-local representation may additionally cache or quicken values, but
must not create a second semantic parameter environment.

## Durable constraints

1. **One semantic invocation unit.** Binding, defaults, rest and body are phases
   of one Closure activation.

2. **One activation authority.** Earlier parameter visibility, lexical lookup,
   receiver lookup, `args`, ReturnHome, method-home, module state,
   execution-domain state and dynamic control continue to derive from the same
   `ProtosActivation`.

3. **Existing binding semantics are unchanged.** Parameters bind in the already
   defined order; supplied arguments suppress their defaults entirely; omitted
   defaults execute only when required.

4. **Defaults are ordinary guest code.** Once a default form is claimed as
   migrated, it uses the same Bytecode expression/send/call lowering rules as
   equivalent body guest code. There is no hidden AST fallback for unsupported
   arbitrary defaults.

5. **Suspension is ordinary C-prime suspension.** A genuine suspension inside a
   default captures the current Bytecode activation state and composes with
   callers using the PLAT014 continuation mechanism.

6. **No completed-default replay.** Resuming after a later suspension must not
   re-execute already-completed binding steps or default effects.

7. **No Java-stack semantics.** Correctness may not depend on a parked Java
   stack, `ThreadLocal` invocation identity, Java thread affinity or one carrier
   per suspended invocation.

8. **No global continuation/default registry.** State belongs to the active or
   suspended invocation and scales with work that actually exists.

9. **No new guest institution.** A default expression does not acquire
   independent Closure identity, lifecycle, cancellation authority or public
   introspection identity merely to simplify the backend.

10. **No-default Closures pay no material default cost.** The generated path for
    simple Closures may contain the ordinary binding/body work they already need,
    but must not allocate per-default structures when no defaults exist.

11. **Tooling remains expression-local.** Source sections and instrumentation
    tags may identify individual default expressions while the execution unit
    remains one activation root.

12. **Debugger authority is unchanged.** PLAT013/PLAT015 projections continue to
    observe the semantic activation; PLAT016 does not expose backend helper state
    as a second Protos scope.

13. **Internal compiler outlining is allowed when invisible.** A current or
    future backend may outline, inline, split, quicken, specialize or otherwise
    transform internal execution for optimization. Such transformations do not
    reopen PLAT016 if and only if they preserve one semantic Closure activation,
    introduce no new guest identity, preserve source/tooling observations,
    preserve `ProtosActivation` semantics, preserve continuation behavior and
    introduce no replay.

14. **Outlining is not semantic fragmentation.** If an optimizer internally uses
    an auxiliary host CallTarget/root, that helper is an implementation detail,
    not an independently observable default function and not a separately owned
    continuation policy.

15. **PLAT014 remains the continuation authority.** PLAT016 defines where Closure
    parameter/default execution lives; it does not redefine cancellation,
    ensure/unwind, non-local return, Future/Task ownership or continuation
    composition semantics.

16. **Semantic changes stop here.** If implementation discovers that the selected
    topology requires changing observable parameter/default/rest behavior, the
    affected PERF006 slice stops and routes that question through the applicable
    Dxxx/specification approval gate.

## Scalability assessment

The selected topology scales with actual invocation state rather than declared
default count:

- one semantic activation/root execution unit per Closure invocation;
- no O(number of defaults) permanent semantic root/CallTarget family;
- suspended memory proportional to the actual live Bytecode frame/continuation;
- no carrier parking or global registry;
- no synchronization among unrelated Closures, Tasks, Actors or Processes; and
- simple Closures do not pay for unused default machinery.

This is the strongest fit with high-core-count and many-Task execution because
increasing concurrency increases independent invocation state rather than shared
coordination.

## Why this is the most Protos option

The selected architecture keeps one conceptual universe:

- an invocation remains an invocation;
- defaults remain ordinary guest expressions;
- suspension remains the same suspension mechanism used elsewhere;
- no helper Closure/default-function institution is invented;
- state stays local to the activation;
- simple programs pay only for the machinery they use; and
- larger systems scale by composing the same activation/continuation mechanisms.

It therefore directly follows the project principles of mechanisms over
institutions, ordinary things remaining ordinary, pay-only-for-what-you-use,
scale-by-composition and minimizing shared mutable state.

## PERF006 implementation boundary released by this ratification

PLAT016 ratification releases `PERF006-B2C3B` to continue mechanically under the
selected topology. The previously proposed decomposition remains valid as
implementation sequencing, not additional architecture authority:

1. `B2C3B1` — refactor the current body-only Bytecode plan into an
   activation-root seam while preserving required/rest and no-default behavior.
2. `B2C3B2` — migrate literal/lookup defaults, earlier-parameter visibility and
   supplied-argument suppression.
3. `B2C3B3` — extend ordinary default-expression lowering through the call/send
   forms required by normative fixtures and prove suspension/no-replay before
   declaring defaults fully migrated.

Those slices may be further mechanically subdivided if cost or test isolation
requires it. A newly exposed substantive semantic/platform choice still crosses
the normal explicit approval gate.

## Deliberately deferred

- exact generated Bytecode local layout and operation names;
- exact lowering sequence for each future expression/send form;
- Task/Future ownership migration owned by later PERF006-B3 work;
- normal source compiler cutover;
- replay retirement;
- persistent optimizing-runtime packaging/classpath closure;
- debugger features beyond existing PLAT005/013/015 authority;
- distributed migration of live continuations; and
- any optimization-specific internal outlining strategy that remains
  semantically invisible under the constraints above.
