# Process, I/O, Filesystems, and Authority

> **Status:** Non-normative programming guide
>
> **Primary normative owners:** `spec/io/PROCESS_IO.md`, `spec/io/IO_CORE.md`,
> `spec/io/BYTE_IO.md`, `spec/io/TEXT_IO.md`, and `spec/io/FILESYSTEM.md`
>
> Related normative owners: `spec/concurrency/FUTURES_AND_TASKS.md`,
> `spec/concurrency/ACTORS.md`, `spec/concurrency/PARALLEL_EXECUTION.md`,
> `spec/semantics/VALUES_AND_COLLECTIONS.md`, and `spec/semantics/ERRORS.md`

Protos I/O is organized around explicit capabilities rather than a global
operating-system namespace.

The key mental model is:

> A `Process` is a Protos execution domain and custodian of Process-local
> bootstrap authority. `Filesystem` is a separate namespace capability. `Path`
> is an authority-free structural value. Open `File`, byte-stream, and text
> wrappers expose only the operations their concrete capabilities actually
> support.

That separation is deliberate.

A path does not grant access to anything.

A Process does not imply filesystem authority.

An imported module does not automatically inherit ambient Process or Filesystem
authority.

A wrapper does not automatically inherit every capability of the object it
wraps.

I/O that may wait returns ordinary Futures and suspends only when ordinary Future
operations such as `value()` are used.

## Start with authority, not filenames

A useful way to read Protos I/O code is to ask:

```text
What value carries the authority for this operation?
```

For example:

```text
Path.relative().child("settings.json")
```

only constructs a Path value.

It does not read a file, consult the current directory, touch an operating-system
namespace, or discover a default Filesystem.

The actual authority enters here:

```text
filesystem.open(path)
```

where `filesystem` is an explicitly provisioned `Filesystem` capability.

This yields a general rule:

> Data that describes a resource and authority to access a resource are separate
> values.

That distinction keeps ordinary data transferable and easy to reason about while
making access boundaries visible.

## A Protos Process is not an operating-system process

Every Protos execution has exactly one Protos `Process` domain and begins with one
RootActor.

A standalone launcher may map that Process to one operating-system process, but
that mapping is not the language model.

A managed runtime could host Protos Processes differently.

The Process exists to define one execution/failure/bootstrap domain, not to expose
a portable PID, POSIX process table, signal API, subprocess API, or arbitrary host
process introspection.

Core v0.1 deliberately leaves those host/process-control facilities outside this
I/O model.

## The initial module receives `process` locally

Before the RootActor's initial module evaluates its first source expression, the
host provisions a local binding:

```protos
process
```

on that initial module's `moduleContext`.

This is bootstrap-local state.

It is **not**:

```text
a global variable
a prelude singleton
an intrinsic
an import side effect
a service locator
```

Imported modules do not receive `process` automatically.

If imported code needs Process authority, ordinary code must pass the capability
or a narrower capability derived from it.

This is the same explicit-authority principle used elsewhere in Protos.

## Filesystem authority is separately provisioned

A host may also provision a bootstrap-local:

```protos
filesystem
```

binding on the initial module context.

That authority is independent of `process`.

Possessing a Process capability does not provide an operation that recovers a
Filesystem.

When no default Filesystem authority is granted, the `filesystem` binding is
absent; it is not bound to `null`.

Imported modules likewise do not receive the initial Filesystem automatically.

This lets an embedder or tool give a program exactly the namespace authority it
needs without turning filesystem access into ambient global state.

The current standalone application CLI intentionally grants no default
application Filesystem merely because the launcher itself could read the source
file. Bundled tools may receive specifically confined Filesystem capabilities
because their launcher contract grants them explicitly.

In the standard Polyglot embedding, the initial module receives `filesystem`
only when the host Context effectively allows file access. Its base is the
Context's working directory within the filesystem provider the host configured,
and every operation goes through that provider, so a virtual, restricted, or
read-only provider keeps its restrictions. If file access is allowed but that
working directory cannot be safely established, the first evaluation fails
before any guest code runs instead of starting without the binding.

## Process authority can be delegated to another Actor

Creating another Actor does not implicitly inherit the Process capability.

If code explicitly supplies a Process capability across an Actor boundary, the
standard Process delegation contract rematerializes an Actor-local proxy to the
same logical Process authority.

That is explicit capability delegation, not shared mutable wrapper identity.

The destination does not gain more Process authority than the source capability
carried.

This special Process delegation also does not make arbitrary resources
transferable.

In particular, it does not imply that an open `File`, a native handle, or any
other non-transferable live resource can cross an Actor boundary.

`Process` has no Core P-transfer contract and cannot be captured or supplied into
isolated P execution.

## `process.args()` is a frozen ordinary Array

The Process argument interface is:

```protos
args: process.args()
```

The result is an ordinary Core `Array` of the application-argument Strings, in
the order supplied, frozen before it is exposed. It has no Process-specific type
or protocol: `size()`, `at(index)`, indexing, and `each(block)` are the ordinary
Array operations, and mutation such as `atPut` fails because the Array is frozen.

```protos
args.size()
args.at(0)
args.atPut(0, "x")   // fails: the Array is frozen
```

Every element is a valid Protos `String`. With no application arguments the
result is an empty frozen Array.

The launcher/executable identity analogous to POSIX `argv[0]` is not part of
this application-argument sequence.

The portable contract is the stable content, not the identity of the container.
Two calls observe the same count, order, and Strings, but whether they return the
same Array object is unspecified:

```protos
a: process.args()
b: process.args()

a === b    // not portable: may be true or false
```

Portable code compares or uses the contents and never relies on either outcome.
Because the Array is frozen and its elements are Strings, it can be passed to
another Actor under the ordinary Array transfer rules.

## Argument capture is stable against host mutation

`process.args()` does not re-read a mutable native argument area on every call.

Conceptually:

```text
Process bootstrap
    |
    | establish one portable argument snapshot
    v
stable Protos argument sequence
```

Once established successfully, later host-native argument mutation does not alter
the Protos values.

If the complete native argument set cannot be represented as valid Protos
Strings, acquisition fails rather than exposing a partial or lossy snapshot.

This follows a recurring Protos boundary rule:

> Host representation is validated at the boundary; invalid host state does not
> leak into ordinary Protos values.

## `process.environment()` is a read-only snapshot, not a Map

The Process environment interface is:

```protos
env: process.environment()
```

with the conceptual protocol:

```text
get(name)
contains(name)
each(block)
```

It is not specified as a standard `Map`.

Like the argument sequence, a successfully established Environment has stable
content for the logical Process. Whether separate calls to `process.environment()`
return the same object is unspecified and not portable.

`get(name)` distinguishes:

```text
valid representable name, present      -> String value
valid representable name, absent       -> null
name not representable in native domain -> Error
present value not representable as String -> Error
```

An unrepresentable query name is not silently treated as absence.

`contains(name)` can determine existence without requiring the value itself to
decode successfully.

Enumeration validates the portable `(String, String)` representation before
running user callbacks and uses the specification's deterministic Unicode-scalar
name ordering rather than host enumeration order.

## Standard streams are byte capabilities

The Process standard-stream accessors are:

```protos
process.stdin()
process.stdout()
process.stderr()
```

These are synchronous capability lookups.

The streams are byte-oriented.

Text encoding is deliberately not implicit.

Each stream is independently optional.

A Process may have:

```text
stdin only
stdout only
stdout + stderr
all three
none
```

according to what the host provisioned.

Redirection changes the resource behind the capability; it does not change the
portable byte protocol.

## Standard-stream encodings are explicit associations

The corresponding text associations are:

```protos
process.stdinEncoding()
process.stdoutEncoding()
process.stderrEncoding()
```

Each successful accessor returns an `Encoding` descriptor associated with that
Process standard stream.

This does not make byte streams secretly textual.

The program still chooses to create a text wrapper explicitly:

```protos
reader: TextReader(process.stdin(), process.stdinEncoding())
writer: TextWriter(process.stdout(), process.stdoutEncoding())
```

The host-selected Encoding may be one of the portable descriptors or another
explicitly provisioned Encoding semantic value.

There is no hidden locale/default codec lookup inside `TextReader` or
`TextWriter`.

## Encoding descriptors carry configuration, not I/O authority

The standard portable Encoding descriptors are:

```text
Encoding.UTF8
Encoding.UTF16LE
Encoding.UTF16BE
Encoding.Latin1
```

An Encoding descriptor is reusable and authority-free.

It describes conversion semantics; it does not identify an open stream or grant
access to one.

The one-shot operations are synchronous:

```protos
bytes: Encoding.UTF8.encode(text)
text: Encoding.UTF8.decode(bytes)
```

`encode` produces a fresh open `Bytes` result.

`decode` produces a Protos `String`.

String-to-byte conversion is never implicit merely because an I/O method needs
bytes.

## Encoding family membership is semantic, not duck typing

An ordinary object does not become an Encoding merely because it provides:

```text
encode
decode
```

or delegates to an Encoding object.

Parameters that require an Encoding descriptor accept only members of that
semantic family.

This keeps host-provided stateful codecs, portable descriptors, wrapper
construction, and transfer semantics coherent.

A wrapper never needs to guess whether a lookalike object is "probably an
encoding".

## TextReader and TextWriter are explicit layers

Portable borrowing construction is:

```protos
reader: TextReader(source, encoding)
writer: TextWriter(target, encoding)
```

where:

```text
source satisfies ByteReadable
target satisfies ByteWritable
encoding is an Encoding descriptor
```

Each successful construction creates a fresh wrapper identity and fresh
per-flow codec state.

The wrapper's decoder/encoder state is not shared merely because two wrappers use
the same Encoding descriptor.

This separation is important for stateful encodings and concurrent independent
flows.

## Borrowing versus owning is a lifetime decision

Borrowing wrappers are:

```protos
TextReader(source, encoding)
TextWriter(target, encoding)
```

Owning wrappers are:

```protos
TextReader.owning(source, encoding)
TextWriter.owning(target, encoding)
```

Ownership changes cleanup responsibility; it does not enlarge authority.

A borrowing wrapper closes its own wrapper lifecycle without implicitly taking
ownership of the underlying resource.

An owning wrapper includes release of the underlying closable resource in its
lifecycle.

For a TextWriter, close must finish any required encoder-final bytes before an
owned target is released.

Choose ownership according to who is responsible for resource lifetime, not
according to convenience at one call site.

## TextReader has one ordered decoded-input domain

A standard TextReader provides:

```text
readText()
readLine()
readLine(maxBytes)
close()
```

The text-reading operations share one logical decoder/input sequence.

They are not independent readers that can race past each other merely because
several Futures are pending.

`readText()` is progress-oriented: once useful complete decoded text is
returnable, the operation does not wait merely to fill a preferred larger
buffer.

`readLine()` recognizes LF, CR, and CRLF framing and omits the terminator from
the returned String.

`readLine(maxBytes)` applies an explicit encoded-source byte bound rather than a
character-count guess.

EOF with no remaining text is `null`.

## TextWriter has one ordered encoder/output domain

A standard TextWriter provides the current text-output wrapper surface:

```text
writeText(text)
writeLine(text)
flush()
close()
```

Text output is encoded explicitly using that wrapper's Encoding descriptor.

`writeLine` uses the standard line-output contract rather than host-dependent
newline selection.

TextWriter close is a real lifecycle boundary: stateful encoder finalization must
happen before the wrapper finishes closing.

Borrowing versus owning still controls whether the underlying target itself is
released by that wrapper lifecycle.

## There is no universal `Stream` object

Core I/O is capability-oriented.

The standardized byte-facing capability protocols include concepts such as:

```text
ByteReadable
ByteWritable
Flushable
Closable
ByteSeekable
ByteSized
Truncatable
Syncable
ReadShutdown
WriteShutdown
```

These names describe observable capability protocols.

Core does not require every resource to inherit from one universal semantic
`Stream` prototype.

This lets a File, Process standard stream, buffered wrapper, future socket, or
other resource expose only the operations it can implement correctly.

## Wrapping does not automatically forward capabilities

Suppose a byte target is seekable, closable, syncable, and writable.

A wrapper over it is not automatically all of those things.

A wrapper exposes a capability only when the wrapper itself defines the correct
observable protocol over its own state.

For example, a text wrapper has codec state and text-ordering semantics that make
blindly forwarding arbitrary seek behavior incorrect.

Protos therefore avoids a "whatever the child has, the wrapper has" rule.

Capability shape is part of the object contract.

## Operations that may wait return ordinary Futures

Byte and text I/O operations that may wait use the ordinary Future abstraction.

For example:

```protos
future: source.read(4096)
chunk: future.value()
```

The operation invocation itself does not introduce hidden Protos suspension.

Suspension occurs at the ordinary Future observation boundary when a pending
Future is awaited.

This means I/O composes with the same task ownership, cancellation, Actor
termination, and Error observation rules explained in
[chapter 08](08-futures-and-structured-concurrency.md).

I/O does not create a parallel async universe.

## Invalid I/O arguments still use the Future result channel

After a standardized Future-returning I/O operation has been successfully
dispatched, semantic I/O argument errors use a failed returned Future.

For example, an invalid byte-read maximum or an invalid write payload does not
invent a second synchronous exception path for the standard asynchronous
operation.

Conceptually:

```text
valid dispatch
    |
    +-- invalid I/O argument known now -> already-failed Future
    |
    +-- operation may proceed          -> pending/resolved/failed Future
```

This keeps one result channel after dispatch.

Ordinary language failures that prevent dispatch itself still follow ordinary
language semantics.

## `COMMITTED` is an operation property, not a Future state

The standard Future states remain:

```text
pending
resolved
failed
cancelled
```

I/O additionally needs to reason about whether an operation has crossed an
irreversible semantic effect boundary.

Conceptually:

```text
PENDING
  |
  +--> CANCELLED
  |
  +--> COMMITTED --> RESOLVED
  |
  +--> COMMITTED --> FAILED
```

`COMMITTED` is not a fifth Future state.

It answers a different question:

> Can cancellation still make the operation observably as though its irreversible
> effect never happened?

Once the answer becomes no, cancellation cannot rewrite that history.

## Actor termination is a cancellation request, not rollback

When an Actor terminates, pending Actor-originated asynchronous I/O receives the
same strength of cancellation request as ordinary `Future.cancel()`.

That request wins only while the particular I/O operation can still satisfy its
pre-commit cancellation contract.

Actor termination does not:

```text
roll back already committed output
implicitly flush a stream
implicitly sync a file
implicitly close shared Process streams
undo an accepted resource effect
```

Residual backend work needed to finish an already committed effect may continue
under runtime custody without executing ordinary Protos code in the dead Actor.

## Reads return data, EOF, or failure distinctly

The standard byte-readable operation is:

```protos
future: source.read(maxBytes)
```

`maxBytes` must be an Integer greater than zero.

A successful non-EOF result is a fresh open `Bytes` with between 1 and
`maxBytes` octets.

`null` means EOF for the receiver's current sequence state.

An empty `Bytes()` result is not used to mean "no data yet" and is not used to
mean EOF.

If no data is currently returnable and EOF is not established, the Future
remains pending.

A read may return fewer bytes than requested.

## Read cancellation preserves the logical input sequence

If cancellation wins before a read commits, that read consumes no observable
input bytes.

Likewise, an ordinary failed ByteReadable read consumes zero observable bytes.

An implementation that read ahead from a host source may need to buffer,
reposition, or otherwise reconcile those bytes so the later Protos-visible input
sequence remains correct.

This is why a native syscall having happened is not by itself the Protos
commitment rule.

The portable sequence is the authority.

## Writes snapshot mutable Bytes at invocation

The standard byte-writable operation is:

```protos
future: target.write(bytes)
```

For admitted output work, the logical size and octets of the supplied `Bytes` are
captured during invocation.

Later caller mutation does not rewrite the already-admitted output.

Conceptually:

```text
mutable caller Bytes
      |
      | invocation-time logical snapshot
      v
admitted write payload
```

This does not require eager copying.

Copy-on-write or other representations are allowed when caller mutation remains
ordinary and cannot alter the captured write.

## Output backpressure must remain bounded

The snapshot rule does not authorize an unlimited output queue.

A logical output flow has finite effective capacity for admitted retained write
work.

If the implementation cannot reserve bounded state needed to preserve another
write snapshot, that write may fail before admission with the standard capacity
failure outcome.

The runtime must not silently accumulate unbounded pending payloads or block the
Protos caller inside `write()` waiting for memory.

Backpressure remains an end-to-end semantic concern across wrappers and routing.

## Successful writes do not imply flush or durability

A successful `write(bytes)` means the complete logical byte sequence has been
accepted by that receiver according to its write semantics.

It does **not** automatically mean:

```text
all buffering flushed
data made durable
remote peer consumed it
terminal rendered it
storage synchronized
```

Those are separate capabilities and operations.

Semantic distinctions such as `write`, `flush`, and `sync` remain visible instead
of being collapsed into one vague notion of "done".

## One logical flow has one ordering domain

Operations against one logical byte/text flow have the ordering guarantees of
that flow.

Multiple Actor-local wrappers may denote one shared logical standard stream.

Physical wrapper identity is not what defines the ordering domain.

Conversely, two objects that happen to reach the same host device are not
automatically one Protos logical flow unless their capability semantics say so.

For writes in one defined order, successful logical write byte sequences do not
interleave with each other.

The implementation may still use partial native writes, batching, or buffering
internally.

## `close()` is an irreversible lifecycle cutover

For a standard `Closable` receiver:

```protos
future: receiver.close()
```

close begins the receiver's permanent closing lifecycle before returning its
Future.

That cutover is itself irreversible.

Therefore cancelling the close Future does not roll back the close lifecycle.

Operations ordered before or after that cutover are classified by the standard
lifecycle/commitment rules.

A previously accepted operation that had already committed keeps its terminal
aftermath.

A previously accepted but still uncommitted operation may be
closure-terminated with `IOLifecycleError`.

A later operation requiring the resource to remain open is rejected.

## Repeated lifecycle calls use fresh Future identities

A standardized idempotent Future-returning lifecycle operation does not need one
canonical Future object.

Each successful dispatch creates a fresh standard Future identity.

Those Futures can still observe one shared underlying lifecycle outcome.

For example, repeated close observations do not mean repeated physical close
attempts simply because the Future objects are distinct.

Future identity and resource-lifecycle identity remain different concepts.

## Path is an immutable structural value

`Path` represents path structure.

It is not Filesystem authority and it does not consult a host filesystem when
constructed.

A Path is an ordered sequence of normal component Strings. The empty Path
denotes the interpreting Filesystem's configured base, and every Path is
interpreted downward from that base. There is no rooted Path and no parent
component.

The portable construction protocol is:

```protos
Path.relative()
path.child(name)
```

`child(name)` appends exactly one normal component; the empty String, `"."` and
`".."` are rejected.

The String supplied to `child` is not parsed as a mini host path.

Characters such as:

```text
/
\
:
```

inside that one component do not magically become host separators, drive
syntax, or UNC structure.

There is no implicit String-to-Path coercion.

Runnable companion:

- [`../../protos/tutorials/06-path-values/01-structure-and-identity.protos`](../../protos/tutorials/06-path-values/01-structure-and-identity.protos)

## Path equality is structural; Path identity is ordinary identity

Two Path values can compare equal because their ordered component String
sequences are equal.

That does not make them the same semantic object identity.

So keep these questions separate:

```text
Do these Paths describe the same structural path value?
Are these the same Path object identity?
Does this Filesystem capability authorize that path?
What resource does this open operation select now?
```

They are different semantic questions.

This also means a Path hash is filesystem-independent; it is not a lookup into a
live namespace.

## Filesystem is namespace authority

A `Filesystem` capability defines authority over one filesystem namespace view.

It is not a global `FileSystem` singleton and it is not recovered from a Path.

The same structural Path can produce different results under different
Filesystem capabilities because the authority and namespace views are different.

Conversely, having a broad-looking Path value does not widen a confined
Filesystem capability.

The backend must fail rather than escape or ambiguously exceed the authority
represented by that capability.

## Opening produces a stable File capability

The fundamental open operation is:

```protos
fileFuture: filesystem.open(path)
file: fileFuture.value()
```

or:

```text
filesystem.open(path, options) -> Future<File>
```

The default one-argument form means:

```text
read access
existing target
preserve content
positioned placement
```

A successful open selects one authorized resource and returns a `File` capability
bound to that selected resource.

Later namespace changes do not retarget the already-open File merely because the
Path spelling now refers to something else.

This is a crucial distinction:

```text
Path -> namespace selection at open time -> stable File resource capability
```

After open, File operations act on the selected resource, not by repeatedly
resolving the original Path.

## Open options are explicit dimensions

Core does not define its file-open semantics in terms of opaque host mode
strings such as:

```text
"r"
"w"
"a"
"r+"
```

Instead, open configuration separates:

```text
read/write access
existing/create/createNew
preserve/truncate
```

The public options object uses the standard local Boolean slots:

```text
read
write
create
createNew
truncate
```

Only the captured standard local slots participate. There is no append option or
append mode; any other local slot, including `append`, makes the options invalid.

The configuration is snapshotted at invocation so later mutation of the options
object cannot change an already invoked open.

Invalid combinations fail before target namespace/resource effects.

## Open operations are independent acquisitions

Two outstanding opens do not become ordered merely because:

```text
they use the same Filesystem
they use equal Paths
the same Actor invoked them in source order
```

If the program requires one open's result/effect before invoking another, it
creates that dependency explicitly by awaiting or otherwise sequencing the first
operation.

This prevents a Filesystem capability from becoming an unnecessary global
namespace-operation queue.

Each individual open still obeys its own race-safe selection, confinement,
commitment, and stable-resource-binding contract.

## File capability shape is exact

A `File` is an already-open resource.

The methods present on that File depend on the capability descriptor acquired at
open.

A File may expose, as applicable:

```text
read
write
position
seek
seekBy
seekToEnd
size
truncate
sync
close
```

`close` is part of the standard File lifecycle.

Other operations are present only when that concrete File capability supports
their complete contracts.

A File does not gain seek/truncate/sync merely because the host resource could
possibly perform some approximate operation.

The language-visible capability shape must be truthful.

## Writes are positioned; there is no append mode

A writable File writes relative to that File's logical position.

A File that exposes `ByteSeekable` lets a program compose:

```text
seekToEnd()
write(bytes)
```

That is two ordinary operations on one File, not an append mode. The write
starts at the File's logical position; it does not re-select the end at write
time. The composition is not atomic and gives no placement, non-overlap, or
non-interleaving guarantee against writes through other File capabilities,
aliases, or external writers selecting the same resource.

## File position is logical, not a leaked native cursor

For positioned Files, Protos exposes one logical sequence position for the File
capability.

The runtime may use host offsets, positional I/O, buffering, or another backend
strategy, but native cursor accidents must not leak into the portable contract.

Operations such as:

```text
position
seek
seekBy
seekToEnd
read
write
```

compose through that logical position.

An implementation can optimize representation as long as the same Protos
sequence is observed.

## `sync()` is stronger than `write()`

A File that exposes `sync` provides a distinct durability-oriented capability.

`write()` success alone does not imply durable persistence.

`sync()` exists precisely because contribution to the File and synchronization
to the resource's durability boundary are distinct operations.

Likewise, closing a resource should not be mentally substituted for an explicit
durability contract unless the concrete normative operation says so.

## Namespace mutation is separate from File I/O

The currently implemented Filesystem namespace mutation operations are:

```protos
filesystem.replace(sourcePath, targetPath)
filesystem.remove(path)
```

Both return Futures resolving according to the Filesystem operation contract.

`replace` changes namespace visibility atomically at its semantic effect/commit
cutover.

`remove` is a namespace-entry operation and is non-recursive under the current
Core contract.

These operations do not retroactively retarget already-open File capabilities.

This lets programs reason independently about:

```text
open resource lifetime
namespace naming/publication
file content and cursor
durability
```

rather than collapsing them into a single host-path abstraction.

## Failed publication does not imply compensating rollback

Filesystem and file operations use explicit commitment semantics.

If an operation commits an irreversible namespace/content effect and later
fails for another reason, Core does not generally invent compensating rollback
that restores prior external state.

Programs that need transactional workflows build them with the appropriate
higher-level protocol.

The runtime must preserve the standard operation's atomicity/commitment boundary;
it must not fabricate a stronger transaction model than the capability promises.

## File and Filesystem are live authority, not ordinary transferable data

Open `File` and `Filesystem` capabilities carry live authority.

The current Core reference implementation rejects their ordinary direct Actor
transfer with the standard non-transferable capability behavior and gives them no
P-transfer contract.

The runtime does not silently make such transfer succeed by:

```text
reopening the same Path
duplicating an OS handle
inventing a proxy
granting the destination a new Filesystem
```

A separately designed safe proxy/delegation facility could define its own
contract in the future, but it would be a new explicitly provisioned capability,
not transparent transfer of the original live resource.

Path and Encoding values do not carry that live resource authority.

## Process standard streams are authority too

`process.stdin()`, `process.stdout()`, and `process.stderr()` are not ordinary
byte arrays.

They are Process-local stream capabilities.

A Process capability delegated to another Actor lets that Actor acquire its own
standard stream view according to the Process contract, preserving the shared
logical Process flow without exposing a source Actor's physical wrapper.

This is a capability-specific rule.

It does not generalize into "all streams are transferable".

## Resource lifetime should be explicit

Whenever code acquires a closable resource, decide who owns closing it.

For explicit low-level code, the pattern is conceptually:

```protos
file: filesystem.open(path).value()

(() => {
    // use file
}).ensure() {
    file.close().value()
}
```

The exact body can use the Error/cleanup mechanics explained in
[chapter 07](07-errors-handlers-ensure-and-resource-lifetime.md).

When a TextReader/TextWriter should own the underlying File, the owning wrapper
form can encapsulate that release responsibility.

What should be avoided is accidental ownership:

```text
two independent components both assume the other closes
a borrowed wrapper unexpectedly closes a shared target
a live File is dropped with no clear custodian
cleanup depends on normal-return-only code
```

Authority and lifetime should have one understandable owner.

## A Process stream wrapper is normally borrowing

For Process standard streams, code usually does not own the Process's stream
lifetime merely because it wants text conversion.

For example:

```protos
writer: TextWriter(process.stdout(), process.stdoutEncoding())
writer.writeLine("hello").value()
writer.close().value()
```

uses a borrowing TextWriter.

Closing that wrapper finalizes the wrapper's codec/output lifecycle but does not
mean the application owns and closes the Process standard-output capability.

This is one reason the borrowing/owning distinction is explicit.

## The current repository already uses these boundaries

Bundled Protos tools use the same ordinary capabilities rather than a
tool-specific I/O universe.

For example:

- [`../../protos/tools/package/ManifestCommand.protos`](../../protos/tools/package/ManifestCommand.protos)
  opens `protos.toml` through an explicitly supplied confined Filesystem and
  wraps the resulting File in an owning UTF-8 TextReader;
- [`../../protos/tools/package/MetadataPublication.protos`](../../protos/tools/package/MetadataPublication.protos)
  uses explicit open/write/close plus Filesystem replacement for metadata
  publication;
- [`../../protos/tools/test/Main.protos`](../../protos/tools/test/Main.protos)
  constructs a TextWriter from `process.stdout()` and
  `process.stdoutEncoding()`.

These are implementation/tool consumers of the standard capabilities, not
normative definitions of them.

They are useful evidence that the authority model is practical without adding an
ambient host-I/O escape hatch.

## The Path tutorial is already executable

The existing learning material includes:

- [`../../protos/tutorials/06-path-values/01-structure-and-identity.protos`](../../protos/tutorials/06-path-values/01-structure-and-identity.protos)

which demonstrates the authority-free structural Path model.

The repository does not currently have a dedicated progressive `12-io` tutorial
series. This chapter therefore uses the already-published normative/current
runtime surfaces and links real Protos tool consumers without creating executable
material inside a documentation-only slice.

## Current implementation boundary: D046 entries, D171 host-provisioned capture

D046 introduced general directory observation and a captured-tree capability,
implemented by `I024`. D171 later removed the public `captureTree` selector
(implemented by `I064`). The general guest surface is now:

```text
filesystem.entries(path) -> Future<Array>
```

There is no guest operation that captures, snapshots, clones or freezes an
arbitrary subtree into a new Filesystem. Stable captured trees are provisioned
only by the host/runtime: for example, Package Tool content custody captures a
selected package root once through secure no-follow traversal into immutable
backing and hands guest code an ordinary read-only Filesystem over that capture.
Guest code then verifies and uses that same Filesystem through `entries` and
`open`; it never sees a source Path, backing location or capture selector.

A captured Filesystem remains an ordinary read-only Filesystem capability with no
standard `close()` obligation. Files opened from it retain their normal File
lifecycle. Hosts/backends that cannot provide `entries` still fail it through the
ordinary Future/`IOError` boundary rather than weakening confinement.

## Current implementation evidence

The reference implementation ledger records the core surfaces used by this
chapter as closed:

- `I013 — Standard Path`;
- `I014 — Standard Byte I/O`;
- `I015 — Encoding / Text I/O`;
- `I016 — Filesystem / File`;
- `I017 — Process I/O / bootstrap`;
- `I021 — Filesystem namespace replacement/removal`.

The durable implementation and closure record is:

- [`Implementation Status`](https://github.com/guillermomolina/protos-project-docs/blob/main/docs/project/registries/IMPLEMENTATION_STATUS.md).

The implementation itself reflects the capability model:

- [`ProtosStandardProcessProtocol.java`](../../src/main/java/com/guillermomolina/protos/execution/ProtosStandardProcessProtocol.java)
  exposes exactly the eight Process bootstrap accessors and performs no host
  discovery;
- [`ProtosStandardFilesystemProtocol.java`](../../src/main/java/com/guillermomolina/protos/execution/ProtosStandardFilesystemProtocol.java)
  constructs explicitly provisioned Filesystem capabilities with current
  `open`/`replace`/`remove`/`entries`;
- [`ProtosStandardFileProtocol.java`](../../src/main/java/com/guillermomolina/protos/execution/ProtosStandardFileProtocol.java)
  materializes only the File operations promised by its acquired capability
  descriptor.

These files are reference implementation evidence. The specification remains the
authority.

## Practical authority flow

A useful conceptual flow is:

```text
host / embedder
      |
      +--> initial module `process`
      |       |
      |       +--> args/environment snapshots
      |       +--> optional standard byte streams
      |       +--> explicit standard-stream Encodings
      |
      +--> optional initial `filesystem`
              |
              +--> Path + open configuration
              |
              v
            File capability
              |
              +--> byte operations
              +--> optional seek/size/truncate/sync
              +--> close
              |
              +--> optional explicit TextReader/TextWriter wrapper
```

No arrow is implied merely because two boxes are nearby.

In particular:

```text
Path                  -/-> Filesystem authority
Process               -/-> Filesystem recovery
import(module)        -/-> Process/Filesystem inheritance
TextReader wrapper    -/-> arbitrary source capabilities
Actor boundary        -/-> automatic live-resource transfer
P boundary            -/-> ambient I/O authority
write success         -/-> flush/durability
File Path spelling    -/-> later File retargeting
```

Those non-arrows are as important as the positive APIs.

## Choose the narrowest capability you can

If a function only needs to decode bytes, pass:

```text
Bytes + Encoding
```

rather than Process.

If it only needs to read text from one source, pass a TextReader or the specific
source/Encoding needed to construct one.

If it only needs one already-open File, pass that capability inside the same
authority domain rather than a Filesystem that can open more names.

If it needs namespace access, pass a confined Filesystem rather than relying on a
current directory.

If a module only needs argument data, consider passing the already acquired
immutable argument values rather than the whole Process capability.

The smallest sufficient capability reduces accidental authority and makes tests
easier to construct.

## Keep host policy at the boundary

Portable Protos code should not infer semantics from:

```text
POSIX file descriptors
Windows HANDLEs
native path separators
native current directory
locale-default encodings
host newline conventions
errno values
JVM stream classes
native file cursor behavior
```

Those facts may matter to an implementation or embedding host.

They become Protos behavior only through an explicit normative capability
contract.

This preserves portability without forcing Protos to pretend every host is the
same.

## Practical rules to remember

1. A Protos Process is an execution/bootstrap domain, not a portable
   operating-system process abstraction.
2. The RootActor initial module receives `process` as a local bootstrap
   capability; imports do not receive it automatically.
3. `filesystem` is a separate optional bootstrap capability and cannot be
   recovered from Process.
4. The current standalone application CLI does not grant a default Filesystem
   merely because the launcher can read source.
5. Process capability delegation to another Actor is explicit; Process has no
   Core P-transfer contract.
6. `process.args()` returns a frozen ordinary Array with stable content; the
   identity of separate results is not portable.
7. `process.environment()` returns a read-only Environment with stable content,
   not specified as Map; the identity of separate results is not portable.
8. stdin/stdout/stderr are independently optional byte capabilities.
9. Standard-stream Encoding associations are explicit; byte streams are never
   implicitly text.
10. Encoding descriptors carry conversion configuration, not I/O authority.
11. `TextReader`/`TextWriter` construction is explicit and creates fresh
    per-flow codec state.
12. Borrowing and owning wrappers differ in resource-lifetime responsibility,
    not authority.
13. There is no universal Core Stream prototype; concrete capability shape is
    authoritative.
14. I/O operations that may wait return ordinary Futures and introduce no hidden
    suspension.
15. After successful dispatch, invalid semantic I/O arguments use the operation's
    failed Future result channel.
16. `COMMITTED` is an I/O operation property, not a fifth Future state.
17. Actor termination requests cancellation of pending Actor-originated I/O but
    does not roll back committed effects or implicitly flush/sync/close shared
    resources.
18. Byte reads return fresh non-empty Bytes, `null` EOF, or failure; empty Bytes
    is not a no-data/EOF sentinel.
19. Pre-commit read cancellation/failure preserves the logical input sequence and
    consumes zero observable bytes.
20. Admitted byte writes snapshot the supplied mutable Bytes during invocation.
21. Output retention/backpressure is bounded; write snapshotting is not permission
    for an unbounded queue.
22. Successful `write` does not imply flush, sync, durability, peer receipt, or
    terminal presentation.
23. `close()` establishes an irreversible lifecycle cutover; cancelling its Future
    does not reopen the resource.
24. Repeated idempotent lifecycle calls produce fresh Futures that may observe one
    shared lifecycle outcome.
25. `Path` is an immutable authority-free ordered sequence of normal component
    Strings, built with `relative` and `child`.
26. `child(name)` appends one component and does not parse host path syntax.
27. Path has no rooted form and no parent component; reflection `parent()`
    keeps its object-model meaning.
28. Path equality is structural while semantic object identity remains distinct.
29. Filesystem carries namespace authority; Path does not.
30. `filesystem.open(path)` defaults to read/existing/preserve/positioned and
    returns a Future for a stable selected File capability.
31. Open options are snapshotted explicit dimensions, not opaque host mode
    strings.
32. Independent opens are not implicitly serialized merely by same Filesystem,
    Path, or Actor source order.
33. A File exposes only the read/write/seek/size/truncate/sync capabilities its
    acquired descriptor actually promises, plus its standard close lifecycle.
34. File writes are positioned; Core defines no append open option or mode.
35. File position is a logical Protos property, not leaked native cursor state.
36. `sync()` is distinct from ordinary `write()` completion.
37. Current Filesystem namespace mutation uses explicit `replace` and non-recursive
    `remove`; neither retargets already-open Files.
38. Live File/Filesystem authority is not ordinary Actor/P-transferable data and
    Core never silently reopens, duplicates, or auto-proxies it.
39. Resource lifetime should have one explicit owner; use `ensure` or owning
    wrappers where appropriate.
40. `Filesystem.entries` is the only standard tree-observation operation; D171
    removed the public `captureTree` selector, and stable captured trees are
    provisioned only by the host/runtime as ordinary read-only Filesystems.

## Normative references

For exact behavior, consult:

- [`../../spec/io/PROCESS_IO.md`](../../spec/io/PROCESS_IO.md) for Process
  bootstrap, args/environment, standard streams, standard-stream Encoding
  associations, Process Actor delegation, and Process termination boundaries;
- [`../../spec/io/IO_CORE.md`](../../spec/io/IO_CORE.md) for capability shape,
  Future-returning I/O, commitment/cancellation, close/lifecycle, ownership, and
  cross-cutting I/O rules;
- [`../../spec/io/BYTE_IO.md`](../../spec/io/BYTE_IO.md) for ByteReadable,
  ByteWritable, flush, seek, size, truncate, sync, and byte-flow behavior;
- [`../../spec/io/TEXT_IO.md`](../../spec/io/TEXT_IO.md) for Encoding,
  TextReader, line framing, TextWriter, and codec/lifecycle composition;
- [`../../spec/io/FILESYSTEM.md`](../../spec/io/FILESYSTEM.md) for Path,
  Filesystem authority, File opening, File behavior, namespace operations, and
  D046 tree-observation semantics;
- [`../../spec/concurrency/FUTURES_AND_TASKS.md`](../../spec/concurrency/FUTURES_AND_TASKS.md)
  for the Future/cancellation/structured-ownership substrate reused by I/O;
- [`../../spec/concurrency/ACTORS.md`](../../spec/concurrency/ACTORS.md) for Actor
  isolation and live-resource transfer boundaries;
- [`../../spec/concurrency/PARALLEL_EXECUTION.md`](../../spec/concurrency/PARALLEL_EXECUTION.md)
  for the absence of ambient standard I/O authority inside P;
- [chapter 07](07-errors-handlers-ensure-and-resource-lifetime.md) for cleanup
  and explicit resource lifetime;
- [chapter 08](08-futures-and-structured-concurrency.md) for asynchronous result
  observation and structured ownership;
- [chapter 10](10-actors-actorrefs-and-groups.md) for Actor capability transfer
  and isolation.

Those normative documents define Protos. This chapter supplies the
programmer-facing model for keeping Process authority, filesystem authority,
resource lifetime, data conversion, and asynchronous effects explicit rather
than smuggling host globals into ordinary code.

## Runnable system/resource learning material

The shipped learning material keeps the authority boundary visible rather than
making examples work by granting hidden ambient access.

- [`../../protos/examples/io/encoding-and-bytes.protos`](../../protos/examples/io/encoding-and-bytes.protos)
  is a standalone-CLI cookbook example for the authority-free
  `Encoding`/mutable-`Bytes` boundary.
- [`../../protos/tutorials/12-system-resources/01-process-stream-output.protos`](../../protos/tutorials/12-system-resources/01-process-stream-output.protos)
  runs through the ordinary standalone CLI. It obtains text output only from the
  bootstrap-local `process` capability and its Process-selected Encoding.
- [`../../protos/tutorials/12-system-resources/02-filesystem-text-roundtrip.protos`](../../protos/tutorials/12-system-resources/02-filesystem-text-roundtrip.protos)
  requires a host that explicitly provisions bootstrap-local `filesystem`
  authority. The LM006 system/resource integration harness executes that source
  with deterministic confined test storage.

The ordinary standalone application CLI therefore does **not** run the Filesystem
lesson merely because it can read the lesson's source file. Source discovery and
filesystem authority remain separate concerns.
