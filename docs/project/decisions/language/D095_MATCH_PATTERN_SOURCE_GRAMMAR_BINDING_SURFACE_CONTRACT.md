# D095 — Match-pattern source grammar and binding surface contract

Status: **RATIFIED**
Specification revision: **`0.1.406`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative language-decision record
Primary normative owners: `spec/PROTOS_GRAMMAR.md` and `spec/semantics/MATCHING.md`
Decision issue: GitHub `#389`

## Decision

Core v0.1 selects D095-A-prime: ordinary matcher/value source forms stay ordinary
values; source capture is explicit.

Central rule:

```text
ordinary value/reference/call/member syntax  -> matcher/value pattern
@name                                        -> new source binder
_                                            -> irrefutable discard
```

A bare identifier in a match pattern never silently becomes a new binding.

Ratified source families:

```protos
case 42 => ...
case knownPattern => ...
case patterns.vip => ...
case patternFactory() => ...

case @value => ...
case _ => ...
case @whole: [1, @x, ...@rest] => ...

case %{
    "name": @name,
    "age": @age
} => ...

case exact %{
    "name": @name
} => ...

case [1, @x] | [2, @x] => ...

case opaqueMatcher captures(left, right) => ...
case dynamicMatcher captures(first, ...rest) => ...
```

D095 completes D093's `MATCH-PATTERN` parameter and activates the normative
matching-expression grammar. Parser/runtime implementation remains separate.

### Ordinary matcher/value pattern

Direct matcher/value atoms are rooted in a literal, ordinary identifier, or one
of `this`, `context`, `args`, with ordinary member/call/index postfix operations
permitted. The resulting value is evaluated exactly once when that pattern
position is attempted, then invoked only through D073 `pattern.match(subject)`.

Object expressions, Closure expressions, arbitrary binary expressions,
slot-creation/assignment, non-local return, and arbitrary parenthesized ordinary
expressions are not direct pattern atoms in Core v0.1.

### Binders and discard

`@name` is irrefutable and contributes exactly one capture containing the current
subsubject. `_` is irrefutable and contributes zero captures.

`@name: pattern` captures the current subsubject first, then applies `pattern` to
the same subsubject. Its alias capture precedes nested captures. D088 still
commits source-visible bindings only after complete success.

### Array pattern

`[p1, p2]` is exclusively the D084 standard Array pattern and is exact-length by
default. At most one contextual remainder is permitted:

```protos
[prefix, ..., suffix]
[prefix, ...@rest, suffix]
[prefix, ...remainderPattern, suffix]
```

Bare `...` discards the remainder. A nested remainder pattern receives the one
fresh frozen standard Array remainder defined by D084.

### Map pattern

`%{ keyExpr: pattern }` is exclusively the D086 normal standard Map pattern and
is open/subset by default. `exact %{ ... }` selects require-empty residue
semantics.

`...nested` receives the one fresh frozen normal standard Map remainder defined
by D086; bare `...` discards it.

Map query-key expressions are evaluated exactly once left-to-right after D086's
stable association snapshot is established and before mapped-value child
matching. All required keys are resolved against that same snapshot before child
matchers run.

No bare-name-to-String key shorthand is introduced.

### OR

`p1 | p2` is D090 ordered OR and has the lowest precedence in pattern grammar.
For a fixed source interface, all successful alternatives must expose the same
ordered logical binder-name sequence.

### Opaque matcher capture interface

An ordinary matcher/value pattern may add consumer-side binding names:

```protos
matcher captures(a, b)
matcher captures(first, ...rest)
```

`captures` is contextual source metadata only. It does not send a message, does
not add matcher-side names/arity metadata, and does not change D072/D088.

### Linearity

Fixed binder names are unique and linear. `[@x, @x]` is invalid and never means
equality/rebinding/shadowing.

### Contextual syntax

`exact` and `captures` are not globally reserved. `_` remains an ordinary
identifier outside pattern position. `@`, `|`, `%{` and `...` gain these meanings
only inside `MATCH-PATTERN`; ordinary operator/expression grammar elsewhere is
unchanged.

## Comparative result

Selected A-prime scored:

- correctness: 5.0/5;
- Protos alignment: 5.0/5;
- future-option resilience: 5.0/5;
- scalability: 5.0/5;
- conceptual simplicity: 4.6/5;
- portability/implementation freedom: 5.0/5;
- runtime/resource cost: 5.0/5;
- failure/operability: 5.0/5;
- reversibility/migration: 4.8/5;
- evidence maturity/risk: 4.9/5;
- total: **49.3/50**, confidence HIGH.

The decisive reason is that Protos's open matcher abstraction is already an
ordinary object implementing `match(subject)`. Making a bare identifier into a
binder would make ordinary matcher references awkward and require a pinning or
qualification institution merely to refer to existing matcher values.

## Scalability

No Pattern registry, CaptureSignature, BindingMap, PatternFrame, extractor table,
closed Pattern hierarchy, shared Actor/Process state, or backend-specific runtime
institution is introduced. Standard patterns can be compiled into direct control
flow/decision structures only when D071-D095 observations remain preserved.

## Strongest counterargument

`@name` is noisier than a bare-binding syntax. The extra character buys the
language-wide invariant that a bare name keeps its ordinary lookup/value meaning,
including inside matching, which is especially important for D073/D081.

## Intentionally deferred

D095 does not define optional/repetition/search/subsequence/regex/stream patterns,
generic sequence deconstruction, generic object positional/slot deconstruction,
String/Bytes pattern families, IdentityMap matching, exhaustivity/redundancy,
MatchFailure subtype, recognition fast paths, first-class Pattern reflection, or
parser/runtime implementation.
