# D096 — Match exhaustiveness, redundancy and static no-match analysis contract

Status: **RATIFIED**
Specification revision: **`0.1.407`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record; rationale for normative Core v0.1 match coverage analysis
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#390`

## Decision

Core v0.1 selects **D096-C-prime: sound three-state static coverage analysis with
fail-to-`UNKNOWN`**.

The conceptual analysis result is:

```text
PROVEN_EXHAUSTIVE
PROVEN_NON_EXHAUSTIVE
UNKNOWN
```

A matching expression is legal without a static proof of exhaustiveness.
`UNKNOWN` and `PROVEN_NON_EXHAUSTIVE` are not language-validity failures. D092's
runtime terminal no-selection rule remains authoritative: if actual ordered
matching selects no arm, one fresh ordinary `Error` is signaled.

`UNKNOWN` means exactly that the compiler cannot soundly prove either totality or
a no-selection witness with the static facts available to it. It must never be
silently reclassified as `PROVEN_NON_EXHAUSTIVE`.

## Static proof boundary

Coverage analysis may use only:

- source syntax whose matching meaning is fixed by the language specification;
- compiler-known standard pattern semantics ratified by D084, D086, D090,
  D092 and D095; and
- future static facts introduced by separately ratified language facilities.

Coverage analysis must not:

- execute `match(subject)`;
- execute or reason by assumption about arbitrary `==`, `hash`, query-key
  expressions or guards;
- inspect arbitrary matcher object slots or delegation state;
- infer a coverage contract from matcher names, prototypes, method source or
  current implementation shape;
- reinterpret D095 `captures(...)` as coverage metadata; or
- require a runtime `Pattern`, `CoverageSignature`, `CaptureSignature`,
  registry, reflection protocol or closed matcher hierarchy.

Ordinary matcher/value source forms are therefore coverage-opaque in Core v0.1:

```protos
case knownPattern => ...
case patterns.foo => ...
case makePattern() => ...
case matcher captures(a, b) => ...
```

They may still execute normally under D073; opacity is only a static-analysis
property.

## Stable syntactic irrefutability

Core v0.1 statically recognizes universal ordinary no-mismatch behavior for the
D095 irrefutable source forms:

```protos
_
@name
```

Parenthesized irrefutable patterns remain irrefutable.

An alias is irrefutable exactly when its nested pattern is irrefutable:

```protos
@whole: _
@whole: @value
```

A D090 OR pattern is irrefutable when an alternative reachable under D090's
ordered semantics is itself syntactically irrefutable. For example:

```protos
opaqueMatcher | _
```

cannot finish by ordinary mismatch. An earlier opaque matcher may still propagate
Error, control, cancellation or suspension; those outcomes remain observable and
are not converted into coverage failure.

An **unguarded** universally irrefutable arm proves that normal ordered arm search
cannot reach a later arm through mismatch.

## Guards

Every explicit D092 guard is coverage-opaque for positive totality and subsumption
proofs in Core v0.1.

A guarded arm therefore does not establish exhaustiveness merely because its
pattern is irrefutable:

```protos
case _ when predicate() => ...
```

The guard may return canonical `false`, and may also produce ordinary effects,
Error, non-local control, cancellation or explicit suspension.

Coverage analysis must not evaluate, constant-fold by semantic assumption, or
otherwise treat an arbitrary guard as permanently `true` for language-level
exhaustiveness or unreachability.

## Stable structural errors

Core v0.1 defines a deliberately small source-validity error set whose truth is
fixed by ratified syntax rather than analyzer sophistication.

A source arm after an unguarded universally irrefutable arm is invalid because it
can never be attempted through normal ordered matching:

```protos
subject match {
    case _ => first
    case something => unreachable
}
```

Likewise, within one D090 OR pattern, an alternative that follows an already
reachable syntactically universal-irrefutable alternative is invalid because it
can never be attempted:

```protos
case _ | laterMatcher => ...
```

These structural errors do not expand merely because a future compiler learns a
more sophisticated coverage proof.

## Richer redundancy and non-exhaustiveness diagnostics

A compiler may perform stronger sound usefulness/subsumption analysis over the
language-owned analyzable pattern subset.

When such analysis proves a later arm or alternative redundant beyond the fixed
structural rules above, the result is a warning/lint rather than a Core source
error.

Likewise, `PROVEN_NON_EXHAUSTIVE` may produce a warning/lint and should, when
practical, report a sound witness or uncovered class. It is not a compilation
error because D092 deliberately defines runtime no-selection semantics.

`UNKNOWN` by itself must not be reported as "non-exhaustive".

Implementations and tooling may offer separately configured stricter lint modes,
but those modes do not redefine Core v0.1 source validity.

## Standard Array coverage facts

D084/D095 standard Array patterns may contribute only sound language-owned shape
facts.

An analyzer may reason about:

- standard Array eligibility as a distinct domain;
- exact fixed lengths;
- one-remainder minimum-length shape;
- child positions whose nested pattern is statically understood; and
- a remainder whose nested pattern is syntactically irrefutable.

For example, the D095 shape:

```protos
case [...] => ...
```

may cover all **eligible standard Arrays**, but it does not by itself cover every
Protos value.

If a nested child matcher is opaque, coverage of the corresponding structural
subspace becomes `UNKNOWN` rather than assumed true or false.

## Standard Map coverage facts

D086/D095 standard Map patterns may likewise contribute conservative
language-owned facts.

Because `%{}` is open/subset and has no key requirements:

```protos
case %{} => ...
```

covers every eligible normal standard `Map`.

Because `exact %{}` requires empty residue:

```protos
case exact %{} => ...
```

covers only eligible empty normal standard Maps.

Key-sensitive coverage remains opaque whenever proof would require reasoning
about query-key expression evaluation, current key hash, ordinary `==`, mutable
key state or arbitrary mapped-value child matchers.

No source key expression is executed by coverage analysis.

## Ordinary value/equality patterns

D081 ordinary value patterns are not algebraic constants for D096 purposes.

Repeated source appearance does not permit deduplication or subsumption by
itself:

```protos
case matcher => ...
case matcher => ...
```

Each source occurrence is evaluated at its own attempt point and may resolve to
state-sensitive ordinary behavior. The selected matcher may invoke effectful
`match` or inherited pattern-side `==`.

A future separately ratified pure/compiler-known literal-pattern category may
provide stronger static facts without changing D081.

## Open delegation world

Current delegation/prototype relationships never constitute a closed coverage
universe.

The compiler must not infer that all currently known objects delegating from a
prototype are all values that can ever participate in that family.

A future enum/sealed/closed-domain facility may feed separately ratified static
facts into the D096 analysis lattice. That does not change arbitrary ordinary
matcher semantics.

## OR coverage

D090 alternatives contribute a coverage union only for portions that the analyzer
understands soundly.

An opaque alternative does not denote an empty set and does not permit an
invented closed-world approximation. Its unexplained coverage contribution causes
the relevant proof to remain `UNKNOWN` unless another independently sufficient
fact, such as a later reachable `_`, establishes totality.

Coverage reasoning must not alter D090 attempt order or first-success commitment.

## Bounded analysis complexity

Coverage/usefulness analysis is explicitly resource-bounded.

An implementation may use pattern matrices, symbolic models, decision structures
or another sound algorithm for the analyzable subset. If its configured semantic
analysis budget is exhausted:

```text
analysis budget exhausted
        ↓
UNKNOWN
```

The implementation must not reject otherwise valid source solely because
exhaustiveness/redundancy analysis became too expensive.

An implementation may emit an informational diagnostic that analysis precision
was reduced.

## Runtime and optimization boundary

Coverage analysis does not authorize any change to D071-D095 observable runtime
behavior.

In particular it never authorizes reordering, duplicating, merging, speculatively
executing or eliminating observable arbitrary matcher/guard calls merely because
their source forms appear redundant.

A `PROVEN_EXHAUSTIVE` proof may justify eliminating the final D092 terminal
no-selection branch only when the proof is stable under every runtime/open-world
fact relied upon by generated code.

Proof from a final unguarded syntactically irrefutable D095 pattern is stable.

A future proof based on cross-module closed-domain information should retain a
defensive runtime fallback unless the relevant versioning/closure contract makes
staleness impossible.

## Comparative basis

The audit compared Rust, Swift, Java, Kotlin, Dart, C#, Scala 3, OCaml, Haskell
/GHC, F#, Python, Ruby, Erlang, Elixir, Racket, Clojure `core.match`,
TypeScript/`ts-pattern`, the TC39 pattern-matching proposal and
Maranget-style pattern-matrix usefulness/exhaustiveness analysis.

Closed-domain languages provide strong evidence for mandatory totality when the
compiler owns a finite pattern algebra and subject taxonomy. That architecture is
not Core v0.1 Protos: D073 admits arbitrary matcher objects, D081 ordinary equality
may be effectful, D092 guards are ordinary effectful Protos expressions, and D095
deliberately avoids mandatory matcher signatures/reflection.

The strongest directly relevant precedents are:

- OCaml/F#/Scala for warning-oriented partial-match analysis with runtime failure;
- GHC and C# for explicit analysis-complexity limits/degradation;
- Python for syntax-stable rejection after an irrefutable branch;
- Ruby/BEAM/Racket for runtime no-clause behavior in open/dynamic matching; and
- Clojure/Maranget for pattern-matrix analysis restricted to compiler-known
  pattern algebras.

## Comparative scoring

Selected C-prime:

| Dimension | Score |
| --- | ---: |
| Correctness / invariant preservation | 5.0 |
| Protos alignment | 5.0 |
| Future-option resilience | 5.0 |
| Scalability | 5.0 |
| Conceptual simplicity | 4.8 |
| Portability / implementation freedom | 5.0 |
| Runtime / resource cost | 5.0 |
| Failure / operability | 5.0 |
| Reversibility / migration cost | 5.0 |
| Evidence maturity / implementation risk | 4.9 |
| **Total** | **49.7 / 50** |

Headline scores:

- **future-option resilience:** 5.0 / 5;
- **scalability:** 5.0 / 5;
- **Protos alignment:** 5.0 / 5;
- **confidence:** HIGH.

## Strongest counterargument

Rust, Swift, Java, Kotlin and Dart provide stronger compile-time safety when the
subject domain is statically closed.

If Protos had a closed algebraic/nominal value universe, mandatory exhaustiveness
would be preferable. Core v0.1 instead deliberately supports arbitrary ordinary
matcher objects. Requiring global static totality would therefore either be
unsound or collapse in practice into a mandatory catch-all convention.

## Regret and escape path

If future users require strict totality, an opt-in compiler/lint mode can require
`PROVEN_EXHAUSTIVE` at selected boundaries without changing Core semantics.

If Protos later gains a genuine enum/sealed-domain facility, that facility can
supply closed-domain facts to the same tri-state proof lattice.

If ecosystem evidence later justifies explicit static descriptors for selected
custom matcher families, those descriptors can be designed as an optional
compile-time facility by a separate decision. D096 does not tax every matcher
with mandatory metadata today.

The reverse migration would be harder: mandatory catch-alls, a closed Pattern
hierarchy, or mandatory coverage metadata would permanently shape source and APIs
around institutions the current open matcher semantics do not require.

## Intentionally deferred

D096 does not define:

- a strict-totality compiler mode or annotation syntax;
- enum/sealed/closed-domain language facilities;
- static coverage descriptors for arbitrary user matchers;
- dedicated `MatchFailure` Error taxonomy;
- optional/repetition/search/subsequence/regex/stream pattern families;
- generic deconstruction;
- concrete parser/runtime implementation of matching; or
- an optimizer transformation that changes D071-D095 observations.
