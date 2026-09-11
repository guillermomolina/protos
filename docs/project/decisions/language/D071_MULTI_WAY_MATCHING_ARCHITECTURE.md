# D071 — Multi-way matching semantic architecture

Status: **RATIFIED**
Specification revision: **`0.1.394`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative decision record; rationale for normative matching architecture
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#350`

## Decision

Protos selects **pattern-owned recognition plus subject-owned explicit logical
deconstruction** as the architecture for extensible multi-way matching.

Recognition policy belongs to a pattern object. Structural exposure belongs to
the subject and must cross an explicit logical-view boundary. Matching must not
silently reinterpret local slots, delegation ancestry, semantic-family ancestry,
indexed state, or host/JVM representation as structural fields.

Custom pattern families remain locally extensible: there is no global matcher
registry and subjects do not need to know every pattern family.

At the future matching-construct boundary, the subject expression is evaluated
once, arms are considered in source order, first success wins, and the selected
branch executes under ordinary Closure/callable semantics. Matching introduces
neither truthiness nor implicit await. Ordinary Error and non-local control
behavior propagate under existing rules.

## Why this architecture

The comparative review covered closed compiler-owned matching, subject-owned
matching, pattern-owned extractors, mediator/registry models, and two-sided
composition. Closed ADT/record systems such as Rust provide powerful exhaustivity
and optimization but depend on a structural institution that Protos deliberately
does not have. Scala/F# extractors, Raku custom matching, Ruby structural views,
and prototype/message-oriented systems show that open matching can remain local
and protocol-based.

The selected split gives each side one responsibility without requiring a global
institution or representation leakage.

## Intentionally deferred

D071 did not select the matcher result carrier, final selector spelling,
structural-projection carrier, literal/equality patterns, guards, exhaustivity,
standard pattern taxonomy, capture flattening, or concrete syntax. D072-D074
subsequently resolve bounded portions of that space.
