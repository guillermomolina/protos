# D083 — Nested capture composition and binding contract

Status: **RATIFIED**
Specification revision: **`0.1.399`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record; rationale for normative standard composite capture composition
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#363`

## Decision

Standard composite patterns compose immediate child matcher outcomes only through
the public D072 result returned by the D073 authority:

```text
child.match(childSubject)
```

For one standard composite attempt:

```text
false        -> immediate composite mismatch; do not invoke later children
true         -> zero contributed captures
[c1, ...]    -> append exactly the OUTER carrier elements, in order
```

The composition boundary is exactly one D072 carrier level. Captured values are
ordinary values and are never recursively flattened merely because a captured
value is an Array or another collection. Thus `[[1, 2]]` means one captured
Array, and a parent may expose `[a, [1, 2], b]` but never reinterpret that as
`[a, 1, 2, b]`.

Children run in deterministic semantic child order; ordered child lists run
left-to-right. Each attempted child is invoked exactly once. Valid child carrier
Arrays are shallowly consumed before the next child so later carrier mutation
cannot rewrite the capture references already observed by the current attempt.

All-success with zero total captures returns canonical `true`. All-success with
one or more total captures returns one standard non-empty Array containing the
concatenated capture sequence. Invalid D072 results signal ordinary `Error`.
Error/non-local control/cancellation/explicit suspension propagate normally and
prior effects are not rolled back.

Patterns that want several values to constitute one capture publish an explicit
ordinary aggregate value as that one D072 capture. Rest, repetition, optional,
whole-subject and similar pattern-family semantics remain separate decisions.

## Comparative basis

The audit covered Rust, Python, Scala, Haskell, OCaml, F#, Erlang, Elixir,
Racket, Clojure, Ruby, Dart, Swift, Java, C#, Kotlin, LPeg, Peggy and Parsy,
with Self, Io and Smalltalk used as object-model philosophy checks.

The strongest recurring architecture separated:

1. nested recognition/decomposition structure;
2. a flat public capture/binding interface; and
3. explicit aggregate values for rest/repetition/whole-subject behavior.

LPeg provided the closest direct analogue to D072: ordinary composition can
publish a sequence of capture values while explicit grouping turns several
semantic values into one aggregate capture. Mainstream pattern systems likewise
usually make nested structural recognition produce a flat arm binding surface,
with rest/as/repetition aggregation explicit.

The selected option scored 5.0/5.0 for future-option resilience, 4.9/5.0 for
scalability and 5.0/5.0 for Protos alignment, with 49.3/50 across the full
ten-dimension comparison.

## Why one carrier level

D072 already makes each element of a non-empty matcher-result Array one ordinary
captured value and specifically makes `[[]]` an unambiguous one-capture result.
Recursive Array flattening would therefore contradict an existing observable
semantic boundary.

Conversely, preserving an implicit hierarchical capture tree would require a new
tag/wrapper institution because an ordinary Array is already a legal captured
value. It would also expose pattern-tree refactoring through the public result
shape and allocate structure proportional to pattern topology instead of actual
captures.

Immediate-child carrier concatenation preserves child abstraction. A parent need
not know whether a child is a literal pattern, a standard composite, or an
arbitrary user matcher; it consumes only the child's public D072 result.

## Effects and optimization

The exactly-once ordered child calls are observable. A mismatch at a later child
does not roll back effects of earlier attempts, and later children are not
executed after normal mismatch.

The shallow carrier-consumption rule prevents mutation of a returned carrier
Array from retroactively changing capture positions already observed by the
parent while preserving identity of the captured values themselves.

Implementations remain free to fuse standard composite layers, preallocate frame
storage, or eliminate intermediate Arrays when observational behavior is
unchanged. No semantic mutable capture sink or `CaptureFrame` is introduced.

## Strongest argument against

Arbitrary ordinary matchers may have subject-dependent dynamic capture arity.
A future source form with a statically fixed list of binding names cannot always
know in advance how many capture positions such a matcher will publish.

That pressure does not justify imposing mandatory capture-signature metadata on
every matcher now. Future fixed-binding syntax can restrict itself to standard
patterns with a known or validated binding interface, provide a dynamic aggregate
form, or add an optional signature facility under another explicit decision if
concrete ecosystem evidence requires it.

## Regret and escape path

A future tooling/metaprogramming requirement might need first-class provenance
mapping each capture to the exact nested pattern node that produced it. D083's
flat public carrier intentionally does not contain that provenance.

That requirement remains additive: standard syntax may carry debug/source
metadata, an optional pattern-introspection protocol may be introduced, or a
pattern may explicitly publish a structured ordinary capture value. None of
those require changing D072 or the standard composite value boundary selected
here.

The reverse migration would be harder: if Core first standardized a hierarchical
capture carrier and libraries depended on its topology, flattening that public
tree later would break compatibility.

## Intentionally deferred

D083 does not select concrete `match`/arm grammar, source binding spelling,
duplicate binding-name rules, alternative/or-pattern syntax or exact signature
validation mechanics, guards, exhaustivity, collection-specific Map/sequence
remainder semantics, repetition/optional/rest pattern semantics, whole-subject
alias syntax, identity-pattern syntax, or a recognition-only matcher fast path.
