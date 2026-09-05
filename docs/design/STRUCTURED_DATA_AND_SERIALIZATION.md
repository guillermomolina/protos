# Structured Data and Serialization Design

Status: exploratory architecture record; non-normative

## Purpose

This document records cross-format design investigation for structured data,
document formats, application-data mapping, and object persistence in Protos.

It is intentionally broader than any one Standard Library work item. Its purpose
is to preserve architectural reasoning that may affect JSON, YAML, XML, binary
data-interchange formats, or future object-persistence facilities without
prematurely turning one format's data model into a universal Protos
serialization model.

This document does not define observable Protos semantics. Normative language
semantics remain owned by `spec/`. Concrete Standard Library work items must
record their adopted contracts separately in work-item-specific design records
before implementation.

## Problem decomposition

The investigation distinguishes four related but semantically different
problems.

### Format codecs

A format codec parses or emits one concrete interchange/document format.

Examples include JSON, YAML, XML, CBOR, MessagePack, and similar formats.

A format codec should preserve the semantics that belong to that format rather
than forcing all formats through the structural assumptions of the first codec
implemented.

### Format representation models

Different formats have genuinely different representation structures.

JSON is fundamentally a tree of null, Boolean, number, String, array, and
String-keyed object values.

YAML's representation model is richer: tagged scalar/sequence/mapping nodes may
form a graph with aliases, shared node identity, cycles, and non-String mapping
keys.

XML is a document-oriented ordered structure with elements, attributes,
namespaces, mixed text/element content, comments, processing instructions, and
other XML-specific information.

These are not interchangeable views of one universal node hierarchy.

### Application-data mapping

Application objects and domain data may need explicit conversion to or from the
representation accepted by a particular codec.

That conversion is conceptually separate from parsing and formatting.

A value's visible object structure does not by itself establish its intended
serialized representation.

### Object-graph and program persistence

Persisting live Protos objects is a separate problem from data interchange.

Object persistence may need to preserve or reconstruct semantic properties such
as:

- object identity and sharing;
- cycles;
- delegation relationships;
- Closures and captured state;
- module relationships;
- authority and capability boundaries;
- initialization or reconstruction intent;
- references to environment-dependent values.

A JSON, YAML, or XML codec must not accidentally become the definition of
general Protos object persistence.

## Prior-art lessons

The investigation intentionally compares architectures rather than merely API
spellings.

### Self

Self is a particularly relevant control case because its programs are graphs of
live prototype-based objects rather than instances whose persistence intent can
be inferred from a class declaration.

Self's Transporter experience shows that inspecting a live object is not enough
to know how it should be reconstructed. A currently stored value may need to be
saved literally, referenced through a creator relationship, or recomputed by an
initialization expression.

The resulting need for creator annotations, initialization rules, explicit
transport metadata, and related machinery is strong evidence that object
reflection and persistence intent are different semantic concerns.

For Protos this argues against making ordinary reflective slot traversal the
default meaning of serialization.

### Smalltalk / Pharo

Smalltalk JSON libraries commonly distinguish generic JSON-compatible data from
mapping arbitrary application objects.

NeoJSON-style generic operation maps JSON structures to ordinary collection and
scalar data, while object mapping is an additional explicit facility.

Smalltalk object-persistence systems such as STON or Fuel address a different
problem and may preserve concepts such as type information, shared references,
or cycles that plain JSON does not possess.

This separation is useful for Protos: data interchange and live-object
persistence should not be conflated merely because both eventually produce
bytes or text.

### Erlang / OTP

Erlang's standard JSON facilities are small and composable: ordinary
JSON-compatible terms are encoded directly, while caller-provided callbacks can
adapt other data or control decoding accumulation.

Erlang XML facilities use XML-specific SAX events rather than forcing XML
through JSON-shaped events.

The reusable idea is therefore the composition pattern:

```text
input
  -> incremental parser
  -> format-specific events
  -> caller-selected consumer / accumulator
```

The event vocabulary remains owned by the format.

Erlang ASN.1 provides the complementary lesson: a common model is appropriate
when there is an independently meaningful common schema/data model and several
encodings implement that model. Commonality should be semantic, not invented
merely because multiple formats serialize data.

### Serde

Serde demonstrates that a shared serialization data model can be valuable after
it has been justified by many real formats and a clear application-data
boundary.

It does not make JSON's tree model the universal model. Application types
implement serialization/deserialization against an intermediate data model, and
formats implement serializers/deserializers against that same model.

The XML ecosystem around Serde also demonstrates the limit: XML concepts such
as attributes and mixed content still require format-specific adaptation.
A useful generic model does not eliminate genuinely different document
semantics.

### Jackson / Java

Jackson successfully shares streaming/databinding infrastructure across JSON,
YAML, XML, and other formats, but XML requires XML-specific rules and
workarounds because a JSON-token-shaped abstraction is not a complete XML data
model.

Jakarta's separation of JSON-P and JSON-B is also instructive: JSON tree/
streaming representation and application-object binding are different layers.

### C++, C#, Go, Python, and JavaScript

These ecosystems provide additional evidence for separating concerns:

- explicit JSON value/tree models coexist with application-object binding;
- raw/source-preserving views are often separate from semantic data models;
- numeric conversion and exact textual representation are independent concerns;
- automatic object binding introduces policies for ignored members, naming,
  cycles, references, constructors, visibility, and custom converters;
- JSON-compatible native-data mappings are convenient but must not be mistaken
  for a universal serialization model.

## Architectural boundaries

The current investigation establishes the following directional boundaries.

### No universal structured-data node by default

Do not introduce a `UniversalValue`, `SerializationNode`, or equivalent sum of
every concept required by JSON, YAML, XML, and future formats.

Adding format-specific concepts to one expanding universal hierarchy would make
each new format pay for unrelated semantics and would obscure real differences
between trees, graphs, and document models.

### Format models remain format-specific

A future JSON codec may expose a JSON-oriented data mapping.

A future YAML codec may expose a simple native-data view, a full YAML
representation graph, or both.

A future XML codec may expose an XML document/tree model and/or XML-specific
event processing.

None of those choices automatically becomes the model of another format.

### Reflection does not imply serialization intent

Core reflection exposes local object structure. That is not evidence that every
local slot should be serialized, that delegated behavior should or should not be
included, or that the reflected structure contains enough information to
reconstruct a useful application object.

Any future reflection-assisted mapper or persistence facility must make that
adaptation explicit rather than silently redefining ordinary object structure as
a persistence contract.

### Object persistence is independently scoped

Identity-preserving or program-state persistence should be tracked as its own
design problem if and when Protos needs it.

A data-interchange codec should not gain reference IDs, class/type tags,
delegation reconstruction, Closure serialization, authority restoration, or
other object-graph machinery merely to claim generic serialization support.

### Shared abstractions must be earned

Do not create generic `Serializer`, `Deserializer`, `Format`, `Node`,
`ValueVisitor`, `Schema`, or similar institutions before multiple concrete
formats demonstrate a stable semantic boundary that actually benefits from the
abstraction.

Ordinary Protos Closures, objects, modules, collections, and I/O mechanisms can
express explicit adaptation in the meantime.

### Streaming may share architecture, not vocabulary

JSON, YAML, XML, and other formats may all benefit from incremental parsing and
emission.

The common architecture can be:

```text
source
  -> parser
  -> format-specific events
  -> consumer

producer
  -> format-specific events / calls
  -> writer
  -> sink
```

The event vocabulary should remain format-specific unless later evidence proves
a smaller genuinely common protocol.

### Text and byte I/O are the real lower boundary

Concrete structured-data codecs can compose with the existing Protos String,
Bytes, Encoding, TextReader/TextWriter, and ByteReadable/ByteWritable layers as
their requirements demand.

That I/O boundary is genuinely shared because it concerns transport of text and
bytes rather than pretending that document structures are identical.

## Three representation levels

Future codec designs should state explicitly which information level they
preserve.

### Semantic application-data representation

This level preserves the useful data semantics chosen by the codec, not exact
source presentation.

For example, whitespace or alternate escape spelling may disappear.

### Format representation model

This level preserves the structural concepts that belong to the format, such as
YAML graph identity/tags or XML namespaces/attributes/mixed content.

### Source-preserving / lossless representation

Editors, formatters, migration tools, or forensic applications may require
lexeme spelling, whitespace, comments, quoting style, source ranges, or other
presentation details.

A lossless concrete-syntax-tree style API should be a deliberate capability,
not an accidental obligation imposed on every ordinary parser result.

## Design tests for future format work

Before standardizing any structured-data or serialization facility, ask:

1. Is this rule owned by the format, by Protos application mapping, or by
   object/program persistence?
2. Does the design preserve a real semantic distinction or erase it for API
   uniformity?
3. Is a generic abstraction supported by at least two independently useful
   concrete cases?
4. Does an application that only needs one simple codec avoid paying for graph,
   reflection, schema, persistence, or lossless-source machinery?
5. Does the API accidentally infer persistence intent from object reflection?
6. Does it preserve Protos identity, Actor-transfer, authority, mutation, and
   delegation boundaries rather than creating hidden exceptions?
7. Can a large input be processed incrementally without requiring construction
   of a complete in-memory representation when the caller does not need one?
8. Are exact numeric/textual representation and semantic numeric conversion
   treated as separate concerns where the format requires that distinction?
9. Are duplicate names, ordering, sharing, cycles, and invalid structures
   handled deliberately rather than inherited accidentally from a host
   container implementation?
10. Could a future YAML, XML, binary, or persistence facility coexist without
    pretending that the current format's model is universal?

## Relationship to work-item design records

This document is intentionally cross-cutting and exploratory.

Once a concrete tracked work item adopts a bounded design, its implementation
contract should be recorded separately under `docs/project/`, following the
precedent of:

```text
docs/project/LIB001_COLLECTIONS_DESIGN.md
```

A work-item design record may reference this document, choose among alternatives
investigated here, define concrete public APIs and slices, and record rejected
alternatives for that work item.

Those project records remain non-normative. If a library design exposes a
missing Core semantic prerequisite, that prerequisite must be resolved through
the appropriate normative owner under `spec/` before the library relies on it.

## Deferred questions

This architecture record deliberately does not decide:

- any concrete JSON API or JSON value mapping;
- any JSON number representation or conversion policy;
- any JSON duplicate-name or ordering contract;
- any Standard Library module name for JSON, YAML, XML, or generic
  serialization;
- whether Protos will standardize YAML or XML;
- whether a future multi-format serializer/deserializer protocol is justified;
- whether Protos needs a general object-persistence format;
- whether a lossless source-preserving tree should ever be standardized.

Those decisions belong to future focused work-item audits.
