# D078 — Complete named structural view and remainder-capture contract

Status: **RATIFIED**
Specification revision: **`0.1.396`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative decision record; rationale for normative open/subset named-object matching
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#362`

## Decision

Generic named object structural matching is **open/subset**.

Only logical field names explicitly requested through the D075
`subject.deconstructFields(...names)` operation participate in a generic named
structural attempt. Additional logical fields exposed by the subject do not cause
mismatch, are not enumerated or materialized merely because matching occurs, and
are not captured implicitly.

Core v0.1 does **not** standardize generic named-object `**rest` / remainder
capture or complete logical-field enumeration. D075 `deconstructFields` remains
the single generic subject-side named-projection authority and gains no full-view
mode, sentinel, or special request.

No required `deconstructAllFields`, `deconstructFieldNames`,
`deconstructFieldsAndRest`, `DeconstructionView`, raw-slot enumeration, delegated
lookup enumeration, indexed-state reinterpretation, or host-reflection fallback
is introduced.

A future whole-subject alias may be considered separately and can bind the
original subject without implying complete logical-field enumeration.
Collection-specific Map/sequence remainder semantics remain a separate decision.

## Comparative basis

The audit compared Ruby, Python, ECMAScript, Racket, Erlang/Elixir, Rust, Dart,
F#, C#, Java, Kotlin, Haskell, Clojure, Self, GNU Smalltalk, Io, GraphQL,
Protobuf FieldMask, MongoDB and BigQuery-style projection semantics.

The strongest durable pattern for record/object-like structures is open/subset
named matching without manufacturing a captured remainder. Erlang/Elixir, F#,
C#, Rust and Dart all provide evidence that omitted/unmentioned named structure
can simply remain outside the match. Capturable remainder is strongest on values
that are already intrinsically enumerable mappings/sequences.

Clojure provides an important lower-cost alternative for future ergonomics:
selected components may coexist with a binding of the original whole subject,
without constructing `allFields - selectedFields`. GraphQL and FieldMask further
support explicit selective projection as the scalable default; FieldMask also
illustrates the schema-evolution risk of implicit all-fields behavior.

The selected option scored 5.0/5.0 for future-option resilience, 5.0/5.0 for
scalability, and 5.0/5.0 for Protos alignment in the final candidate comparison.

## Why no complete-view protocol yet

A universal complete logical view would create a new public schema institution
that Protos does not otherwise need. It could expose expensive or virtual fields,
make newly added fields observable to old patterns, create aggregate allocation
and transfer consequences, and establish a second semantic authority capable of
disagreeing with D075 selective projection.

Deferring complete-view support is highly reversible: all selector/protocol
space remains available if concrete schema-preserving transformation, forwarding,
AST, proxy, or migration use cases later justify it. Standardizing such a
protocol now would be substantially harder to retract.

## Strongest argument against

Generic transformations can benefit from “match known fields and preserve every
unknown current/future field.” If that becomes common in real Protos programs,
automatic remainder capture could materially improve ergonomics. D078 therefore
does not prohibit a future complete-view facility; it requires that capability to
earn its own explicit decision from concrete evidence.

## Intentionally deferred

D078 does not select concrete `match`/arm syntax, literal/equality pattern rules,
guards, exhaustivity, positional subject deconstruction, sequence or Map
remainder semantics, nested capture flattening, whole-subject alias syntax, named
binding syntax, or a recognition-only matcher fast path.
