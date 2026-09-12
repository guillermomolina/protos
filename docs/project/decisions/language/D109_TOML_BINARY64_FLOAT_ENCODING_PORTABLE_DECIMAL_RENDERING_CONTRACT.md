# D109 — TOML binary64 float encoding and portable decimal-rendering contract

Status: **RATIFIED — Candidate C selected**
Explicit project-owner approval: **2026-09-12**
Nature: non-normative public Standard Library semantic-decision record
Decision issue: GitHub `#434`
Primary consumer: `LIB010` / GitHub `#418`
Primary public contract owner: `docs/project/work/LIB010/LIB010_TOML_DESIGN.md`

## Trigger

LIB010-B closed the strict public TOML 1.1 parser with TOML `float` values mapped
to the existing Protos binary64 `Float` semantic family.

LIB010-C must now implement `TOML.encode(rootTable)`. Core intentionally exposes
no normative general Float-to-decimal String protocol. The current JVM
interop/display implementation happens to use Java `Double.toString`, but that
is diagnostic/interop behavior and is not portable Protos serialization
semantics.

The encoder therefore needed an explicit decision before choosing one observable
decimal spelling policy.

## Decision

Protos selects **Candidate C — canonical shortest-round-trip binary64 decimal
rendering followed by TOML lexical normalization**.

For every finite non-zero semantic Protos Float, the encoder selects a
deterministic shortest decimal representation that, when parsed under
round-to-nearest/ties-to-even binary64 semantics, recovers the exact same
binary64 value.

The implementation algorithm is not itself public API. Any proven-equivalent
Ryū/Schubfach-class or other implementation may be used provided it realizes the
same observable contract.

After the shortest round-trip decimal is selected, the TOML layer applies only
TOML-specific normalization:

```text
positive zero  -> 0.0
negative zero  -> -0.0
+infinity      -> inf
-infinity      -> -inf
any NaN        -> nan
```

For finite values:

- output uses ASCII decimal digits;
- a negative value may have one leading `-`;
- no redundant leading `+` is emitted;
- exponent spelling, when selected, uses lowercase `e`;
- exponent digits contain no redundant leading zeroes;
- a positive exponent does not carry a redundant leading `+`;
- no underscore grouping is emitted;
- if the shortest finite representation contains neither `.` nor exponent, `.0`
  is appended so the token remains lexically a TOML Float rather than becoming
  an Integer.

Examples:

```text
1.0   -> 1.0
0.0   -> 0.0
-0.0  -> -0.0
+Inf   -> inf
-Inf   -> -inf
NaN    -> nan
```

The semantic contract is:

```text
semantic binary64 Float
        |
        v
canonical shortest-round-trip decimal
        |
        v
TOML lexical normalization
        |
        v
valid TOML Float token
        |
        v
TOML.parse
        |
        v
same semantic binary64 Float
```

For NaN, "same semantic Float" means the existing Protos semantic NaN category.
D109 does not promise preservation of source NaN sign, payload,
signaling/quiet encoding, or lexical spelling because the already-ratified
semantic TOML model does not expose those distinctions.

## Why shortest-round-trip is selected

The public TOML layer owns semantic serialization, not host display.

A fixed 17-significant-digit/max-digits approach can be correct but often emits
unnecessary digits. Exact full decimal expansion is also correct but needlessly
inflates common values and subnormals. Delegating to the current host renderer is
small but would turn one runtime's display policy into Protos semantics.

Shortest-round-trip keeps exactly the precision required by the existing
binary64 semantic model while minimizing output and preserving implementation
freedom.

## Prior-art audit

The comparative audit covered materially different production strategies rather
than API spelling alone.

### Rust — `toml`, `toml_edit`, `toml_writer`

Modern Rust float formatting uses shortest round-trip conversion. TOML writers
ensure that an integral-looking floating value remains lexically a Float.
Rust's separate source-preserving editing layer is also consistent with LIB010's
semantic `TOML` versus future `Document` split.

A historical `toml-rs` precision regression involving an `f32` widened through
`f64` demonstrates why representation boundaries must not be treated casually.

### Go — `pelletier/go-toml/v2`

The writer handles NaN and infinities explicitly, uses round-trip-safe
`strconv` conversion for finite floats, and appends `.0` when otherwise needed
to preserve TOML Float lexical category.

Its fixed-notation choice may emit more characters for extreme magnitudes, but
its correctness discipline strongly supports explicit specials, round-trip
fidelity and lexical-category preservation.

### Python — `tomllib`, `tomli-w`, `tomlkit`

Python's standard `tomllib` is parse-only. `tomli-w` delegates ordinary float
rendering to Python's modern shortest-round-trip `str(float)` behavior.

That is useful evidence for shortest round-trip, but Protos does not adopt the
host-renderer-as-contract pattern. `tomlkit` separately reinforces the decision
that source-preserving spelling belongs in a future Document/editing layer.

### .NET — Tomlyn

Tomlyn historically used a fixed human-oriented precision that could lose
round-trip fidelity. Current Tomlyn uses explicit round-trip formatting for
`double`, handles specials explicitly and appends a decimal point where needed.

This is direct evidence against reduced fixed precision for semantic
serialization.

### C++ — `toml++`

`toml++` provides especially strong precedent: its normal path uses
round-trip-safe conversion (`std::to_chars` where available, with a
`max_digits10` fallback), handles special values explicitly and preserves Float
lexical category. A separate relaxed-precision mode is explicitly distinct and
may sacrifice round-trip fidelity.

The architectural lesson is that lossless semantic encoding is the default
invariant; any human-oriented lossy formatting policy should be separate and
explicit.

### Java — Night Config / TomlJ

TomlJ is primarily a parser. Night Config handles TOML special values and relies
on Java numeric string conversion for finite values.

Modern Java conversion is round-trip capable, but making `Double.toString`
normative would couple Protos semantics to one host/runtime policy. D109 instead
defines the observable contract independently and permits a runtime mechanism
only insofar as it proves equivalent.

### JavaScript — `@iarna/toml`

`@iarna/toml` explicitly handles Infinity, -Infinity, NaN and negative zero and
uses JavaScript number rendering for ordinary finite values. It reinforces the
need for deliberate `-0.0` handling, but JavaScript's single Number domain is a
weaker architectural fit for Protos' distinct Integer and Float families.

### Ruby

Ruby's dtoa-style Float rendering reinforces shortest-representation precedent,
but its TOML ecosystem mostly delegates the contract to the host renderer.
Again, Protos adopts the semantic property rather than host coupling.

### Ryū / Schubfach

Ryū and Schubfach-class algorithms directly solve the binary-to-shortest-decimal
round-trip problem with deterministic, proven strategies. They demonstrate that
the selected contract is mature numerical engineering rather than a
Protos-specific invention.

## Candidate comparison

Scores use the GITHUB010 1–5 scale. `H` = HIGH confidence, `M` = MEDIUM
confidence.

| Criterion | A host renderer | B fixed 17/max-digits | C shortest round-trip | D exact expansion | E public Core format first | F retain source lexeme |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Correctness / invariants | 4/H | 5/H | **5/H** | 5/H | 5/H | 3/H |
| Protos alignment | 2/H | 4/H | **5/H** | 4/H | 3.5/M | 2/H |
| Future-option resilience | 2.5/H | 4/H | **5/H** | 4.5/H | 4.5/M | 3/H |
| Scalability | 5/H | 5/H | **5/H** | 3.5/H | 5/H | 4/H |
| Conceptual simplicity | 5/H | 4.5/H | 4/H | 3/H | 2.5/H | 2.5/H |
| Portability / implementation freedom | 1.5/H | 5/H | **5/H** | 5/H | 5/H | 4/H |
| Runtime / resource cost | 5/H | 4.5/H | **5/H** | 3.5/H | 5/H | 4/H |
| Failure / operability | 3/M | 5/H | **5/H** | 5/H | 5/H | 3/M |
| Reversibility / migration | 2.5/M | 4.5/H | **5/H** | 4/H | 2.5/M | 2/H |
| Evidence maturity / implementation risk | 4/H | 5/H | **5/H** | 4/H | 4/M | 5/H |

Focused project-owner criteria:

```text
Candidate C
aguante de futuro:  5.0 / 5
escalabilidad:      5.0 / 5
filosofía Protos:   5.0 / 5
```

Scores are evidence aids, not decision authority.

## Rejected alternatives

### A — host renderer is normative

Rejected because a host runtime/version would become the authority for public
Protos serialization semantics. A host renderer may be used internally only if
the implementation proves that it realizes D109's independent contract.

### B — fixed `max_digits10` / 17 significant digits

Not incorrect, and retained as a credible fallback design, but rejected as the
public canonical policy because it commonly emits digits that carry no
additional semantic information.

### D — exact full decimal expansion

Rejected because it can dramatically inflate values, especially very small
numbers/subnormals, without improving semantic round-trip beyond Candidate C.

### E — first define public Core Float formatting

Rejected as premature. TOML encoding does not justify freezing a universal
program-display/numeric-formatting protocol in Core. A future numeric formatting
facility should be designed on its own requirements.

### F — retain source float lexeme in semantic TOML nodes

Hard-rejected because it contradicts the already-ratified semantic TOML model,
does not solve encoding of programmatically constructed nodes and duplicates the
future source-preserving `Document` responsibility.

## Host/runtime implementation boundary

D109 selects the **observable semantic contract**, not a JVM shortcut.

LIB010-C must not introduce a hidden Java/JVM-only formatter or binary64
bit-decomposition primitive merely because the current implementation is hosted
on the JVM.

If implementing D109-C in ordinary Protos reveals that the language/runtime lacks
a required portable primitive or privileged binary64 decomposition capability,
the affected encoder slice must stop and open the appropriate Dxxx/PLATxxx
decision before crossing that boundary.

Such a prerequisite may later be reusable, but it is not silently authorized by
D109.

## Scaling and future scenarios

The selected policy is stateless and local to one Float value. It requires no
process-global registry, locale, cache, mutable shared state, clock, I/O,
scheduler, Actor coordination, package authority or network service.

It therefore scales independently across:

- large TOML documents;
- many concurrent Tasks/Actors/Processes;
- many runtime Contexts;
- alternate Standard Library implementations;
- native-image/AOT execution;
- alternate host runtimes;
- future streaming writers.

A future Decimal or arbitrary-precision numeric family would require its own
serialization contract; D109 does not pre-decide it.

A future source-preserving `std:toml/Document` may retain the original float
lexeme while projecting the same semantic binary64 node. That does not change
the semantic encoder's canonical D109 spelling.

## Future-regret scenario and escape path

The strongest regret scenario is a future Protos runtime that wants one common
numeric-rendering engine shared by JSON, TOML, diagnostics and general
formatting.

D109 does not block that. The shared engine may later implement a reusable
shortest-round-trip primitive, and TOML can consume it while keeping D109's
observable contract unchanged.

Conversely, if a future user-facing formatter wants configurable precision or
presentation, it must remain separate from the lossless semantic TOML guarantee.

## Implementation consequence

D109 ratification itself changes no executable code, specification text, Maven
implementation version, native boundary or D087 private-tool TOML behavior.

`LIB010-C` is released to implement `TOML.encode(rootTable)` under Candidate C.

Before introducing any new privileged runtime/bit-decomposition mechanism,
LIB010-C must apply the host/runtime implementation boundary above and stop for
a dedicated design decision if required.

Top-level LIB010 full/integrated validation debt remains unchanged.
