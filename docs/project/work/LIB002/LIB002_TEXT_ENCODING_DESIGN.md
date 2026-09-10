# LIB002 Text / Encoding Conveniences Design Record

Status: `LIB002-A` implementation published; initial `LIB002` scope closed
Work item: `LIB002`
Nature: Project design record; **non-normative**
Research closed: 2026-09-06

## Purpose

This document records the focused design investigation for `LIB002 — Text /
encoding conveniences` so implementation does not begin from a familiar String,
codec, or stream API by default and later agents can distinguish decisions that
have already been investigated from questions that remain open.

The normative Protos specification under `spec/` remains authoritative. This
record does not add a Core String operation, an Encoding semantic value, an I/O
capability, a Future rule, a transfer rule, a module rule, or any other language
semantics. `LIB002` is ordinary Standard Library work built on the already
finalized Core Encoding/Text I/O substrate.

The design goal is deliberately narrow:

> Add useful ergonomic entry points for the mandatory portable encodings without
> weakening the explicit Encoding/TextReader/TextWriter model, inventing a second
> codec family, hiding I/O suspension, or constraining future Process, host,
> network, and distributed-runtime evolution.

## Audit scope

The design audit materially reviewed the current project rules, roadmap,
normative contracts, implementation boundaries, and executable evidence before
choosing an API.

### Repository and project guidance reviewed

- `AGENTS.md`
- `spec/AGENTS.md`
- `src/AGENTS.md`
- `docs/design/PROTOS_DESIGN_PHILOSOPHY.md`
- `docs/design/STANDARD_LIBRARY_IDEAS.md`
- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/registries/IMPLEMENTATION_BLOCKERS.md`
- `docs/project/governance/STANDARD_LIBRARY_NAMING.md`
- `docs/project/architecture/CORE_NATIVE_BOUNDARY.md`
- `docs/project/work/LIB001/LIB001_COLLECTIONS_DESIGN.md`

The important project-level constraints are:

- Standard Library behavior should remain ordinary Protos behavior whenever the
  existing language mechanisms suffice.
- `LIBxxx` work must not introduce or redefine normative Core behavior.
- Standard Library modules use the portable `std:<logical-name>` resolver
  namespace and live outside `protos/lib/core/`.
- Module state is Actor-local; mutable Standard Library state is not a
  process-global singleton.
- Observable Standard Library behavior should normally be tested with executable
  Protos source.
- The design must preserve the project principles of no pets, explicit semantic
  distinctions, minimal shared state, independent progress, and pay only for
  what is used.

### Normative material reviewed

The focused audit followed the applicable normative owners and their relevant
cross-references:

- `spec/io/TEXT_IO.md`
- `spec/io/IO_CORE.md`
- `spec/io/BYTE_IO.md`
- `spec/io/PROCESS_IO.md`
- `spec/semantics/OBJECT_MODEL.md`
- `spec/semantics/VALUES_AND_COLLECTIONS.md`
- `spec/semantics/MODULES.md`
- `spec/concurrency/FUTURES_AND_TASKS.md`
- `spec/concurrency/ACTORS.md`
- `spec/concurrency/PARALLEL_EXECUTION.md`
- `spec/concurrency/DISTRIBUTED_RUNTIME.md`

No normative contradiction or unresolved prerequisite was found for the initial
LIB002 surface described below.

### Implementation and tests reviewed

The audit also inspected the current implementation shape and existing
conformance coverage, including:

- `protos/lib/core/Encoding.protos`
- `protos/lib/core/String.protos`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardEncodingProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardTextReaderProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardTextWriterProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardStringProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardBytesProtocol.java`
- `src/main/java/com/guillermomolina/protos/runtime/ProtosEncodingValue.java`
- existing Encoding/TextReader/TextWriter conformance programs and manifest
  entries under `protos/tests/conformance/`;
- existing ordinary Standard Library modules and real-`std:` conformance under
  `protos/lib/collections/` and `protos/tests/conformance/library/collections/`.

This implementation evidence is not normative authority. It was inspected to
verify that the selected ordinary-library design can faithfully reuse the
normative Core surface without inventing a new native boundary.

## Core model that LIB002 must preserve

### String and Bytes remain different semantic domains

Core String is Unicode text. Binary I/O is byte-oriented and uses `Bytes`.
String never implies a binary encoding.

Encoding conversion therefore remains an explicit semantic boundary rather than
an implementation detail hidden inside String, Bytes, File, Socket, or a module
resolver.

### Encoding is a semantic family, not a duck-typed protocol

Core defines `Encoding` as a standardized reusable descriptor/configuration
abstraction. Mandatory portable descriptors are obtained exactly as:

```text
Encoding.UTF8
Encoding.UTF16LE
Encoding.UTF16BE
Encoding.Latin1
```

A host may explicitly provision additional Encoding values. Those additional
values are valid Encoding semantic values even when they are not one of the four
portable named descriptors.

Delegation, copying, composition, possession of `encode`/`decode` slots, or other
structural compatibility does **not** confer Encoding semantic-family
membership. Reaching Core Encoding behavior through delegation likewise does
not bypass the standard receiver-domain check.

This exact-family rule is central to the LIB002 design. An ordinary helper must
not silently replace it with duck typing.

### Encoding owns canonical one-shot conversion

The canonical Core one-shot direction is:

```text
encoding.encode(text)   -> Bytes
encoding.decode(bytes)  -> String
```

These operations are synchronous and in-memory. They do not return Futures.
Core deliberately does not require reciprocal `String.encode(encoding)` or
`Bytes.decode(encoding)` messages. Ordinary libraries may add conveniences only
when they preserve the canonical Encoding operation semantics.

A successful one-shot encode returns a fresh open Bytes identity, including for
empty output. `encode` accepts exactly semantic String and `decode` accepts
exactly semantic Bytes; neither performs implicit conversion.

### Descriptor state and per-flow state are separate

An Encoding descriptor is immutable/reusable and carries no I/O authority.
Mutable or otherwise evolving incremental encoder/decoder state belongs to the
individual conversion flow.

The current runtime reflects that contract directly: the descriptor creates a
fresh streaming encoder or decoder for each flow. Host-provided Encoding values
must obey the same rule. Actor/P transfer of the descriptor is safe because the
descriptor itself is authority-free and immutable; per-flow state is not shared
through it.

LIB002 must not introduce a cache, singleton encoder, shared decoder, mutable
registry entry, or other state that collapses this distinction.

### TextReader and TextWriter are explicit adapters

Portable construction is already:

```text
TextReader(source, encoding)
TextReader.owning(source, encoding)

TextWriter(target, encoding)
TextWriter.owning(target, encoding)
```

Construction validates the required byte capability and exact Encoding semantic
family before wrapper creation. Each success creates a fresh wrapper and fresh
per-flow codec state.

Borrowing and owning forms remain semantically different. A convenience layer
must preserve that distinction instead of hiding ownership or inferring it from
the wrapped capability.

### Waiting I/O stays Future-shaped

I/O operations that may wait return Futures. I/O introduces no hidden Protos
suspension; suspension occurs through ordinary Future mechanisms such as
`future.value()`.

`TextReader.readText()` is specifically a progress-oriented chunk read, not a
read-all operation. TextReader read operations share one ordered decoder/input
domain, with defined cancellation, failure, read-ahead, and lifecycle rules.
TextWriter operations likewise share one ordered encoder/output domain and
compose with the existing commitment, cancellation, flush, and close contracts.

A Standard Library convenience must not introduce an apparently ordinary call
that secretly waits on an unbounded stream or creates a second ordering domain.

### Modules are Actor-local

Core has no special global-variable semantic category. Imported module instances
belong to one Actor and each Actor owns its own module cache. The same canonical
ModuleKey therefore produces separate ordinary module instances in separate
Actors.

This is a useful property for LIB002: convenience behavior may be Actor-local
without creating a shared mutable process-global object. Immutable implementation
artifacts may still be physically shared when that sharing is unobservable.

### Protos Process is not an OS process

The distributed-runtime contract makes physical hosting intentionally
non-semantic:

- a Protos Process is an execution, isolation, and failure domain;
- it is not normatively an operating-system process or address-space boundary;
- one runtime may host multiple Protos Processes;
- physically local Processes may use shared memory, mmap, IPC, local sockets, or
  another optimized transport;
- Processes on different machines may use network transport;
- the physical transport must not change observable pass-by-value semantics or
  expose cross-Process mutable references;
- Node identity is also logical and does not equal one machine, VM, container,
  or Kubernetes node.

LIB002 must therefore remain correct without knowing whether two callers share a
thread, JVM, OS process, address space, machine, or network.

## Prior-art research

The comparison was question-driven rather than a popularity survey. Each family
was evaluated for text representation, encoding representation, streaming state,
I/O layering, defaults/global state, concurrency implications, and lessons that
survive translation into the Protos object/capability model.

### Pharo / Smalltalk — explicit character encoders

Pharo represents characters and strings as Unicode text. Zinc uses explicit
`ZnCharacterEncoding` implementations such as `ZnUTF8Encoder` to translate
between text and binary data. The Zinc documentation is especially relevant
because it explicitly rejects automatic conversions/default assumptions at
protocol boundaries where the encoding must be known.

Useful lesson for Protos:

- keep Unicode text separate from its external byte encoding;
- make the conversion boundary explicit;
- reusable encoder/encoding objects are useful, but conversion policy must not
  become ambient state.

What Protos should not copy:

- Smalltalk/Pharo class hierarchy structure is not a reason to add a Protos
  hierarchy or prototype family;
- existing Protos Encoding/TextReader/TextWriter semantics are already the
  semantic owner and should not be replaced by a library-level parallel object
  model.

Primary reference:

- https://books.pharo.org/booklet-Zinc/
- https://books.pharo.org/booklet-Zinc/pdf/2020-03-23-Zinc.pdf

### Self — prototype-native String organization

Self is important because its object/delegation model is much closer to Protos
than class-oriented languages. Its collection hierarchy makes String a vector
specialization and shares behavior through traits.

Useful lesson for Protos:

- reusable behavior can be expressed through ordinary prototype/delegation
  mechanisms rather than a class hierarchy;
- public API shape should fit the language's native object model.

What Protos should not copy:

- Self's String/byte-vector representation choices are VM-specific and differ
  from Protos's already-closed semantic distinction between String and Bytes;
- canonical-string VM tables and other representation institutions do not
  justify a LIB002 registry or privileged global table.

Primary reference:

- https://handbook.selflanguage.org/2024.1/collections.html

### Io — unified Sequence as a useful counterexample

Io unifies symbols, strings, buffers, and vectors in a `Sequence` abstraction.
Sequence carries an `encoding` attribute and its methods may perform automatic
representation conversions.

This is attractive for local convenience but conflicts with several Protos
properties:

- Protos String and Bytes are intentionally separate semantic domains;
- an Encoding is explicit rather than mutable metadata on every text/byte value;
- automatic conversion would make operation cost and failure policy less local;
- representation choices would become visible in places where Protos currently
  keeps them behind semantic boundaries.

Io is therefore evidence for an alternative that LIB002 should deliberately
**not** adopt.

Primary reference:

- https://iolanguage.org/docs/Guide/index.html

### Erlang / OTP — modules, Unicode conversion, and I/O servers

Erlang's `unicode` module contains explicit conversion functions between
character representations. The `io` module is a separate interface to I/O
servers; an I/O device is commonly represented by the pid of the process that
handles the I/O protocol.

The exact Erlang I/O protocol is not a design to copy into Core Protos, but its
long-lived separation is highly relevant:

```text
conversion functionality     separate from     I/O device ownership/state
```

and the stateful I/O endpoint can live behind process communication rather than
requiring a shared mutable client-side singleton.

Useful lesson for Protos:

- module-oriented convenience APIs scale naturally when state remains with the
  actual flow/resource;
- encoding conversion should not imply ownership of an I/O device;
- distribution does not require a global codec object shared across processes.

Primary references:

- https://www.erlang.org/docs/26/man/unicode.html
- https://www.erlang.org/docs/26/man/io
- https://www.erlang.org/docs/17/apps/stdlib/io_protocol

### Elixir — UTF-8 strings over BEAM binaries

Elixir Strings are UTF-8 encoded binaries and its String module applies Unicode
semantics over that representation. Raw binary operations remain available
separately.

Useful lesson for Protos:

- a strong single internal representation can simplify an ecosystem;
- modules are natural homes for reusable text algorithms in an Actor/process
  environment.

What Protos should not copy:

- Protos has already chosen semantic String values distinct from Bytes, so
  making every String a UTF-8 Bytes value in LIB002 would contradict Core rather
  than simplify it;
- the Standard Library must build on the existing semantic model instead of
  replacing it with the BEAM binary/String relationship.

Primary references:

- https://hexdocs.pm/elixir/String.html
- https://hexdocs.pm/elixir/binaries-strings-and-charlists.html

### C# / .NET — Encoding descriptor plus stateful Encoder/Decoder

.NET has a useful separation among reusable `Encoding`, stateful `Encoder` /
`Decoder`, byte-oriented `Stream`, and text-oriented `StreamReader` /
`StreamWriter`.

The most relevant detail is that `Encoding.GetEncoder()` and
`Encoding.GetDecoder()` produce stateful conversion objects specifically because
network and file operations process blocks whose encoded sequences may span
buffer boundaries.

This strongly validates the Core Protos decision:

```text
reusable descriptor
        +
per-flow incremental state
        +
explicit text adapter over byte I/O
```

.NET also shows a feature Protos should avoid copying: `StreamReader` supports
encoding defaults and BOM-based autodetection. `CurrentEncoding` may therefore
change after reading begins. Protos intentionally keeps the selected Encoding
explicit and does not let a BOM silently change it to another Encoding.

Primary references:

- https://learn.microsoft.com/dotnet/api/system.text.encoding
- https://learn.microsoft.com/dotnet/api/system.text.encoding.getencoder
- https://learn.microsoft.com/dotnet/api/system.text.encoding.getdecoder
- https://learn.microsoft.com/dotnet/api/system.io.streamreader

### Java — immutable Charset, explicit Reader/Writer bridge, and registry trade-offs

Java `Charset` is an immutable mapping with one-shot conversion and factories for
stateful encoder/decoder objects. `InputStreamReader` bridges byte streams to
character streams using a specified Charset or CharsetDecoder.

This again validates separation between descriptor, incremental state, bytes,
and text.

Java also demonstrates trade-offs that are not appropriate for initial LIB002:

- Charset has name/alias lookup and VM-wide supported-charset discovery;
- convenience `Charset.encode/decode` uses replacement behavior rather than
  Protos's strict default;
- Reader construction may use a default charset when none is supplied.

Protos already has explicit descriptors, strict default conversion, and no Core
name registry. LIB002 should preserve those decisions rather than importing
Java's discovery/default institutions.

Primary references:

- https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/nio/charset/Charset.html
- https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/io/InputStreamReader.html

### C++ — cautionary history around codecvt and locale coupling

C++ historically coupled Unicode conversion to `<codecvt>`/locale machinery.
Those Unicode conversion facets were deprecated in C++17 and removed in C++26.
The removal proposal identifies under-specification, including inadequate error
handling, as a core problem.

C++26 separately introduces `std::text_encoding` work focused on **naming and
identifying** encodings and explicitly discusses separating encoding identity
from locale formatting/text-transformation concerns.

Useful lesson for Protos:

- avoid making Encoding simultaneously own conversion, locale, registry,
  formatting, stream buffering, and environmental defaults;
- error policy and encoding identity need explicit contracts;
- an encoding registry/name system is a distinct problem from conversion.

Primary references:

- https://isocpp.org/files/papers/P2871R3.pdf
- https://isocpp.org/files/papers/P1885R12.pdf

### Python — explicit TextIOWrapper is good; locale-dependent defaults are not

Python separates binary buffered streams from `TextIOWrapper`, which adds a
selected encoding, error policy, and newline behavior. That separation is useful
prior art for a byte/text adapter.

Python documentation also warns about a portability problem caused by the
locale-specific default encoding of text files and recommends explicitly
selecting UTF-8 when UTF-8 is intended.

Useful lesson for Protos:

- explicit text adapters are robust;
- implicit environment-selected encodings create cross-platform surprises.

Core Protos already makes standard-stream Encoding association explicit through
Process provisioning, so LIB002 should not add a competing default.

Primary reference:

- https://docs.python.org/3/library/io.html

### Ruby — flexible per-String encodings expose the cost of ambient defaults

Ruby associates Encoding with Strings and I/O can have external and internal
encodings. It also provides global default external/internal encoding settings.
Ruby's own documentation warns that changing `Encoding.default_external` in
program code can leave strings created before and after the change with different
encodings.

Useful lesson for Protos:

- external versus internal representation is a real distinction;
- mutable global defaults make local reasoning and multi-component composition
  harder.

What Protos should avoid:

```text
Encoding.default
Text.defaultEncoding
currentEncoding
setDefaultEncoding(...)
```

Such a value immediately raises ambiguous scope questions in Protos: Actor,
Process, OS process, Node, Cluster, or host. The simplest answer is to have no
such ambient Standard Library setting.

Primary references:

- https://docs.ruby-lang.org/en/master/Encoding.html
- https://docs.ruby-lang.org/en/master/language/encodings_rdoc.html

### Rust — explicit UTF-8 construction and the cost of read-all helpers

Rust distinguishes bytes from UTF-8 String construction and provides explicit
I/O helpers such as `read_to_string`/`read_to_end`. The read-all family is useful
but necessarily accumulates data until EOF and has its own allocation/error
contract.

Useful lesson for Protos:

- whole-input convenience is a separate semantic/resource commitment from
  progress-oriented streaming;
- a read-all helper should not be smuggled in as a trivial alias around a chunk
  read.

Primary references:

- https://doc.rust-lang.org/std/string/struct.String.html
- https://doc.rust-lang.org/std/io/fn.read_to_string.html

### Swift — Unicode String with explicit encoding views

Swift String is Unicode text and exposes explicit `unicodeScalars`, `utf8`, and
`utf16` views. Representation/encoding views are not the same thing as the
String's user-visible Character collection.

Useful lesson for Protos:

- external or code-unit representation can remain an explicit view/boundary
  without making the semantic text value itself carry mutable external-encoding
  state;
- Unicode text operations and byte encoding are related but distinct concerns.

Primary references:

- https://developer.apple.com/documentation/swift/string
- https://developer.apple.com/documentation/swift/string/utf8view

## Cross-language conclusions

The prior-art study does not identify one API to copy. It does reinforce several
stable design principles that align with existing Protos semantics:

1. **Text and external byte representation should remain distinct.**
2. **Reusable encoding description and per-flow conversion state should remain
   distinct.**
3. **I/O lifecycle/authority should remain with the actual I/O wrapper/resource,
   not with a convenience module.**
4. **Ambient locale/default encoding is a portability and composition hazard.**
5. **Encoding discovery/registries are a separate problem from conversion.**
6. **Module-oriented convenience scales well when modules do not own shared
   mutable flow state.**
7. **Read-all and Unicode text transformations have independent contracts and
   should not be bundled into a thin encoding-convenience slice.**

## Design alternatives evaluated

### A. One aggregate `std:text/Text` module

Candidate shape:

```text
Text: import("std:text/Text")

Text.UTF8.encode(text)
Text.UTF8.decode(bytes)
Text.UTF8.reader(source)
...
```

Advantages:

- one obvious import;
- one namespace in which later text conveniences could accumulate.

Problems:

- importing one portable codec materializes an aggregate institution containing
  unrelated codecs;
- `Text` tends to become a de facto registry or dumping ground for unrelated
  String transformation, locale, normalization, stream, and codec concerns;
- future growth would make a simple UTF-8 caller pay for a wider conceptual
  surface it did not request;
- an aggregate `Text` object has no corresponding Core semantic responsibility.

Disposition: **viable but rejected for the initial design**. The aggregate adds
more institution than the convenience requires.

### B. One ordinary module per mandatory portable Encoding

Selected shape:

```text
std:text/UTF8
std:text/UTF16LE
std:text/UTF16BE
std:text/Latin1
```

Each module is an ordinary Actor-local Standard Library module whose behavior is
hard-wired to the corresponding already-standard Core descriptor. No module is
itself an Encoding semantic value.

Advantages:

- no registry or shared mutable state;
- import cost is local to the codec actually used;
- Core performs all exact String/Bytes/capability/Encoding validation;
- Core still owns all per-flow encoder/decoder state;
- the module creates no new runtime family or native boundary;
- mandatory portable encodings remain explicit, while host-provided Encoding
  values continue to work directly through Core;
- future additional standard helpers can be added without changing the existing
  Core Encoding contract.

Disposition: **selected for LIB002-A**.

### C. Generic library `Codec(encoding)` wrapper

Attractive candidate:

```text
Codec: import("std:text/Codec")
utf8: Codec(Encoding.UTF8)
custom: Codec(hostEncoding)
```

This would be the most general surface if ordinary Protos could validate the
exact Encoding semantic family.

The current language surface does not expose a general ordinary operation that
answers "is this exact semantic Encoding value?". A pure-Protos wrapper that
merely stores `encoding` and later sends `encoding.encode(text)` would accept an
ordinary object with its own user-defined `encode` slot. That silently changes
the library contract from exact Encoding membership to duck typing.

Counterexample:

```text
fake: {
    encode: (text) => { ... }
    decode: (bytes) => { ... }
}
```

`fake` is not an Encoding descriptor merely because those slots exist. A generic
wrapper that accepted it would weaken the Core invariant the convenience claims
to preserve.

Disposition: **rejected for now**. A future general library wrapper may be
re-audited if Protos later gains an ordinary, general mechanism that can preserve
semantic-family validation without adding a LIB002-specific primitive.

### D. Add reciprocal messages to Core String / Bytes

Candidate surface:

```text
text.encode(encoding)
bytes.decode(encoding)
```

Core explicitly allows ordinary libraries to expose conveniences but does not
require these reciprocal messages. Adding them directly to String/Bytes would
move optional Standard Library ergonomics back into Core/prelude behavior and
would make the canonical Encoding dispatch direction less obvious.

Disposition: **rejected for LIB002**. This work item does not modify
`protos/lib/core/`, the frozen prelude, or the native String/Bytes boundary.

### E. Do nothing

Core is already usable directly:

```text
Encoding.UTF8.encode(text)
TextReader(source, Encoding.UTF8)
```

This is maximally conservative but provides no additional ergonomic value and
would leave the READY Standard Library item without a meaningful result.

Disposition: **safe but insufficient**.

## Selected LIB002-A public surface

The initial implementation should publish exactly four ordinary modules:

```text
std:text/UTF8       -> protos/lib/text/UTF8.protos
std:text/UTF16LE    -> protos/lib/text/UTF16LE.protos
std:text/UTF16BE    -> protos/lib/text/UTF16BE.protos
std:text/Latin1     -> protos/lib/text/Latin1.protos
```

Each module exposes the same six operations:

```text
encode(text)
decode(bytes)
reader(source)
owningReader(source)
writer(target)
owningWriter(target)
```

Conceptually, UTF8 is only:

```text
encode(text)
    -> Encoding.UTF8.encode(text)

decode(bytes)
    -> Encoding.UTF8.decode(bytes)

reader(source)
    -> TextReader(source, Encoding.UTF8)

owningReader(source)
    -> TextReader.owning(source, Encoding.UTF8)

writer(target)
    -> TextWriter(target, Encoding.UTF8)

owningWriter(target)
    -> TextWriter.owning(target, Encoding.UTF8)
```

The other three modules substitute only their corresponding mandatory Core
Encoding descriptor.

### The helper module is not an Encoding

This distinction is intentional and testable:

```text
UTF8: import("std:text/UTF8")

TextReader(source, UTF8)           // invalid Encoding argument
TextReader(source, Encoding.UTF8)  // Core construction
UTF8.reader(source)                // library convenience
```

The module object does not acquire Encoding semantic-family membership,
Encoding identity, per-flow codec state, or I/O authority merely because it
provides encoding-related convenience messages.

### No exported mutable codec state

The modules should contain behavior only. They do not maintain:

- cached encoder/decoder state;
- a mutable default Encoding;
- a codec registry;
- a name/alias registry;
- a current locale;
- a global BOM/autodetection setting;
- I/O source/target state;
- a queue, lock, scheduler, or cross-Actor coordination service.

Each call reaches the existing Core operation/factory, which owns the applicable
validation, result identity, Future, ordering, cancellation, ownership, and
lifecycle behavior.

### Host-provided Encoding values remain first-class Core inputs

LIB002-A does not attempt to wrap every possible Encoding descriptor. A
host-provided Encoding remains usable through Core exactly as before:

```text
encoding.encode(text)
encoding.decode(bytes)
TextReader(source, encoding)
TextWriter(target, encoding)
```

The four library modules are convenience entry points for the four mandatory
portable descriptors; they are not a claim that those four values exhaust the
Encoding semantic family.

If a future standard introduces another mandatory portable Encoding, a new
ordinary module can be added after a focused API review without changing the
existing four module identities or the Core Encoding contract.

## Future-proofing and scale audit

The selected design was explicitly tested against growth from trivial local
programs to multiple Actors, multiple Protos Processes, multiple operating-system
processes, and distributed Nodes.

### One Actor / one Process

Only the imported helper module and the Core objects actually used are involved.
There is no global registry, scheduler, or background worker.

Result: **PASS**.

### Many Actors in one Protos Process

Each Actor receives its own module instance under existing module semantics.
The helper module owns no mutable flow state. Core Encoding descriptors are
semantically immutable/authority-free and the current transfer boundary may
share/rematerialize them according to the closed Encoding transfer contract.

No Actor must coordinate with another Actor merely to encode local text.

Result: **PASS**.

### Isolated P work

The helper module is ordinary behavior and need not cross the P boundary. Core
Encoding descriptors are authority-free and already have the applicable P
transfer contract. Live I/O authority retains its existing restrictions.

LIB002 adds no P-specific path and makes no non-transferable capability
transferable.

Result: **PASS**.

### Multiple Protos Processes in one runtime / OS process

The API assumes no shared mutable module instance and no process-global default.
Each Process/Actor can import its own helper behavior and use explicitly
provisioned capabilities/descriptors.

Result: **PASS**.

### Multiple operating-system processes

The API contains no Java/JVM identity, memory address, static registry, process
singleton, or shared heap assumption. Equivalent module code and portable Core
Encoding semantics are sufficient.

Result: **PASS BY DESIGN**.

### Multiple machines / Nodes

The distributed-runtime specification permits physical transport choices to
change while preserving pass-by-value semantics. LIB002 does not use transport
identity or physical locality as part of its API.

A remote text flow still uses whatever explicitly provisioned byte capability
and Core TextReader/TextWriter semantics the networking/distributed layer
provides. The helper remains local behavior.

Result: **PASS BY DESIGN**.

### Host-provided encodings

Additional host Encoding descriptors require no LIB002 registry entry and no
change to the four portable helper modules. Direct Core construction/conversion
remains available.

Result: **PASS**.

### Stateful future encodings

Per-flow encoder/decoder state remains inside Core's fresh flow state. The
helper never caches a stateful converter. Therefore an encoding such as a future
stateful host codec does not require changing LIB002's concurrency model.

Result: **PASS**.

### Future Unicode revisions

The initial helper modules perform no Unicode casing, normalization,
segmentation, collation, or locale algorithm. They delegate conversion semantics
to the selected Core Encoding contract.

Updating an independently owned Unicode algorithm therefore does not require
redefining the LIB002-A module architecture.

Result: **PASS**.

### Future network streams

LIB002 does not acquire network authority or define socket semantics. A future
networking facility can provision ByteReadable/ByteWritable capabilities, which
then compose with the existing Core TextReader/TextWriter layer. No network
special case is required in the helper.

Result: **PASS**.

### Backpressure and large streams

The helper adds no buffer, read-ahead, output queue, or admitted-work capacity of
its own. All waiting/ordering/backpressure remains governed by the Core I/O
receiver used by the program.

Result: **PASS**.

## Explicitly rejected global/default state

The initial library must not add any equivalent of:

```text
Encoding.default
Text.defaultEncoding
Text.currentEncoding
setDefaultEncoding(...)
```

Besides the ordinary global-state problems, such a setting has no coherent
portable scope in Protos. It would immediately require choosing among Actor,
Process, OS process, Node, Cluster, module instance, or host environment.

The existing architecture already has the stronger compositional answer:
Encoding is explicit where conversion is required, while Process hosts may
explicitly provision the Encoding associated with standard streams.

No new ambient state is necessary.

## Why `readAll` is outside LIB002-A

A helper such as:

```text
readAll(reader)
```

looks small but is not semantically equivalent to Core `readText()`.

A correct contract would have to define at least:

- whether the helper returns a Future or suspends internally;
- structured ownership of any continuation/task it creates;
- cancellation propagation to the current underlying read;
- the relationship between helper cancellation and TextReader's zero-consumption
  cancellation contract;
- behavior after already-buffered/read-ahead text;
- EOF and permanent TextReader failure behavior;
- lifecycle interaction with close;
- memory growth for arbitrarily large sources;
- behavior on sources that never reach EOF;
- string-assembly complexity and allocation guarantees.

A synchronous ordinary helper that repeatedly calls `.value()` would hide
suspension behind the helper call. A helper implemented through `Future.then`
would need its own structured-cancellation reasoning because cancelling a
continuation destination does not automatically cancel its source Future.

`readAll` is therefore a distinct higher-level asynchronous/resource abstraction,
not a thin encoding alias.

Disposition: **deferred; requires its own focused design if requested**.

## Why String transformations are outside LIB002-A

Core intentionally does not standardize `uppercase()` or `replace(...)` merely
as obvious String methods. Case mapping, normalization, locale-sensitive rules,
replacement semantics, split/join contracts, collation, and similar text
algorithms each introduce questions independent from external byte encoding.

Examples include:

- Unicode version and update policy;
- locale-independent versus locale-tailored behavior;
- one-to-many case mappings;
- grapheme/scalar/code-unit matching domains;
- empty-pattern behavior;
- normalization preservation or transformation;
- callback/effect behavior for generalized replacements;
- result allocation and complexity guarantees.

Bundling those questions into the first Encoding convenience slice would turn
LIB002 into an unbounded "everything String" project and mix independent
semantic owners.

Disposition: **out of the initial LIB002 closure**. A future text-algorithms
library item should receive its own focused comparative/API audit.

## Falsification cases used to reject or constrain designs

The selected design was tested against concrete failure scenarios rather than
only its happy path.

### Fake Encoding object

An ordinary object defines `encode` and `decode` but is not an Encoding semantic
value.

Required outcome: a convenience that claims Core Encoding semantics must not
accept it as though it were an Encoding descriptor.

Consequence: reject generic pure-Protos `Codec(encoding)` until exact family
validation can be preserved.

### Mutable global default changed by another component

Two independent libraries expect different encodings and one changes a global
default.

Required outcome: neither library's meaning should depend on the other's hidden
configuration.

Consequence: no LIB002 global/default encoding state.

### Host-provided encoding unknown to the Standard Library

A Process host explicitly provisions a valid Encoding not among the four
portable descriptors.

Required outcome: it remains usable without registering it in a mutable
Standard Library table.

Consequence: Core direct path remains the general extension path.

### Stateful sequence split across native/network reads

A logical encoded character or state transition spans backend chunks.

Required outcome: flow state remains continuous and local to the TextReader or
TextWriter.

Consequence: convenience modules never cache or share encoder/decoder state.

### Two Actors concurrently use one logical writer

Required outcome: TextWriter's existing ordering/commitment rules remain the one
source of truth.

Consequence: no LIB002 lock, queue, multiplexing rule, or second ordering domain.

### Helper used in different OS processes or Nodes

Required outcome: the same public helper spelling and Core semantics work
without shared memory/object identity.

Consequence: ordinary local module behavior only; no physical singleton.

### Infinite or long-lived source passed to read-all

Required outcome: a library operation must make its unbounded wait/memory
contract explicit rather than masquerading as a small convenience.

Consequence: `readAll` excluded from LIB002-A.

### Future Unicode algorithm changes

Required outcome: a change to Unicode casing/normalization must not force a
redesign of byte/text conversion helpers.

Consequence: keep text transformations separate from initial Encoding
conveniences.

## LIB002-A implementation plan

One implementation slice is sufficient because the four modules instantiate the
same already-audited law and no independently useful semantic intermediate state
exists between them.

### Slice identity

```text
LIB002-A — Portable text/encoding conveniences
```

### Planned distributable source

```text
protos/lib/text/UTF8.protos
protos/lib/text/UTF16LE.protos
protos/lib/text/UTF16BE.protos
protos/lib/text/Latin1.protos
```

No production Java file and no `protos/lib/core/**` file should change.

### Planned executable conformance

Prefer Protos-level tests under the existing Standard Library conformance tree.
The implementation should cover at least:

- each canonical `std:text/...` module resolves through the real Standard
  Library resolver;
- `encode` matches the corresponding Core descriptor semantics;
- each successful encode preserves Core fresh/open Bytes result behavior;
- `decode` matches Core String result and strict failure behavior;
- invalid String/Bytes arguments retain the Core failure path rather than being
  coerced;
- `reader`/`writer` preserve borrowing construction;
- `owningReader`/`owningWriter` preserve explicit ownership construction;
- invalid byte capability inputs are rejected by the Core factory path;
- the imported module object is not accepted as an Encoding descriptor;
- repeated calls do not expose shared per-flow codec state;
- the four modules remain ordinary Standard Library behavior with no production
  Java/native-boundary addition.

Java tests should be added only if a specifically Java-side invariant cannot be
meaningfully checked from Protos. The expected initial slice should not require
such a Java-specific behavior change.

### Publication and closure

`LIB002-A` is executable Standard Library work, so the implementation commit must
follow the repository's executable-impact publication rules:

1. re-fetch current `origin/main` and re-check that this design's assumptions are
   still valid;
2. increment the Maven implementation version exactly once;
3. update root `CHANGELOG.md`;
4. add the four library modules and Protos conformance/manifest entries;
5. run focused applicable tests;
6. run the full Maven test suite;
7. run static/governance/license/diff checks required by the repository;
8. publish only after all required validation passes;
9. update `docs/project/IMPLEMENTATION_STATUS.md` in the same publication.

If the complete selected initial surface is implemented and validated with no
new blocker, `LIB002-A` and the top-level `LIB002` work item may both close in
that publication. There is no need to keep LIB002 open merely for unrelated
future Unicode text algorithms or read-all facilities that were explicitly
excluded by this design.

## Deferred / separate future concerns

The following are intentionally **not** promised by LIB002-A:

```text
readAll
readLines
writeAll
String.encode / Bytes.decode Core augmentation
uppercase / lowercase / case folding
replace
split / join
Unicode normalization
collation
locale
encoding names / aliases / discovery registry
automatic encoding detection
default/current Encoding
incremental public encoder/decoder feed/reset API
```

Some may become useful Standard Library work later. Each should be evaluated
against its own semantic, resource, concurrency, portability, and API pressures
rather than being treated as automatically part of the text/encoding helper
surface.

## Closed design status

The result of the focused investigation is:

```text
LIB002: CLOSED
REPOSITORY AUDIT: CLOSED
NORMATIVE AUDIT: CLOSED
PRIOR-ART AUDIT: CLOSED
DESIGN FALSIFICATION: CLOSED
FUTURE-PROOFING / SCALE AUDIT: CLOSED
NORMATIVE_BLOCKER: NONE
API DESIGN: CLOSED
IMPLEMENTATION: CLOSED
LIB002-A: CLOSED
```

Implementation should not reopen these decisions solely because another familiar
language uses a different syntax. Re-audit is warranted if current `main`
changes one of the Core invariants on which the design depends, or if
implementation exposes a genuine normative or architectural contradiction.
