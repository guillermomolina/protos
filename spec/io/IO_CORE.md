# Protos I/O Core v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of cross-cutting I/O capability, asynchronous-operation, commitment, lifecycle, wrapper-ownership, concrete-capability-shape, and pay-as-you-grow rules.

The modular I/O specification consists of `IO_CORE.md`, `BYTE_IO.md`, `TEXT_IO.md`, `FILESYSTEM.md`, and `PROCESS_IO.md`. Legacy section numbers from `IO_CORE.md` and the applicable sibling I/O module are intentionally retained so historical citations remain understandable. Normative ownership now belongs to these modules; the former monolithic file is removed by revision 326.

## 1. Scope

Core I/O is capability-oriented, byte-oriented at the lowest layer, asynchronous where an operation may wait, and compatible with the Actor isolation model.

This document defines:

- byte-readable and byte-writable protocols;
- flush, close, seek, size, truncate, sync, and half-close capabilities;
- I/O cancellation and commitment boundaries;
- wrapper ownership and lifecycle rules;
- text readers/writers and character encodings;
- line reading;
- files, filesystem authority, paths, and file URLs;
- Process-local arguments, environment, and standard-I/O facilities relevant to I/O bootstrap;
- the relationship between I/O capabilities and Actors.

The following are intentionally outside this I/O model:

- external-process creation and control;
- process identifiers and host-process inspection;
- portable or POSIX signal APIs;
- process groups and sessions;
- terminal/curses-style control protocols;
- exact standard-library namespace/import spellings beyond standard-prelude bindings explicitly required by sibling I/O modules;
- filesystem operations beyond those explicitly defined here;
- network authority acquisition and policy;
- socket creation, `connect`, `bind`, `listen`, `accept`, datagram addressing, and transport-configuration APIs;
- DNS/name resolution and the relationship between names, addresses, and network authority;
- pipe creation/pairing and pipe-specific cross-endpoint semantics, including writer-close-to-reader-EOF behavior, reader-close/broken-pipe behavior, pipe buffering/capacity, and any pipe-specific readiness or atomic-write guarantee;
- a general incremental encoder/decoder feed/reset API;
- `print` and the exact object textual-representation protocol.

When later facilities expose byte or text streams, those streams conform to this document.

---
## 2. General I/O Principles

Protos separates resource identity, I/O capability, text encoding, buffering, terminal behavior, and lifecycle ownership.

There is no universal `Stream` prototype required by v0.1. A file, socket, pipe endpoint, buffered wrapper, or memory byte stream may share protocols without sharing a common semantic ancestor other than the normal Protos delegation hierarchy.

Traits express observable capabilities of the receiver. A capability possessed by a wrapped object does not automatically become a capability of the wrapper.

Standard I/O capabilities are not implicitly transferable into isolated parallel
execution. Creating P work does not inherit the caller Actor's open files,
sockets, process handles, terminal capabilities, filesystem authority, standard
streams, or other ambient I/O authority. The fact that an I/O capability may be
delegated safely across an Actor boundary does not by itself make that capability
valid across the P boundary.

A future facility may define a specifically P-safe I/O/effect capability, but it
must define its crossing, ordering, cancellation, lifetime, and authority
semantics explicitly. Until then, ordinary isolated parallel computation performs
CPU-local isolated computation and returns values; Actor/cooperative execution
performs standard I/O effects.

> A wrapper exposes a Trait only when it correctly implements that protocol over its own observable state.

The standardized binary I/O capability Traits are:

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

Binary I/O uses `Bytes`. `String` never carries an implicit binary encoding.

I/O operations that may wait return `Future` values. They never introduce hidden Protos suspension. Suspension occurs only through the ordinary Future mechanisms, such as invoking `.value()` on a pending Future.

### Portable I/O Error categories

Core Error-object construction, freshness, identity, handler matching, and
prototype parentage are owned by `../semantics/ERRORS.md`. This I/O model owns
only which already-standardized category applies to an I/O failure and the
I/O-specific effect/lifecycle consequences of that failure.

The portable I/O categories are deliberately small:

- `IOError` is the general I/O failure category. A standardized I/O failure for
  which this model does not require a narrower category is a fresh `IOError`
  occurrence.
- `InvalidIOArgument` is used when a successfully dispatched standardized I/O
  operation rejects semantic I/O argument or option values before receiver/backend
  effects attributable to that invalid request. For a Future-returning operation,
  the returned Future fails with that fresh Error occurrence.
- `IOLifecycleError` is used when an operation is rejected or
  closure-terminated because the relevant receiver/resource/direction is already
  closing, closed, shut down, or otherwise permanently unavailable through that
  lifecycle, unless the operation is instead specified to re-observe an already
  recorded terminal lifecycle Error.
- `IOCapacityExhausted` is used when a bounded standard output facility cannot
  admit additional required retained output state and rejects the operation under
  its capacity/admission contract before contribution.
- `EncodingError` is used for strict standard encoding/decoding or required
  native-to-Protos text-representation failure where the text/encoding contract
  defines failure rather than replacement.
- `LineTooLong` is used when `readLine(maxBytes)` exceeds its standardized
  maximum-line bound.

All other standard operational/backend/open/path/filesystem failures remain
`IOError` in Core v0.1 unless a sibling normative I/O rule explicitly names one
of the narrower categories above. In particular, Core v0.1 does not standardize
separate Error prototypes merely for target absence, target already existing,
permission denial, confinement rejection, host path syntax, native errno/status,
backend availability, or generic open failure. Implementations may retain such
information privately for diagnostics but must not turn it into additional
Protos-visible standard ancestry.

Cancellation is not an I/O Error category. A successfully cancelled I/O Future
has the ordinary Future `cancelled` terminal state and is observed through the
standard fresh `Cancelled` occurrence rule owned by
`../concurrency/FUTURES_AND_TASKS.md` and `../semantics/ERRORS.md`.

Error category does not determine whether an I/O operation is safe to repeat.
Commitment, consumption, contribution, lifecycle cutovers, and uncertainty remain
owned by the operation contracts below and by the sibling I/O modules. Handling
an `IOError`, `EncodingError`, or another I/O Error never resumes or retries the
abandoned signal point. A program that decides a later attempt is semantically
safe performs a new ordinary invocation; Core v0.1 provides no privileged
handler-level retry operation.

When one I/O lifecycle explicitly records an Error as its stable terminal failure
cause, every later same-domain operation that this specification says observes
**that same recorded failure outcome** uses the exact recorded Error object. It
does not manufacture a new Error merely because the outcome is observed again.
This applies, for example, to repeated observation of one failed close lifecycle.
A later operation that merely encounters the same lifecycle category but is not
re-observing such a recorded cause creates a fresh `IOLifecycleError` occurrence.
Actor/P/value-transfer boundaries remain subject to their ordinary reconstruction
and isolation rules; this paragraph does not leak source-domain Error identity.

For every standardized I/O operation whose contract in this document says that the operation returns a `Future`, semantic validation failures of that operation's I/O arguments are represented by a failed returned Future with a fresh `InvalidIOArgument` occurrence rather than by introducing a second synchronous failure channel after the operation has been successfully dispatched. This includes, for example, an invalid `read(maxBytes)` bound, a non-`Bytes` `write` payload, an invalid seek/truncate numeric argument, an invalid `readLine(maxBytes)` bound, or a non-`String` standard text-write payload.

Such argument validation occurs before that operation performs receiver/backend I/O effects attributable to the invalid request. A failed validation therefore contributes no bytes, consumes no input, changes no logical position/size/content/lifecycle state, establishes no flush/sync/shutdown frontier, and does not exercise external authority merely to discover an error already determined by the supplied semantic argument values.

When validation depends only on already-evaluated Protos values, an implementation may return an already-failed Future. The Future-returning API shape remains uniform whether the eventual failure is discovered immediately or only after asynchronous work.

This rule begins only after ordinary Protos invocation has successfully identified and dispatched the standardized I/O operation. Ordinary language-level failures that prevent such an invocation from existing at all — for example message lookup failure, a receiver outside the operation's receiver domain, or ordinary call-arity failure before method body/operation dispatch — retain their normal language semantics rather than being retroactively wrapped in an I/O Future.

The rule likewise does not change APIs that this document explicitly defines as synchronous/non-Future operations, such as one-shot `Encoding.encode/decode`, Process standard-stream accessors, or other ordinary in-memory/bootstrap queries. Their validation/failure behavior remains governed by their own synchronous contracts and the same portable category mapping where applicable.

A standardized Future-returning I/O operation therefore has one operation-result channel after successful dispatch: immediate semantic argument invalidity is an already-known failed Future outcome, while later I/O/backend failure is a later failed Future outcome.

Unless a stronger protocol says otherwise, ordering guarantees are per logical receiver/flow. Distinct capability objects or Actor-local proxies may denote the same logical flow; object identity alone does not create an independent ordering domain. Conversely, two resources that happen to reach the same host destination are not one logical flow unless the capability semantics say so.

For writes belonging to one logical output flow, each issuing Actor's invocation order is preserved. Writes that are concurrent because they originate from independently progressing Actors have no predetermined order; the flow may choose either order when admitting/routing them. Once the flow chooses their relative order, that order is stable and the writes contribute their logical byte sequences in that order rather than interleaving the bytes of two successful writes.

This per-write non-interleaving guarantee is a Protos logical-flow property, not a promise that one native `write` syscall is atomic. Implementations may use partial native writes, buffering, routing, or multiple backend operations while preserving the same observable sequence. A stronger concrete receiver may define additional atomicity guarantees; unrelated logical flows receive no global order merely because a host backend later merges them.

Protos defines no global order among independent I/O receivers such as standard output and standard error.

---
## 4. I/O Operation Commitment and Future State

Some I/O operations require a conceptual commitment boundary to define cancellation correctly.

`COMMITTED` is an I/O-operation state/property. It is **not** a fifth `Future` state.

The standardized Future state set remains:

```text
pending
resolved
failed
cancelled
```

A Future representing an I/O operation reaches `cancelled` only when cancellation satisfies the cancellation contract of the underlying operation.

Conceptually, an I/O operation may progress through states such as:

```text
PENDING
    -> CANCELLED

PENDING
    -> COMMITTED
    -> COMPLETED

PENDING
    -> COMMITTED
    -> FAILED
```

An operation that has crossed its irreversible semantic commitment boundary cannot later be made observably as though it had been cancelled before that boundary.

Actor termination participates in this same rule. If an Actor incarnation
terminates while an asynchronous I/O operation that it initiated is still
pending, termination records a cancellation request on that operation's Future.
That request has exactly the same strength as an explicit `Future.cancel()` for
the operation: cancellation wins only while the operation's own contract still
permits a cancelled outcome.

Actor termination is not implicit `close()`, `flush()`, `sync()`, or shutdown of
the receiver. It does not revoke a Process-local stream merely because one Actor
was using it, and it does not roll back output or other effects that had already
committed. If producer/backend work must continue after the Actor has terminated
in order to honor an already-committed operation or perform safe cancellation,
that residual work remains under runtime/producer custody; it may not execute
ordinary Protos code in the terminated Actor.

Conversely, an uncommitted pending operation must not be allowed to continue
merely because an implementation failed to associate its non-task-backed Future
with the Actor that initiated it. This matters in particular for shared logical
flows: cancellation of a dead Actor's uncommitted read preserves the ordinary
zero-consumption rule rather than allowing that dead Actor to consume future
input solely as a scheduler/runtime artifact.

The exact internal mechanism used to preserve this contract is implementation-defined.

### Idempotent lifecycle Future identity

The general Core-standard Future result-identity rule is owned by
`../concurrency/FUTURES_AND_TASKS.md` §26. Therefore each successfully dispatched
invocation of a standardized Future-returning idempotent lifecycle operation
produces its own fresh standard Future identity unless that operation expressly
specifies an existing Future result.

Fresh Future identity does not begin a fresh lifecycle attempt. All invocations
that observe one idempotent lifecycle observe that lifecycle's single logical
outcome. While it is pending, each fresh Future follows that same eventual
success or failure. After successful completion, a later invocation returns a
fresh Future that resolves according to the operation's existing success
contract without repeating the lifecycle effect. After failed completion, a
later invocation returns a fresh Future that observes the already-established
failed lifecycle rather than retrying it.

Where the lifecycle contract requires preservation of an exact recorded Error
object, every fresh Future that re-observes that failed lifecycle fails with
that exact Error object within the same value/isolation domain. This
specialization changes only lifecycle sharing and outcome observation; it does
not weaken the general Future identity rule, weaken Error-identity guarantees,
create a canonical lifecycle Future, introduce a Future subtype or wrapper, or
add a hidden lifecycle token.

---
## 8. Closable

The Trait is conceptually:

```text
Closable {
    close()
}
```

`close()` returns a `Future`. On successful completion, that Future resolves to the receiver.

Closing begins permanent lifecycle termination of that receiver/resource.

Invoking `close()` is itself the irreversible semantic commitment boundary for the close operation: the receiver enters its permanent closing lifecycle before `close()` returns its Future. Because that observable lifecycle transition cannot be rolled back, the close Future cannot subsequently become `cancelled`. A cancellation request may stop an activation from waiting for close according to the ordinary Future/structured-concurrency rules, but it does not cancel or reverse the already-committed close operation.

Once closing begins, the receiver accepts no new operation that requires the resource to remain open.

`close()` has a receiver-visible lifecycle cutover point: the irreversible transition
into the closing lifecycle. Operations that require the receiver to remain open and
`close()` are ordered relative to that cutover rather than by host/native completion
timing.

When an operation and `close()` have a Protos-defined order, that order is
preserved. In particular, an operation issued earlier on the same logical receiver
is admitted before a later close even when its Future is still pending. Admission,
however, is not an implicit drain guarantee: the close cutover deterministically
classifies that preceding operation according to whether the operation has already
crossed its own irreversible semantic commitment boundary.

At the close cutover, every previously accepted operation that has not yet crossed
its own irreversible semantic commitment boundary is closure-terminated. Its
Future fails with a fresh `IOLifecycleError` occurrence, and that operation
contributes no effect beyond whatever its ordinary pre-commitment contract already
permits — which for a correctly uncommitted operation is no irreversible
operation effect. Close does not leave such an operation implementation-selectably
running merely because a backend/native request had already been started.

A previously accepted operation that had already crossed its own irreversible
semantic commitment boundary before the close cutover is not closure-terminated.
Its committed effect is not rolled back, and the operation continues to its normal
success/failure aftermath under its own contract. Close waits for that terminal
aftermath before it can complete successfully.

An operation ordered after the close cutover is not accepted as resource work: it
fails with a fresh `IOLifecycleError` occurrence and has no operation
effect. It does not race the close by starting a later native/backend operation
merely because host scheduling happens to run it first.

For an operation and `close()` that are genuinely concurrent because they originate
from independently progressing Actors through Actor-safe proxies, Protos defines no
predetermined cross-Actor arrival order. Routing/admission may choose either
request first. If close establishes the cutover before the competing operation is
admitted, that operation is rejected. If the operation is admitted first, it is
then classified at the cutover by the same commitment rule above: uncommitted
means closure-terminated failure; already committed means its normal terminal
aftermath is preserved. Once routing/admission and commitment have established
those facts, host scheduling cannot retroactively move the operation across the
cutover or rewrite its classification.

The cutover is a logical receiver-lifecycle property shared by Actor-local proxies
that denote the same receiver. It does not require one native operation at a time
and does not make proxy object identity an ordering primitive.

Closure-induced termination of an operation is distinct from cancellation of that
operation. A previously accepted uncommitted operation terminated by close fails
with a fresh `IOLifecycleError` occurrence; close does not report it as
`cancelled`. If an independent cancellation request satisfies that operation's own
cancellation contract before the close cutover terminates it, the operation may
instead become `cancelled`.

Operations that had already committed when closing began are not rolled back and
are never rewritten as cancelled or closing-failed merely because of close. They
may complete successfully or fail according to their operation contract, including
any already-permitted partial external effect.

Successful close completion requires every operation accepted before closing to
have reached a terminal Future state and the receiver's required resource-release
work to have completed successfully. Because every uncommitted accepted operation
is closure-terminated at the cutover, successful close never depends on an
implementation-selected choice to keep reversible accepted work running
indefinitely. Only operations whose own semantic effects had already committed may
remain in progress beyond the cutover, and close waits for their required terminal
aftermath.

`close()` therefore has one portable treatment of preceding accepted work:
uncommitted work fails at the close cutover; committed work keeps its ordinary
aftermath; later work is rejected.

Successful close completion means that the receiver/resource is permanently released or unusable according to its lifecycle contract.

Close is logically idempotent. A call made while closing observes the same close lifecycle rather than beginning an independent second close operation; a call made after successful close succeeds without beginning another release. Each invocation returns a fresh standard Future under the idempotent-lifecycle Future-identity rule above, while all calls observing one close lifecycle remain bound to that lifecycle's single logical success or failure outcome.

`close()` does not imply `flush()` or `sync()` unless a more specific receiver protocol explicitly requires such behavior.

A failed close does not make the object usable again. The object remains permanently failed/unusable. The Error that made the close lifecycle fail is recorded as that lifecycle's terminal failure cause. Later `close()` calls observe that same failed close lifecycle and fail with that exact recorded Error object within the same value/isolation domain; they do not begin a fresh lifecycle, manufacture a new same-category Error, or pretend that closure succeeded. Boundary reconstruction remains governed by `../semantics/ERRORS.md`.

Invoking `close()` also permanently transfers program-facing release custody of the receiver's underlying resource to that close lifecycle. After close begins, the program never regains an open/retryable resource through the same receiver merely because close later fails.

A failed close does not, by itself, prove either that the backend resource remained open or that every backend release effect completed. Some backends can report a release/flush error after making the native resource identifier unusable; others can report an error while the native resource's release state is uncertain. That backend distinction is not promoted into a portable Protos reopen-or-retry state.

When backend release state is uncertain, an implementation must not blindly retry a native close/release operation using an identifier that might already have been released and reused for an unrelated resource. A native retry is permitted only when the implementation can establish from the backend contract/state that the identifier still denotes the same resource and that retrying release cannot affect an unrelated resource.

Any backend resource or release bookkeeping that remains after a failed close remains under implementation/host custody, not program custody. It cannot be exposed again through the failed receiver, transferred from that receiver, or require a later program `close()` call to make the original lifecycle safe. The implementation may retain only the internal state needed to honor the backend's safe cleanup rules; such state does not create a second Protos close lifecycle.

Therefore a close failure means that the receiver could not establish the complete successful-close contract, not that the program has obtained a portable guarantee about whether an external/native endpoint is still open. Programs that require an effect stronger than close provides, such as durable file state, must use the corresponding explicit protocol such as `sync()` before close.

Object reachability is not a lifecycle operation. A `Closable` receiver becoming unreachable, losing its last ordinary Protos reference, or becoming eligible for implementation garbage collection does not semantically invoke `close()`, does not establish a close frontier, and does not produce a close Future or close success/failure outcome.

Portable programs therefore cannot rely on garbage-collection timing, reference-count transitions, VM safepoints, heap pressure, process placement, or implementation finalizers/cleaners to release an I/O resource at a particular time or before another observable operation. When deterministic release matters, the program must use the resource's explicit lifecycle mechanism, such as `close()`, directly or through whatever structured cleanup facility is normatively defined elsewhere.

An implementation may perform best-effort reclamation of unreachable backend/native resources to prevent implementation leaks. Such reclamation is implementation/host cleanup, not a second Protos close lifecycle. Its timing is non-portable, it must not execute arbitrary Protos user code, and it must not fabricate a successful `close()` result, surface a close error to unrelated code, or make a later-reachable Protos receiver appear closed.

If backend reclamation itself has externally visible consequences, such as releasing a host lock, descriptor, pipe endpoint, or socket endpoint, the timing of those consequences after the Protos receiver has become unreachable is deliberately outside portable Protos semantics. A program that requires those consequences before some later action must arrange explicit lifecycle termination rather than using loss of reachability as synchronization.

Explicit ownership does not change this rule. In particular, an owning wrapper closes its owned target when the wrapper's explicit close lifecycle requires it; merely abandoning or making the wrapper unreachable does not semantically invoke that lifecycle or provide a deterministic target-release guarantee.

Resource exhaustion caused by resources that a program leaves without explicit lifecycle termination is likewise not a portable scheduling or reclamation guarantee. An implementation may reclaim unreachable backend resources earlier or later, but it must not turn that implementation policy into a Protos-visible finalizer protocol.

Graceful output shutdown or half-close is represented separately by `WriteShutdown`; input half-close is represented by `ReadShutdown`.

---
## 9. Wrapper Ownership and Lifecycle

Wrapping establishes data-flow dependency, not lifecycle ownership.

> Constructing an adapter around another object does not, by itself, transfer lifecycle ownership of the wrapped object.

By default, a reader/writer/decoder/encoder/buffering adapter does not own the wrapped source or target.

Closing an adapter permanently terminates that adapter, rejects new adapter operations, and finalizes the adapter's own state.

For an output adapter, "state/output to finalize or propagate during close" means state and output that had already become semantically committed to that adapter before the close cutover, together with the normal terminal aftermath of any earlier adapter operation whose own irreversible semantic commitment boundary had already been crossed. Buffered bytes accepted by a successfully or otherwise committed earlier write remain adapter-owned output and are finalized/propagated as required by the adapter's contract.

This wrapper-finalization rule does not override the ordinary `Closable` cutover rule. An adapter operation that was accepted before close but was still uncommitted at the close cutover is closure-terminated and fails with a fresh `IOLifecycleError` occurrence. Reversible validation, speculative encoding, staged bytes, reserved buffer space, queued requests, or other implementation work belonging only to such an uncommitted operation do not become committed output merely because the receiver is an adapter and close has begun.

Conversely, close must not discard or pretend away output/state that had already committed to the adapter before the cutover. If a committed preceding operation is still pending, it retains its normal aftermath under the ordinary `Closable` rule, and adapter close waits for that required terminal aftermath before completing successfully.

Thus output-wrapper close reconciles two distinct obligations without an implementation-selected drain policy: uncommitted accepted operations fail at the cutover, while already-committed adapter state/output is finalized and propagated according to the wrapper's contract.

Closing an adapter does not automatically close the wrapped source/target.

A concrete adapter API may explicitly acquire ownership. Standard Core byte wrappers use exact ordinary factory forms: `BufferedReader(source)` and `BufferedWriter(target)` borrow their underlying capability; `BufferedReader.owning(source)` and `BufferedWriter.owning(target)` own it. `BufferedReader` and `BufferedWriter` are standard frozen-prelude factory/prototype bindings. Each successful call creates a fresh wrapper. Invalid underlying protocol arguments fail synchronously before wrapper creation or underlying I/O. Ownership is fixed at construction, does not enlarge authority, and is not exclusive; multiple owning wrappers deliberately created for the same target observe the ordinary idempotent `Closable` consequences.

When an adapter explicitly owns its target, close order is:

1. finalize and propagate the adapter's own committed state/output, including the required normal aftermath of any preceding adapter operation already committed before the close cutover; accepted-but-uncommitted adapter operations are instead closure-terminated under the ordinary `Closable` rule;
2. close the owned target.

Ownership is not a universal `Closable` property or method.

`TextWriter.close()` finalizes the encoder state that was committed before the close cutover, emits and propagates any required final bytes belonging to that committed state, waits for the required aftermath of any text-write operation already committed before the cutover, and permanently closes the wrapper. A text write that was accepted but still uncommitted at the cutover is closure-terminated under the ordinary `Closable` rule; speculative/staged encoding for that operation does not become part of finalization merely because close began. `TextWriter.close()` does not close its byte target by default.

`TextReader.close()` permanently terminates decoder/wrapper state. It does not close its byte source by default.

Closing or abandoning a reading adapter does not restore bytes that the adapter already consumed from its source because of read-ahead, buffering, or decoding.

Core v0.1 defines no universal `detach()` or adapter `reset(target)` operation.

If wrapper finalization fails during close, close fails and the wrapper remains unusable. If the wrapper owns its underlying resource, required resource release is still attempted even when wrapper finalization fails; the overall close still fails.

Wrapper close has deterministic failure precedence following the mandated close order. A failure established while finalizing/propagating the wrapper's own state is the primary failure of that wrapper close lifecycle. Required close of an owned target is still invoked after that failure, but a later failure of the owned target's close lifecycle does not replace the already-established wrapper-finalization failure as the portable failure reported by the wrapper close.

If wrapper finalization/propagation succeeds and the required close of an owned target fails, that target-close failure is the failure of the wrapper close lifecycle.

This rule deliberately does not introduce a universal aggregate-error or suppressed-error protocol. An implementation may preserve later cleanup failures for diagnostics through facilities whose semantics are separately defined, but portable Protos code must observe the primary failure selected above rather than an implementation-dependent choice between sibling cleanup errors.

When wrapper finalization has already failed, the wrapper close Future need not remain pending solely to wait for a subsequently invoked owned-target close lifecycle to reach its terminal state. Invoking the owned target's `close()` transfers that target's release custody to its own close lifecycle under the ordinary `Closable` rules. The wrapper may then report its already-established primary failure while the target close continues under implementation/runtime custody. This does not reopen either object, does not permit retry through the wrapper, and does not weaken any explicit requirement of a stronger concrete wrapper contract.

Conversely, an implementation must not omit the required owned-target close merely to report the wrapper-finalization failure sooner. Before the wrapper close Future exposes that failure, the owned target's close lifecycle must at least have been invoked and permanently committed according to `Closable`; no later program action is required to start that release.

An output wrapper must not guess downstream progress after a propagation failure. In particular, if the wrapper delegates buffered output through an ordinary `ByteWritable.write` that fails and the downstream contract does not reveal enough progress to determine which prefix was accepted, the wrapper cannot safely reconstruct its remaining buffered suffix. In that state the wrapper's output side becomes permanently failed/unusable rather than retrying bytes that may already have been accepted, discarding bytes that may not have been accepted, or changing their order.

A stronger downstream protocol may permit recovery only when it provides sufficient semantic information for the wrapper to determine its exact remaining output without ambiguity. This is a protocol property, not an implementation guess based on host-specific error codes or buffering behavior.

Failure of the wrapper's output side does not transfer ownership of the wrapped target and does not by itself close that target. An owning wrapper still follows its explicit close/release obligations.

Capabilities and lifecycle ownership propagate explicitly, never automatically.

---
## 13. Standard Concrete Capability Shapes

The following capability sets are semantic guidance for standard objects; a concrete object exposes only protocols it can correctly implement.

A raw `File`, depending on open mode, may expose:

```text
ByteReadable
ByteWritable
ByteSeekable
ByteSized
Truncatable
Closable
Syncable      // only when the backend has a meaningful durability boundary
```

A raw file is not required to expose `Flushable`; raw writes may already target the file's underlying byte boundary while durability remains a separate `sync()` concern.

Core v0.1 does not standardize concrete `PipeReader` / `PipeWriter` endpoint types or a paired-pipe lifecycle contract. A library or host facility may provide a readable pipe endpoint exposing `ByteReadable` and `Closable`, and a writable pipe endpoint exposing `ByteWritable` and `Closable`, but those capability shapes alone do not define a portable relationship between two endpoints.

In particular, Core v0.1 does not infer from those Traits that closing a writable endpoint commits EOF on some reader, that closing a readable endpoint causes a specific later writer failure, that one endpoint has exactly one peer, that any particular buffering/capacity exists, or that writes have a pipe-specific atomicity/readiness guarantee beyond the ordinary `ByteWritable` contract. A future standard pipe facility must define those cross-endpoint semantics explicitly rather than inheriting POSIX, Java, Windows, or another host pipe model accidentally.

A `Socket` exposes `ByteReadable`, `ByteWritable`, `ReadShutdown`, `WriteShutdown`, and `Closable`.

In this document, `Socket` describes the I/O/lifecycle capability shape of an already-provisioned connected byte-stream endpoint. It does **not** imply a standardized ambient socket constructor, ambient network namespace, or authority to create, connect, bind, listen, accept, resolve names, or select arbitrary remote/local addresses.

For a standard `Socket`, a `ByteReadable.read(maxBytes)` result of `null` that represents remote/end-of-stream completion permanently establishes EOF for that Socket's input direction. While the Socket remains otherwise open, later ordinary reads on that same logical input direction return `null`; later backend activity cannot make new bytes appear after that established stream end.

This is the concrete Socket specialization of the general ByteReadable EOF rule. A connected byte-stream endpoint is not a mutable regular-file sequence whose extent can later grow after EOF. An implementation must not treat one host/native end-of-stream indication as a temporary no-data condition, reconnect the endpoint implicitly, switch to a replacement connection, or resurrect input merely because a backend abstraction could do so internally.

Remote input EOF terminates only the Socket's input byte sequence. It does not by itself invoke `ReadShutdown`, `WriteShutdown`, or `close()`, does not terminate the output direction, and does not imply that previously accepted output has been flushed or acknowledged. A program may continue to use the output direction when the Socket's other lifecycle/capability rules permit it.

Local `shutdownRead()` remains a distinct irreversible local input cutover whose later reads also produce the specified EOF-like `null` result. The two states can therefore have the same ordinary read result while representing different causes: remote stream completion versus local refusal to receive. Neither state is allowed to resurrect ordinary input afterward.

Whole-resource `close()` remains stronger. Once the close cutover occurs, new operations that require the Socket to remain open fail under the ordinary Closable lifecycle rather than being reclassified as successful EOF reads merely because remote EOF had previously been observed.

Buffered bytes that logically precede remote EOF are delivered before `null` under the ordinary ByteReadable ordering rules. Only after all such preceding input has been delivered can the remote EOF result commit. A standard `BufferedReader` layered over this Socket may therefore memoize that committed EOF because the source contract itself makes it permanent.

A standard Socket's remote/end-of-stream `null` is thus a permanent end of that connected input byte stream, not a resumable no-data observation.

Possessing such a Socket transfers only the capabilities exposed by that endpoint. It does not implicitly transfer a broader capability to create sibling sockets, reconnect elsewhere, perform DNS/name resolution, inspect the host network namespace, or bypass the Process host's network policy.

A future networking domain model may define creation/listening/datagram/address/name-resolution facilities. Those facilities must make network authority and any host-dependent namespace behavior explicit and must compose with the I/O protocols defined here. Until such a model exists, implementations must not treat host APIs such as BSD/POSIX sockets, Java networking, WinSock, or ambient DNS as portable Protos semantics merely because an already-provisioned Socket object exists.

A `BufferedReader(source)` requires a `ByteReadable` source and exposes `ByteReadable` over its own buffered state. It may read ahead.

A standard `BufferedReader` preserves the source's EOF resumability rather than strengthening one observed source EOF into a permanent adapter EOF. If the BufferedReader has no unread buffered bytes and an ordered source read establishes EOF for the source's current sequence state, the corresponding BufferedReader read may return `null`. That observation does not by itself authorize the BufferedReader to cache a permanent EOF when the source contract permits later sequence-state changes to make data readable again.

After such a non-permanent EOF observation, a later BufferedReader read with no earlier buffered data must re-evaluate the underlying source through the ordinary ordered `ByteReadable` semantics. If the source still reports EOF, the BufferedReader reports `null` again. If the source now provides bytes, those bytes become the next buffered/logical input and may satisfy that later BufferedReader read. Thus a BufferedReader over a readable standard File can observe later file growth in the same manner as the File itself, subject to the File's existing cross-capability/backend visibility rules.

The adapter need not poll, subscribe, or keep an EOF read pending for hypothetical future data. A BufferedReader read that reaches a source EOF state completes according to that state; possible future resumability is considered only by a later BufferedReader read. This preserves ordinary pull semantics and does not turn buffering into implicit tail/follow behavior.

If the source contract makes EOF permanent, repeated re-evaluation may be optimized away internally because every Protos-visible later read must still produce the same permanent EOF. Such memoization is representation freedom justified by the source's semantic permanence, not by an implementation guess based on one native `read` result.

Buffered read-ahead does not weaken this rule. Bytes obtained before a source EOF are delivered in logical order before that EOF is exposed, and a later source state change cannot cause newly available bytes to bypass unread buffered bytes. Conversely, an implementation must not retain a stale EOF marker ahead of bytes that a later source re-evaluation makes readable.

This rule is specific to byte-buffering transparency. It does not weaken a standard `TextReader`'s separately defined permanent text-EOF lifecycle: once TextReader commits its own text EOF, later growth of an underlying mutable File does not resurrect that TextReader.

A standard BufferedReader therefore preserves whether EOF is permanent or resumable according to its ByteReadable source; buffering alone cannot make a resumable source EOF permanently sticky.

A `BufferedWriter(target)` exposes `ByteWritable` and `Flushable`. Its successful `write()` may mean that bytes have been copied into the wrapper's own buffer; `flush()` propagates the relevant frontier to its target according to the wrapper contract.

For every standard output wrapper that exposes `Flushable`, successful wrapper `flush()` must propagate its frontier through the complete chain of standard `Flushable` output wrappers/capabilities that are semantically on the path to that wrapper's defined underlying output boundary. Propagation does not stop merely because the wrapper's buffered bytes have been accepted by an immediate target that itself still retains them behind its own `Flushable` boundary.

Concretely, when a wrapper flush has delivered all output belonging to its frontier into an immediate target that also exposes `Flushable`, the wrapper's flush must establish an ordered target `flush()` frontier after those delivered bytes and must not resolve successfully until that target flush has completed successfully. If that target is itself a standard output wrapper over another `Flushable` target, the same rule composes recursively. Thus one successful flush of the outer standard wrapper cannot leave bytes from its frontier stranded solely in a deeper standard Protos-managed flushable buffer.

The target `flush()` may also propagate other target output that is ordered before the target's own flush frontier under the target's ordinary ordering rules. That extra propagation does not make such independently originated output part of the outer wrapper's earlier logical write sequence and does not create a new global ordering relation.

If the immediate target does not expose `Flushable`, the wrapper's portable flush obligation ends at the target boundary that the target's `ByteWritable` contract actually exposes. The wrapper must not invent an unstated host flush, durability operation, device drain, remote acknowledgement, or other stronger effect merely to simulate a missing `Flushable` capability.

Failure/cancellation aftermath composes with the existing `Flushable` rules at each layer. In particular, an outer wrapper flush cannot report success when a required deeper target flush fails, and a propagation failure does not authorize guessing hidden downstream progress or replaying bytes unsafely.

This chaining rule does not make `Flushable` automatically inherited from a wrapped object. A wrapper exposes `Flushable` only when its own contract says so and it can implement this propagation semantics correctly.

A wrapper does not automatically expose seek, size, truncate, sync, close, or shutdown merely because the wrapped object exposes them.

`Bytes` itself is not a cursor-bearing stream merely because it stores octets. A memory byte-stream adapter may separately expose byte-reading/writing/seeking/size/truncate behavior over in-memory bytes.

---
## 27. Pay-as-You-Grow

The existence of a Process, the RootActor, or a standardized Process API does not require eager construction of every I/O facility.

A runtime may lazily materialize standard-stream wrappers, encoders, proxies, routing services, buffers, or host adapters only when the relevant capability is requested or delegated.

Such laziness is representation laziness, not permission for hidden waiting. Work performed on the synchronous Process standard-stream/encoding accessor path must be locally completable without awaiting external readiness. Potentially waiting provisioning remains outside that accessor path as defined by the standard-stream availability rules above.

A program that never uses stdin/stdout/stderr need not pay for Protos-level standard-stream adapters merely because the host could provide those streams.

A program that never creates another Actor need not pay for cross-Actor I/O proxy/routing infrastructure.

These optimizations are permitted only when they preserve all observable semantics in this document.

---
## 29. Normative Summary

The central invariants of the v0.1 I/O model are:

```text
Binary I/O is byte-oriented.
String never implies an encoding.
readLine result, limit, decoding-error, and I/O-error precedence follows logical decoded input order and is independent of buffering/read-ahead.
A TextReader line-too-long, decoding, or underlying I/O failure permanently fails its text-reading side; there is no implicit drain or recovery-to-next-line behavior.
TextWriter encoding failure is pre-output failure-atomic: it emits zero bytes and preserves encoder state; writeLine text plus LF is one ordered logical text-write operation.
Successful cancellation of readText/readLine consumes zero logical text and preserves decoder/framing state; internal read-ahead may be retained but cannot become text loss, duplication, or reordering.
Implementation-controlled read-ahead in standard readers has finite effective retained-state bounds; bounded read operations do not authorize unbounded speculative consumption/retention, while intrinsically unbounded results such as readLine() remain unbounded by semantics.
readText and both readLine forms on one logical TextReader share one ordered decoded-input domain; mixed/outstanding operations cannot race separate decoder or framing states.

I/O that may wait returns Future.
I/O introduces no hidden Protos suspension point.
I/O Error instances use the fresh-or-recorded identity rules owned by ERRORS.md; standard Error prototypes are category objects, never implicit singleton failures.
Invalid semantic I/O arguments use InvalidIOArgument; lifecycle rejection/termination uses IOLifecycleError; bounded admission exhaustion uses IOCapacityExhausted; strict text conversion uses EncodingError; readLine limit failure uses LineTooLong; other standard I/O failures use IOError unless a narrower rule applies.
I/O Error category never implies retry safety; commitment/effect semantics remain authoritative and Core handlers are non-resumable.

ByteWritable.write captures its Bytes value snapshot at invocation.
Later mutation of the caller's Bytes cannot change that write.
A failed ByteWritable write contributes one contiguous prefix of its captured sequence; failure does not reveal that prefix length or make whole-write retry safe.
Append writes update logical position only through actual byte contribution: zero contribution leaves it unchanged; otherwise it becomes the position immediately after that append operation's last contributed byte.
Truncatable.truncate is failure-atomic: a failed truncate contributes no size/content change, and a backend unable to provide that contract does not expose standard Truncatable.
Pending writes remain subject to finite end-to-end admission; write snapshots do not authorize unbounded retained output.
Distinct proxies for one logical output flow share its ordering domain; concurrent successful writes are ordered as whole logical byte sequences, not byte-interleaved.


COMMITTED is an I/O-operation concept, not a Future state.
Successful cancellation before commitment preserves zero observable effect.

Capabilities are orthogonal Traits.
Socket in v0.1 is only an already-provisioned endpoint I/O shape; socket creation, addressing, DNS, and network authority are outside this model and are never ambient by implication.
Wrapped capabilities do not propagate automatically.
Wrapping does not imply lifecycle ownership.
Owning-wrapper close uses deterministic first-failure precedence in mandated close order: wrapper finalization failure remains primary, while owned-target close is still committed and any later target-close failure cannot replace it.
Invoking close commits permanent lifecycle termination; close itself cannot subsequently become cancelled.
A failed close records one stable Error cause for that close lifecycle; same-domain re-observation uses that exact Error rather than a new instance.
A failed close never returns release custody to the program and never authorizes blind native-close retry; uncertain residual backend release state remains implementation/host custody.
Object unreachability/GC never semantically invokes close; deterministic resource release requires an explicit lifecycle operation, while best-effort unreachable-resource reclamation remains non-portable implementation/host cleanup.

flush != sync != close != shutdownWrite
Invoking read/write shutdown commits permanent termination of that direction; cancellation cannot reopen it, and a failed shutdown does not create a fresh retry lifecycle.
Write shutdown has a logical cutover shared by one output direction: writes ordered before it remain accepted and are awaited, while writes ordered after it fail with zero contribution; genuinely concurrent cross-Actor write/shutdown requests are stably ordered by routing/admission.
Read shutdown has an explicit cutover: read results committed before it survive, while every accepted but uncommitted read becomes local EOF (`null`); host callback timing cannot decide the boundary.
A failed sync may leave an unknown subset durable, but does not itself poison the receiver; a later successful sync covers its entire later frontier without replaying logical data changes.
A failed flush never authorizes duplicate replay; an output wrapper with unknowable downstream progress becomes unusable unless a stronger protocol makes exact recovery possible.
EOF != unavailable capability != I/O failure
A failed ByteReadable read consumes zero observable bytes and leaves the logical sequence position unchanged; any bytes already obtained are preserved for later logical reading.
Every standard ByteReadable has a finite effective bound on Protos-managed unread state retained solely by implementation-chosen read-ahead; maxBytes need not equal a native read size, but bounded reads cannot create unbounded speculative retention.
Distinct Actor-local proxies for one ByteReadable input sequence share one consumption-ordering domain: per-Actor invocation order is preserved, while genuinely concurrent cross-Actor reads are initially unordered but stably ordered once admitted.
ByteReadable cancellation/failure preservation follows that same established input order; it does not reintroduce a nonexistent global cross-Actor invocation order.
ByteSeekable seek operations are failure-atomic with respect to logical position; failed or successfully cancelled seeks leave that position unchanged.

Path is a value, not filesystem authority.
Path equality is structural and filesystem-independent: it compares rootedness plus the ordered component sequence, as owned by `FILESYSTEM.md`.
Path semantic identity is ordinary individual object identity under `../semantics/VALUES_AND_COLLECTIONS.md`; structural equality does not make Path a Core value-identity family. Filesystem lookup identity, host syntax, and resource identity remain separate.
URL is a value, not resource-access authority.
Filesystem carries filesystem authority.
File access mode guarantees ByteReadable/ByteWritable as requested, while ByteSeekable, ByteSized, Truncatable, and Syncable are exposed only when that backend can meet each protocol's normative contract.
Path resolution through a Filesystem is confined to that capability's authorized namespace; path syntax or backend indirection cannot escape into ambient authority.
filesystem.open may report cancelled only before any portable create/truncate effect and before File-result commitment.
filesystem.open captures its complete semantic option configuration at invocation; later mutation of an options builder/value cannot change access, creation, truncation, append, or capability outcome.
A failed committed open does not compensate by deleting an already-created target or restoring already-truncated content.

A Protos Process is an execution domain, not an OS process.
Process termination revokes the execution domain's I/O authority and transfers solely Process-owned residual resources to host/runtime cleanup custody; it is not an implicit successful close/flush/sync and does not roll back committed I/O.
Every Protos execution has one Process and one RootActor.
Process-local facilities are provisioned by the Process host.
Process existence and unused facilities may remain lightweight/lazy.

Environment.each prevalidates complete portable String representability before invoking user code; invalid native environment text therefore cannot produce an implementation-dependent callback prefix.
Environment is single-valued under its native name identity; duplicate-equivalent native entries make process.environment() fail rather than selecting an implementation-dependent winner.
Process standard streams are byte capabilities.
Each available stdin/stdout/stderr binding is one Process-local logical stream for the Process lifetime; repeated accessor calls may return different capability objects but cannot create independent input sequences or output-ordering domains.
Text views are explicit adapters over those byte capabilities and their associated Encoding objects.

Actor creation does not implicitly inherit host capabilities.
Authority crosses Actor boundaries only through explicit provisioning/delegation consistent with Actor isolation.
```
