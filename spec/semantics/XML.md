# Standard Library XML processing

Language version: 0.1
Status: Draft
Last updated: 2026-11-28
Decision: D268-R1

This document is the primary normative owner of the XML Standard Library
contract. It specifies required behavior; it does not claim that the module is
already implemented. General Core semantics remain owned by the documents
referenced in §18. Imperative requirements below are normative obligations on
implementations.

## 1. Scope and standards profile

This contract defines a non-validating, namespace-aware, DTD-free profile of:

- XML 1.0 Fifth Edition, W3C Recommendation of 26 November 2008.
- Namespaces in XML 1.0 Third Edition, W3C Recommendation of 8 December 2009.

The normative external standards are:

- https://www.w3.org/TR/2008/REC-xml-20081126/
- https://www.w3.org/TR/2009/REC-xml-names-20091208/

Require exactly one document element, optional XML declaration, valid prolog and epilog.

Accept:

- Elements and empty-element syntax.
- Ordinary attributes.
- Namespace declarations.
- Ordered mixed content.
- Comments.
- Processing instructions.
- CDATA.
- Five predefined entities.
- Decimal and hexadecimal character references.

Reject:

- XML versions other than 1.0.
- Multiple document roots.
- Rootless XML fragments.
- Malformed XML.
- DOCTYPE and every DTD form.
- Custom entity declarations and references.
- External entity resolution.

No recovery mode, schema validation, XPath, XSLT, XInclude processing, Canonical XML, HTML interpretation or implicit URI resolution.

A syntactically valid element belonging to an XInclude namespace remains ordinary XML data. It does not activate inclusion.

Comments, processing instructions, URI values and textual content never invoke external behavior by themselves.

The restricted profile does not claim complete support for all valid documents admitted by unrestricted XML 1.0.

## 2. Module identity and public surface

The exact ordinary Standard Library MODULE is:

```protos
XML: import("std:xml/xml")
```

Its product implementation location is:

`protos/lib/xml/xml/module.protos`

The module exports exactly these eleven public bindings:

```text
parse(text, limits = null)
eventParser(emit, limits = null)
readEvents(textReader, emit, limits = null)

elements(element, namespaceURI, localName)
firstElement(element, namespaceURI, localName)
attribute(element, namespaceURI, localName)
textContent(element)

encode(document, limits = null)
eventWriter(emit, limits = null)
writeEvents(textWriter, limits = null)

XmlError
```

No additional public constructors, Core prototypes, Prelude bindings, parser framework, global configuration registry or hidden provider.

The module is Actor-local under `MODULES.md`.

Importing it acquires no I/O authority.

Require one shared incremental parsing grammar and semantic kernel. Eager parsing composes tree construction over this kernel. Do not authorize a separately maintained eager grammar.

Serialization is a separate kernel sharing the approved structural vocabulary. Eager serialization composes the event writer.

## 3. Exact public record model

All XML data records directly delegate to the ordinary `Object` prototype,
and their collections are standard Arrays. The record names below are
descriptive schema names, not additional public prototypes or semantic value
families.

The ordinary data records have the following own slots.

**Document**

```protos
{
    kind: "document"
    declaration: null
    prolog: []
    root: element
    epilog: []
}
```

The declaration is either a Declaration record or null.

Prolog and epilog contain ordered Comment and ProcessingInstruction records.

The root is exactly one Element.

Whitespace outside the root is validated but not represented as Text nodes.

**Declaration**

```protos
{
    kind: "declaration"
    version: "1.0"
    encoding: null
    standalone: null
}
```

The version is exactly `"1.0"`.

The encoding is a String preserving the declared encoding name or null.

Standalone is canonical true for `yes`, canonical false for `no`, or null when absent.

**Name**

```protos
{
    namespaceURI: "urn:example"
    localName: "item"
    prefix: "p"
}
```

The namespaceURI is a nonempty String or null.

The localName is an NCName.

The prefix is an NCName or null.

Null is the sole representation of absent namespace and absent prefix.

Name expansion identity compares namespaceURI and localName exactly. The prefix is retained separately as lexical information.

**Element**

```protos
{
    kind: "element"
    name: name
    attributes: []
    namespaceDeclarations: []
    children: []
}
```

Attributes, namespaceDeclarations and children are ordered standard Arrays.

Children may contain Element, Text, Comment and ProcessingInstruction records.

The original interleaving between ordinary attributes and namespace declarations is not preserved.

**Attribute**

```protos
{
    name: name
    value: "value"
}
```

An attribute value is always a String.

No implicit numeric, Boolean, URI, date or application-type conversion.

**NamespaceDeclaration**

```protos
{
    prefix: "p"
    namespaceURI: "urn:example"
}
```

Default namespace declarations use prefix null.

An explicit default namespace clearing declaration uses both prefix null and namespaceURI null.

**Text**

```protos
{
    kind: "text"
    value: "content"
}
```

Text nodes contain nonempty Strings.

Contiguous logical character data is combined into one Text node, including adjacent CDATA content and character/entity references.

Empty CDATA does not introduce an empty Text node.

**Comment**

```protos
{
    kind: "comment"
    value: "comment"
}
```

**ProcessingInstruction**

```protos
{
    kind: "processingInstruction"
    target: "application"
    data: "content"
}
```

All parser-produced records and Arrays are ordinary individually frozen objects before publication.

Do not introduce recursive freeze semantics into Core. Apply the existing shallow freeze independently to every published XML record and collection.

No record retains a Closure, parser, mutable namespace environment, external capability or mandatory parent backlink.

Preserve ordinary identity, default equality and hashing. Do not introduce structural value identity or an XML semantic family.

Two independent successful parses produce distinct Document identities.

Ordinary Actor transfer rules apply to the complete data graph.

Structurally valid manually constructed XML records are admitted by queries and serialization without a hidden provenance marker.

## 4. Namespaces

Apply the XML Namespaces 1.0 Third Edition constraints.

All declarations belonging to a start tag must be processed before resolving the expanded names of that element and its ordinary attributes.

Namespace scopes follow element nesting. Restore outer bindings when leaving an element.

The default namespace applies to unprefixed element names, never to unprefixed ordinary attribute names.

The namespace constraints are:

- `xml` is prebound to `http://www.w3.org/XML/1998/namespace`.
- An explicit correct `xmlns:xml` declaration is permitted.
- Any alternative binding of `xml` is rejected.
- No other prefix or default namespace may bind the reserved XML URI.
- `xmlns` is reserved for namespace declaration syntax.
- The namespace URI `http://www.w3.org/2000/xmlns/` cannot be bound as an ordinary namespace.
- The default namespace may be cleared with `xmlns=""`.
- Non-default prefixes cannot be undeclared with an empty value.
- Undeclared prefixes are invalid.
- Invalid QName and NCName spellings are rejected.
- Duplicate lexical attributes or duplicate namespace declarations are rejected.
- Duplicate expanded ordinary attribute names are rejected.

Other prefixes starting with a case-insensitive spelling of `xml` are not rejected exclusively for that circumstance.

Require exact lexical QName matching between start and end tags.

Two different prefixes mapped to the same namespace do not make a mismatched closing QName valid.

Namespace URI identity uses exact String contents after XML attribute normalization.

Do not normalize URI case, Unicode, percent escapes or relative references. Do not access the URI or depend on `std:uri` to reinterpret namespace identity.

Namespace declarations are separate from ordinary attributes.

Preserve prefixes and local namespace declarations so that QName-valued textual content is not changed by serialization.

## 5. Unicode and lexical processing

Admit exactly the XML 1.0 Fifth Edition `Char` repertoire:

```text
U+0009
U+000A
U+000D
U+0020 through U+D7FF
U+E000 through U+FFFD
U+10000 through U+10FFFF
```

Apply the repertoire to literal characters and numeric character references.

Use the XML Name, NameStartChar and NameChar productions (XML 1.0 Fifth Edition §2.3, productions [4], [4a] and [5]) and Namespaces NCName productions (Namespaces 1.0 Third Edition §3, productions [4]–[6]), not the Protos identifier grammar.

Do not normalize Unicode names.

The exact selected W3C productions normatively define the XML name ranges.

Allow only the predefined entity names:

```text
amp
lt
gt
apos
quot
```

Require complete semicolon-terminated decimal or lower-case-x hexadecimal numeric-reference syntax.

Reject invalid Unicode scalar values, invalid XML characters, malformed references, unknown entities and incomplete references.

Character references and entity replacement characters are interpreted once. Their resulting characters never re-enter the markup lexer.

Normalize physical CRLF and CR to LF before lexical processing, independently of feed boundaries.

In attribute values, including namespace declarations, replace literal TAB and normalized LF with space.

Numeric character references preserve the referenced whitespace character.

Do not trim or collapse whitespace.

Preserve textual whitespace inside elements.

CDATA contributes ordinary character data.

Preserve comments and processing instructions as inert data records.

## 6. Explicit Encoding and BOM boundary

`parse` and `feed` accept semantic Strings.

They do not accept Bytes through an implicit conversion.

No String-like facade, arbitrary conversion protocol or automatic source acquisition is introduced.

Decode external bytes through existing explicit Encoding and TextReader contracts.

The XML String parser does not strip an initial U+FEFF. Such a character does not satisfy the document prolog grammar.

A matching byte-order mark consumed by the selected Encoding is the responsibility of that Encoding.

U+FEFF within permitted textual content remains an ordinary character.

Validate XML declaration encoding-name syntax and preserve its spelling.

Do not claim that a declaration matches original bytes which the parser has not received.

Byte autodetection and transport-charset negotiation are excluded.

The parser cannot certify original byte validity when the caller supplied text produced by replacement decoding.

## 7. Document queries

Define:

```text
elements(element, namespaceURI, localName) -> Array
```

Select only matching direct child Elements, in document order.

Return a fresh open independent Array containing references to the selected Elements.

An empty result is a fresh empty Array.

```text
firstElement(element, namespaceURI, localName) -> Element | null
```

Return the first direct matching Element or null.

```text
attribute(element, namespaceURI, localName) -> String | null
```

Return the matching ordinary attribute value or null.

Distinguish a missing attribute from an existing empty String value.

```text
textContent(element) -> String
```

Concatenate descendant Text content in document order.

Ignore comments and processing instructions.

Do not insert artificial separators or apply HTML interpretation.

The three expanded-name queries `elements`, `firstElement` and `attribute`
accept a namespaceURI that is either null or a nonempty String, and a localName
that is a String satisfying NCName. Invalid query arguments signal `XmlError`
with kind `"argument"`.

Queries compare expanded names using exact namespaceURI and localName contents.

Do not require hidden parser provenance.

Reject invalid record structure necessary for the query. A cyclic graph must not cause unbounded traversal.

## 8. Incremental event parser

Define:

```text
eventParser(emit, limits = null) -> parser

parser.feed(text) -> parser
parser.finish() -> parser
```

Every successful operation returns the exact parser receiver.

An admitted empty String feed is a no-op.

`finish()` declares semantic EOF and validates one complete document.

A completed root element does not itself finish the parser.

Use exactly these event kinds and fields:

| kind | Other own slots |
|---|---|
| declaration | version, encoding, standalone |
| startElement | name, attributes, namespaceDeclarations |
| text | value |
| comment | value |
| processingInstruction | target, data |
| endElement | name |

Every emitted event is a fresh ordinary frozen record.

Nested records and Arrays are independently frozen before emission.

Event data remains stable after callback completion.

Empty-element syntax and equivalent explicit start/end syntax both produce a startElement event followed by an endElement event.

Do not introduce document-start, document-end, CDATA-boundary or entity-reference events.

Invoke the consumer through ordinary polymorphic invocation:

```protos
emit(event)
```

Its normal result is ignored, including a Future returned as an ordinary value.

Do not implicitly await it.

Do not introduce a new callability-inspection primitive.

An actual callback invocation failure retains the existing ordinary failure behavior.

## 9. Deterministic Text event segmentation

Require 4096 decoded Unicode scalars per complete Text event block.

Emit complete blocks immediately when established.

Continue the same logical text run across:

- Feed boundaries.
- Predefined entities.
- Numeric references.
- CDATA boundaries.

Emit the remaining nonempty residual block before the next completely validated non-text event.

If malformed markup establishes failure before that next event is valid, discard the unreported residual block.

Never emit an empty Text event.

Eager tree materialization combines consecutive Text events into a single logical Text node.

Event order, segmentation, failure position and previously emitted event prefix must not depend on transport chunking.

Do not validate an entire feed in advance in a way that suppresses earlier events that should already have been delivered.

## 10. Parser and writer lifecycle

Define the conceptual private states OPEN, ACTIVE, FINISHED and FAILED.

They are not new Core values or public runtime types.

Receiver lifecycle admission precedes argument validation. An ACTIVE or terminal
(FINISHED or FAILED) parser or writer rejects the operation with a lifecycle
Error even when the supplied argument is invalid. On an OPEN receiver, invalid
arguments rejected before operation admission do not poison that receiver.

Admit only one active operation on a parser or writer.

Reject overlapping or reentrant operations with a lifecycle Error.

When a reentry Error is caught without leaving the original active operation, that original operation can continue.

When the Error escapes and abandons the original active operation, the parser or writer becomes terminal.

An invalid argument rejected before operation admission does not consume input, emit output or poison an otherwise OPEN parser or writer.

An admitted operation that exits abnormally becomes terminal.

Normal feed completion returns to OPEN.

Successful finish reaches FINISHED.

Suspension preserves the same ACTIVE operation and continuation.

Do not replay callbacks, reset progress, or make the receiver available to another operation during suspension.

An escaping Error, non-local return or cooperative cancellation must preserve its original control transfer and leave the admitted operation terminal.

Cleanup follows the existing `ensure` semantics in [ERRORS.md](ERRORS.md#error-precedence-during-ensure-cleanup).

Do not create a new suspension, cancellation, rollback or recovery mechanism.

Effects of callbacks already executed are not rolled back.

Operations after FINISHED or FAILED signal fresh lifecycle failures.

No reset, retry, resume, implicit yield, hidden preemption or invented cancellation boundary.

## 11. TextReader adapter

Define:

```text
readEvents(textReader, emit, limits = null) -> adapter

adapter.read() -> Future<Boolean>
```

Each accepted read issues exactly one ordered `readText()` operation.

A String result feeds the incremental parser and resolves to true.

A null result finishes the parser and resolves to false.

An empty String from a compatible custom reader is a feed, not EOF.

Allow one pending operation per adapter.

Reject overlap and use after terminal state.

An accepted operation that fails or is cancelled leaves the adapter terminal.

Preserve the exact original Error and ordinary Future cancellation behavior.

Do not wrap I/O or Encoding errors into XmlError.

Under the existing [Future.then cancellation contract](../concurrency/FUTURES_AND_TASKS.md#future-then-continuations), cancelling a downstream continuation does not automatically cancel an upstream source Future.

Do not promise restoration of already consumed input.

Do not imply that a `then` transformation runs after source failure or cancellation.

The adapter must not acquire, close, flush, reset, rewind or change the Encoding of the reader.

Resource ownership remains with the caller.

No implicit readLine, additional speculative read, automatic retry or hidden network/filesystem acquisition.

## 12. Exact resource budgets

The eight portable defaults are:

| Option | Default |
|---|---:|
| maxDocumentScalars | 2097152 |
| maxDepth | 256 |
| maxAttributesPerElement | 256 |
| maxNamespaceDeclarationsPerElement | 128 |
| maxNameScalars | 1024 |
| maxAttributeScalars | 262144 |
| maxTokenScalars | 1048576 |
| maxNodes | 250000 |

Budget accounting is defined as follows:

- maxDocumentScalars counts original input Unicode scalars before line normalization.
- maxDepth counts simultaneously open Elements, with root depth one.
- maxAttributesPerElement counts ordinary attributes of one Element.
- maxNamespaceDeclarationsPerElement counts declarations local to one Element.
- maxNameScalars counts lexical name length including prefix and colon.
- maxAttributeScalars counts normalized attribute value length, including namespace declaration values.
- maxTokenScalars counts lexical start/end tags, comments, processing instructions, declaration and references including delimiters.
- maxNodes counts appearances of Elements, logical contiguous Text runs, Comments and ProcessingInstructions.

Document, Declaration, Name, Attribute and NamespaceDeclaration records are not individually counted as XML nodes.

Multiple event blocks representing one logical Text run count as one node.

References within attribute values contribute their lexical size to the surrounding tag.

Character data and CDATA content do not require retention as a single bounded token. Their applicable delimiters and references remain subject to the lexical budget.

Limits are inclusive: exactly the selected bound is allowed.

`limits = null` selects all defaults.

A supplied ordinary options object may specify a subset of the eight named
options through its own slots. Inherited slots do not supply configuration.
Only the eight declared option names are accepted as own option slots.

Options missing from the object's own slots use defaults.

Reject unknown option names and values outside the positive semantic Integer domain.

No Float, Boolean, null individual value, coercion or unlimited sentinel.

Snapshot selected limits before admitting work. Later option-object mutations cannot affect an existing parser or writer.

Account in logical consumption order.

Reject at the established limit crossing, without consuming additional input or publishing additional events.

Do not allocate or retain data beyond an already-established budget merely to discover a later failure.

These are semantic resource budgets, not exact host heap or runtime latency promises.

Transport-byte limits remain independent.

## 13. XML error model

Define one module-exported ordinary Error prototype:

`XmlError`

Its immediate delegation parent is the standard Error prototype.

It is not a Prelude binding or additional Core Error category.

Every library-created XmlError failure occurrence has fresh identity and exactly these own payload slots:

```protos
{
    kind: "syntax"
    offset: 128
    limit: null
}
```

The exact permitted kind Strings are:

```text
syntax
namespace
unsupported
limit
argument
lifecycle
```

Define offset as a zero-based original-input Unicode scalar position before line normalization, or null where specified.

Use these diagnostic anchors:

| Failure | Offset |
|---|---|
| Invalid literal character | Character position |
| Invalid reference | Opening ampersand |
| Invalid markup | Token start |
| Namespace failure or duplicate | Tag start |
| Mismatched closing name | End-tag start |
| Unexpected EOF | Original input length |
| Limit exceeded | Position where excess is established |
| Invalid argument | null |
| Lifecycle failure | null |
| Serialization failure | null |

The limit slot is the exact exceeded option name for a limit failure and null otherwise.

Do not add normative natural-language messages, mandatory line/column computation, native diagnostic objects, filesystem paths or authority-bearing metadata.

Preserve the exact identity of externally supplied callback, Encoding and I/O Errors.

Do not replace an ordinary invocation failure with an invented XML failure.

For logical parsing steps, preserve the approved validation precedence:

1. Total input budget.
2. Active lexical-token budget.
3. Character validity and lexical grammar.
4. Name and attribute-value budgets as those values are formed.
5. Namespace and duplicate validation at completed tags.
6. Structural budgets before corresponding node or event publication.

Coincident structural budget checks use this order:

```text
attributes
namespace declarations
depth
nodes
```

A startElement event cannot be emitted before the relevant tag validation succeeds.

Diagnostic behavior and already-published event prefixes must be independent of feed partitioning.

## 14. Complete and incremental serialization

Define:

```text
encode(document, limits = null) -> String

eventWriter(emit, limits = null) -> writer

writer.feed(event) -> writer
writer.finish() -> writer
```

Complete encoding produces one valid String or signals Error.

No partial successful String result.

Use the same approved event vocabulary.

Validate each event completely, including applicable output budgets, before emitting its corresponding nonempty String.

Finish validates completion and emits no additional output.

Use explicit opening and closing tags even for empty Elements.

Do not require lookahead to generate empty-element shorthand.

Serialize local namespace declarations first and ordinary attributes second, preserving Array order within each category.

Preserve supplied prefix spellings.

Validate namespace coherence, expanded names and lexical end-name matching.

Do not invent namespace declarations, rewrite prefixes or repair namespace scope automatically.

A subtree serialized in a different context must carry the declarations necessary for that context.

## 15. Exact escaping and lexical output

For textual content:

| Character | Output |
|---|---|
| Ampersand | `&amp;` |
| Less-than | `&lt;` |
| Greater-than | `&gt;` |
| CR | `&#xD;` |
| LF | Literal |
| TAB | Literal |

Always escaping greater-than prevents literal forbidden `]]>` sequences.

For attribute values:

- Always use double quotes.
- Escape ampersand, less-than, greater-than and double quote.
- Emit TAB as `&#x9;`.
- Emit LF as `&#xA;`.
- Emit CR as `&#xD;`.

Do not generate CDATA sections.

Preserve XML declaration presence, version and standalone value.

Omit encoding from the serialized String declaration because the serializer does not control subsequent byte encoding.

Do not generate a declaration when none is present.

Do not emit a BOM.

Reject comments containing `--`, ending in `-`, or containing invalid XML characters.

Reject invalid processing-instruction targets.

A PI target must satisfy NCName and must not equal `xml` under case-insensitive ASCII comparison.

Reject PI data containing `?>`.

Consume initial separator whitespace between PI target and data during parsing. Reject manually constructed PI data that begins with separator whitespace that cannot survive this representation unchanged.

Reject CR in manually constructed comments and processing instructions because XML normalization would change the character and references are not interpreted within those constructs.

## 16. Serialization graph validation and structural equivalence

Admit the defined ordinary XML record model without requiring private provenance.

Do not implement arbitrary-object serialization.

Reject cyclic graphs.

A repeated reference in an acyclic graph is serialized once per appearance.

Count each appearance against the applicable budgets.

Do not promise aliasing preservation.

Require the approved structural equivalence for representable documents within the selected input and output budgets:

```text
parse(encode(document)) ≡ document
```

Compare:

- Expanded names.
- Prefixes.
- Local namespace declarations.
- Attribute values and category order.
- Ordered mixed content.
- Comments and processing instructions.
- Declaration presence, version and standalone.

The equivalence does not require identical Protos object identities, aliasing or declaration encoding metadata.

It does not preserve original quote spelling, CDATA boundaries, character-reference spelling, empty-element lexical syntax, discarded outside-root whitespace or original interleaving of namespace declarations and attributes.

Require the canonical tree representation of one nonempty Text per contiguous logical text run.

Serialization uses the same eight budgets. The document scalar budget counts emitted XML characters including escaping and delimiters.

Do not emit a partial event contribution and then discover that the remainder exceeds its output budget.

## 17. TextWriter adapter

Define:

```text
writeEvents(textWriter, limits = null) -> adapter

adapter.feed(event) -> Future<adapter>
adapter.finish() -> Future<adapter>
```

Allow one pending operation.

Validate and serialize each accepted event into a stable String before invoking the corresponding ordered `writeText(chunk)`.

Later mutation of the supplied event cannot alter that accepted String contribution.

On successful completion resolve with the exact adapter receiver.

Finish validates the complete XML document and establishes one ordered:

```protos
textWriter.writeText("")
```

This is the existing zero-output ordered TextWriter operation, not a new lifecycle mechanism.

It does not imply flush, close, sync, encoder finalization or resource ownership.

Failure or cancellation of an accepted operation leaves the adapter terminal.

Preserve original Error and Future semantics.

Do not retry or replay writes when output progress may be uncertain.

No automatic close or upstream cancellation is implied.

## 18. Authority, isolation and implementation freedom

The following existing normative owners govern composition; this document does
not redefine their general Core contracts:

- [OBJECT_MODEL.md](OBJECT_MODEL.md): ordinary identity, delegation and shallow freezing.
- [VALUES_AND_COLLECTIONS.md](VALUES_AND_COLLECTIONS.md): String scalar semantics, standard Array state, equality and hashing.
- [MODULES.md](MODULES.md): MODULE identity and Actor-local instances.
- [CALLABLES.md](CALLABLES.md): ordinary callable invocation and non-local return.
- [EXECUTION_AND_CONTROL.md](EXECUTION_AND_CONTROL.md): ordinary evaluation, control and iteration.
- [ERRORS.md](ERRORS.md): Error identity, signaling, unwinding and `ensure` cleanup.
- [FUTURES_AND_TASKS.md](../concurrency/FUTURES_AND_TASKS.md): Futures, cooperative suspension and cancellation.
- [ACTORS.md](../concurrency/ACTORS.md): Actor isolation and graph transfer.
- [PARALLEL_EXECUTION.md](../concurrency/PARALLEL_EXECUTION.md): isolated parallel execution.
- [TEXT_IO.md](../io/TEXT_IO.md): explicit Encoding and TextReader/TextWriter protocols.
- [IO_CORE.md](../io/IO_CORE.md): borrowed I/O composition and resource ownership.

Do not create a new cancellation boundary for XML CPU processing.

Do not introduce implicit worker Tasks, host threads or scheduling.

Ordinary control, numeric operations, indexed access, slots, object construction
and iteration retain their existing message semantics.

The event parser does not require materialization of a complete XML tree.

Programs that do not request XML must not pay XML initialization or runtime costs.

Portable observable XML semantics do not require a specific host stack, Java
representation, lexer technology or external parser dependency.

### 18.1. Non-normative implementation restrictions for I182

This subsection is non-normative with respect to portable observable XML
semantics. Its restrictions remain binding for the I182 implementation work;
they neither redefine those semantics nor authorize additional public APIs.

The parser, namespace processing, document model, queries, serialization,
limits and adapters must be implemented in ordinary Protos. No host XML parser,
Java XML API bridge, FFI adapter, native XML library, private XML primitive,
hidden runtime facility or Java fallback is permitted. No implementation may
depend on hidden host XML behavior.

This contract authorizes no Java production change or alteration of existing
Truffle infrastructure. Primitive representation remains long, double and
boolean, not int. The usual hot execution paths for ordinary control, numeric
operations, indexed access, slots, object construction and iteration remain
internally specialized and optimizable by GraalVM. Existing specialization
infrastructure must be preserved without reintroducing obsolete dispatch
infrastructure through fallback paths.

The later ordinary Protos implementation must avoid repeated full namespace-environment copies, quadratic growing-buffer concatenation and host-stack dependence for deeply nested input.

Do not transform these implementation constraints into unapproved new public APIs.

## 19. Explicitly excluded extensions

The following are outside the initial normative contract:

- DTD support.
- External entities.
- XML 1.1.
- XML fragments.
- DOM-style mutable document API.
- XPath.
- Schema validation.
- XSLT.
- XInclude processing.
- Canonical XML.
- Automatic namespace repair.
- Byte encoding autodetection.
- Arbitrary-object serialization.
- Atom-specific or RSS-specific policy.
- HTML parsing, sanitization and serialization.
- Network or filesystem acquisition.

No excluded capability is implicitly enabled through the XML profile.

## 20. Examples

These normative illustrations use only the defined XML API and assume the
ordinary `XML` module binding introduced in the first example. They otherwise
remain independent unless accompanying text explicitly supplies a prior binding.

### Import and namespace-aware direct-child queries

```protos
XML: import("std:xml/xml")
document: XML.parse("<feed xmlns='http://www.w3.org/2005/Atom'><entry><title>News</title></entry></feed>")
entries: XML.elements(document.root, "http://www.w3.org/2005/Atom", "entry")
entry: XML.firstElement(document.root, "http://www.w3.org/2005/Atom", "entry")
title: XML.firstElement(entry, "http://www.w3.org/2005/Atom", "title")
content: XML.textContent(title)
```

`entries` is a fresh open Array referring to the direct child entry; `content`
is `"News"`.

### Default namespace clearing and unprefixed attributes

```protos
document: XML.parse("<outer xmlns='urn:outer'><inner xmlns='' id=''/></outer>")
inner: XML.firstElement(document.root, null, "inner")
value: XML.attribute(inner, null, "id")
missing: XML.attribute(inner, "urn:outer", "id")
```

`inner` has no namespace. `value` is the empty String and `missing` is null.
An unprefixed ordinary attribute has no namespace even on an element in a
default namespace.

### Namespace failures

The following call signals `XmlError` with kind `"namespace"`:

```protos
XML.parse("<r xmlns:a='urn:x' xmlns:b='urn:x' a:id='1' b:id='2'/>")
```

The ordinary attributes have duplicate expanded names. Different prefixes do
not distinguish them.

```protos
XML.parse("<a:r xmlns:a='urn:x' xmlns:b='urn:x'></b:r>")
```

The latter call fails for mismatched lexical closing QNames; it does not become
valid because both prefixes expand to the same namespace. The diagnostic anchor
is the end-tag start as specified in §13. This illustration requires rejection
without assigning a separate error-kind rule beyond §13.

### Mixed content

```protos
document: XML.parse("<p>A<![CDATA[B]]>&amp;&#67;<b/>D</p>")
content: XML.textContent(document.root)
```

The children are one Text with value `"AB&C"`, one Element named `b`, and one
Text with value `"D"`. `content` is `"AB&CD"`.

### Incremental parsing

Given an ordinary invokable consumer `emit`:

```protos
parser: XML.eventParser(emit)
parser.feed("<r>A<![CDA")
parser.feed("TA[B]]>&amp;</r>")
parser.finish()
```

The events are startElement, text with value `"AB&"`, and endElement. Each
successful operation returns the exact `parser`. Only `finish()` establishes
semantic EOF.

### Explicit UTF-8 boundaries

Given caller-supplied Bytes `sourceBytes`, decode explicitly:

```protos
text: Encoding.UTF8.decode(sourceBytes)
document: XML.parse(text)
```

Given that Document, encode serialized text explicitly:

```protos
serialized: XML.encode(document)
outputBytes: Encoding.UTF8.encode(serialized)
```

The caller selects the byte Encoding. The XML module neither acquires the bytes
nor certifies a declaration against original bytes it has not received.
