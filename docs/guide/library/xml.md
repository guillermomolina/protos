# XML processing

```protos
XML: import("std:xml/xml")
document: XML.parse("<feed xmlns='urn:example'><title>A &amp; B</title></feed>")
title: XML.firstElement(document.root, "urn:example", "title")
XML.textContent(title) // "A & B"
```

The normative authority is [Standard Library XML processing](../../../spec/semantics/XML.md).
The module uses a namespace-aware, non-validating, DTD-free profile of XML 1.0
Fifth Edition and Namespaces 1.0 Third Edition. Importing it grants no authority.
This implementation is awaiting human-executed conformance and acceptance
validation; its presence in source is not evidence of a passing validation run.

## Public API

| Binding | Result |
|---|---|
| `parse(text, limits: null)` | Complete frozen Document |
| `eventParser(emit, limits: null)` | Incremental parser with `feed(text)` and `finish()` |
| `readEvents(textReader, emit, limits: null)` | Borrowed adapter with `read() -> Future<Boolean>` |
| `elements(element, namespaceURI, localName)` | Fresh open Array of matching direct children |
| `firstElement(element, namespaceURI, localName)` | First matching direct child or null |
| `attribute(element, namespaceURI, localName)` | Attribute String or null; empty Strings remain distinct |
| `textContent(element)` | Descendant Text concatenated in order |
| `encode(document, limits: null)` | Complete XML String |
| `eventWriter(emit, limits: null)` | Incremental writer with `feed(event)` and `finish()` |
| `writeEvents(textWriter, limits: null)` | Borrowed adapter with Future-returning `feed` and `finish` |
| `XmlError` | Ordinary module-local Error prototype |

Expanded-name queries use the exact namespace URI String and local NCName.
Use null for an absent namespace. Prefixes are retained as lexical data; they
are not part of expanded-name identity. Only Elements inherit the default
namespace; unprefixed ordinary attributes have null namespaceURI.

## Trees and events

A Document has `kind`, `declaration`, `prolog`, `root`, and `epilog`. An Element
has `kind`, `name`, `attributes`, `namespaceDeclarations`, and `children`.
A Name contains `namespaceURI`, `localName`, and `prefix`. Children contain
Element, nonempty Text, Comment, or ProcessingInstruction records.

Every parser-produced record and Array is individually frozen with ordinary
shallow freezing. Queries return existing node references inside fresh open
Arrays. Caller-created records with the specified shape are also accepted;
there is no provenance marker. Descendant traversal and serialization reject
ancestor cycles while counting repeated acyclic appearances separately.

The six event kinds are `declaration`, `startElement`, `text`, `comment`,
`processingInstruction`, and `endElement`. Eager parsing consumes these same
events. Text events contain complete blocks of 4096 decoded Unicode scalars,
plus a nonempty residual before the next fully validated structural event.
Chunk boundaries, references, and CDATA do not split a logical Text node.

```protos
count: 0
parser: XML.eventParser((event) => {
    if(event.kind == "startElement") { count = count + 1 }
})
parser.feed("<r>hello &am")
parser.feed("p; goodbye</r>")
parser.finish()
```

The callback is invoked ordinarily; its return value, including a Future, is
ignored. Reentry and terminal reuse signal lifecycle failures. An invalid input
argument before admission leaves an open parser reusable; an admitted operation
that exits abnormally leaves it terminal. Suspension keeps the operation active.

## Encoding and resource budgets

The parser accepts semantic Strings. Decode bytes explicitly with the appropriate
Encoding or pass an already configured TextReader to `readEvents`. The parser
preserves declaration encoding metadata but cannot verify original byte encoding.
It does not strip a leading U+FEFF from a supplied String. Physical CRLF and CR
normalize to LF; numeric references preserve referenced whitespace.

```protos
bounded: XML.parse("<r/>", { maxDepth: 16; maxDocumentScalars: 65536 })
```

The eight default inclusive bounds are:

| Option | Default |
|---|---:|
| `maxDocumentScalars` | 2097152 |
| `maxDepth` | 256 |
| `maxAttributesPerElement` | 256 |
| `maxNamespaceDeclarationsPerElement` | 128 |
| `maxNameScalars` | 1024 |
| `maxAttributeScalars` | 262144 |
| `maxTokenScalars` | 1048576 |
| `maxNodes` | 250000 |

Only positive semantic Integers in the options object's own named slots are
accepted. Missing own options use defaults; null as the entire options argument
selects defaults. Factories snapshot options. Input document accounting uses
original Unicode scalars before normalization; output accounting includes all
escaping and delimiters. Text blocks count as one node per logical run.

XML-owned failures directly delegate to `XML.XmlError`, with exactly `kind`,
`offset`, and `limit` payload slots. Kinds are `syntax`, `namespace`,
`unsupported`, `limit`, `argument`, and `lifecycle`. Parsing offsets are
zero-based original scalar positions; argument, lifecycle, and serialization
offsets are null. A limit failure names its exceeded option. External callback,
Encoding, and I/O errors retain their exact original identity.

## Serialization and borrowed text I/O

`XML.encode(document)` traverses the tree through the same `eventWriter` kernel.
It emits explicit start/end pairs and double-quoted attributes, namespace
declarations before ordinary attributes, and exact approved escaping. It
preserves prefixes and local declaration order, checks namespace coherence,
and performs no namespace repair. Encoding metadata is omitted from a supplied
XML declaration; version and standalone are preserved. No BOM or CDATA is emitted.

```protos
chunks: Encoding.UTF8.encode("\u{feff}")
writer: XML.eventWriter((chunk) => {
    for(octet in Encoding.UTF8.encode(chunk)) { chunks.add(octet) }
})
parser: XML.eventParser((event) => { writer.feed(event) })
parser.feed("<r>A &amp; B</r>")
parser.finish()
writer.finish()
text: Encoding.UTF8.decode(chunks)
```

For existing capabilities, construct `XML.readEvents(reader, emit)` or
`XML.writeEvents(writer)`. Each accepted reader operation performs exactly one
`readText()`: a String, including an empty compatible-reader chunk, resolves
true; null finishes the parser and resolves false. Each accepted writer event
produces one stable String and performs one `writeText(chunk)`; finish validates
completion and performs exactly one ordered `writeText("")` barrier. Writer
operations resolve to the exact adapter.

Allow one pending operation per adapter. Failure or cancellation leaves it
terminal, including cancellation before a continuation first runs. Downstream
cancellation does not implicitly cancel the upstream I/O Future. Neither adapter
closes, flushes, rewinds, retries, changes Encoding, or takes ownership.

DTD, custom/external entities, XML 1.1, fragments, recovery, schema validation,
XPath, XSLT, XInclude processing, Canonical XML, HTML behavior, automatic byte
encoding detection, and implicit filesystem/network acquisition are excluded.
URI values and processing instructions remain inert data.

See the original synthetic [Atom query](../../../protos/examples/xml/atom-query.protos),
[incremental parser](../../../protos/examples/xml/incremental-parser.protos), and
[sitemap encoder](../../../protos/examples/xml/sitemap-encoding.protos) examples.
