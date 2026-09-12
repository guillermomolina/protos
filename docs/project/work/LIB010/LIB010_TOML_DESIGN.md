# LIB010 — TOML Standard Library design

Status: **LIB010-A/D104 ALIGNMENT CLOSED; LIB010-B parser READY**

Owning work item: GitHub Issue `#418` — `LIB010 — TOML parsing, document model and public Standard Library API`

Nature: project Standard Library design record; **non-normative**

Explicit project-owner approval: **2026-09-12**

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Purpose

This record closes the `LIB010-0` exhaustive comparative design/selection
checkpoint for a public TOML facility in the Protos Standard Library.

The selected architecture is **Candidate C — semantic TOML data model plus a
separate future lossless/editable Document layer**.

LIB010 deliberately does not expose the current private bundled-tool TOML node
shapes as public API. It also does not turn TOML into a generic serialization
institution, a reflection/object-binding system, an I/O authority, or a
bootstrap dependency of Package Tool or Test Tool.

The normative Protos specification under `spec/` remains authoritative. LIB010
uses existing Protos String, Integer, Float, Boolean, Array, Map, Error, module,
Actor/isolation and I/O semantics; it adds no new Core semantic family.

## Ratified ownership boundary

D087 remains authoritative.

The durable ownership direction is:

```text
                           Package Tool / TOOL001
                          /
private toolchain Toml10 ---- Test Tool / TOOL002
                          \
                           public std:toml/TOML
```

The private bootstrap engine remains toolchain-owned and private.

Consequently:

- TOOL001 and TOOL002 remain able to parse their persisted formats before public
  Standard Library/package resolution is available;
- Package Tool and Test Tool do not import the public TOML module merely because
  implementation pieces may be shared;
- public TOML evolution cannot silently broaden an older persisted Package/Test
  schema generation;
- private `tool-shared:Toml10/...` import spellings remain private;
- private node shapes remain replaceable implementation detail;
- a public implementation may reuse, wrap, factor, or replace pieces of the
  private engine while preserving this ownership direction.

The current private `Toml10` implementation is valuable implementation evidence,
not the public compatibility contract. In particular, its current
`string`/`boolean`/signed-64-bit `integer`/`array`/`table` materialization and
`unsupported` fallback do not define the public data model.

## Selected public module identity

The canonical initial public module identity is:

```text
std:toml/TOML
```

with physical distribution source:

```text
protos/lib/toml/TOML.protos
```

This follows the Standard Library exact-case logical naming policy. `TOML` is a
module of TOML-specific construction, parsing and encoding behavior. It is not a
Core prototype and does not create a TOML runtime value family.

Importing `std:toml/TOML` grants no filesystem, network, process, clock,
environment, package-manager or other live authority.

## Selected public architecture

The initial public facility has a semantic data layer only.

Conceptually:

```text
source TOML text
      |
      v
TOML.parse
      |
      v
semantic TOML data tree
      |
      v
TOML.encode
      |
      v
valid TOML text
```

A future source-preserving editing layer is separate:

```text
source TOML text
      |
      v
future std:toml/Document
      |
      +--> lossless/editable source structure
      |
      +--> semantic TOML projection
```

The initial semantic layer does not retain comments, whitespace, original quote
choice, integer radix, underscore placement, float spelling, `Z` versus
`+00:00`, dotted-key spelling, inline-versus-expanded table presentation, or
other source trivia.

That omission is deliberate. It prevents every ordinary TOML consumer from
paying the representation and compatibility cost required by an editor/formatter.

## Selected semantic value model

A TOML semantic value is represented by ordinary Protos data with one explicit
TOML `kind` and its validated `value`.

The selected semantic kinds are exactly:

```text
string
integer
float
boolean
offsetDateTime
localDateTime
localDate
localTime
array
table
```

The `kind` tag is ordinary data. It does not create a runtime type classifier,
Core value family, privileged identity category, or universal structured-data
Node hierarchy.

### String

```text
kind  = "string"
value = semantic Protos String
```

TOML basic/literal and single/multiline source spellings normalize to the same
semantic String when they denote the same scalar sequence.

No Unicode normalization is performed merely by TOML parsing.

### Integer

```text
kind  = "integer"
value = ordinary unbounded Protos Integer
```

The public semantic model deliberately does **not** inherit the current private
bootstrap parser's signed-64-bit implementation limit.

TOML permits implementations to support integer ranges larger than signed
64-bit provided accepted values are lossless. Protos already has an exact
unbounded Integer family, so the public library preserves every syntactically
valid TOML Integer that can be represented by that family.

Decimal, hexadecimal, octal and binary source spellings normalize to the same
mathematical Integer when they denote the same value. Radix and underscore
spelling are source representation, not semantic data.

### Float

```text
kind  = "float"
value = semantic Protos Float
```

The selected mapping is IEEE binary64 because that is the existing Core Float
semantic family and TOML requires implementations to support at least that
precision level.

The mapping preserves the Core distinction between positive and negative zero.
TOML infinities map to the corresponding Protos Float infinities.

TOML NaN maps to the existing Core semantic NaN value. The semantic TOML layer
does not promise to retain a source NaN sign, payload, signaling/quiet encoding,
or lexical spelling that Core does not expose. Such information would belong to
a future source-preserving representation.

LIB010 does not introduce Decimal, BigDecimal, arbitrary-precision Float, or a
second numeric hierarchy.

### Boolean

```text
kind  = "boolean"
value = canonical true | false
```

Only the canonical Core Boolean values are valid payloads.

### Temporal kinds

TOML's four temporal categories remain distinct semantic TOML kinds:

```text
offsetDateTime
localDateTime
localDate
localTime
```

They are represented by TOML-specific ordinary data records, not Java/JVM
`LocalDate`, `Instant`, `OffsetDateTime`, host clock objects, or a prematurely
standardized general Protos datetime library.

The durable rules selected here are:

- calendar/time components are exact ordinary Integer data;
- an offset date-time records an explicit numeric UTC offset independently of
  any system time-zone database;
- no locale, system clock, current zone, DST lookup, tzdb, calendaring service or
  host authority is consulted;
- parsing/encoding these values is pure syntax/data processing;
- lexical differences that denote the same TOML temporal value need not survive
  the semantic layer;
- exact public record slot spelling and constructor argument layout are owned by
  the first bounded semantic-model implementation slice and must remain within
  these already-approved constraints; if that slice encounters a genuinely
  substantive semantic choice rather than mechanical record spelling, it must
  stop under the normal approval gate.

A future general `std:datetime` facility may provide calendar/time operations or
explicit conversion helpers. LIB010 does not make its TOML records Core datetime
families merely to anticipate that work.

### D104 leap-second clarification

D104 is **RATIFIED — Candidate B′ selected**.

For TOML 1.1 format fidelity, the time-bearing semantic records use an ordinary
Integer `second` component whose domain is `0..60`:

```text
localTime.second       = 0..60
localDateTime.second   = 0..60
offsetDateTime.second  = 0..60
```

A value with `second = 60` is TOML semantic data. `std:toml/TOML` does not
thereby claim that a corresponding real UTC leap-second event exists.

Real-event validation, UTC/TAI conversion, leap-second schedules, tzdb and other
time-scale authority remain outside TOML. A future explicit datetime/time-scale
facility may validate or convert a TOML temporal record without changing whether
the TOML layer can represent it.

For TOML 1.1 partial times whose seconds are omitted, the semantic TOML value has
`second = 0`. Whether `:00` was lexically present is source-representation data
and belongs to a future source-preserving Document layer.

The bounded LIB010-A/D104 executable alignment is **CLOSED** at implementation
version `0.2.417-SNAPSHOT`. The three time-bearing constructors now accept `second` in
`0..60`; retained focal evidence preserves `60` for `localTime`,
`localDateTime`, and `offsetDateTime`, while `61` remains rejected for all three.

No leap-second schedule, tzdb, clock, host datetime representation, parser,
encoder, Document, streaming surface or D087 private-tool TOML behavior was
introduced by the alignment. LIB010-B is therefore released to implement strict
TOML 1.1 parsing under D104.

### Array

```text
kind  = "array"
value = ordered Array of TOML semantic values
```

TOML array order is significant.

Arrays may contain any combination permitted by the selected TOML dialect. The
semantic layer does not create a cross-format generic collection wrapper.

### Table

```text
kind  = "table"
value = Map from semantic String key to TOML semantic value
```

TOML tables, dotted keys, inline tables, and arrays-of-tables are syntax /
document-construction mechanisms. After successful semantic parsing:

- a table is represented as `table`;
- an inline table is represented as `table`;
- an array of tables is represented as an `array` whose members are `table`
  values.

The semantic tree therefore does not retain inline-versus-expanded presentation.
A future `Document` layer may retain it.

Duplicate definitions, table redefinitions, dotted-key conflicts, attempts to
extend an inline table where TOML forbids it, and other TOML ownership conflicts
are parse errors. They never silently use first-wins or last-wins replacement.

Map insertion/source order may be retained for deterministic ordinary encoding,
but order is not promoted into universal TOML table equality.

## Initial public operation roles

The initial baseline selects these two primary operations:

```text
TOML.parse(text)
TOML.encode(rootTable)
```

`parse` consumes one semantic Protos String and returns one semantic TOML root
table.

`encode` accepts one valid semantic TOML root table and returns one semantic
Protos String containing valid TOML in the public module's selected dialect.

The semantic-model implementation slice also supplies explicit constructors /
validators for the selected TOML value kinds. The constructor names follow the
kind vocabulary unless a purely mechanical Protos naming constraint requires a
different spelling. A constructor must validate its public representation rather
than silently coerce arbitrary objects through `toString`, reflection, host
datetime conversion or another unrelated protocol.

No `loadFile`, `saveFile`, URL reader, network operation, environment lookup, or
ambient current-directory convenience belongs to this kernel.

## Selected TOML dialect policy

The initial public module targets **TOML 1.1**.

This decision is intentionally independent from the persisted bundled-tool
formats.

Conceptually:

```text
TOOL001 schema generation currently selected by D087 -> private TOML 1.0
TOOL002 schema generation currently selected by D087 -> private TOML 1.0

std:toml/TOML initial public library               -> TOML 1.1
```

An update of `std:toml/TOML` therefore cannot silently change which syntax an
older Package Tool or Test Tool persisted-schema generation accepts.

The public module must not expose an unversioned host-dependent concept of
"whatever TOML this runtime currently supports". Its accepted grammar is a
library compatibility contract.

A later TOML specification revision requires an explicit LIB010/public-library
evolution decision. Existing persisted tool schemas remain independently pinned
until their owners explicitly migrate them.

## Parse contract

`TOML.parse(text)`:

- requires exactly a semantic Core String;
- is synchronous and introduces no hidden Future or suspension;
- parses the complete document strictly under the selected TOML 1.1 grammar;
- returns semantic data only after the complete document is valid;
- rejects malformed UTF/text, malformed TOML, duplicate/redefinition conflicts,
  invalid date/time values and invalid numeric syntax;
- does not perform schema/application interpretation;
- does not perform environment interpolation or include processing;
- does not perform filesystem/network/package resolution;
- does not silently repair invalid TOML;
- does not bind arbitrary application objects;
- does not preserve source trivia in the semantic result.

Parse failure signals an ordinary fresh Error unless a later approved diagnostic
surface explicitly selects a narrower public category/payload.

The initial semantic parser is transactional with respect to its returned tree:
a malformed suffix does not return a partially successful root table.

## Encode contract

`TOML.encode(rootTable)` validates the complete supplied semantic representation
needed by the operation and emits valid TOML 1.1.

It does not:

- reflect over arbitrary application objects;
- call a `toTOML` / `toJSON`-style hook;
- invoke a generic Serializer hierarchy;
- infer TOML kinds from arbitrary delegation;
- acquire I/O authority;
- preserve source trivia that is absent from the semantic model.

The ordinary writer may choose a deterministic canonical **library style** for
source choices that are semantically irrelevant, such as quoting/radix/table
presentation. That style is not a claim of byte-for-byte source preservation and
is not a universal TOML canonicalization standard.

The Float emission implementation requires a focused current-main audit before
the encoder slice is materialized. Protos Core intentionally does not define a
general Float-to-decimal String protocol merely for host convenience. LIB010
must therefore not expose JVM `Double.toString` accidentally as portable TOML
semantics. A portable library algorithm or an explicitly approved reusable
numeric-formatting prerequisite may be used. If implementation discovers that
new Core/runtime semantics are required, the encoder slice must stop and route
that prerequisite through the normal decision/blocker process.

## Round-trip guarantee

The selected baseline guarantees semantic rather than textual round-trip.

For every valid semantic TOML tree `t` accepted by the encoder:

```text
TOML.parse(TOML.encode(t))
```

must produce a semantically equivalent TOML tree.

The initial baseline does **not** promise:

```text
TOML.encode(TOML.parse(source)) == source
```

as exact String equality.

Differences may include:

- comments;
- whitespace;
- basic versus literal String spelling;
- single-line versus multiline String spelling;
- integer radix and underscores;
- float exponent/case/spelling choices;
- date/time equivalent lexical choices;
- quoted versus bare keys where both are legal;
- dotted versus expanded table syntax;
- inline versus expanded table presentation.

An exact/source-preserving round trip belongs to the future `Document` layer.

## Source-preserving/editable boundary

A source-preserving editor is a legitimate future requirement, not an initial
semantic-parser requirement.

The likely future identity is:

```text
std:toml/Document
```

but this record does not publish that module or freeze its final API.

A future Document layer may retain:

- comments and trivia;
- whitespace;
- key quoting;
- String quoting and multiline style;
- integer radix/underscore representation;
- float representation;
- temporal representation;
- dotted-key spelling;
- table header and inline-table presentation;
- source ranges;
- editable syntax structure.

It must be possible for such a layer to project to the semantic `TOML` model
without forcing semantic consumers to carry all source metadata.

The public semantic model is deliberately chosen so that this future layer can
be added rather than retrofitted into every node.

## Error and diagnostics boundary

The initial parser/encoder signals ordinary synchronous Error for invalid input
or invalid semantic representation.

Detailed diagnostics are useful and strongly supported by prior art, but LIB010-0
does not make one host parser's diagnostic structure public by accident.

Implementation may retain private diagnostic state for tests/debugging.

A future approved diagnostic surface may expose ordinary data such as:

```text
message
offset/span
line
column
context
```

only after its stability and source-position units are deliberately selected.

Strict parsing is the default. No permissive/repair mode is selected.

## Streaming and Text I/O

No public streaming/event parser is selected for the initial baseline.

TOML is a document-oriented configuration format whose table ownership,
dotted-key and redefinition rules naturally require document-level state. Prior
art shows that accepting an `io.Reader`/stream does not necessarily provide
bounded-memory semantic parsing: mature implementations may still consume and
materialize the complete document.

Therefore initial work optimizes the common and composable boundary:

```text
String -> TOML.parse -> semantic tree
semantic tree -> TOML.encode -> String
```

A later `TextReader`/`TextWriter` convenience may be justified by real consumers,
but it must receive already-constructed wrappers and preserve Core ownership,
Encoding, Future ordering, cancellation, close and authority semantics exactly.

TOML never owns filesystem/network authority merely because configuration is
commonly stored in files.

A future event/incremental interface requires a demonstrated use case and an
explicit retained-state/backpressure/error-prefix contract rather than being
added for symmetry with JSON or CSV.

## Complexity and scalability targets

For input/output size `n`, the implementation quality targets are:

```text
parse:   O(n) time, O(n) semantic result storage
encode:  O(n) time, O(n) output storage
```

Parser state must not require:

- process-global mutable caches;
- a global dialect registry;
- a shared parser singleton;
- hidden synchronization between independent calls;
- host/JVM recursive call depth as the only protection against adversarial table
  nesting.

Many Actors/Processes may parse unrelated documents independently using ordinary
module/isolation rules.

Reasonable explicit implementation protection against adversarial depth/resource
consumption is permitted and encouraged. The external `toml-test` project notes
that table-nesting limits such as 128 or 256 are sufficient for essentially all
real-world documents while preventing pathological resource use. A portable
public limit or tunable-limit API is **not** selected by this design; if one
becomes observable public behavior, it requires explicit review rather than an
accidental host-stack limit.

Large input handling must remain deterministic and fail closed. Repeated parsing
must not leak retained source trees or mutable global data.

## Actor, Process and alternate-runtime boundary

`std:toml/TOML` is an ordinary Actor-local module instance under Core module
semantics.

Constructor/parser results are ordinary data graphs. The module itself contains
behavior; parsed semantic data does not need to carry module Closures merely
because TOML behavior exists.

No implementation contract requires:

- Java reflection;
- Java `LocalDate`/`Instant`;
- JVM parser libraries;
- Java serialization;
- a host object wrapper;
- Native Image reflection configuration;
- a process-global TOML service.

The initial implementation should prefer ordinary Protos source and may reuse
private ordinary-Protos parser mechanics where that reuse does not expose or
couple the public API to private shapes.

## Comparative prior-art audit

The audit compared architecture and semantic boundaries, not only API names.

### TOML specification and toml-test

TOML 1.1 explicitly allows implementations to support integer ranges larger
than signed 64-bit and requires accepted integers to be lossless. It recommends
at least signed 64-bit support. Floats recommend at least IEEE binary64 and
include infinities, NaN and signed zero.

`toml-test` treats semantically equivalent encodings as equivalent rather than
requiring one writer spelling, and recommends practical protection against
pathological table nesting.

Durable lesson for Protos: distinguish semantic value from source spelling and
make resource protection deliberate rather than host-stack accidental.

### Python `tomllib`, Tomli-W and TOMLKit

`tomllib` provides a deliberately small semantic parse API, maps TOML to native
data, exposes positional decode diagnostics, warns about hostile-input CPU/memory
cost, and deliberately does not provide writing. Python documentation points
users needing source editing to TOMLKit.

TOMLKit preserves comments, whitespace and ordering as an editable document.

Durable lesson: ordinary semantic reading and source-preserving editing are
different products. Protos should preserve that separation while using an
explicit TOML node model rather than relying on Python's runtime type classifier.

### Rust `toml` and `toml_edit`

Rust supplies a conventional semantic TOML API and a distinct `toml_edit`
facility. `toml_edit` preserves comments, spaces and relative order and exposes
representation-specific formatting controls.

This is the closest architectural precedent for Candidate C: source-preserving
editing can be powerful without forcing its storage model into every semantic
parse.

### Go BurntSushi/toml

BurntSushi/toml is mature and TOML 1.1 compatible, but its primary API is
reflection/struct/map binding. Its current decoder also reads an `io.Reader`
fully before parsing and uses a default maximum table nesting of 128.

Durable lesson: a Reader-shaped API is not proof of bounded-memory streaming,
and reflection/domain binding should remain above the Protos TOML kernel.

### Go pelletier/go-toml v2

go-toml v2 targets TOML 1.1 and deliberately models its stable API after Go's
`encoding/json`. Its lower-level iterative parser remains explicitly unstable.
The v2 evolution also removed the old general `toml.Tree` document model from
the stable scope rather than freezing an immature tree API indefinitely.

Durable lesson: keep parser internals and experimental syntax-tree surfaces
replaceable until a real stable public document contract is earned.

### Java TomlJ

TomlJ provides a semantic table-oriented result, detailed error positions and
error recovery. Its public shape is useful evidence for diagnostics and a
format-specific table abstraction.

Durable lesson: source position information is useful, but Java temporal/runtime
types and parser-generator architecture are implementation choices, not portable
Protos semantics.

### .NET Tomlyn

Current Tomlyn separates a high-level serializer/object-binding path from a
low-level lexer/parser and a lossless trivia-preserving syntax tree. It also
emphasizes AOT-friendly generated metadata.

Durable lesson: semantic binding, parser mechanics and lossless editing can be
separate layers. Protos should keep reflection/object binding out of the base
library and naturally avoids AOT reflection pressure by using ordinary Protos
data.

### C++ toml++ / toml11 family

Modern C++ TOML libraries demonstrate portable native semantic trees, source
regions/diagnostics and exact format-specific value categories without requiring
a VM-global serializer institution.

Durable lesson: a format-specific semantic tree is a mature design point; host
variant/template types themselves are not the portable Protos API.

### Ruby implementations

Ruby TOML libraries generally favor Hash/Array/native scalar mapping.

Durable lesson: the simple mapping is ergonomic in a language with ubiquitous
runtime type discrimination, but Protos deliberately avoids inventing `typeOf`
or making delegation mean semantic-family membership merely to copy that model.

### JavaScript / Node

Modern Node implementations such as smol-toml expose the cost of mapping TOML
directly to JavaScript `number`: integer-vs-float distinctions and integers above
the safe integer range require BigInt/options/additional tagging. Historical
implementations also had to adapt TOML local date/time concepts to JavaScript
`Date`, which does not naturally represent all four TOML temporal categories.

Durable lesson: host-native mapping is only attractive when the host type system
actually preserves TOML distinctions. Protos should use explicit TOML kinds.

### Cargo

Cargo is one of the strongest long-running TOML consumers. The important lesson
is ownership, not API spelling: TOML is the manifest encoding while Cargo owns
package schema, dependency semantics, feature semantics, resolver behavior and
compatibility policy.

This exactly reinforces D087 and the existing Protos package design: a public
TOML parser must not become owner of Package Tool schema meaning.

### Taplo and editor/formatter tooling

Taplo and similar TOML language/editor tooling demonstrate real demand for
syntax trees, formatting, source spans, schema assistance and document editing.

Durable lesson: those needs justify a future `Document`/tooling layer, not
inflating the initial semantic configuration-data model.

## Focused precedent scorecard

Scores are 1–5 for suitability as precedent for the Protos public TOML design,
not a claim about each project's overall quality.

| System / approach | Future resilience | Scalability | Protos alignment | Principal lesson |
|---|---:|---:|---:|---|
| Rust `toml_edit` | 5.0 | 3.5 | 4.5 | Excellent future Document precedent; too rich as mandatory base model. |
| Cargo | 5.0 | 4.0 | 4.5 | Encoding and consuming schema semantics remain separate. |
| Rust `toml` | 4.5 | 4.0 | 4.5 | Strong semantic format-specific model. |
| pelletier/go-toml v2 | 4.5 | 4.5 | 3.5 | Stable semantic API; parser/tree internals kept evolvable. |
| Tomlyn | 4.5 | 4.5 | 3.5 | Strong separation of serializer, parser and lossless syntax tree. |
| TOMLKit | 4.5 | 2.5 | 3.5 | Excellent editing fidelity; heavier than base semantic needs. |
| toml11 / modern C++ models | 4.5 | 3.5 | 3.5 | Explicit TOML values and source diagnostics. |
| Python `tomllib` | 4.0 | 3.5 | 4.0 | Small durable semantic parser; separate editing facility. |
| TomlJ | 4.0 | 3.5 | 4.0 | Format-specific table model and diagnostics. |
| toml++ | 4.0 | 4.0 | 4.0 | Portable explicit semantic tree. |
| smol-toml | 4.0 | 4.5 | 2.5 | Performance evidence; host numeric mapping requires repair. |
| BurntSushi/toml | 3.5 | 3.5 | 2.5 | Mature binding; reflection is wrong as the Protos base. |
| Taplo | 3.5 | 3.0 | 4.5 | Strong tooling/document precedent, not semantic kernel. |
| Ruby native-mapping family | 3.5 | 3.0 | 3.5 | Simple mapping but depends on host family introspection. |
| broad config-framework model | 3.5 | 3.0 | 1.5 | Avoid format + I/O + lifecycle + object-binding institution. |
| historical `@iarna/toml` model | 2.0 | 3.5 | 1.5 | Host Date/hooks and legacy dialect coupling are poor precedents. |

## Candidate architectures

### A — direct native Protos values only

Map TOML directly to `String` / `Integer` / `Float` / Booleans / Array / Map and
some ordinary temporal records without explicit TOML kind nodes.

Advantage: smallest surface.

Rejected as the selected architecture because Protos has no universal semantic
type classifier and TOML must preserve distinctions such as integer versus
float. Reconstructing format category from arbitrary ordinary values would
either require hidden classification rules or grow a new generic type
institution.

### B — one TOML-specific semantic node model only

Strong, simple and portable. This would be a legitimate minimal solution.

Not selected as the complete architecture because editing/source-preserving
requirements are credible enough that the escape path should be part of the
design now, even though the Document API itself is deferred.

### C — semantic TOML model + separate future lossless Document

**Selected.**

The initial semantic layer remains small and scalable. A future Document layer
can preserve source representation independently and project into the semantic
model.

### D — lossless CST/Document as the only public model

Rejected for the base facility. It would force every application-config consumer
to pay for comments/trivia/source-representation machinery and make parser
internals a wider compatibility surface.

### E — reflection/application-object binding as TOML

Rejected as the base. Object binding is a separate convenience layer with
application-schema and reconstruction policy. It also encourages host/runtime
reflection semantics that Protos deliberately avoids.

### F — universal Serializer/Deserializer/Node hierarchy

Rejected. JSON, TOML, CSV, YAML and XML have materially different semantic
models. LIB003 and LIB009 already demonstrate the value of format-specific
boundaries. TOML does not justify introducing a cross-format institution.

### G — event/streaming API as the primary TOML interface

Rejected initially. It raises parser-lifetime, prefix-emission, table-ownership,
backpressure and recovery contracts without a demonstrated TOML consumer that
benefits enough to justify them. It remains a possible future extension.

## Required GITHUB010 scorecard

Scores are 1–5. Confidence is `H` high or `M` medium. Arithmetic is a comparison
aid, not decision authority.

| Candidate | Correctness | Protos | Future | Scale | Simplicity | Portability | Resource cost | Failure/ops | Reversible | Evidence |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| A native values | 4/H | 3/H | 3/H | 4.5/H | 4.5/H | 4/H | 5/H | 4/H | 2.5/M | 5/H |
| B semantic TOML model | 5/H | 5/H | 4.5/H | 4.5/H | 4/H | 5/H | 4.5/H | 4.5/H | 4.5/H | 5/H |
| **C semantic + future Document** | **5/H** | **5/H** | **5/H** | **4.5/H** | **4/H** | **5/H** | **4.5/H** | **5/H** | **5/H** | **5/H** |
| D lossless-only | 5/H | 3.5/H | 4.5/H | 3/M | 2.5/H | 4.5/H | 2.5/M | 4/H | 3/M | 5/H |
| E reflection binding | 4/H | 1.5/H | 3/H | 4/H | 3/H | 2.5/H | 3.5/M | 3/H | 2/H | 5/H |
| F universal serializer | 3.5/M | 1/H | 2.5/M | 4/M | 1.5/H | 3/M | 2.5/M | 3/M | 1.5/H | 4.5/H |
| G event-first | 4/M | 4/H | 4/H | 5/H | 2.5/H | 5/H | 4/H | 3.5/M | 4/H | 4/H |

Candidate C is selected because it preserves every important future escape path
without making source-editing machinery or a serialization hierarchy mandatory
for ordinary configuration consumers.

## Focused owner-requested scores

For the selected Candidate C:

```text
aguante de futuro:  5.0 / 5
escalabilidad:      4.5 / 5
filosofía Protos:   5.0 / 5
```

The strongest reasons are:

- future source editing has a clean separate layer;
- semantic parsing remains O(n)-style whole-document work with no compulsory
  syntax-tree/trivia retention;
- private bootstrap parsing remains private and independently versioned;
- values use existing Protos families and ordinary data rather than host
  reflection/runtime types;
- no ambient authority, registry, cache or global mutable parser state is needed;
- no universal cross-format serialization hierarchy is introduced.

## Future stress tests

### Ordinary application configuration

The semantic API is the direct path: parse once, inspect/validate domain data,
optionally edit semantic values, encode if needed.

### Package manifests

Package Tool remains on its private TOML 1.0/schema-generation boundary.
Application-level `std:toml/TOML` cannot silently redefine package acceptance.

### Test Tool resource documents

Same rule: D077/Test Tool schema meaning remains Test Tool-owned and TOML 1.0
remains pinned until a separate migration.

### Very large documents

Whole-document parsing necessarily retains the semantic result. The parser should
avoid additional asymptotically larger retention and host-recursive nesting.
Applications processing data too large for a configuration document may later
justify an incremental facility, but that is not free API complexity.

### Editing and formatting tools

This is the principal regret scenario for a semantic-only API. Candidate C
already preserves the escape path: add a separate Document layer with trivia and
source representation rather than breaking `TOML` values.

### Many concurrent parses

All state is per call/module instance. No registry, singleton mutable parser,
global schema cache or synchronization bottleneck is required.

### Future TOML specification revisions

The public module's supported dialect evolves explicitly. Persisted tool schemas
remain independently pinned.

### Alternate Protos runtime

The contract depends on Protos values and ordinary library behavior, not the JVM,
Java datetime classes, Java reflection or a specific third-party parser.

### Future JSON/YAML/XML libraries

TOML's explicit data model remains format-specific. No universal Node or
Serializer hierarchy is forced onto other formats.

## Regret analysis

The most plausible requirement that could make a semantic-only API insufficient
is user-facing editing where one operation must change a value while preserving
comments, whitespace and local formatting exactly.

Candidate C does not require replacing the semantic API in that scenario.

Escape path:

```text
std:toml/TOML
    remains the semantic data API

std:toml/Document
    adds source-preserving editing
    projects to/from semantic TOML where defined
```

The private parser can be refactored behind both layers without making its
current node shapes public.

## Strongest argument against Candidate C

Candidate C creates two conceptual public layers over time instead of one. If
Protos never gains serious TOML editing/formatter/IDE use cases, explicitly
planning a future Document boundary may appear more architectural than strictly
necessary.

Candidate B would then be simpler.

The reason C still wins is that the additional cost **today** is only a boundary:
the Document module is not implemented, loaded or paid for. The boundary prevents
the semantic model from accidentally consuming source-preserving responsibilities
and gives a credible high-value future requirement a migration path with very low
current runtime cost.

## Implementation decomposition

Ratification releases bounded implementation work in this order.

### LIB010-A — semantic model and constructors

- create exact-case `std:toml/TOML`;
- publish validated ordinary TOML semantic node construction for the ten selected
  kinds;
- keep behavior on the Actor-local module and data behavior-free;
- preserve ordinary transfer/isolation rules;
- no parser or encoder yet unless required only as private focal scaffolding;
- finalize mechanical temporal record slot/constructor spelling within the
  already-ratified temporal constraints;
- if that work uncovers a substantive semantic choice, stop under the approval
  gate.

### LIB010-B — strict TOML 1.1 parser

- parse semantic String input;
- complete TOML 1.1 syntax needed by the public contract;
- unbounded Protos Integer mapping;
- binary64 Float mapping including signed zero, infinity and NaN;
- all four temporal categories;
- tables/dotted keys/inline tables/arrays-of-tables;
- duplicate/redefinition/conflict rejection;
- adversarial nesting/resource strategy that is not accidental host recursion;
- no source-preserving public tree.

### LIB010-C — deterministic semantic encoder

- validate semantic root-table representation;
- emit valid deterministic TOML 1.1;
- preserve semantic round-trip;
- perform the focused Float-formatting prerequisite audit before implementation;
- do not expose host/JVM decimal formatting as semantics accidentally;
- no reflection/object binding or generic serializer.

### LIB010-D — conformance, stress and baseline closure

- official TOML/toml-test-oriented retained conformance as feasible in the
  project harness;
- large/deep/adversarial input tests;
- semantic parse/encode round-trip;
- independent-module/Actor/isolation checks;
- confirm D087 bootstrap boundary remains unchanged;
- confirm no source-preserving API, generic serializer, hidden I/O authority or
  global mutable parser state entered the baseline;
- close the bounded initial LIB010 surface.

`Document`, public streaming/events, richer diagnostics, TextReader/TextWriter
adapters, object binding, general datetime conversion, schema facilities and
future TOML dialect migrations remain separate extensions.

## LIB010-A implementation result

Status: **CLOSED**

Implementation version: `0.2.410-SNAPSHOT`

Published surface:

```text
std:toml/TOML

string(value)
integer(value)
float(value)
boolean(value)
offsetDateTime(year, month, day, hour, minute, second,
               fractionCoefficient, fractionDigits, offsetMinutes)
localDateTime(year, month, day, hour, minute, second,
              fractionCoefficient, fractionDigits)
localDate(year, month, day)
localTime(hour, minute, second, fractionCoefficient, fractionDigits)
array(...nodes)
table(...nameNodePairs)
```

The ten constructors materialize the already-ratified explicit TOML semantic
kinds and no additional exported helper slots. Scalar family validation is
performed through canonical existing Core receiver domains (`String`,
unbounded `Integer`, binary64 `Float`, canonical Boolean identity) rather than
through `typeOf`, reflection, conversion-as-classification or host runtime
objects.

Temporal values are ordinary behavior-free component records:

- TOML calendar years are `1..9999`;
- month/day validity uses the proleptic Gregorian leap-year rule;
- local clock values use hour `0..23`, minute `0..59`, second `0..59`;
- fractional seconds are exact ordinary Integer decimal components
  `coefficient` and `digits`, with `0 <= coefficient < 10^digits` and the
  zero-digit representation requiring coefficient zero;
- offset date-times use exact numeric `offsetMinutes` in `-1439..1439`;
- no clock, timezone database, locale, JVM date/time object or ambient authority
  participates.

`array` retains the frozen rest-capture Array and permits all ten TOML semantic
kinds. `table` creates a fresh ordinary Map from String/name-node pairs,
rejects duplicate keys and preserves ordinary Map insertion order without
making table order a TOML equality rule.

Retained focal evidence covers exact export surface, very large unbounded
Integer payloads, signed negative-zero Float identity, all four temporal
categories, nesting, wrong-family rejection, invalid calendar/time/fraction/
offset data, duplicate/odd table input, Actor-local module identity and
ordinary Actor transfer of the semantic data graph.

LIB010-A adds no parser, encoder, public Document/CST, event stream,
TextReader/TextWriter adapter, filesystem/network authority, schema/object
binding, generic serializer hierarchy, Java/native operation or Core semantic
family. D087 private TOOL001/TOOL002 TOML 1.0 bootstrap ownership remains
unchanged.

D104 alignment status: **CLOSED**.

Next bounded implementation slice:
**LIB010-B — strict TOML 1.1 parser**.

## Deliberately deferred

LIB010-0 does not select:

- a public `std:toml/Document` implementation or exact API;
- lossless source editing;
- a formatter API;
- a canonical-TOML standard;
- schema/application-object binding;
- a generic Serializer/Deserializer/Node abstraction;
- filesystem/network/process convenience;
- global dialect or schema registries;
- permissive parser repair;
- public parser event streaming;
- TextReader/TextWriter adapters;
- a general Protos datetime library;
- time-zone database semantics;
- a public structured diagnostic payload;
- caller-configurable public resource limits;
- TOML versions later than 1.1;
- migration of any D087-pinned persisted tool schema away from TOML 1.0.

## Primary external references

- TOML 1.1 specification: <https://toml.io/en/v1.1.0>
- TOML conformance corpus: <https://github.com/toml-lang/toml-test>
- Python `tomllib`: <https://docs.python.org/3/library/tomllib.html>
- Rust `toml_edit`: <https://docs.rs/toml_edit/>
- Go BurntSushi/toml: <https://github.com/BurntSushi/toml>
- Go pelletier/go-toml: <https://github.com/pelletier/go-toml>
- Java TomlJ: <https://github.com/tomlj/tomlj>
- .NET Tomlyn: <https://github.com/xoofx/Tomlyn>
- Python TOMLKit: <https://tomlkit.readthedocs.io/>
- Node smol-toml: <https://github.com/squirrelchat/smol-toml>
- Node @iarna/toml: <https://github.com/iarna/iarna-toml>
- Cargo manifest reference: <https://doc.rust-lang.org/cargo/reference/manifest.html>
