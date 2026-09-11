# D090 — OR / alternative-pattern ordered-choice and capture-interface semantics

Status: **RATIFIED**
Specification revision: **`0.1.403`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record; rationale for normative ordered alternative-pattern semantics
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#377`

## Decision

Core v0.1 standard alternative-pattern semantics use **ordered first-success
choice** over the existing matcher protocol.

Alternatives are attempted in their semantic order. Each attempted alternative
is invoked exactly once through the existing D073 authority:

```text
alternative.match(subject)
```

Every attempted alternative receives the same ordinary subject value. The
alternative composite does not re-evaluate, clone, freeze, snapshot, or
otherwise replace that subject merely because an earlier alternative failed.

Normal results are consumed only through D072:

```text
false        -> this alternative mismatches; try the next alternative
true         -> the alternative composite succeeds immediately with zero captures
[c1, ...]    -> the alternative composite succeeds immediately with those captures
```

An invalid normal D072 result signals ordinary `Error`. Error, non-local
control, cancellation, and explicit suspension propagate normally from the
currently attempted alternative and do not cause a later alternative to be
tried.

The **first successful alternative commits the alternative composite**. No later
alternative is attempted after that success. A later failure outside the
alternative composite, including failure of a future guard or another outer
consumer condition, does not reopen that already-successful alternative
composite or resume it at a later branch.

Effects performed by an alternative before it eventually returns canonical
`false` are ordinary program effects and are not rolled back. A later
alternative therefore observes the ordinary program state that exists after
those effects.

D090 introduces no speculative or parallel branch execution. If the currently
attempted matcher suspends, the alternative composite suspends at that attempt;
it does not start another alternative concurrently.

D072/D083 remain the sole standard runtime capture representation and
composition boundary. D090 introduces no branch tag, named matcher result,
binding Map, `CaptureFrame`, `CaptureSignature`, capture-name registry, mutable
capture sink, rollback log, or second matcher authority.

D088 remains authoritative for source-visible bindings. When an alternative form
feeds one fixed source/arm binding interface, every successful branch must be
projectable onto the same ordered logical D088 arm-binding ABI. Different
branches may obtain those logical bindings from different structural positions;
arbitrary matcher objects are not required to publish capture names or static
capture signatures merely to participate in alternatives.

Arbitrary matchers may retain dynamic capture arity. A consumer intentionally
accepting variable captures may use ordinary Closure rest-parameter semantics.
If a fixed consumer is structurally known to be incompatible, the source form
should be rejected before execution; if incompatibility is only discovered after
recognition succeeds, D088's ordinary callable binding/arity `Error` applies and
does not cause the alternative composite to try another branch.

One D072 capture remains one capture. Array, Map, Closure, Future, remainder, or
other aggregate values are not recursively flattened by D090.

Nested ordered alternatives are associative with respect to their semantic
attempt sequence: grouping does not change the left-to-right sequence of
alternative attempts. Ordered alternative choice is not commutative.

Implementations may flatten nested standard alternative nodes, inline standard
matchers, eliminate unobservable intermediate carrier allocations, or build
decision structures only when observable behavior remains identical: matcher
authority, attempted-call order/count, first-success commitment, D072 result
semantics, effects, Error/control/cancellation/suspension behavior, and D088
binding-interface behavior must all be preserved.

## Comparative basis

The audit compared Rust, OCaml, F#, Python, Dart, Swift, Racket, Haskell/GHC
OrPatterns, Scala 3, Ruby, C#, LPeg, PCRE2 branch-reset alternatives, and
Tree-sitter query alternatives.

Rust, OCaml, Python, Dart, Swift, and Racket provide strong positive evidence
that alternatives sharing one body require a stable binding interface.
Haskell/GHC, Scala, Ruby, and C# provide useful negative evidence: when a stable
interface cannot be guaranteed, mature designs prefer restricting bindings over
exposing branch-dependent partial binding state.

LPeg is the closest runtime analogue to the Protos matcher boundary: first-class
patterns compose with ordered choice and captures without requiring a
compiler-owned closed pattern universe. Protos deliberately strengthens the
effect/control contract beyond PEG-style freedom: attempted alternatives are
semantically ordered, no speculative branch execution is permitted, and
ordinary Error/control/cancellation/suspension remain observable.

PCRE2 and Tree-sitter demonstrate richer capture/branch metadata mechanisms, but
that additional carrier state is unnecessary for the already-ratified
D072/D083/D088 architecture.

The selected option scored 5.0/5.0 on future-option resilience, 4.9/5.0 on
scalability and 5.0/5.0 on Protos alignment, with 49/50 across the complete
ten-dimension comparison. Confidence is HIGH.

## Why compatibility belongs at the D088 boundary

Rust/ML-style systems can require every OR branch to expose one statically known
binding set because the compiler owns their pattern universe.

Protos deliberately allows arbitrary ordinary matcher objects whose D072 capture
arity may be dynamic. Requiring every matcher to publish capture names or a
static signature would make one source-binding convenience a mandatory runtime
institution.

D088 already owns the fixed source/arm interface. Requiring compatibility there
keeps the generic matcher protocol small while still guaranteeing that one fixed
arm never receives a branch-dependent logical binding layout.

## Effects, commitment, and downstream failure

Ordered alternative matching is not transactional.

A failing earlier alternative may already have performed effects. Those effects
remain. Error or non-local control is not converted to mismatch. Suspension does
not invite another branch to race ahead.

Once an alternative succeeds, the alternative composite itself has succeeded.
Later arm invocation, future guard evaluation, or another outer consumer step is
outside that selection. Failure there cannot reinterpret the prior success as
mismatch merely to obtain another alternative.

This keeps recognition local and prevents OR from becoming an implicit general
backtracking/rollback engine.

## Scalability and optimization

For first success at the `k`th attempted branch, ordered alternative dispatch
requires `O(k)` matcher attempts and no semantic per-branch binding environment
or rollback log.

A simple program pays only for alternatives it actually attempts. Large
alternative sets remain eligible for implementation specialization when the
optimizer can prove the same observable ordered matcher behavior. In
particular, a decision tree, literal dispatch table, or other shortcut may not
skip observable user matcher sends or reorder effectful alternatives merely
because it is faster.

The contract introduces no global registry, shared mutable capture-signature
state, Actor/Process coordination, or backend-specific runtime object. It
therefore remains compatible with many Tasks/Actors/Processes, alternate
runtimes, Native Image, and future Bytecode DSL lowering.

## Strongest counterargument

D090 intentionally permits two levels of knowledge: arbitrary matcher objects
may have dynamic positional captures, while a fixed source form that shares one
arm requires a stable logical D088 interface.

That is more nuanced than the simple Rust/OCaml rule that every OR branch binds
the same statically known names. The complexity is deliberate: D072/D083 already
preserve arbitrary matcher extensibility, and D088 already places source-visible
names at the arm/source boundary. Moving the fixed-interface rule into every
matcher would simplify OR only by making the entire matching protocol heavier.

## Regret and escape path

Future first-class pattern reflection, serialization, or advanced tooling may
want to inspect binding/capture descriptors before executing a matcher.

If concrete ecosystem evidence requires that capability, Protos can add optional
compile-time/debug metadata or an explicit pattern-introspection protocol. Such
metadata can describe source names and branch projections while leaving
D072/D083/D088/D090 runtime semantics unchanged.

The reverse migration would be harder: making runtime capture signatures or
branch tags mandatory now would create library/ecosystem dependencies that
would later be difficult to remove.

## Intentionally deferred

D090 does not select:

- concrete `|`, `or`, `OR`, `match`, `case`, arm, default, or binder syntax;
- guard semantics except that downstream guard failure cannot reopen an already
  successful D090 alternative composite;
- exhaustivity or redundancy analysis;
- repetition, optional, find, subsequence, or general backtracking patterns;
- whole-subject alias syntax;
- first-class Pattern reflection or mandatory capture-signature metadata;
- recognition-only matcher fast paths; or
- parser/runtime implementation of the future alternative-pattern surface.
