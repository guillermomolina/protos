# D086 — Map/keyed pattern semantics and remainder contract

Status: **RATIFIED**
Specification revision: **`0.1.401`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record; rationale for normative standard Map keyed-pattern semantics
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#371`

## Decision

Core v0.1 standard keyed-pattern semantics are specialized to subjects that own
**normal standard `Map` keyed-entry state**.

The pattern establishes one stable shallow logical snapshot of the subject Map's
current associations before query-key `hash` / `==` behavior or nested
mapped-value matcher behavior runs. Snapshot associations preserve the stored
representative key reference, mapped value reference, recorded key hash, and
relative insertion order.

Each keyed requirement supplies an ordinary query key value and one
mapped-value child pattern. The key is resolved against the stable snapshot
using the existing normal Map search relation:

```text
query key current hash
        ↓
snapshot entries with equal recorded hash
        ↓
candidates in snapshot insertion order
        ↓
queryKey == storedRepresentativeKey
```

No second matching-specific key relation is introduced.

All keyed requirements resolve before mapped-value child matching. Missing keys
cause ordinary mismatch while present `null` or other sentinel-like values
remain ordinary present mapped values.

Repeated/equivalent keyed requirements are allowed as repeated constraints.
Several requirements may select the same subject association; residue accounting
counts that association once.

The default residue policy is open/subset. Exact matching is a `require-empty`
residue policy. Remainder matching supplies a **fresh frozen normal standard
`Map`** containing exactly unselected snapshot associations in original relative
insertion order.

Remainder construction preserves representative key references, mapped value
references, recorded hashes, and relative insertion order without sending
ordinary `hash`, `==`, `atPut`, or iteration messages merely to reconstruct the
residue.

`IdentityMap`, arbitrary mapping/indexing duck types, arbitrary key-pattern
entry search, defaults/optional missing-key behavior, generic keyed-projection
protocols, concrete syntax, bindings, guards and exhaustivity remain deferred.

## Comparative basis

The audit compared Python, Ruby, Erlang, Elixir, Racket, Dart, Clojure, Scala,
Rust, Java, C#, GNU Smalltalk, Self, Io, ts-pattern, JSONPath and jq.

The strongest convergent evidence was:

- mapping patterns are normally open/subset rather than exact by default;
- exactness and remainder are best treated as policies over unselected
  associations rather than as a second key-lookup law;
- mapping keys should use the subject collection's established key relation;
- arbitrary key-pattern scanning is a different search/quantification feature;
- absence must stay distinct from a stored null-like ordinary value; and
- mutable-map remainder capture has a real materialization/snapshot cost that
  should be paid only when requested.

Ruby and Racket were particularly useful for the explicit open/exact/remainder
dimension. Erlang and Racket support repeated equivalent key requirements as
conjunction rather than requiring duplicate-key rejection. Racket's deprecation
of its older arbitrary key/value pair pattern also reinforces the distinction
between keyed lookup and general entry-pattern search.

The selected option scored 5.0/5.0 for future-option resilience, 4.8/5.0 for
scalability and 5.0/5.0 for Protos alignment, with 49.2/50 across the full
ten-dimension comparison.

## Why normal standard Map only

Protos already distinguishes semantic Map keyed state from the broad ordinary
`at` protocol. Treating any `at`/`containsKey` object as a standard Map-pattern
subject would collapse a distinction that indexed-access semantics explicitly
preserve.

Pattern-owned `match(subject)` already gives libraries a generic extension path.
A shared opt-in keyed observation protocol remains additive if multiple
independent map-like families later justify one.

`IdentityMap` is intentionally excluded because it uses a distinct key relation
based on semantic identity. Adding it later can be explicit; making the same
standard pattern silently change key equality according to subject family now
would widen D086 unnecessarily.

## Why stable association observation

Map key `hash` and `==` remain ordinary behavior and can have observable
effects. Nested mapped-value child matchers are ordinary effectful matchers too.

Resolving a Map pattern against live mutable state one key at a time could
therefore combine associations from different Map states or make exactness and
remainder depend on earlier child effects.

A stable logical association snapshot gives open, exact and remainder modes one
coherent structural observation. It follows existing Protos shallow-snapshot
discipline while leaving implementations free to use versioned or persistent
storage rather than an eager public copy.

## Why preserve recorded hashes in the remainder

Normal Map search records key hashes as part of keyed-entry state and later
searches compare a query's current hash with those recorded hashes.

Reconstructing a remainder by ordinary reinsertion would recompute current key
hashes and could invoke user behavior. For a mutable key whose hash-relevant
state changed after insertion, that would silently create a different keyed
state.

The remainder therefore preserves the observed associations—including their
recorded hashes—without new user hash/equality sends merely because matching
materializes residue.

## Strongest argument against

The stable logical snapshot is semantically stronger than a minimal selective
live lookup. A simple implementation may retain/copy more association state than
a one-key open match would otherwise need.

The stronger rule is selected because it prevents query-key effects or earlier
child effects from changing the structural universe observed by later keys,
exactness, or remainder. Implementations remain free to acquire that snapshot
cheaply through versioning, structural sharing, copy-on-write state or another
observationally equivalent representation.

## Regret and escape path

If future Protos develops many independent Map-like families—persistent maps,
B-tree maps, foreign dictionaries, database-row maps, distributed snapshots—an
ordinary standard-Map-only pattern may become too narrow.

The escape path is additive: a later Dxxx can introduce an explicit opt-in
keyed-observation protocol or a pattern-owned adapter/extractor architecture
based on concrete ecosystem evidence. Normal Map can participate without
changing D086's visible key, residue, effect or capture semantics.

The reverse migration is harder: if arbitrary `at`/`containsKey` objects
participate implicitly from the start, narrowing that accidental membership
later is breaking.

## Intentionally deferred

D086 does not select `IdentityMap` standard keyed-pattern semantics, arbitrary
key-pattern scanning, optional/defaulted missing keys, Map-entry repetition or
quantification, a generic mapping/keyed-projection protocol, concrete
Map-pattern/exactness/remainder syntax, which source expressions form query
keys, query-key source evaluation timing, source binding spelling, guards,
exhaustivity, identity-pattern syntax, or recognition-only matcher fast paths.
