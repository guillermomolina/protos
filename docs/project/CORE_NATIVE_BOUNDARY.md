## I028-A1 — standard `IpAddress` ordinary-object bridge

I028-A1 publishes the already-ratified D048 `IpAddress(version, bits)` factory/prototype
without introducing a Java runtime value family. `IpAddress.protos` owns the canonical ordinary
prototype identity; the bounded Java provider installs four representation-bridge Closures for
construction-time exact Integer/range validation plus freeze, transparent `recognizes(value)`,
and callback-free structural `==` / `hash`. Successful values remain fresh frozen ordinary
objects with exactly `version` and `bits` local slots and the canonical `IpAddress` as immediate
parent.

The direct bridge is required because recognition must inspect frozen state, exact immediate
parent and the exact receiver-local slot set without invoking candidate behavior. The audited
Core boundary therefore becomes **119 native Closure construction sites across 31 providers**.
No `IpEndpoint`, Actor/P transfer, Network/TCP authority, DNS, UDP or TLS behavior is included.

## I030 — standard `Object.without` / `Object.alias` structural views

LM007-D exposed that the already-normative structural-view helpers existed in
`ProtosObjectValue` but their ordinary inherited `Object` messages were not
published to guest Protos code. I030 publishes exactly `without(name)` and
`alias(sourceName, aliasName)` through `ProtosStandardObjectProtocol`.

These operations are representation bridges: their contract dynamically reads
receiver-local slot names and constructs a fresh ordinary local-slot snapshot,
which cannot be expressed faithfully through the currently published guest
reflection surface. Each selector therefore adds one reviewed native Closure
construction site. The audited Core boundary becomes **115 native Closure
construction sites across 30 providers**, with `ProtosStandardObjectProtocol.java`
at nine sites. `slotNames`, `removeSlot`, structural `close`, and structural
`freeze` remain outside this bounded implementation finding.

# Core Native Boundary

## I029 — D050 Boolean protocol completion

I029 enlarges the existing standard Boolean selector surface without adding a
native Closure construction site. The one audited
`ProtosStandardBooleanProtocol` helper now installs `not`, `ifTrue`,
`ifFalse`, `ifTrueIfFalse`, `and`, and `or`; canonical selection and
selected-only callback validation remain host-irreducible control behavior.
The audited Core total therefore remains **113 native Closure construction
sites across 30 providers**. No `if`/`else` syntax or new runtime category is
introduced.

## I024-D — final Filesystem tree-observation closure

I024-D adds no production native Closure construction site. The standard
`ProtosStandardFilesystemProtocol` continues to expose `open`, `replace`,
`remove`, `entries`, and `captureTree` through one shared resource/capability
bridge construction helper. The execution-time inventory and executable
architecture guard agree on **113 native Closure construction sites across 30 Core
providers**.

The NIO enumeration/capture backends and managed captured backing are backend
machinery rather than additional standard Closure providers. D046 therefore
closes without a Directory/Filesystem-close native surface expansion.


## TOOL002-F3C1B2A4P2 — standard `Object.slotValue` reflection prerequisite

The retained-Future inspector progression reached the first local-slot value read
and exposed the second pre-existing gap in the already-normative Core reflection
surface: `Object.slotValue(name)` remained absent after the intentionally narrow
`hasSlot` prerequisite.

This slice implements only `Object.slotValue(name)`. It validates exactly one
semantic String argument, reads only the receiver's own local-slot table through
`ProtosObjectValue.readLocalSlot`, returns the exact stored value without copying
or delegated lookup, and signals the ordinary fresh `Error` when the receiver is
not an ordinary object or the named local slot is absent.

This adds exactly one reviewed native Closure construction site. The current
audited Core boundary is therefore **113 native Closure construction sites across
30 providers**, with `ProtosStandardObjectProtocol.java` at seven sites.
`slotNames` and `removeSlot` remain outside this slice. Test Tool policy,
`executionInspect`, Future behavior, syntax, specification and canonical TOOL002
ledgers are unchanged.

## TOOL002-F3C1B2A1P1 — standard `Object.hasSlot` reflection prerequisite

The F3C1B2 retained-Future inspector audit exposed a pre-existing implementation
gap in the already-normative Core reflection surface: Protos source could not
invoke inherited `Object.hasSlot(name)` because that selector was absent from the
runtime's standard Object surface. The specification already defines the
operation completely; this slice therefore implements the general Core
prerequisite instead of adding a Test Tool-specific slot-inspection escape hatch.

`Object.hasSlot(name)` is one representation bridge. It validates exactly one
semantic String argument before inspecting receiver state. For an ordinary
object it returns the canonical Boolean corresponding to presence in that
receiver's own local-slot table only; delegated slots do not count. Opaque
represented Core values have no representation-owned mutable local-slot table
and therefore report canonical false. Invalid argument shape signals the
ordinary fresh `Error`.

This adds exactly one reviewed native Closure construction site to the existing
`ProtosStandardObjectProtocol` provider. At that prerequisite publication cutover the audited Core boundary became
**112 native Closure construction sites across 30 providers**, with
`ProtosStandardObjectProtocol.java` at six sites. `slotNames`, `slotValue`, and
`removeSlot` remain outside this slice and remain unimplemented by this change.
No Test Tool policy, execution capability, scheduler, syntax, or normative
specification changes.


## I023-D — final standard `while` closure

I023-D re-audits the complete production native boundary after every standard
`while` implementation/conformance slice is closed. I023-B/C/D add zero
production `nativeClosure` construction sites after the single I023-A
`Object.while` site. The executable architecture guard, provider table and
selector-surface audit agreed at the I023-D publication cutover on **111 native Closure
construction sites across 30 Core providers**. `ProtosStandardObjectProtocol`
was at five construction sites at that cutover and `while` remains one ordinary inherited
standard selector rather than a new runtime object, scheduler boundary or syntax
category.

## I023-B1 — bounded `while` replay retention

The post-I023-A longevity audit found that replaying a later suspended callback was
constant-time but retained the completed callback event/activation prefix for every prior
iteration. I023-B1 changes only internal continuation bookkeeping: after each condition or
body callback completes normally, its child evaluator suffix is committed and truncated back
to one stable callback checkpoint, completed callback activation keys are discarded, and the
next callback reuses the same parent-event ordinal. A callback that actually suspends remains
uncommitted and therefore retains exactly the replay state needed to resume that callback.

This makes retained `while` replay state depend on the currently active callback trace rather
than the number of completed iterations and removes the former monotonic `int` callback-count
limit. D044 semantics, selector placement, scheduling/cancellation boundaries, and the audited
native Closure construction-site/provider counts are unchanged.


## I023-A — standard `while` control boundary

I023-A adds exactly one reviewed native Closure-construction site to the existing
`ProtosStandardObjectProtocol` provider. `Object.while` is host-irreducible control
machinery rather than source-expressible derived behavior: D044 requires eager semantic
Closure validation before the first condition activation, direct Closure activation
without a second polymorphic callback model, strict canonical Boolean branching, and
replay-safe continuation across a suspended condition/body without duplicating completed
callback effects. The implementation reuses the task-local replay tape and dynamic-control
identity already established by I022; it creates no public runtime type, scheduler boundary,
Future, handler, cleanup scope, or syntax category.

The audited Core boundary after I023-A is **111 native Closure construction sites across
30 providers**; `ProtosStandardObjectProtocol.java` accounts for five sites.

Status: non-normative implementation architecture inventory.

This document records the Java-backed standard-behavior boundary after I018 Core
self-hosting/bootstrap minimization. It does not define Protos semantics; the
normative owners under `spec/` remain authoritative.

The inventory exists for one reason: a Java-native standard operation must be an
explicitly reviewed boundary, not a convenient place to accumulate ordinary Core
library behavior. If a standard operation can be expressed faithfully as normal
Protos objects and Closures without changing receiver validation, identity,
ordering, failure, suspension, capability, or representation semantics, it
belongs in `protos/lib/core/`.

## Classification

The final I018 audit uses these implementation categories:

- **host-irreducible** — fundamental execution/control machinery needed to run
  ordinary Protos source at all;
- **representation bridge** — behavior whose contract depends on an opaque or
  implementation-selected representation of a semantic Core value;
- **concurrency/runtime bridge** — behavior coupled to Tasks, Futures, Actors,
  isolation, scheduling, suspension, transfer, cancellation, or commitment;
- **resource/capability bridge** — behavior coupled to resource custody,
  capability shape, I/O lifecycle, buffering, or backend effects.

There are no remaining entries classified as source-expressible. I018 already
moved such behavior and standard identities to distributable Core source where
the current language can express them faithfully.

## Audited Java native-Closure providers

The `nativeClosure` count is intentionally a construction-site count rather than
a selector count. Some providers use one audited helper to install several
selectors. `ProtosCoreNativeBoundaryArchitectureTest` complements this table with
runtime selector-surface checks so helper-based expansion cannot silently widen
the standard native boundary.

| Provider | Native Closure sites | Classification | Audited reason for remaining native |
|---|---:|---|---|
| `ProtosStandardObjectProtocol.java` | 9 | host-irreducible / representation bridge | Generic polymorphic `call` performs Closure invocation or ordinary instance construction; `identityHash` exposes semantic identity without dynamic-dispatch substitution; inherited `hasSlot` validates one semantic String and projects exact receiver-local slot presence; inherited `slotValue` validates one semantic String, reads only an ordinary receiver local binding and returns the exact stored value or signals Error when absent; inherited `without` / `alias` validate semantic String names, inspect only receiver-local ordinary-object structure, and construct fresh Object-parented shallow views; `ensure` establishes the D043 Closure-only protected dynamic extent and executes unwind cleanup before normal/return/Error propagation; inherited `parent` projects the exact immutable semantic delegation parent across ordinary and opaque represented values and signals for the unique root because no structural parent exists; `while` establishes the D044 Closure-only iterative control boundary while reusing ordinary Closure invocation, replay, suspension, cancellation and task ownership machinery. |
| `ProtosStandardBooleanProtocol.java` | 1 | host-irreducible | `not`/`ifTrue`/`ifFalse`/`ifTrueIfFalse`/`and`/`or` are the primitive Boolean/control surface, including canonical negation and path-sensitive callback selection/validation. |
| `ProtosStandardHashSupport.java` | 3 | representation bridge | Object identity hashing and Number/String hashing depend on semantic identity or exact represented values and must not be redefined through overrideable message sends. |
| `ProtosStandardNumberEqualityProtocol.java` | 1 | representation bridge | Exact cross-family Number equality needs Integer/fixed/binary64 representation knowledge, including NaN and exact-integral Float handling. |
| `ProtosStandardNumberOrderingProtocol.java` | 1 | representation bridge | Exact cross-family ordering and unordered NaN behavior require representation-aware comparison. |
| `ProtosStandardIntegerProtocol.java` | 3 | representation bridge | `+`, `-`, `*`, `/`, `div`, and `mod` are exact numeric representation primitives; derived `negated` and `%` are already source-backed. |
| `ProtosStandardFloatProtocol.java` | 1 | representation bridge | Binary64 arithmetic is the primitive represented-value boundary; derived `negated` is already source-backed. |
| `ProtosStandardNumericConversionProtocol.java` | 1 | representation bridge | Numeric factory conversion performs exact family/range/binary64 conversion over host representations. |
| `ProtosStandardStringProtocol.java` | 3 | representation bridge | String size/indexing use required Unicode grapheme segmentation and `+` constructs semantic String representation values. |
| `ProtosStandardArrayProtocol.java` | 5 | representation bridge | Array factory/index mutation/size own indexed representation state; `each` also requires eager callability validation and a start-of-operation snapshot. |
| `ProtosStandardProcessArgumentsProtocol.java` | 3 | representation bridge | Immutable Process-argument snapshots own an opaque captured indexed representation; `size`/`at` expose that representation and `each` requires eager polymorphic-callability validation before ordered callback invocation. The helper prototype is construction-only and not a Core-prelude authority or named standard prototype. |
| `ProtosStandardEnvironmentProtocol.java` | 3 | representation bridge | Standardized Environment snapshots retain opaque native-name identity/representability state. `get`/`contains` preserve native query semantics and selective value decoding; `each` performs eager callability plus whole-snapshot portable String validation before canonical Unicode-scalar-order callbacks. Environment remains outside the required Core prelude and is not a Map subtype. |
| `ProtosStandardEncodingProtocol.java` | 2 | representation bridge | Encoding semantic-family receiver validation and exact one-shot String/Bytes conversion cross immutable descriptor and codec representations. I015-E completes the descriptor boundary with fresh per-flow transactional streaming decoder/encoder factories, explicit strict/replacement and initial-BOM policy state, deterministic portable Unicode maximal-subpart replacement, and explicit host-provided semantic codec contracts; the source-backed Encoding factory still exposes only the four mandatory portable strict descriptors plus native `encode`/`decode`. |
| `ProtosStandardTextReaderProtocol.java` | 2 | resource/capability bridge | Source-backed `TextReader` construction validates ByteReadable/optional owning-Closable authority plus exact Encoding-family membership, including explicitly provisioned host descriptors. One factory helper and one wrapper-operation helper remain the only native Closure sites. The runtime owns one ordered transactional decoder/input domain, strict or deterministic replacement decoding, exact source-octet line accounting, CR/LF framing, zero-consumption cancellation, permanent failure and ownership lifecycle across portable and host codecs. |
| `ProtosStandardTextWriterProtocol.java` | 2 | resource/capability bridge | Source-backed `TextWriter` construction validates ByteWritable/optional owning-Closable authority plus exact Encoding-family membership, including explicitly provisioned host descriptors. One factory helper and one wrapper-operation helper remain the only native Closure sites. The runtime owns transactional per-flow encoder state, complete pre-output validation, ordered target commitment/failure, empty-write zero-transition behavior, compositional flush, explicit encoder finalization during close and ownership release. |
| `ProtosStandardProcessStreamProtocol.java` | 2 | resource/capability bridge | Process standard-stream views expose exactly one byte direction (`read` for stdin or `write` for stdout/stderr) over Process-local shared ordering/backpressure bindings. Actor transfer rematerializes an Actor-local proxy to the same binding; P rejects the live authority; no Closable/File/text/seek/flush surface is inferred. |
| `ProtosStandardProcessProtocol.java` | 1 | resource/capability bridge | The source-backed authority-free `Process` prototype receives eight synchronous accessors from one audited Closure-construction helper. Each selector requires an actual represented Process capability, reads only already-established Process bootstrap state, rejects unavailable/invalid state, and rejects every proxy after the Process termination cutover; no accessor performs host discovery, waiting, filesystem recovery, or capability construction. |
| `ProtosStandardMapProtocol.java` | 7 | representation bridge | Map storage, recorded hashes, reentrancy restrictions, mutation state, lookup equality callbacks, and iteration snapshots are receiver-owned keyed representation semantics. |
| `ProtosStandardIdentityMapProtocol.java` | 7 | representation bridge | IdentityMap storage and lookup require primitive semantic identity/identityHash plus keyed representation state and iteration snapshots. |
| `ProtosStandardBytesProtocol.java` | 7 | representation bridge | Bytes owns octet-indexed mutable state, reservation state, exact octet validation, snapshot iteration, and P-region interaction. Its standard prototype identity is already source-backed and construction-only. |
| `ProtosStandardPathProtocol.java` | 6 | representation bridge | Path construction, components, structural equality, and structural hash operate on the immutable Path representation. |
| `ProtosStandardIpAddressProtocol.java` | 4 | representation bridge | D048 construction and recognition require exact unbounded-Integer/range validation, fresh ordinary-object state publication plus freeze, and direct inspection of frozen state, immediate canonical parent and exact local-slot shape without invoking candidate behavior; structural equality/hash read only the same canonical `version`/`bits` state. |
| `ProtosStandardErrorProtocol.java` | 2 | host-irreducible | `Error.signal` performs the language Error control transfer with exact signaled-object preservation; `Error.handle` installs and consumes the dynamic handler frame whose selection precedes unwind cleanup. |
| `ProtosStandardFutureProtocol.java` | 2 | concurrency/runtime bridge | `future`, `value`, `cancel`, `detach`, `then`, and `all` depend on Task ownership, suspension, observation, terminal states, cancellation, and Actor-local execution domains. |
| `ProtosParallelRuntime.java` | 2 | concurrency/runtime bridge | `parallel`, Array parallel operations, Bytes/ByteRegion `parallelRange`, snapshot transfer, reservations, commitment, and bounded host carriers form the P execution substrate. |
| `ProtosStandardActorProtocol.java` | 9 | concurrency/runtime bridge | `spawn`/`current`/`group`, ActorRef `send`/`request`/`stop`/`termination`, GroupRef `send`/`request`, and SendOperation `cancel`/`retry` cross Actor/Group routing, transfer, admission, scheduler, uncertainty, and lifecycle boundaries. ActorRef and GroupRef receive distinct Closure values from the same two audited communication construction helpers; ActorRef, GroupRef, and SendOperation prototype identities are source-backed. |
| `ProtosStandardImportProtocol.java` | 1 | resource/capability bridge | The source-backed `import` facility's `call` crosses the host resolver, canonical ModuleKey, Actor-local module cache, loading, cycle, and initialization boundary. |
| `ProtosStandardByteIoProtocol.java` | 12 | resource/capability bridge | Byte I/O operations are capability-honest wrappers over ordered flow state, Future commitment, positioning, sizing, truncation, sync, and directional shutdown. |
| `ProtosStandardBufferedByteIoProtocol.java` | 6 | resource/capability bridge | Source-backed factories retain native construction bridges because wrappers attach buffering, ownership, underlying-capability validation, Future, and lifecycle state. |
| `ProtosStandardFileProtocol.java` | 10 | resource/capability bridge | File objects are acquired resource capabilities whose exact local surface depends on backend-provided authority and whose operations own cursor/append/sync/close/commitment state. |
| `ProtosStandardFilesystemProtocol.java` | 1 | resource/capability bridge | Host-provisioned Filesystem authority exposes standard `open`, `replace`, `remove`, `entries`, and `captureTree` through one shared audited operation-Closure construction helper. Open retains confined/race-free acquisition and File materialization; D041 namespace mutation uses the host-neutral effect/commit cutover; D046 tree observation reuses the host-neutral I024 flow, materializes inert Array descriptors or a fresh structurally read-only Filesystem, and leaves unsupported backends default-fail without adding a Directory or Filesystem-close boundary. |

Total audited Core production construction sites: **115 across 30 providers**.

CLI/launcher-owned host conveniences are not Core standard behavior and therefore
do not change that 30-provider / 113-site Core boundary. They are nevertheless
kept explicit rather than allowed to accumulate invisibly:

| Non-Core provider | Native Closure sites | Boundary | Reason |
|---|---:|---|---|
| `ProtosCliPrintFacility.java` | 1 | standalone CLI host/display bridge | Installs one ordinary initial-context `print` Closure only for normal standalone CLI sessions. General value rendering is CLI policy; output is delegated through a borrowing standard `TextWriter` over the already-provisioned Process stdout capability and Encoding. Bundled tools, Core bootstrap, imported modules and non-root Actor bootstrap do not receive this binding. |
| `ProtosExactExecutionFacility.java` | 2 | bundled-tool bootstrap execution bridges | Installs the ordinary initial-context `execution` Closure plus the opt-in `executionInspect` Closure only when the host explicitly grants those tooling capabilities. `execution` delegates to the existing fresh-Process/root-task/private-capture machinery; `executionInspect` delegates to the C1A same-Process live-result inspection boundary and detaches only the inspector terminal observation. Neither is a Core/prelude binding, so the 30-provider / 113-site Core standard native boundary remains unchanged while the audited non-Core provider count for this file increases from one construction site to two. |


### TOOL002-D3B2A Object.parent reflection prerequisite

The D3B2 `error-parent` migration exposed a pre-existing implementation gap in
the already-normative Core reflection surface: Protos source could not invoke
the inherited standard `Object.parent()` selector even though delegation parent
is portable observable semantics. D3B2A closes that general gap rather than
adding a Test Tool-specific parent-inspection facility.

`ProtosStandardObjectProtocol` therefore gains one reviewed native Closure
construction site. This is a representation bridge: ordinary objects store their
parent directly while represented semantic values obtain it from their selected
Prelude-backed representation contract. `ProtosValueLookup.delegationParent`
centralizes that projection so lookup and reflection cannot silently disagree.

The standard prelude also regains its normative `Object` binding. Because the root
cannot name itself before that binding exists, bootstrap supplies a temporary
`_coreRootObject` lexical seed solely while evaluating distributable
`prelude.protos`; the seed is removed before prelude freeze and the final binding
is source-declared. This adds no native Closure construction site.

The definitive Core boundary becomes **110 sites across 30 providers**. No new
provider, syntax, capability, scheduler, or Test-only runtime surface is introduced.

### I022-F definitive dynamic-control re-audit

I022-F re-audits the complete Core native boundary after `Error.handle`,
`Object.ensure`, replay-stable cleanup, and cooperative cancellation unwind are
all published. At the I022-F publication cutover, the audited boundary was **109 production
construction sites across 30 Core providers**. That figure is historical: later
published Core work added reviewed sites and the current boundary is owned by the
provider table and executable architecture guard above.

The two I022 additions were already reviewed at their publication cutovers:
I022-B added one host-irreducible `Error.handle` construction site and I022-C
added one host-irreducible `Object.ensure` construction site. I022-D, I022-E1,
I022-E2, I022-E3, and I022-F add **zero** production `nativeClosure`
construction sites. Dynamic handler state remains task-local runtime state;
cleanup replay/cancellation bookkeeping remains control machinery behind the two
audited selectors rather than a new Protos-visible primitive family.

The numerical entries below are retained as chronological audit history at the
named slices. Lower historical counts in that progression are not current
boundary claims; the table above plus the executable architecture guard own the
current 113-site / 30-provider inventory.
I018-L closed with the 90-site/22-provider baseline. I016-D1 was an explicitly
reviewed post-I018 resource/capability extension adding exactly one
`Filesystem.open` native-Closure construction site.

I016-D3 re-audited the complete boundary after D2. The current result remains
**91 sites across 23 providers**: D2's File/Filesystem authority markers and
Actor/P transfer guards add no native Closure construction site, and D3 adds
only conformance/architecture evidence. `Filesystem` remains absent from the
Core prelude. No source-expressible standard behavior has moved back into Java.

I021-A widens the already-audited Filesystem resource/capability selector surface
from `open` to `open`/`replace`/`remove` through the same single
`ProtosClosureValue.nativeClosure(...)` construction helper. The repository-wide
definitive boundary therefore remains **107 sites across 30 providers**. The new
host-neutral namespace flow owns Future/cancellation/commitment mechanics; no
package-manager-specific native operation or ambient Filesystem authority is
introduced.

I021-B adds the production confined NIO namespace backend behind that existing
Filesystem bridge and adds no `ProtosClosureValue.nativeClosure(...)` construction
site. I021-C closes I021 with ordinary Protos-source integrated conformance over
the production backend. Package-tool Filesystem Slice 2B / B006 reuses that same
standard bridge for exact staging `createNew` writes and namespace replace/remove;
it adds no Core native Closure construction site or package-specific filesystem
primitive. The definitive Core native boundary therefore remains **107 sites across 30 providers**.

I017-B is a reviewed post-I018 representation-boundary extension. It adds exactly
three native-Closure construction sites for the construction-only Process-argument
snapshot protocol (`size`, `at`, and `each`), bringing the current boundary to
**94 sites across 24 providers**. The snapshot's canonical per-Process identity
and immutable backing are runtime representation state; no `Process` or argument-
snapshot authority is added to the Core prelude.

I017-C is a reviewed post-I018 representation-boundary extension. It adds exactly
three native-Closure construction sites for the construction-only standardized
Environment protocol (`get`, `contains`, and `each`), bringing the current boundary
to **97 sites across 25 providers**. Native-name identity and representability,
selective value decoding, whole-snapshot enumeration prevalidation and canonical
Unicode-scalar ordering are representation-boundary semantics; Environment remains
outside the required Core prelude and carries no live Process authority.

I017-D1 is a reviewed post-I018 resource/capability-boundary extension. It adds
exactly two native-Closure construction sites for construction-only Process
standard-stream view protocols (`read` and `write`), bringing the current boundary
to **99 sites across 26 providers**. The Process-local binding owns cross-view and
cross-Actor ordering/backpressure state; Actor-local views expose no implicit
Closable/File/text/seek/flush authority and the live stream capability has no P
transfer contract.

I015-A is a reviewed post-I018 representation-boundary extension. The standard
Encoding factory/prototype identity is source-backed in `protos/lib/core/Encoding.protos`;
Java adds exactly two native-Closure construction sites for represented one-shot
`encode`/`decode`, bringing the current boundary to **101 sites across 27 providers**.
The immutable descriptors carry no I/O authority; there is no String-name registry,
alias lookup, host codec discovery, or public constructor.

I011-17 widens the audited Actor communication selector surface without adding a Java
native-Closure construction site. `ActorRef` and `GroupRef` each receive distinct
`send`/`request` Closure values created through the same two already-counted communication
construction helpers in `ProtosStandardActorProtocol`; the provider remains at eight sites
and the repository-wide provider/site totals therefore remain whatever the definitive
baseline records. The new internal `GroupRef` prototype identity is source-backed.

I017-E1 is a reviewed post-I018 resource/capability-boundary extension. The public
authority-free `Process` prototype identity is source-backed in
`protos/lib/core/Process.protos`; Java adds one native-Closure construction helper
that materializes the eight runtime-backed synchronous Process accessors. The
executable selector-surface guard fixes all eight names explicitly, while the
construction-site inventory grows only once, to **102 sites across 28 providers**.
No Process capability is created by the prototype and no root/module/host bootstrap
authority is introduced by E1.

I017-F completes the post-I017 native-boundary re-audit after D2/E2/E3. Those
slices and F add bootstrap state, host-neutral assembly, CLI wiring, authority
confinement and conformance but no Java `nativeClosure` construction site.
The final I017 boundary therefore remains exactly **102 sites across 28 providers**.
The executable architecture guard still fixes both the provider/site inventory
and the eight-selector native surface of the source-backed `Process` prototype.

I015-B is a reviewed post-I018 resource/capability-boundary extension. The
`TextReader` factory/prototype identity is source-backed in
`protos/lib/core/TextReader.protos`; Java adds exactly two native-Closure
construction helpers: one shared by borrowing/owning construction and one shared
by wrapper `readText`/`close`. Transactional incremental decoding, ordered
Future/cancellation state, wrapper close and ownership necessarily cross
representation/resource boundaries. The audited boundary therefore becomes
**105 sites across 29 providers**. I015-B initially covered the four portable Encoding descriptors. I015-E later
completes the same two-site bridge for explicitly host-provided transactional
streaming codecs without changing the TextReader native-Closure count.

I015-C widens the already-audited TextReader wrapper selector surface with
`readLine()` / `readLine(maxBytes)` through the same existing operation-Closure
construction helper. No Java `nativeClosure` construction site is added:
deterministic line framing, CRLF fold state, strict portable scalar decoding,
encoded-octet accounting, cancellation and permanent LineTooLong/failure state
live inside the existing runtime resource bridge. The audited provider/site
totals therefore remain unchanged from the definitive I015-B baseline.

I015-D is a reviewed resource/capability-boundary extension. The public
`TextWriter` factory/prototype identity is source-backed in
`protos/lib/core/TextWriter.protos`; Java adds exactly two native-Closure
construction helpers, one shared by borrowing/owning factory selectors and one
shared by wrapper `writeText`/`writeLine`/`flush`/`close`. Complete portable
encoding validation, ordered output commitment/failure, Flushable propagation,
close cutover and explicit ownership cross representation/resource boundaries.
The audited boundary therefore grows by exactly **2 sites / 1 provider** from the
definitive I015-C baseline. I015-E later completes host-provided transactional
encoder state and close finalization without adding another construction site.

I015-E completes I015 without widening the Java-native selector/construction
boundary. `ProtosEncodingValue` now makes the explicit host Encoding-provisioning
boundary semantically complete through fresh transactional per-flow streaming
codec states and descriptor-carried decode/BOM policy. Portable replacement uses
the required Unicode maximal-subpart rule independent of source chunking;
TextReader and TextWriter accept the same semantic Encoding family for portable
and host descriptors; state speculation remains reversible until the surrounding
I/O operation commits; and TextWriter close propagates required encoder-final
bytes before owned-target release. The post-I015 exhaustive guard remains exactly
**107 sites across 30 providers**.


I011-21 is a reviewed concurrency/runtime-boundary extension implementing D039's
only Core ActorGroup acquisition selector. `Actor.group(...)` adds exactly one
native-Closure construction site to `ProtosStandardActorProtocol`, because the
operation crosses current-Actor/Process ownership, Group identity/membership,
and GroupRef capability construction in one synchronous cutover. It creates no
public Group object, discovery registry, controller, placement, endpoint, or
transport-selection API. The current boundary is therefore **103 sites across
28 providers**.

## Source-backed I018 invariants

I018 specifically prevents the following ordinary derived behavior from
regressing to Java-only implementation:

- `Object.init`, default `Object.==`, and `Object.!=`;
- `Integer.negated` and `Integer.%`;
- `Float.negated`.

It also moved Java-allocated standard identities into Core source for:

- the public `Actor`, `Process`, `TextReader`, `TextWriter`, `BufferedReader`, `BufferedWriter`, and `import` objects;
- the construction-only standard `Bytes`, `ActorRef`, `GroupRef`, and `SendOperation`
  prototypes;
- the final frozen prelude bindings object itself.

The remaining primitive/native operations are still installed as ordinary
Closure-valued slots. There is no parallel hidden Java dispatch surface.

## Architectural guard

`ProtosCoreNativeBoundaryArchitectureTest` is the executable I018 closure guard.

It checks:

1. the exact set of production Java files that construct native Closures and the
   exact current construction-site count for every provider;
2. the exact native local-selector surface of statically reachable Core
   prototypes, including helper-installed numeric, Future, parallel, Actor, and
   factory surfaces;
3. the internal Bytes, ActorRef, and SendOperation native surfaces;
4. that migrated Object/Integer/Float behavior still has source definition and
   execution-plan provenance and no native body;
5. that `ProtosCoreBootstrap` retains only its two direct host construction
   contexts and does not reconstruct the prelude slot-by-slot in Java.

A change that intentionally adds or reclassifies a native standard boundary must
therefore update both this inventory and the executable guard in the same
reviewed change. A change that can instead be expressed faithfully in ordinary
Protos must place that behavior under `protos/lib/core/`.

I022-C is a reviewed host-irreducible control-boundary extension. D043's standard Closure `ensure(cleanup)` cannot be expressed by ordinary Protos before the protected dynamic extent/unwind primitive exists, so `ProtosStandardObjectProtocol` gains exactly one native Closure construction site. The selector remains an ordinary local `Object` slot with Closure-only standard receiver behavior; no resource-specific cleanup primitive or general cancellation mask is introduced. I022-C closes synchronous normal/non-local-return/Error cleanup; I022-D/E remain responsible for replay/suspension and cancellation-unwind closure.
