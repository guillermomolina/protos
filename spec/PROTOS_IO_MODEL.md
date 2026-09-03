# Protos I/O Model v0.1

Language version: 0.1  
Document revision: 164
Status: Draft  
Last updated: 2026-09-03
This document is the normative domain model for Protos input/output semantics.

It complements:

- `PROTOS_LANGUAGE_SPEC.md`
- `PROTOS_GRAMMAR.md`
- `PROTOS_RUNTIME_SEMANTICS.md`
- `PROTOS_CONCURRENCY_MODEL.md`

The language specification owns language constructs, the grammar owns syntax and mandatory lowering, the runtime-semantics document owns executable core evaluation rules, the CLOSED normative sections of the concurrency model own Actor/Future/concurrency-domain semantics, and this document owns observable I/O-domain semantics. Unresolved concurrency-ledger sections and explicitly open subtopics are non-normative.

The I/O model deliberately avoids introducing special I/O syntax. I/O facilities are ordinary objects, capabilities, Traits, and messages unless another normative specification explicitly says otherwise.

---

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
- the exact public API for explicit wrapper-ownership acquisition;
- exact standard-library namespace/import spellings;
- exact error prototype names where this document only requires that an error be signaled;
- filesystem operations beyond those explicitly defined here;
- network authority acquisition and policy;
- socket creation, `connect`, `bind`, `listen`, `accept`, datagram addressing, and transport-configuration APIs;
- DNS/name resolution and the relationship between names, addresses, and network authority;
- a general incremental encoder/decoder feed/reset API;
- `print` and the exact object textual-representation protocol.

When later facilities expose byte or text streams, those streams conform to this document.

---

## 2. General I/O Principles

Protos separates resource identity, I/O capability, text encoding, buffering, terminal behavior, and lifecycle ownership.

There is no universal `Stream` prototype required by v0.1. A file, socket, pipe endpoint, buffered wrapper, or memory byte stream may share protocols without sharing a common semantic ancestor other than the normal Protos delegation hierarchy.

Traits express observable capabilities of the receiver. A capability possessed by a wrapped object does not automatically become a capability of the wrapper.

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

Unless a stronger protocol says otherwise, ordering guarantees are per logical receiver/flow. Distinct capability objects or Actor-local proxies may denote the same logical flow; object identity alone does not create an independent ordering domain. Conversely, two resources that happen to reach the same host destination are not one logical flow unless the capability semantics say so.

For writes belonging to one logical output flow, each issuing Actor's invocation order is preserved. Writes that are concurrent because they originate from independently progressing Actors have no predetermined order; the flow may choose either order when admitting/routing them. Once the flow chooses their relative order, that order is stable and the writes contribute their logical byte sequences in that order rather than interleaving the bytes of two successful writes.

This per-write non-interleaving guarantee is a Protos logical-flow property, not a promise that one native `write` syscall is atomic. Implementations may use partial native writes, buffering, routing, or multiple backend operations while preserving the same observable sequence. A stronger concrete receiver may define additional atomicity guarantees; unrelated logical flows receive no global order merely because a host backend later merges them.

Protos defines no global order among independent I/O receivers such as standard output and standard error.

---

## 3. Bytes Used by I/O

`Bytes` is a standardized byte-sequence object but is not a required Core-prelude binding.

A `Bytes` element is an ordinary semantic `Integer` in the inclusive range 0 through 255. A byte value is not a distinct `Byte` or `UInt8` object merely because it is stored in `Bytes`.

The minimum standardized `Bytes` behavior required by this model is conceptually:

```text
Bytes()
size()
at(index)
atPut(index, value)
each(block)
add(value)
removeAt(index)
```

`Bytes` is mutable and dynamically resizable. `atPut(index, value)` modifies an existing position and does not resize the sequence. `add(value)` appends. `removeAt(index)` removes the existing element, shifts later elements left, and returns the removed element.

`atPut` and `add` accept only semantic `Integer` values from 0 through 255. A value such as `1.0` is rejected even when it is numerically equal to an allowed Integer.

No sparse `Bytes` representation is observable.

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

The exact internal mechanism used to preserve this contract is implementation-defined.

---

## 5. ByteReadable

The Trait is conceptually:

```text
ByteReadable {
    read(maxBytes)
}
```

`read(maxBytes)` returns a `Future`.

`maxBytes` must be an `Integer` greater than zero. `read(0)` is an error.

A successful non-EOF result is a `Bytes` object containing from 1 through `maxBytes` octets.

`null` means permanent end-of-file/end-of-sequence for the receiver's current sequence state.

For `read(maxBytes)` with `maxBytes > 0`, an empty `Bytes()` result is never used to mean either "no data yet" or EOF. If data is not yet available and EOF has not been established, the Future remains pending.

A read may complete with fewer octets than requested.

I/O errors fail the Future and are distinct from EOF.

If data remains before EOF, that data is returned before EOF is reported. Once EOF has been observed, subsequent reads return `null` unless an operation such as seeking changes the sequence state.

Multiple outstanding reads against the same logical receiver share one input-consumption ordering domain.

When reads have a Protos-defined invocation order, that order is preserved. In particular, reads issued sequentially by one Actor consume/evaluate the input sequence in that Actor's invocation order even when earlier read Futures remain pending.

Reads that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe routing/proxies have no predetermined relative order merely from host scheduling. The receiver/routing layer may choose either read first when admitting the competing requests. Once it chooses their relative order, that order is stable: the chosen earlier read owns the next logical input opportunity according to its own `read(maxBytes)` semantics, and a later chosen read cannot bypass it to consume bytes that precede the earlier read's result.

The chosen order concerns logical input consumption, not native syscall start/completion order. Implementations may overlap host operations, prefetch, route, or buffer internally when those mechanisms cannot change which ordered Protos read receives each byte, EOF, or failure.

### 5.1 ByteReadable cancellation and failure

A pending read may be cancelled only while cancellation can preserve the observable input sequence.

If cancellation wins before commitment, the operation consumes no bytes from the observable sequence.

A failed ordinary `ByteReadable.read` also consumes zero bytes from the receiver's observable input sequence. Failure is therefore not a partial-read result hidden behind a failed Future.

If an implementation has already obtained bytes from an operating-system, host, or downstream source before the Protos read is cancelled or fails, those bytes must be preserved or rebuffered whenever they belong to that read's logical input position. They remain the earliest unread bytes of the observable sequence and must be returned by later successful reads before newer source bytes that follow them.

`read(maxBytes)` also bounds implementation-controlled speculative retention associated with that logical input flow. A standard `ByteReadable` implementation must have an effective finite bound on unread bytes, native-read results, bookkeeping, or equivalent state retained solely because it chose to read ahead beyond the bytes currently needed to satisfy accepted Protos reads. The numeric bound is implementation-specific and is not portable Protos behavior.

The `maxBytes` argument limits only the successful result of one read, not necessarily the size of one native/backend operation. A receiver may use larger native reads, prefetch, batching, shared buffers, or other bounded read-ahead strategies. However, repeatedly invoking a small bounded Protos read against an open-ended source must not by itself authorize unbounded growth of Protos-managed unread speculative state.

Data retained because it is semantically required by already accepted higher-level operations is governed by those operations' own resource semantics. For example, an unbounded `readLine()` layered above a `ByteReadable` may necessarily accumulate an arbitrarily long line; that is not permission for the underlying `ByteReadable` itself to accumulate an independently unbounded additional speculative suffix.

This finite-retention requirement concerns state controlled by the Protos receiver/runtime. It does not constrain buffering performed independently by a host kernel, filesystem cache, remote peer, device, or separately authorized backend outside the receiver's managed retention.

For a receiver whose reads share a logical sequence position, cancellation or failure leaves that logical position unchanged. An implementation whose backend position advanced while obtaining bytes that are subsequently preserved must virtualize, rebuffer, reposition, or otherwise reconcile the backend so later Protos operations observe the unchanged logical position.

Pending reads retain the logical input-consumption order established above. For reads with a Protos-defined invocation order, that is the preserved invocation order; for genuinely concurrent cross-Actor reads, it is the stable order chosen by routing/admission. Cancelling or failing the earlier read in that established order does not allow a later outstanding read to consume bytes that the earlier outcome was required to preserve before those bytes are again available in logical order.

A later seek or other operation that explicitly changes the receiver's sequence state may make preserved read-ahead irrelevant according to that operation's normal semantics; this is not permission to expose an intermediate host position or to duplicate preserved bytes.

Read failure does not by itself semantically close or poison the receiver. Later operations are evaluated against the receiver's actual protocol and backend state and may succeed or fail accordingly. A concrete receiver may define stronger lifecycle behavior when that behavior is part of its observable contract.

Once a read has committed its data, EOF, or error result, cancellation cannot roll that result back.

---

## 6. ByteWritable

The Trait is conceptually:

```text
ByteWritable {
    write(bytes)
}
```

`write(bytes)` returns a `Future`. On successful completion, that Future resolves to the receiver.

The argument must be `Bytes`.

`write(bytes)` captures a logical snapshot of the argument's size and octet contents at invocation time. The snapshot is fixed before the operation can remain pending because of backpressure, wait behind an earlier operation, or begin host/native I/O. Later mutation of the supplied `Bytes` does not change the byte sequence belonging to that write, and each write invocation captures its own logical snapshot.

This is a semantic snapshot, not a requirement for eager physical copying. An implementation may use immutable backing storage, copy-on-write, reference retention, scatter/gather I/O, or another representation strategy provided that ordinary later mutation of the caller's `Bytes` remains valid and cannot change the captured write sequence. `write` does not impose a hidden caller-visible borrow, freeze, pin, or "do not mutate until completion" lifetime rule.

Snapshot capture does not itself commit output. A write whose cancellation wins before the I/O commitment boundary still contributes zero bytes to the observable output sequence.


Writing `Bytes()` is valid and may complete immediately.

Writes to one logical receiver contribute to one ordered output byte sequence in invocation order.

Successful completion means that the complete supplied byte sequence has been accepted by the receiver according to that receiver's output semantics.

Successful completion does **not** by itself imply:

- flush through buffering layers;
- durable persistence;
- receipt by a remote peer;
- terminal presentation;
- any stronger device-specific effect.

Backpressure may keep a write pending. Internally, an implementation may use partial native writes, but ordinary `ByteWritable.write` does not expose partial success as its normal successful result.

### 6.1 Write admission and end-to-end backpressure

Invocation-time snapshotting does not make a `ByteWritable` an unbounded output queue.

A logical output flow must have an effective finite bound on write work that has been accepted but has not progressed far enough for its retained state to be released. The bound may be fixed, adaptive, receiver-specific, or imposed by downstream capacity; its numeric value is not portable Protos behavior.

When that bound is reached, later writes remain pending at admission/backpressure rather than forcing the implementation to retain an unbounded number or volume of write snapshots. Backpressure must be capable of propagating toward the code issuing writes through the pending Futures.

This rule is end-to-end across adapters, Actor-safe proxies, routing layers, and native/backend buffering controlled by the Protos implementation. No intermediate Protos-managed queue may grow without bound merely because another layer has not yet applied pressure.

The rule does not impose a one-write-at-a-time protocol. Implementations may admit and overlap multiple writes, batch them, coalesce representation, or use bounded pipelining while preserving invocation ordering, snapshot semantics, cancellation/commitment rules, and the receiver's observable output contract.

A program that intentionally issues writes without waiting may therefore accumulate pending write Futures, but the runtime is not required to accept all corresponding payload state into an ever-growing output queue. Program-held Futures and arguments remain subject to ordinary reachability/lifetime rules; this backpressure guarantee concerns retention required by the I/O delivery path itself.


### 6.2 ByteWritable cancellation and failure

While a write is still pending and no irreversible output effect has occurred, it may be cancelled with the guarantee that zero bytes from that write contributed to the receiver's observable output sequence.

The first irreversible output effect commits the operation. After commitment the write cannot be cancelled as though nothing happened.

For a captured write sequence of length `N`, a failed write contributes exactly one contiguous prefix of that sequence of some length `k` where `0 <= k <= N`. It contributes no byte after the first byte it did not contribute. `k = 0` means failure occurred before any output effect; `k > 0` means the write committed before failing. Failure is permitted even when `k = N` if the receiver cannot truthfully report successful completion despite the whole logical sequence having become observable at its output boundary.

The value of `k` is not exposed by ordinary `ByteWritable.write`. Therefore a failed write does not prove that zero bytes were written and does not make automatic retry of the whole captured sequence semantically safe.

Invocation ordering still applies across failure. Any contribution from a later write on the same logical output flow occurs after the failed write's contributed prefix; bytes from a later write do not fill holes inside, precede, or interleave with that prefix.

A committed prefix remains part of the logical output flow. If the receiver remains usable and later `flush()`, `sync()`, position-sensitive operations, or other ordered operations apply to that output, they observe the committed prefix as preceding output/effect according to their own contracts.

A write failure does not by itself semantically close the receiver or create a universal poisoned state. A later operation is evaluated against the receiver's actual protocol and backend state and may succeed or fail accordingly. A concrete receiver may define stronger failure/lifecycle behavior when that behavior is part of its observable contract.

---

## 7. Flushable

The Trait is conceptually:

```text
Flushable {
    flush()
}
```

`flush()` returns a `Future`. On successful completion, that Future resolves to the receiver.

`flush()` establishes an output-propagation frontier at invocation time.

Successful completion means that output committed to the receiver before that frontier has been propagated through buffering controlled by that receiver to the receiver's defined underlying output boundary.

`flush()` does not terminate the output sequence and does not imply durable persistence, remote application receipt, or physical terminal presentation.

A flush with no pending output is valid and may complete immediately.

For:

```text
write(A)
flush()
write(B)
```

successful completion of that `flush()` establishes the required frontier for A without requiring B to have been flushed.

Multiple flush operations may be internally coalesced when all observable frontier guarantees are preserved.

Cancelling a flush never cancels or undoes preceding writes. A flush may be cancelled before its own irreversible propagation work commits. After irreversible propagation begins, cancellation cannot undo that propagation.

A flush may fail after partial propagation; failure does not imply rollback.

For one ordered output flow, partial propagation of a failed flush is itself ordered: it may advance the frontier through some prefix of the output that preceded the flush, but it does not propagate later bytes while leaving an earlier hole in that same logical flow.

Ordinary `Flushable.flush()` does not expose how far a failed flush propagated. A receiver that nevertheless retains exact internal knowledge of its own propagation progress may remain usable and may allow a later flush to continue from the unpropagated remainder. Such continuation must not duplicate bytes/effects already propagated merely because the earlier flush Future failed.

A standard output adapter that exposes `Flushable` propagates flush through the output layers that the adapter itself owns or semantically controls, without inventing durability guarantees beyond those layers.

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

Closure-induced termination of an operation is distinct from cancellation of that operation. A previously pending uncommitted operation that is prevented from proceeding because close began fails with an error indicating that the receiver/resource is closing or closed; close does not report that operation as `cancelled`. If an independent cancellation request for that operation satisfies its own cancellation contract before closure wins, that operation may instead become `cancelled`.

Operations that had already committed when closing began are not rolled back and are never rewritten as cancelled merely because of close. They may complete successfully or fail according to their operation contract, including any already-permitted partial external effect.

Successful close completion requires every operation accepted before closing to have reached a terminal Future state and the receiver's required resource-release work to have completed successfully. Close need not wait for a pending operation to succeed: it may cause that operation to fail as specified above. This rule prevents a successful close from leaving accepted I/O operations indefinitely pending.

Successful close completion means that the receiver/resource is permanently released or unusable according to its lifecycle contract.

Close is logically idempotent. A call made while closing observes the same close lifecycle rather than beginning an independent second close operation; a call made after successful close succeeds without beginning another release. Exact Future-object identity across repeated calls is not required, but calls observing one close lifecycle must not disagree about whether that lifecycle ultimately succeeded or failed.

`close()` does not imply `flush()` or `sync()` unless a more specific receiver protocol explicitly requires such behavior.

A failed close does not make the object usable again. The object remains permanently failed/unusable. Later `close()` calls observe that failed close lifecycle and fail consistently with that outcome; they do not begin a fresh lifecycle or pretend that closure succeeded.

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

Closing an adapter permanently terminates that adapter, rejects new adapter operations, and finalizes the adapter's own state. An output adapter's close also propagates all previously accepted output that belongs to that adapter layer as required by that adapter's contract.

Closing an adapter does not automatically close the wrapped source/target.

A concrete adapter API may explicitly acquire ownership. Exact ownership-option syntax is outside v0.1 I/O semantics.

When an adapter explicitly owns its target, close order is:

1. finalize and propagate the adapter's own pending state/output;
2. close the owned target.

Ownership is not a universal `Closable` property or method.

`TextWriter.close()` finalizes encoder state, emits and propagates any required final bytes, and permanently closes the wrapper. It does not close its byte target by default.

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

## 10. ByteSeekable

The Trait is conceptually:

```text
ByteSeekable {
    position()
    seek(position)
    seekBy(offset)
    seekToEnd()
}
```

All operations return `Future` values.

Positions and offsets are measured in octets.

`position()` resolves to a non-negative `Integer` representing the current logical position.

`seek(position)` requires a non-negative Integer and resolves to the resulting absolute position.

`seekBy(offset)` accepts an Integer displacement relative to the current position and resolves to the resulting absolute position. A result below zero is an error.

`seekToEnd()` sets the position to the current end of the represented byte sequence and resolves to that absolute position.

Seeking beyond the current end is allowed unless the concrete resource cannot represent such a position. Seeking alone does not extend or otherwise modify the byte sequence.

Reading beyond the current EOF returns `null`. A later write beyond the end may grow a concrete resource according to that resource's write semantics.

Reads, writes, and seeks that affect one shared sequence position are ordered in invocation order.

When a `ByteWritable` write on such a receiver fails after contributing a prefix of length `k`, the shared logical position advances exactly by the committed contribution that the receiver's positioned-write semantics place into the sequence. A later ordered position-sensitive operation therefore starts from the post-prefix logical position, not from the pre-write position merely because the write's Future failed.

`position()` is asynchronous for uniform no-hidden-suspension semantics even when a concrete implementation can answer immediately.

Seeking after EOF re-enables reading according to the new position.

Standard `seek(position)`, `seekBy(offset)`, and `seekToEnd()` are failure-atomic with respect to the receiver's logical sequence position. If a seek Future fails, that seek operation leaves the logical position exactly as it was immediately before that seek's ordered evaluation.

For `seekBy(offset)`, the base position used to compute the target is the logical position established by all earlier ordered position-affecting operations when this seek reaches its evaluation point. For `seekToEnd()`, the target is the sequence end applicable at that same ordered evaluation point. A later independent size/content change does not retroactively change an already committed seek result.

A seek commits only when its complete logical position change has been established. Once committed, cancellation cannot undo it and that seek completes successfully rather than reporting failure after exposing the requested position change.

Cancellation before seek commitment leaves the logical position unchanged. Failure before commitment likewise leaves it unchanged. An implementation whose backend cursor moved tentatively before the Protos seek failed or was cancelled must restore, virtualize, or otherwise reconcile that cursor so subsequent Protos operations observe the unchanged logical position.

This guarantee concerns the logical position belonging to the `ByteSeekable` receiver. It does not require one native seek call, nor does it prohibit speculative backend work that remains unobservable.

A backend that cannot provide or emulate this failure-atomic logical-position contract must not expose the standard `ByteSeekable` capability merely because it has a host cursor API with weaker failure semantics.

A failed `position()` query has no position-changing effect.

`ByteSeekable` does not imply `ByteSized`.

---

## 11. ByteSized and Truncatable

`ByteSized` is conceptually:

```text
ByteSized {
    size()
}
```

`size()` returns a `Future` resolving to the current underlying byte-sequence length as a non-negative Integer measured in octets.

`Truncatable` is conceptually:

```text
Truncatable {
    truncate(size)
}
```

`truncate(size)` returns a `Future` and requires a non-negative Integer.

If `size` is less than the current sequence size, bytes at positions greater than or equal to `size` are discarded.

If `size` is greater than or equal to the current sequence size, the sequence is unchanged. Core v0.1 truncation does not extend a sequence.

Successful `truncate(size)` resolves to the receiver.

Truncation does not change a `ByteSeekable` receiver's current position, even when that position becomes greater than the new EOF.

Standard `Truncatable.truncate` is failure-atomic with respect to the sequence contents and size changed by that operation. If `truncate(size)` fails, the truncate operation itself contributes no change to the sequence size or contents. It does not expose a partially truncated intermediate size as the aftermath of a failed Future.

For a size-reducing truncate, the operation commits only when the complete requested truncation effect has been established at its semantic boundary: the sequence size is `size` and content at positions greater than or equal to `size` has been discarded. Once that commitment occurs, cancellation cannot undo it and the truncate operation completes successfully rather than reporting a truncate failure after its complete semantic effect has committed.

For a request whose `size` is greater than or equal to the sequence size at the operation's ordered evaluation point, the standard no-extension rule makes the operation a successful no-op; it does not acquire an irreversible content-change commitment merely by checking the size.

Cancellation before commitment leaves sequence contents and size unchanged. Failure before commitment likewise leaves them unchanged. An implementation may perform tentative backend work before commitment only if it can keep that work from becoming a failed truncate's observable sequence effect.

A backend that cannot provide or emulate this failure-atomic contract must not expose the standard `Truncatable` capability for that operation merely because it has a host primitive named truncate. A future stronger/different protocol may explicitly model partially effective resizing, but ordinary `Truncatable` does not.

This failure-atomicity rule concerns effects attributable to the truncate operation itself. It does not freeze the resource against independently authorized concurrent writers or other operations; such independent changes retain their own ordering/authority semantics and may change the resource before or after the truncate's commitment point.

For one logical byte-sequence receiver, operations exposed by that receiver whose result or effect depends on the same sequence contents, size, or shared logical position participate in one sequence-state ordering domain. This includes, when the receiver exposes them, ordinary reads, positioned writes, append writes, `position()`, seeks, `size()`, `truncate()`, and durability/propagation frontiers whose contracts refer to preceding receiver changes.

When such operations have a Protos-defined invocation order, that order is preserved even when their Futures are simultaneously pending. Therefore a `size()` invoked after an earlier ordered write observes the sequence state including whatever contribution that write has committed before the size query reaches evaluation; it does not bypass the earlier write merely because the write Future is still pending. Likewise, a later ordered `truncate()` does not race ahead of an earlier write, and a later ordered `seekToEnd()` or `size()` uses the sequence state established by all earlier ordered sequence-state operations at its evaluation point.

A failed or cancelled earlier operation contributes exactly the state change allowed by that operation's own contract before the next ordered operation is evaluated. For example, a cancelled/failed ordinary read contributes no sequence-position change, a failed positioned or append write may contribute its permitted byte prefix and corresponding logical-position aftermath, and a failed `truncate()` contributes no truncate-induced size/content change.

Operations that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe routing/proxies have no predetermined relative order merely from host scheduling. The receiver/routing layer may choose either operation first, but once their relative order is chosen it is stable for all sequence-state effects and observations attributable to those operations.

This rule does not serialize unrelated receivers, does not impose one native syscall at a time, and does not freeze the underlying file or sequence against independently authorized external actors/processes/backends. Implementations may pipeline, speculate, buffer, batch, or use positional/native APIs without a shared host cursor, provided the observable Protos sequence-state order is preserved.

---

## 12. Syncable and Durability

The Trait is conceptually:

```text
Syncable {
    sync()
}
```

`sync()` returns a `Future`. On successful completion, that Future resolves to the receiver.

`flush()` and `sync()` are distinct:

- `flush()` propagates previously accepted output through buffering controlled by the receiver to its defined output boundary;
- `sync()` establishes a durable-state frontier over receiver changes ordered before it.

Successful `sync()` completion guarantees that changes to the receiver ordered before that frontier have reached the durable-storage boundary defined by that receiver/backend.

For a standard local file, `sync()` means the strongest ordinary file synchronization offered for preserving the resulting file state, including file content and metadata necessary to preserve that state.

Core v0.1 defines no distinct `syncData()` operation.

`Syncable` is orthogonal to `ByteWritable`; a receiver may expose one without the other when meaningful.

Not every file/backend is required to expose `Syncable`. A remote or virtual backend exposes it only when the backend has a meaningful durable boundary it can honestly specify.

`sync()` does not discover or flush wrappers above the receiver. A wrapper exposes `Syncable` only if it correctly bridges its own pending state to the underlying durability boundary.

`close()` does not imply `sync()`.

Later changes are not required to be durable when an earlier sync frontier completes.

File synchronization does not by itself guarantee filesystem-namespace durability for creation, deletion, rename, or directory-entry changes.

Cancellation may cancel sync before the durable operation commits. Once irreversible synchronization has begun, cancellation cannot undo persistence already reached. Sync may fail after partial persistence.

A failed `sync()` does not roll back durability already reached and does not make that partial durable state invalid. Ordinary `sync()` does not expose which subset of its frontier became durable before failure.

Sync failure does not by itself semantically close or poison the receiver. If the receiver remains otherwise usable, a later `sync()` establishes its own frontier in the ordinary invocation order and, on success, guarantees durability for all receiver changes ordered before that later frontier, including changes that were part of an earlier failed sync frontier and had not yet become durable.

Retrying `sync()` therefore does not duplicate or replay the underlying logical data changes: it requests durability for the then-current frontier. The implementation may avoid re-synchronizing state it already knows to be durable, but that optimization is not observable.

A concrete receiver may define stronger lifecycle behavior after a synchronization failure when that behavior is part of its observable contract. Host-specific conventions, error codes, or backend writeback details do not by themselves create such a Protos lifecycle rule.

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

A `PipeReader` exposes `ByteReadable` and `Closable`.

A `PipeWriter` exposes `ByteWritable` and `Closable`.

A `Socket` exposes `ByteReadable`, `ByteWritable`, `ReadShutdown`, `WriteShutdown`, and `Closable`.

In this document, `Socket` describes the I/O/lifecycle capability shape of an already-provisioned connected byte-stream endpoint. It does **not** imply a standardized ambient socket constructor, ambient network namespace, or authority to create, connect, bind, listen, accept, resolve names, or select arbitrary remote/local addresses.

Possessing such a Socket transfers only the capabilities exposed by that endpoint. It does not implicitly transfer a broader capability to create sibling sockets, reconnect elsewhere, perform DNS/name resolution, inspect the host network namespace, or bypass the Process host's network policy.

A future networking domain model may define creation/listening/datagram/address/name-resolution facilities. Those facilities must make network authority and any host-dependent namespace behavior explicit and must compose with the I/O protocols defined here. Until such a model exists, implementations must not treat host APIs such as BSD/POSIX sockets, Java networking, WinSock, or ambient DNS as portable Protos semantics merely because an already-provisioned Socket object exists.

A `BufferedReader(source)` requires a `ByteReadable` source and exposes `ByteReadable` over its own buffered state. It may read ahead.

A `BufferedWriter(target)` exposes `ByteWritable` and `Flushable`. Its successful `write()` may mean that bytes have been copied into the wrapper's own buffer; `flush()` propagates the relevant frontier to its target according to the wrapper contract.

A wrapper does not automatically expose seek, size, truncate, sync, close, or shutdown merely because the wrapped object exposes them.

`Bytes` itself is not a cursor-bearing stream merely because it stores octets. A memory byte-stream adapter may separately expose byte-reading/writing/seeking/size/truncate behavior over in-memory bytes.

---

## 14. TextReadable and TextWriter Separation

Text I/O is layered over byte I/O.

The text-reading capability is conceptually:

```text
TextReadable {
    readText()
}
```

`readText()` returns a `Future` resolving to either a non-empty `String` or `null`.

`null` means permanent text EOF with no remaining decoded text.

Returned String chunk boundaries have no Unicode semantic significance. A chunk boundary may split an extended grapheme cluster across two returned String values.

Concatenating the returned Strings in order reconstructs exactly the decoded text stream.

The decoder never exposes an incomplete encoded character as malformed String content. Incomplete encoded byte sequences remain buffered until sufficient input arrives or EOF makes them invalid according to the selected decoding policy.

Text I/O chunk size is not normatively expressed in grapheme clusters or Unicode code points.

The text-writing capability is conceptually:

```text
TextWritable {
    writeText(text)
}
```

`writeText(text)` returns a `Future`. On successful completion, that Future resolves to the receiver. The argument must be `String`.

The associated encoder converts text to bytes while preserving invocation order. Protos does not require each `writeText()` call to map one-to-one to a single underlying byte write.

There is no `TextSeekable` protocol in Core v0.1.

---

## 15. Encoding Objects

`Encoding` is a standardized immutable/reusable descriptor/configuration abstraction outside the required Core prelude.

The portable standardized encodings in v0.1 are:

```text
UTF8
UTF16LE
UTF16BE
Latin1
```

Exact standard-library namespace/import spellings remain outside this document.

A Process host may additionally provide an `Encoding` object describing a host-selected standard-stream encoding even when that encoding is not one of the portable named values above.

Per-flow encoder and decoder state is independent and may be stateful. Reusing an `Encoding` descriptor does not mean sharing one mutable encoder/decoder state across flows.

The standardized one-shot operations are conceptually:

```text
encoding.encode(text)   -> Bytes
encoding.decode(bytes)  -> String
```

They are in-memory operations and do not return Futures.

One-shot decoding treats an incomplete final encoded sequence as an error.

### 15.1 Error policy

Decoding is strict/fatal by default.

A decoding configuration may explicitly request replacement of malformed input with U+FFFD. Core v0.1 defines no ignore-malformed-input policy.

Encoding is strict by default. Core v0.1 defines no general replacement-encoding policy for characters not representable in the selected encoding.

### 15.2 Latin1

`Latin1` means ISO-8859-1 semantics: octets 0x00 through 0xFF map directly to Unicode U+0000 through U+00FF and vice versa.

It is not the WHATWG/Windows-1252 interpretation sometimes exposed under a `latin1` label by other platforms.

### 15.3 Unicode validity

UTF-8 decoding rejects overlong sequences, surrogate scalar values, and values greater than U+10FFFF.

UTF-16LE and UTF-16BE validate surrogate pairing.

A Protos `String` contains valid Unicode text; the encoding layer never introduces isolated surrogate values merely to preserve arbitrary host bytes.

### 15.4 BOM handling

The selected `Encoding` determines interpretation. A BOM never silently changes the selected encoding to another encoding.

For UTF8, UTF16LE, and UTF16BE, an initial matching BOM is consumed by default. A configuration may explicitly preserve it as U+FEFF.

A BOM occurring later in the stream is ordinary U+FEFF text.

Encoders emit no BOM by default. Exact configuration syntax for explicit BOM emission is outside v0.1.

---

## 16. TextReader and readLine

A standard `TextReader` layers decoding and text buffering over `ByteReadable`.

It may provide:

```text
readLine()
readLine(maxBytes)
```

Both operations return Futures resolving to `String` or `null`.

Line terminators are:

```text
LF
CR
CRLF
```

CRLF is one terminator. The terminator is consumed and omitted from the returned String.

EOF with no remaining decoded text returns `null`.

EOF after non-empty unterminated text returns that final text once; the next line read returns `null`.

Examples:

```text
empty input + EOF  -> null
"\n"               -> "", then null
"\r\n"             -> "", then null
"abc" + EOF        -> "abc", then null
"abc\n"            -> "abc", then null
"abc\n\n"          -> "abc", "", then null
```

An I/O or decoding error while constructing a line fails the Future; a partial line is not returned as success.

All standard text-reading operations accepted by one logical `TextReader` — `readText()`, `readLine()`, and `readLine(maxBytes)` — share one logical decoded-input sequence and one operation-ordering domain. They do not race independent decoder, buffering, or line-framing states merely because their Futures are simultaneously pending.

When two such operations have a Protos-defined invocation order, they consume/evaluate the decoded input in that order. In particular, operations invoked sequentially by one Actor retain that invocation order even when the earlier Future remains pending.

Requests that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe routing/proxies have no predetermined relative order merely from host scheduling. The reader/routing layer may choose either request first, but once their relative order is chosen it is stable. Each operation then receives the next logical text according to that chosen order and its own operation semantics.

`readText()` and the two line-reading forms participate in the same ordering domain. A pending `readText()` cannot consume text past an earlier ordered `readLine()`, and a pending line read cannot consume text past an earlier ordered `readText()`.

If an earlier ordered text-read is successfully cancelled, its zero-consumption rule leaves the next ordered operation to observe the same earliest remaining logical text. If an earlier ordered operation commits a decoding, I/O, or line-too-long failure that permanently fails the TextReader, later outstanding text-read operations fail under that lifecycle rule without bypassing the failed operation and consuming later input.

This ordering is a logical TextReader property, not a requirement to execute one native byte read at a time. Implementations may read ahead, buffer, decode speculatively, and pipeline internal work when the resulting observable assignment of text/results/errors to operations is exactly the one required by this ordering.

### 16.1 Line length limit

Core v0.1 imposes no universal arbitrary fixed line limit.

`readLine(maxBytes)` provides an explicit safety bound. `maxBytes` must be an Integer greater than zero and counts input octets belonging to the line content, excluding the line terminator.

If the line content exceeds the limit, the Future fails and no fragment is returned as a successful line.

A line-too-long failure permanently fails the `TextReader`'s text-reading side. The reader does not implicitly scan/discard the remainder of the overlong line, search for a later terminator, or attempt to recover a next-line boundary.

A decoding or underlying I/O failure encountered while a `TextReader` is constructing a `readText()` or `readLine()` result likewise permanently fails that reader's text-reading side. This deterministic wrapper failure rule is independent of whether the wrapped byte source itself remains usable.

After the text-reading side has failed, subsequent `readText()` and `readLine()` operations fail without consuming additional input from the wrapped source. Core v0.1 defines no recovery/reset operation that resumes text decoding on the same `TextReader`.

Successful cancellation is not such a failure and does not poison the reader; the cancellation rules below preserve the logical input sequence.

Failure of the `TextReader` does not by itself close, fail, or transfer ownership of its wrapped byte source. `close()` remains permitted when the reader exposes `Closable`, and an owning reader still performs its explicit release obligations according to the wrapper lifecycle rules.

### 16.2 Deterministic line framing and error precedence

Line construction is determined in logical input order after decoding under the selected `Encoding`; buffering and read-ahead do not change which condition belongs to the current line operation.

For `readLine(maxBytes)`, decoding validity is established for each next encoded character before the source octets of that character are counted as line content. If decoding that character fails, the operation fails with the decoding error rather than treating malformed octets as valid content merely to reach the size limit.

After a valid non-terminator character is decoded, all source octets belonging to that character count toward the current line's content. If that makes the content count exceed `maxBytes`, the line-too-long condition is established immediately. The implementation need not read or decode later input merely to discover another possible error. An I/O or decoding failure that is encountered before the limit has been established remains the failure of the current operation.

LF terminates the current line immediately. CR also terminates the current line immediately. If the next decoded character is LF, that LF is consumed as the second character of the same CRLF terminator; otherwise it belongs to the following input. Determining whether an LF follows CR does not make the already-complete line depend on later input: EOF, an I/O failure, or a decoding failure encountered after the terminating CR is observed belongs to subsequent reading rather than retroactively failing the completed line.

An implementation that reads ahead across a completed line must preserve any following bytes, EOF state, or deferred error so that the next operation observes the same logical input sequence and failure ordering.

These rules define result/error precedence, not an implementation requirement to decode one character or perform one underlying read at a time.

### 16.3 TextReader cancellation

Cancellation semantics apply uniformly to `readText()`, `readLine()`, and `readLine(maxBytes)` on a `TextReader`.

A pending text-read operation may become `cancelled` only before its own result or failure commits and only while the reader can preserve the operation's zero-consumption cancellation contract.

When cancellation succeeds, that operation consumes no text from the `TextReader`'s observable decoded input sequence and does not make the reader failed. A later text-read operation observes the same remaining decoded text and line-framing state that it would have observed if the cancelled operation had not consumed a result.

Bytes already obtained from the wrapped `ByteReadable`, decoded characters already produced internally, incomplete encoded-character state, BOM/decoder state, and read-ahead beyond a prospective result may remain buffered inside the TextReader. The implementation need not physically roll its decoder or wrapped byte source backward, but it must retain, rebuffer, virtualize, or otherwise reconcile that internal progress so it cannot cause text loss, duplication, reordering, a changed line boundary, or a spurious decoding error after successful cancellation.

Implementation-controlled speculative read-ahead must not create unbounded retained state independently of the semantic work requested by the program. Every standard buffering/decoding reader has an effective finite implementation bound on additional unread bytes, decoded text, checkpoints, or equivalent state retained solely because the implementation chose to read or decode ahead of the earliest outstanding operation's required progress. The numeric bound is implementation-specific and is not portable Protos behavior.

This bound is distinct from storage intrinsically required by an operation whose semantic result itself is unbounded. For example, unbounded `readLine()` may necessarily accumulate an arbitrarily long unterminated line because its successful result is that line. The rule does not impose an arbitrary portable line-length limit.

Conversely, an explicitly bounded operation such as `readLine(maxBytes)` does not authorize speculative retention that grows without bound with input beyond the point needed to establish that operation's result or failure. Once enough logical encoded input has been examined to establish the line-too-long condition, an implementation must not continue consuming or retaining an unbounded suffix merely as read-ahead for that already-determined operation. Limited read-ahead already obtained as part of a finite buffer remains permitted and follows the ordinary preservation/discard rules.

Likewise, `readText()` chunk boundaries remain implementation-selectable, but that freedom is not permission to require an ever-growing implementation-chosen chunk before completing an otherwise progress-capable read. A standard TextReader must be able to choose finite chunks and apply backpressure through pending reads rather than turning an open-ended source into unbounded Protos-managed speculative retention.

This is the text/adapter specialization of the general `ByteReadable` finite speculative-retention rule. Standard `BufferedReader` and other standard read adapters may use fixed, adaptive, or downstream-informed buffering, but each layer must preserve the same distinction between semantically required operation state and independently chosen speculative read-ahead.

The requirement constrains Protos-managed retention, not the amount of data a host kernel, remote peer, filesystem cache, or independently authorized underlying source may buffer outside the Protos reader. It also does not require one native read at a time or prohibit prefetch, vectorized I/O, decoder batching, or bounded pipelining.

This guarantee is about the TextReader's logical text sequence. As with ordinary read-ahead, it does not promise to restore the separately accessible wrapped byte source's native/logical cursor to the position it had before the wrapper performed internal reads; abandoning or closing the wrapper retains the wrapper-lifecycle rule that already-consumed source bytes are not restored.

For `readLine()` and `readLine(maxBytes)`, a line result commits when the complete result has been determined by a terminator or by EOF with remaining content.

For `readText()`, a result commits when the complete non-empty String chunk chosen for that operation, or the `null` EOF result, has been determined. The existing freedom in `readText()` chunk boundaries does not weaken the rule that a successfully cancelled operation consumes zero logical text.

Once a text result, decoding failure, or underlying I/O failure has committed for the operation, cancellation cannot replace that outcome. A committed decoding or I/O failure follows the permanent TextReader failure lifecycle defined above.

---

## 17. TextWriter and Line Output

A standard `TextWriter(byteWritable, encoding)` layers encoding over a `ByteWritable` target.

A TextWriter may provide:

```text
writeText(text)
writeLine(text)
```

Both operations return `Future` values resolving to the receiver on successful completion.

Each `writeText(text)` invocation is one ordered logical text-write operation. `writeLine(text)` is likewise one logical text-write operation whose text payload is `text` followed by LF (`U+000A`). The LF is therefore part of the same operation for encoding validation and ordering; another operation on that TextWriter does not interleave between the text and its line terminator.

`writeLine(text)` uses LF as the canonical Protos line terminator and does not perform platform-native newline translation.

Before any byte belonging to one of these operations becomes observable at the byte target's output boundary, the TextWriter must establish that the operation's complete text payload is encodable under the writer's current per-flow encoder state and configured error policy.

If that encoding step fails, the text-write Future fails with zero bytes from that operation contributed to the target, and the TextWriter's encoder state remains as it was immediately before that operation's ordered evaluation. An encoding failure by itself does not poison the TextWriter; a later text-write operation may proceed from that unchanged encoder state.

This encoding-failure atomicity does not require eager allocation of the complete encoded byte sequence. An implementation may validate, checkpoint encoder state, encode in a reversible staging representation, perform a dry run, or use another strategy, provided no target-visible bytes or committed encoder-state change from the operation precede successful validation of the complete text payload.

After complete encoding validation succeeds, the operation has one logical encoded byte sequence and one resulting encoder state determined from the encoder state at that operation's ordered evaluation point. The implementation may deliver that logical byte sequence through one or more underlying byte writes; the mapping from one text write to native/underlying write calls remains non-observable.

Cancellation may win only while the operation can still preserve both zero target-visible byte contribution and the pre-operation encoder state. Once the first irreversible output effect of the operation occurs at the target's semantic output boundary, the text-write operation is committed and cancellation cannot make it observably disappear.

An underlying output failure after commitment follows the ordinary `ByteWritable` and wrapper-failure rules. In particular, the target may already contain a prefix of the operation's encoded byte sequence, and a TextWriter must not guess how much downstream progress occurred. When exact remaining output cannot be known, its output side becomes permanently failed/unusable as specified by the wrapper lifecycle rules.

A successfully completed text write commits its resulting encoder state for later ordered text writes. A successfully cancelled text write and a text write that fails during pre-output encoding validation do not advance that state.

A TextWriter exposes `Flushable` or `Closable` only when it correctly implements those protocols over its own state. Target capabilities are never inherited automatically.

Closing a TextWriter finalizes encoder state and propagates the final bytes belonging to that wrapper before the wrapper becomes closed. The byte target is not closed by default.

---

## 18. File Open Semantics

`File` means an already-open file resource. `Path` represents a filesystem path value. `Filesystem` represents authority over a filesystem namespace.

The fundamental open operation is conceptually:

```text
filesystem.open(path, options) -> Future<File>
```

Core v0.1 does not use opaque mode strings such as `"r"`, `"w"`, `"a"`, or `"r+"` as its normative semantic model.

Open configuration has separate dimensions:

```text
access:
    read
    write
    read + write

creation:
    existing
    create
    createNew

initial content:
    preserve
    truncate

write placement:
    positioned
    append
```

Exact construction/spelling of the options object is outside this document.

`filesystem.open(path, options)` captures the complete semantic open configuration at invocation time, before the operation may remain pending, wait for another operation, or begin host/backend I/O. The captured configuration consists of the access, creation, initial-content, and write-placement choices defined above.

Later mutation of an ordinary object, collection, builder, or other library value that was used to express `options` cannot change the configuration of an already-invoked open. Each open invocation captures its own configuration independently.

This is a semantic value capture, not a requirement to eagerly copy a particular public options object. An implementation or standard library may use immutable option values, builders, compact flags, copy-on-write state, or another representation. It must not impose a hidden caller-visible borrow/freeze/"do not mutate until the Future completes" rule merely to keep an open's configuration stable.

Capturing the configuration does not itself commit any filesystem effect and does not by itself prevent cancellation. The ordinary open commitment rules below still determine when creation, truncation, or File-result custody makes cancellation impossible.

Validation of access/creation/initial-content/write-placement combinations is performed against that captured configuration. A later mutation of the value originally used to express the options cannot turn an invalid captured open into a valid one, change a valid captured open into a destructive one, alter its access rights, or otherwise change its eventual File capability shape.

At least read or write access is required.

Append requires write access.

Truncate requires write access.

Append plus truncate is invalid.

`create` and `createNew` are mutually exclusive choices.

Write access alone does not imply create or truncate.

Append alone does not imply create.

Invalid combinations fail open.

### 18.1 Creation

`existing` requires the target to exist.

`create` opens an existing target or creates it when absent.

`createNew` creates a new target and fails if the target already exists. The existence check and creation are one race-free/atomic creation operation at the backend's semantic boundary.

### 18.2 Truncate-on-open

Writable open with `truncate` establishes initial file size zero as part of open.

Read-only plus truncate is invalid.

### 18.3 Append

Append is not merely an initial seek to EOF.

Every append-mode write is placed at the current file end applicable to that write independently of the current seek position.

Therefore `seekToEnd()` followed by an ordinary positioned write is not semantically equivalent to append in the presence of concurrent external writers.

Append placement and logical-position aftermath follow the ordinary `ByteWritable` prefix semantics. Merely determining or consulting the current EOF is not an irreversible output effect. A cancelled append whose cancellation wins before any byte contribution leaves the handle's logical position unchanged.

For a captured append sequence of length `N`, let `k` be the contiguous prefix length contributed by that write according to the ordinary failed-write rule. If the write fails with `k = 0`, its logical position is unchanged. If it contributes at least one byte, whether the Future ultimately resolves successfully or fails, the handle's logical position becomes one greater than the file offset at which the last byte contributed by that append operation was placed.

Consequently, after a failed append that contributed bytes, the logical position does not revert merely because the Future failed. Conversely, a failed append that contributed no bytes does not move the logical position merely because an implementation/native API temporarily positioned a backend cursor at EOF.

A successful append of non-empty `Bytes` uses the same rule and therefore leaves the logical position immediately after that operation's last contributed byte. A successful append of `Bytes()` contributes no byte and leaves the logical position unchanged.

The logical position established by an append is a numeric position, not a promise that it remains the current EOF. A later append still uses the then-current EOF rather than assuming that the stored logical position is still the end.

Unrelated external writers or independently authorized operations may change the file between backend-level append actions when the backend does not provide stronger atomicity. Such changes do not retroactively alter the logical position already established by a contributed byte. They may also mean that the final position cannot be derived as the pre-write position plus `k`, or as one initial EOF plus `k`; the normative rule is the position immediately following this operation's last contributed byte.

A read+append handle may seek for reading. Append writes nevertheless retain append placement semantics.

The initial logical position is zero even for append mode.

The standard Protos append contract does not promise stronger non-interleaving with unrelated external writers than the backend can provide.

Writes invoked on the same receiver still preserve their required invocation ordering.

### 18.4 File capabilities by mode

Open mode determines the access capabilities that a successful standard `File` must expose; backend semantics determine which orthogonal optional file capabilities can additionally be exposed honestly.

A readable File exposes `ByteReadable` and `Closable`.

A writable File exposes `ByteWritable` and `Closable`.

A read/write File exposes both `ByteReadable` and `ByteWritable`, plus `Closable`.

`ByteSeekable` is additionally exposed only when the backend can provide or emulate the standard logical-position and failure-atomic seek contract.

`ByteSized` is additionally exposed only when the backend can provide the standard current-size query semantics for that file resource.

`Truncatable` is additionally exposed only for a writable File whose backend can provide or emulate the standard failure-atomic truncate contract. Support for truncate-on-open does not by itself imply that the returned File can support later `Truncatable.truncate` operations.

`Syncable` is additionally exposed when the backend provides the required durability semantics.

Thus open access mode is a lower bound on the File's access authority, not a promise that every backend implements every orthogonal random-access/size/truncate/durability protocol. The capability set of the returned File must remain stable for that File's lifetime; an implementation does not add or remove these Traits merely because a particular invocation would succeed or fail at the current moment.

This rule is the concrete `File` specialization of the general capability principle in section 13: a concrete object exposes only protocols it can correctly implement. It also means that a backend must not satisfy section 18.4 by exposing a protocol whose normative guarantees it cannot meet and then failing every operation merely because a host API has weaker semantics.

A raw File is not required to expose `Flushable`.

### 18.5 Open commitment, cancellation, and resource custody

`filesystem.open(path, options)` follows the general I/O commitment rule, but its commitment boundary depends on which requested open effects are observable.

An open may resolve to `cancelled` only while both of the following remain true:

- no portable filesystem namespace/content effect required by that open has become observable; and
- no successful `File` result has committed.

For an open that would only acquire an existing file with preserved content, acquiring an implementation/native handle is not by itself a portable Protos filesystem effect. Cancellation may therefore still win before the `File` result commits, provided the implementation can dispose of every internally acquired resource without exposing a `File`.

If `create` or `createNew` actually creates a previously absent target, creation is an irreversible open commitment. If `truncate` changes an existing target to size zero, that content change is an irreversible open commitment. Once either effect can be observed through the Filesystem capability, cancellation cannot make the open Future `cancelled` as though the target had remained unchanged.

A committed open may subsequently fail before producing a `File`. Such failure does not perform compensating rollback of portable effects already committed by that open. In particular, the open operation itself does not delete a target that it already created and does not restore content that it already truncated.

This rule constrains only compensating action performed by the failed open. It does not guarantee that a later observer will still find the created target present or the file still empty: independent Actors, external processes, or backend activity may subsequently rename, remove, replace, truncate, or write the target according to their own authority and ordering.

If an open reaches `failed` or `cancelled` without returning a `File`, no undisclosed live `File` resource is transferred to the program. Any native or backend resource acquired internally remains under implementation custody and must be released before that terminal result. If cancellation cannot satisfy that cleanup obligation, cancellation does not win; the operation continues to a non-cancelled terminal outcome.

When open resolves successfully, custody of the resulting resource is represented by the returned `File`, whose subsequent lifecycle is governed by `Closable`.

Host effects not otherwise exposed by the Filesystem capability, such as platform-specific metadata side effects outside the portable model, do not create additional Protos commitment boundaries merely because a particular native `open` primitive happens to have them.


---

## 19. ReadShutdown and WriteShutdown

The half-close Traits are independent:

```text
ReadShutdown {
    shutdownRead()
}

WriteShutdown {
    shutdownWrite()
}
```

Both operations return Futures. On successful completion, each operation resolves to its receiver.

### 19.1 WriteShutdown

`shutdownWrite()` permanently ends the receiver's output direction without closing its input direction or necessarily closing the whole resource.

Invoking `shutdownWrite()` is itself the irreversible commitment boundary for the output-direction lifecycle. Before the returned Future is observable, the receiver has entered a permanent write-shutting-down state and accepts no new writes. That transition cannot be rolled back, so the shutdown Future cannot subsequently become `cancelled`. Cancellation of an activation waiting for it does not cancel or reopen the committed shutdown lifecycle.

`shutdownWrite()` establishes an end-of-output frontier after writes invoked before it. Those writes retain the opportunity to complete, and shutdown waits behind them.

Successful completion means that every required preceding output operation reached the outcome required for a clean frontier and that the underlying resource accepted the end-of-output frontier after that preceding output. It does not mean the peer application consumed the data.

There is no universal implied flush. A wrapper that exposes `WriteShutdown` must first correctly finalize/propagate its own output state before propagating shutdown to the underlying output direction.

If required preceding output fails, or if establishment of the end-of-output frontier itself fails, the shutdown Future fails rather than pretending that a clean end-of-output was established. The output direction remains permanently unavailable to new writes; failure never reopens it.

Write shutdown is logically idempotent. Calls made while shutdown is pending observe the same lifecycle rather than beginning independent shutdown attempts. After successful shutdown, later calls succeed without establishing another end-of-output frontier. After failed shutdown, later calls fail consistently with that failed lifecycle and do not retry the frontier or reopen output. Exact Future-object identity is not required.

Input capability and input lifecycle remain unaffected except where a stronger concrete protocol explicitly couples them.

### 19.2 ReadShutdown

`shutdownRead()` permanently terminates the receiver's willingness to receive input without closing unrelated capabilities.

Invoking `shutdownRead()` is itself the irreversible commitment boundary for the input-direction lifecycle. Before the returned Future is observable, the receiver has entered a permanent read-shutting-down state. That transition cannot be rolled back, so the shutdown Future cannot subsequently become `cancelled`. Cancellation of an activation waiting for it does not cancel or reopen the committed shutdown lifecycle.

Buffered or future input may be discarded by read shutdown. Once read shutdown begins, no new read is accepted as an ordinary data-producing operation.

Read shutdown has a receiver-visible cutover point: the irreversible transition into `read-shutting-down`. The outcome of every read competing with shutdown is determined relative to that cutover, not by which host/native completion callback happens to run first.

A read whose `Bytes`, EOF, or error result committed before the read-shutdown cutover retains that committed result. A read accepted before shutdown but still uncommitted at the cutover completes with `null`; it does not remain pending indefinitely, consume/disclose buffered input after the cutover, or become `cancelled` merely because shutdown occurred. Thus invoking `shutdownRead()` while an earlier read Future is still pending intentionally terminates that uncommitted read rather than waiting behind it as `shutdownWrite()` waits behind preceding writes.

For operations issued through independently progressing Actors or Actor-safe proxies, routing/admission may determine whether a competing read commits before the shutdown request reaches the receiver's cutover. Protos defines no global cross-Actor arrival order. Once the receiver has established the cutover, however, host scheduling cannot retroactively move an uncommitted read to the pre-shutdown side or expose bytes that were discarded by shutdown.

Internal/native reads may already have obtained bytes before cutover without having committed a Protos read result. Those bytes may be discarded as part of read shutdown; they must not later escape through the terminated receiver merely because backend work completed before or after the shutdown call. No restoration to a separately accessible underlying source is implied unless a stronger wrapper/source protocol explicitly provides it.

After read shutdown begins, later ordinary reads return `null`, including while the shutdown Future is still pending. This local EOF-like result reflects the receiver's terminated input direction and does not claim remote EOF.

If underlying/backend work required to establish or release the read-shutdown state fails, the shutdown Future may fail, but the input direction remains permanently unavailable for ordinary reading; failure never reopens it.

Read shutdown is logically idempotent. Calls made while shutdown is pending observe the same lifecycle. After successful shutdown, later calls succeed without performing another shutdown. After failed shutdown, later calls fail consistently with that failed lifecycle and do not start a fresh shutdown attempt. Exact Future-object identity is not required.

Remote EOF and local read shutdown are distinct events even though ordinary reads after either condition may return `null`.

Output capability and output lifecycle remain unaffected except where a stronger concrete protocol explicitly couples them.

### 19.3 Whole-resource close

`close()` closes the whole resource according to its lifecycle contract. It is not normatively defined as the composition `shutdownRead()` plus `shutdownWrite()`.

Core v0.1 defines no separate universal `shutdownBoth()` operation.

---

## 20. Filesystem Authority and Path

`Path`, `Filesystem`, and `File` are distinct concepts.

`Path` is a non-authoritative value describing a path according to filesystem-path semantics.

Possessing a `Path` grants no filesystem access.

`Filesystem` is a capability representing authority over a filesystem namespace.

Possessing a `File` grants access only through the capabilities exposed by that already-open resource.

> Knowing a Path does not grant filesystem access. Filesystem authority is carried by a Filesystem capability.

`Filesystem` is not a required Core-prelude binding. A Process host may provision one or more filesystem capabilities according to its policy.

`Path` is a standardized path value rather than merely an alias for `String`. Core v0.1 defines no special path literal syntax.

Every Filesystem defines the namespace/root/base used to interpret paths supplied to it.

Protos does not require mutable Process-global current-working-directory state. Relative path interpretation belongs to the Filesystem capability. Derived Filesystem capabilities may use different bases without mutating other Actors' path interpretation.

An "absolute" Path is absolute within the namespace of the Filesystem interpreting it; it does not necessarily denote an operating-system root.

### 20.1 Filesystem authority confinement

A `Filesystem` operation must resolve every supplied `Path` entirely within the authority represented by that `Filesystem` capability.

Path syntax or backend name-resolution behavior cannot enlarge that authority. In particular, parent traversal, absolute-path forms, symbolic links, reparse points, aliases, mount/redirection mechanisms, or other backend indirections must not cause an operation to access a resource outside the capability's authorized namespace.

A relative path is resolved from the Filesystem's configured base, but successful resolution may move only within that same authorized namespace. An absolute Path is resolved from the Filesystem's namespace root, not from any ambient host root.

If resolving a path would cross the Filesystem authority boundary, the operation fails. Protos does not silently reinterpret an escaping path as ambient host access, does not fall back to a Process-global current directory, and does not obtain broader authority merely because the host API used internally would permit it.

Confinement applies to the complete resolution operation, not only to lexical preprocessing of `.` or `..`. An implementation must remain confined in the presence of backend indirections and concurrent namespace changes. If it cannot establish that the requested resolution stays within authority because of a race or backend limitation, the operation fails rather than proceeding with uncertain authority.

This rule does not require host ancestry to define the namespace. A virtual, mounted, remote, or otherwise mediated Filesystem may deliberately include resources that are not descendants of one host directory. The invariant is capability authority: every resource reached by resolution must belong to the namespace that the `Filesystem` is authorized to expose.

Path normalization, component/equality rules, and host-native path conversion remain separate questions; none of them may weaken this authority boundary.

### 20.2 Portable Path value semantics

The portable semantic content of a `Path` is filesystem-independent. A Path consists of:

- a rooted/relative flag; and
- an ordered sequence of path components.

A normal path component contains one valid Protos `String` name. The empty String, `"."`, and `".."` are not normal component names in the portable component model. Parent traversal is represented as a distinct parent component rather than by pretending that `".."` is an ordinary child name. A current-directory component is semantically redundant and is not retained in the portable value.

A relative Path with no components denotes the interpreting Filesystem's configured base. A rooted Path with no components denotes that Filesystem's namespace root.

The component sequence is not lexically collapsed across parent components. In particular, a path conceptually containing `a / b / parent / c` is not the same Path value as `a / c`. Filesystem resolution may observe backend indirection at `b`, so eliminating the parent component before resolution could change the target and weaken authority reasoning.

Path value equality is structural and filesystem-independent: two Paths are equal exactly when they have the same rooted/relative flag and the same ordered component kinds and normal-component String values. Equality does not access a Filesystem and does not use host case folding, Unicode normalization, drive-letter rules, inode/file identity, symlink resolution, or backend aliases.

Therefore unequal Path values may resolve to the same resource in a particular Filesystem, and an equal Path value may resolve differently at different times if the authorized namespace itself changes. Resource identity and Path value equality are distinct concepts.

A Path is immutable and carries no authority. It may cross Actor boundaries according to the ordinary rules for immutable values without transferring a Filesystem capability.

The exact public constructors, parsing helpers, display syntax, and native-path conversion APIs remain outside Core v0.1. Any standardized constructor/parser that produces a portable Path must produce the semantic value described above rather than embedding the host platform's separator, drive, UNC, device-prefix, case-folding, or current-directory rules into Path identity.

When a Filesystem maps a normal component to a concrete backend, that component is one logical child name. A backend that cannot represent that name may reject the operation, but it must not reinterpret one component as multiple components, a root/prefix change, a drive/device selector, or another authority-changing native syntax. Host-native path values that require such semantics belong behind an explicitly host-specific/native boundary.

Whether a concrete Filesystem treats two distinct normal names as referring to the same backend entry is a property of that Filesystem's namespace semantics. That lookup behavior does not change portable Path equality.

The minimum filesystem operation closed by this model is `open`. Existence queries, metadata/stat, remove, mkdir, rename, symlink operations, directory iteration, and richer namespace operations remain outside this I/O revision.

---

## 21. URL and Path

`URL` is not a subtype of `Path`.

A URL contains URL semantics such as scheme, authority, URL path, query, and fragment. A URL path component is not a filesystem `Path` merely because both use slash-like notation on some platforms.

A `file:` URL is a bridge between URL identity and filesystem naming, not an inheritance relationship.

Converting a file URL to a Path is Filesystem-dependent because platform namespace rules, UNC forms, and percent encoding may matter. Conceptually a Filesystem may provide an operation equivalent to:

```text
filesystem.pathFromURL(url)
```

The exact public spelling remains open. A non-file URL is invalid for such a conversion.

Neither a Path nor a URL grants resource-access authority.

> Resource identifiers are values. Resource access is performed through capabilities.

Network-resource access therefore belongs to separately provisioned network/HTTP/etc. capabilities rather than to ambient `url.fetch()` authority.

Relative URL references follow URL resolution semantics, not Filesystem path semantics.

Core v0.1 requires no URI/URL/URN class hierarchy or special URL literal syntax.

---

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

`Process` is a standardized runtime-backed object/capability, not a reserved word, not a language intrinsic, and not a required Core-prelude binding.

The exact bootstrap API/syntax by which the initial RootActor receives access to its Process capability remains outside this document. The semantic requirement is that Process authority is provisioned by the host/runtime rather than introduced as an ambient global variable.

The Process is the custodian of Process-local host authority. The RootActor is the initial Actor to which appropriate Process capabilities are made available.

Creating another Actor does not implicitly copy or inherit the creator's Process capability or host-capability objects.

---

## 23. Process Arguments

`process.args()` returns an immutable sequential snapshot of the application arguments supplied to the Protos execution.

The result implements the ordinary immutable sequential protocol needed here:

```text
size()
at(index)
each(block)
```

Every element is a valid Protos `String`.

The host-launcher/executable argument corresponding to facilities such as POSIX `argv[0]` is not part of this application-argument sequence.

Program/executable identity, invocation details, and host-native argv representation are outside this I/O model.

The complete portable argument snapshot is validated as one operation. If any application argument supplied by the host cannot be represented as a valid Protos String, `process.args()` fails rather than returning a partial snapshot, deferring failure to a later `at(index)`, introducing invalid Unicode, or using lossy conversion.

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

`get(name)` returns the String value when the named variable exists and is representable as valid Protos Unicode text. It returns `null` when the variable is absent. It signals an error when the variable exists but its value cannot be represented as a valid Protos String.

`contains(name)` returns whether the variable exists according to the represented environment's native name-identity rules. It does not need to decode the variable's value merely to determine existence; therefore an existing entry with a non-Unicode value still makes `contains(name)` return `true` when its name is representable and matches.

`each(block)` invokes the block with `(name, value)` String pairs. Iteration order among valid entries is unspecified.

Before the first block invocation of one `each(block)` call, the Environment validates that every entry belonging to the portable snapshot can be represented as a valid `(String, String)` pair. If any entry's name or value is not representable as a Protos String, `each(block)` signals the representation error and invokes `block` zero times for that call.

This prevalidation makes portable-representation failure atomic with respect to user callbacks: an implementation cannot expose an implementation-dependent prefix/subset of valid entries and only then discover an invalid-Unicode entry according to host enumeration order.

The rule does not require a particular physical representation or a second host-environment read. The Process Environment is already a stable snapshot; an implementation may validate eagerly when constructing that snapshot, cache validation state, retain native entries, or validate on first enumeration, provided each call has the observable behavior above.

Once representability has been established, ordinary callback behavior applies. Because iteration order remains unspecified, the order of successful callback invocations is not portable. If `block` itself signals an error or otherwise performs an ordinary non-local control effect, callbacks that already occurred before that user-code outcome are not rolled back; the prevalidation guarantee concerns Environment-to-String representation failure, not transactional execution of arbitrary Protos code.

No entry is silently omitted because it cannot be represented as String. Host-specific/native APIs may expose such entries losslessly through a separate native representation.

Environment variable name identity follows the semantics of the represented environment. In particular, a POSIX-like environment may distinguish case while a Windows-like environment may treat names case-insensitively.

The standardized portable Environment view is immutable for the Process lifetime. Core v0.1 provides no operation to mutate or reload the current Process environment.

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

Different Actors may therefore hold distinct Actor-local capability objects that ultimately refer to the same Process-local service without sharing ordinary mutable Protos object identity.

The runtime may optimize same-Process access aggressively. No observable message hop through RootActor or another ordinary Actor is required merely because Process is the semantic custodian of the capability.

Process-local standard input, when delegated to multiple Actors, denotes one underlying logical input sequence unless a stronger host capability says otherwise. Distinct Actor-local proxies for that stdin therefore share the `ByteReadable` input-consumption ordering domain rather than creating independent streams.

Each Actor's own read invocation order is preserved. Reads issued concurrently by independently progressing Actors have no predetermined cross-Actor order; routing/admission may choose either request first, but once chosen that relative order is stable and determines which request receives the next logical input. Bytes, EOF, and failures are not duplicated or reassigned merely because requests arrived through different proxies or native completions occurred in a different host order.

Delegated capability objects that denote the same standard output denote one logical output flow even when different Actors hold distinct Actor-local proxies. Each Actor's write invocation order is preserved. Writes issued concurrently by independent Actors have no predetermined cross-Actor order, but the routed output flow chooses one stable order for them; successful writes are not byte-interleaved merely because they arrived through different proxies.

This ordering does not impose a global scheduler order between Actors and does not make proxy object identity observable as an ordering primitive. It is only the serialization required by the shared output flow once competing writes reach that flow.

---

## 27. Pay-as-You-Grow

The existence of a Process, the RootActor, or a standardized Process API does not require eager construction of every I/O facility.

A runtime may lazily materialize standard-stream wrappers, encoders, proxies, routing services, buffers, or host adapters only when the relevant capability is requested or delegated.

A program that never uses stdin/stdout/stderr need not pay for Protos-level standard-stream adapters merely because the host could provide those streams.

A program that never creates another Actor need not pay for cross-Actor I/O proxy/routing infrastructure.

These optimizations are permitted only when they preserve all observable semantics in this document.

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

A future external-process facility may expose stdin/stdout/stderr pipes or other streams. Such streams use the I/O protocols defined here.

A future host-signal facility must integrate with the Actor/concurrency model rather than asynchronously executing arbitrary Protos code inside an Actor turn. The exact signal API is outside this document.

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
A failed close never returns release custody to the program and never authorizes blind native-close retry; uncertain residual backend release state remains implementation/host custody.
Object unreachability/GC never semantically invokes close; deterministic resource release requires an explicit lifecycle operation, while best-effort unreachable-resource reclamation remains non-portable implementation/host cleanup.

flush != sync != close != shutdownWrite
Invoking read/write shutdown commits permanent termination of that direction; cancellation cannot reopen it, and a failed shutdown does not create a fresh retry lifecycle.
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
Portable Path identity is structural: rootedness plus ordered components; Filesystem lookup identity, host syntax, and resource identity are separate.
URL is a value, not resource-access authority.
Filesystem carries filesystem authority.
File access mode guarantees ByteReadable/ByteWritable as requested, while ByteSeekable, ByteSized, Truncatable, and Syncable are exposed only when that backend can meet each protocol's normative contract.
Path resolution through a Filesystem is confined to that capability's authorized namespace; path syntax or backend indirection cannot escape into ambient authority.
filesystem.open may report cancelled only before any portable create/truncate effect and before File-result commitment.
filesystem.open captures its complete semantic option configuration at invocation; later mutation of an options builder/value cannot change access, creation, truncation, append, or capability outcome.
A failed committed open does not compensate by deleting an already-created target or restoring already-truncated content.

A Protos Process is an execution domain, not an OS process.
Every Protos execution has one Process and one RootActor.
Process-local facilities are provisioned by the Process host.
Process existence and unused facilities may remain lightweight/lazy.

Environment.each prevalidates complete portable String representability before invoking user code; invalid native environment text therefore cannot produce an implementation-dependent callback prefix.
Process standard streams are byte capabilities.
Text views are explicit adapters over those byte capabilities and their associated Encoding objects.

Actor creation does not implicitly inherit host capabilities.
Authority crosses Actor boundaries only through explicit provisioning/delegation consistent with Actor isolation.
```
