# D081 — Literal and value-pattern comparison semantics

Status: **RATIFIED**
Specification revision: **`0.1.398`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative decision record; rationale for normative default ordinary value-pattern matching
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#366`

## Decision

Core v0.1 gives ordinary values a default zero-capture pattern behavior through
the already-ratified D073 matcher authority:

```text
pattern.match(subject)
```

The standard root `Object.match(subject)` behavior performs exactly one ordinary
pattern-side semantic-equality operation:

```text
this == subject
```

and returns that canonical Boolean result unchanged. Canonical `false` is D072
no-match; canonical `true` is D072 success with zero captures.

The default root behavior performs no `===` pre-check, subject-side equality
fallback, hashing, truthiness, coercion, retry, implicit await, Future adoption,
or literal-family special case. Equality Error and non-normal control propagate
under ordinary Protos rules.

Any object may override or shadow `match(subject)` to own richer recognition or
capture semantics. Such an override remains the single D073 matcher authority
and need not make the object's ordinary `==` mean pattern recognition.

D081 does not select concrete source syntax for value patterns.

## Comparative basis

The audit compared Scala, Ruby, Dart, Swift, Python, Haskell, Rust, C#, Java,
Erlang, Elixir, OCaml, F#, Racket, Clojure `core.match`, the TC39 pattern-matching
proposal, TS-Pattern, GNU Smalltalk and Self.

Three especially relevant precedents emerged:

- Scala and Dart show that ordinary equality can be pattern-side
  (`patternValue == subject`);
- Ruby and Swift show the value of a pattern-owned recognition operation distinct
  from ordinary equality; and
- Rust/BEAM/Java/TC39 show the optimization and static-analysis advantages of a
  restricted noncustomizable exact-value relation.

Protos already has the richer pattern-specific operation that Ruby/Swift need:
D073 `match(subject)`. Therefore the selected design composes the two mechanisms
instead of adding another one: inherited `match` defaults to ordinary pattern-side
`==`, while richer patterns override `match`.

The selected option scored 5.0/5.0 for future-option resilience, 4.9/5.0 for
scalability, and 5.0/5.0 for Protos alignment, with 49.4/50 across the full
ten-dimension comparison.

## Why pattern-side equality

`==` is a customizable ordinary message, so dispatch direction is observable.
Pattern-side equality preserves D071/D073 ownership: the pattern decides its
default recognition relation.

The runtime does not attempt both directions. If `pattern == subject` and
`subject == pattern` differ, the former alone defines inherited value-pattern
matching. D081 does not try to repair asymmetric user equality by adding hidden
fallback calls.

Using `===` instead would make ordinary value patterns identity patterns and
would ignore deliberately customized semantic equality for user/domain values.
A separate explicit identity matcher can remain a future library or language
design if concrete need appears.

## Effects and optimization

The exactly-once equality send is observable. An implementation may specialize
standard Number/String/Boolean/null or other proven-standard cases only when the
result is observationally equivalent to the ordinary inherited
`match(subject) -> this == subject` path.

This preserves implementation freedom without letting optimization silently
erase custom equality effects, Error, suspension, or control transfer.

## Strongest argument against

A visually simple literal/value pattern may execute arbitrary user behavior when
its resolved pattern value customizes `==`. Systems such as Rust, BEAM, Java and
the current TC39 proposal deliberately restrict exact-value matching to improve
static exhaustiveness, purity reasoning, and optimization.

That concern is real, but effectful pattern recognition is already part of the
ratified Protos model because `pattern.match(subject)` is ordinary behavior.
D081 therefore adds no second effectful universe; it defines the smallest default
inside the existing one.

If a future language version needs a statically analyzable, side-effect-free
exact-literal category, it can introduce a separately approved explicit pattern
whose `match` has that stronger contract without changing arbitrary ordinary
pattern objects.

## Intentionally deferred

D081 does not select concrete `match`/arm grammar, which source expressions denote
ordinary value patterns, D079 nested-capture composition, named binding syntax,
guards, exhaustivity, sequence/Map pattern semantics, whole-subject alias syntax,
identity-pattern syntax, future Float/NaN-specific pattern semantics, or a
recognition-only matcher fast path.
