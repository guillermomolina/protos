# LIB009 — CSV Standard Library design

Status: **LIB009-0 RATIFIED — default-profile implementation slices released**

Owning work item: GitHub Issue `#346` — `LIB009 — CSV parsing, encoding and streaming`

Nature: project Standard Library design record; **non-normative**

Explicit project-owner approval: **2026-09-11**

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

## Purpose

This record closes the `LIB009-0` comparative design/selection checkpoint for a
small, scalable CSV facility in the Protos Standard Library.

The selected architecture is **Candidate A — small textual CSV kernel + explicit
local dialect + row streaming + layered interpretation**.

The design deliberately keeps CSV syntax processing separate from header
interpretation, schema/type conversion, filesystem/network authority, Encoding
selection/detection, spreadsheet policy, delimiter sniffing, tabular analytics
and object serialization.

The normative Protos specification under `spec/` remains authoritative. LIB009
uses already-defined Protos values, module behavior and I/O composition; it adds
no new Core semantic family.

## Selected module and distribution identity

The selected canonical module identity is:

```text
std:csv/CSV
```

with physical distribution source:

```text
protos/lib/csv/CSV.protos
```

The module is an ordinary Standard Library module. Importing it grants no
filesystem, process or network authority and creates no global registry,
background worker, shared cache or ambient mutable configuration.

## Selected base data model

CSV base data is ordinary Protos data:

```text
field = String
row   = Array<String>
table = Array<row>      // eager convenience only
```

There is no CSV-specific semantic value family, `Cell`, `CsvRow`, typed scalar
tag, hidden `null` convention or automatic domain-object mapping.

CSV cells are text at this boundary. Values such as:

```text
"00123"
"true"
"2026-09-11"
""
```

remain those exact Strings. Integer, Boolean, Date, null, schema and application
interpretation belong to explicit higher layers.

The parser does not reinterpret the first row as headers. Header mapping is an
independent future convenience because duplicate names, empty names, row-width
mismatch, ordering and key lookup are separate semantics.

## Selected default CSV profile

The default profile is strict and RFC-centered while preserving common portable
line-ending input:

- field delimiter is comma `,`;
- quote character is double quote `"`;
- a quote inside a quoted field is escaped by doubling it (`""`);
- quoted fields may contain field delimiters and record-separator characters;
- whitespace outside quoting is ordinary field data and is never silently
  trimmed;
- record boundaries accepted by the parser are CRLF, LF and CR;
- the default writer emits CRLF record separators;
- CR, LF and CRLF occurring *inside quoted fields* are preserved exactly in the
  resulting field String and are not normalized;
- malformed quoting, including bare/extraneous quotes inconsistent with the
  selected grammar, signals ordinary synchronous `Error`;
- empty input represents zero rows;
- an explicit empty record represents one row containing one empty String;
- blank records are not silently dropped;
- differing field counts across rows are preserved by the CSV syntax layer
  rather than rejected automatically.

Equal-width table requirements are higher-level tabular/schema invariants, not
CSV lexical validity.

The strict parser does not heuristically repair malformed input. A future
explicit permissive/import compatibility layer may be added without weakening
the default contract.

## Selected row-streaming architecture

Streaming is fundamental to LIB009, not an afterthought.

The selected scalable abstraction is a fresh parser whose state is local to that
parser and whose output unit is a complete row. The initial conceptual spelling
is:

```text
parser = CSV.rowParser(consumer)
parser.feed(textChunk)
parser.finish()
```

`feed` and `finish` are synchronous parser operations over already-decoded
Protos `String` chunks.

Each complete row is emitted exactly once as fresh ordinary `Array<String>`
data. Parsing is invariant under arbitrary chunk boundaries, including chunks
that split:

- delimiters;
- doubled quote sequences;
- CRLF record separators;
- quoted multiline field content.

The consumer is non-reentrant for one parser invocation. If consumer execution
signals failure, the parser becomes terminal rather than attempting to replay
already-emitted rows.

An already-emitted prefix is not rolled back merely because a later source
suffix is malformed. This is the same scalable streaming principle already used
by the JSON event parser, specialized here to CSV row emission rather than
inventing a generic Serializer/Parser hierarchy.

No parser-global registry, cache, worker pool or synchronization is selected.

## Selected eager operations and round-trip law

The selected initial semantic roles include:

```text
CSV.parse(text)
CSV.encode(rows)
CSV.rowParser(consumer)
```

`parse` is an eager convenience over the same CSV grammar and returns the whole
ordinary table representation.

`encode` accepts ordinary rows of Strings and emits default-profile CSV text. It
must validate the supplied row/field shape rather than silently coercing
arbitrary Protos values through `toString`-style behavior.

The important round-trip property is:

```text
CSV.parse(CSV.encode(rows)) == rows
```

for valid row data under the selected profile, modulo ordinary Protos collection
identity (content/structure is preserved; a fresh result is permitted).

LIB009 deliberately does **not** promise:

```text
CSV.encode(CSV.parse(source)) == source
```

because equivalent CSV can differ lexically in quoting choices and record
separator spelling. Source-preserving CSV editing would require a distinct
lexical/document model and is not part of the base table-data contract.

## Selected dialect architecture

Real-world delimited text variants are acknowledged, but no global dialect
registry is selected.

Dialect configuration must be:

- explicit;
- local to a call/parser/writer;
- ordinary data;
- free of ambient locale/culture;
- free of hidden mutation/shared registry state;
- limited at the base layer to genuinely lexical CSV/delimited-text choices.

The architecture must remain able to express at least delimiter, quote and
writer record-separator policy without making headers, comments, trimming,
`null`, type conversion, delimiter sniffing, malformed-input repair or
spreadsheet policy into implicit parser semantics.

**Deliberately deferred:** the exact public constructor/configuration spelling,
the exact ordinary-data slot shape, and the exact names of `...With`/configured
variants were not selected by LIB009-0. Implementation must not invent those
public contracts. Default-profile `parse`, `encode` and `rowParser` work can
proceed independently.

## Encoding, BOM and I/O boundary

CSV operates on semantic Protos `String` text.

Encoding/decoding and BOM policy remain owned by the existing explicit
`Encoding` + `TextReader` / `TextWriter` boundary. LIB009 therefore selects no:

- implicit UTF-8 assumption at a byte/file API boundary;
- charset/codepage registry;
- encoding autodetection;
- BOM-driven encoding switch;
- `CSV.readFile(path)` filesystem authority;
- URL/network input convenience.

For streaming I/O composition, a CSV adapter must consume `TextReader.readText()`
chunks, not use `readLine()` as the record parser, because quoted CSV fields may
legitimately contain line terminators.

TextReader/TextWriter ownership, cancellation, failure and underlying byte-source
semantics remain those already defined by Core I/O. CSV must not duplicate or
silently change them.

## Error and malformed-input policy

Default parsing is strict.

Malformed CSV signals ordinary `Error` at the point the CSV invariant is known
to be violated. LIB009-0 does not introduce a new Core Error family or a new
exception hierarchy.

Streaming preserves logical order:

- complete rows established before a later malformed suffix may already have
  been emitted;
- malformed syntax never causes silent repair in strict mode;
- consumer failure is terminal for that parser;
- `finish()` must reject incomplete quoted-field/escape state instead of
  fabricating completion.

Exact structured diagnostic payload/spelling is not selected here. If
implementation discovers that choosing public error metadata would be a new
substantive API contract, that choice must return through the explicit approval
gate.

## Resource and scalability contract

Target complexity:

```text
one-shot parse:  O(n) time, O(n) result storage
encode:          O(n) time, O(n) output storage
row streaming:   O(n) time,
                 O(current field + current row + bounded parser state)
                 retained data, excluding consumer-owned results
```

The base design contains no compulsory whole-file materialization for streaming
use.

No arbitrary universal field/record maximum becomes CSV semantics. The design
must permit explicit caller-selected protection for hostile/untrusted input,
without a process-global mutable limit.

**Deliberately deferred:** exact public limit API and exact accounting unit. If
existing Core mechanisms do not mechanically determine that contract, the
implementation must stop rather than invent it.

Independent parser instances require no coordination and can progress in
different Actors/Processes under ordinary Protos rules. LIB009 makes no promise
that one CSV source is automatically parsed in parallel: quoted multiline fields
make arbitrary partitioning non-trivial. A later ingestion layer may discover
safe boundaries and parallelize while preserving the same row semantics.

## Security boundary

Spreadsheet formula interpretation is not CSV syntax.

Strings beginning with characters such as `=`, `+`, `-` or `@` remain exact
field data. `CSV.encode` must not silently prefix, quote-for-policy, escape or
rewrite values merely because spreadsheet software may later execute them.

A future explicitly named spreadsheet-safe export policy/helper may transform
data intentionally at that consumer boundary.

Likewise, CSV parsing grants no filesystem/network/process authority and performs
no external resolution.

## Future extension path

The selected kernel intentionally leaves room for independent layers such as:

- header-to-record conveniences;
- schema validation and type conversion;
- W3C CSVW metadata;
- delimiter/profile sniffing returning explicit hypotheses/confidence;
- permissive or Excel-oriented import compatibility;
- spreadsheet-safe export;
- bounded/untrusted-input helpers;
- batch/vector/columnar consumers;
- Arrow/DataFrame adapters;
- specialized parallel file ingestion;
- lexical/source-preserving CSV editing.

None of these requires redefining what a base CSV field or row means.

## Comparative prior-art findings

The audit compared:

- RFC 4180;
- RFC4180-bis direction;
- W3C CSV on the Web / tabular-data model;
- Python `csv`;
- Go `encoding/csv`;
- Rust `csv`;
- Apache Commons CSV;
- .NET CsvHelper;
- Ruby CSV;
- Node `csv-parse` / `csv-stringify`;
- Papa Parse;
- Swift CodableCSV;
- Elixir NimbleCSV;
- Haskell cassava;
- DuckDB CSV scanning;
- Apache Arrow CSV.

Principal lessons:

- **NimbleCSV** is the closest philosophical precedent: small raw-row
  parsing/dumping, eager + streaming operation, headers/casting composed above.
- **Go** demonstrates a small strict incremental reader with explicit leniency,
  but Protos deliberately avoids silently dropping blank records or normalizing
  CRLF inside quoted field data.
- **Rust** strongly supports separation between raw CSV records and optional
  typed/domain mapping.
- **Node csv-parse** supplies strong evidence for streaming, explicit bounds and
  large-input operation while also illustrating how option surfaces can grow.
- **Python** validates explicit dialects but its global registry/global field
  limit and lossy `None` convention are poor Protos precedents.
- **Apache Commons CSV** and **CsvHelper** are mature evidence for real-world
  compatibility but demonstrate policy accretion when headers, nulls, comments,
  culture, reflection, autodetection, DB conventions and security policy become
  one CSV institution.
- **CSVW** shows that schema/datatype/metadata interpretation naturally belongs
  above CSV text.
- **DuckDB** and **Arrow** demonstrate that high-scale parsing, conversion,
  inference and columnar/batch execution can remain separate phases rather than
  forcing typed-table semantics into the lexical kernel.

## Candidate scorecard

Scores are decision aids, not authority. `H` = high-confidence comparison,
`M` = medium.

| Candidate | Correct | Protos | Future | Scale | Simple | Portable | Cost | Failure | Reversible | Evidence | Total |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **A — kernel + dialect + row stream** | **5/H** | **5/H** | **5/H** | **5/H** | **4/H** | **5/H** | **5/H** | **5/H** | **5/H** | **5/H** | **49** |
| B — fixed RFC eager codec | 5/H | 5/H | 3/H | 2/H | 5/H | 5/H | 4/H | 5/H | 3/H | 5/H | 42 |
| C — broad dialect framework | 4/H | 3/H | 4/H | 4/H | 3/H | 4/H | 4/H | 4/H | 3/M | 5/H | 38 |
| D — typed mapping in CSV | 4/H | 2/H | 4/H | 4/H | 2/H | 3/M | 3/H | 4/H | 3/M | 5/H | 34 |
| E — ingestion engine base | 5/H | 2/H | 4/H | 5/H | 2/H | 3/M | 3/H | 4/H | 3/M | 5/H | 36 |
| F — permissive autodetect | 2/H | 2/H | 3/M | 4/H | 3/M | 3/M | 3/M | 2/H | 3/M | 5/H | 30 |

Candidate A wins because its boundaries preserve the useful future capabilities
demonstrated by the broader libraries without making those capabilities costs or
semantics of every CSV operation.

## Rejected initial directions

The initial base rejects:

- fixed-RFC eager-only architecture as insufficiently scalable/extensible;
- global named dialect registries;
- hidden delimiter/header/type autodetection;
- parser-owned header-to-Map conversion;
- implicit number/Boolean/date/null conversion;
- reflection/object mapping as CSV semantics;
- ambient locale/culture behavior;
- filesystem/network authority in the codec;
- parser-owned encoding/BOM detection;
- permissive quote repair by default;
- silent spreadsheet-formula rewriting;
- typed/columnar ingestion-engine semantics as the base CSV abstraction;
- obligatory whole-file materialization.

## Future stress test

### Very large files

The row parser retains only local parse state/current field/current row.
Applications can consume/discard rows without retaining the whole source.

### Many concurrent consumers

Each parser instance owns independent local state. No global dialect registry,
cache, worker pool or synchronization bottleneck is required.

### Alternative runtime / native implementation

The contract is over Protos Strings, Arrays, closures/modules and existing I/O
boundaries, not JVM readers, reflection, host locale or Java charset APIs.

### Future Arrow/DataFrame analytics

A batch/columnar consumer can sit above row streaming, or a specialized scanner
can expose a compatible batch layer without changing ordinary CSV field
semantics.

### Future CSVW/schema

Metadata/schema/type conversion remains an independent interpreter over String
rows.

### Future parallel scanning

A specialized ingestion layer may establish safe record boundaries and
parallelize physical scanning without changing `CSV.parse` or row meaning.

### Future browser/spreadsheet compatibility

Explicit import/export policies can be layered above exact CSV data rather than
making heuristic repair or formula rewriting universal.

### Regret scenario and escape path

The most plausible regret is that analytics-heavy workloads find per-row
`Array<String>` allocation too expensive.

The escape path remains open: add a batch/columnar consumer or specialized
scanner beneath/alongside the convenience row API while preserving the same CSV
lexical rules and String-cell meaning. The base contract therefore does not
force high-scale engines to materialize every row as a long-lived ordinary
Array.

## Strongest argument against the selected architecture

A broad Python/Commons/CsvHelper-style API is more convenient immediately.
Typical callers often want `headers=true`, semicolon autodetection, typed
integers/dates, comments, trimming, null markers and direct file reading in one
place.

The selected design intentionally refuses that convenience at the kernel
boundary because each option carries independent semantics, authority,
heuristics or compatibility cost. Those facilities can still be added as
composable higher layers without turning `CSV` itself into a large ambient
policy institution.

## LIB009-B canonical writer policy

Status: **APPROVED — 2026-09-11**

The project owner approved Candidate A for the default-profile writer:

- quote a field if and only if its exact String contains comma, double quote, CR
  or LF;
- inside a quoted field, every double quote is doubled;
- do not quote merely because a field is empty, has leading/trailing whitespace,
  looks numeric/Boolean/date-like, begins with spreadsheet-formula characters,
  resembles a database sentinel, or carries any application-specific meaning;
- emit CRLF after every encoded row, including the final row;
- an empty table encodes as the empty String;
- an empty row (`Array()`) is invalid encode input because the already-ratified
  parser model has no zero-field record representation: an explicit blank record
  parses as one empty String field;
- `CSV.parse(CSV.encode(rows)) == rows` remains the structural law for valid
  encode input;
- custom dialect representation/spelling remains independently deferred.

This is the smallest deterministic writer policy that preserves exact field text,
keeps application/DB/spreadsheet compatibility policy out of the CSV kernel and
lets later streaming output serialize each row independently without buffering
to discover whether it is the last row.

## Intentionally deferred

LIB009-0 does not decide:

- exact public spelling and exact slot shape of the explicit dialect
  constructor/configuration value;
- exact names of configured `parse` / `encode` / `rowParser` variants;
- header/record convenience API;
- delimiter/profile auto-sniffing;
- permissive/Excel compatibility profiles;
- comment or trimming policies;
- null/type/schema conversion helpers;
- CSVW metadata integration;
- spreadsheet-safe export API;
- source-preserving lexical/document model;
- batch/columnar/Arrow/DataFrame adapters;
- parallel physical file scanning;
- exact resource-limit public API/accounting unit;
- richer public error metadata beyond ordinary `Error`;
- any generic serialization hierarchy.

A dependent implementation slice that reaches one of these questions must stop
at the approval boundary instead of selecting it implicitly.

## Initial implementation decomposition

The approved architecture permits bounded implementation work to proceed without
crossing the deferred dialect/API questions:

```text
LIB009-A  strict default-profile row parsing + eager parse
LIB009-B  default-profile encode + structural round-trip
LIB009-C  incremental rowParser + chunk-boundary/lifecycle stress
LIB009-D  TextReader/TextWriter composition over the published CSV kernel
LIB009-E  integrated conformance, scalability and Actor-independence closure
```

The explicit custom-dialect public surface is a later slice and remains
`NEEDS_USER_DECISION` for its exact public representation/spelling.

Every implementation slice must stop if it discovers a substantive semantic or
architectural choice not closed by this record.
