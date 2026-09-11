# D074 — Structural deconstruction view architecture

Status: **RATIFIED**
Specification revision: **`0.1.394`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative decision record; rationale for normative structural-projection architecture
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#355`

## Decision

Generic structural matching uses a **named, selective logical projection** from
the subject.

A generic structural matcher requests the logical field names it actually needs.
The subject controls which logical names it exposes and the values denoted by
those names. The protocol must not force a complete logical-view enumeration when
only a subset is requested.

Structural matching does not implicitly enumerate local slots, delegated members,
prototype ancestry, indexed contents, or host/JVM representation. Physical field
reordering is therefore not part of the matching contract.

Ordinary objects acquire no universal positional product layout. Domain-specific
positional extraction can still be implemented by a pattern's ordinary
`match(subject)` method. Array/Map/Bytes/indexed contents retain their existing
collection/indexing semantics rather than becoming object fields.

## Why named selective projection

The audit compared Ruby `deconstruct`/`deconstruct_keys`, Python `__match_args__`,
C# `Deconstruct`/property patterns, Kotlin positional and name-based destructuring,
C++ structured bindings, Haskell views/pattern synonyms, Scala/F# extractors, and
compiler-owned structural systems.

Ruby provides strong evidence for selective named projection, while Kotlin's
recent name-based evolution highlights the compatibility cost of permanent
positional ordering for record-like objects. Protos has no intrinsic record
layout, so imposing a universal order would create a new institution solely for
matching.

The selected architecture preserves encapsulation, computes only requested
logical components, and keeps positional/domain extractors optional and local.

## Intentionally deferred

D074 does not select the exact selector name, request carrier, result carrier or
ordering, no-view versus missing-field failure contract, complete-view/remainder
capability, positional subject-deconstruction protocol, nested capture flattening,
named binding syntax, literal/equality patterns, guards, exhaustivity, arms, or
concrete `match` grammar. Those exact projection mechanics belong to D075.
