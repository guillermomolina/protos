# D080 — Positional subject-deconstruction architecture

Status: **RATIFIED**
Specification revision: **`0.1.397`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative decision record; rationale for normative positional-deconstruction boundary
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#365`

## Decision

Core v0.1 does **not** require arbitrary objects to expose a generic positional
logical-deconstruction protocol.

D075 `subject.deconstructFields(...names)` remains the generic subject-side
structural protocol for record/object-like matching. Positional/domain-specific
extraction remains pattern-owned through the already-ratified
`pattern.match(subject)` authority and D072 positional-capture carrier.

Intrinsically ordered values remain governed by their own collection/indexing
or future sequence/tuple-like semantics rather than being reclassified as
generic object structure.

No required `deconstruct()`, `deconstructPositions`, `componentN`, ordered
positional-schema metadata, dedicated positional-view object, or
request-polymorphic deconstruction selector is introduced. No positional order
is inferred from slots, delegation, declaration order, D075 name order, indexed
state, prototype ancestry, or host representation.

## Comparative basis

The audit compared Ruby, Python, C#, Kotlin, Scala 3, Haskell, F#, OCaml, Rust,
Swift, Erlang, Elixir, Dart, Racket, C++26, Java 26, Self and Io.

The strongest cross-system distinction is between values whose order/arity is
already intrinsic and arbitrary domain objects. Tuples, tuple structs, enum/DU
payloads, records with positional components and sequences can support positional
patterns scalably because order already exists independently of matching.
By contrast, Ruby `deconstruct`, C# `Deconstruct`, Kotlin `componentN`, Python
`__match_args__`, and C++ tuple-like customization add a durable compatibility
contract when ordinary/domain objects opt into position.

Scala extractors, F# active patterns, Haskell ViewPatterns/pattern synonyms and
Racket `app`/match expanders demonstrate the alternative already selected by
Protos: custom/domain extraction can belong to the pattern/view rather than a
universal subject protocol. Self and Io further support keeping arbitrary
prototype objects from acquiring an implicit tuple/product identity.

The selected no-generic-protocol option scored 5.0/5.0 for future-option
resilience, 5.0/5.0 for scalability, and 5.0/5.0 for Protos alignment. The
strongest future fallback was selective batched positional projection, which
remains available for a later explicit decision if real ecosystem evidence
justifies it.

## Why no generic positional protocol

A second subject-side positional view would create a durable order/arity contract
alongside D075 named projection. Protos would then need either coherence rules
between the two views or would permit generic named and positional syntax to
observe unrelated structures of the same object.

Pattern-owned extraction already provides positional captures without imposing
that universal contract. It also allows several legitimate domain views of one
object—for example Cartesian versus polar extraction—without declaring either
to be the object's one canonical positional schema.

Deferring a generic subject-side positional protocol is highly reversible.
Adding one later is an additive decision; removing or reordering a published
positional schema after libraries depend on it would be substantially more
expensive.

## Strongest argument against

A mature Protos ecosystem may eventually contain many independently authored,
small, stable tuple-like domain objects whose libraries want one interoperable
positional convention without sharing pattern objects. In that future, an
opt-in generic positional protocol could improve ergonomics and interoperability.

D080 therefore does not prohibit such a future protocol. It requires that the
capability be justified separately by concrete ecosystem evidence rather than
making every arbitrary object pay the conceptual compatibility cost now.

## Intentionally deferred

D080 does not select D079 nested-capture composition, concrete match/arm grammar,
named binding syntax, literal/equality patterns, guards, exhaustivity,
Map/sequence remainder semantics, future sequence/tuple pattern syntax, or a
recognition-only matcher fast path.
