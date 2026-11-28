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
The maintainer reported 2088/2088 XML tests passing on the JVM on
2026-11-28, including all 1983 executable official profile cases. See the
acceptance evidence below; installed Native Image acceptance remains separate.

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

## External profile acceptance

The [offline acceptance documentation](../../../protos/tests/conformance/library/xml/official/README.md)
describes the pinned W3C release and captured real Atom input. Python performs
only acquisition metadata processing, deterministic fixture projection and
integrity checking. It never executes Protos tests or starts test processes.

The ordinary Test Tool executes one Test per full executable catalog identity.
Projection groups the current 1983 Tests by classification into 33 sources of at
most 64 Tests each: 2 accept, 4 reject and 27 profile-reject sources. The 602
nonexecutable cases remain in inventory metadata, with reasons and no passing
status. Each Test constructs its own exact fixture chunks inside its body; suite
discovery does not materialize all fixtures. The shared canonical harness retains
eager, whole-input and partitioned parsing, empty feeds, exact XML-owned rejection
checks, diagnostic/event-prefix equivalence and independent encoder/eventWriter
round-trips. Successful completion returns canonical `true`; unexpected Errors
and assertion failures escape to the Test Tool.

The canonical `Harness.protos` and `Fixtures.protos` are co-located in
`official/suite/`. Official tests import `./Harness.protos`, which imports
`./Fixtures.protos`; ordinary XML tests import `./official/suite/Fixtures.protos`.
The Test Tool confines direct-file SOURCE resolution to the selected entry's
parent directory. The shared helpers are authored once, never generated or
registered as Tests, and preserved by projection cleanup. No resolver authority
is widened.

The existing [Test Tool](../tools/test-tool.md) supplies discovery, fresh semantic
Process isolation, bounded `--jobs` parallelism, progress, failure classification
and distributed sharding. `--file` selects one generated source; `--directory`
selects the suite or XML subtree. The existing `make test-protos-lib LIB=xml`
owner scope includes registered official descendants. For exact selection, obtain
an opaque CaseRef with `--list-cases` and use `--case`; the catalog identity is
the Test name. No separate XML execution or scheduling mechanism is introduced.

Ordinary sources directly in `library/xml` present as
`conformance/standard-library/xml`. Official sources directly in
`library/xml/official/suite` present independently as
`conformance/standard-library/xml-official/<source>`, using the source filename
without `.protos` (for example `accept-00` or `profile-reject-26`). Each generated
source contains at most 64 Tests. Only sources with retained Cases contribute
official subgroups; selector directories and catalog identities do not define
groups. Existing file, directory, owner scope and shard selection still apply.
Progress grouping is presentation only and changes no identities, scheduling,
isolation or result classification. The maintainer's ordinary XML result was
105/105 under the former `data-text-test` label, taking 117 seconds. The later
suite-native run passed all 34 groups (33 official and one ordinary XML group).

Contributor redistribution rights remain unresolved. The publication policy is
conservative: keep the original W3C acquisition and fixture-bearing sources local;
version only original tooling, reviewed metadata and authored helpers. W3C
inventory, generation and checking verify the pinned archive and license offline
and do not require the independent historical Atom capture. `tools/xml/xml_test_projection.py generate` materializes
ignored local suite sources and registers only their exact suite-native rows in
the local conformance manifest. The permanent committed manifest remains valid
without acquisitions. Do not publish local rows or fixture-bearing sources.
`check` is read-only; explicit `cleanup` removes only owned registrations and
hash-verified generated sources, including obsolete standalone projection files.
It preserves canonical helpers, acquisitions, metadata and historical evidence.
Conflicts, unknown legacy files and modified owned sources fail safely.

Historical maintainer evidence includes 105/105 ordinary XML tests, 10/10 Test Tool
integration tests, 54/54 initial Python projection tests, GraalVM 25.6.5.1 alignment,
build, generation and artifact checking PASS. The former standalone path passed
its first regenerated case, `rmt-ns10-017`, and subsequently 65/65 Python
projection/executor tests, artifact integrity and official smoke 10/10.

The suite-native replacement subsequently passed 57/57 Python projection tests
and 27/27 focal Java tests, build, discovery, generation and artifact checking.
Inventory is COMPLETE: 2585 logical cases, 2584 distinct input members, 1983
executable and 602 nonexecutable cases, with zero unresolved references or
classifications. On 2026-11-28 the maintainer ran:

```sh
time bin/protos test --jobs 8 --directory protos/tests/conformance/library/xml
```

The reported result was **2088 passed, 0 failed**: 105 ordinary XML tests, 74
SUPPORTED_ACCEPT, 237 SUPPORTED_REJECT and 1672 PROFILE_REJECT. Wall-clock time
was 157.435 seconds (user 30m43.845s, sys 0m36.013s). The earlier standalone
duration was only an estimate, not a controlled benchmark. Generated metadata
continues to report NOT_EXECUTED; the execution evidence is independent.

Testing targets the approved DTD-free profile, not unrestricted XML conformance or
W3C certification. D268-R1 differential acceptance remains an outstanding
obligation. Mandatory real Atom and website-consumer parity require the independent
consumer result and exact website revision; generic parser agreement is insufficient.
Installed Native Image acceptance has not been established.
