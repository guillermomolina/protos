# Changelog

## 0.2.110-SNAPSHOT

- Implement I016-D2 authority/transfer boundaries with explicit runtime markers for live File and host-provisioned Filesystem capabilities while keeping their language-visible ordinary-object protocols unchanged.
- Reject direct File/Filesystem authority and ordinary descendants carrying that authority at Actor transfer with `NonTransferableValue`, and at isolated P transfer with `NonParallelValue`; no proxy, reopen, handle duplication, ambient inheritance, or new native Closure provider is introduced. I016-D3 becomes READY for deterministic authority/race conformance and the final post-D2 I018 boundary re-audit.

## 0.2.109-SNAPSHOT

- Implement I016-D1 Filesystem open integration: host-provisioned open-only Filesystem capability, exact one/two-argument `open` bridge over the I016-A preflight/acquisition flow, and standard positioned/append File materialization through the I016-B/C File protocol with exact requested read/write/append authority.
- Tighten open options to the normative ordinary-object domain and document the backend authority contract for confinement, race-free selection, create/truncate commitment, stable selected-resource custody, and synchronous pre-commit cancellation cleanup. Register the single new resource/capability native bridge in the post-I018 boundary guard/inventory so the full suite remains architectural evidence. I016 remains IN_PROGRESS under the recorded D1-D4 completion plan.

## 0.2.108-SNAPSHOT

- Implement I011-12 GroupRef capability-identity foundation: add an opaque represented `GroupRef` value whose semantic reference identity is independent of Group identity, physical wrapper identity, and acquisition path.
- Integrate GroupRef with primitive semantic identity/identityHash and Actor-boundary transfer so repeated rematerializations preserve one capability identity and effective restriction descriptor, while independent acquisitions to the same Group remain distinct.
- Verify transferred GroupRef values remain valid `Map` and `IdentityMap` keys without exposing or copying mutable Group/controller/routing state. This slice intentionally adds no public Group acquisition, membership, routing, send/request, broadcast, Authority, or controller API; I011 remains IN_PROGRESS.

## 0.2.107-SNAPSHOT

- Implement I011-11 Actor-boundary keyed-collection transfer for `Map` and `IdentityMap`, preserving keyed insertion order, graph aliasing/cycles, local slots, and open/closed/frozen state while keeping the complete transfer atomic.
- Rebuild destination hash bookkeeping without invoking ordinary `hash`/`==` during snapshot formation: default identity-based Map keys receive the copied identity's hash, specialized recorded Map hashes are preserved, and IdentityMap entries receive the copied key's semantic `identityHash`.
- Align default `Object.hash` with the existing semantic `ProtosIdentity.identityHash` primitive so rematerialized identity-bearing capabilities such as `ActorRef` remain valid equal/hash keys. I011 remains IN_PROGRESS; Group/distributed routing, GroupRef/Process capability transfer, failure authority and RootActor/Process integration remain.

## 0.2.106-SNAPSHOT

- Close I018 Core self-hosting/bootstrap minimization after an exhaustive current-main inventory of every production `ProtosClosureValue.nativeClosure(...)` provider. The final boundary contains 90 native-Closure construction sites across exactly 22 Java providers, all classified as irreducible host execution/control, semantic-value representation, concurrency/runtime, or resource/capability bridges; no remaining standard slot is classified as faithfully source-expressible.
- Add `docs/project/CORE_NATIVE_BOUNDARY.md` as the non-normative maintenance inventory and `ProtosCoreNativeBoundaryArchitectureTest` as an executable guard over the exact provider set/counts, helper-backed runtime selector surfaces, migrated Object/Integer/Float source provenance, internal Bytes/ActorRef/SendOperation surfaces, and the Core-bootstrap direct-allocation boundary.
- Mark I018 CLOSED and lift the temporary coordination pause that held I016 at the already-published I016-C state. I016 may resume from I016-D after re-auditing the then-current `origin/main`. No normative specification changes are made.

## 0.2.105-SNAPSHOT

- Implement I011-10 graceful Actor lifecycle: add public `ActorRef.stop()` and `ActorRef.termination()`, establish the irreversible stop cutover without a stop Future, and provide fresh caller-local termination observation Futures tied to one concrete incarnation.
- Move Actor termination cancellation to the TERMINATING cutover: cancel all live Actor-local tasks (including detached work), pending Actor-originated non-task Futures and I/O operations, suppress accepted-but-undispatched work/bootstrap after the cutover, and reach TERMINATED only after required task cancellation unwind completes.
- Preserve producer commitment/acceptance boundaries, exact request uncertainty, non-preemption of already-running non-suspending turns, and independent/cancellable termination observations. I011 remains IN_PROGRESS; distributed/Group routing, specialized capability transfer, failure authority and RootActor/Process integration remain for later slices.

## 0.2.104-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the internal standard `ActorRef` and `SendOperation` delegation prototypes in distributable `protos/lib/core/actor.protos` rather than allocating those prototype identities inside `ProtosStandardActorProtocol`.
- Supply the exact source-created prototypes to the Actor runtime installer, validate direct-`Object` parentage plus open/empty source shape before mutation, install only the existing native communication/lifecycle bridges (`send`, `request`, `cancel`, `retry`), freeze the exact supplied objects, and remove the construction-only helper bindings before prelude construction.
- Extend Actor API regression coverage for the exact supplied prototype identities/surfaces and update deterministic send/request fixtures to inject their prototype identities explicitly. `ActorRef` and `SendOperation` remain absent as public prelude bindings. No normative specification changes are made; I018 remains open only for the final exhaustive native-boundary inventory/architectural guard, and I016 remains frozen at I016-C.

## 0.2.103-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard default `Object.==` body from Java into distributable `protos/lib/core/object.protos`.
- Define the source-backed body as `this === other`, delegating exactly to Protos primitive semantic identity rather than host object identity; reuse the existing isolated frozen Object-source capture context and narrow symbolic-selector installation bridge, and remove the duplicated native equality closure from `ProtosStandardObjectProtocol`.
- Extend bootstrap provenance coverage so `Object.init`, `Object.==`, and `Object.!=` are all source-backed and share the isolated frozen source context. Existing default-equality/non-identity conformance remains focal. No normative specification changes are made, I018 remains open for the final native-bridge inventory/guard, and I016 remains frozen at I016-C.

## 0.2.102-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard frozen-prelude bindings object in distributable `protos/lib/core/prelude.protos` instead of allocating and populating that object slot-by-slot in `ProtosCoreBootstrap`.
- Preserve the exact existing prelude surface and identities, including the complete validated Error taxonomy, direct `Context` parentage, absence of the construction-only `Bytes` binding, and shallow final freeze. Remove the Java-side Error-taxonomy export helper while retaining host-side topology validation.
- Add focused regression coverage for the exact prelude binding set and an architectural guard preventing `ProtosCoreBootstrap` from reintroducing direct `preludeBindings.createLocalSlot(...)` construction. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.101-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard `Float.negated()` body from Java into distributable `protos/lib/core/float.protos`.
- Express Float negation as `(0.0 - 1.0) * this`: constructing semantic binary64 `-1.0` through native subtraction avoids recursive use of `negated`, and native Float multiplication then validates the original receiver while producing exact binary64 sign inversion, including `+0.0`/`-0.0`, infinities, subnormals, and Core NaN semantics.
- Add focused provenance and receiver-domain regression coverage proving `Float.negated` is source-backed while the binary64 `*` representation primitive remains native-backed. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.100-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard ordinary `Integer.%` derived body from Java into distributable `protos/lib/core/integer.protos`.
- Express `%` as `(0 + this).mod(argument)`: the native standard Integer `+` on semantic zero validates the original receiver before any `mod` dispatch, then the existing native `mod` primitive supplies the normative truncation-toward-zero remainder semantics. Java retains only the narrow symbolic-selector installation bridge from the temporary source name to `%`.
- Add provenance and adversarial receiver-domain regression coverage proving `%` is source-backed, `mod` remains native-backed, the temporary source slot is removed, and an incompatible Integer-delegating object cannot bypass standard receiver validation by overriding `mod`. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.99-SNAPSHOT

- Implement I011-9 concrete-Actor `ActorRef.request(selector, arguments...)`: reuse the I011-7/I011-8 delivery boundary, return a fresh caller-domain Future, transfer the normal handler result back across the Actor boundary, and never flatten or adopt a destination-local Future.
- Complete direct concrete-Actor accepted-work loss notification: accepted-but-undispatched work lost at termination becomes post-acceptance failure; request loss/fatal handler failure maps to a fresh caller-domain `RequestOutcomeUncertain`, non-transferable replies fail with `NonTransferableValue`, and cancellation preserves the pre/post-acceptance distinction. Distributed/Group routing uncertainty, lifecycle observation/stop, specialized transfers, and final Actor cleanup remain for later I011 slices.

## 0.2.98-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by moving the standard ordinary `Integer.negated()` body from Java into distributable `protos/lib/core/integer.protos` as ordinary Protos behavior (`0 - this`).
- Keep exact unbounded Integer subtraction host-backed as the representation primitive; using semantic Integer zero as the subtraction receiver preserves the existing Integer receiver-domain rejection even when an incompatible ordinary object merely delegates to `Integer`.
- Add focused provenance and receiver-domain regression coverage proving `negated` is source-backed while `Integer.-` remains native-backed. No normative specification changes are made, and I016 remains frozen at I016-C.

## 0.2.97-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the internal standard `Bytes` factory/prototype used by buffered byte wrappers from distributable `protos/lib/core/bytes.protos` instead of allocating that standard identity in Java.
- Preserve the existing native Bytes constructor/indexing/mutation/snapshot/parallel-region protocol unchanged, including factory-receiver parentage of produced Bytes values; remove the construction-only `Bytes` binding before the frozen standard prelude is built because Core v0.1 does not require a `Bytes` prelude binding.
- Add a bootstrap regression that `Bytes` remains absent from the standard prelude. I016 remains frozen at I016-C.

## 0.2.96-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard frozen-prelude `import` facility from distributable `protos/lib/core/import.protos` instead of allocating that public standard identity in Java.
- Keep module-specifier validation, host resolution, canonical ModuleKey handling, Actor-local caching, cache-before-execute, cycles, initialization failure, and retry semantics in the existing runtime; Java now installs only the `call` primitive bridge into the exact source-created import object and freezes that same object.
- Add focused regression coverage for direct-`Object` parentage, exact `call` surface, native bridge provenance, and installer identity preservation. Restore the required full APL Part 5 notice on the touched import protocol/module runtime test sources. I016 remains frozen at I016-C.

## 0.2.95-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard `BufferedReader` and `BufferedWriter` frozen-prelude factory/prototype objects from distributable Core source instead of allocating those public standard identities in Java.
- Keep buffered byte construction, capability validation, borrowing/ownership, Future, buffering, and lifecycle behavior host-backed; Java now installs only the existing `call`/`owning` primitive bridges into the exact source-created factory objects and freezes those same objects.
- Add focused regression coverage for direct-`Object` parentage, exact `call`/`owning` surface, preserved supplied factory identity, and native bridge provenance. Restore the required full APL Part 5 notice on the touched buffered protocol/test sources. I016 remains frozen at I016-C.

## 0.2.94-SNAPSHOT

- Continue I018 Core self-hosting/bootstrap minimization by constructing the standard `Actor` prelude entry object from distributable `protos/lib/core/actor.protos` instead of allocating that public standard object in Java.
- Keep `Actor.spawn` and `Actor.current` as host-backed primitive bridges, install them into the exact source-created Actor object, validate its direct-`Object` parent and empty/open source shape, and freeze that same object without changing Actor semantics.
- Add focused regression coverage that the Actor protocol installer preserves the supplied source object identity and contributes only native `spawn`/`current` behavior. I016 remains frozen at the already-published I016-C state.

## 0.2.93-SNAPSHOT

- Implement I011-8 public concrete-Actor `ActorRef.send(selector, arguments...)`: require an exact semantic String selector, form the complete Actor-boundary snapshot synchronously before admission, and dispatch accepted work against the destination Actor's stable behavior/message environment while ignoring the handler's normal result.
- Add the local identity-bearing SendOperation with exactly `cancel()` / `retry()`: cancellation succeeds only when known pre-acceptance cancellation wins; retry is explicit after known terminal delivery failure, creates a fresh operation identity, and reuses the original logical snapshot without source re-evaluation. SendOperation is local/non-transferable. Request/reply and distributed/accepted-work-loss uncertainty remain for later I011 slices.

## 0.2.92-SNAPSHOT

- Start I018 Core self-hosting/bootstrap minimization by moving standard `Object.init` and the body of standard `Object.!=` out of Java and into distributable `protos/lib/core/object.protos`.
- Load those source-backed Object Closures in an isolated frozen bootstrap context, promote them through a narrow host selector bridge, and prevent the process-global root `Object` from retaining the main Core-construction bindings.
- Keep observable Object initialization and inequality semantics unchanged; remaining Java-installed standard protocols are left for later bounded I018 slices rather than expanded. I016-C was already published before this slice; I018 does not advance I016 further.

## 0.2.91-SNAPSHOT

- Implement I011-7 concrete-Actor pre-acceptance delivery admission/backpressure substrate: keep pending logical operations outside the bounded accepted mailbox, wake admission deterministically as capacity is released, and preserve known acceptance/cancellation boundaries.
- Use a FIFO pending discipline to satisfy same-sender FIFO across backpressure and Core weak admission fairness without exposing a new public queue or total-order contract. Public ActorRef send/request, SendOperation retry/reply/uncertainty, specialized transfers, and lifecycle observation remain for later I011 slices.

## 0.2.90-SNAPSHOT

- Implement I016-C append-mode File placement and aliasing semantics: every non-empty append write selects the then-current EOF at the operation's contribution boundary, successful empty append leaves the logical cursor unchanged, failed writes preserve the exact contributed-prefix aftermath, and an earlier seek never turns append into positioned output.
- Define the backend AppendWritableResource contract as an underlying-resource-wide atomic append-placement boundary shared across distinct File/resource aliases, preventing overlap/interleaving while leaving concurrent alias order nondeterministic. Add deterministic cancellation, Actor-termination, ordering, capability-honesty, and cross-alias conformance coverage. Filesystem authority/confinement and public open materialization remain for the final I016 slice.

## 0.2.89-SNAPSHOT

- Implement I016-B positioned standard File capability: stable per-File logical cursor beginning at zero, explicit positional read/write backend boundary, ordered read/write/position/seek/seekToEnd/size/truncate/sync operations, exact failed-write prefix position aftermath, bounded write snapshot admission, and capability-honest stable protocol shape.
- Integrate File Closable lifecycle with I014 commitment and Actor-termination cancellation machinery; close-cutover now invokes the cancellation hook of accepted uncommitted I/O before resource release begins, while committed operations keep their normal aftermath. Raw File does not invent Flushable, Filesystem.open is still not publicly installed, and append/confinement host integration remain for later I016 slices.

## 0.2.88-SNAPSHOT

- Implement I011-6 internal bounded Actor mailbox ownership and READY-gated implicit event-loop dispatch; accepted message turns remain finite, FIFO in accepted order, and undispatched while the destination is INITIALIZING.
- Add automatic Actor-local scheduler wakeups and a weak-fair cross-Actor scheduler that selects one non-preemptive segment per Actor turn, permits independent Actors to use different carriers, and never executes two Protos segments concurrently in one Actor incarnation. I011 remains IN_PROGRESS; pre-acceptance admission/backpressure/FIFO fairness and the public send/request operation semantics remain for later slices.

## 0.2.87-SNAPSHOT

- Implement I016-A Filesystem open preflight/acquisition substrate: exact invocation-time capture of local standard open options, deterministic invalid-combination rejection before backend authority, host-neutral asynchronous acquisition, and independent open dispatch without an implicit Filesystem/Path FIFO.
- Reuse the established I/O commitment and Actor-termination cancellation machinery so pre-commit cancellation contributes no portable filesystem effect, committed create/truncate effects cannot be rewritten as cancelled, and a result-only open that loses cancellation releases untransferred backend custody. No public Filesystem/File surface is installed by this foundational slice; I016 remains IN_PROGRESS.


## 0.2.86-SNAPSHOT

- Implement I011-5 public Actor prelude surface with exactly `spawn` and `current`; perform exact semantic-String validation, one creator-side canonical module resolution, and the I011-4 atomic initialization-vector transfer before the creation cutover.
- Kick off I011-3 destination-local bootstrap only after the cutover and return the stable ActorRef without waiting for READY; preserve current-ActorRef identity and same-incarnation termination on later bootstrap failure. I011 remains IN_PROGRESS; mailbox/send/request, stop/termination observation, RootActor integration, distributed routing, and remaining specialized transfers stay outside this slice.

## 0.2.85-SNAPSHOT

- Implement I011-4 Actor graph snapshot/value-transfer foundation: add one Actor-specific atomic graph-copy boundary with shared-operation memoization, preserving aliases and cycles across roots while copying transferable scalar values, ordinary object state, Arrays, Bytes state, and Paths.
- Rematerialize ActorRef capability wrappers without retargeting or copying Actor state; preserve ordinary delegation/local-slot/mutation state for copied objects; reject Closure, Future, execution-context, ByteRegion, unknown host/runtime values, and not-yet-integrated keyed collections with the standard NonTransferableValue occurrence before any snapshot is exposed. I011 remains IN_PROGRESS and no Actor public API is installed by this slice.

## 0.2.84-SNAPSHOT

- Implement I011-3 Actor bootstrap and behavior cutover: bind each concrete Actor to its existing execution domain, expose only a runtime-local current-ActorRef substrate, load bootstrap code by an already-canonical ModuleKey in the destination Actor-local module cache, require an exact local bootstrap binding, invoke it with already-transferred arguments, and install the exact ordinary-object result before the READY cutover.
- Preserve canonical module identity without destination re-resolution, Actor-local module instances, stable behavior identity after READY, and initialization-failure termination. Public Actor.spawn/current remain deliberately uninstalled until synchronous graph transfer and the remaining runtime prerequisites are implemented; mailbox/admission, send/request, stop/termination monitoring, GroupRef, and distributed routing remain outside this slice.

## 0.2.83-SNAPSHOT

### Fixed
- Implement the normative standard Number ordering selectors `<`, `<=`, `>`, and `>=` as ordinary Number-owned Closure-valued behavior inherited by Integer, fixed-width Integer, and Float values.
- Compare numeric families without promotion or coercion, including exact arbitrary-precision Integer versus finite binary64 Float ordering, signed zero, infinities, and unordered NaN behavior.
- Add regression coverage for the official recursive factorial form in a persistent multiline REPL session.

### Notes
- Standard ordering rejects non-Number arguments and incompatible original receivers with a Protos Error.
- No parser, multiline-REPL, arithmetic compatibility, or normative specification behavior is changed.

## 0.2.82-SNAPSHOT

- Implement I011-2 ActorRef capability semantics: represent ActorRef as an opaque communication capability permanently bound to one Actor incarnation and add explicit Actor-boundary rematerialization that creates fresh wrappers while preserving semantic identity, `identityHash`, delegation parent, and the original target.
- Keep the mutable Actor target outside Protos-visible state and preserve references after termination without retargeting replacements. This slice intentionally does not add Actor.spawn/current, bootstrap/READY behavior installation, mailbox/admission, graph snapshot traversal, send/request, stop/termination monitoring, GroupRef, or distributed routing.

## 0.2.81-SNAPSHOT

- Implement I011-1 Actor incarnation identity/lifecycle foundation: add an explicit runtime-local immutable incarnation identity, centralized race-safe `INITIALIZING`/`READY`/`TERMINATING`/`TERMINATED` state machine, and a semantic `ActorRef` value permanently bound to one incarnation.
- Reuse the existing Actor execution domain and Actor-local module state, preserve `ActorRef` semantic identity and `identityHash` across rematerialized wrappers, and add deterministic lifecycle/concurrency focal tests. I011 remains open; this slice does not add spawn/current, mailbox, send/request, backpressure, transfer, monitoring, graceful-stop policy, GroupRef, or distributed routing.

## 0.2.80-SNAPSHOT

### Fixed
- Implement CLI003 multiline REPL input so a complete JLine bracketed-paste payload is compiled and evaluated once instead of being split into per-line evaluations.
- Accumulate interactive and stream REPL source while the parser reports an unexpected end of source, preserving nested closures, object/block source, blank lines, and persistent top-level context until the syntactic unit is complete.
- Keep invalid non-EOF syntax recoverable without poisoning the next REPL input, and make Ctrl-C discard the entire pending interactive unit.

### Notes
- Parser completeness uses the actual EOF token carried by `ParseError`; CLI003 does not count delimiters or introduce a separate syntax heuristic.
- JLine history continues to store non-blank physical lines independently, while evaluation now operates on the complete source unit.
- File execution and `-e` continue to use the existing source compiler path unchanged, and the Truffle `sun.misc.Unsafe` warning remains outside CLI003.
- No normative specification revision is changed.

## 0.2.79-SNAPSHOT

- Complete I014-G buffered byte-I/O lifecycle/cancellation conformance: close cutover now terminates accepted-but-uncommitted adapter operations with fresh lifecycle failures, active cancellation is propagated to lower Futures without rolling back committed effects, close retains its activation across asynchronous completion, in-flight buffered flushes are reconciled without duplicate propagation, and owning close waits for the owned target close while preserving a primary wrapper-finalization failure.

## 0.2.78-SNAPSHOT

- Implement I014-F standard buffered byte I/O: frozen `BufferedReader`/`BufferedWriter` factories with borrowing/owning forms, ordered bounded buffering, transparent/resumable EOF, recursive flush propagation, deterministic wrapper close ownership/failure handling, permanent output-side failure after ambiguous propagation, fresh standard Futures, and focal conformance tests.

## 0.2.77-SNAPSHOT

- Implement I014-E standard byte I/O directional shutdown: capability-honest `ReadShutdown`/`WriteShutdown`, irreversible directional cutovers, idempotent fresh-Future lifecycle observation, pending-read cutover, output-frontier ordering, cancellation/commitment behavior, and focal tests.

## 0.2.76-SNAPSHOT

- Implement I014-D standard byte I/O durability: explicit `Syncable.sync()` capability, ordered durability frontiers integrated with I014-C sequence-state ordering, and cancellation/commitment semantics that prevent post-commit sync from becoming cancelled.

## 0.2.75-SNAPSHOT

- Implement I014-C standard byte I/O positioning: `Flushable.flush`, `ByteSeekable` position/seek operations, `ByteSized.size`, and failure-atomic non-extending `Truncatable.truncate`, integrated with I014-B transfer ordering and Future cancellation/commitment semantics.


## 0.2.74-SNAPSHOT

- Implement I014-B standard sequential byte transfer: ordered asynchronous `ByteReadable.read(maxBytes)` and `ByteWritable.write(bytes)`, Future-based validation/errors, EOF/partial-read behavior, write snapshotting, bounded admission, cancellation/commitment integration, and focal tests.

All notable changes to the Protos implementation project will be documented in this file.

For specification changes, see [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## 0.2.73-SNAPSHOT

- Implement I010 isolated parallel execution: Closure.parallel, deterministic Array parallelMap/parallelFilter/parallelFindIndex/canonical parallelReduce/stable parallelSort, and exclusive Bytes/ByteRegion parallelRange.
- Add bounded lazy process-local P carriers, graph isolation and per-P Closure relowering, nested-P helping, Future structured ownership/cancellation, deterministic failure precedence, and atomic ByteRegion publication.
- Preserve caller standard-prototype mutability while restricting direct P physical sharing to already-frozen standard identities. No public P/Task/scheduler value, parallelEach, I011, or I014 surface is introduced.

## 0.2.72-SNAPSHOT

- Implement I009 — Future/Task: standard Future prototype/value state machine, `closure.future()`, suspendable `value()`, cancellation, `then()`, deterministic `Future.all(...)`, adoption/flattening, and `detach()`.
- Integrate pending Future waiters with the I009B evaluator bridge and I009A Actor-local Task scheduler, including race-safe register/suspend handoff, multiple waiters, cancellation cleanup, exact resume, and first-terminal-wins.
- Preserve Future/result/Error identity, semantic null, fresh Cancelled observations, Actor-domain isolation, continuation non-reentrancy, and deterministic aggregate ordering.
- Add focal end-to-end tests across real Truffle suspension/resumption plus protocol/state-machine coverage. No normative specification revision is changed.

## 0.2.71-SNAPSHOT

- Implement I009B evaluator suspension/resumption bridge for ordinary Truffle-backed Protos execution using Actor-local per-task continuation state.
- Preserve completed expression effects across cooperative suspension while rebuilding only the host Java/Truffle call stack, including nested Closure invocation activation and return-home state.
- Add explicit cancellation-aware wait cleanup and deterministic end-to-end coverage for peer-task progress, exact resume values, side-effect non-repetition, nested evaluation/non-local return, cancellation wake-up, and no double resume.
- No normative specification revision is changed.

## 0.2.70-SNAPSHOT

- Implement I009A internal Task/Actor execution infrastructure: Actor-local cooperative FIFO runnable queues, race-safe Task state transitions, suspension/resume, cooperative cancellation wake-up, and structured parent/child ownership.
- Keep cancellation of a suspended Task independent from its observed wait dependency so Future.value() can later wake for cancellation without cancelling or completing the observed Future.
- Add deterministic focal runtime tests for Actor isolation, dispatch ordering, duplicate resume exclusion, cancellation boundaries/races, structured ownership, and terminal transition safety.

## 0.2.69-SNAPSHOT

- Implement I008 standard module runtime semantics: exact semantic-String `import(specifier)`, host-produced canonical `ModuleKey`, Actor-local module caches, cache-before-execute cycles, single evaluation, failure eviction, and retry.
- Execute imported module source exclusively through `ProtosSourceCompiler`; module instances are their ordinary `moduleContext` objects and partially initialized state is directly observable during cycles.
- Translate host resolver/source/compiler failures to Core `Error` signaling without exposing Java exceptions, while leaving host resolution policy and Filesystem APIs outside I008.
- Add focal module conformance coverage for semantic membership, canonical identity, Actor isolation, cycles, cache behavior, retry, and host-error translation.

## 0.2.68-SNAPSHOT

- Implement Standard Bytes semantics: add receiver-owned mutable standard Bytes state and an explicitly installable standardized Bytes factory/prototype without adding a mandatory Core-prelude binding.
- Add exact octet validation (semantic Integer 0..255), zero-based size/at, replacement-only atPut, open-only add/removeAt, and ascending-snapshot each through ordinary polymorphic invocation.
- Preserve identity-bearing defaults for ==, hash, === and identityHash; byte contents are not traversed for equality or hashing.
- Add focal I012 conformance tests for construction, membership, lookup, numeric boundaries, mutation state, snapshot iteration, identity/equality/hash, bracket indexing, and Core Error paths.
- Do not add String/Encoding/Text I/O, UTF-8 coercion, slicing, concatenation, iterators, Array conversion, Filesystem, Set, or IdentitySet behavior.

## 0.2.67-SNAPSHOT

### Added
- Implemented I013 Standard Path with portable represented values and the normative `relative`, `rooted`, `child`, and `parentComponent` construction protocol.
- Added filesystem-independent structural equality/hash while preserving D037 ordinary individual identity and IdentityMap behavior.
- Added focused conformance coverage for construction, lookup, receiver domains, frozen-prelude binding, D037, and absence of host path parsing.

### Notes
- No filesystem authority, host normalization, realpath, separator interpretation, or String-to-Path coercion is introduced.
- No normative specification revision is changed.

## 0.2.66-SNAPSHOT

### Fixed
- Changed interactive REPL history handling so a bracketed paste containing multiple complete lines is stored as one JLine history entry per line, matching the already independent per-line evaluation behavior.
- Added focused coverage for multiline history splitting and ordering.

### Notes
- History remains implemented by JLine; Protos does not introduce a separate readline/history subsystem.
- Structured syntactic multiline parsing remains out of scope: pasted complete lines are still evaluated independently.
- No normative specification revision is changed.

## 0.2.65-SNAPSHOT

### Fixed
- Corrected the CLI002 multiline-paste regex Java string literal so the regex engine receives `\R` through a valid Java string literal.
- This is a compile-fix only; REPL semantics and the previously added line-by-line paste handling are unchanged.
- No normative specification revision is changed.

## 0.2.64-SNAPSHOT

### Fixed
- Fixed CLI002 bracketed-paste handling so a JLine `readLine` result containing multiple complete lines is split and evaluated sequentially in the same persistent REPL session.
- Added a focused regression test for the actual JLine-style multiline paste payload, rather than only testing newline-separated stream input.
- Added `--enable-native-access=ALL-UNNAMED` to the launcher to suppress the JDK restricted-native-access warning emitted when JLine initializes its native terminal support.

### Notes
- This does not add structured syntactic multiline parsing; each pasted complete line remains an independent evaluation.
- The pre-existing Truffle `sun.misc.Unsafe` warning is unchanged and remains outside CLI002.
- No normative specification revision is changed.

## 0.2.63-SNAPSHOT

### Added
- Added CLI002 interactive terminal UX using JLine 3.30.6 for readline-style editing, session history, bracketed paste, Ctrl-C cancellation, and clean Ctrl-D exit.
- Preserved the stream-based REPL path for automated tests and non-terminal input; pasted complete lines remain independent evaluations with one persistent Protos context.

### Notes
- CLI002 changes terminal input only; the existing parser/canonical/lowering/Truffle evaluation pipeline is unchanged.
- Structured syntactic multiline input is not added because CLI002 does not introduce parser-completeness heuristics.
- Protos Error rendering remains minimal (`Error: <object>`); Truffle/JDK `sun.misc.Unsafe` warnings are outside CLI002.
- No normative specification revision is changed.

## 0.2.62-SNAPSHOT

### Added
- Added CLI001 basic executable CLI with file execution, `-e`, help/version, and persistent-context REPL.
- Added non-normative value rendering, focused CLI/REPL tests, `bin/protos`, and executable shaded-JAR packaging.

### Notes
- CLI001 reuses the normal parser/canonical/lowering/Truffle pipeline.
- Top-level script arguments remain unavailable because Core `args` is Closure-invocation-only.
- No normative specification revision is changed.

## 0.2.61-SNAPSHOT

### Added
- Implemented I006 IdentityMap with semantic identity hashing plus primitive `===`.
- Completed fixed-width Integer identity and identity-hash coherence.
- Added IdentityMap conformance coverage.

### Notes
- Standard Map remains on `hash` plus `==`; Set and IdentitySet remain out of scope.
- No normative specification revision is changed.

## 0.2.60-SNAPSHOT

### Added
- Implemented I005 Standard Map: Map(), at, atPut, containsKey, remove, size, insertion-order each, semantic receiver-domain checks, recorded hashes and query-key equality.
- Added Map-required standard hash behavior for ordinary objects, Numbers and Strings.
- Set remains explicitly out of scope.

### Notes
- No normative specification revision is changed.

## [0.2.59-SNAPSHOT] - 2026-09-04

### Added

- I003 — implemented the standard semantic String prototype and represented-value
  delegation through the generalized I002 lookup bridge.
- Added standard String `size`, `at`, bracket-read, and binary `+` behavior with
  receiver-domain validation and Protos Error signaling.
- Pinned ICU4J 78.1 and require Unicode 17 data so `String.size` and `String.at`
  use Unicode 17.0.0 default extended grapheme clusters rather than host/JDK text
  segmentation.
- Added conformance coverage for empty/ASCII/Unicode/supplementary Strings,
  exact-scalar equality/identity, fixed-width Integer indexing, invalid indexes,
  concatenation, and non-membership by delegation.

### Notes

- String semantic identity remains exact Unicode-scalar-sequence identity; no
  normalization, coercion, encoding, Bytes, module, or text-I/O behavior is added.
- No normative specification change is introduced.
- Maven remains intentionally outside the installer and is run manually.
- Project implementation version is `0.2.59-SNAPSHOT`.

## [0.2.58-SNAPSHOT] - 2026-09-04

### Fixed

- I007 follow-up — SlotNotFound test-prelude compatibility: updated the lightweight
  test prelude to expose the normative `SlotNotFound -> Error` prototype and
  corrected legacy lookup/member/message/bare-assignment assertions to expect
  fresh `SlotNotFound` occurrences.
- Repaired the malformed implementation version `0.2.57-SNAPSHOT-SNAPSHOT`
  produced by the original I007 installer.

### Notes

- No normative specification change is introduced.
- Maven remains intentionally outside the installer and is run manually.
- Project implementation version is `0.2.58-SNAPSHOT`.

## [0.2.57-SNAPSHOT] - 2026-09-04

### Added

- I007 — Core error infrastructure: installed the closed normative Core Error
  prototype taxonomy and exact parent relations.
- Added typed fresh-occurrence factories and exact-object signaling through
  `ProtosCoreErrors`.
- Installed `Error.signal()` with receiver/arity validation, no implicit
  condition-designator coercion, and exact signaled-object preservation.
- Added focused tests for freshness, hierarchy, `InvalidReturn`, signaling,
  incompatible receivers, and host/program-error separation.

### Fixed

- Normative lookup absence now creates fresh `SlotNotFound` occurrences rather
  than generic `Error` occurrences.

### Notes

- Future, Actor, parallel, I/O, and filesystem execution remain reserved to
  their subsystem implementations; I007 only exposes the standard Core
  prototypes/factory infrastructure they require.
- `Error.handle(body, handler)` is not faked with a Java catch. The current
  runtime does not yet contain the handler-frame/unwind machinery required to
  implement the normative deactivation-before-cleanup rule correctly.
- No normative specification change is introduced.
- Project implementation version changed from `0.2.56-SNAPSHOT` to `0.2.57-SNAPSHOT`.

## [0.2.56-SNAPSHOT] - 2026-09-04

### Changed

- I002 — uniform represented-value lookup: introduced a small internal
  `ProtosRepresentedValue` bridge that supplies only the immediate delegation
  parent used by ordinary lookup for specialized runtime representations.
- Migrated Integer, Float, fixed-width Integer, canonical Boolean, and canonical
  `null` lookup onto the uniform bridge, removing the growing represented-value
  `instanceof` chain from `ProtosValueLookup`.
- Unified activation receiver lookup through `ProtosValueLookup`, preserving
  ordinary-object lookup while allowing represented values whose normative
  parent does not require the Core prelude to participate in the same path.
- Added regression coverage for ordinary lookup, numeric/fixed-width lookup,
  Boolean/null delegation, original-receiver binding, receiver-domain rejection,
  bridge extensibility, and polymorphic invocation.
- Project implementation version changed from `0.2.55-SNAPSHOT` to
  `0.2.56-SNAPSHOT`.

### Notes

- No normative specification change is introduced.
- No new Core family or standard prototype is materialized by I002. In
  particular, the existing String runtime representation is left unchanged until
  its standard Core bootstrap/protocol work is implemented; future specialized
  families can adopt the same bridge without adding dispatcher cases.


## [0.2.55-SNAPSHOT] - 2026-09-04

### Added

- Completed the I004 standard Array conformance audit against the current
  normative collection, object-model, callable, and Error contracts.
- Added exhaustive Array completion coverage for fresh/empty construction,
  dense boundary indexing, exact indexed-mutation result, closed/frozen
  mutation behavior, semantic Integer size, polymorphic non-Closure callback
  invocation, shallow snapshot order, callback failure propagation, ordinary
  identity/default equality, and receiver-domain rejection.
- Added explicit coverage that an ordinary object delegating to `Array` does
  not acquire standard Array indexed state, while an inherited Array factory
  still creates a real standard Array whose parent is the invocation receiver.
- Project implementation version changed from `0.2.54-SNAPSHOT` to `0.2.55-SNAPSHOT`.

### Notes

- The audit found the existing standard Array runtime behavior already aligned
  with the current normative surface; this completion closes the remaining
  conformance gaps without changing Array runtime semantics.
- No Array literal, growth, slicing, insertion/removal, negative-from-end
  indexing, or unrelated collection behavior is introduced.
- I001 (`args`), I002 (represented-value lookup), I007 (Error infrastructure),
  and the normative specification are intentionally untouched.



## [0.2.54-SNAPSHOT] - 2026-09-04

### Fixed

- I001: `args` now lowers uniformly to `ProtosArgsNode` instead of retaining a
  generic-lowering `UnsupportedOperationException` path for nested canonical
  expression shapes.
- Object-construction activations preserve the enclosing invocation's already
  materialized `args` Array while retaining existing construction `context`,
  receiver, lexical-capture, method-home, and return-home semantics.
- Added end-to-end coverage for zero/one/multiple arguments, ordering, mixed
  value families, element identity, fresh Array identity, nested Closure and
  method calls, polymorphic invocation, non-local return, and object bodies.
- Replaced the obsolete regression that explicitly expected `args` lowering to
  remain unimplemented.
- Project implementation version changed from `0.2.53-SNAPSHOT` to
  `0.2.54-SNAPSHOT`.

### Notes

- `args` remains the fresh frozen standard Array established from the flattened
  caller-supplied positional vector; receiver/default values are not inserted.
- No normative specification change is introduced.


## [0.2.53-SNAPSHOT] - 2026-09-04

### Fixed

- Updated `CanonicalizerEqualityTest` to the normative P66 lowering:
  source `!=` canonicalizes to an ordinary `!=` message send, and source `!==`
  canonicalizes to the dedicated non-dispatchable `CanonicalNotIdentity` form.
- Removed the obsolete test expectations that both operators lowered through
  the unrelated `not` selector.
- Project implementation version changed from `0.2.52-SNAPSHOT` to
  `0.2.53-SNAPSHOT`.

### Notes

- No runtime semantics changed.
- No normative specification change is introduced.


## [0.2.52-SNAPSHOT] - 2026-09-04

### Added

- Implemented standard `Object.==` using Protos semantic identity as the default
  equality for receivers without a nearer equality override.
- Implemented standard `Object.!=` as the strict Boolean complement of the
  receiver's dynamically selected current `==` behavior.
- `Object.!=` propagates equality errors and rejects a non-Boolean normal result
  instead of applying truthiness.
- Added a dedicated canonical non-identity form and Truffle execution node so
  `!==` is the primitive Boolean complement of `===` with no `==`, `!=`, or
  `not` message dispatch.
- Corrected source `!=` lowering to send the ordinary `!=` selector rather than
  synthesizing a `not` send after `==`.
- Added Java and executable `.protos` conformance coverage for default object
  equality, numeric inequality, fixed-width cross-family inequality, and
  primitive non-identity.
- Project implementation version changed from `0.2.51-SNAPSHOT` to
  `0.2.52-SNAPSHOT`.

### Notes

- Prefix `!` remains a separate implementation/spec audit item; this patch does
  not invent standard `not` behavior.
- No normative specification change is introduced.


## [0.2.51-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the standard numeric-equality wrong-arity regression test so it
  invokes the `==` selector directly with two supplied arguments through
  `ProtosInvocation`.
- Removed the invalid source spelling `1.==(1, 2)`, which is rejected by the
  parser before message dispatch and therefore could not test runtime arity
  behavior.
- Project implementation version changed from `0.2.50-SNAPSHOT` to
  `0.2.51-SNAPSHOT`.

### Notes

- No numeric equality runtime semantics changed.
- No normative specification change is introduced.


## [0.2.50-SNAPSHOT] - 2026-09-04

### Added

- Implemented standard Number-family `==` as an ordinary `Number`-owned
  Closure-valued slot inherited by Integer, Float, and all fixed-width integer
  prototypes.
- Numeric equality compares mathematical numeric value across semantic numeric
  families without performing arithmetic coercion or narrowing.
- Added exact Float-vs-exact-integer comparison using the actual represented
  binary64 value, avoiding rounded host-integer comparison.
- Implemented IEEE-style NaN equality (`NaN == x` is always false), signed-zero
  numeric equality, infinity equality, and cross-family exact-integer equality.
- Standard Number equality with a non-Number argument returns canonical `false`.
- Added receiver-domain protection so ordinary objects that merely inherit
  Number-family `==` are not treated as semantic Numbers.
- Added Java and executable `.protos` conformance coverage for cross-family,
  exact-rounding, NaN, signed-zero, infinity, and non-Number cases.
- Project implementation version changed from `0.2.49-SNAPSHOT` to
  `0.2.50-SNAPSHOT`.

### Notes

- `!=` is intentionally not implemented in this slice. The current canonicalizer
  lowers it through a `not` send, but Core's normative Boolean section does not
  currently define a standard `not` selector. That normative gap is left
  untouched rather than inventing behavior.
- No normative specification change is introduced.


## [0.2.49-SNAPSHOT] - 2026-09-04

### Added

- Implemented the standard Boolean protocol selectors `ifTrue`, `ifFalse`,
  `and`, and `or` as ordinary Object-owned Closure-valued slots reached by the
  canonical `true`/`false` delegation bridge.
- Added exact Boolean receiver-domain enforcement: standard Boolean behavior
  accepts only canonical `true` and `false`.
- Implemented selected-path ordinary polymorphic callback invocation with zero
  positional arguments and exact propagation of normal results for `ifTrue`
  and `ifFalse`.
- Implemented path-local callability validation: callbacks on unselected paths
  are neither validated nor invoked.
- Implemented canonical Boolean result validation for selected `and` and `or`
  callbacks, with invalid normal results signaling Error.
- Added `boolean` and `null` language-conformance expectation kinds and
  executable `.protos` cases for selected/unselected paths, short-circuiting,
  invalid callback results, and non-invokable selected callbacks.
- Project implementation version changed from `0.2.48-SNAPSHOT` to
  `0.2.49-SNAPSHOT`.

### Notes

- No standard `Boolean` prototype or prelude binding is introduced.
- The already-published direct canonical Boolean delegation to `Object` remains
  unchanged.
- No truthiness conversion, implicit awaiting, hidden suspension, or callback
  pre-validation is introduced.
- No normative specification change is introduced.


## [0.2.48-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard prototypes `UInt8`, `Int8`, `UInt16`, `Int16`,
  `UInt32`, `Int32`, `UInt64`, and `Int64`, each delegating directly through
  `Integer`.
- Added semantic fixed-width integer runtime values carrying exact family and
  mathematical value with range-enforced construction.
- Extended ordinary lookup to each fixed-width numeric prototype.
- Added all eight explicit fixed-width conversion factories with exact range
  checks for Integer, integral Float, and cross-family fixed-width inputs.
- Extended `Integer(...)` and `Float(...)` to accept fixed-width exact integers.
- Added Java and `.protos` conformance coverage and a `fixed-integer`
  expectation kind.
- Project implementation version changed from `0.2.47-SNAPSHOT` to
  `0.2.47-SNAPSHOT`.

### Notes

- Fixed-width arithmetic is intentionally deferred to P64.
- No implicit numeric promotion or wrapping is introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.47-SNAPSHOT] - 2026-09-04

### Added

- Implemented the canonical Boolean delegation bridge: represented `true` and
  `false` now continue ordinary lookup directly through `Object`.
- Added regression coverage for inherited Object lookup, original-receiver
  dispatch through polymorphic invocation, and absence of a standard `Boolean`
  prelude prototype.
- Closed implementation blocker B003 after D027 normatively fixed the Boolean
  parent topology.
- Project implementation version changed from `0.2.46-SNAPSHOT` to
  `0.2.47-SNAPSHOT`.

### Notes

- Canonical Booleans remain the existing host singleton representations
  `ProtosBooleanValue.TRUE` and `ProtosBooleanValue.FALSE`.
- No standard `Boolean`, `Value`, or other synthetic Protos-visible ancestor is
  introduced.
- No normative specification document or specification revision changed.

## [0.2.46-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P62 non-integral Float-to-Integer rejection test to use `1.5`,
  a binary64 value that is actually finite and mathematically non-integral.
- Removed the incorrect test assumption that the source literal
  `9007199254740991.5` remains non-integral after Float literal rounding; that
  decimal source rounds to the exact binary64 value `9007199254740992.0`,
  which is mathematically integral and therefore valid input to `Integer(...)`.
- Project implementation version changed from `0.2.45-SNAPSHOT` to
  `0.2.46-SNAPSHOT`.

### Notes

- No runtime conversion semantics changed.
- Existing P62 implementation remains unchanged.
- No normative specification change is introduced.
- D027/B003 remains untouched.


## [0.2.45-SNAPSHOT] - 2026-09-04

### Added

- Added standard one-argument ordinary invocation factories for the `Integer`
  and `Float` prototype objects.
- `Integer(value)` now accepts ordinary Integer values and finite mathematically
  integral Float values, returning the exact unbounded Integer without rounding
  or truncation.
- Float-to-Integer conversion derives the exact mathematical integer represented
  by binary64 bits rather than relying on decimal rendering or host narrowing.
- `Float(value)` now preserves existing Float semantic values and converts exact
  Integers with exact-to-binary64 `roundTiesToEven`, including precision loss and
  overflow to infinity required by Core.
- Added arity, non-Number, non-integral Float, NaN, infinity, and incompatible
  invocation-receiver rejection.
- Added receiver-domain protection so inheriting or copying a standard numeric
  factory `call` does not turn an ordinary object into a numeric conversion
  prototype.
- Added Java and executable `.protos` conformance coverage, including the
  adversarial exact value of `Integer(1e23)`.
- Project implementation version changed from `0.2.44-SNAPSHOT` to
  `0.2.45-SNAPSHOT`.

### Notes

- This slice implements the currently represented ordinary `Integer` and
  `Float` semantic families. Fixed-width conversion factories remain separate
  until those eight semantic families have runtime representations.
- No implicit numeric promotion or coercion is introduced by these explicit
  factories.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.44-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary Float arithmetic for `+`, `-`, `*`, `/`, and unary
  `negated`.
- Standard Float arithmetic operates directly on semantic binary64 operands and
  produces binary64 results, including signed zero, subnormal/underflow,
  infinity, and NaN behavior required by IEEE 754-2019.
- Float division by zero and invalid IEEE arithmetic now produce the
  corresponding Float infinity or NaN rather than a Protos Error.
- Added Float receiver-domain validation and rejection of mixed Float/Integer
  arithmetic without implicit numeric promotion or coercion.
- Added Java coverage for normal arithmetic, signed-zero negation, division by
  zero, overflow, underflow, NaN-producing operations, mixed-family rejection,
  and copied-method incompatible receivers.
- Added executable `.protos` Float conformance programs and a semantic
  `float-nan` expectation that does not expose implementation-specific NaN
  payload or sign bits.
- Project implementation version changed from `0.2.43-SNAPSHOT` to
  `0.2.44-SNAPSHOT`.

### Notes

- Java `double` is used here only as the host representation of Protos binary64
  primitive operations; no wider intermediate value is retained across a Protos
  operation boundary.
- NaN conformance intentionally tests semantic NaN membership rather than raw
  NaN payload bits, which are not portable Protos surface semantics.
- Numeric comparison/equality/hash and explicit numeric conversion factories
  remain separate implementation slices.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.43-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer / Integer` behavior returning the correctly
  rounded IEEE binary64 Float representation of the exact mathematical rational
  quotient.
- Added exact integer-arithmetic binary64 rounding with `roundTiesToEven`,
  including normal values, subnormals, signed underflow zero, overflow to
  infinity, and the normal/subnormal boundary.
- Integer division now rounds the exact rational quotient once rather than first
  converting each arbitrary-precision Integer operand to a host `double`.
- Added Java coverage for huge operands, halfway ties, subnormal rounding,
  signed zero, infinity, zero-divisor failure, and mixed-family rejection.
- Extended the language conformance manifest with `float-bits` expectations so
  Float results can be checked by exact raw binary64 representation.
- Added executable `.protos` conformance programs for Integer division,
  including adversarial cases where separate host-double operand conversion
  would produce the wrong result.
- Project implementation version changed from `0.2.42-SNAPSHOT` to
  `0.2.43-SNAPSHOT`.

### Notes

- This slice implements only ordinary `Integer / Integer`. Standard Float
  arithmetic and fixed-width integer-family division remain separate work.
- Exact zero divided by a nonzero Integer produces positive `0.0`; a nonzero
  exact quotient that rounds to zero preserves the quotient sign as required.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.42-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer.div(argument)` quotient behavior with
  truncation toward zero.
- Added standard ordinary `Integer.mod(argument)` and `%` remainder behavior
  using `a - (a div b) * b`, preserving the dividend sign for nonzero
  remainders.
- Added zero-divisor and mixed-numeric-family rejection for standard Integer
  quotient and remainder operations.
- Added exact arbitrary-precision quotient/remainder coverage in Java and
  executable `.protos` conformance programs.
- Added conformance cases for positive and negative operands, zero divisors,
  mixed Integer/Float rejection, and `%` equivalence with standard `mod`.
- Project implementation version changed from `0.2.41-SNAPSHOT` to
  `0.2.42-SNAPSHOT`.

### Notes

- Ordinary Integer `/` remains separate work because Core requires its Float
  result to be the correctly rounded binary64 representation of the exact
  rational quotient; it must not be implemented by separately rounding large
  Integer operands to host doubles before division.
- Float `div`, `mod`, and `%` are intentionally not introduced.
- Fixed-width integer-family quotient/remainder behavior remains separate until
  those semantic families are represented by the implementation.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.41-SNAPSHOT] - 2026-09-04

### Added

- Added the first end-to-end Protos language conformance harness.
- Added executable `.protos` conformance programs under
  `protos/tests/conformance/`, keeping the language test corpus independent from
  the Java/Maven resource layout.
- Added an external tab-separated expectation manifest so conformance assertions
  remain outside the Protos language itself.
- Added P57 Integer conformance programs covering small arithmetic, negative
  results, arbitrary-precision overflow boundaries, unary negation, and
  mixed-family Error cases.
- Conformance programs execute through `ProtosSourceFileLoader`, the normal
  source compiler/lowering/runtime path, and a freshly bootstrapped Core prelude.
- Project implementation version changed from `0.2.40-SNAPSHOT` to
  `0.2.41-SNAPSHOT`.

### Notes

- The JUnit runner remains under `src/test/java`, but the Protos conformance
  corpus is intentionally implementation-layout-independent under `protos/tests`.
- Existing Java unit/integration tests remain valuable for implementation
  invariants; Protos conformance tests complement rather than replace them.
- No test-only Protos syntax, assertion primitive, privileged test object, or
  standard-library testing API is introduced.
- Future observable language slices should add `.protos` conformance programs
  when their behavior can be expressed through the executable language surface.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.40-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Integer` arithmetic behavior for `+`, `-`, `*`, and
  unary `negated`.
- Ordinary Integer arithmetic now returns exact unbounded Integer values backed
  by arbitrary-precision arithmetic and therefore does not expose host-machine
  integer overflow.
- Standard Integer binary arithmetic rejects arguments from other numeric
  families instead of implicitly promoting or coercing them.
- Added receiver-domain validation so copying a standard Integer arithmetic
  Closure onto an ordinary object does not make that receiver a semantic
  Integer.
- Added coverage for large exact results, unary negation, mixed-family rejection,
  and incompatible receivers.
- Project implementation version changed from `0.2.39-SNAPSHOT` to `0.2.40-SNAPSHOT`.

### Notes

- Division, remainder/modulo, comparisons, conversion factories, Float
  arithmetic, and fixed-width integer families remain separate implementation
  work.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.39-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected P56 `Array.each` tests so they validate callback order and shallow
  snapshot behavior without depending on the not-yet-implemented standard
  numeric `+` operator.
- The revised tests use ordinary invokable native-backed Closure values only as
  test callbacks, preserving the same `Array.each` invocation path while
  isolating the behavior under test.
- Project implementation version changed from `0.2.38-SNAPSHOT` to `0.2.39-SNAPSHOT`.

### Notes

- No `Array.each` runtime semantics are changed.
- No numeric arithmetic behavior is introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.38-SNAPSHOT] - 2026-09-04

### Added

- Added standard `Array.size()` as a read-only ordinary Closure-valued protocol
  operation returning the semantic Integer indexed length.
- Added standard `Array.each(block)` with ordinary polymorphic callback
  validation, ascending shallow-snapshot traversal, one exact element argument
  per callback, and original-receiver normal result.
- Array iteration snapshots are isolated from later element replacement while
  preserving ordinary element identity and callback effects.
- Added coverage for open/closed/frozen size observation, iteration order,
  snapshot replacement behavior, callback validation, and exact receiver result.
- Project implementation version changed from `0.2.37-SNAPSHOT` to `0.2.38-SNAPSHOT`.

### Notes

- `each` invokes callbacks through the existing ordinary invocation protocol;
  it does not require callbacks to be Closures.
- No parallel Array operations are introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.37-SNAPSHOT] - 2026-09-04

### Added

- Added standard ordinary `Array.at(index)` and `Array.atPut(index, value)`
  Closure-valued protocol slots on the source-backed `Array` object.
- Standard Array indexed reads now require a semantic Integer index in the dense
  range `0 <= index < length` and return the exact stored element.
- Standard Array indexed updates replace exactly one existing element, preserve
  length, return the exact supplied value, allow replacement on closed Arrays,
  and reject mutation of frozen Arrays before index validation.
- Added executable lowering for `CanonicalIndexedAssign`, preserving the
  syntax-level evaluation order receiver -> index -> RHS -> `atPut` and returning
  the exact RHS after normal `atPut` completion.
- Added coverage for custom `atPut` return values, closed/frozen Arrays,
  non-Integer indices, and bounds failures.
- Project implementation version changed from `0.2.36-SNAPSHOT` to `0.2.37-SNAPSHOT`.

### Notes

- Bracket syntax remains ordinary `at` / `atPut` protocol dispatch rather than a
  privileged Array runtime operation.
- No insertion, growth, holes, negative-from-end indexing, or Array literals are
  introduced.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.36-SNAPSHOT] - 2026-09-04

### Added

- Added the standard source-backed `Array` object's ordinary local `call`
  specialization as an Array factory.
- `Array(...)` now creates a fresh open `ProtosArrayValue` containing the exact
  supplied positional objects in order, with no cloning, freezing, or
  Integer-length overload.
- Inherited Array-factory invocation uses the original invocation receiver as
  the new Array's delegation parent, so ordinary descendants such as `MyArray`
  construct Arrays delegating to that descendant.
- Added receiver-domain validation so copying the standard Array factory Closure
  onto an unrelated object does not make that object a standard Array-family
  factory.
- Project implementation version changed from `0.2.35-SNAPSHOT` to `0.2.36-SNAPSHOT`.

### Notes

- The factory is installed as an ordinary Closure-valued `call` slot on the
  source-loaded `Array` object; no hidden invocation special case is added.
- Indexed Array protocol methods remain separate implementation work.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.35-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P53 message-send spread test so it exercises spread flattening
  with an already-materialized `ProtosArrayValue` instead of depending on the
  still-pending standard `Array.call` factory specialization.
- Project implementation version changed from `0.2.34-SNAPSHOT` to `0.2.35-SNAPSHOT`.

### Notes

- No message-send runtime semantics are changed.
- No standard Array factory behavior is introduced by this correction.
- D027/B003 remains untouched.
- No normative specification change is introduced.


## [0.2.34-SNAPSHOT] - 2026-09-04

### Added

- Added executable lowering for `CanonicalSend` through a dedicated
  `ProtosSendNode`.
- Added direct ordinary message invocation for arbitrary Protos receivers using
  the existing semantic value lookup bridge, preserving original receiver and
  physical `methodHome`.
- Added coverage for local and inherited method sends, argument/spread handling,
  and missing-message Core Error behavior.
- Project implementation version changed from `0.2.33-SNAPSHOT` to `0.2.34-SNAPSHOT`.

### Changed

- Callable lowering now recursively supports nested `CanonicalSend` expressions
  in closure bodies and defaults.

### Notes

- Message send remains ordinary slot lookup plus Closure activation; no parallel
  host dispatch mechanism is introduced.
- D027/B003 remains untouched: this change does not choose a parent for
  canonical `true` or `false` and does not introduce a `Boolean` prototype.
- No normative specification change is introduced.


## [0.2.33-SNAPSHOT] - 2026-09-04

### Fixed

- Allowed semantic member lookup to traverse ordinary-object delegation chains
  without requiring a Core prelude when no represented value boundary is
  crossed.
- Required the Core prelude lazily only when the semantic lookup walker actually
  reaches represented Integer or Float values.
- Restored extracted-Closure member-read execution in minimal activations while
  preserving represented numeric lookup through source-backed Core prototypes.
- Project implementation version changed from `0.2.32-SNAPSHOT` to `0.2.33-SNAPSHOT`.

### Notes

- D027/B003 remains untouched. No parent for canonical `true` or `false` is
  selected and no `Boolean` prototype is introduced.
- No normative specification change is introduced.


## [0.2.32-SNAPSHOT] - 2026-09-04

### Fixed

- Routed member lookup through the semantic value walker even when the initial
  receiver is an ordinary object, so an ordinary delegation chain can continue
  through a represented numeric value and then into its source-backed
  `Integer`/`Float`/`Number` prototype chain.
- Prevented the legacy `ProtosObjectValue.lookupSlot` host exception from
  escaping that mixed ordinary/represented delegation path.
- Preserved the existing language-level Core Error behavior for represented
  value families whose prototype bridge is not implemented in this slice.
- Project implementation version changed from `0.2.31-SNAPSHOT` to `0.2.32-SNAPSHOT`.

### Notes

- No delegation parent is selected for canonical `true` or `false`; D027/B003
  remains untouched.
- No `Boolean` prototype or Boolean fallback is introduced.
- No normative specification change is introduced.


## [0.2.31-SNAPSHOT] - 2026-09-04

### Fixed

- Restored minimal/internal `ProtosPrelude` construction without requiring
  source-backed `Number`, `Integer`, and `Float` bindings in every prelude.
  Full Core bootstrap remains responsible for loading and validating the numeric
  hierarchy.
- Ordinary `ProtosObjectValue` member reads no longer require an owning Core
  prelude. A prelude is required only when lookup crosses from a represented
  non-ordinary runtime value into its source-backed standard prototype chain.
- Corrected P52 numeric receiver-binding tests to use member extraction followed
  by the already-supported ordinary `CanonicalCall` path instead of unsupported
  `CanonicalSend` lowering.
- Project implementation version changed from `0.2.30-SNAPSHOT` to `0.2.31-SNAPSHOT`.

### Notes

- Numeric hierarchy semantics from P52 are unchanged.
- D027/B003 remains untouched: no parent is chosen for canonical `true` or
  `false`, and no `Boolean` prototype is introduced.
- No normative specification change is introduced.


## [0.2.30-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard `Number`, `Integer`, and `Float` prototype objects
  under `protos/lib/core/`.
- Added the runtime value-lookup bridge that maps semantic Integer and Float
  value representations into those source-backed ordinary prototype chains.
- Added coverage for numeric prototype hierarchy, inherited numeric lookup,
  exact receiver preservation, and ordinary objects delegating to numeric
  values.
- Added implementation blocker B003 so canonical Boolean parentage remains
  explicitly deferred to D027 rather than being guessed by the runtime.

### Changed

- Core bootstrap now loads and validates `Number -> Object`,
  `Integer -> Number`, and `Float -> Number`, then publishes those exact objects
  in the frozen prelude.
- Member lookup, invocation lookup, and activation receiver fallback now use the
  semantic value lookup bridge when a Core prelude is available.
- Project implementation version changed from `0.2.29-SNAPSHOT` to `0.2.30-SNAPSHOT`.

### Notes

- This change intentionally does not choose a parent for canonical `true` or
  `false`, does not create a `Boolean` prototype, and does not install Boolean
  lookup behavior. That work remains blocked on D027.
- String/null and the remaining standard value families are not assigned
  substitute parentage by this increment.
- No normative specification change is introduced.


## [0.2.29-SNAPSHOT] - 2026-09-04

### Added

- Added ordinary parenthesized invocation through the normative `call` slot protocol.
- Added `CanonicalCall` lowering, target-before-argument evaluation, spread flattening, ordinary `call` lookup, Closure validation, receiver/method-home binding, and terminal direct Closure activation.
- Installed standard `Object.call` and `Object.init` as ordinary Closure-valued slots on `Object`, with default construction and D022's `Object.init() -> this` result.

### Changed

- `ProtosClosureValue` now participates in the ordinary object/delegation model as a direct child of `Object`; standard Closure invocation therefore inherits `Object.call` rather than using a hidden callable flag.
- Extracted/bound Closure wrappers preserve ordinary local slots and structural state while replacing only receiver/method-home binding metadata.
- Project implementation version changed from `0.2.28-SNAPSHOT` to `0.2.29-SNAPSHOT`.

### Tests

- Added end-to-end coverage for plain Closure calls, local and inherited `call`, incompatible shadowing, default construction, overridden and standard `init`, spread, nested calls, and non-local return across ordinary calls.

### Notes

- Host-represented primitive value prototype bridging and standard Array/Map/numeric `call` specializations remain later implementation layers.
- No normative specification change is introduced.


## [0.2.28-SNAPSHOT] - 2026-09-04

### Changed

- Closed implementation blocker B002 for the runtime semantics of
  `Object.without(name)` and `Object.alias(sourceName, aliasName)` structural
  views.
- Structural-view results now always use the unique root `Object` as their
  immediate delegation parent rather than accepting an implementation-selected
  parent.
- The result remains a fresh open ordinary object regardless of the source
  object's parent or open/closed/frozen state.
- Project implementation version changed from `0.2.27-SNAPSHOT` to `0.2.28-SNAPSHOT`.

### Tests

- Extended object-runtime coverage for fresh identity, root-`Object` parent,
  open result state, frozen-source behavior, shallow exact-value copying,
  mutation independence, and delegated alias-name non-collision.

### Notes

- This block implements the now-closed runtime object semantics without exposing
  new Protos-visible messages yet; ordinary message dispatch remains pending the
  invocation-protocol work.
- `CanonicalCall` remains deliberately unopened while the current specification
  does not yet define the portable inheritance/replacement mechanism of the
  ordinary invocation protocol.
- No normative specification change is introduced.


## [0.2.27-SNAPSHOT] - 2026-09-04

### Added

- Added source-backed standard `InvalidReturn` under `protos/lib/core/`, with
  direct delegation to the standard `Error` prototype.
- Added `ProtosReturnNode` and a dedicated internal non-local-return control
  transfer carrying the exact target home and result value.
- Callable-plan lowering now lowers canonical `^value` in Closure defaults and
  bodies.
- Added fresh `InvalidReturn` construction through the activation-owned Core
  prelude.

### Changed

- Selected Closure invocation now catches a non-local return only when the
  invocation owns the exact target home; nested invocations sharing a captured
  home rethrow the transfer unchanged.
- An owned home is still completed on every exit path, including a handled
  non-local return.
- Project implementation version changed from `0.2.26-SNAPSHOT` to `0.2.27-SNAPSHOT`.

### Tests

- Added integrated coverage for direct active `^`, `^` from a default
  expression, nested captured-home propagation, escaped-Closure
  `InvalidReturn`, fresh Error identity, and the exact source-backed
  `InvalidReturn -> Error` prototype relationship.
- Extended Core bootstrap coverage for the standard `InvalidReturn` prototype.

### Notes

- This block closes non-local return execution for already selected Closures.
- Ordinary `CanonicalCall`, message send, `super`, and polymorphic object
  invocation remain outside this block.
- No normative specification change is introduced.


## [0.2.26-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosClosureInvoker` for executing an already selected Closure through
  activation establishment, normative parameter binding, and body execution.
- Closure execution plans now own reusable Truffle call targets for binding and
  body execution.

### Changed

- An invocation-owned return home is completed when that invocation leaves its
  dynamic binding/body extent, including failure exits.
- Nested Closure invocations that reuse a captured return home never complete
  that home themselves.
- Project implementation version changed from `0.2.25-SNAPSHOT` to `0.2.26-SNAPSHOT`.

### Tests

- Added coverage for selected-Closure invocation, normal owned-home completion,
  binding failure propagation, and preservation of a captured nested return home.

### Notes

- This increment still does not lower or dispatch `CanonicalCall`/message sends.
- Non-local `^` transfer itself remains unopened; this slice establishes the
  lifecycle boundary it will target.
- No normative specification change is introduced.


## [0.2.25-SNAPSHOT] - 2026-09-04

### Fixed

- Corrected the P47 callable-lowering test to import `ProtosParser` from its
  actual `com.guillermomolina.protos.parser` package.

### Changed

- Project implementation version changed from `0.2.24-SNAPSHOT` to `0.2.25-SNAPSHOT`.

### Notes

- No production implementation or Protos semantics changed in this corrective
  commit.



## [0.2.24-SNAPSHOT] - 2026-09-04

### Added

- Closure lowering now prepares an implementation-private execution plan
  containing the parameter-binding node and body node without invoking either.
- Callable-plan lowering recognizes `args` in Closure defaults and bodies and
  lowers it to `ProtosArgsNode`.
- Materialized Closure values retain their prepared execution plan, including
  across extracted-method binding.

### Changed

- General program/module lowering continues to reject `args`; only callable
  plans receive invocation-context `args` lowering.
- Project implementation version changed from `0.2.23-SNAPSHOT` to `0.2.24-SNAPSHOT`.

### Tests

- Added coverage proving both a default expression and a Closure body plan can
  observe the exact activation-owned `args` Array.

### Notes

- This increment does not execute calls, parameter binding automatically, or
  Closure bodies automatically; it only prepares the complete callable plan.
- Return-home completion and `^` execution remain unopened.
- No normative specification change is introduced.


## [0.2.23-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosArgsNode`, an invocation-only execution node that returns the
  exact standard frozen Array already established on the current Closure
  activation.

### Changed

- Project implementation version changed from `0.2.22-SNAPSHOT` to `0.2.23-SNAPSHOT`.

### Tests

- Added focused coverage proving `args` returns the exact activation-owned Array,
  retaining its source-backed standard `Array` parent and frozen state.

### Notes

- This increment intentionally does not enable `args` in the general
  program/module lowerer because Core defines it as an invocation-context
  binding and does not assign a substitute value outside invocation.
- Callable-body/default lowering will use this node when the Closure execution
  plan is connected.
- No normative specification change is introduced.


## [0.2.22-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosParameterBindingNode`, implementing the normative left-to-right
  Closure parameter-binding algorithm over an already established invocation
  activation.
- Binding supports supplied arguments, real-activation default evaluation,
  trailing rest capture as a distinct fresh frozen standard Array, and generic
  argument-count Error signaling.

### Changed

- Project implementation version changed from `0.2.21-SNAPSHOT` to `0.2.22-SNAPSHOT`.

### Tests

- Added coverage for earlier-parameter visibility from defaults, exact `args`
  preservation, distinct frozen rest Arrays, missing required parameters, and
  deferred excess-argument detection.

### Notes

- Parameter names are created only after their supplied/default value is
  obtained; there is no predeclaration or arity preflight.
- Slot-creation conflicts during parameter establishment use ordinary generic
  Error signaling.
- This increment does not yet execute `CanonicalCall`, lower the `args`
  intrinsic, execute Closure bodies, or complete return homes.
- No normative specification change is introduced.


## [0.2.21-SNAPSHOT] - 2026-09-04

### Added

- Closures now preserve their implementation-private owning `ProtosPrelude`
  together with lexical contexts and callable control metadata.
- Added `ProtosActivation.forClosureInvocation(...)`, which atomically
  establishes a fresh execution context, exact captured receiver and lexical
  contexts, source-backed frozen `args`, `methodHome`, and return-home state.
- Invocation activations record whether they own a newly established return home
  or reuse a captured lexical home.

### Changed

- Closure literal materialization now captures the exact owning prelude.
- Project implementation version changed from `0.2.20-SNAPSHOT` to `0.2.21-SNAPSHOT`.

### Tests

- Added coverage for complete top-level Closure activation establishment and for
  nested Closure reuse of the exact captured return home.

### Notes

- This increment still does not execute `CanonicalCall`, bind parameters or
  defaults/rest, execute Closure bodies, complete return homes, or execute `^`.
- No partial observable call path is introduced.
- No normative specification change is introduced.


## [0.2.20-SNAPSHOT] - 2026-09-04

### Added

- Added implementation-private activation `methodHome` state matching the
  normative `super` lookup model.
- Closure literals now capture the current activation's `methodHome` together
  with receiver and return-home metadata.

### Changed

- Object-construction execution preserves enclosing `methodHome` metadata while
  continuing not to become a lexical capture scope.
- Project implementation version changed from `0.2.19-SNAPSHOT` to `0.2.20-SNAPSHOT`.

### Tests

- Added focused coverage proving construction preserves the exact enclosing
  method home.

### Notes

- This increment does not execute `super`, perform callable dispatch, or create
  method activations yet.
- No normative specification change is introduced.


## [0.2.19-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosReturnHome`, an implementation-private identity object with
  explicit active/completed lifecycle state for callable non-local return
  ownership.
- `ProtosActivation` can now carry an optional lexical return home, and object
  construction preserves the enclosing home.
- `ProtosClosureValue` now captures and preserves the exact lexical return home,
  including across extracted-method binding.

### Changed

- Closure literal materialization now records the current activation's return
  home when one exists.
- Project implementation version changed from `0.2.18-SNAPSHOT` to
  `0.2.19-SNAPSHOT`.

### Tests

- Added focused return-home lifecycle and object-construction propagation
  coverage.

### Notes

- This increment does not yet execute `^`, create callable activations, or
  establish fresh invocation return homes.
- It does not add activation-level `methodHome`; `super` activation state remains
  a separate subsequent slice.
- No normative specification change is introduced.


## [0.2.18-SNAPSHOT] - 2026-09-04

### Added

- Added implementation-private invocation-argument state to `ProtosActivation`.
  Non-invocation activations expose no argument Array.
- Added `ProtosPrelude.newFrozenArray(...)`, producing a fresh frozen standard
  Array with the exact source-backed `Array` prototype.

### Changed

- Test preludes now include the mandatory standard `Array` binding introduced
  by the source-backed Core bootstrap.
- Project implementation version changed from `0.2.17-SNAPSHOT` to
  `0.2.18-SNAPSHOT`.

### Tests

- Extended Core bootstrap coverage for fresh frozen standard Array
  materialization.

### Notes

- This increment prepares the activation representation required by the
  normative `args` semantics but does not yet establish callable activations,
  bind parameters, or lower the `args` intrinsic.
- No observable invocation shortcut or partial call execution is introduced.
- No normative specification change is introduced.


## [0.2.17-SNAPSHOT] - 2026-09-04

### Added

- Added distributable `protos/lib/core/array.protos`, defining the standard
  `Array` prototype as an ordinary child of `Object`.
- Added `ProtosPrelude.arrayPrototype()` and `newArray(...)` so runtime
  machinery can materialize standard Arrays with the exact source-backed
  Array prototype as delegation parent.

### Changed

- Core bootstrap now loads and validates the `Array` binding before freezing
  the standard prelude and installs that exact source-created object alongside
  `Context` and `Error`.
- Project implementation version changed from `0.2.16-SNAPSHOT` to
  `0.2.17-SNAPSHOT`.

### Tests

- Extended Core bootstrap coverage to verify the exact source-backed Array
  binding, its delegation parent, and the parent of a materialized standard
  Array value.

### Notes

- This increment does not implement Array invocation, `at`, `atPut`, `size`,
  `each`, `args`, rest binding, or any other Array protocol.
- No hardcoded Java Array prototype singleton is introduced.
- No normative specification change is introduced.


## [0.2.16-SNAPSHOT] - 2026-09-04

### Added

- Added distributable `protos/lib/core/error.protos`, defining the mandatory
  standard `Error` prototype as an ordinary child of `Object`.

### Changed

- Core bootstrap now executes both `context.protos` and `error.protos`, validates
  their exact required parent relationships, installs both bindings into the
  frozen standard prelude, and exposes `Error` through that prelude.
- Runtime language failures now create fresh error objects whose parent is the
  exact source-backed `Error` prototype owned by the current activation's
  prelude.
- `ProtosCoreErrors` is now a stateless runtime factory; its process-global
  `ERROR_PROTOTYPE` singleton and `errorPrototype()` accessor were removed.
- Project implementation version changed from `0.2.15-SNAPSHOT` to
  `0.2.16-SNAPSHOT`.

### Tests

- Runtime error tests now use an explicit test prelude rather than the removed
  production Error singleton.
- Core bootstrap coverage verifies the source-backed Error binding and its
  direct delegation to Object.

### Notes

- A module-local binding named `Error` cannot redirect runtime-generated Core
  errors because runtime failure identity is obtained from the activation's
  owning prelude, not ordinary shadowable lexical lookup.
- No second Error identity or process-global standard Error object remains.
- No normative specification change is introduced.


## [0.2.15-SNAPSHOT] - 2026-09-04

### Changed

- Activations created by a `ProtosPrelude` now retain an implementation-private
  reference to that exact owning prelude.
- Object-construction activations preserve the owning prelude from their
  enclosing activation.
- Legacy direct activation construction remains available for bootstrap and
  focused runtime tests and carries no implicit process-global prelude.
- Project implementation version changed from `0.2.14-SNAPSHOT` to
  `0.2.15-SNAPSHOT`.

### Tests

- Added coverage that module activations retain their exact prelude and that
  object-construction activations propagate it unchanged.

### Notes

- This is runtime plumbing only and introduces no Protos-visible binding,
  lookup rule, identity, or behavior change.
- The explicit prelude reference is the foundation for resolving standard Core
  identities such as `Error` without using shadowable lexical lookup or
  process-global standard-object singletons.
- No normative specification change is introduced.


## [0.2.14-SNAPSHOT] - 2026-09-04

### Changed

- `ProtosPrelude` now owns an explicit frozen ordinary Protos bindings context
  rather than only retaining the `Context` prototype reference.
- `ProtosCoreBootstrap` now constructs that real prelude context after loading
  `Context`: it delegates to `Context`, contains the exact `Context` binding,
  and is frozen before becoming observable to later runtime stages.
- Added `ProtosPrelude.newModuleActivation()` so a fresh module context captures
  the frozen standard prelude through the ordinary lexical-context mechanism.
- Project implementation version changed from `0.2.13-SNAPSHOT` to
  `0.2.14-SNAPSHOT`.

### Tests

- Added coverage for frozen prelude structure and ordinary lexical lookup of the
  `Context` binding from a module activation.
- Extended Core bootstrap coverage to verify the source-backed prelude context.

### Notes

- This does not add any new standard objects or hardcoded Core behavior.
- The prelude remains explicit runtime state; no process-global mutable prelude
  is introduced.
- No normative specification change is introduced.


## [0.2.13-SNAPSHOT] - 2026-09-04

### Removed

- Removed the temporary static `ProtosCorePrelude` and its Java-constructed
  standard `Context` prototype.
- Removed the obsolete tests that treated that static scaffold as the Core
  prelude.

### Changed

- Migrated remaining test execution-context construction to explicit
  `ProtosPrelude` state or, for the Core bootstrap test itself, to the
  irreducible root-backed bootstrap context.
- Updated the Core bootstrap architecture note to record that the Java-side
  `Context` scaffold has been retired.
- Project implementation version changed from `0.2.12-SNAPSHOT` to
  `0.2.13-SNAPSHOT`.

### Notes

- The standard `Context` identity is now constructed by
  `protos/lib/core/context.protos` through `ProtosCoreBootstrap`.
- No replacement process-global standard prototype is introduced.
- No normative specification change is introduced.


## [0.2.12-SNAPSHOT] - 2026-09-04

### Added

- Added explicit `ProtosPrelude` runtime state whose `Context` prototype is
  supplied rather than hardcoded.
- Added `ProtosCoreBootstrap`, which executes distributable
  `protos/lib/core/context.protos` through the ordinary source pipeline and
  returns a prelude backed by the resulting ordinary `Context` object.
- Added focused coverage for explicit prelude context creation and source-backed
  Core bootstrap.

### Changed

- Project implementation version changed from `0.2.11-SNAPSHOT` to
  `0.2.12-SNAPSHOT`.

### Notes

- Core bootstrap uses a short-lived internal root-backed context only to create
  the first standard `Context`; that bootstrap context is not a Protos module
  instance or a new standard prototype.
- No process-global mutable prelude state is introduced. `ProtosPrelude` is an
  explicit object so later Actor ownership can remain local.
- The older static `ProtosCorePrelude` remains for one migration increment and
  is not extended.
- No normative specification change is introduced.


## [0.2.11-SNAPSHOT] - 2026-09-04

### Added

- Added the first executable distributable Core source,
  `protos/lib/core/context.protos`.
- The Core source constructs the standard `Context` prototype as an ordinary
  Protos object with `Object` as its delegation parent.
- Added coverage that loads and executes that Core source through the ordinary
  source-file/compiler pipeline and observes the resulting `Context` binding.

### Changed

- Project implementation version changed from `0.2.10-SNAPSHOT` to `0.2.11-SNAPSHOT`.

### Notes

- The existing Java-side `Context` in `ProtosCorePrelude` remains temporary
  bootstrap scaffolding for now. This increment establishes the source-side
  replacement before changing activation construction to consume it.
- No special Core parser, AST, object constructor, or Java-side standard
  prototype was added.
- No normative specification change is introduced.


## [0.2.10-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosSourceFileLoader` to read UTF-8 Protos source from a host `Path`
  and compile it through the existing ordinary source compiler pipeline.
- Added focused coverage for successful UTF-8 loading and host file-loading
  failure propagation.

### Changed

- Project implementation version changed from `0.2.9-SNAPSHOT` to
  `0.2.10-SNAPSHOT`.

### Notes

- This is host-side implementation plumbing, not the Protos language I/O model.
  It does not expose `Path`, Java NIO, or file-loading behavior to Protos code.
- The loader deliberately does not define module identity, import caching,
  bootstrap order, or Core object identities. Those higher-level semantics stay
  outside this increment.
- This entry point is intended for the upcoming `protos/lib/core/` bootstrap
  loader and ordinary module-loading machinery.
- No normative specification change is introduced.


## [0.2.9-SNAPSHOT] - 2026-09-04

### Added

- Added `ProtosSourceCompiler`, a single source-to-execution entry point that
  composes the existing parser, canonicalizer, Truffle lowerer, and call-target
  construction pipeline.
- Added focused coverage proving compiled source preserves ordinary sequence,
  literal, object-construction, and parser-failure behavior.

### Changed

- Project implementation version changed from `0.2.8-SNAPSHOT` to
  `0.2.9-SNAPSHOT`.

### Notes

- This is implementation plumbing only. It introduces no new syntax, lookup,
  invocation, object, or bootstrap semantics.
- The new entry point is intended to be reused by the upcoming
  `protos/lib/core/` loader so Core source and user source travel through the
  same ordinary compiler pipeline.
- No normative specification change is introduced.


## [0.2.8-SNAPSHOT] - 2026-09-04

### Fixed

- Fixed `ProtosArgumentVectorNodeTest` to use the actual `SourceSpan` constructor
  instead of a nonexistent `SourceSpan.unknown()` helper.
- Project implementation version changed from `0.2.7-SNAPSHOT` to
  `0.2.8-SNAPSHOT`.

### Notes

- This is a test-compilation correction only; caller argument-vector semantics
  and implementation behavior are unchanged.
- No normative specification change is introduced.


## [0.2.7-SNAPSHOT] - 2026-09-04

### Added

- Added the caller-supplied positional-vector evaluation stage for future
  polymorphic invocation.
- Ordinary argument items are evaluated exactly once from left to right.
- Spread items require standard Array indexed state and append a shallow
  ascending-index snapshot at their exact evaluation position.
- Invalid spread sources signal Core `Error` immediately and prevent later
  argument evaluation.

### Changed

- Project implementation version changed from `0.2.6-SNAPSHOT` to `0.2.7-SNAPSHOT`.

### Notes

- This increment deliberately stops before Closure activation and parameter
  binding. It therefore does not need to manufacture the standard frozen
  `args` Array before the Core `Array` prototype is available from
  `protos/lib/core/`.
- No standard Array prototype or protocol behavior is hardcoded in Java.
- No normative specification change is introduced.


## [0.2.6-SNAPSHOT] - 2026-09-04

### Changed

- Removed temporary Java-side `Number`, `Integer`, and `Float` prototype objects
  from `ProtosCorePrelude`.
- Removed the Java-side numeric-family prototype resolver and the tests that
  treated those temporary objects as the implementation's standard numeric
  prelude.
- Kept only the explicitly documented temporary `Context` bootstrap scaffold.
- Project implementation version changed from `0.2.5-SNAPSHOT` to
  `0.2.6-SNAPSHOT`.

### Notes

- Numeric value representations and already-implemented numeric literal/identity
  semantics are unchanged.
- This corrects an implementation-architecture regression: standard numeric
  prototype objects belong to the future `protos/lib/core/` bootstrap path
  rather than a growing hardcoded Java standard library.
- No normative specification change is introduced.


## [0.2.5-SNAPSHOT] - 2026-09-04

### Added

- Added an internal standard-Array object representation with receiver-owned
  dense indexed state distinct from ordinary object slots.
- Array indexed reads and updates use mathematical `BigInteger` indices,
  preserve exact element references, reject negative/out-of-range indices, and
  never grow or create holes.
- Closed Arrays may replace existing indexed elements while frozen Arrays reject
  replacement before index validation.
- Added detached shallow indexed snapshots for future call-spread and iteration
  semantics.

### Changed

- `ProtosObjectValue` is now extensible internally so specialized object
  representations can retain the ordinary object/delegation/slot model without
  adding parallel language object categories.
- Project implementation version changed from `0.2.4-SNAPSHOT` to
  `0.2.5-SNAPSHOT`.

### Notes

- This increment adds representation only. It does not hardcode or expose the
  standard `Array` prelude object or its protocol methods in Java.
- The explicit parent supplied to each Array instance preserves the normative
  Array-factory rule and will allow `protos/lib/core/` to own the standard
  prototype object when Core bootstrap is available.
- No normative specification change is introduced.


## [0.2.4-SNAPSHOT] - 2026-09-04

### Added

- Added execution of canonical composition items inside object bodies.
- Composition evaluates its source first, copies effective local bindings into
  the object under construction, and makes successful contributions immediately
  visible to later body items.
- Direct local declarations reserve their names structurally across the complete
  receiving object body, excluding those names from every composition item.
- Composition conflicts and invalid non-ordinary composition sources now signal
  Core `Error` objects instead of leaking host exceptions.

### Changed

- Canonical object-body lowering now supplies the object's structural reservation
  set to each composition item while preserving strict left-to-right body
  execution.
- Project implementation version changed from `0.2.3-SNAPSHOT` to
  `0.2.4-SNAPSHOT`.

### Notes

- Composition reuses the existing atomic runtime contribution helper, so a
  conflicting item installs none of its effective bindings.
- `without` and `alias` remain blocked by B002 and are not exposed by this
  increment.
- No normative specification change is introduced.


## [0.2.3-SNAPSHOT] - 2026-09-04

### Added

- Added Truffle execution for canonical object expressions without composition.
- Bare object expressions now create fresh open ordinary objects delegating to
  the unique `Object` root.
- Explicit parent expressions are evaluated before object-body execution and
  their exact result becomes the constructed object's immutable delegation
  parent.
- Object bodies execute through construction activations, so local slot creation
  targets the new object while Closures skip the construction object as a lexical
  capture scope.

### Changed

- Canonical-to-Truffle lowering now accepts `CanonicalObject` when all body
  expressions are otherwise supported by the current execution slice.
- Project implementation version changed from `0.2.2-SNAPSHOT` to
  `0.2.3-SNAPSHOT`.

### Notes

- Canonical composition execution remains a separate following increment.
- No normative specification change is introduced; this implements the current
  object-model and execution-context contracts.


## [0.2.2-SNAPSHOT] - 2026-09-04

### Added

- Added explicit object-construction activations whose current context and
  receiver are the object under construction while Closure capture skips that
  construction object.
- Added transitive construction-scope skipping so Closures created inside nested
  object bodies capture only genuine enclosing lexical contexts.

### Changed

- Closure materialization now obtains its lexical capture chain from activation
  semantics instead of unconditionally capturing the activation's current
  context.
- Project implementation version changed from `0.2.1-SNAPSHOT` to
  `0.2.2-SNAPSHOT`.

### Notes

- This is implementation architecture for already-specified object-construction
  and lexical-capture semantics; no normative specification change is introduced.
- The new construction-activation boundary is intended to support subsequent
  canonical object execution and Core source bootstrap.


## [0.2.1-SNAPSHOT] - 2026-09-04

### Added

- Added executable numeric literal materialization and semantic identity execution.
- Added the runtime ordinary-object foundation with immutable delegation parents,
  local/delegated lookup, slot mutation, structural open/closed/frozen state,
  local-slot removal and snapshots, composition views, and atomic composition
  contributions.
- Added activation-context lookup ordering, Core error signaling, `this` and
  `context` execution, bare/member slot mutation, member lookup-home preservation,
  Closure materialization, and extracted-method binding foundations.
- Added empty-Sequence execution returning canonical `null`.
- Added canonical object composition-reservation discovery for direct local
  declarations.
- Added the initial standard `Context` bootstrap scaffold and fresh execution
  contexts delegating through `Context` to `Object`.
- Added the non-normative Core bootstrap architecture and reserved
  `protos/lib/core/` for standard objects and behavior implemented in Protos.

### Changed

- Project implementation version changed from `0.2.0-SNAPSHOT` to
  `0.2.1-SNAPSHOT`.
- Established an explicit repository rule requiring every committed executable
  implementation or distributable Core-library change to bump the Maven
  implementation patch version and add its corresponding root changelog entry.
- Limited Java-side standard-object construction to irreducible or explicitly
  temporary bootstrap scaffolding; ordinary Core behavior should move to
  `protos/lib/core/` as soon as it can be loaded faithfully.

### Notes

- This entry catches up implementation release metadata that was not maintained
  during the preceding incremental runtime/execution work.
- Protos Core language version remains 0.1.
- No normative language semantics are changed by the bootstrap architecture
  decision; observable Core behavior remains defined exclusively by `spec/`.
- The current Java-side `Context` bootstrap is temporary scaffolding until the
  Core source loader can construct it faithfully.

## [0.2.0-SNAPSHOT] - 2026-09-01

### Added

- Added canonical String literal execution as ordinary immutable Protos String values.

- Added initial Canonical-to-Truffle lowering for canonical `true`, `false`, and `null` singleton literals and non-empty sequences.

- Added a Truffle CallTarget entry point for executing Protos expression trees through the root-node boundary.
- Added the initial Truffle root execution boundary, delegating directly to the executable expression tree without introducing language-value semantics.

- Added the first executable Truffle node: non-empty expression sequences execute strictly left-to-right and return the final child result.
- Added the initial Truffle execution-node boundary with source-span preservation and the Truffle API dependency.
- Added canonical call-spread lowering with a contextual `CanonicalSpread(expression)` marker for ordinary, member, and super invocation arguments.
- Added dedicated canonical indexed-assignment lowering that preserves receiver/index/value evaluation structure and the distinct `atPut` assignment-result semantics.
- Added dedicated canonical lowering for non-spread `super.message(arguments...)` operations, preserving super lookup semantics separately from ordinary message sends.
- Added dedicated canonical intrinsic nodes for the reserved execution-context expressions `this`, `context`, and `args`, keeping them distinct from ordinary lexical lookup.
- Added canonical lowering for non-spread calls, preserving the semantic distinction between ordinary `Call(receiver, arguments)` and member message `Send(receiver, message, arguments)` forms.
- Added canonical object lowering with optional explicit parent, canonical object-body sequencing, and `Compose(object)` nodes for contextual composition items.
- Added canonical `Return(value)` lowering for the `^ expression` non-local return form.
- Added canonical `Create`/`Assign` lowering for bare and explicit-member slot writes while leaving indexed assignment for its distinct `atPut` semantics.
- Added canonical lowering for indexed reads as ordinary one-argument `at` message sends.
- Added canonical lowering for lazy `&&` and `||` as `and`/`or` message sends whose right-hand side is wrapped in a parameterless canonical Closure.
- Added canonical Closure and parameter lowering, including mandatory normalization of expression-bodied Closures to a one-expression canonical Sequence.
- Added canonical lowering for semantic equality/inequality and non-overridable identity/non-identity, with a dedicated canonical identity node.
- Added canonical lowering for the standard comparison operators `<`, `<=`, `>`, and `>=` as ordinary one-argument message sends.
- Added canonical lowering for the standard arithmetic operators `+`, `-`, `*`, `/`, and `%` as ordinary one-argument message sends.
- Added canonical lowering for custom symbolic binary operators as ordinary one-argument message sends while leaving standard binary operators for dedicated semantic lowering.
- Added canonical lowering for Core prefix `-` and `!` as ordinary zero-argument `negated` and `not` sends.
- Added the canonical semantic AST foundation and the first Surface AST canonicalization slice for literals, name lookup, grouping, member reads, and sequences.
- Established `.protos` as the project source-file extension and added initial non-normative tutorial, task-oriented example, and portable benchmark corpora.
- Added benchmark workloads for recursion, slots, closure and method calls, object creation, delegation depth, and monomorphic/polymorphic dispatch.
- Added parser support for same-line parameterless trailing closures as the final argument of ordinary call suffixes.
- Added closure surface AST integration and deterministic parsing for closure parameters, defaults, rest parameters, and braced or expression bodies.
- Added parser support for object expressions, parent expressions, and contextual object composition items.
- Added parser support for structural super message sends without making `super` a first-class expression.
- Added parsing for slot creation and assignment with grammar-defined target restrictions.
- Added deterministic parsing for the grammar-defined non-local return expression.
- Added parser support for the separate custom binary-operator precedence domain and its required standard/custom mixing errors.
- Added deterministic parsing for the standard unary and binary operator precedence ladder.
- Added parser support for same-line semicolon expression separators with the grammar-defined error cases.
- Extended the parser foundation with parenthesized expressions, member access, calls, indexing, argument spread, and leading-dot continuation.
- Added the first deterministic parser foundation with source-aware errors and a portable surface AST.
- Added source-aware lexer token occurrences with portable half-open source spans as parser infrastructure.
- Expanded lexer conformance coverage for raw Unicode scalar handling across every Core String form, including rejection of unpaired surrogates.
- Expanded lexer conformance coverage for numeric termination at structural delimiters, logical newlines, and standard and custom operators.
- Expanded lexer conformance coverage for uppercase radix-prefix commitment and case-insensitive exponent completion errors.
- Expanded lexer conformance coverage for malformed numeric/identifier adjacency across ASCII, reserved-word, underscore, and Unicode identifier continuations.
- Expanded lexer conformance coverage for single-line String raw-newline rejection, raw-source indentation matching, and interpolation-looking escape rejection.
- Expanded lexer conformance coverage for triple-double String CR/CRLF delimiter-newline handling and shared escape semantics.
- Expanded lexer conformance coverage for Unicode escape digit-count boundaries, hexadecimal case handling, and the closed String escape set.
- Expanded lexer conformance coverage for exact, case-sensitive reserved-word recognition and the closed Core v0.1 reserved-word set.
- Expanded lexer conformance coverage for triple-double String structural indentation, including absent and empty prefixes, blank-line exemption, and exact SPACE/TAB matching.
- Expanded lexer conformance coverage for the symbolic-operator alphabet, maximal-munch classification, and comment/operator lexical precedence.
- Expanded lexer conformance coverage for numeric separator placement, unsupported suffixes and radix floats, special-value identifiers, and valid token boundaries.
- Expanded lexer conformance coverage for line-comment termination, non-nesting block comments, first-delimiter closure, and comment delimiters inside Strings.
- Expanded lexer conformance coverage for String normalization independence, raw Unicode scalar content, quote-run boundaries, and the absence of triple-single String syntax.
- Expanded lexer conformance coverage for Core v0.1 String quote-run boundaries, unterminated String forms, and the closed whitespace rules.
- Added bundled Unicode 17.0.0 character-property and normalization data used by the lexer independently of the host JDK Unicode version.
- Added conformance tests against the official Unicode 17.0.0 `XID_Start`, `XID_Continue`, and normalization test data.

### Changed

- Decoupled canonical literal kinds from parser surface literal kinds with an explicit Surface-to-Canonical mapping.
- Corrected malformed parser imports introduced by slot-creation and assignment support.
- Restored the canonical NEWLINE token value after adding source-aware token occurrences.
- Fixed source-aware newline token occurrence emission so canonical NEWLINE lexemes remain valid Java Strings.
- Updated GitHub Actions CI to current supported `actions/checkout` and `actions/setup-java` major versions.
- Relicensed Protos from the Server Side Public License (SSPL) v1 to the OSI-approved Adaptive Public License 1.0 (APL-1.0). The complete license, including the completed Exhibit A, is in [LICENSE.TXT](LICENSE.TXT).
- Project implementation version changed from `0.1.6-SNAPSHOT` to `0.2.0-SNAPSHOT`.

### Notes

- Protos Core language version remains 0.1.
- No language semantics changed.

### Fixed

- Fixed parser newline continuation while a member suffix or structural `super` message send is necessarily incomplete.
- Fixed parser newline continuation after `...` in spread arguments and rest parameters, matching the grammar's necessarily-incomplete construct rule.
## [0.1.6-SNAPSHOT] - 2026-08-31

### Added

- Expanded lexer support to cover the lexical rules defined by the current Core v0.1 specification.
- Added lexer support for comments, Unicode-aware identifiers, reserved words, numeric literal forms, multiline strings, ellipsis, and custom symbolic operators.
- Added lexical validation for malformed string escape sequences and invalid Unicode scalar values.
- Added regression coverage for supplementary Unicode escape sequences.

### Changed

- Updated lexer tests to match the current specification for reserved words, period tokenization, and numeric literals adjacent to periods.
- Unicode escape decoding now preserves supplementary Unicode code points instead of truncating them to Java `char` values.

### Notes

- The lexer implementation is still under specification-compliance review.
- Unicode identifier handling requires further review for exact `XID_Start` and `XID_Continue` compliance.
- Specification changes are documented separately in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.5-SNAPSHOT] - 2026-08-31

### Notes
- No implementation changes in this session.
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).

## [0.1.4-SNAPSHOT] - 2026-08-31

### Added
- Initial minimal Protos lexer implementation under `com.guillermomolina.protos.lexer`.
- Token model and token type definitions to represent the current lexical grammar.
- Basic lexical support for:
  - identifiers and reserved intrinsic keywords
  - numeric literals, including radix literals (`0x`, `0b`, `0o`)
  - string literals with escape handling
  - punctuation and structural tokens
  - operators and custom symbolic operators
  - newline-delimited token separation
- Focused lexer regression tests covering the implemented lexical behavior.
- Project licensing metadata by adding the license text in the then-current `LICENSE` file and referencing it from the README.

### Changed
- Added JUnit 5 to support lexer-focused test coverage.
- Updated project version from `0.1.3-SNAPSHOT` to `0.1.4-SNAPSHOT` as a conservative patch bump for the completed milestone.
- Documented the chosen license in the project README for this version.

### Notes
- Specification changes documented in [spec/PROTOS_SPEC_CHANGELOG.md](spec/PROTOS_SPEC_CHANGELOG.md).
