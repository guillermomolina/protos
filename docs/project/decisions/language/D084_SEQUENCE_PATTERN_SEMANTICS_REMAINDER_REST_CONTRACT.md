# D084 — Sequence pattern semantics and remainder/rest contract

Status: **RATIFIED**
Specification revision: **`0.1.400`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record; rationale for normative standard Array sequence-pattern semantics
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#369`

## Decision

Core v0.1 standard sequence-pattern semantics are specialized to subjects that
own **standard Array indexed state**.

This is deliberately narrower than a generic indexing or iteration protocol.
Defining/inheriting `at`, `size`, `each`, an iterator, or Array-like methods does
not make an arbitrary object a standard sequence-pattern subject. Delegation to
Array does not confer standard Array indexed state.

A fixed standard sequence pattern is exact-length by default.

A standard sequence pattern may contain at most one semantic remainder
component. The remainder denotes the contiguous unmatched middle segment between
fixed prefix and suffix components and may contain zero elements. More than one
remainder would require a separately designed distribution/greediness/backtracking
policy and is not part of D084.

Before invoking any child matcher, the standard sequence matcher establishes a
shallow logical observation of all subject Array element references semantically
needed by the attempt. Fixed prefix/suffix references are observed before child
effects. If a remainder aggregate is needed, its middle references are also
observed before child matching. A discard-only remainder does not require the
middle to be traversed or materialized.

This standard Array observation is semantic and does not send ordinary
user-visible `size`, `at`, `each`, iterator, or deconstruction messages.

When the unmatched remainder must be supplied as one ordinary value, that value
is a **fresh frozen standard Array** containing the unmatched shallow element
references in order. An empty remainder produces a fresh frozen empty Array.
The result is shallow: referenced mutable element objects are not cloned or
frozen.

Child matching then proceeds in deterministic sequence-component order and
composes under D083. A remainder Array captured as one value remains one capture
and is never recursively flattened.

## Comparative basis

The audit compared Rust, Python, Ruby, Dart, Erlang, Elixir, Haskell, OCaml, F#,
Scala 3, Racket, Clojure, C#, Java and Swift, with Self, Io and Smalltalk used as
message/prototype philosophy checks.

Strong convergent evidence favored:

- exact fixed sequence shape by default;
- one explicit rest/remainder capability;
- keeping sequence eligibility semantically explicit rather than treating every
  iterable/indexable value as equivalent;
- avoiding destructive iterator semantics in ordinary refutable matching; and
- representing a requested finite remainder as an ordinary aggregate value.

Python's design rationale was particularly relevant: arbitrary iterators were
rejected because failed refutable matching cannot cleanly undo consumption, and
`len` plus subscripting was rejected as the sequence criterion because mappings
can satisfy overlapping low-level protocols. This directly parallels Protos'
broad `at` protocol.

Rust/Dart/F#/OCaml/BEAM reinforce exact fixed shape plus explicit remainder.
Scala and Ruby demonstrate that richer domain sequence extraction can remain
pattern/extractor-owned instead of forcing a universal subject protocol.

The selected option scored 5.0/5.0 for future-option resilience, 4.9/5.0 for
scalability, and 5.0/5.0 for Protos alignment, with 49.4/50 across the full
ten-dimension comparison.

## Why standard Array only

Core already has a precise semantic standard Array receiver domain. Standard
`Array.size`, indexed state, iteration snapshot semantics and call-spread
snapshot semantics distinguish standard Array state from arbitrary objects that
happen to expose similarly named ordinary messages.

Using that existing boundary avoids creating a new `Sequence` institution or
conflating protocol indexability with finite dense positional structure.

String, Bytes and Map each have materially different semantic domains. Iterators,
generators and streams add destructive-consumption, rollback, suffix-buffering,
termination and suspension questions. D084 leaves them separate.

## Why pre-child shallow observation

D073 allows child matchers to execute arbitrary ordinary Protos behavior. If the
sequence matcher read Array positions only immediately before each child call,
an earlier child could mutate the original Array and change the value later
children observe in the same pattern attempt.

Core already uses shallow logical Array snapshots before effectful consumers in
standard `Array.each`, and call spread captures standard Array element references
without invoking user-defined `size`, `at`, `each`, iterator, or conversion
behavior.

D084 follows that established semantic discipline while remaining selective:
irrelevant middle elements of a discard-only remainder do not need to be copied
or traversed.

## Why a fresh frozen remainder Array

A finite rest value must survive later mutation of the original Array's indexed
positions while remaining an ordinary Protos value. Protos already uses fresh
frozen standard Arrays for callable rest parameters.

A borrowed slice/view would reduce copying but would cross a qualitative 0-to-1
boundary by requiring view identity, lifetime, aliasing, mutation and transfer
semantics. D084 does not introduce that institution solely for matching.

Implementations remain free to share backing storage or use copy-on-write
internally if fresh Array identity, frozen behavior and observable independence
are preserved.

## Strongest argument against

The selected standard contract is intentionally narrow. User-defined persistent
vectors, deques, foreign arrays, ropes, tensors, memory-mapped vectors and other
finite ordered collections do not automatically receive the standard sequence
pattern.

Python/C# demonstrate the ergonomic value of broader interoperability.

The reason not to pay that cost now is that Protos has no generic Sequence
semantic family and its existing `at` protocol intentionally covers more than
dense finite positional collections. Broadening through low-level structural
duck typing would be easy to add and difficult to remove once user objects began
matching accidentally.

## Regret and escape path

If future Protos ecosystems develop several independent finite sequence families
that all need interoperable standard sequence matching, Array-only eligibility
may become too narrow.

The escape path is additive: a later Dxxx can introduce an explicit opt-in
sequence observation protocol or another standard pattern-owned extractor
architecture based on real ecosystem evidence. Standard Array can participate
without changing D084's already-visible exact/rest behavior.

The reverse migration is harder: if Core first makes every `size`/`at` object a
sequence pattern subject, narrowing that accidental participation later is
breaking.

## Intentionally deferred

D084 does not select Map/keyed patterns, Map remainder capture, String/Bytes
patterns, repetition/optional patterns, sequence find/subsequence search,
iterator/generator/stream matching, generic sequence interoperability, concrete
match/sequence/rest syntax, capture-variable spelling, duplicate binding rules,
guards, exhaustivity, identity-pattern syntax, or recognition-only matcher fast
paths.
