# D072 — Matcher outcome and capture carrier contract

Status: **RATIFIED**
Specification revision: **`0.1.394`**
Explicit project-owner approval: **2026-09-11**
Nature: non-normative decision record; rationale for normative matcher outcome semantics
Primary normative owner: `spec/semantics/MATCHING.md`
Decision issue: GitHub `#351`

## Decision

One extraction-capable matcher attempt uses exactly this normal outcome universe:

```text
false        -> no match
true         -> successful match with zero captures
[x, ...]     -> successful match with one-or-more positional captures
```

The Booleans are the canonical Protos values. The capture carrier is a non-empty
standard Array. Empty outer Array and every other normal result shape are invalid
matcher outcomes at the standard consuming boundary. Error and non-normal control
transfer propagate normally instead of being encoded in the carrier.

Captured values remain ordinary values. A one-element capture may therefore be
`[null]`, `[false]`, `[true]`, or `[[]]`; the latter represents one captured empty
Array rather than zero captures.

## Why this carrier

The audit compared a dedicated MatchResult family, `null | Array`, `false |
Array`, exact `false | true | non-empty Array`, dual recognition/extraction modes,
callbacks, and mutable sinks. Scala, F#, TC39 and production matcher APIs provide
strong evidence that zero-capture recognition should not be forced through an
allocated wrapper.

The selected carrier uses only existing Protos values, preserves strict Boolean
semantics, allocates nothing new for failure or zero-capture success, and pays for
an Array only when captures actually exist.

## Intentionally deferred

D072 does not define matcher selector names/modes, named captures, nested-capture
flattening, structural-deconstruction request/result forms, guards, exhaustivity,
or syntax.
