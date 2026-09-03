# Protos I/O Model v0.1

Language version: 0.1  
Document revision: 116
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

Multiple outstanding reads against the same logical receiver consume the sequence in invocation order.

### 5.1 ByteReadable cancellation and failure

A pending read may be cancelled only while cancellation can preserve the observable input sequence.

If cancellation wins before commitment, the operation consumes no bytes from the observable sequence.

A failed ordinary `ByteReadable.read` also consumes zero bytes from the receiver's observable input sequence. Failure is therefore not a partial-read result hidden behind a failed Future.

If an implementation has already obtained bytes from an operating-system, host, or downstream source before the Protos read is cancelled or fails, those bytes must be preserved or rebuffered whenever they belong to that read's logical input position. They remain the earliest unread bytes of the observable sequence and must be returned by later successful reads before newer source bytes that follow them.

For a receiver whose reads share a logical sequence position, cancellation or failure leaves that logical position unchanged. An implementation whose backend position advanced while obtaining bytes that are subsequently preserved must virtualize, rebuffer, reposition, or otherwise reconcile the backend so later Protos operations observe the unchanged logical position.

Pending reads are logically ordered by invocation. Cancelling or failing an earlier read does not allow a later outstanding read to consume bytes that the earlier outcome was required to preserve before those bytes are again available in logical order.

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

Cancellation before seek commitment leaves the position unchanged. Once the position change commits, cancellation cannot undo it.

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

Cancellation before commitment leaves sequence contents and size unchanged. A committed truncation cannot be undone by cancellation.

Operations whose results depend on the same sequence contents, size, or position preserve the logical invocation ordering required by the concrete receiver.

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

### 16.1 Line length limit

Core v0.1 imposes no universal arbitrary fixed line limit.

`readLine(maxBytes)` provides an explicit safety bound. `maxBytes` must be an Integer greater than zero and counts input octets belonging to the line content, excluding the line terminator.

If the line content exceeds the limit, the Future fails and no fragment is returned as a successful line.

After a line-too-long, decoding, or I/O failure, v0.1 does not guarantee that the same reader can recover and continue from a defined next-line boundary; the reader may become failed/unusable.

### 16.2 Deterministic line framing and error precedence

Line construction is determined in logical input order after decoding under the selected `Encoding`; buffering and read-ahead do not change which condition belongs to the current line operation.

For `readLine(maxBytes)`, decoding validity is established for each next encoded character before the source octets of that character are counted as line content. If decoding that character fails, the operation fails with the decoding error rather than treating malformed octets as valid content merely to reach the size limit.

After a valid non-terminator character is decoded, all source octets belonging to that character count toward the current line's content. If that makes the content count exceed `maxBytes`, the line-too-long condition is established immediately. The implementation need not read or decode later input merely to discover another possible error. An I/O or decoding failure that is encountered before the limit has been established remains the failure of the current operation.

LF terminates the current line immediately. CR also terminates the current line immediately. If the next decoded character is LF, that LF is consumed as the second character of the same CRLF terminator; otherwise it belongs to the following input. Determining whether an LF follows CR does not make the already-complete line depend on later input: EOF, an I/O failure, or a decoding failure encountered after the terminating CR is observed belongs to subsequent reading rather than retroactively failing the completed line.

An implementation that reads ahead across a completed line must preserve any following bytes, EOF state, or deferred error so that the next operation observes the same logical input sequence and failure ordering.

These rules define result/error precedence, not an implementation requirement to decode one character or perform one underlying read at a time.

### 16.3 Cancellation

A pending line read may be cancelled only before its result commits. Bytes already pulled internally must be preserved/rebuffered so successful cancellation consumes no observable input from the reader.

A line result commits when the complete result has been determined by a terminator or by EOF with remaining content.

---

## 17. TextWriter and Line Output

A standard `TextWriter(byteWritable, encoding)` layers encoding over a `ByteWritable` target.

A TextWriter may provide:

```text
writeText(text)
writeLine(text)
```

`writeLine(text)` returns a `Future` that resolves to the receiver on successful completion. It writes the text followed by LF (`U+000A`) as the canonical Protos line terminator. It does not perform platform-native newline translation.

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

A successful append-mode write updates the handle's logical position to the position after the bytes written. A later append still uses the then-current EOF rather than assuming that stored position is still the end.

A read+append handle may seek for reading. Append writes nevertheless retain append placement semantics.

The initial logical position is zero even for append mode.

The standard Protos append contract does not promise stronger non-interleaving with unrelated external writers than the backend can provide.

Writes invoked on the same receiver still preserve their required invocation ordering.

### 18.4 File capabilities by mode

A standard readable File exposes `ByteReadable`, `ByteSeekable`, `ByteSized`, and `Closable`.

A standard writable File exposes `ByteWritable`, `ByteSeekable`, `ByteSized`, `Truncatable`, and `Closable`.

A read/write File exposes the union of the corresponding capabilities.

`Syncable` is additionally exposed when the backend provides the required durability semantics.

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

It establishes an end-of-output frontier after writes invoked before it. Those writes retain the opportunity to complete, and shutdown waits behind them.

Once write shutdown begins, no new writes are accepted.

Successful completion means that the underlying resource accepted the end-of-output frontier after preceding output. It does not mean the peer application consumed the data.

There is no universal implied flush. A wrapper that exposes `WriteShutdown` must first correctly finalize/propagate its own output state before propagating shutdown to the underlying output direction.

If required preceding output fails, a pending graceful write shutdown fails rather than pretending that a clean end-of-output was established.

Write shutdown is logically idempotent.

### 19.2 ReadShutdown

`shutdownRead()` permanently terminates the receiver's willingness to receive input without closing unrelated capabilities.

Buffered or future input may be discarded by read shutdown.

After successful read shutdown, future reads return `null`.

Pending uncommitted reads may complete with `null` if shutdown wins their commitment race. A read that has already committed a `Bytes` result remains that committed read.

Remote EOF and local read shutdown are distinct events even though later ordinary reads may return `null` in both states.

Read shutdown is logically idempotent.

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

`each(block)` invokes the block with `(name, value)` String pairs. Iteration order is unspecified. The operation must not silently omit an entry merely because an entry that should be exposed through the portable view cannot be represented as String; reaching such an entry produces an error rather than silent loss.

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

Process-local standard input, when delegated to multiple Actors, denotes one underlying input sequence unless a stronger host capability says otherwise. Competing consumers are serialized/routed consistently with the capability contract; bytes are not duplicated merely because multiple Actors can request input.

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

I/O that may wait returns Future.
I/O introduces no hidden Protos suspension point.

ByteWritable.write captures its Bytes value snapshot at invocation.
Later mutation of the caller's Bytes cannot change that write.
A failed ByteWritable write contributes one contiguous prefix of its captured sequence; failure does not reveal that prefix length or make whole-write retry safe.
Pending writes remain subject to finite end-to-end admission; write snapshots do not authorize unbounded retained output.
Distinct proxies for one logical output flow share its ordering domain; concurrent successful writes are ordered as whole logical byte sequences, not byte-interleaved.


COMMITTED is an I/O-operation concept, not a Future state.
Successful cancellation before commitment preserves zero observable effect.

Capabilities are orthogonal Traits.
Wrapped capabilities do not propagate automatically.
Wrapping does not imply lifecycle ownership.
Invoking close commits permanent lifecycle termination; close itself cannot subsequently become cancelled.

flush != sync != close != shutdownWrite
A failed sync may leave an unknown subset durable, but does not itself poison the receiver; a later successful sync covers its entire later frontier without replaying logical data changes.
A failed flush never authorizes duplicate replay; an output wrapper with unknowable downstream progress becomes unusable unless a stronger protocol makes exact recovery possible.
EOF != unavailable capability != I/O failure
A failed ByteReadable read consumes zero observable bytes and leaves the logical sequence position unchanged; any bytes already obtained are preserved for later logical reading.

Path is a value, not filesystem authority.
Portable Path identity is structural: rootedness plus ordered components; Filesystem lookup identity, host syntax, and resource identity are separate.
URL is a value, not resource-access authority.
Filesystem carries filesystem authority.
Path resolution through a Filesystem is confined to that capability's authorized namespace; path syntax or backend indirection cannot escape into ambient authority.
filesystem.open may report cancelled only before any portable create/truncate effect and before File-result commitment.
A failed committed open does not compensate by deleting an already-created target or restoring already-truncated content.

A Protos Process is an execution domain, not an OS process.
Every Protos execution has one Process and one RootActor.
Process-local facilities are provisioned by the Process host.
Process existence and unused facilities may remain lightweight/lazy.

Process standard streams are byte capabilities.
Text views are explicit adapters over those byte capabilities and their associated Encoding objects.

Actor creation does not implicitly inherit host capabilities.
Authority crosses Actor boundaries only through explicit provisioning/delegation consistent with Actor isolation.
```
