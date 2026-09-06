# LIB003 JSON Design Record

Status: initial design closed for implementation; LIB003-A selected for first publication
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

## Streaming direction

Streaming is a later JSON-specific layer:

```text
source -> JSON parser -> JSON-specific events -> consumer
```

and the reverse for output.

XML/YAML may reuse the composition pattern but own their own event vocabularies.
No generic Serializer hierarchy is justified by LIB003.

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

- JSON-specific incremental parse/write events or equivalent streaming surface;
- TextReader/TextWriter composition;
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
