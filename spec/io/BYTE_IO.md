# Protos Byte I/O v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Bytes-facing binary I/O protocols, reading, writing, flushing, seeking, sizing, truncation, durability, and directional shutdown.

The modular I/O specification consists of `IO_CORE.md`, `BYTE_IO.md`, `TEXT_IO.md`, `FILESYSTEM.md`, and `PROCESS_IO.md`. Legacy section numbers from `IO_CORE.md` and the applicable sibling I/O module are intentionally retained so historical citations remain understandable. Normative ownership now belongs to these modules; the former monolithic file is removed by revision 326.

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

The exact programmer-visible semantics of those standard Bytes operations,
including zero-argument empty construction, exact octet validation,
replacement-only `atPut`, dynamic resizing through `add` / `removeAt`,
ascending-snapshot `each`, callback callability, return values, and
open/closed/frozen behavior, are governed normatively by
`../semantics/VALUES_AND_COLLECTIONS.md`.

`Bytes` remains mutable and dynamically resizable through the explicit
length-changing operations `add` and `removeAt`. No other operation acquires
implicit resizing semantics merely because it is indexed.

The concurrency model's P-local `ByteRegion` is an authority capability, not an
I/O resource and not an ordinary transferable `Bytes` value. I/O APIs do not
implicitly acquire its parent-region authority merely because they accept byte
values elsewhere.

No sparse `Bytes` representation is observable.

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

`null` means end-of-file/end-of-sequence for the receiver's current sequence state. For a receiver whose sequence state cannot subsequently gain readable data, that EOF is permanent. For a receiver that explicitly represents a mutable sequence whose contents/extent may change, a later state change may make data readable again according to that concrete receiver's contract.

For `read(maxBytes)` with `maxBytes > 0`, an empty `Bytes()` result is never used to mean either "no data yet" or EOF. If data is not yet available and EOF has not been established, the Future remains pending.

A read may complete with fewer octets than requested.

`maxBytes` is a maximum successful-result size, not a minimum fill requirement. Once an ordered read has reached a state in which at least one next logical octet can be returned without waiting for additional source/backend progress, that read must not remain pending solely to accumulate more octets toward `maxBytes`. It may resolve with any non-empty prefix of the then-returnable logical input whose length is at most `maxBytes`, subject to the receiver's stronger concrete contract.

This rule is about avoiding implementation-selected extra waiting after useful input is already available to the Protos operation. It does not require polling, busy waiting, one native read per Protos read, or a portable host-level `available()` byte-count API. An implementation may issue a backend/native read whose own completion waits for source progress; once such work has made one or more octets available to satisfy the earliest ordered Protos read, the implementation cannot withhold that read's result merely to seek a fuller buffer.

Likewise, implementation-controlled buffering or read-ahead that already contains unread logical input makes that input available to the earliest ordered read; the implementation must not pretend the buffer is empty merely because it prefers a larger chunk. A stronger protocol may explicitly define an exact-fill or delimiter/framing operation, but ordinary `ByteReadable.read(maxBytes)` does not.

EOF or an I/O failure that becomes established before any octet is returnable for the earliest ordered read retains its ordinary precedence.

If one or more logical octets precede a later EOF or I/O failure and are already returnable for that read under the receiver's concrete semantics, those octets take precedence over the later terminal condition. Ordinary `read(maxBytes)` must return a non-empty prefix of that preceding logical input rather than exposing the later EOF or failure first merely because implementation buffering, native batching, prefetch, or read-ahead discovered the later condition before the Future was resolved.

The implementation remains free to choose any permitted non-empty chunk boundary up to `maxBytes`. It need not return every byte preceding the later condition in one result. However, every logical octet that precedes that EOF/failure must remain ahead of that condition in the input order and must be made available to successive ordered reads before the later EOF/failure becomes the outcome of an applicable read.

A later I/O failure discovered while reading ahead beyond already-returnable bytes is therefore preserved/deferred as logical receiver error state until all preceding logical bytes have been delivered. Merely discovering that later failure does not make the current read a failed read and does not invoke the failed-read zero-consumption/rebuffering rule for bytes that already precede the failure in logical input order.

When the preserved later failure reaches the head of the logical input/error order — that is, no preceding logical octet remains to be delivered for the applicable ordered read — it becomes that read's failure outcome and is reported exactly once under the ordinary ByteReadable failure rules. After it has actually been reported, the existing rule that an already-reported error is not automatically replayed remains unchanged.

The same ordering rule applies to EOF: data that logically precedes EOF is delivered before `null`, while EOF established before any octet is returnable retains precedence. Thus implementation-selected read-ahead depth may change successful chunk boundaries but cannot change the relative observable order of preceding bytes versus a later EOF or I/O failure.

For a readable standard `File`, bytes between the current logical position and the file size applicable at the read's ordered evaluation point are immediately returnable file content: a read whose position is before that EOF must not remain pending merely to fill `maxBytes` or wait for later file growth. It returns a non-empty prefix up to `maxBytes` of the bytes then readable from that position, subject to an independently established I/O failure. At current EOF it follows the existing `null` rule rather than waiting for hypothetical growth.

Ordinary ByteReadable progress therefore requires completion once useful input for that read is already returnable; `read(maxBytes)` may return short, and no implementation may reinterpret the requested maximum as an exact-fill threshold.

I/O errors fail the Future and are distinct from EOF.

If data remains before EOF, that data is returned before EOF is reported. Once EOF has been observed, subsequent reads return `null` while the receiver remains in the same relevant sequence state. A seek is one explicit way to change that state; a concrete mutable-sequence receiver may also specify that independently authorized changes to its represented sequence can make later data readable without a seek. A concrete receiver must define that distinction rather than inheriting accidental host EOF-latching behavior.

Multiple outstanding reads against the same logical receiver share one input-consumption ordering domain.

When reads have a Protos-defined invocation order, that order is preserved. In particular, reads issued sequentially by one Actor consume/evaluate the input sequence in that Actor's invocation order even when earlier read Futures remain pending.

Reads that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe routing/proxies have no predetermined relative order merely from host scheduling. The receiver/routing layer may choose either read first when admitting the competing requests. Once it chooses their relative order, that order is stable: the chosen earlier read owns the next logical input opportunity according to its own `read(maxBytes)` semantics, and a later chosen read cannot bypass it to consume bytes that precede the earlier read's result.

The chosen order concerns logical input consumption, not native syscall start/completion order. Implementations may overlap host operations, prefetch, route, or buffer internally when those mechanisms cannot change which ordered Protos read receives each byte, EOF, or failure.

### 5.1 ByteReadable cancellation and failure

A pending read may be cancelled only while cancellation can preserve the observable input sequence.

If cancellation wins before commitment, the operation consumes no bytes from the observable sequence.

A failed ordinary `ByteReadable.read` also consumes zero bytes from the receiver's observable input sequence. Failure is therefore not a partial-read result hidden behind a failed Future.

If an implementation has already obtained bytes from an operating-system, host, or downstream source before the Protos read is cancelled or fails, those bytes must be preserved or rebuffered whenever they belong to that read's logical input position. They remain the earliest unread bytes of the observable sequence and must be returned by later successful reads before newer source bytes that follow them.

An error already reported by the failed read is not itself an unread stream element and is not automatically replayed after those preserved bytes are later returned. The failed Future is the portable observation of that read's error outcome. After preserved bytes have been consumed in logical order, a later read is evaluated against the receiver's then-current protocol/backend state and fails only if that later operation independently encounters or inherits an error required by the concrete receiver's contract.

Therefore an implementation must not synthesize a second failure merely to reproduce the same lower-level error that already caused the earlier Protos read Future to fail. Conversely, preserving bytes does not suppress a distinct or persistent backend error: if the receiver's actual state still requires a later read to fail, that later failure is reported normally.

This rule deliberately separates byte-sequence preservation from error-event replay. It prevents two implementations from disagreeing solely because one models a previously reported host error as a queued input event while another treats it as the already-consumed outcome of the failed Protos operation.

A failed ByteReadable read reports its error once as that operation's outcome; preserved bytes are replayed as input, but the already-reported error is not automatically replayed after them unless the later receiver state independently requires another failure.

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

For a write that is admitted as an outstanding output operation, `write(bytes)` captures a logical snapshot of the argument's size and octet contents during that invocation, before the call returns a pending/successful Future or begins host/native I/O. Later mutation of the supplied `Bytes` does not change the byte sequence belonging to that admitted write, and each admitted write invocation captures its own logical snapshot.

This is a semantic snapshot, not a requirement for eager physical copying. An implementation may use immutable backing storage, copy-on-write, reference retention, scatter/gather I/O, or another representation strategy provided that ordinary later mutation of the caller's `Bytes` remains valid and cannot change the captured write sequence. `write` does not impose a hidden caller-visible borrow, freeze, pin, or "do not mutate until completion" lifetime rule.

Because the API is non-blocking at invocation and the caller may mutate or discard the original `Bytes` immediately after the call returns, preserving an arbitrary number or volume of outstanding snapshots cannot be achieved by backpressure alone. Therefore admission includes obtaining whatever finite implementation-managed retention/reservation is required to preserve that write's snapshot until the operation no longer needs it.

If that finite admission capacity is unavailable, the implementation may reject the write before admitting it as output work and return an already-failed Future representing resource/capacity exhaustion. Such rejection is an explicitly host/implementation-resource-dependent failure, not an implementation-selected change to the write's bytes. It contributes zero output bytes, creates no flush/sync/shutdown frontier contribution, changes no logical position or sequence state, and does not poison or close an otherwise usable receiver merely because capacity was temporarily unavailable.

A capacity-rejected write need not retain an enduring snapshot after its failed Future outcome has been established. The implementation must nevertheless complete ordinary argument validation before reporting capacity exhaustion where those validation results are already determined by the supplied semantic values; resource pressure cannot be used to turn an invalid `write` argument into a different portable argument-validation outcome.

An implementation must not instead block or suspend the Protos caller inside `write()` waiting for snapshot capacity, must not return a pending Future whose promised snapshot it has no bounded way to preserve, and must not borrow/freeze/pin the caller's mutable `Bytes` until capacity becomes available. If the write is admitted, the snapshot guarantee is unconditional for that operation.

Snapshot capture/admission does not itself commit output. An admitted write whose cancellation wins before the I/O commitment boundary still contributes zero bytes to the observable output sequence.

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

A logical output flow must have an effective finite bound on admitted write work whose retained state, including any state needed to preserve invocation snapshots, has not progressed far enough to be released. The bound may be fixed, adaptive, receiver-specific, or imposed by downstream capacity; its numeric value is not portable Protos behavior.

Backpressure propagates through pending Futures only for writes whose snapshot/admission state has actually been retained within that finite bound. Once no bounded retention/reservation is available for another invocation, the implementation uses the capacity-rejection rule above rather than accumulating an unbounded pre-admission snapshot queue or silently suspending the caller inside `write()`.

This rule is end-to-end across adapters, Actor-safe proxies, routing layers, and native/backend buffering controlled by the Protos implementation. No intermediate Protos-managed queue may grow without bound merely because another layer has not yet applied pressure.

The rule does not impose a one-write-at-a-time protocol. Implementations may admit and overlap multiple writes, batch them, coalesce representation, or use bounded pipelining while preserving invocation ordering, snapshot semantics, cancellation/commitment rules, and the receiver's observable output contract.

A program that intentionally issues writes without waiting may therefore accumulate Future objects, including already-failed capacity-rejection Futures, but portable semantics do not require the runtime to preserve an unbounded amount of admitted output payload state. Program-held values remain subject to ordinary reachability/lifetime rules; the I/O delivery path itself retains only the bounded admitted state required by the operations it accepted.


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

The frontier is a logical position in the ordered output flow. Writes and `flush()` operations that target the same logical output flow are ordered relative to that frontier rather than by host/native completion timing.

When a write and `flush()` have a Protos-defined order, that order is preserved. A write ordered before a flush belongs to that flush frontier even if that write has not yet committed any output when the flush is invoked or begins backend work. The flush cannot complete successfully until the terminal aftermath of every such preceding write is known and every byte contribution established by that aftermath has been propagated through the receiver-controlled buffering covered by the flush.

Accordingly, a preceding write that later succeeds contributes its complete captured sequence to the flush frontier; a preceding write that later fails contributes exactly its permitted contiguous prefix, if any; and a preceding write whose cancellation wins contributes zero bytes. A successful flush may drive, wait for, or otherwise compose with those earlier writes internally, but it cannot resolve first and then allow an earlier-frontier write to create newly committed output behind the already-completed propagation frontier.

This requirement concerns the write's terminal semantic aftermath, not merely its Future completion implementation mechanics. The implementation need not execute one operation at a time, but before reporting flush success it must know which output, if any, every preceding-frontier write contributed under its ordinary success/failure/cancellation contract and must have propagated that contribution to the flush boundary.

A failure of a preceding write does not automatically require the flush itself to fail merely because that earlier write Future failed. If the receiver remains usable and the exact committed prefix from that failed write is internally known well enough to satisfy the ordinary flush contract, the flush may successfully propagate that prefix together with the other preceding output. Conversely, if the receiver's state after the write failure prevents the flush contract from being established, the flush fails under its ordinary receiver/error semantics. Portable code does not infer the earlier write's hidden prefix length from the later flush outcome.

For a write and `flush()` that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe proxies, Protos defines no predetermined cross-Actor arrival order. Routing/admission may choose either request first. If the write is admitted before the flush frontier, it belongs to that flush frontier and the same terminal-aftermath rule applies; if the flush frontier is established first, the competing write is later output and is not covered by that flush. Once the relative order is chosen, host scheduling cannot retroactively move the write across the frontier.

A successful flush therefore establishes the required propagation frontier for exactly the output contribution of every write logically ordered before it, including contributions whose commitment was still pending when the flush was invoked. It does not establish a global memory-ordering or synchronization relation between unrelated Actors, and it does not force writes ordered after the frontier to wait for the flush unless their own output ordering or a stronger protocol requires that relationship.

Successful completion means that all output contributed by operations belonging to the flush frontier has reached the receiver's defined underlying output boundary through buffering controlled by that receiver. No operation already inside the frontier may later contribute additional output that was absent from the successful flush.

A flush frontier is ordered within one logical output flow: writes ordered before it are covered, writes ordered after it are not, and genuinely concurrent cross-Actor write/flush requests are stably ordered by routing/admission.

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

When a receiver exposes `ByteReadable` and `ByteSeekable` over the same logical sequence position, a successful non-EOF `read(maxBytes)` starts at the logical position applicable at that read's ordered evaluation point. If the returned `Bytes` contains `n` octets, the read consumes exactly those next `n` logical octet positions and the receiver's logical position after the read is the starting position plus `n`. The position advances by the actual successful result length, not by `maxBytes`, a native/backend read size, or an implementation-selected buffer extent.

A `null` EOF result consumes zero octets and leaves the logical position unchanged. Therefore repeatedly reading at the same unchanged EOF position continues to observe that position until a seek or a concrete mutable-sequence state change makes data readable there according to that receiver's contract.

The existing cancellation/failure rule remains failure-atomic for position: a successfully cancelled or failed ordinary read consumes zero logical octets and leaves the shared logical position unchanged even if backend read-ahead occurred and had to be preserved internally.

This position aftermath is determined by the Protos read result, not by native cursor movement. An implementation may prefetch, use positional I/O, virtualize a cursor, or obtain more backend bytes than it returns in one Protos read, but only the octets delivered by that successful read advance the receiver's observable logical position. Preserved read-ahead remains unread logical input.

Independently authorized changes to a mutable underlying resource do not alter this arithmetic. They may affect which bytes are visible to the read under the resource/backend semantics, but once a successful read returns `n` octets from starting logical position `p`, that operation's position aftermath is exactly `p + n`.

A successful position-sharing ByteReadable read advances the receiver's logical position by exactly the number of octets returned; EOF, cancellation, and ordinary read failure advance it by zero.

When a `ByteWritable` write on such a receiver fails after contributing a prefix of length `k`, the shared logical position advances exactly by the committed contribution that the receiver's positioned-write semantics place into the sequence. A later ordered position-sensitive operation therefore starts from the post-prefix logical position, not from the pre-write position merely because the write's Future failed.

`position()` is asynchronous for uniform no-hidden-suspension semantics even when a concrete implementation can answer immediately.

`position()` is an observation-only I/O query. Its operation commits only when its non-negative Integer result or its failure outcome has been determined for that query. Before that commitment, cancellation may win and the Future becomes `cancelled`. A successfully cancelled `position()` contributes no logical-position change, no sequence-state effect, no ordering frontier beyond reaching its own terminal cancellation outcome, and no Protos-visible position observation.

Because `position()` is read-only, implementation/backend work performed while answering it does not create an irreversible Protos sequence effect that by itself makes cancellation lose. If host/backend query work cannot be physically stopped after cancellation, that residual work remains under implementation/runtime custody: its eventual value or error is discarded, cannot resurrect or re-complete the cancelled Future, and cannot later modify the receiver's logical position or sequence state.

The query remains an ordinary member of the receiver's sequence-state ordering domain until it reaches a terminal Future state. Successful cancellation is itself such a terminal outcome. A later ordered sequence-state operation therefore observes the aftermath of all earlier operations that actually changed state, but there is no additional state transition or observation frontier contributed by the cancelled `position()` query merely because backend work for it may still be unwinding internally.

Once a `position()` result or failure has committed, cancellation cannot replace that terminal outcome.

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

`size()` is an observation-only I/O query with the same cancellation shape as `position()`. Its operation commits only when the non-negative Integer size result or its failure outcome has been determined for that query. Before that commitment, cancellation may win and the Future becomes `cancelled`.

A successfully cancelled `size()` contributes no sequence-content, size, logical-position, durability, propagation, or lifecycle effect and establishes no observation/frontier that later ordered operations must preserve beyond the fact that the query itself reached a terminal cancelled state. Later ordered sequence-state operations are evaluated from the aftermath of the other operations in that ordering domain.

Backend/native metadata work already started for a cancelled `size()` may finish only as residual implementation/runtime work when it cannot be physically stopped safely. Its eventual value or error is discarded and cannot re-complete the cancelled Future, mutate the represented sequence, or retroactively constrain which sequence state a later Protos query/change observes under the existing ordering rules.

Once a `size()` result or failure has committed, cancellation cannot replace that terminal outcome.

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

The sync frontier is a logical position in the receiver's sequence-state ordering domain. Receiver changes and `sync()` operations that participate in that same domain are ordered relative to the frontier rather than by host/native start or completion timing.

When a receiver change and `sync()` have a Protos-defined order, that order is preserved. A change ordered before the sync belongs to that durability frontier: successful sync completion cannot omit the durable effect required for that change merely because its Future was still pending in an implementation queue when `sync()` was invoked. A change ordered after the frontier is not required to be durable when that sync completes.

For a receiver change and `sync()` that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe proxies for the same logical receiver, Protos defines no predetermined cross-Actor arrival order. Routing/admission may choose either operation first. If the change is admitted before the sync frontier, it belongs to that frontier; if the sync frontier is established first, the competing change is later state and is outside that sync. Once the relative order is chosen, host scheduling cannot retroactively move the change across the durability frontier.

A successful sync therefore covers exactly the receiver changes logically ordered before its frontier under the receiver's existing sequence-state semantics. It does not create a global durability barrier across independently opened Files merely because they alias the same underlying resource, does not establish a general Actor memory-ordering relation, and does not make later independent changes part of the completed frontier.

A backend may durably persist later or independently originated changes as a side effect of its synchronization mechanism. Such extra persistence is not a portable guarantee of that sync and cannot be used to infer a Protos ordering relation that the logical receiver did not define.

A sync frontier is ordered within one receiver's sequence-state domain: changes ordered before it are covered, later changes are not required to be durable, and genuinely concurrent cross-Actor change/sync requests are stably ordered by routing/admission.

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

`shutdownWrite()` has a receiver-visible output cutover point: the irreversible transition into `write-shutting-down`. Writes and write shutdown that target the same logical output direction are ordered relative to that cutover rather than by host/native completion timing.

When a write and `shutdownWrite()` have a Protos-defined order, that order is preserved. In particular, a write issued earlier by one Actor on the same logical output flow is admitted before a later shutdown from that Actor even when the write Future is still pending. That accepted write retains the opportunity to complete, and shutdown waits behind it.

A write ordered after the write-shutdown cutover is not accepted as output work: it fails under the receiver's write-shut-down lifecycle and contributes zero bytes. It does not race the shutdown by starting a later native write merely because backend scheduling happens to run it first.

For a write and shutdown request that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe proxies, Protos defines no predetermined cross-Actor arrival order. Routing/admission may choose either request first. If the write is admitted first, it becomes preceding accepted output and shutdown waits behind it; if shutdown establishes the cutover first, the competing write is rejected with zero contribution. Once that relative order is chosen, host scheduling cannot retroactively move the write across the cutover.

The cutover is a logical output-direction property shared by proxies that denote that same direction. It does not require one native syscall at a time and does not make proxy object identity an ordering primitive.

Successful completion means that every required preceding output operation reached the outcome required for a clean frontier and that the underlying resource accepted the end-of-output frontier after that preceding output. It does not mean the peer application consumed the data.

A preceding `ByteWritable.write` whose cancellation wins under the ordinary write-cancellation contract satisfies the clean-frontier requirement with zero contribution. `shutdownWrite()` waits until that preceding write has reached its terminal `cancelled` state, but it does not fail merely because the write was cancelled: successful write cancellation already guarantees that no byte from that write entered the logical output sequence.

This rule applies equally when routing/admission placed a genuinely concurrent cross-Actor write before the shutdown cutover and that admitted write is subsequently cancelled successfully. Once the cancellation outcome is terminal, there is no hidden output contribution left for shutdown to await or propagate from that write.

Cancellation is deliberately distinct here from write failure. A failed preceding write remains a failed required output operation even when its hidden committed prefix length happens to be `k = 0`; under the existing WriteShutdown rule, such failure causes shutdown to fail rather than being reclassified as a clean cancellation. Portable code therefore cannot infer or substitute write-failure progress from this cancellation rule.

Likewise, a cancellation request that loses because the preceding write has already committed does not turn that write into zero-contribution output. The write proceeds to its ordinary success/failure aftermath, and `shutdownWrite()` composes with that actual terminal outcome.

When one logical output direction exposes both `Flushable` and `WriteShutdown`, `flush()` participates in that same output-direction lifecycle and is ordered relative to the write-shutdown cutover. This does not make `flush()` a write and does not introduce a universal implied flush.

A `flush()` operation admitted before the `shutdownWrite()` cutover remains a preceding accepted output operation. Shutdown does not overtake, reject, or retroactively move that flush to the post-shutdown side merely because the flush Future is still pending. The flush retains its ordinary success/failure/cancellation semantics, and `shutdownWrite()` proceeds only after that preceding flush has reached its terminal aftermath required by the existing clean-frontier/output-lifecycle rules.

A `flush()` ordered after the write-shutdown cutover is not accepted as new output-direction work. It fails under the receiver's write-shut-down lifecycle and establishes no new propagation frontier. In particular, successful write shutdown does not leave `flush()` as a separately usable post-shutdown control operation, and a later flush is not converted into a successful no-op merely because no new writes can be accepted.

For a `flush()` and `shutdownWrite()` that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe proxies for the same logical output direction, Protos defines no predetermined cross-Actor arrival order. Routing/admission may choose either request first. If flush is admitted first, it is the preceding operation described above; if shutdown establishes the cutover first, the competing flush is rejected. Once that relative order is chosen, host/backend scheduling cannot move the flush across the cutover.

This rule fixes admission and ordering only. It does not redefine the ordinary aftermath of an already-admitted flush, does not infer hidden propagation progress, and does not by itself make every preceding flush failure permanently fatal to the output lifecycle. Recovery is governed by the `Flushable` progress rules below.

A failed preceding `flush()` is distinct from a failed preceding `ByteWritable.write`. The failed flush Future remains failed and its already-permitted partial propagation is not rolled back, but `Flushable` already permits a receiver that retains exact internal propagation progress to continue from the unpropagated remainder with a later ordered flush.

For `shutdownWrite()` clean-frontier purposes, such a failed flush is repaired only if, before the end-of-output frontier is established, a later ordered `flush()` completes successfully with a propagation frontier that covers all output belonging to the earlier failed flush's frontier. That later successful flush proves the required propagation state for the covered output; it does not rewrite the earlier flush Future as successful, erase its failure event, or expose how far the earlier flush had progressed.

If output ordered after the failed flush also belongs to the later successful flush frontier, that later flush may establish one combined recovered propagation frontier for all of that covered output. `shutdownWrite()` waits for the recovering flush's terminal success before relying on that frontier.

A failed `ByteWritable.write` is not repaired by this rule. Its Future remains a failed required output operation under the existing WriteShutdown rule even if a later flush can propagate the write's known committed prefix. Likewise, a later flush that itself fails, is cancelled before establishing the needed propagation frontier, or does not cover the failed flush's required frontier does not repair that earlier flush for shutdown purposes.

`shutdownWrite()` does not itself implicitly retry or replay a failed flush merely because recovery would have been possible. If no later successful ordered flush establishes the required recovered frontier, a preceding failed flush remains an unsatisfied required propagation operation and causes shutdown to fail under the rule below.

There is no universal implied flush. A wrapper that exposes `WriteShutdown` must first correctly finalize/propagate its own output state before propagating shutdown to the underlying output direction.

If required preceding output remains failed or otherwise lacks the clean frontier required above, or if establishment of the end-of-output frontier itself fails, the shutdown Future fails rather than pretending that a clean end-of-output was established. A preceding failed flush whose required propagation frontier was subsequently covered by a later successful flush is no longer an unsatisfied propagation failure for this shutdown decision; a failed write remains failed as specified above. The output direction remains permanently unavailable to new writes; failure never reopens it.

Write shutdown is logically idempotent. Calls made while shutdown is pending observe the same lifecycle rather than beginning independent shutdown attempts. After successful shutdown, later calls succeed without establishing another end-of-output frontier. After failed shutdown, later calls fail consistently with that failed lifecycle and do not retry the frontier or reopen output. Each invocation returns a fresh standard Future under the idempotent-lifecycle Future-identity rule in `IO_CORE.md`.

Input capability and input lifecycle remain unaffected except where a stronger concrete protocol explicitly couples them.

### 19.2 ReadShutdown

`shutdownRead()` permanently terminates the receiver's willingness to receive input without closing unrelated capabilities.

Invoking `shutdownRead()` is itself the irreversible commitment boundary for the input-direction lifecycle. Before the returned Future is observable, the receiver has entered a permanent read-shutting-down state. That transition cannot be rolled back, so the shutdown Future cannot subsequently become `cancelled`. Cancellation of an activation waiting for it does not cancel or reopen the committed shutdown lifecycle.

Buffered or future input may be discarded by read shutdown. Once read shutdown begins, no new read is accepted as an ordinary data-producing operation.

Read shutdown has a receiver-visible cutover point: the irreversible transition into `read-shutting-down`. The outcome of every read competing with shutdown is determined relative to that cutover, not by which host/native completion callback happens to run first.

A read whose `Bytes`, EOF, or error result committed before the read-shutdown cutover retains that committed result. A read accepted before shutdown but still uncommitted at the cutover completes with `null`; it does not remain pending indefinitely, consume/disclose buffered input after the cutover, or become `cancelled` merely because shutdown occurred. Thus invoking `shutdownRead()` while an earlier read Future is still pending intentionally terminates that uncommitted read rather than waiting behind it as `shutdownWrite()` waits behind preceding writes.

Cancellation of that competing read is ordered by the same commitment boundary rather than by host callback timing. If the read's ordinary cancellation contract has already won and committed the Future to `cancelled` before the read-shutdown cutover reaches that read, the read remains `cancelled`; shutdown does not rewrite an already-terminal cancellation into `null`.

Conversely, if the read is still uncommitted and not already terminally cancelled when the read-shutdown cutover reaches it, the shutdown-induced `null` outcome commits at that cutover. A cancellation request that arrives or would otherwise complete afterward loses: it cannot replace that committed `null` with `cancelled`, cannot resurrect the read as pending, and cannot restore discarded input.

For a cancellation request and `shutdownRead()` that are genuinely concurrent because they progress independently, Protos defines no predetermined wall-clock winner. Their observable outcome is determined by which semantic event commits first under the existing read-cancellation and read-shutdown rules: terminal cancellation first yields `cancelled`; shutdown cutover first while the read is still uncommitted yields `null`. Once either outcome has committed, host/native completion scheduling cannot rewrite it.

This rule introduces no extra read effect or restoration requirement. Successful cancellation still preserves the ordinary zero-consumption guarantee; shutdown winning still permits the existing discard behavior for uncommitted internal/native read-ahead. The distinction fixes only which terminal Future outcome owns the race.

For operations issued through independently progressing Actors or Actor-safe proxies, routing/admission may determine whether a competing read commits before the shutdown request reaches the receiver's cutover. Protos defines no global cross-Actor arrival order. Once the receiver has established the cutover, however, host scheduling cannot retroactively move an uncommitted read to the pre-shutdown side or expose bytes that were discarded by shutdown.

Internal/native reads may already have obtained bytes before cutover without having committed a Protos read result. Those bytes may be discarded as part of read shutdown; they must not later escape through the terminated receiver merely because backend work completed before or after the shutdown call. No restoration to a separately accessible underlying source is implied unless a stronger wrapper/source protocol explicitly provides it.

After read shutdown begins, later ordinary reads return `null`, including while the shutdown Future is still pending. This local EOF-like result reflects the receiver's terminated input direction and does not claim remote EOF.

If underlying/backend work required to establish or release the read-shutdown state fails, the shutdown Future may fail, but the input direction remains permanently unavailable for ordinary reading; failure never reopens it.

Read shutdown is logically idempotent. Calls made while shutdown is pending observe the same lifecycle. After successful shutdown, later calls succeed without performing another shutdown. After failed shutdown, later calls fail consistently with that failed lifecycle and do not start a fresh shutdown attempt. Each invocation returns a fresh standard Future under the idempotent-lifecycle Future-identity rule in `IO_CORE.md`.

Remote EOF and local read shutdown are distinct events even though ordinary reads after either condition may return `null`.

Output capability and output lifecycle remain unaffected except where a stronger concrete protocol explicitly couples them.

### 19.3 Whole-resource close

`close()` closes the whole resource according to its lifecycle contract. It is not normatively defined as the composition `shutdownRead()` plus `shutdownWrite()`.

Core v0.1 defines no separate universal `shutdownBoth()` operation.

---
