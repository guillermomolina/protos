# Protos Filesystem v0.1

Language version: 0.1
Document revision: 326
Status: Draft
Last updated: 2026-09-04

This document is the primary normative owner of File opening, filesystem authority, Path, and URL/filesystem conversion semantics.

The modular I/O specification consists of `IO_CORE.md`, `BYTE_IO.md`, `TEXT_IO.md`, `FILESYSTEM.md`, and `PROCESS_IO.md`. Legacy section numbers from `IO_CORE.md` and the applicable sibling I/O module are intentionally retained so historical citations remain understandable. Normative ownership now belongs to these modules; the former monolithic file is removed by revision 326.

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

Validation of the captured standard open-configuration tuple is a preflight semantic step. After ordinary argument evaluation has supplied the `Filesystem`, `Path`, and captured open configuration required by the invocation, the access/creation/initial-content/write-placement combination is validated before that open performs filesystem namespace resolution, resource selection/acquisition, creation, truncation, or other host/backend I/O for the target.

If that captured combination is invalid under the standard rules above, the `filesystem.open` Future fails for invalid open configuration without exercising filesystem authority against the supplied Path and without producing any namespace, content, cursor, resource-acquisition, or other target effect. In particular, an implementation must not discover `append + truncate`, missing read/write access, read-only truncate, or another standard-invalid combination only after it has looked up, created, opened, or modified the target.

Because this validity decision depends only on the already-captured semantic configuration, implementations may and normally will return an already-failed Future. The standard asynchronous API is preserved: this rule does not introduce a separate synchronous exception path for an otherwise well-formed `filesystem.open` invocation merely because its captured option combination is invalid.

This preflight rule establishes deterministic precedence over target-dependent filesystem outcomes for a valid `Path` value. For example, an invalid captured configuration fails as invalid configuration rather than sometimes reporting target absence, target existence, permission/confinement failure, or another backend/path outcome according to whether an implementation happened to resolve the Path before validating its options.

The rule does not require every valid configuration to be supportable by every backend. A configuration may be semantically valid yet later fail because the selected resource/backend cannot provide a required standard capability or invariant. Such support/resource failures occur under the ordinary open, confinement, acquisition, cancellation, and commitment rules; they are distinct from the configuration tuple being invalid in the first place.

Likewise, this rule does not redefine ordinary language-level receiver/argument validation needed to establish that the invocation has usable `Filesystem`, `Path`, and option-expression values at all. It only fixes the first filesystem-domain step once the semantic open configuration has been captured: validate the tuple before touching the target namespace/resource.

Standard-invalid open configurations therefore fail through the open Future before target namespace/resource work begins; invalid options can never create, truncate, acquire, or otherwise affect the supplied filesystem target.

Multiple `filesystem.open` invocations do not form one ordered namespace-operation stream merely because they use the same `Filesystem` capability, equal `Path` values, or one Actor invokes them sequentially without awaiting an earlier Future. `Filesystem` carries namespace authority; it is not a mutable sequence cursor whose independent acquisitions are implicitly serialized in caller invocation order.

Accordingly, after two valid open operations have both been invoked and remain simultaneously pending, Protos does not define which operation reaches its own namespace selection/commitment point first unless another normative dependency already establishes that order. This remains true when the opens use equal Paths and their effects can interact, such as `createNew` racing `existing`, two `createNew` operations, or an open with `truncate` racing another acquisition.

Each open still individually obeys its race-free selection, creation, confinement, stable-resource-binding, cancellation, and commitment rules. Once one open commits a namespace/content effect, another open that reaches its selection point later observes the namespace/resource state then applicable under the Filesystem/backend semantics. Protos does not permit an implementation to rewrite an already-selected File merely to manufacture caller-order serialization afterward.

A program that requires one open's terminal result or committed effects to precede a later acquisition establishes that dependency through ordinary Protos sequencing: it waits for the first operation's Future to reach the required terminal outcome before invoking the dependent open, or uses a future explicit higher-level protocol whose normative contract supplies the needed ordering. Merely retaining both Futures or invoking both calls from one Actor without such a dependency does not establish it.

An implementation may internally serialize some or all opens for a backend when doing so cannot change outcomes allowed by this rule, but that queueing policy is not portable Protos ordering. Conversely, implementations may perform independent opens concurrently, batch them, or use backend-native asynchronous acquisition. No global Filesystem lock, per-Path queue, or same-Actor namespace FIFO is required solely by the standard `open` protocol.

This rule concerns ordering among distinct open operations. It does not weaken the stable ordering domains of a `File` after acquisition, the cross-File append-placement invariant for append operations selecting the same underlying resource, or any stronger future Filesystem operation that explicitly defines its own transaction/order domain.

Standard Filesystem opens are independent asynchronous acquisitions: same Filesystem, same Path, or same-Actor invocation does not by itself order their namespace selection/commitment points; dependencies must be established explicitly.

### 18.1 Creation

`existing` is one race-free select-and-open operation at the Filesystem/backend semantic boundary. At that selection point the target must exist; if it is absent, open fails. If it exists, the operation selects that resource and acquires the File binding as one semantic operation.

The standard operation must not expose an implementation gap between a separate existence/metadata probe and acquisition in which a competing namespace change can produce behavior belonging to two different namespace states. In particular, a successful preliminary probe does not reserve the target, and an implementation must not claim that the probed resource was selected if the actual acquisition selected a replacement instead.

This guarantee does not impose a predetermined winner among genuinely concurrent namespace operations. Another authorized operation may create, remove, rename, replace, or redirect the target before this open's selection point; the namespace state at the selection point determines whether `existing` fails or which authorized resource it selects. Once selection succeeds, the stable File-resource binding rules apply immediately, so later namespace changes do not retarget the returned File or any truncate-on-open effect belonging to this open.

An implementation may realize `existing` with one backend primitive or with a race-safe/emulated resolution-and-acquisition mechanism. Any helper probes may be used only when their intermediate results are not promoted into Protos-visible selection state. If the backend cannot provide or emulate one confined select-and-open result under concurrent namespace change, it must fail rather than expose an uncertain standard `File` binding.

Standard `existing` selects and opens one existing authorized resource as a single race-free semantic acquisition; helper existence probes do not reserve identity or split resource selection across namespace states.

`create` is one race-free open-or-create selection at the Filesystem/backend semantic boundary. At that selection point, if the target exists, the operation selects and opens that existing resource; if the target is absent, the operation creates and selects the new resource. The standard operation must not expose an implementation gap between an existence probe and acquisition/creation in which a competing namespace change can turn an otherwise valid `create` into a spurious already-exists/not-found outcome or cause it to acquire a resource different from the one selected by its own open-or-create decision.

This guarantee does not impose a predetermined winner among genuinely concurrent namespace operations. Another authorized operation may create, remove, rename, or replace the target before the `create` operation's selection point, and that namespace state may determine which resource this open selects. Once this open has selected its resource, the stable File-resource binding rules apply: later namespace changes do not retarget the resulting File or any truncate-on-open effect belonging to this open.

An implementation may realize `create` with one backend primitive or by emulation, but any emulation must preserve the same race-free semantic result. In particular, a probe-then-exclusive-create sequence must recover from a concurrent creator by selecting the then-existing target when that is the result a single standard open-or-create operation requires, rather than leaking the helper primitive's race as a Protos-visible failure. If a backend cannot provide or emulate this invariant while also preserving Filesystem confinement, it must not expose standard `create` semantics for that resource.

`createNew` creates a new target and fails if the target already exists. The existence check and creation are one race-free/atomic creation operation at the backend's semantic boundary.

Whenever standard `create` selects the absent-target branch, and whenever standard `createNew` succeeds, the newly created file resource begins with logical byte size zero and contains no octets at the creation commitment point. This empty-content state is part of the standard file-creation semantic result, not a host-selected default.

The rule is independent of `initial content: preserve` versus `truncate`. `preserve` has no pre-creation content to preserve on a newly created resource, so the created resource is empty. `truncate` likewise finds the newly created resource already at logical size zero and introduces no additional destructive content effect or commitment merely to re-establish zero size.

Creation emptiness is a logical content guarantee rather than a physical allocation rule. A backend may create a sparse object, lazy object, copy-on-write object, virtual file, remote resource, or other representation without eagerly materializing an empty byte buffer, provided that the resource's Protos-visible initial byte sequence is exactly empty.

The empty state is established at this open's creation commitment point. It is not a promise that the resource remains empty until the open Future resolves or until some later observer examines it: independently authorized writes or other backend changes may occur afterward according to the existing cross-capability, namespace, and resource-visibility semantics. Such later changes do not retroactively alter the content state that this creation operation itself established.

If creation commits and the open later fails before returning a `File`, the ordinary no-compensating-rollback rule still applies: the operation does not delete the created target merely because later acquisition work failed. The created resource was established empty by this operation, although independent authorized activity may subsequently change it.

A backend whose primitive for creating the standard file resource necessarily exposes non-empty implementation-selected initial content, inherited template bytes, residual storage contents, or another non-empty default must clear/emulate that state before creation can commit as a standard Protos creation. If it cannot do so while preserving the required race-free creation, confinement, and commitment semantics, it must not expose that operation as standard `create`/`createNew`.

A standard newly created File resource therefore starts as an empty logical byte sequence of size zero; host/backend creation defaults cannot supply portable initial content.

Standard `create` is an atomic/race-free open-or-create selection, not a portable existence-check-then-create recipe; concurrent namespace changes may determine the selected state before the selection point, but implementation helper races must not become observable results.

### 18.2 Truncate-on-open

Writable open with `truncate` establishes initial file size zero as part of open.

Read-only plus truncate is invalid.

The truncate-on-open content effect is indivisible at the Protos semantic boundary. For an already-existing selected resource whose size/content would actually change, that open either has not yet committed any truncate-attributable content change, or it has established the complete requested truncation to logical size zero. A standard open must not expose a failed or cancelled truncate-on-open whose own aftermath is an intermediate nonzero truncation merely because a backend performed resizing in several steps.

Before the complete zero-size effect has committed, cancellation or an open failure attributable to the truncate attempt leaves the selected resource's content and size unchanged by that truncate-on-open operation. An implementation may use tentative backend work only when it can restore, virtualize, defer publication, or otherwise prevent a partial resize from becoming the failed/cancelled open's observable effect.

Once the selected existing resource has been completely truncated to size zero, that content effect is an irreversible open commitment under section 18.5. Cancellation can no longer win. The open may still fail later for another reason, but the open performs no compensating restoration of the discarded content.

If the selected resource was already size zero, the truncate request is a successful content no-op and creates no additional irreversible content commitment merely by checking or confirming that state. If `create` or `createNew` creates a new empty resource, creation remains the relevant namespace commitment; requesting `truncate` does not invent a second destructive effect when the newly created resource already has size zero.

This failure-atomicity concerns only content change attributable to the truncate-on-open operation. It does not freeze the selected resource against independently authorized writes, truncations, or other backend changes. Such independent activity may make a later observer see nonzero size even after this open's complete zero-size effect occurred, according to the existing cross-capability/backend ordering and visibility rules.

Support for this open-time invariant does not require the returned File to expose `Truncatable`. A backend may have a race-safe/atomic open-with-truncate primitive or may emulate the invariant specifically during acquisition. If it cannot provide or emulate the all-or-zero truncate-on-open effect while preserving the other standard open guarantees, it must reject that standard open configuration rather than expose an implementation-dependent partial destructive failure.

Standard truncate-on-open is therefore failure-atomic with respect to its own content effect: before commitment it contributes no truncation, at commitment it establishes complete logical size zero, and a later open failure does not roll that committed effect back.

### 18.2.1 Positioned File writes

For a writable standard `File` opened with `write placement: positioned`, each `ByteWritable.write(bytes)` starts at that File's logical sequence position applicable when the operation reaches its ordered evaluation point. Let that starting position be `p` and let the captured write sequence have length `N`.

The write replaces existing file octets beginning at `p`; it does not insert bytes and shift later file content. If the operation contributes a prefix of length `k` under the ordinary `ByteWritable` success/failure rules, its own byte contribution occupies exactly offsets `p` through `p + k - 1`, in source order. For `k > 0`, the File's logical position after that operation is `p + k`, whether the Future ultimately succeeds or fails. For `k = 0`, the logical position is unchanged.

If contributed bytes extend beyond the file size applicable to the write, the file grows as necessary to include the last contributed byte. If `p` is greater than that file size when the first byte of this operation is contributed, every newly created logical octet between the former EOF and `p` reads as zero. Those zero-valued gap octets are part of the file-growth effect required to place this operation's first contributed byte; they are not bytes from the captured write sequence and do not increase `k`.

A positioned write that contributes zero bytes does not grow the file or create a gap merely because its logical position is beyond EOF. In particular, cancellation or failure with `k = 0` preserves both the logical position and the file size/content effects attributable to that write. An implementation that tentatively extends, allocates, seeks, or materializes backend storage before the first contributed byte must reconcile that work so a zero-contribution outcome does not expose such tentative growth.

The zero-gap rule is logical file content, not a requirement to physically allocate or write every intervening octet. Sparse allocation, holes, extents, virtual zero ranges, explicit zero filling, or another backend representation are all permitted when reads and size observations produce the specified result. A backend that cannot provide or emulate deterministic zero-valued gap content must not expose that resource as a standard positioned File whose writes may extend beyond EOF.

Independent operations through separately opened File capabilities, external actors/processes, or backend agents remain governed by the existing cross-capability/backend semantics. This rule does not create a new cross-File ordering or atomicity domain. In particular, such independently authorized changes may alter file size or content before or after this write's own placement/effects according to the backend contract; they do not change which offsets are attributable to this write's contiguous contributed prefix once its starting logical position has been established.

For operations ordered on the same logical File, the ordinary sequence-state domain applies: a later position-sensitive operation starts from the post-write logical position and sequence aftermath defined above, including the permitted prefix and any required zero-valued gap created by that prefix.

A standard positioned File write overwrites from the File's current logical position, advances that position by exactly its contributed prefix, grows the file when necessary, and exposes deterministic zero-valued logical gap octets when placement begins beyond EOF.

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

Every successful standard `File` open whose read/write behavior uses a logical sequence position establishes that position as zero before the `File` result commits. This is independent of `existing`/`create`/`createNew`, `preserve`/`truncate`, and `positioned`/`append` choices.

The zero position is a Protos logical-position guarantee, not a requirement that the backend's native cursor already has that value. An implementation may use positional I/O, a virtual cursor, a native handle whose cursor is initialized differently, or another representation, but the first position-sensitive Protos operation must observe the same state as if the File's logical position had begun at zero.

Append mode does not change this initialization rule. An append write still chooses its placement from the current file end independently of the stored logical position and then updates that logical position according to the append-contribution rules above. Thus opening in append mode does not itself seek the Protos logical position to EOF, even on a host API or standard library whose append-opening helper happens to do so internally.

Likewise, truncate-on-open establishing file size zero does not create a distinct initial-position rule: the returned File's logical position is zero because every standard positioned File begins there, not because truncation happened to make EOF zero.

A backend or host adapter that cannot provide or emulate this initial logical-position state must not expose a standard File whose operations depend on that position while leaking an implementation-selected starting cursor.

A newly opened standard File begins at logical byte position zero whenever it has position-sensitive read/write behavior; append changes each write's placement, not the File's initial logical position.

The standard Protos append contract does not promise stronger non-interleaving with unrelated external writers than the backend can provide.

For append writes performed through standard Protos `File` capabilities that select the same underlying filesystem resource, each write operation has an atomic append-placement boundary. Concurrent append writes have no predetermined relative order, but once the filesystem accepts one append operation as the next append contributor, no byte from another append write may be placed between bytes contributed by that operation. A failed append therefore contributes its contiguous prefix, if any, before the next append operation can contribute bytes to a later file position.

The placement boundary is distinct from the write's completion boundary. An append may contribute a prefix and subsequently fail, and the next append may then continue from the resulting file end. An implementation must not reserve the complete requested sequence in advance and thereby create a semantic hole when a committed append contributes fewer bytes than requested.

This guarantee applies to standard Protos append operations selecting the same resource, even when they are reached through distinct `File` capabilities or aliases. Their relative order remains nondeterministic when genuinely concurrent; Protos does not expose which operation won. The guarantee is nevertheless strong enough to prevent two such append operations from overlapping or interleaving their contributed byte sequences.

If a backend cannot provide or emulate this append-placement boundary for the resource, it must not expose standard append mode for that resource. It may expose a weaker host-specific facility separately.

This rule does not establish a general ordering domain for independently opened Files. It is a specific invariant of append placement required to make the standard append contract meaningful across independently opened capabilities. Other operations on those capabilities remain governed by their ordinary cross-capability semantics.

Append writes selecting the same underlying resource have an atomic placement boundary: concurrent operations may be ordered either way, but their contributed byte sequences do not overlap or interleave, and a partial failed append does not reserve an uncommitted suffix.

Writes invoked on the same receiver still preserve their required invocation ordering.

### 18.4 File resource identity and capabilities by mode

A successful `filesystem.open(path, options)` binds the returned `File` to the concrete filesystem resource selected by that open operation. The `Path` used to open it is not a continuing lookup instruction and is not the identity of the returned `File`.

After successful open, subsequent namespace changes do not retarget the `File`. Renaming, removing, replacing, relinking, or otherwise changing the directory entry used to reach an already-open resource does not make that `File` begin referring to the newly named or newly created resource. Operations on the `File` continue to apply to the resource selected by the successful open, subject to that resource's own backend semantics and to independently authorized changes to that resource's contents.

Conversely, opening the same `Path` again is a new resource-acquisition operation. The resulting `File` is a distinct capability with its own lifecycle and, when applicable, its own logical sequence position. Two `File` values obtained by separate successful opens are not required to share a cursor, buffering state, lifecycle, shutdown state, or I/O ordering domain merely because their opens used equal `Path` values.

Two separately opened `File` capabilities may nevertheless refer to the same underlying resource according to the Filesystem's namespace semantics, including through aliases such as hard links or other backend-equivalent names. Such aliasing does not merge the Protos `File` objects or create a new global ordering domain.

When separately opened `File` capabilities refer to the same underlying resource, Protos preserves each capability's own logical ordering and lifecycle independently, but does not define a relative ordering, atomicity, visibility latency, or byte-level non-interleaving guarantee between those distinct logical receivers unless a stronger concrete protocol explicitly provides one. An implementation may therefore use independent native operations, shared backend state, locking, or another mechanism, provided that each File's own observable contract remains satisfied.

In particular, Protos does not make two successful opens of one resource behave like two handles to one Protos logical receiver merely because the host reports a common inode, file identifier, object, or equivalent identity. A write through one File may become observable through another according to the underlying Filesystem's resource semantics, but the timing and ordering of such cross-capability observation are not elevated into a portable Protos guarantee by File identity alone.

Observable interactions between independently opened capabilities are therefore governed by the resource/backend semantics and the Protos ordering rules for each logical receiver, not by `Path` equality.

For a readable standard `File`, EOF is evaluated against the represented file resource at each ordered read evaluation point; observing `null` does not permanently latch that File at EOF while the resource can still change. If the File's current logical position is at or beyond the then-current file size, that read returns `null` and does not advance the logical position. If a later independently authorized change grows or otherwise changes the same represented resource so that readable octets then exist at that unchanged logical position, a later read on this File may return those octets without requiring an intervening seek.

The same rule applies whether the resource growth came through another standard File capability, another authorized Process/host agent, or another backend mechanism whose effects are visible under the Filesystem's resource semantics. Protos does not add a new ordering or visibility guarantee for when such an independent change becomes observable; it only defines the result once that changed resource state is the state applicable to the later File read.

Conversely, merely observing EOF does not make a pending File read wait for hypothetical future growth. A read evaluated at a state where its logical position is at or beyond current EOF completes with `null`; following growth is considered only by a later read operation. This keeps regular-file EOF distinct from stream readiness and avoids turning ordinary File reads into implicit tail/follow operations.

This File rule does not alter a `TextReader` wrapper's stronger text-stream lifecycle. A TextReader that has itself committed its standardized permanent text EOF remains at text EOF according to section 14/16 even if its separately accessible underlying File later grows; Core v0.1 defines no TextReader reset/reopen operation. Programs that need to observe later file growth use the raw File/byte capability or construct a new text-reading layer under the ordinary authority/lifecycle rules.

A readable File therefore treats EOF as a point-in-sequence-state observation, not as permanent closure of a mutable file resource: later visible growth past the File's unchanged logical position can make a subsequent read return data.

If the backend cannot maintain a stable binding from the successful open to the selected resource for the lifetime of the returned `File`, or cannot otherwise emulate the required stable-resource semantics, it must not expose that resource as the standard `File` capability merely because the host API returned a handle-like value.

This rule does not require exposing a portable numeric inode, file ID, handle, descriptor, or other host identity token. Resource identity is a semantic property of the open `File` capability and remains distinct from portable `Path` equality.

Open mode determines the access capabilities that a successful standard `File` must expose; backend semantics determine which orthogonal optional file capabilities can additionally be exposed honestly.

A successful File open binds the File to the selected resource rather than to a continuing Path lookup; later namespace changes do not retarget it, while separate opens remain separate capabilities even when they select the same underlying resource.
Separate File capabilities that alias one underlying resource retain independent Protos ordering/lifecycle domains; cross-capability ordering, visibility latency, and atomicity are not portable guarantees unless a stronger protocol provides them.

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

Standard file-URL conversion preserves URL structure before converting it to Path structure. Scheme, authority, URL path hierarchy, and URL path-segment boundaries are determined under URL semantics before percent-encoded data inside a segment is decoded for filesystem naming. An implementation must not percent-decode a raw URL/path string first and then reinterpret decoded characters as URL delimiters.

Consequently, a percent-encoded slash or other decoded hierarchy-looking character that was data inside one URL path segment cannot create an additional Path component, change rootedness, introduce a Filesystem prefix/root/device selector, or otherwise acquire separator semantics during conversion. The decoded value belongs to that one candidate filesystem component. If the interpreting Filesystem cannot represent it as one logical child name, conversion or the later filesystem operation fails rather than splitting or reinterpreting it.

URL dot-segment semantics are likewise resolved in the URL domain before portable Path components are constructed. A segment that was not a URL `.` or `..` hierarchy segment does not become current/parent traversal merely because percent-decoding its data yields the String `"."` or `".."`. Because those Strings are not valid normal portable Path component names, a standard conversion that would otherwise produce such a normal component fails rather than silently changing its structural meaning.

Percent decoding and conversion to Protos `String` must be lossless for every produced normal component. Malformed percent encoding, a byte/text sequence that the Filesystem's file-URL mapping cannot represent as valid Protos text, or a conversion requiring replacement, truncation, normalization, delimiter reinterpretation, or another lossy transformation makes the conversion fail. The exact native filename encoding remains a Filesystem/host boundary; lossiness is not a portable fallback.

A file URL authority is data to be interpreted only through the receiving Filesystem capability's explicitly supported namespace mapping. A non-empty or non-local-looking authority does not grant ambient network, DNS, UNC, host-root, or sibling-filesystem authority merely because the host platform has APIs that could interpret it that way. If the Filesystem cannot map that authority wholly inside its authorized namespace without obtaining additional authority, the conversion fails.

`pathFromURL` does not itself perform ambient DNS/name resolution or network acquisition to decide whether a file authority is local or reachable. Such facilities are outside this I/O model. A managed Filesystem may have an already-provisioned mapping for authorities or UNC-like forms, but using that mapping must preserve the same confinement and one-component rules as every other Path conversion and resolution.

File-URL conversion parses URL hierarchy before percent decoding, never lets decoded segment data become Path separators/parent traversal, performs only lossless component conversion, and cannot turn URL authority into ambient filesystem/network authority.

Neither a Path nor a URL grants resource-access authority.

> Resource identifiers are values. Resource access is performed through capabilities.

Network-resource access therefore belongs to separately provisioned network/HTTP/etc. capabilities rather than to ambient `url.fetch()` authority.

Relative URL references follow URL resolution semantics, not Filesystem path semantics.

Core v0.1 requires no URI/URL/URN class hierarchy or special URL literal syntax.

---
