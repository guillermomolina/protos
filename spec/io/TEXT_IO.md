# Protos Text I/O v0.1

Language version: 0.1
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of Encoding, TextReader, line framing, and TextWriter semantics.

The modular I/O specification consists of `IO_CORE.md`, `BYTE_IO.md`, `TEXT_IO.md`, `FILESYSTEM.md`, and `PROCESS_IO.md`. Legacy section numbers from `IO_CORE.md` and the applicable sibling I/O module are intentionally retained so historical citations remain understandable. Normative ownership now belongs to these modules; the former monolithic file is removed by revision 326.

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

For a standard `TextReader`, `readText()` is a progress-oriented chunk read, not an exact-fill, delimiter-seeking, or read-all operation. Once the earliest ordered `readText()` has a non-empty prefix of decoded logical text that can be returned without waiting for additional source/backend progress, that operation must not remain pending solely to accumulate a larger implementation-selected String chunk.

A returnable decoded prefix contains only complete valid Protos String text. Bytes that form only an incomplete encoded character, or valid decoder-control/state transitions that have not yet produced text, do not by themselves require a successful `readText()` result; the reader may remain pending until text, EOF, or an error becomes established. Conversely, decoded text already retained in implementation-controlled buffering is available to the earliest ordered `readText()` and cannot be hidden merely because the implementation prefers a larger chunk.

The exact non-empty chunk boundary remains implementation-selectable. A reader may return one Unicode scalar or a larger finite prefix, may split an extended grapheme cluster as already permitted, and may use bounded read-ahead or decoder batching. That freedom changes chunking only; it does not permit implementation-selected extra waiting after useful decoded text is already returnable.

For a standard `TextReader`, an I/O or decoding error that belongs logically after one or more already-returnable decoded characters does not retroactively erase those preceding characters merely because buffering/read-ahead discovered the later error before the implementation happened to resolve the current Future. `readText()` returns a non-empty prefix of the valid decoded text that precedes that error, and the later error is preserved/deferred in the reader's logical input/error state for a subsequent ordered text-reading operation after all preceding decoded text has been delivered.

Therefore implementations may choose different permitted chunk boundaries before the same later error, but they must expose the same concatenated valid decoded prefix before that error becomes the outcome of a text-reading operation. A decoding or I/O error established before any text is returnable for the earliest ordered `readText()` remains that operation's failure and follows the ordinary permanent TextReader failure lifecycle.

This rule does not alter `readLine()` framing. A line operation still waits until its complete line result, EOF-final line, or defined failure is established, and an error before that line's terminator can fail the line even when some non-terminating text has already been decoded. `readText()` and `readLine()` continue to share one ordered decoder/input domain.

Ordinary `readText()` therefore completes once useful decoded text is returnable, while preserving later errors in logical order instead of letting implementation chunk size or read-ahead move those errors ahead of already-decoded text.

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

The `Encoding` object is the canonical receiver for the standard
one-shot conversion operations. Core v0.1 does not additionally require
`String.encode(encoding)` or `Bytes.decode(encoding)` convenience messages.
Libraries may expose such ordinary conveniences, but they are not part of the
portable Core one-shot encoding contract.



`Encoding` is a standardized immutable/reusable descriptor/configuration abstraction and a standard frozen-prelude factory/prototype binding; it carries no I/O authority. The mandatory portable descriptors are obtained exactly as `Encoding.UTF8`, `Encoding.UTF16LE`, `Encoding.UTF16BE`, and `Encoding.Latin1`. Core v0.1 defines no String-name registry lookup, alias lookup, host codec discovery, or `Encoding("utf-8")` constructor. Host-provided additional Encoding values may be supplied explicitly by another capability.

A Process host may additionally provide an `Encoding` object describing a host-selected standard-stream encoding even when that encoding is not one of the portable named values above.

Per-flow encoder and decoder state is independent and may be stateful. Reusing an `Encoding` descriptor does not mean sharing one mutable encoder/decoder state across flows.

The standardized one-shot operations are conceptually:

```text
encoding.encode(text)   -> Bytes
encoding.decode(bytes)  -> String
```

The Encoding receiver is the canonical standard one-shot dispatch direction.
Core v0.1 does not additionally require reciprocal `String.encode(encoding)` or
`Bytes.decode(encoding)` convenience messages. Implementations may expose such
ordinary library conveniences only when they do not change the normative
Encoding-operation semantics; portable Core code must use the Encoding-object
contract unless another standard explicitly says otherwise.


They are in-memory operations and do not return Futures.

Every successful `encoding.encode(text)` result is a standard Bytes-producing
result governed by `../semantics/VALUES_AND_COLLECTIONS.md`. Each invocation
therefore returns its own fresh open Bytes identity and independent mutable
state, including when the encoded result is empty; it is not mutable state of
the reusable Encoding descriptor or an implementation-controlled encoder
buffer.

For the standard one-shot operations, `encoding.encode(text)` requires `text` to be a Protos `String`, and `encoding.decode(bytes)` requires `bytes` to be a Protos `Bytes` value. These are exact semantic argument domains, not conversion requests.

A non-`String` argument to `encode` and a non-`Bytes` argument to `decode` fail synchronously under ordinary argument/type-validation semantics before any encoding or decoding work is performed. Because these operations are explicitly synchronous/non-Future, such validation does not manufacture a failed Future.

Standard one-shot conversion performs no implicit `toString`-style conversion, character/numeric collection conversion, byte-array adaptation, host-buffer coercion, duck-typed extraction, or other implementation-selected conversion merely because the supplied value could in principle be transformed into text or octets. Libraries may expose separate explicit conversion/adaptation facilities, but those facilities are not part of the standard `Encoding.encode` / `Encoding.decode` operation.

This strict argument-domain rule is independent of the selected Encoding's malformed-input, representability, replacement, or BOM policy. Those policies are evaluated only after a valid `String` or `Bytes` argument has been established.

One-shot decoding treats an incomplete final encoded sequence according to the selected decoding error policy. Under the default strict/fatal policy it is an error. Under an explicitly selected replacement policy, an incomplete final subsequence is malformed final input and is handled by the replacement rule below rather than being unconditionally fatal.

### 15.1 Error policy

Decoding is strict/fatal by default.

A decoding configuration may explicitly request replacement of malformed input with U+FFFD. Core v0.1 defines no ignore-malformed-input policy.

For the portable `UTF8`, `UTF16LE`, and `UTF16BE` encodings, replacement decoding is deterministic: each maximal subpart of an ill-formed subsequence, using the Unicode Standard 17.0.0 section 3.9.6 maximal-subpart definition, is consumed and replaced by exactly one U+FFFD. `Latin1` has no malformed octet sequence because every octet maps directly to one Unicode scalar in U+0000 through U+00FF.

Streaming/chunk boundaries do not alter this replacement segmentation. A decoder buffers an incomplete prefix while more source input may still complete it. When later input proves the prefix ill-formed, or EOF makes an incomplete final prefix ill-formed, replacement consumes exactly the maximal subpart required by the rule above. An implementation must not emit a different number or placement of U+FFFD values merely because native reads, buffers, vectorized decoding, or converter calls split the same source octets differently.

For a host-provided non-portable `Encoding`, the malformed-subsequence segmentation used by replacement decoding is part of that Encoding's host-bound semantic contract. It must be deterministic for the same encoded source sequence and independent of implementation chunking; an implementation must not leave the observable replacement grouping to an accidental converter-call boundary or library-version heuristic.

Replacement consumes malformed source input; it is not an ignore policy. The source octets consumed for a replacement participate in byte-accounting rules such as `readLine(maxBytes)` exactly where those rules count consumed source octets.

Encoding is strict by default. Core v0.1 defines no general replacement-encoding policy for characters not representable in the selected encoding.

Strict decoding fails on malformed input, while replacement decoding consumes deterministic malformed subsequences and emits U+FFFD; portable UTF replacement uses Unicode 17.0.0 maximal subparts, is invariant under streaming chunk boundaries, and its consumed octets count toward bounded line input.

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

Whenever a standard `Encoding` configuration explicitly requests BOM emission, that request is part of the encoded byte result of each fresh one-shot `encoding.encode(text)` conversion. The emitted BOM is exactly one initial matching BOM for the selected Encoding, placed before all bytes encoding `text`.

This applies even when `text` is the empty String. Therefore a fresh one-shot `encode("")` under an explicit BOM-emitting configuration returns exactly that Encoding's BOM bytes, whereas `encode("")` under the default no-BOM configuration returns `Bytes()`.

One-shot BOM emission is determined by the Encoding configuration and the fresh conversion boundary, not by whether the text payload contains at least one Unicode scalar. An implementation must not suppress a requested BOM merely because the payload is empty, nor emit multiple BOMs for one one-shot conversion.

This one-shot rule does not change streaming `TextWriter` state semantics. In particular, an ordinary `TextWriter.writeText("")` remains the already-defined zero-byte, zero-encoder-state-transition operation and does not by itself trigger BOM emission, stream initialization/finalization, flush, or reset. Any BOM behavior of a stateful streaming writer belongs to that writer's explicit encoder/lifecycle contract rather than being inferred from the one-shot `Encoding.encode` rule.

---
## 16. TextReader and readLine

A standard `TextReader` layers decoding and text buffering over `ByteReadable`.

`TextReader` and `TextWriter` are standard frozen-prelude factory/prototype bindings. Portable construction is `TextReader(source, encoding)` and `TextWriter(target, encoding)` for borrowing wrappers, and `TextReader.owning(source, encoding)` / `TextWriter.owning(target, encoding)` for owning wrappers. Each successful call creates a fresh wrapper identity and fresh per-flow codec state. Source must satisfy `ByteReadable`, target must satisfy `ByteWritable`, and encoding must be an Encoding descriptor; no coercion occurs. Invalid semantic arguments fail synchronously before wrapper creation or underlying I/O. Ownership follows `IO_CORE.md`, never enlarges authority, and wrapper construction introduces no hidden suspension.

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

`readLine(maxBytes)` provides an explicit safety bound. `maxBytes` must be an Integer greater than zero.

The bound is measured over the encoded source octets consumed for the current line before its decoded line terminator begins, not merely over octets that map one-to-one to returned Unicode characters. After the TextReader's initial stream setup for the selected Encoding has been consumed (for example an initial BOM that the Encoding itself consumes), the byte budget for a line starts immediately after the previous line terminator's complete encoded extent, or at the first remaining source octet for the first line.

Every source octet consumed by the decoder in that interval counts toward `maxBytes`, including octets that only change or maintain decoder state and produce no Unicode scalar value. This is required for stateful encodings: shift/escape/control sequences cannot be inserted without bound while evading a supposedly byte-bounded line operation.

The encoded extent that produces the terminating LF or CR does not count toward the line-content budget. For CRLF, neither the encoded extent producing CR nor the immediately following encoded extent producing LF counts. Decoder-control octets consumed before the decoder can produce the terminating character remain part of the pre-terminator interval and therefore count; implementations do not get to classify such octets differently based on converter internals.

If EOF occurs without a terminator, every remaining source octet consumed for that final unterminated line after its line-budget start counts, including non-text-emitting state-control octets. Bytes that were already consumed before the current line-budget start because they established decoder state for an earlier line are not charged again to the new line.

If the counted pre-terminator interval exceeds `maxBytes`, the Future fails and no fragment is returned as a successful line. The limit is therefore a bound on encoded source consumption attributable to the current line-framing interval, independent of whether the selected Encoding is stateless, multibyte, or stateful.

`readLine(maxBytes)` bounds all valid encoded source octets consumed in the current pre-terminator line interval, including state-only bytes, while excluding the encoded terminator itself and bytes consumed before that line begins.

A line-too-long failure permanently fails the `TextReader`'s text-reading side. The reader does not implicitly scan/discard the remainder of the overlong line, search for a later terminator, or attempt to recover a next-line boundary.

A decoding or underlying I/O failure that becomes the committed failure outcome of a `TextReader` `readText()` or `readLine()` operation permanently fails that reader's text-reading side. For `readLine()`, the line-framing/error-precedence rules determine whether such a failure belongs to the current line before a complete line result exists. For `readText()`, the progress rule in section 14 first delivers any valid decoded text that logically precedes a later read-ahead I/O/decoding error; merely discovering that later error while constructing or buffering beyond the returned chunk does not poison the reader before the preceding text has been delivered. Once the preserved error becomes the outcome of the next applicable ordered text-reading operation, the text-reading side then enters this permanent failed state. This deterministic wrapper failure rule is independent of whether the wrapped byte source itself remains usable.

After the text-reading side has failed, subsequent `readText()` and `readLine()` operations fail without consuming additional input from the wrapped source. Core v0.1 defines no recovery/reset operation that resumes text decoding on the same `TextReader`.

Successful cancellation is not such a failure and does not poison the reader; the cancellation rules below preserve the logical input sequence.

Failure of the `TextReader` does not by itself close, fail, or transfer ownership of its wrapped byte source. `close()` remains permitted when the reader exposes `Closable`, and an owning reader still performs its explicit release obligations according to the wrapper lifecycle rules.

### 16.2 Deterministic line framing and error precedence

Line construction is determined in logical input order after decoding under the selected `Encoding`; buffering and read-ahead do not change which condition belongs to the current line operation.

For `readLine(maxBytes)`, decoding is evaluated in logical source order under the TextReader's selected decoding error policy.

Under strict/fatal decoding, a malformed sequence fails with the decoding error if that failure is established before the byte budget has already been exceeded by validly consumed pre-terminator source octets; an implementation must not pretend malformed octets were valid content merely to manufacture an earlier size failure.

Under replacement decoding, malformed input does not establish a decoding failure. The decoder consumes the malformed subsequence according to the Encoding's deterministic replacement rule and produces U+FFFD. Every source octet consumed for that replacement counts toward the current line's byte budget when it lies in the pre-terminator interval. If consuming that replacement extent makes the count exceed `maxBytes`, the line-too-long condition is established in the ordinary way.

Valid decoder input that changes state without producing a Unicode scalar still advances the current line's byte budget as soon as those octets have been validly consumed in the pre-terminator interval. Likewise, when a valid non-terminator character is decoded, every source octet in its consumed encoded extent has already contributed to that same budget.

As soon as validly consumed pre-terminator source octets make the count exceed `maxBytes`, the line-too-long condition is established. The implementation need not read or decode later input merely to discover another possible error or terminator. An I/O or decoding failure encountered before the limit has been established remains the failure of the current operation.

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

For standard `TextWriter`, `writeText("")` has an empty logical text payload and therefore contributes no encoded bytes and performs no encoder-state transition. It is not an implicit encoder flush, finalization, reset, BOM-emission trigger, shift-state reset, or other control operation merely because the configured Encoding may be stateful. Successful completion leaves the per-flow encoder state exactly as it was immediately before this operation's ordered evaluation.

This empty-payload rule is semantic rather than representational. An implementation need not invoke its backend/native encoder at all for `writeText("")`; if it does invoke one, any implementation-selected control bytes or state changes that are not the encoding of a source character must remain unobservable. A host/native encoder API whose "empty input" call would flush/reset state must therefore not be mapped directly to ordinary `writeText("")` in a way that exposes that control effect.

`writeText("")` remains an ordinary member of the TextWriter ordering and lifecycle domain. It may complete without target I/O once every earlier ordered text-write operation has reached an aftermath that leaves the writer usable, but it must not resolve successfully ahead of an earlier ordered operation that can still permanently fail the TextWriter. If such an earlier operation does permanently fail the writer, the later empty write fails under the same predecessor-failure rule as any other later text write and contributes no bytes/state change.

`writeLine("")` is not an empty-payload operation: its logical text payload is LF (`U+000A`) and it is encoded and ordered normally.

Encoder finalization remains the job of the explicit lifecycle operation that requires it, such as `TextWriter.close()`. Ordinary empty text writes cannot be used as a portable substitute for finalization.

All standard text-output operations accepted by one logical `TextWriter` share one encoder-state and output-ordering domain. `writeText()` and `writeLine()` do not race independent encoder states or independently ordered byte-output sequences merely because their Futures are simultaneously pending or because callers use distinct Actor-safe proxies for the same writer.

When two text-write operations have a Protos-defined order, that order is preserved. In particular, text writes issued sequentially by one Actor are encoded and contribute output in that Actor's invocation order even when an earlier text-write Future remains pending. The later operation's encoding starts from the encoder state established by the earlier operation's specified terminal aftermath; it cannot bypass the earlier operation merely because validation or downstream byte I/O for the later request could run first.

Text-write requests that are genuinely concurrent because they originate from independently progressing Actors through Actor-safe routing/proxies have no predetermined relative order. The writer/routing layer may admit either operation first. Once it chooses their relative order, that order is stable for complete-payload validation, encoder-state evolution, cancellation/failure aftermath, and target-visible byte contribution.

This ordering is a logical TextWriter property, not a requirement to hold one host lock, allocate one complete encoded buffer, or perform one underlying byte write at a time. Implementations may validate, encode, stage, pipeline, or overlap downstream work when such machinery cannot change the chosen Protos text-operation order or make one operation observe an encoder state inconsistent with that order.

If the TextWriter exposes `Flushable`, its flush frontier is ordered in this same logical text-output flow: encoded output belonging to text writes ordered before the frontier is covered according to the ordinary `Flushable` contract, while later text writes are not required to be propagated by that flush. If the TextWriter exposes `Closable`, the ordinary close cutover likewise applies to these text-write operations; object/proxy identity does not create an independent lifecycle domain.

A TextWriter has one logical encoder/output ordering domain: per-Actor text-write invocation order is preserved, genuinely concurrent cross-Actor writes receive one stable routing/admission order, and any exposed flush/close lifecycle composes with that same ordered text flow.

`writeLine(text)` uses LF as the canonical Protos line terminator and does not perform platform-native newline translation.

Before any byte belonging to one of these operations becomes observable at the byte target's output boundary, the TextWriter must establish that the operation's complete text payload is encodable under the writer's current per-flow encoder state and configured error policy.

If that encoding step fails, the text-write Future fails with zero bytes from that operation contributed to the target, and the TextWriter's encoder state remains as it was immediately before that operation's ordered evaluation. An encoding failure by itself does not poison the TextWriter; a later text-write operation may proceed from that unchanged encoder state.

This encoding-failure atomicity does not require eager allocation of the complete encoded byte sequence. An implementation may validate, checkpoint encoder state, encode in a reversible staging representation, perform a dry run, or use another strategy, provided no target-visible bytes or committed encoder-state change from the operation precede successful validation of the complete text payload.

After complete encoding validation succeeds, the operation has one logical encoded byte sequence and one resulting encoder state determined from the encoder state at that operation's ordered evaluation point. The implementation may deliver that logical byte sequence through one or more underlying byte writes; the mapping from one text write to native/underlying write calls remains non-observable.

Cancellation may win only while the operation can still preserve both zero target-visible byte contribution and the pre-operation encoder state. Once the first irreversible output effect of the operation occurs at the target's semantic output boundary, the text-write operation is committed and cancellation cannot make it observably disappear.

An underlying output failure after commitment follows the ordinary `ByteWritable` and wrapper-failure rules. In particular, the target may already contain a prefix of the operation's encoded byte sequence, and a TextWriter must not guess how much downstream progress occurred. When exact remaining output cannot be known, its output side becomes permanently failed/unusable as specified by the wrapper lifecycle rules.

An ordered later text-write operation must not cross its own target-visible output commitment boundary while an earlier ordered text-write operation can still terminate in a way that permanently fails this TextWriter's output side. The implementation may validate, encode, checkpoint, or stage the later operation speculatively, but irreversible contribution of that later operation to the byte target waits until every earlier ordered text write has reached an aftermath that leaves the TextWriter usable for later output.

Therefore, if an earlier ordered text write permanently fails the TextWriter's output side, every later outstanding `writeText()` or `writeLine()` operation in that same logical ordering domain fails without contributing any bytes of its own to the target and without advancing committed encoder state. Such an operation cannot bypass the failed predecessor merely because its encoding validation completed, its bytes were staged, it came through another Actor-safe proxy, or backend scheduling would otherwise let its native write run first. New text-write invocations after the permanent output failure likewise fail without target contribution.

This is a commitment-ordering constraint, not a prohibition on pipelining. Work whose effects remain reversible/unobservable may overlap freely. It also does not require waiting for unrelated receivers or flows. The constraint exists only between ordered operations of the same TextWriter and only up to the point needed to keep a possible permanent predecessor failure from making later target-visible output implementation-dependent.

A stronger downstream protocol that lets the TextWriter recover from an underlying failure is compatible with this rule only when the wrapper can determine its exact remaining output and encoder/output state under that stronger protocol. Recovery may complete the earlier logical text operation or otherwise establish a precisely specified usable aftermath before a later text write commits. Host error codes, guessed native write counts, or implementation-specific buffering knowledge that is not part of the downstream protocol cannot justify releasing the later operation across this failure frontier.

A successfully completed text write commits its resulting encoder state for later ordered text writes. A successfully cancelled text write and a text write that fails during pre-output encoding validation do not advance that state. A text write whose downstream failure permanently fails the writer establishes no usable encoder-state starting point for later text output, because later text writes are rejected by that failed lifecycle.

Later TextWriter operations may be staged speculatively but cannot make target-visible output while an earlier ordered text write can still poison the writer; permanent predecessor failure makes all later outstanding/new text writes fail with zero own contribution.

A TextWriter exposes `Flushable` or `Closable` only when it correctly implements those protocols over its own state. Target capabilities are never inherited automatically.

Closing a TextWriter finalizes encoder state and propagates the final bytes belonging to that wrapper before the wrapper becomes closed. The byte target is not closed by default.

---
