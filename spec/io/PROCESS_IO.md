# Protos Process I/O v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Protos Process I/O bootstrap, arguments, environment, standard streams, Actor delegation of Process-local I/O authority, and Process-termination I/O boundaries.

The modular I/O specification consists of `IO_CORE.md`, `BYTE_IO.md`, `TEXT_IO.md`, `FILESYSTEM.md`, and `PROCESS_IO.md`. Legacy section numbers from `IO_CORE.md` and the applicable sibling I/O module are intentionally retained so historical citations remain understandable. Normative ownership now belongs to these modules; the former monolithic file is removed by revision 326.

## 22. Protos Process and I/O Bootstrap

A Protos `Process` is an execution domain, not an operating-system process.

Every Protos execution has exactly one Process execution domain and begins with one RootActor. The Process exists conceptually even when the RootActor is the only Actor ever created.

Existence of the Process does not require heavyweight Actor, Node, Cluster, routing, or distributed-runtime infrastructure. A minimal standalone execution may represent the Process with only lightweight bootstrap/runtime state.

A standalone launcher may map one Protos Process to one operating-system process, but that mapping is not normative. A managed Node/Cluster runtime may host Protos Processes differently.

The Process host provisions Process-local application-facing facilities. In a standalone command-line execution these will normally derive from the host operating system; in managed, embedded, sandboxed, or clustered execution they may be virtualized, redirected, restricted, or absent.

For I/O/bootstrap purposes, the standardized Process-facing operations are conceptually:

```text
process.args()
process.environment()

process.stdin()
process.stdinEncoding()

process.stdout()
process.stdoutEncoding()

process.stderr()
process.stderrEncoding()
```

`Process` is the standard prototype of runtime-backed Process capability objects.
The prototype may be present in the frozen prelude because it carries no instance
authority; possessing/importing it cannot recover a Process capability.

### Root bootstrap acquisition

Before the initial RootActor's initial module body evaluates its first source
expression, the host provisions exactly one local slot named `process` on that
module's `moduleContext`. Its value is the RootActor's Process capability and
delegates to `Process`. The slot is bootstrap-local state, not a prelude/global
binding, import side effect, intrinsic, or service locator. The same rule applies
to a standalone non-importable initial entry. Imported modules receive no such
slot and can use Process only when ordinary code passes them a capability.

The Process is the custodian of Process-local host authority. Its capability grants
only operations standardized here and authority explicitly returned by them; it
does not imply filesystem, network, subprocess, Node, Cluster, or arbitrary native
authority. There is one logical Process domain but multiple Actor-local capability
proxies may denote authority into it.

Creating another Actor does not implicitly inherit Process. Explicitly supplying
a Process capability in an Actor message or `Actor.spawn` initialization vector
uses a Process-specific delegation contract: the destination receives an Actor-
local proxy to the same logical Process authority, with no authority amplification
or mutable alias to the source wrapper. This does not make open files, sockets,
native handles, or arbitrary resources transferable. Process has no Core P-transfer
contract and cannot be supplied/captured into isolated parallel execution.

---
## 23. Process Arguments

`process.args()` returns an immutable sequential snapshot of the application arguments supplied to the Protos execution.

The result implements the ordinary immutable sequential protocol needed here:

```text
size()
at(index)
each(block)
```

For this Process-argument snapshot, `size()` returns the exact semantic non-negative Integer equal to the number of application-argument String values in the snapshot. It is a synchronous observation of already-established bootstrap data and introduces no hidden suspension or external acquisition.

`at(index)` uses the same standard zero-based indexed domain as `Array.at`: `index` must be an exact semantic Integer and must satisfy `0 <= index < size()`. There is no negative-from-end indexing, numeric coercion, truncation, wrapping, saturation, or host-width interpretation. For a valid index, `at(index)` returns the exact String value at that logical position in the Process-argument snapshot. For a non-Integer, negative, or out-of-range index, it fails under the same standard indexed-access failure semantics as `Array.at`; it does not return `null` merely to represent invalid indexing.

This index-domain reuse does not make the Process-argument snapshot a standard Array, require Array object identity, or confer Array mutability. It only gives its promised immutable sequential `size()`/`at(index)` protocol the same portable index meaning already used by Core's standard zero-based sequence.

An empty Process-argument snapshot therefore has `size() == 0`, and every `at(index)` invocation fails under that indexed-access rule.

For this Process-argument snapshot, `each(block)` uses the same ordinary polymorphic callback-invocation domain as the standard Core `each` operations; it is not Closure-only. After ordinary receiver/argument evaluation has established the snapshot receiver and supplied `block`, the operation validates that `block` is callable without invoking it. A non-callable callback fails before any argument callback runs.

On a valid snapshot, callbacks are invoked exactly once for each argument String in ascending logical index order from `0` through `size() - 1`. Each callback receives that one String argument. Because the Process-argument snapshot is immutable and its complete String representability is already established by successful `process.args()` acquisition, iteration does not take a second mutable-state snapshot and cannot observe a host-native argument reorder or mutation during the call.

When every callback returns normally, `each(block)` returns the Process-argument snapshot receiver itself. Callback return values are ignored for the `each` result. An empty argument snapshot invokes `block` zero times and still returns its receiver.

If `block` signals an error or performs another ordinary non-local control effect at index `i`, callbacks already completed for lower indices are not rolled back and no callback for a higher index is invoked by that `each` call. The callback outcome follows the ordinary invocation/control semantics; Process-argument iteration does not introduce transactional rollback or an implementation-selected continuation policy.

These rules define the observable `each` behavior of the immutable Process-argument sequence without requiring that the returned snapshot be a standard `Array` or have Array object identity.

Every element is a valid Protos `String`.

The host-launcher/executable argument corresponding to facilities such as POSIX `argv[0]` is not part of this application-argument sequence.

Program/executable identity, invocation details, and host-native argv representation are outside this I/O model.

The complete portable argument snapshot is validated as one operation. If any application argument supplied by the host cannot be represented as a valid Protos String, `process.args()` fails rather than returning a partial snapshot, deferring failure to a later `at(index)`, introducing invalid Unicode, or using lossy conversion.

The application-argument sequence is one stable Process-bootstrap snapshot established for the lifetime of that Protos Process. Repeated successful `process.args()` calls observe the same argument count, order, and String values. The implementation may return the same immutable object or equivalent immutable views; physical identity is not normative unless another specification explicitly makes it observable.

Representability is part of that same stable snapshot contract. If the supplied bootstrap argument sequence cannot form the complete portable String snapshot, repeated `process.args()` calls for that Process fail consistently with that bootstrap condition; an implementation must not make one call fail and a later call succeed merely by re-reading a changed host-native argument area, changing conversion strategy, or observing host mutation after Process bootstrap.

Conversely, once the portable argument snapshot has been established successfully, later mutation or rewriting of host-native argv storage, launcher metadata, process-title storage, or another host representation does not alter the Protos snapshot and cannot make a later `process.args()` call fail or return different values.

This stability does not require eager String allocation or copying at Process startup. An implementation may retain an immutable captured representation, validate eagerly or lazily, cache conversion results, or use another representation strategy, provided all calls are observationally equivalent to one bootstrap-time logical snapshot and the success/failure outcome is stable.

`process.args()` does not re-query a live operating-system process-information facility on each invocation. A future host/native process-inspection API may expose mutable or best-effort native argument information separately, but that information is not the standardized Protos application-argument snapshot.

Host-specific APIs may later expose a native representation separately.

Arguments are immutable Process bootstrap data, not live Process authority. They may be supplied to another Actor according to the ordinary transfer rules for immutable values.

---
## 24. Environment

`process.environment()` returns the Process's standardized read-only `Environment` snapshot.

`Environment` is outside the required Core prelude and is not specified as a subtype of `Map`.

The portable protocol is conceptually:

```text
environment.get(name)
environment.contains(name)
environment.each(block)
```

`name` must be a Protos `String`.

A lookup name must also be losslessly representable as exactly one valid native environment-variable name in the represented Environment's native name domain. If the supplied String cannot be represented there — for example because the native environment format forbids one of its characters, uses a representation that cannot encode the String losslessly, or would require truncation, replacement, normalization, delimiter reinterpretation, or another lossy transformation — both `get(name)` and `contains(name)` signal a query-name representation/validity error.

Such a failure is not absence. `get(name)` must not return `null`, and `contains(name)` must not return `false`, merely because an implementation cannot express the requested Protos String as a native environment name. The failure is determined before value decoding or ordinary lookup-result selection.

If the String is a valid representable native name, lookup then uses the represented environment's native name-identity rules. Native case-insensitive comparison where applicable is an identity rule, not permission to apply implementation-selected Unicode normalization or lossy case conversion before entering the native name domain.

This rule deliberately leaves the concrete native name repertoire at the host/environment boundary. Protos does not require POSIX byte-name rules, Windows environment-block rules, or any particular native encoding as universal language semantics. It does require implementations representing the same native environment to agree that an unrepresentable query is an error rather than silently treating it as a missing binding.

`get(name)` returns the String value when the named variable exists and is representable as valid Protos Unicode text. It returns `null` when the variable is absent after a valid representable-name lookup. It signals an error when the variable exists but its value cannot be represented as a valid Protos String.

`contains(name)` returns whether the variable exists according to the represented environment's native name-identity rules after the query name has passed the representation/validity check above. It does not need to decode the variable's value merely to determine existence; therefore an existing entry with a non-Unicode value still makes `contains(name)` return `true` when its name is representable and matches.

Environment lookup distinguishes an absent valid native name from a Protos String that cannot be losslessly represented as one native environment-variable name; the latter makes both get and contains fail rather than returning null/false.

`each(block)` invokes the callback with `(name, value)` String pairs in one canonical Environment iteration order.

After the complete portable `(String, String)` representation validation described below succeeds, entries are ordered by their `name` Strings using lexicographic comparison of Unicode scalar values. At the first scalar position where two names differ, the name whose scalar value is numerically smaller comes first. If one name's scalar sequence is an exact prefix of the other, the shorter name comes first. Equal name Strings cannot occur as two distinct entries because the Environment is a single-valued mapping under its native name-identity rules.

This ordering is defined directly for Environment enumeration; it does not require or define a general String ordering operator. It performs no Unicode normalization, locale collation, native case folding, filesystem-style comparison, or host-environment ordering. The already represented Protos String values are compared exactly as scalar sequences.

`block` uses the same ordinary polymorphic callback-invocation domain as the standard Core `each` operations; `Environment.each` is not Closure-only. After ordinary receiver/argument evaluation has established the Environment and supplied `block`, the operation validates that `block` is callable without invoking it. A non-callable callback fails at that point, before Environment entry representability validation and before any user callback can run.

After callback callability has been established, and before the first callback invocation of that `each(block)` call, the Environment validates that every entry belonging to the portable snapshot can be represented as a valid `(String, String)` pair. If any entry's name or value is not representable as a Protos String, `each(block)` signals the representation error and invokes `block` zero times for that call.

This ordering gives deterministic validation precedence when more than one condition is invalid: callback callability is established first; complete Environment-to-String representability is established second; callback execution begins only after both validations succeed. Neither validation invokes arbitrary user callback code.

This prevalidation makes portable-representation failure atomic with respect to user callbacks: an implementation cannot expose an implementation-dependent prefix/subset of valid entries and only then discover an invalid-Unicode entry according to host enumeration order.

When all required validation succeeds and every callback invocation returns normally, `each(block)` returns the Environment receiver itself. Callback return values are ignored for the `each` result; the result is not `null`, the last callback result, a newly allocated collection, or an implementation-selected value. An empty valid Environment therefore invokes the callback zero times and still returns its receiver.

The rule does not require a particular physical representation or a second host-environment read. The Process Environment is already a stable snapshot; an implementation may validate eagerly when constructing that snapshot, cache validation state, retain native entries, or validate on first enumeration, provided each call has the observable behavior above.

Once representability has been established, callbacks are invoked in the canonical Environment name order defined above. The order is therefore portable for one standardized Environment snapshot and cannot depend on host enumeration order, native environment-block ordering, hash-table layout, decoder/materialization order, or implementation scheduling.

If `block` itself signals an error or otherwise performs an ordinary non-local control effect, callbacks that already occurred earlier in that canonical order are not rolled back and later entries are not invoked by that call. The prevalidation guarantee concerns Environment-to-String representation failure, not transactional execution of arbitrary Protos code.

No entry is silently omitted because it cannot be represented as String. Host-specific/native APIs may expose such entries losslessly through a separate native representation.

Environment variable name identity follows the semantics of the represented environment. In particular, a POSIX-like environment may distinguish case while a Windows-like environment may treat names case-insensitively.

The standardized portable Environment is a single-valued mapping under those native name-identity rules. The Process bootstrap environment therefore must not expose two or more native entries that compare as the same environment-variable name under the represented environment's own identity semantics.

If the supplied native bootstrap environment contains such duplicate-equivalent entries, `process.environment()` fails rather than constructing an Environment whose `get(name)`, `contains(name)`, or `each(block)` behavior would depend on host enumeration order, first/last-match convention, case-preserving spelling, or implementation-specific deduplication.

This duplicate-name validation is performed against native environment-name identity, before any rule that would make ambiguity depend on conversion to Protos String. Two POSIX-like names that differ only by case remain distinct because that represented environment treats them as distinct; two Windows-like names that differ only by case conflict when that represented environment treats them as the same name.

Duplicate-equivalent entries are rejected even when their values happen to be equal. Equality of values does not make duplicate callback count, preserved name spelling, or future native-boundary behavior semantically unambiguous.

The standardized view does not silently choose the first entry, choose the last entry, merge entries, or invent a canonical spelling. A host-specific/native environment API may expose a raw environment representation separately when an embedding needs to preserve host states that do not form a valid standardized Environment mapping.

The standardized portable Environment view is immutable for the Process lifetime. Core v0.1 provides no operation to mutate or reload the current Process environment.

The represented native/bootstrap environment itself is one stable Process-bootstrap snapshot. Repeated successful `process.environment()` calls observe the same native-name domain, the same set of native entries, the same native name-identity relationships, and therefore the same standardized Environment lookup/enumeration semantics. The implementation may return the same Environment object or equivalent immutable views; physical identity is not normative unless another specification explicitly makes it observable.

Acquisition validity is part of that stable bootstrap contract. If the supplied bootstrap environment cannot form a valid standardized Environment — for example because it contains duplicate-equivalent native names under the represented environment's own identity rules — repeated `process.environment()` calls for that Process fail consistently with that same bootstrap condition. An implementation must not make one acquisition fail and a later one succeed by re-reading a changed host environment, observing native mutation, changing enumeration order, or switching first/last-winner policy.

Conversely, once `process.environment()` has successfully established the standardized snapshot, later mutation of an operating-system environment block, embedding-host environment object, launcher state, or other native source does not add, remove, rename, or change bindings in that Protos Environment and cannot make a later `process.environment()` acquisition fail.

This stability does not require eager copying, decoding, or complete String validation at Process startup. The implementation may capture a native snapshot representation, materialize the Environment lazily, cache duplicate-name or representation metadata, and validate individual String conversions according to the existing `get`, `contains`, and `each` rules. Those choices are permitted only when every acquisition is observationally equivalent to one bootstrap-time native Environment snapshot and the acquisition success/failure outcome is stable.

`process.environment()` therefore does not re-query a live host environment on each invocation. A future host/native environment-inspection facility may expose mutable, refreshed, or raw native state separately, but such state is not the standardized Process Environment.

Protos does not place invalid Unicode, surrogate escapes, or lossy replacements into `String` merely to preserve arbitrary host environment bytes. Host-specific/native APIs may expose exact native representations separately.

The snapshot is Process bootstrap data. The implementation may return the same Environment object on repeated calls or equivalent immutable views; physical identity is not normative unless another specification explicitly makes it observable.

---
## 25. Standard Input, Output, and Error

Process standard streams are independently optional byte-oriented capabilities.

When available:

```text
process.stdin()  -> ByteReadable
process.stdout() -> ByteWritable
process.stderr() -> ByteWritable
```

They do not inherently expose text, buffering, terminal, seek, file, or lifecycle protocols merely because a particular host resource happens to possess such properties.

In particular, standard-stream capabilities do not expose `Closable` by default. Their underlying lifecycle belongs to the Process/host.

Redirection changes the resource behind a standard-stream capability, not the capability protocol itself.

A standard output may therefore be backed by a terminal, file, pipe, null sink, logging facility, cluster service, or another host-defined destination while remaining a ByteWritable capability.

### 25.1 Availability

Availability of stdin, stdout, and stderr is independent.

Requesting an unavailable standard-stream capability fails. It does not return `null` and does not fabricate a silent dummy stream.

Absence of stdin is distinct from EOF:

```text
process.stdin() unavailable    -> capability acquisition error
stdin.read(n) returns null     -> EOF on an available input sequence
```

An available input connected to an empty source or null device is an ordinary input resource whose reads may immediately produce EOF.

An available output connected to a sink that intentionally discards bytes is an ordinary successful ByteWritable resource. Successful writes to such a sink are not treated as unavailable output.

If an already acquired standard stream later encounters a broken pipe, host failure, or other I/O error, the affected operation fails normally. That is an operation failure, not retroactive absence of the capability.

Availability of each standard stream is established at Process bootstrap and remains semantically stable for the Process lifetime. A host may internally redirect or replace implementation resources only when the observable capability contract remains the same.

The standardized `process.stdin()`, `process.stdout()`, `process.stderr()`, and corresponding `*Encoding()` accessors are synchronous capability/configuration lookups over that already-established Process bootstrap state. They do not perform a new external acquisition whose completion may wait, and invoking them does not introduce hidden Protos suspension.

An implementation may lazily allocate an ordinary local wrapper, proxy, descriptor object, or other representation when one of these accessors is called, but that materialization must itself be non-waiting in the semantic sense required here. If making a usable standard-stream binding available would require an operation that can wait — for example negotiating a remote service, waiting for a device, opening a deferred external endpoint, or performing another asynchronous host acquisition — that work belongs to Process-host/bootstrap provisioning or to a separately specified asynchronous capability-acquisition protocol. It must not be hidden inside these non-Future accessors.

Failure of one of these accessors therefore reports only the already-established unavailability or invalid bootstrap configuration applicable to that binding. It is not a delayed asynchronous attempt to discover whether the host can eventually obtain a stream.

Standard-stream accessors are non-waiting lookups over bootstrap-established bindings; pay-as-you-grow may defer local representation allocation, but not a potentially waiting external acquisition behind a non-Future call.

Each available standard-stream binding denotes one Process-local logical byte stream for the Process lifetime. Repeated calls to `process.stdin()`, `process.stdout()`, or `process.stderr()` do not create fresh independent input sequences or output-ordering domains.

The runtime may return the same capability object on repeated access or distinct Actor-local/view/proxy objects. Physical object identity is not normative. Every capability obtained for one standard-input binding participates in that binding's single `ByteReadable` input-consumption ordering domain. Every capability obtained for one standard-output or standard-error binding participates in that binding's single logical `ByteWritable` output flow and its ordering/backpressure semantics.

This remains true if an implementation internally duplicates native descriptors/handles, rematerializes a proxy, or replaces a host/backend object while preserving the standard-stream binding. Such implementation machinery cannot duplicate stdin bytes, create per-access stdout/stderr ordering domains, reset logical stream state, or otherwise make repeated accessor calls behave like opening independent resources.

`stdout` and `stderr` remain distinct logical output bindings even if a particular host routes both to the same backend destination. Their lack of a Protos-defined relative order is not changed by accidental backend identity.

Repeated calls to a corresponding `*Encoding()` accessor may likewise return the same `Encoding` object or semantically equivalent immutable descriptors; their physical identity is not normative. The encoding association belongs to the stable standard-stream binding, not to whichever capability object happened to be returned by one accessor call.

Core v0.1 defines no required `hasStdin()`, `hasStdout()`, or `hasStderr()` query.

### 25.2 Standard-stream text encoding

For each available standard stream, the Process also defines a host-selected text encoding:

```text
process.stdinEncoding()  -> Encoding
process.stdoutEncoding() -> Encoding
process.stderrEncoding() -> Encoding
```

The encoding association is established at Process bootstrap and remains semantically stable with the corresponding standard-stream binding.

If the corresponding stream capability is unavailable, requesting its associated encoding also fails.

The returned Encoding need not be one of the four portable named encodings. It may be a host-provided encoding descriptor.

The fundamental standard stream remains byte-oriented. Text access is obtained by explicitly layering a `TextReader` or `TextWriter` over the byte capability using the corresponding Process encoding.

Conceptually:

```text
TextReader(process.stdin(), process.stdinEncoding())
TextWriter(process.stdout(), process.stdoutEncoding())
TextWriter(process.stderr(), process.stderrEncoding())
```

A host or standard library may provide convenient preconstructed text adapters, but those adapters are not the fundamental standard-stream protocol and do not change the byte-oriented Process contract.

No global relative ordering is guaranteed between stdout and stderr. Each is its own ordered output flow.

---
## 26. Actors and Process-Local I/O Authority

A Process may contain only its RootActor or may contain additional Actors according to the concurrency model.

Host capabilities are not implicitly inherited when another Actor is created.

A new Actor receives only capabilities explicitly provisioned for it at creation or explicitly delegated to it later according to the relevant Actor/capability transfer mechanism.

The Process capability itself is not an ordinary cross-Actor value that should be copied merely to grant one subordinate facility. Instead, authority is delegated at the granularity appropriate to the facility.

For example, an Actor that needs output may receive an Actor-safe output capability without automatically receiving stdin, environment, filesystem authority, or every other capability held by the delegator.

A live non-transferable host resource such as an open file or socket is not made transferable merely because another Actor needs equivalent access. The runtime may instead provision an Actor-local proxy/capability routed to an appropriate service while preserving the observable protocol and isolation semantics.

### Non-transferable live resources are never auto-proxied

Actor transfer of an existing live resource and provisioning a new proxy are
different semantic operations.

If a `send()`/`request()` payload or reply graph contains a live resource that is
non-transferable under the concurrency model, transfer fails with the ordinary
`NonTransferableValue` rule. The runtime must not make that transfer succeed by
silently creating a proxy, reopening the resource, duplicating a native handle,
or substituting another routed capability.

An Actor-local proxy/capability may be provisioned only through a distinct
facility whose contract defines its resource target, authority, identity,
ordering, failure, cancellation, close/lifetime, and state-sharing semantics.
Such a proxy is a new capability value; it is not the original resource becoming
transferable.

Different Actors may therefore hold distinct Actor-local capability objects that ultimately refer to the same Process-local service without sharing ordinary mutable Protos object identity.

The runtime may optimize same-Process access aggressively. No observable message hop through RootActor or another ordinary Actor is required merely because Process is the semantic custodian of the capability.

Process-local standard input, including repeated accessor results and capabilities delegated to multiple Actors, denotes the one logical input sequence of that standard-input binding unless a stronger explicitly different host capability is provisioned separately. Distinct Actor-local proxies for that stdin therefore share the `ByteReadable` input-consumption ordering domain rather than creating independent streams.

Each Actor's own read invocation order is preserved. Reads issued concurrently by independently progressing Actors have no predetermined cross-Actor order; routing/admission may choose either request first, but once chosen that relative order is stable and determines which request receives the next logical input. Bytes, EOF, and failures are not duplicated or reassigned merely because requests arrived through different proxies or native completions occurred in a different host order.

Delegated capability objects that denote the same standard output denote one logical output flow even when different Actors hold distinct Actor-local proxies. Each Actor's write invocation order is preserved. Writes issued concurrently by independent Actors have no predetermined cross-Actor order, but the routed output flow chooses one stable order for them; successful writes are not byte-interleaved merely because they arrived through different proxies.

This ordering does not impose a global scheduler order between Actors and does not make proxy object identity observable as an ordering primitive. It is only the serialization required by the shared output flow once competing writes reach that flow.

---
## 28. Process-Control Boundary

This I/O model does not define external-process creation, host-process control, process IDs, `kill`, POSIX signals, process groups, or sessions.

Those facilities belong to a separate process/host-execution domain model.

The separation is semantic:

```text
Protos Process
    != operating-system process
```

Terminating a Protos Process means terminating that Protos execution domain. In a standalone launcher this may naturally cause the hosting operating-system process to exit; in a managed runtime it need not terminate the operating-system process that hosts other Protos execution domains or runtime services.

Process termination is a resource-custody boundary, not an implicit successful `close()`, `flush()`, `sync()`, `shutdownRead()`, or `shutdownWrite()` over every I/O object formerly reachable from that Process. Termination does not synthesize user-visible lifecycle Futures, run arbitrary Protos cleanup callbacks, promise buffered output propagation, establish durability, or convert pending I/O into successful operation results.

Once Process termination commits, no Process-local I/O capability or proxy from that execution domain remains usable by Protos code. Host/runtime machinery must revoke or detach the terminated Process's routes to Process-local authority even when the hosting operating-system process, runtime service, or backend remains alive.

Any live backend resource whose remaining custody exists solely on behalf of the terminating Process transfers to implementation/host cleanup custody. That cleanup must no longer depend on reachability, garbage-collection timing, or execution of Protos code in the terminated Process. The implementation may release such resources synchronously or asynchronously and may use backend-specific safe cleanup mechanisms, but it must not return them to program custody or resurrect a usable Protos capability merely because cleanup is delayed, fails, or has uncertain backend state.

Termination does not roll back I/O effects that committed before the termination boundary. Pending or internally executing operations need not be allowed to finish merely to make Process termination graceful; implementation/host cleanup may cancel, abandon, or finish backend work as required to safely retire the Process. No portable Protos program may infer from Process termination whether an unobserved pending operation produced additional host-visible effect beyond effects already constrained by that operation's own commitment semantics.

A backend resource deliberately shared independently of the terminating Process is not necessarily destroyed merely because one Process loses access to it. Termination releases/revokes that Process's authority and custody contribution; it does not revoke separately provisioned authority held by another Process, host service, peer, or external principal.

Exact physical-release timing and backend consequences after custody has transferred to the host/runtime are outside portable Protos semantics, just as failed-close residual cleanup state is. A standalone operating-system process may obtain stronger automatic reclamation from its host; a managed runtime must provide the same Protos-level authority revocation without relying on whole-OS-process exit.

A future external-process facility may expose stdin/stdout/stderr pipes or other streams. Such streams use the I/O protocols defined here.

A future host-signal facility must integrate with the Actor/concurrency model rather than asynchronously executing arbitrary Protos code inside an Actor turn. The exact signal API is outside this document.

---
