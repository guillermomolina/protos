# D104 — TOML 1.1 leap-second representation and validation contract

Status: **RATIFIED — Candidate B′ selected**
Explicit project-owner approval: **2026-09-12**
Nature: non-normative public Standard Library semantic-decision record
Decision issue: GitHub `#423`
Primary consumer: `LIB010` / GitHub `#418`
Primary public contract owner: `docs/project/work/LIB010/LIB010_TOML_DESIGN.md`

## Trigger

LIB010-0 selected strict public TOML 1.1 for `std:toml/TOML`. LIB010-A then
published TOML-specific ordinary temporal records with `second` constrained to
`0..59`.

TOML 1.1 follows RFC 3339's temporal grammar, where `time-second` admits
`00..60` subject to leap-second rules. This exposed a substantive semantic gap:
the public TOML representation could not preserve one format-level temporal
component admitted by the selected dialect.

The parser slice could not resolve this locally. Rejecting `:60`, normalizing it,
consulting a leap-second schedule, introducing a special temporal node, or
blocking on a general datetime subsystem would each change the already-selected
public semantic/round-trip contract.

## Decision

Protos selects **Candidate B′ — preserve `second = 60` as TOML semantic data
without making the TOML layer responsible for proving a real UTC leap event**.

The three time-bearing TOML semantic records use:

```text
localTime.second       = ordinary Integer in 0..60
localDateTime.second   = ordinary Integer in 0..60
offsetDateTime.second  = ordinary Integer in 0..60
```

`localDate` is unaffected.

For a syntactically and otherwise semantically valid TOML temporal value whose
second component is `60`:

```text
TOML.parse(...)
    -> the corresponding TOML temporal node with second = 60
```

and a valid semantic TOML temporal node with `second = 60` must be encodable as
a TOML temporal spelling that preserves that component:

```text
TOML.encode(...)
    -> ...:60...
```

This is a **TOML representation contract**, not an assertion that a corresponding
historical or announced UTC leap-second event actually exists.

## Responsibility boundary

`std:toml/TOML` owns format-level TOML syntax and semantic data.

It does **not** own:

- an IERS leap-second schedule;
- UTC/TAI conversion;
- system tzdb;
- historical or future leap-event validation;
- clock authority;
- current-time authority;
- locale or operating-system time policy;
- host/JVM/.NET/Go datetime objects;
- a process-global leap-second registry;
- a general Protos datetime/time-scale subsystem.

A future explicit time/date facility may validate or convert a TOML temporal
record using an explicitly supplied time-scale/leap-second authority. Such a
facility must not retroactively redefine whether the TOML semantic record can
represent `second = 60`.

This distinction is especially important for `localTime` and `localDateTime`.
Those TOML categories deliberately omit enough instant information that proving a
particular real UTC leap event is not generally possible from the TOML value
alone.

## Omitted seconds

TOML 1.1 permits seconds to be omitted from a partial time. At the semantic TOML
layer, an omitted seconds component denotes `second = 0`.

Whether the source explicitly contained `:00` is source-representation data and
is therefore outside the semantic `TOML` model. A future source-preserving
`std:toml/Document` layer may retain that distinction.

## Prior-art findings

The audit deliberately separated three questions often conflated by host
libraries:

1. can the TOML representation preserve a `60` second component?
2. can the host's ordinary datetime value type represent it?
3. can the implementation prove that it corresponds to a real UTC leap event?

### TOML 1.1 / RFC 3339

TOML 1.1 adopts the RFC 3339-style temporal grammar including a `00..60` seconds
domain subject to leap-second rules. Real-event validation is a time-scale
question involving external leap-second knowledge, not merely token recognition.

### `toml-test`

The retained conformance corpus strongly covers invalid temporal ranges such as
`:61`, but does not currently provide a decisive valid `:60` case. Passing the
suite therefore does not by itself choose the public representation policy.

### Rust `toml` / `toml_datetime`

Rust supplies the strongest direct precedent for the selected contract.
`toml_datetime::Time` is a TOML-specific value and permits a second component up
to `60`. It preserves the TOML representation independently from general-purpose
host datetime semantics.

### Tombi

Tombi likewise accepts second `60` in its TOML validation layer. As an
editor/linter/tooling-oriented TOML implementation, it reinforces that a
format-specific representation benefits from preserving the format's component
domain rather than inheriting a host datetime restriction.

### pelletier/go-toml v2

Current go-toml code documents deliberate rejection of leap seconds because
Go's `time.Time` cannot represent them without rolling/normalizing. Its retained
tests explicitly note both that TOML's grammar admits `:60` and that `toml-test`
does not pin it as a valid conformance case.

This is useful negative evidence for Protos: a host value limitation is a valid
reason for a Go library to compromise, but Protos' TOML-specific ordinary data
record has no corresponding limitation.

### BurntSushi/toml

BurntSushi/toml also builds temporal values through Go `time.Time` machinery.
Its architecture demonstrates the same host-type coupling risk.

### Python `tomllib`

Python maps TOML temporal values to the standard `datetime` family. Python's
ordinary datetime/time model assumes seconds `0..59` and does not provide a
general leap-second civil-time representation. This gives ergonomic host-native
values but constrains exact TOML format fidelity.

### Java TomlJ / `java.time`

TomlJ maps TOML temporals to `java.time` classes such as `LocalTime` and
`OffsetDateTime`; its local-time visitor explicitly validates seconds as
`00..59`.

Java's `DateTimeFormatter` exposes a different compromise for parsing instants:
it can normalize a leap second while retaining separate parsed-leap-second
metadata. This is good evidence for separating representation from later time
interpretation, but Protos does not need a side flag because its TOML record can
store `second = 60` directly.

### .NET Tomlyn

Tomlyn relies substantially on `DateTime`/`DateTimeOffset` parsing and the host
platform's representable range. It demonstrates the same cost of delegating the
TOML semantic model to a general-purpose datetime institution.

### C++ toml++

toml++ has a TOML-specific `time` structure but constrains its seconds field to
`0..59`, showing that a format-specific type alone does not guarantee full
preservation if the library deliberately narrows the domain.

### C++20 `chrono`

C++20's UTC facilities expose leap-second information through dedicated
time-scale/tzdb machinery. This is strong evidence that proving a real leap event
is a materially different responsibility from storing a TOML temporal
component. It is useful precedent for a possible future time-scale layer, not
for making TOML parsing depend on such a subsystem.

### JavaScript Temporal / Node TOML implementations

Temporal deliberately follows a model that does not preserve leap seconds as a
distinct civil-time second and may constrain/normalize `60`. Node TOML libraries
that map directly into host `Date`/Temporal values inherit those semantics.

Again, that is reasonable for a host time API but not a reason for Protos to lose
format-level information in an explicit TOML semantic representation.

## Candidate comparison

Scores use the GITHUB010 1–5 scale. `H` = HIGH confidence, `M` = MEDIUM
confidence.

| Criterion | A reject `:60` | B′ preserve `0..60`, no DB | C validate real event | D normalize + flag | E lossy normalize | F special node | G wait for datetime subsystem |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | 2/H | **4.5/H** | 4/M | 4/H | 1/H | 4/M | 4.5/M |
| Protos alignment | 4/H | **5/H** | 2/H | 2.5/H | 1.5/H | 2/H | 1.5/H |
| Future-option resilience | 2/H | **5/H** | 3/M | 3.5/M | 1.5/H | 3/M | 4.5/M |
| Scalability | **5/H** | **5/H** | 3/M | **5/H** | **5/H** | 4.5/H | 2.5/M |
| Conceptual simplicity | **5/H** | **5/H** | 1.5/H | 2.5/H | 4.5/H | 2/H | 1/H |
| Portability / implementation freedom | **5/H** | **5/H** | 2/H | **5/H** | **5/H** | **5/H** | 2.5/M |
| Runtime / resource cost | **5/H** | **5/H** | 2.5/H | 4.5/H | **5/H** | 4.5/H | 2/H |
| Failure / operability | **5/H** | **4.5/H** | 2.5/M | 4/H | 2/H | 3/M | 2.5/M |
| Reversibility / migration | 2/H | **5/H** | 2.5/M | 3/M | 1.5/H | 2.5/M | 2/M |
| Evidence maturity / implementation risk | 4.5/H | **5/H** | 4/H | 4.5/H | 4/H | 2.5/M | 4.5/H |

Focused owner-requested scores:

```text
Candidate B′
aguante de futuro:  5.0 / 5
escalabilidad:      5.0 / 5
filosofía Protos:   5.0 / 5
```

Scores are evidence aids rather than decision authority.

## Hard rejections

### E — lossy normalization

Rejected because it destroys the selected semantic round-trip guarantee. A TOML
value containing `:60` could no longer be represented as the same TOML semantic
component.

### F — special leap-second node

Rejected because a leap-second spelling is still one of TOML's existing temporal
categories. Creating an additional public kind or side-car value would inflate
the ten-kind model for a component that can be represented by an ordinary
Integer.

### G — block on general datetime

Rejected because it makes every TOML consumer pay for a substantially larger
calendar/time-scale institution that is neither required for configuration data
nor otherwise selected.

### C — real-event database validation in TOML

Rejected because the TOML layer would acquire external/versioned temporal
authority and because `localTime`/`localDateTime` do not necessarily carry enough
information to perform such validation coherently.

### A — reject `:60`

Not structurally impossible, but rejected because it would make the public TOML
1.1 contract intentionally narrower for a limitation Protos does not have.

### D — normalize to `59` plus metadata

Rejected because the side metadata exists only to compensate for a host datetime
representation limitation. Protos' TOML-specific record can represent `60`
directly with less machinery and better semantic round-trip behavior.

## Strongest argument against B′

B′ can represent an offset date-time with `second = 60` without proving that the
corresponding UTC instant was a real historical or announced leap-second event.

That is a genuine distinction from full RFC 3339 event-aware validation.

The reason B′ still wins is ownership: proving the event requires time-scale
knowledge that does not belong to a pure TOML format library, while the
format-level value must remain representable even on runtimes without tzdb/IERS
state and even for local temporal categories that lack sufficient instant
context.

## Future-regret scenario and escape path

A future program may need to convert a TOML offset date-time into an exact UTC or
TAI instant and require proof that `second = 60` corresponds to a real event.

The escape path remains explicit and composable:

```text
TOML temporal record
        +
explicit future datetime/time-scale authority
        |
        v
validate / convert / fail
```

That later facility can use a versioned leap-second source without modifying the
TOML node model or making ordinary TOML parsing depend on global temporal state.

## Implementation consequence

D104 ratification itself changes no executable code.

Before LIB010-B can publish a parser that claims complete TOML 1.1 temporal
support, a bounded executable alignment slice must update the existing
`std:toml/TOML` time-bearing constructors so `second` accepts `0..60`, retain
`61` rejection, and add focal evidence for all three time-bearing kinds.

After that alignment publishes, LIB010-B may resume.

D104 does not change D087 private TOOL001/TOOL002 TOML 1.0 bootstrap semantics.
