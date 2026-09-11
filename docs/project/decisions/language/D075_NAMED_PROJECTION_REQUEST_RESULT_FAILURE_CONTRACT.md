# D075 — Named projection request/result/failure contract

Status: **RATIFIED**
Specification revision: **`0.1.395`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative decision record; rationale for normative named structural-projection protocol
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#356`

## Decision

Generic named structural projection uses `subject.deconstructFields(...names)` with zero or more pairwise-distinct semantic String names. Ordinary argument order defines response correspondence; ordinary call spread handles dynamic name lists.

Exact normal results are `false` (no named structural view), `null` (view exists but a requested logical name is unavailable), `true` (successful zero-name projection), or a non-empty standard Array containing exactly one projected value per requested name in request order. Wrong requests and invalid result shapes are ordinary Error, while projected `null`/`false` and arbitrary values remain unambiguous inside a successful Array.

The standard root behavior is `Object.deconstructFields(...names) -> false` for a valid request. One structural attempt invokes projection exactly once, validates it before nested subpatterns, and captures a shallow ordered snapshot of successful Array element references before those subpatterns execute.

## Comparative basis

The exhaustive audit covered 23 language/tool precedents across dynamic/prototype, static, functional, pattern-matching, keyed-lookup, and production selective-projection families. Ruby `deconstruct_keys` is the closest direct precedent; Self/Smalltalk are closest philosophically; Erlang/Elixir support subset-by-name matching; Go/Rust/.NET reinforce presence/value separation; and GraphQL, Protobuf FieldMask, and MongoDB demonstrate selective-projection scalability.

Protos can simplify Ruby's request-Array/result-Hash shape because variadic calls and call spread already provide dynamic batching, while the matching consumer retains the requested names. The selected variadic/aligned-Array option scored 9.8/10 future resilience, 9.6/10 scalability, and 9.9/10 Protos alignment in the final comparison.

## Strongest argument against

A keyed Map result is more self-describing if this API later becomes general introspection/serialization/RPC projection consumed independently from its request. D075 intentionally optimizes for the concrete matching consumer instead of pre-spending that extra institution; a future independent projection API remains possible.

## Intentionally deferred

D075 does not select complete-view / `**rest`-like semantics, positional subject deconstruction, sequence/map pattern semantics, nested capture flattening, named binding syntax, literal/equality patterns, guards, exhaustivity, case/arm/default syntax, concrete `match` grammar, or a recognition-only matcher fast path.
