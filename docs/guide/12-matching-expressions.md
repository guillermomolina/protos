# Protocol-First Matching and Case Selection

> **Status:** Non-normative programming guide
>
> **Primary normative owners:** `spec/semantics/MATCHING.md` and
> `spec/PROTOS_GRAMMAR.md`
>
> **Current implementation track:** `I041 — D131 protocol-first matching implementation`

Matching in current Protos is deliberately small: **matching is an ordinary
protocol, not a dedicated language construct**.

The core idea is:

```text
pattern.match(subject)
```

The matcher is the receiver. The subject is the argument.

There is no postfix `subject match { ... }` expression, no `case` grammar, no
matching-only binders, wildcard, guards, OR-pattern institution, exact/remainder
pattern syntax, or `captures(...)` source form in Core v0.1.

The words `match`, `case`, `when`, `exact`, and `captures` are ordinary names.

## The root matcher protocol

Every ordinary object inherits the standard root behavior:

```text
Object.match(subject) -> this == subject
```

`Object.match` performs exactly one ordinary equality send with the matcher as
the equality receiver and returns that result unchanged.

For ordinary values this means:

```protos
42.match(42)
"ready".match(status)
```

uses the same customizable equality model as ordinary `==`. Matching does not
introduce a second equality relation.

A domain object may define or override `match(subject)` like any other method:

```protos
Positive: {
    match: (subject) => subject > 0
}

Positive.match(12)
```

No registry, type declaration, pattern class, or compiler hook is required.

## Matcher results

A matching consumer recognizes exactly these normal outcomes:

```text
false                     mismatch
true                      success with zero captures
[one, or, more, values]   success with positional captures
```

The capture carrier must be a **non-empty standard Array**.

An empty Array is not another spelling for zero-capture success. Other normally
returned values such as `null`, Numbers, Strings, arbitrary objects, or Futures
are invalid matcher outcomes when consumed by structural matching or `caseOf`
and signal ordinary Error.

Captured Arrays remain one captured value; capture composition does not
recursively flatten nested collections.

## `Any` and `Capture`

Core initially provides exactly two standard helper matchers:

```protos
Any.match(subject)
Capture.match(subject)
```

`Any.match(subject)` always returns canonical `true`.
`Capture.match(subject)` returns `[subject]`, preserving the exact subject
reference.

`Any` and `Capture` are ordinary standard objects, not keywords.

## Structural Array matching

A standard Array is itself a structural matcher:

```protos
matcher: [1, Capture, Any]
subject: [1, "payload", 99]

result: matcher.match(subject)
```

`Array.match` is exact-length matching.

For one attempt, Protos shallowly observes both matcher and subject Arrays before
invoking any child matcher. Reached child matchers run exactly once from left to
right:

```text
matcherElement.match(subjectElement)
```

A child `false` fails immediately. `true` contributes no capture. A non-empty
standard Array contributes its outer elements to the positional capture
sequence.

If no child contributes captures, the result is canonical `true`; otherwise the
result is one new standard Array containing captures in child order.

Core v0.1 defines no Array remainder matcher.

## Structural Map matching

A normal standard Map is also a structural matcher:

```protos
matcher: Map()
matcher["kind"] = "data"
matcher["payload"] = Capture

subject: Map()
subject["kind"] = "data"
subject["payload"] = 42
subject["extra"] = "ignored"

result: matcher.match(subject)
```

`Map.match` is **open/subset matching**. Every matcher association must resolve
and match, while unrelated subject associations are ignored.

For one attempt, Protos establishes stable shallow observations of matcher
requirements and subject associations. Requirements are processed in matcher
insertion order.

Key selection uses the ordinary normal-Map law:

1. evaluate the query key's current `hash`;
2. consider same-hash subject associations in subject insertion order;
3. send query-side `queryKey == storedRepresentativeKey`;
4. select the first canonical-`true` result.

Key objects are not themselves interpreted as matchers during association
lookup.

All required subject associations are resolved and their mapped-value references
fixed **before the first nested value matcher runs**. Mapped-value matchers then
run in matcher insertion order.

Missing required associations yield canonical `false`. Child matcher outcomes
compose captures through the same `false` / `true` / non-empty-Array protocol.

Core v0.1 defines no exact-Map mode and no Map-remainder capture.

## Multi-way selection with `caseOf`

Multi-way selection is an ordinary message:

```protos
value.caseOf(cases)
```

`cases` is a normal standard Map whose keys are matchers and whose values are
callables:

```protos
cases: Map()

cases["ready"] = () => "can run"
cases[Capture] = (other) => other

status.caseOf(cases)
```

The cases Map is shallowly observed in insertion order when the attempt starts.
Later mutation does not rewrite that attempt.

Each reached matcher is asked once:

```text
matcher.match(value)
```

The result controls selection:

```text
false        continue
true         action()
[captures]   action(...captures)
```

The first success commits. Unreached matchers and actions are not eagerly
validated or invoked.

The selected callable's result is returned unchanged, including `null`. If no
case succeeds, `caseOf` signals one fresh ordinary Error.

Because the carrier is an ordinary normal Map, its normal unique-key,
hash/equality, and insertion-order semantics are part of the model.

## Guard-like behavior needs no special syntax

Core v0.1 has no matching-specific `when` syntax. A matcher simply implements the
predicate it needs:

```protos
Positive: {
    match: (subject) => subject > 0
}
```

A matcher that needs to capture after checking a condition can return a
non-empty Array on success and `false` otherwise. The protocol remains
`matcher.match(subject)`; no separate guard phase is required.

## Matching remains ordinary Protos

Useful boundaries:

- matcher authority is on the receiver of `match`;
- equality remains ordinary `==`;
- Array and Map structural behavior belongs to those standard collection
  protocols;
- `caseOf` is an ordinary Object message using an ordinary Map carrier;
- Errors, effects, control transfer, and suspension follow ordinary execution;
- removed pre-D131 pattern syntax is not dormant compatibility syntax.

## Removed pre-D131 surface

Older development revisions experimented with a dedicated matching language,
including:

```text
subject match { ... }
case ...
when ...
@name
_
Array/Map pattern grammar
Array/Map remainders
exact Map patterns
OR patterns
captures(...)
```

Those forms are intentionally **not** part of current Core v0.1. D131/I041
removed them rather than retaining aliases or compatibility syntax.

Future syntactic sugar or higher-level matcher combinators can be considered by
separate explicit decisions without changing the protocol foundation.

## Practical checklist

1. Identify the matcher object.
2. Send `matcher.match(subject)`.
3. Expect only `false`, `true`, or a non-empty standard Array from matcher
   outcomes consumed by matching.
4. Use `Capture` for one positional capture.
5. Use `Any` for unconditional zero-capture success.
6. Use Arrays for exact-length structural matching.
7. Use Maps for open/subset structural matching.
8. Use `value.caseOf(cases)` for ordered multi-way selection.
9. Remember that Map keys still follow ordinary Map `hash` / `==` semantics.
10. Treat `caseOf` no-selection as Error unless a case explicitly succeeds.

## Implementation-backed examples

Representative retained evidence includes:

- [`ProtosObjectMatchProtocolTest`](../../src/test/java/com/guillermomolina/protos/execution/ProtosObjectMatchProtocolTest.java);
- [`ProtosArrayMatchProtocolTest`](../../src/test/java/com/guillermomolina/protos/execution/ProtosArrayMatchProtocolTest.java);
- [`ProtosMapMatchProtocolTest`](../../src/test/java/com/guillermomolina/protos/execution/ProtosMapMatchProtocolTest.java);
- [`ProtosCaseOfProtocolTest`](../../src/test/java/com/guillermomolina/protos/execution/ProtosCaseOfProtocolTest.java);
- [`protos/tests/conformance/matching/`](../../protos/tests/conformance/matching/);
- [`ProtosParserMatchTest`](../../src/test/java/com/guillermomolina/protos/parser/ProtosParserMatchTest.java), which retains the rejection regression for the removed dedicated syntax.

These files are implementation evidence, not a second specification. If they
disagree with the normative specification, the specification remains
authoritative.
