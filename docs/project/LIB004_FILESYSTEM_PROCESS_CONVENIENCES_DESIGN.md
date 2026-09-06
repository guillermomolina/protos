# LIB004 Filesystem / Process Conveniences Design Record

Status: **DRAFT — design audit in progress; no implementation surface approved**
Work item: `LIB004`
Nature: Project design record; **non-normative**
Draft checkpoint: 2026-09-06
Historical audit checkpoint: `b2bdbe584531284ec499df1a40be6285bea7c50f`

## Purpose

This document preserves the ongoing design investigation for
`LIB004 — Filesystem / process conveniences` before the design is closed.

It exists so a later session can continue from the researched design state rather
than reconstructing decisions from chat history. It is intentionally a draft:
statements under **working recommendation**, **candidate**, **deferred**, or
**open question** are not approved API and must not be implemented merely
because they are recorded here.

The normative Protos specification under `spec/` remains authoritative. This
record does not add or redefine Path, File, Filesystem, Process, Future, Actor,
parallel-execution, Error, cleanup, cancellation, transfer, or module semantics.

The historical SHA above records the repository state reached during this design
audit. It is evidence only. Any later implementation must re-fetch and re-audit
the then-current `origin/main` under `AGENTS.md`.

## Current design objective

The intended LIB004 domain is higher-level ordinary-library convenience over the
already standardized capability-based filesystem and Process I/O surfaces.

The design must preserve all of these properties:

- filesystem authority remains explicit and capability-confined;
- a Path remains structural data and never becomes authority merely by naming a
  resource;
- Process authority remains explicit and Process does not imply Filesystem
  authority;
- imported modules do not obtain bootstrap-local filesystem or process
  capabilities implicitly;
- I/O operations that may wait remain Future-shaped and suspension remains
  explicit to the caller;
- resource ownership and close responsibility remain visible and deterministic;
- cancellation must not create orphaned resources or silently weaken commitment
  guarantees;
- Actor/P/Process boundaries must not gain ambient authority through a
  convenience module;
- physical OS process, thread, machine, JVM, container, and transport placement
  remain implementation facts rather than assumptions made by the library;
- ordinary Protos mechanisms are preferred over new native or Java-only
  convenience primitives.

## Audit material reviewed so far

This draft records an audit that materially inspected the applicable project
rules, project records, normative owners, implementation, and existing library
precedent.

### Project and design records

- `AGENTS.md`
- `spec/AGENTS.md`
- `docs/README.md`
- `docs/design/PROTOS_DESIGN_PHILOSOPHY.md`
- `docs/design/STANDARD_LIBRARY_IDEAS.md`
- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/IMPLEMENTATION_BLOCKERS.md`
- `docs/project/CORE_NATIVE_BOUNDARY.md`
- `docs/project/LIB001_COLLECTIONS_DESIGN.md`
- `docs/project/LIB002_TEXT_ENCODING_DESIGN.md`
- `docs/project/LIB003_JSON_DESIGN.md`
- package-tool filesystem/staging design and implementation records relevant to
  explicit metadata publication.

### Normative owners materially relevant to LIB004

- `spec/semantics/EXECUTION_AND_CONTROL.md`
- `spec/semantics/ERRORS.md`
- `spec/semantics/MODULES.md`
- `spec/io/IO_CORE.md`
- `spec/io/BYTE_IO.md`
- `spec/io/TEXT_IO.md`
- `spec/io/FILESYSTEM.md`
- `spec/io/PROCESS_IO.md`
- `spec/concurrency/FUTURES_AND_TASKS.md`
- `spec/concurrency/ACTORS.md`
- `spec/concurrency/PARALLEL_EXECUTION.md`
- `spec/concurrency/DISTRIBUTED_RUNTIME.md`

### Implementation boundaries materially inspected

- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardFilesystemProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardFileProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardProcessProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardProcessStreamProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardFutureProtocol.java`
- `src/main/java/com/guillermomolina/protos/execution/ProtosStandardErrorProtocol.java`
- `src/main/java/com/guillermomolina/protos/runtime/ProtosFilesystemOpenFlow.java`
- `src/main/java/com/guillermomolina/protos/runtime/ProtosIoOperation.java`
- `src/main/java/com/guillermomolina/protos/runtime/ProtosIoLifecycle.java`
- `src/main/java/com/guillermomolina/protos/runtime/ProtosFutureValue.java`
- `src/main/java/com/guillermomolina/protos/runtime/ProtosTask.java`
- `src/main/java/com/guillermomolina/protos/runtime/ProtosActivation.java`
- ordinary Protos Standard Library modules under `protos/lib/`;
- package-tool `MetadataPublication.protos` staging/publish workflow;
- existing Protos-language conformance around Filesystem, Process, Future,
  TextReader/TextWriter and namespace mutation.

This list is a checkpoint, not permission for a later implementation agent to
skip the mandatory fresh audit.

## Existing Core boundary that LIB004 must preserve

### Filesystem is authority; Path is not

A `Filesystem` value is an explicit authority boundary. Core does not provide a
public ambient constructor or a process-global/current filesystem.

A `Path` is a non-authoritative immutable structural value. Possessing a Path
does not grant permission to open, mutate, inspect, or enumerate anything.

Therefore this draft rejects convenience directions such as:

```text
Path.open(...)
File.open(...)
Filesystem.current()
Process.filesystem()
std:fs.current()
```

unless a future normative design explicitly introduces a new authority model.
LIB004 must not do so.

### Process bootstrap authority is local and explicit

A RootActor bootstrap/module context may be provisioned with a local `process`
binding and, independently, an optional local `filesystem` binding. Imported
modules do not automatically inherit either authority.

Process therefore does not imply Filesystem and a library module cannot recover
ambient authority merely because its caller runs inside a Process.

### Current Filesystem namespace surface is intentionally narrow

The language-visible Filesystem operations relevant to this design are:

```text
filesystem.open(path [, options]) -> Future<File>
filesystem.replace(sourcePath, targetPath) -> Future<Filesystem>
filesystem.remove(path) -> Future<Filesystem>
```

The current Core model deliberately does not standardize a broad host-filesystem
API containing all of:

- existence/stat queries;
- directory creation or recursive creation;
- directory enumeration;
- recursive removal;
- generic rename/move policy;
- symlink creation/inspection APIs;
- arbitrary host-path parsing;
- temporary-file/directory policy.

LIB004 cannot correctly manufacture those semantics from host APIs.

### `replace` is not generic `move`

`Filesystem.replace` is a specific failure-atomic namespace transition with
defined final-entry selection, cancellation/commitment behavior and live
visibility. It is not a byte copy and it does not promise crash durability.

A convenience named `move` would falsely suggest a broader policy surface
including cross-filesystem moves, fallback copy/delete, directory behavior or
host rename conventions. This draft therefore rejects `move` as an alias for
`replace`.

### `removeIfExists` and `exists` are not currently portable conveniences

Core intentionally maps several host causes into the portable `IOError` family.
The library cannot safely distinguish target absence from permission denial,
confinement rejection, backend failure, unsupported entry behavior or other I/O
failure merely by catching `IOError`.

Trying to implement `exists` by opening a path is also incorrect: it answers
whether an entry can be opened as the requested File capability, not whether a
namespace entry exists.

Therefore this draft treats the following as blocked by missing Core surface
rather than as LIB004 conveniences:

```text
exists
removeIfExists
mkdir
mkdirs
listDirectory
recursiveRemove
```

A future Core design may make some of these possible, but LIB004 must not invent
the missing distinctions.

### Open options are semantic values, not mode strings

Filesystem open options use explicit ordinary option slots such as:

```text
read
write
create
createNew
truncate
append
```

The option values are semantically snapshotted for the operation and invalid
combinations fail before backend namespace/I/O effects attributable to that
invalid request.

A Standard Library convenience may create fresh ordinary option objects, but it
should not introduce `"r"`, `"w"`, `"a"` or other compact mode strings that form
a second policy language.

### File capability shape remains capability-honest

A File exposes the operations the backend can guarantee. Read/write,
position/seek/size/truncate/sync capability is not to be invented by a wrapper
that happens to hold a File.

`close()` remains the resource-lifetime operation. Raw File does not gain
Flushable behavior merely because a higher-level writer adapter has flush
semantics.

## Process and multiprocess/distributed considerations

### Protos Process is not an operating-system process

This distinction is central to LIB004.

A Protos Process is a logical execution/isolation/failure domain. The normative
distributed-runtime model does not require one Protos Process to correspond to
one OS process, address space, JVM, container, VM, machine or Kubernetes node.

A runtime may host several Protos Processes physically together or place them on
different machines. Physical locality may permit shared-memory or local-IPC
optimizations, while remote placement may require network transport; observable
Protos semantics must not change merely because of that placement.

Consequently a library API must not assume:

```text
Process == PID
Process == java.lang.Process
Process == one JVM
Process == one host
Process == one container
Process == one Node
```

### Standard streams belong to the logical Process contract

`process.stdin()`, `process.stdout()` and `process.stderr()` are synchronous
capability lookups for the Process-local standard byte flows, with associated
stable Encoding descriptors obtained through the corresponding
`*Encoding()` accessors.

Repeated accessor results/proxies refer to the same logical Process stream flow;
stdout and stderr remain separate flows.

The normative Process I/O model explicitly permits convenient text adapters to
be built over these byte streams. Any LIB004 adapter must use the Process-provided
Encoding rather than assume UTF-8 or another ambient default.

### Process authority does not cross boundaries accidentally

Live resource capabilities are not made transferable merely because another
Actor or Process wants equivalent access. Explicit provisioning/proxy protocols
own any such boundary.

Likewise, isolated parallel execution `P` has no contract granting ambient
Process/File/Filesystem I/O authority. A convenience must not create one.

### Future OS-process/subprocess support is a separate capability problem

Common languages often place child-process spawning next to their general
process APIs. Protos should not copy that association merely by name.

A future external-process facility would need to define at least:

- explicit spawn/exec authority;
- executable/image selection;
- argument and environment capture;
- working-directory/filesystem authority;
- stdin/stdout/stderr provisioning;
- lifecycle and termination authority;
- exit observation;
- cancellation and commitment;
- host failure mapping;
- Actor/P/Process transfer rules;
- physical-locality assumptions, if any.

That is a new capability/runtime boundary. This draft keeps shell/subprocess/OS
process control outside LIB004 and does not overload the existing logical
`Process` abstraction.

## Comparative filesystem/process API review

Prior art is evidence, not authority. The comparison below records the lessons
that survived translation into the Protos capability model.

### C / POSIX

POSIX exposes path strings against process-global filesystem state and
process-wide concepts such as current directory, file descriptors and `errno`.
This is compact and powerful but couples ordinary operations to ambient process
authority.

Useful lesson:

- keep low-level operations precise and explicit about open flags and namespace
  effects.

Rejected for Protos:

- ambient current directory/filesystem authority as the universal library model;
- numeric/bitmask flags as the primary user-facing convenience surface;
- treating host `errno` taxonomy as portable Protos semantics.

### Java / NIO

Java separates `Path`, `Files`, channels/streams and `ProcessBuilder`.
`try-with-resources` provides strong lexical cleanup for synchronous
`AutoCloseable` resources.

Useful lessons:

- a path value and the operation that acts on it can remain separate;
- explicit open-option objects/enums are clearer than magic mode strings;
- resource scope should be structured.

Rejected or not directly transferable:

- the default host filesystem/provider is still effectively ambient;
- `AutoCloseable.close()` is fundamentally a synchronous cleanup shape and does
  not solve Protos asynchronous File acquisition/cancellation races;
- `java.lang.Process` is an OS-process handle and must not define Protos Process.

### Rust and capability-oriented Rust libraries

Rust separates `Path`/`PathBuf`, `File`, filesystem functions and
`std::process::Command`. RAII/`Drop` ties resource release to lexical ownership.
Capability-oriented libraries such as `cap-std` demonstrate the value of
directory/capability-relative filesystem access rather than ambient global path
authority.

Useful lessons:

- authority can be rooted in explicit capabilities;
- lexical ownership makes resource responsibility easier to reason about;
- path structure should not itself grant authority.

Not copied directly:

- Protos does not currently have affine/linear ownership or a borrow checker;
- traditional destructor cleanup is not a good fit for a cleanup operation that
  may itself return a Future and suspend;
- OS `Command`/child-process APIs are not the same concept as logical Protos
  Process.

### Go

Go separates `os`, `io`, `io/fs`, and `os/exec`; `defer` is widely used to close
resources.

Useful lessons:

- small interfaces can make read/write composition practical;
- cleanup should compose lexically.

Rejected for Protos:

- process-global environment/current-directory authority as an implicit default;
- adding another `defer` mechanism when Protos already has normative `ensure`;
- assuming an OS process model for Protos Process.

### Python

Python combines `pathlib`, `open`, context managers and `subprocess`.
`with`/`async with` show that acquire/use/release can be exposed as a protocol.

Useful lesson:

- resource lifetime can be a reusable higher-level protocol rather than custom
  cleanup code at every call site.

Rejected for Protos:

- ambient `open(path)`/current working directory;
- exception-suppression policy embedded into a general resource-exit protocol;
- conflating OS subprocesses with logical Process.

### C# / .NET

.NET distinguishes synchronous `IDisposable`/`using` from
`IAsyncDisposable`/`await using` and exposes OS processes separately through
`Process`.

Useful lesson:

- asynchronous cleanup is a real semantic concern and should not be disguised
  as a synchronous destructor.

Protos already has a different foundation:

- File close is Future-shaped;
- `ensure` cleanup may suspend;
- the language does not need separate `Dispose` and `DisposeAsync` categories
  merely for LIB004.

### JavaScript / Node.js

Node exposes filesystem promises/file handles, process-global state,
`child_process`, and modern ECMAScript explicit resource management includes
`using`, `await using` and disposable stacks.

Useful lessons:

- asynchronous disposal and LIFO resource stacks are useful patterns for future
  higher-level library design.

Rejected for initial LIB004:

- Node's ambient global `process`/filesystem assumptions;
- child-process spawning as an automatic extension of Protos Process;
- introducing a general disposable stack before one-resource scoped acquisition
  has demonstrated that the abstraction is needed.

### Haskell

`bracket acquire release use` and asynchronous-exception masking are the most
useful semantic precedent for the hard LIB004 resource problem.

Useful lesson:

- the critical invariant is not merely “run close eventually”; it is to protect
  the acquisition-to-release-registration window so that a successfully acquired
  resource always has a custodian.

Difference favorable to Protos:

- Protos cooperative cancellation is observed at defined boundaries rather than
  being injected at arbitrary ordinary instructions, reducing the need for a
  general masking facility in the common scoped-resource case.

### Kotlin

Kotlin's `use` is a useful ergonomic precedent for a library-level scoped
resource operation rather than new syntax.

Protos should not copy Kotlin/Java suppressed-error precedence automatically:
the normative `ensure` rules already define cleanup error precedence.

### C++ and Swift

C++ RAII and Swift `defer` reinforce lexical cleanup and reverse-order cleanup
composition. They do not by themselves solve Protos Future-shaped asynchronous
acquisition/close semantics.

### WASI and capability systems

WASI preopened capabilities and capability-oriented filesystem designs reinforce
the Protos direction that authority should be explicitly provisioned and
confined rather than recovered from global process state.

### Cross-platform filesystem pressure

Windows drive/UNC/device namespaces, reparse points and delete/share behavior,
POSIX pathname and rename traditions, filesystem-specific case sensitivity, hard
links, symlinks and atomic-operation support all argue against pretending that a
large host-filesystem API is one uniform portable semantic surface.

The existing narrow Core Filesystem capability is therefore a useful boundary,
not an inconvenience for LIB004 to bypass.

## Resource-management comparison

The resource-lifetime design was compared across several families:

| Model | Representative systems | Main lesson for Protos |
|---|---|---|
| RAII/destructor | C++, Rust | strong lexical lifetime, but ordinary destructors do not naturally model Future-shaped close |
| `finally`/`defer` | Java, Go, Swift | lexical cleanup is essential; Protos already has `ensure` |
| resource statement | Java `try` resources, C# `using` | useful ergonomics but no need for new syntax in LIB004 |
| context manager | Python `with` / `async with` | acquire/use/release can be a protocol |
| `use` helper | Kotlin | ordinary library API can express scoped use |
| bracket | Haskell | strongest precedent for acquisition custody and cancellation races |
| async disposal stack | modern ECMAScript | possible future multi-resource abstraction, premature for initial LIB004 |

The working direction is therefore:

```text
Haskell-style custody invariant
    +
Kotlin-like library ergonomics
    +
asynchronous close
    +
existing Protos ensure/cancellation semantics
```

without adding new language syntax or a new disposal object family merely for
LIB004.

## Future and cancellation constraints

### Waiting remains explicit

A synchronous-looking ordinary helper that repeatedly executes `.value()`
internally would hide suspension behind a normal call.

The preferred shape for a high-level operation that may wait is therefore:

```text
highLevelOperation(...) -> Future
```

The caller explicitly chooses when to wait with ordinary Future mechanisms.

### `then` does not own its source

Cancelling the destination returned by:

```text
source.then(transform)
```

does not generally cancel `source`. This is correct because the source may have
other observers/consumers.

`Future.all` likewise must not be treated as resource ownership.

Therefore resource custody cannot be implemented by casually composing
`open(...).then(...)` and assuming downstream cancellation owns the open.

### No hidden `detach`

A helper-created task should remain structured under the current Actor-local
task/turn unless the caller explicitly chooses a detached lifetime under the
existing Future contract.

LIB004 must not hide `.detach()` simply to make cleanup continue in the
background. That would weaken the very lifetime structure the convenience is
supposed to provide.

### No generic upstream-cancellation rewrite

Changing Future semantics so cancellation automatically propagates upstream from
every continuation would break valid shared-source cases. Ownership belongs to
the abstraction that explicitly acquires the resource, not to Future chaining in
general.

## Scoped acquisition: exact working state machine

The hard problem is who owns the result while `Filesystem.open()` itself is
still pending.

The current working state machine is:

```text
NOT_STARTED
     |
     | owner task starts
     v
ACQUIRING
     |  openFuture retained
     |
     +-- open fails/cancels --------------------------> TERMINAL
     |
     | open resolves File
     v
OWNED
     |
     v
USING
     |
     | normal / Error / non-local return / cancellation
     v
RELEASING
     | file.close().value()
     v
TERMINAL
```

### Cancellation before helper execution

If the helper's task is cancelled before its first ordinary instruction, no
`open()` occurs and there is no resource to clean up.

### Cancellation while open is uncommitted

The owner retains `openFuture` and explicitly requests its cancellation.

The current Filesystem open machinery has a producer-side commitment/custody
protocol. Pre-commit cancellation can win, and a backend result that arrives too
late is released through the open flow's producer-side
`releaseIfUntransferred` responsibility rather than escaping as an orphan File.

This is the desired result:

```text
helper cancelled
    -> open cancellation wins before commitment
    -> backend later acquires resource
    -> producer releases untransferred resource
    -> no File becomes ownerless
```

### Cancellation after open commitment but before File transfer

This is the decisive race.

Post-commit cancellation cannot retroactively erase a committed open outcome.
Therefore the owner cannot merely call:

```text
openFuture.cancel()
return
```

and forget the acquisition.

The strong custody rule requires the owning cleanup path to determine the final
open outcome:

```text
request openFuture cancellation

if final outcome is cancelled:
    no File exists for the library to close

if final outcome is failed:
    no File exists for the library to close

if final outcome is resolved(File):
    the owner must close that File
```

This prevents a File from becoming ownerless after the helper has already
accepted cancellation.

### Deliberate trade-off: cancellation cleanup may wait indefinitely

If open has crossed commitment and the backend never reaches a terminal result,
strong custody may force the cleanup path to remain suspended waiting to learn
whether a File exists.

The alternatives are worse:

- abandoning immediately can leak a post-commit File;
- a hidden detached guardian loses structured failure/lifetime reporting;
- generic upstream cancellation changes Future semantics for unrelated users;
- a filesystem-specific native `withOpen` would duplicate a problem the general
  cleanup model is intended to solve.

The working recommendation accepts potentially unbounded cancellation-cleanup
latency rather than weakening resource safety.

This is consistent with the general principle that deterministic cleanup can
delay final task/Actor termination when the cleanup operation itself waits.

### Once the File is owned

After `openFuture.value()` has successfully returned the File to ordinary code,
the acquisition race is over. The owner can protect use with `ensure` and close
on all relevant control exits.

The important cases are:

- normal completion -> close;
- Error unwind -> close;
- non-local return crossing the scope -> close;
- cooperative cancellation -> close;
- close itself may suspend;
- a cleanup Error follows the existing `ensure` precedence rather than inventing
  Java/Kotlin-style suppressed-error semantics.

### File close has a different commitment shape from open

The current File lifecycle commits close at close invocation. Cancelling a
Future that observes close completion does not undo the close lifecycle itself.

That makes:

```text
file.close().value()
```

a coherent cleanup action once File ownership is established.

## `ensure` / dynamic-handler implementation checkpoint

Normatively, the execution/control specification already states that unwind-safe
cleanup is available through an `ensure`-style protocol and explicitly notes
that higher-level resource protocols such as `use` or `withOpen` can be built
from ordinary messages and closures.

The Error specification likewise defines ordinary dynamic handler installation.

However, during this draft audit the current executable implementation inspected
at the historical checkpoint did not reveal an obvious complete public
installation of the normative `ensure` and dynamic-handler surface needed by a
pure-Protos resource-custody helper. For example, the inspected standard Error
protocol visibly installed `signal`, while the audited activation/runtime shape
did not obviously expose the complete normative handler-frame machinery.

This is recorded as an **implementation-audit finding requiring fresh
verification**, not as a new language-design gap and not as a normative blocker
declared by this draft.

Important consequences:

1. Do not implement a filesystem-specific native `withOpen` merely to bypass the
   general missing machinery.
2. Before a LIB004 slice acquires and owns resources, re-audit whether normative
   `ensure` and dynamic handlers are executable on current `main`.
3. If that general Core behavior is actually incomplete, reconcile it under the
   applicable Core/specification implementation owner before LIB004 relies on it.
4. Do not silently reopen or rewrite the normative cleanup model just to make
   LIB004 easier.
5. `I007` is currently recorded as CLOSED in the project ledger; this draft does
   not change that status because the implementation finding has not yet been
   exhaustively reconciled against every current implementation path and
   historical closure claim.

## Public `withOpen` remains deferred

A generic public API such as:

```text
withOpen(filesystem, path, options, body) -> Future
```

looks attractive but has an additional semantic trap.

Suppose `body(file)` returns a pending Future whose producer continues using the
File. Closing the File when the callback invocation returns is too early.
Automatically adopting the returned Future also requires a precise rule for
what counts as continuing scoped use, how cancellation propagates, and when the
resource scope ends.

A public callback API would therefore impose a subtle contract such as:

> The callback may explicitly suspend while it runs, but it must finish all
> File-dependent work before it returns; returning a Future that continues to
> use the File does not extend the File scope unless the API explicitly defines
> such adoption.

That contract is easy to misunderstand and has not yet earned its generality.

Working recommendation:

- keep scoped acquisition as an internal reusable implementation pattern first;
- use it in bounded operations whose library code controls the entire lifetime;
- defer a public generic `withOpen`/`Resource.use` API until Future-returning
  callbacks and scope extension are designed deliberately.

## Candidate convenience surfaces

Nothing in this section is approved API yet.

### Process text adapters — strongest early candidate

A small ordinary module may provide borrowing text adapters over explicitly
supplied Process authority:

```text
stdinReader(process)
stdoutWriter(process)
stderrWriter(process)
```

with semantics equivalent to:

```text
TextReader(process.stdin(), process.stdinEncoding())
TextWriter(process.stdout(), process.stdoutEncoding())
TextWriter(process.stderr(), process.stderrEncoding())
```

Working properties:

- explicit Process argument;
- no ambient/current Process lookup;
- host/Process-selected Encoding is preserved;
- borrowing, not owning, standard streams;
- each adapter construction can remain an ordinary fresh wrapper;
- no Java/native expansion.

Open point to revisit before closure:

Repeated fresh text wrappers over one logical standard byte flow can have
independent codec state. The Process specification permits convenient text
adapters, but the exact desired wrapper lifetime/freshness contract should be
closed explicitly rather than assumed.

### Open option recipes — low-risk candidate

An ordinary module could return fresh explicit option objects for common cases,
for example conceptually:

```text
readExisting()
writeExisting()
createWriter()
createNewWriter()
truncateWriter()
appendExisting()
appendOrCreate()
readWriteExisting()
```

The main design question is usefulness, not semantic feasibility.

Constraints:

- no mode strings;
- no hidden Filesystem authority;
- return fresh ordinary data;
- preserve exact Core option meaning and validation;
- avoid an unbounded combinatorial catalogue.

### Path component conveniences — possible but low priority

Simple helpers equivalent to explicit `child` chaining are feasible, such as a
component-oriented relative/rooted builder.

The draft currently recommends not expanding into:

```text
parse
fromString
normalize
basename
extension
```

until portable rules for separators, roots, empty components, dot/dotdot and
host-independent spelling are separately designed.

### Whole-file read/write — promising after lifecycle prerequisite

Once strong scoped acquisition is executable, the library can control the
complete lifetime of operations such as:

```text
readAllBytes(...)
readAllText(...)
writeAllBytes(...)
writeAllText(...)
```

These should remain Future-shaped externally.

The design still has to close:

- exact module and selector names;
- explicit Encoding arguments/default policy for text;
- bounded-memory versus intentionally whole-content semantics;
- result values;
- write create/truncate/append policy;
- treatment of close/flush/sync distinctions;
- cancellation and partial-write aftermath.

### Copy — deferred pending policy design

A generic copy needs explicit answers for:

- whether destination must not exist or may be replaced/truncated;
- partial destination aftermath on failure/cancellation;
- metadata preservation, which current Core does not provide;
- link/entry semantics;
- source/target close ordering;
- buffering/backpressure policy.

A lower-level content-copy operation between already supplied readable/writable
capabilities may eventually be more general than a Filesystem path-to-path copy.

### Staged publication — technically evidenced but not yet closed as library API

The package tool already demonstrates an explicit-authority workflow:

```text
caller-supplied staging Path
    -> createNew staging File
    -> write content
    -> close
    -> filesystem.replace(staging, target)
```

This proves that ordinary Protos can express staging/publish behavior when the
caller explicitly provides authority and staging identity.

Do not call this generically `atomicWrite`: current Filesystem replacement gives
atomic live namespace visibility, not crash durability.

Possible future vocabulary such as `publishBytes`, `publishText` or
`replaceWithBytes` should be evaluated only after cancellation, cleanup of
abandoned staging entries and error precedence are closed.

The library must not invent `/tmp`, random names, PID-derived names, clocks or
other ambient host naming policy merely to manufacture a staging path.

## Explicitly deferred / excluded from current LIB004 direction

The current working boundary excludes:

```text
Filesystem.current
Process.filesystem
Path.open
File.open

exists
removeIfExists
mkdir
mkdirs
listDirectory
recursiveRemove

generic move aliasing replace

ambient temporary-file/directory creation
implicit random/PID/clock staging names

shell
exec
spawn OS process
subprocess
child_process
ProcessBuilder-equivalent behavior

automatic UTF-8 for Process streams
global/default Encoding
hidden current working directory
hidden current Process

implicit P/Actor/Process I/O authority transfer
hidden Future.detach
generic upstream Future cancellation
filesystem-specific native withOpen
```

Some exclusions are permanent consequences of the current capability model;
others are simply deferred until an explicit future design earns the required
semantics.

## Future-proofing conclusions

### Multiprocess and distributed execution

The library should continue to work if a future runtime:

- hosts many Protos Processes in one JVM;
- puts each Process in a separate OS process;
- moves Processes between hosts;
- uses local shared-memory transport for some Process pairs;
- uses network transport for others;
- provisions resource proxies rather than transferring live resources.

No LIB004 convenience should branch semantically on those physical choices.

### Physical multithreading

Resource ownership must remain task/Actor semantic state, not thread-local
state. A runtime is free to change carrier threads across suspension/resume.

### High concurrency

No global lock, global filesystem, global current Process, global codec, shared
mutable option object or singleton resource manager is required by the proposed
direction.

A program that never uses LIB004 should pay no runtime or conceptual cost for it.

### Future resource families

A sound scoped-custody pattern should be reusable for sockets, pipes, database
connections or future external-process handles without teaching the language
that File is special.

That generality should be demonstrated by use before promoting a universal
`ResourceScope` abstraction.

### Future multi-resource scope

A dynamic LIFO resource stack similar in spirit to disposable stacks in other
languages may eventually be valuable for workflows that acquire several
resources.

It is intentionally deferred. Nested ordinary scopes should be tried first.

## Current working recommendation

This is the checkpoint to resume discussion from; it is **not approval**.

| Area | Working recommendation |
|---|---|
| Process text adapters | likely yes, after closing wrapper freshness/lifetime |
| OpenOptions recipes | probably yes if API noise remains bounded |
| Path builders | defer unless real ergonomic demand justifies them |
| generic public `withOpen` | defer |
| internal strong scoped acquisition | yes, once general cleanup machinery is executable |
| whole-file read/write | pursue after scoped lifecycle prerequisite |
| content copy | later, after target/partial-effect policy |
| staged publication | later, after cleanup/error policy |
| `exists` / `removeIfExists` | blocked by current Core distinctions |
| mkdir/list/recursive operations | outside current Core surface |
| shell/subprocess/OS process | separate future capability area, not LIB004 |
| new Future cancellation semantics | no |
| `Cancellation.mask` | no current need |
| hidden `detach` | no |
| native filesystem-specific resource helper | no |

## Candidate implementation ordering, not assigned slices

No `LIB004-A/B/...` identifiers are assigned by this draft.

If the design closes near the current direction, a dependency-respecting order
would likely be:

1. close Process text-adapter semantics;
2. decide whether OpenOptions recipes earn inclusion;
3. re-verify the executable general cleanup/dynamic-handler prerequisite;
4. implement bounded whole-file operations over strong scoped acquisition;
5. design content copy;
6. design explicit staged publication;
7. perform a final architecture/conformance closure audit.

If step 3 exposes a real Core implementation gap, resolve it outside LIB004
before continuing with resource-acquiring conveniences.

## Open design questions for the next session

Continue from these questions rather than reopening the entire audit unless
current `origin/main` invalidates an assumption.

1. Should Process text helpers return a fresh borrowing TextReader/TextWriter per
   call, or should a Process convenience module retain one stable adapter per
   logical standard stream to avoid independent codec state over the same flow?
2. Are named OpenOptions recipes useful enough to justify a Standard Library
   surface, and what is the smallest non-combinatorial set?
3. Is the normative `ensure` plus dynamic-handler surface fully executable on
   current `main`? If not, which existing Core implementation item owns the
   reconciliation?
4. What exact Future/result contract should `readAllBytes` and `writeAllBytes`
   expose?
5. Should text whole-file helpers require an explicit Encoding always, or is
   there any capability-specific source of Encoding that avoids an ambient
   default?
6. What memory/size semantics make “all” honest for very large files?
7. Should copy start as capability-to-capability content transfer rather than
   path-to-path filesystem policy?
8. What abandoned-staging cleanup contract is possible without
   `removeIfExists`, and how should cleanup failure interact with an earlier
   publication failure?
9. At what point, if ever, has a generic `Resource.use`/`withOpen` abstraction
   earned its public API?
10. Which of these conveniences belong in LIB004's bounded initial closure and
    which should become later independent Standard Library work?

## Draft closure rule

Do not treat this document as a completed LIB004 design until the user explicitly
closes the remaining choices and the project record is updated accordingly.

When that happens:

- re-fetch current `origin/main`;
- repeat the mandatory audit where current changes could affect conclusions;
- turn accepted working recommendations into an explicit bounded implementation
  contract;
- assign implementation slices only after their dependency order is known;
- record any genuine missing Core prerequisite under its proper owner rather than
  hiding it inside LIB004;
- keep this document non-normative even after the design is closed;
- change observable language/Core semantics only through their normative owners
  under `spec/`.

## 2026-09-06 D043 / I022 prerequisite checkpoint

Subsequent audit after this draft's original checkpoint closed the cleanup
protocol ambiguity rather than adding a filesystem-specific escape hatch.

D043 / specification revision `0.1.380` standardizes:

```text
body.ensure(cleanup)
```

as an ordinary Closure-specific `Object` behavior with no new syntax or
`Closure` prototype. It fixes Closure-only receiver/cleanup validation, protected
dynamic extent, exact normal-result preservation, exactly-once LIFO cleanup,
suspension/replay behavior, later cleanup control-transfer precedence, and the
already-designed narrow cancellation shielding.

The implementation audit also confirms that the current Core substrate still
needs the general dynamic handler/unwind machinery historically deferred by
I007. That work is now tracked separately as `I022 — Dynamic Error handlers and
unwind-safe cleanup`, READY after D043. LIB004 must not implement a native
File/Filesystem-specific `withOpen` substitute.

This checkpoint resolves the earlier question of whether the normative cleanup
model itself was missing: the public `ensure` surface is now specified by D043,
while executable handler/cleanup machinery is an I022 implementation
prerequisite.

The LIB004 convenience design remains non-normative and not yet implementation
closed. The later audit direction continues to prefer a small byte-I/O /
filesystem surface over Process text-wrapper caching or mode-string/OpenOptions
aliases, but final LIB004 slices are assigned only after I022 closes and the
then-current `origin/main` is re-audited.
