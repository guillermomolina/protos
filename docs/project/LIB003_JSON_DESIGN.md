# LIB003 JSON Design Record

Status: LIB003-A/B/C/D/E published; initial LIB003 JSON scope CLOSED
Work item: `LIB003`
Nature: Project design record; **non-normative**
Cross-cutting context: `docs/design/STRUCTURED_DATA_AND_SERIALIZATION.md`

## Purpose

This record converts the cross-format structured-data investigation into a
bounded JSON implementation contract. LIB003 is JSON, not generic Protos
serialization.

The normative Protos specification under `spec/` remains authoritative. LIB003
must use already-standardized Protos mechanisms. A genuinely missing
Core/runtime semantic prerequisite must be tracked and resolved outside LIB003
before the library relies on it.

## Scope decision

LIB003 provides a JSON-specific data representation and codec surface built from
ordinary Protos values and modules.

It does not define:

- reflection-based serialization of arbitrary Protos objects;
- `Serializable`, `Serializer`, `Deserializer`, `Format`, a universal `Node`,
  universal `Value`, or another cross-format institution;
- object-graph/program persistence;
- reference identity for arbitrary object graphs;
- YAML or XML data models;
- JSON syntax in the Protos grammar;
- a new Core semantic value family;
- a new runtime tag, `typeOf`, or `instanceof`;
- JVM-specific JSON semantics.

A later multi-format abstraction must be earned by independently useful formats.

## Repository constraints established by the audit

- Standard Library functionality belongs primarily in ordinary modules outside
  `protos/lib/core/`.
- `std:` names are case-sensitive direct distribution identities.
- Delegation is not semantic value-family membership.
- Protos exposes no universal type-family classifier suitable for imitating
  JavaScript `JSON.stringify`.
- `String` denotes valid Unicode scalar-value sequences.
- `Integer` is unbounded and exact; `Float` is IEEE binary64.
- Core Arrays and Maps already provide the required container machinery.
- Reflection exposes ordinary object structure, not persistence intent.
- Standard module instances are Actor-local; pure data can transfer under the
  existing graph-transfer rules.
- LIB003-A uses the ordinary mutation states produced by its construction mechanisms:
  node/Number-record/Object-Map components are fresh/open, while Array payloads are
  fresh frozen standard Arrays because trailing rest capture has that existing
  language contract. LIB003 introduces no private freezing primitive.
- I015 Encoding/Text I/O is closed; later adapters can compose with it directly.
- Core has no normative general Float-to-decimal String protocol. JSON must not
  promote JVM `Double.toString` into Protos semantics.

## Prior-art findings

The audit compared semantic models rather than copying familiar APIs.

- **RFC 8259 JSON:** six JSON value categories; String object names; duplicate
  names cause interoperability divergence; Number is decimal syntax, not
  intrinsically binary64.
- **RFC 7493 I-JSON:** strict duplicate-name and Unicode-scalar discipline are
  useful defaults, but Protos should not import unrelated restrictions that
  narrow otherwise-valid Protos Strings.
- **RFC 8785 JCS:** canonical JSON is a separate profile with separate sorting
  and binary64-number rules.
- **JavaScript:** reflection-first object serialization immediately imports
  property-selection, hooks, unsupported-value, cycle/reference and binary64
  policies.
- **Python `json`:** native mappings are convenient, while `parse_int` and
  `parse_float` demonstrate that lexical parsing and target numeric type can be
  separated.
- **Jakarta JSON-P / JSON-B:** JSON tree/streaming and application-object
  binding are distinct responsibilities.
- **Jackson:** multi-format infrastructure can be shared, but XML still needs
  XML-specific rules because a JSON-shaped model is not a complete XML model.
- **Smalltalk / Pharo NeoJSON:** generic JSON data and domain-object mapping can
  remain separate even in a dynamic object system.
- **STON / Fuel:** object persistence needs identity/reference/type/reconstruction
  machinery that plain JSON does not possess.
- **Self Transporter / mirrors:** live prototype-object structure does not encode
  persistence intent; explicit transport/reflection metadata became necessary.
- **Erlang/OTP:** JSON uses small format-specific mappings/callbacks, while xmerl
  uses XML-specific events rather than forcing XML through JSON events.
- **Serde / serde_json:** a broader serialization data model can be useful after
  many formats justify it; JSON-specific `Value` remains a separate concern.
- **Haskell Aeson / Scientific:** coefficient plus base-10 exponent represents
  arbitrary finite JSON decimals exactly without collapsing to binary64.
- **C++ nlohmann / Boost.JSON:** explicit JSON values coexist with explicit
  domain conversions and separate SAX/event APIs.
- **C# System.Text.Json:** DOM/tree, typed binding, raw text and
  reference-preserving object serialization are separate capabilities.
- **Go JSON:** newer APIs favor stricter duplicate/UTF-8 defaults after the
  interoperability and security cost of historical permissiveness.
- **YAML 1.2:** YAML is a tagged graph with aliases/shared identity/cycles and
  arbitrary mapping keys, not merely JSON plus syntax.
- **XML Infoset:** XML is an ordered document model with attributes, namespaces
  and mixed content, not a String-keyed JSON object tree.
- **CBOR / MessagePack:** JSON-adjacent binary formats still add byte strings,
  tags/extensions, arbitrary keys or different numeric domains.

Primary references include:

- <https://www.rfc-editor.org/rfc/rfc8259>
- <https://www.rfc-editor.org/rfc/rfc7493>
- <https://www.rfc-editor.org/rfc/rfc8785>
- <https://yaml.org/spec/1.2.2/>
- <https://www.w3.org/TR/xml-infoset/>
- <https://www.erlang.org/doc/apps/stdlib/json.html>
- <https://www.erlang.org/doc/apps/xmerl/xmerl_sax_parser.html>
- <https://serde.rs/data-model.html>
- <https://handbook.selflanguage.org/4.4/howtoprg.html>
- <https://handbook.selflanguage.org/2024.1/mirrors.html>

## Rejected directions

### Automatic arbitrary-object encoding

Rejected. Visible slots do not identify which state is data, what delegation
means for persistence, how Closures/authority should behave, or how a useful
object should be reconstructed. This would turn JSON into accidental object
persistence.

### Native Protos values as the only JSON tree model

A direct `null/Boolean/String/Number/Array/Map` mapping is convenient in
languages with cheap semantic type discrimination. Protos deliberately does not
equate delegation with family membership and has no universal type classifier.

Native values remain useful payloads inside an explicit JSON model, but are not
implicitly reclassified as JSON.

### JSON Number as Float

Rejected. JSON decimals can exceed binary64 precision/range. Mapping every
number to Float would lose information.

### JSON Number as Integer-or-Float selected from spelling

Rejected as the fundamental model. `1`, `1.0` and `1e0` should not acquire
different Protos numeric-family semantics merely because their JSON spellings
differ.

### Universal structured-data node

Rejected. JSON is a tree, YAML is a tagged graph, and XML has its own document
model. A universal node would accumulate format-specific exceptions.

### Reference IDs / cycles in JSON

Rejected. `$id`/`$ref`-style conventions define a separate object-serialization
protocol rather than plain JSON.

## Selected module identity

The initial module is:

```text
std:json/JSON
```

with source:

```text
protos/lib/json/JSON.protos
```

No lowercase alias is provided.

## Selected JSON data model

Every constructor-created JSON node is a fresh open ordinary object with:

```text
kind
value
```

`kind` is one exact semantic String:

```text
"null"
"boolean"
"string"
"number"
"array"
"object"
```

The tag is ordinary data, not a runtime type category. Behavior stays on the
Actor-local JSON module rather than being copied into each data node.

### Mutation-state decision

LIB003-A does not impose one synthetic mutation state across every JSON
representation component.

The state follows the ordinary Protos construction mechanism actually used:

- JSON node objects are fresh and open;
- JSON Number coefficient/exponent record objects are fresh and open;
- JSON Object payload Maps created by `Map()` are fresh and open;
- JSON Array payloads are the trailing rest-argument Array bound to
  `array(...nodes)`, and Protos rest capture is a **fresh frozen standard
  Array**.

That frozen Array state is not produced by a hidden LIB003 primitive or by a
library call to `freeze()`. It is the existing closure-parameter contract for
rest capture.

Immutability is not itself JSON semantics, so LIB003 does not add a Core/runtime
boundary merely to equalize these mutation states. Consumers must validate the
representation they receive:

- callers may mutate open node/record/Map components after construction;
- a mutated tree may become malformed;
- Array payload structure remains fixed because rest capture is frozen;
- parser-created values must follow the same public representation contract;
- encoder/streaming consumers validate representation invariants and reject
  malformed/cyclic data;
- Actor transfer preserves the existing mutation state of copied ordinary
  graph components.

A later convenience for deeply immutable JSON data would require a separately
justified ordinary-library design and must not redefine this initial JSON model.

### Null

```text
kind  = "null"
value = null
```

### Boolean

```text
kind  = "boolean"
value = true | false
```

Only canonical Protos Booleans are accepted.

### String

```text
kind  = "string"
value = semantic Protos String
```

No Unicode normalization occurs. All Unicode scalar values representable by a
Protos String remain admissible, including Unicode noncharacters. LIB003 does
not narrow the general String domain solely to copy I-JSON.

A parser must reject unpaired surrogate escapes because they cannot produce a
semantic Protos String.

### Number

```text
kind  = "number"
value = fresh open ordinary object:
    coefficient
    exponent
```

Both fields are ordinary unbounded Protos `Integer` values.

LIB003-A validates that exact family through the existing strict
`Integer.div(1)` receiver domain. It deliberately does not call the
`Integer(value)` conversion factory merely to classify a value: conversion and
family validation are different responsibilities, and delegated/fixed-width
values must not be reclassified as ordinary unbounded Integer payloads.

The mathematical value is exactly:

```text
coefficient * 10^exponent
```

This represents every finite JSON number without Float, a Decimal Core family,
or host/JVM number formatting.

The pair is **not eagerly normalized**. `JSON.number(c, e)` stores the exact
validated pair supplied by the caller. This avoids imposing hidden potentially
unbounded normalization work merely to construct a node. A later explicit JSON
semantic comparison can compare mathematical decimal values.

Negative JSON zero does not create a new signed-decimal-zero category. Once its
coefficient is mathematical zero, the sign is not part of the semantic tree.
A future raw/lossless parser may preserve lexical `-0` separately if justified.

### Array

```text
kind  = "array"
value = fresh frozen rest-capture Array of JSON nodes
```

Order is significant. The payload is the fresh frozen standard Array created
by Protos trailing-rest parameter binding for `array(...nodes)`. Child nodes are
not copied, frozen, or otherwise mutated. Later parser/encoder operations still
validate the complete representation rather than relying on constructor
provenance.

### Object

```text
kind  = "object"
value = fresh open Map:
    semantic String -> JSON node
```

The public constructor accepts alternating name/node arguments:

```text
JSON.object(
    "name", JSON.string("Ada"),
    "age",  JSON.number(36, 0)
)
```

Zero arguments produce an empty object. Odd argument count, non-String name,
non-node immediate value, or duplicate semantic String name signals an Error.
Duplicate names never silently use first-wins or last-wins replacement.

Map insertion order is retained for deterministic traversal/ordinary encoding,
but is not promoted to JSON object semantic equality.

## LIB003-A public surface

LIB003-A publishes exactly:

```text
nullValue()
boolean(value)
string(value)
number(coefficient, exponent)
array(...nodes)
object(...nameNodePairs)
```

No `parse`, `encode`, streaming, raw-source, canonicalization, reflection hook,
`toJSON`, serializer interface, schema or object-binding API belongs to A.

## Failure discipline

Constructors signal Error when explicit library invariants are violated. They do
not silently coerce Numbers to Booleans, arbitrary objects to Strings,
Float/fixed-width numeric families to the decimal pair, arbitrary values to JSON
nodes, or non-String object keys to Strings.

A dedicated JSON error taxonomy is not introduced by A. Later parser/encoder
work should add narrower categories only if callers gain a real semantic benefit.

## Equality and identity

JSON nodes remain ordinary identity-bearing Protos objects. LIB003-A does not
override `==`, `===`, hashing, Map or IdentityMap semantics.

If JSON semantic comparison later proves useful, it must be an explicit JSON
operation. JSON Number comparison would compare mathematical decimal values
rather than node identity.

## Mutation and Actor transfer

JSON constructor-owned nodes, Number records and Object Maps are fresh/open ordinary
Protos data; Array payloads are fresh frozen standard rest-capture Arrays. LIB003-A
does not depend on an implementation-only freezing shortcut or add a new Core/runtime
boundary merely to equalize those ordinary mutation states.

The module instance contains Closures and remains Actor-local. The pure data
tree contains no module Closure merely because JSON behavior exists and remains
eligible for ordinary Actor graph transfer when its payloads are transferable.

JSON adds no remote identity, interning or singleton exception.

## Parsing direction

The initial parser will be strict RFC-8259-oriented JSON over semantic Protos
String input:

- any JSON top-level value;
- no comments/trailing commas;
- no NaN/infinities/non-JSON numbers;
- reject duplicate names after escape decoding;
- reject unpaired surrogate escapes;
- preserve all other valid Protos Unicode scalar values without normalization;
- exact coefficient/exponent Number construction;
- array order preserved;
- object source order retained in the resulting Map.

Because `String.at` is grapheme-oriented, JSON tokenization must not accidentally
be defined in terms of grapheme clusters. Existing Encoding/Bytes machinery may
be used for scalar/octet processing.

The parser must avoid making JVM call-stack depth an accidental portable nesting
limit. Fixed portable resource limits, if introduced, require an explicit
library contract.

## LIB003-B implementation closure

LIB003-B publishes `parse(text)` on `std:json/JSON`, implemented entirely in
ordinary Protos source.

The parser requires a semantic Protos String and scans
`Encoding.UTF8.encode(text)` octets. JSON structural syntax is ASCII, avoiding
grapheme-oriented `String.at()` tokenization while preserving semantic Unicode
String content.

Container nesting uses an explicit linked stack of ordinary frame objects rather
than recursive descent, so JSON nesting depth consumes heap state rather than one
host/JVM call frame per container. No arbitrary portable nesting limit is added.

For each non-whitespace octet in normal mode, the parser snapshots whether the
octet begins at top level before consuming it. That octet is then routed exactly
once through either top-level value start or the already-existing container frame;
opening `[` or `{` cannot change depth and cause the same delimiter to be
reprocessed inside the newly opened container.

JSON Arrays use balanced power-of-two Array chunks. Appending performs binary-carry
merges and closing combines remaining chunks before invoking the existing
`array(...nodes)` constructor once. This avoids quadratic repeated
`Array(...old, value)` growth; helper recursion is logarithmic in one Array's
element count and independent of JSON nesting depth.

Object member names are decoded to semantic Strings before insertion. Duplicate
decoded names are rejected before a later value can replace the earlier entry;
ordinary Map insertion order retains source member order.

Number grammar is validated directly into an unbounded Integer coefficient and
base-10 exponent. No Float conversion, JVM decimal parser, NaN, infinity, leading
`+`, leading-zero extension, or incomplete fraction/exponent form is accepted.

JSON escapes are decoded explicitly. High-surrogate `\uXXXX` escapes require an
immediately following low-surrogate `\uXXXX`; unpaired low/high surrogates fail.
No Unicode normalization is performed.

Malformed syntax, duplicate names, invalid escape/surrogate structure, incomplete
tokens, extra top-level values and non-String input signal ordinary Error. LIB003-B
does not introduce a JSON-specific Error taxonomy.

## Encoding direction

The ordinary encoder will accept JSON nodes, not arbitrary application objects.

It must validate representation before relying on payload shape, emit exact
decimal Numbers without Float formatting, perform JSON String escaping, use the
retained Map traversal order for deterministic ordinary output, and reject
malformed/cyclic trees rather than inventing references.

Canonical JSON/JCS remains a separate possible profile.

## LIB003-C implementation closure

LIB003-C publishes `encode(node)` on `std:json/JSON`, implemented entirely in
ordinary Protos source.

The encoder accepts the explicit LIB003 JSON tree only. It validates every
visited node's exact `kind` tag and the representation invariant owned by that
kind before emitting the corresponding value. It does not reflect over arbitrary
application objects, invoke a `toJSON` hook, introduce a runtime JSON family, or
add a generic Serializer abstraction.

String emission scans the semantic String's UTF-8 octets. Quote and backslash
are escaped, control octets U+0000 through U+001F are emitted as lowercase
`\u00xx`, and all other UTF-8 octets are preserved without Unicode
normalization. This is ordinary deterministic JSON output, not a
source-preserving or canonical-JCS profile.

Number emission validates the stored coefficient and exponent through the same
strict unbounded-Integer receiver domain used by the constructors. The
coefficient is written as an exact base-10 Integer. A nonzero exponent is
written as `e` followed by its exact base-10 Integer. No Float conversion,
binary64 formatting, host decimal formatter, eager normalization, or
precision/range narrowing occurs.

Arrays preserve payload order. Objects traverse their ordinary Map payload in
retained insertion order, validate each key as a semantic String, and emit that
order deterministically.

Cycle detection uses an ordinary local `IdentityMap` containing only JSON node
objects on the active traversal path. Re-entering an active node signals
ordinary Error; nodes are removed after their subtree completes. Consequently,
shared but acyclic child nodes are valid and are encoded independently at each
tree occurrence. No reference IDs or object-persistence semantics are invented.

Malformed node tags, scalar payloads, Number records, object keys, container
payloads, child nodes, or cyclic trees signal ordinary Error. Output is built in
a fresh local Bytes buffer and returned only after the complete traversal
succeeds, so an encoding failure does not expose a partial String result.

## Streaming direction

Streaming is a later JSON-specific layer:

```text
source -> JSON parser -> JSON-specific events -> consumer
```

and the reverse for output.

XML/YAML may reuse the composition pattern but own their own event vocabularies.
No generic Serializer hierarchy is justified by LIB003.


### LIB003-D decomposition and D1 closure

The current-main audit separates the original LIB003-D work into three ordered
publication slices because incremental lexical state, event-output validation,
and asynchronous I/O lifecycle composition have independently meaningful failure
surfaces:

- `LIB003-D1` — JSON-specific incremental parser events;
- `LIB003-D2` — JSON-specific incremental event writer;
- `LIB003-D3` — TextReader/TextWriter and byte/Encoding adapters.

D1 publishes `JSON.eventParser(consumer)`. Each successful call returns one
fresh ordinary parser object exposing synchronous `feed(text)` and `finish()`
operations. `feed` accepts only semantic Protos String input and consumes that
chunk immediately; `finish` marks semantic EOF. Empty chunks are allowed. A
parser is single-use after successful finish, and syntax/consumer failure makes
that parser unusable.

The consumer is called synchronously with fresh ordinary JSON event objects whose
`kind` / `value` vocabulary is format-specific:

- `objectStart`, `objectEnd`, `arrayStart`, `arrayEnd` with `value === null`;
- `name` with the decoded semantic String member name;
- `null`, `boolean`, `string`, and `number` for scalar values, where Number value
  is the exact ordinary `{ coefficient, exponent }` decimal record already used
  by the JSON tree model.

Events are emitted as soon as their complete JSON token or structural boundary
is established. Therefore an already-emitted prefix is not rolled back if later
input proves the overall document malformed; this is the deliberate streaming
contract rather than a transactional tree-parse promise. Consumer invocation is
non-reentrant for the parser. A consumer failure propagates and leaves the
parser terminal instead of attempting to replay or duplicate an emitted event.

D1 preserves the strict LIB003-B lexical grammar across arbitrary String chunk
boundaries: JSON whitespace, literals, exact decimal grammar, escape handling,
strict surrogate pairing, decoded duplicate-name rejection, one top-level value,
and no trailing non-whitespace data. String chunks are independently valid
Protos Strings; JSON escape/token state may span chunks without changing the
result. No Unicode normalization, Float conversion, host JSON parser, or
implementation-selected duplicate policy is introduced.

Container state uses an explicit linked frame stack, so JSON nesting does not use
host recursion. D1 does not materialize Array element collections or a complete
JSON tree. Retained parser state is limited to open-container state, decoded
member names needed to reject duplicates in currently open objects, and the
currently incomplete String/Number token. Final resource-stress validation
remains owned by LIB003-E.

### LIB003-D2 implementation closure

D2 publishes `JSON.eventWriter(consumer)`. Each call creates one fresh ordinary
writer exposing synchronous `feed(event)` and `finish()` operations. The accepted
event vocabulary is exactly the JSON-specific D1 vocabulary; D2 does not create
a generic serializer/event hierarchy or reuse JSON names for YAML/XML.

Each successful `feed` validates one complete event against the writer's current
JSON structural state before invoking the consumer. A valid event contributes
exactly one non-empty semantic String chunk. Array separators, object member
separators and colons are inserted deterministically by the writer; callers do
not provide punctuation events.

Object member `name` events require semantic Strings and are escaped through the
published ordinary JSON encoder contract. Duplicate names are rejected per open
object using a local Map. Scalar null/Boolean/String/Number events reuse the
published constructor/encoder validation, so exact decimal coefficient/exponent
semantics, String escaping, Unicode preservation and invalid scalar domains do
not acquire a second implementation contract.

The writer maintains an explicit linked container stack. `objectEnd` and
`arrayEnd` must match the current open container, object names must be followed
by exactly one value, only one root value is accepted, and `finish()` succeeds
only after that root is complete and every container is closed. A successful
finish makes the writer single-use.

Consumer callbacks are synchronous and non-reentrant for the writer. A consumer
failure propagates and leaves that writer terminal. Output already accepted by a
consumer is not rolled back when a later event is invalid; that is the intended
incremental streaming boundary. No TextWriter/Future/ownership or byte-I/O
lifecycle semantics are introduced by D2; those remain the sole purpose of D3.


### LIB003-D3 implementation closure

D3 publishes two ordinary protocol-composition helpers over the already
standardized text-I/O surface:

- `JSON.readEvents(textReader, consumer)` creates a fresh incremental input
  adapter exposing `read() -> Future<Boolean>`;
- `JSON.writeEvents(textWriter)` creates a fresh incremental output adapter
  exposing `feed(event) -> Future` and `finish() -> Future`.

The adapters deliberately receive an already-constructed text reader/writer
rather than hiding `TextReader(source, encoding)`,
`TextReader.owning(source, encoding)`, `TextWriter(target, encoding)`, or
`TextWriter.owning(target, encoding)`. Byte authority, exact Encoding-family
validation, borrowing versus owning choice, codec state, close/flush behavior,
and underlying resource lifetime therefore remain explicit at the standard
I/O boundary. Typical byte/Encoding composition is simply:

```text
JSON.readEvents(TextReader(source, encoding), consumer)
JSON.writeEvents(TextWriter(target, encoding))
```

with the corresponding `.owning(...)` construction when ownership is intended.
D3 does not acquire, close, flush, or enlarge authority on behalf of the caller.

`read()` accepts at most one outstanding adapter operation. It invokes exactly
one ordered `TextReader.readText()` operation. A non-null String chunk is fed
synchronously into the published D1 parser and resolves the adapter Future to
canonical `true`; a `null` EOF finalizes D1 and resolves to canonical `false`.
Thus empty String chunks are progress results rather than EOF. Parser/consumer
failure fails the continuation Future and leaves the adapter terminal. Reuse
after successful EOF is rejected.

`feed(event)` likewise permits at most one outstanding adapter operation. D2
validates and converts that event to its single deterministic non-empty String
chunk before the adapter invokes `TextWriter.writeText(chunk)`. The returned
Future resolves only after that ordered text write succeeds. A downstream
failure/cancellation leaves the adapter terminal rather than allowing later JSON
state to run past an uncertain output operation.

`finish()` first validates D2 structural completion and then issues
`TextWriter.writeText("")` as an ordered zero-output barrier. Core TextWriter
semantics define that empty write as contributing no encoded bytes and no encoder
state transition while still participating in the writer's ordering/failure
domain. D3 therefore remains Future-shaped without inventing a second flush,
close, encoder-finalization, or commitment contract.

The one-outstanding-operation rule is deliberate backpressure. D3 does not queue
an unbounded number of JSON events/chunks, create another I/O ordering domain, or
hide suspension. Callers observe the returned Future through ordinary Future
mechanisms before issuing the next adapter operation. Cancelling the returned
continuation Future retains Core downstream-only Future cancellation: it does not
retroactively cancel an already accepted underlying TextReader/TextWriter
operation. The adapter remains terminal after that uncertain operation instead
of attempting to guess or reconstruct consumed input/output progress.

No Java/runtime boundary, generic Serializer hierarchy, JSON-specific Future
kind, implicit Encoding, ownership inference, hidden close, or new Core I/O
semantics are introduced.

### LIB003-E final closure

LIB003-E closes the bounded initial JSON scope without adding another JSON API.
The executable E1 tranche is published at
`e215952e5459782e95f0d9c73c7bffc948bac943`. Its Protos-source conformance
covers deep valid and truncated parsing, explicit D1/D2 event-stack streaming,
large Array materialization, a large exact-decimal round-trip, long incremental
String tokenization, and a complete B -> C -> D1 -> D2 -> B round-trip. The same
publication also re-ran the existing Java-specific parser implementation stress,
the JSON Actor graph-transfer boundary regression, and the complete Maven suite.

Those stress cases are regression evidence for the implementation properties
already selected by B/D; they do not standardize a portable maximum JSON input
size, nesting depth, Array length, decimal digit count, or event count. Resource
limits remain implementation concerns unless a later normative design explicitly
standardizes one.

E2 performs the final architecture audit over the published module rather than
adding behavior. The exact-case `protos/lib/json/JSON.protos` file remains the
only distributable source in the JSON module directory. No lowercase module
alias, `Serializable`/`Serializer`/`Deserializer` hierarchy, `toJSON`-style
object-binding hook, YAML/XML event vocabulary, runtime `typeOf`/`instanceof`
classifier, `$id`/`$ref` reference convention, generic cross-format Node/Value
institution, or identity-preserving object-persistence mechanism has been
introduced.

The Actor-local JSON module versus transferable ordinary JSON data boundary,
strict semantic-String parsing, exact decimal representation, deterministic
validated encoding, JSON-specific event vocabulary, and explicit
TextReader/TextWriter ownership/Encoding boundary therefore remain the complete
initial surface. Raw/lossless JSON, canonicalization, schema/pointer/patch,
reflection binding, YAML/XML/CBOR-style formats, and object persistence remain
separately scoped future work.

E2 changes only project documentation/governance. It does not modify
`protos/lib/**`, `src/**`, `spec/**`, the Maven implementation version, or
normative Protos semantics. With A/B/C/D/E published, top-level LIB003 is CLOSED.

## Raw / lossless JSON

The initial tree is semantic/structural data, not a source-preserving CST. It
does not promise exact whitespace, escape spelling, exponent spelling, redundant
decimal spelling or source ranges.

A future raw/lossless API must be a separately justified capability.

## Object persistence remains separate

A future Protos persistence design would have to address identity/sharing,
cycles, delegation, Closures, modules, authority, reconstruction and
environment-dependent state independently. Self Transporter and Smalltalk
STON/Fuel are direct evidence that these are real additional semantics.

## Dependency conclusion

LIB003-A has no unresolved normative blocker.

Required foundations are already available:

- I003 Standard String;
- I004 Array completion;
- I005 Standard Map;
- I007 Error infrastructure;
- I008 Modules;
- I012 Standard Bytes;
- I015 Encoding / Text I/O.

LIB001 is useful precedent but not a runtime dependency. LIB002 convenience
helpers are not required by LIB003-A/B/C core work.

No production Java boundary is required for LIB003-A.

## Planned implementation slices

### LIB003-A — explicit JSON data model

- persist this design record;
- publish exact-case `std:json/JSON`;
- six fresh ordinary JSON node constructors;
- exact decimal coefficient/exponent Number data;
- duplicate object-name rejection;
- Actor-local module versus transferable pure-data conformance.

### LIB003-B — strict String parser

- RFC-8259-oriented semantic-String parser;
- exact decimal parsing;
- duplicate-name rejection;
- strict escape/surrogate handling;
- deterministic order;
- no host-recursion nesting contract.

### LIB003-C — ordinary String encoder

- validated JSON tree to String;
- exact decimal emission without Float formatting;
- JSON String escaping;
- retained object traversal order;
- malformed-tree/cycle rejection.

### LIB003-D — streaming and text/byte adapters

Decomposed after the D1 current-main audit:

- LIB003-D1 — JSON-specific incremental parser events;
- LIB003-D2 — JSON-specific incremental event writer;
- LIB003-D3 — TextReader/TextWriter and byte/Encoding composition;
- no generic Serializer hierarchy.

### LIB003-E — final conformance and closure

- cross-slice validation;
- malformed/security/resource stress cases;
- Actor-transfer regression coverage;
- confirm no accidental YAML/XML/object-persistence commitments;
- close top-level LIB003 when the initial JSON surface is complete.

## Deferred, non-blocking extensions

- JSON5/comments/trailing commas;
- JSON Schema;
- JSON Pointer/Patch;
- JCS/canonical JSON;
- raw/CST JSON;
- reflection-based application object binding;
- generic multi-format serialization;
- YAML/XML;
- CBOR/MessagePack;
- identity-preserving Protos object persistence.
