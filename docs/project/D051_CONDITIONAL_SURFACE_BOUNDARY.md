# D051 — Conditional surface syntax boundary

Status: **RATIFIED**
Specification revision: **`0.1.392`**
Explicit project-owner approval: **2026-09-09**
Nature: normative Core v0.1 syntax/compatibility decision
Primary normative owner: `spec/PROTOS_GRAMMAR.md`
Implementation consumer: **none required**

## Decision

Core v0.1 has no dedicated `if` / `else` conditional syntax. `if` and `else`
remain ordinary identifiers and are not added to the reserved-word set.

The already-valid surface:

```protos
if(condition) {
    body()
}
```

retains its ordinary meaning: an ordinary lookup/call of the identifier `if`
with `condition` as an ordinary argument and the parameterless trailing Closure
as the final argument. No implementation or tool may reinterpret that source as
a built-in conditional merely from the spelling `if`.

`else` likewise has no special continuation role. Core v0.1 defines no
`if (...) { ... } else { ... }` pairing.

Canonical conditional execution remains the strict ordinary Boolean protocol:

```text
ifTrue(block)
ifFalse(block)
ifTrueIfFalse(trueBlock, falseBlock)
```

as completed by D050. D051 introduces no truthiness, hidden branch primitive, or
second conditional semantics.

## Why this option

The review compared:

- retaining only the Boolean protocol;
- reserving `if` / `else` as keywords;
- contextual recognition of the spelling `if`;
- a punctuation ternary such as `?:`;
- an ordinary prelude-level `if(...)` helper; and
- solving future Closure-heavy call ergonomics through a general mechanism
  rather than a conditional-specific parser exception.

Smalltalk and Self demonstrate that Boolean/block protocols can carry
conditional control without requiring a separate conditional semantic universe.
Kotlin and Rust demonstrate the ergonomic value of dedicated `if` expressions,
but those languages establish `if` as syntax from the outset rather than
retroactively stealing an existing ordinary-call shape. Io demonstrates
message-oriented conditional construction but couples it to different
truthiness choices that Protos explicitly rejects.

The selected boundary preserves Protos's existing ordinary-name and
ordinary-call rules, avoids a category 0-to-1 keyword/syntax expansion, keeps
parser/refactoring/tooling behavior independent of identifier spelling, and
allows implementations to optimize the ordinary Boolean protocol without
changing observable semantics.

## Compatibility invariant

For Core v0.1, renaming an ordinary binding to or from `if` or `else` must not by
itself change the grammatical category of an expression.

In particular:

```protos
if: (value, block) => { block() }
if(123) { 42 }
```

is valid ordinary Protos and returns `42`; the non-Boolean first argument proves
that the spelling is not silently lowered to the standard Boolean conditional
protocol.

A future language version may reconsider conditional surface syntax only through
a separate explicit design decision that addresses source-version compatibility.
It must not silently reinterpret Core v0.1 ordinary-call source.

## Future / scale result

- No parser branch, AST category, runtime primitive, reserved token, dispatch
  rule, or scheduler behavior is added.
- Tooling, formatting, refactoring and independent parsers need no
  spelling-sensitive exception for `if` or `else`.
- Optimization remains free to specialize/in-line the ordinary Boolean protocol
  when observable behavior is preserved.
- Future improvements to multi-Closure call ergonomics or general syntax
  extension remain separate designs and are not approved by D051.
- `while`, `return`, pattern matching, ternary syntax, macros and other
  control-flow surface facilities remain independent decisions.

## Implementation state

The current parser already conforms to this decision because identifiers feed the
ordinary postfix-call grammar and trailing Closures are ordinary final call
arguments. D051 therefore requires no production implementation and allocates no
new `Ixxx`.

Two Protos-source conformance cases are added to freeze the observable
compatibility boundary: one proves `if(condition) {...}` is an ordinary call
even with a non-Boolean first argument, and one proves `else` remains a bare
ordinary identifier.
