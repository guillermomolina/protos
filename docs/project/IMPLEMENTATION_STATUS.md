# Implementation Status

<!-- BEGIN CANONICAL IMPLEMENTATION STATUS -->

This file is the canonical repository-level status view for implementation and
the formally tracked project work that drives it. It is project state, not
normative language specification.

Agents must verify this ledger against the current `origin/main` before acting
on it. Git history, the current implementation, tests, and normative dependency
owners remain the evidence used to verify a row.

Initialized: 2026-09-05
Repository implementation version at initialization: `0.2.83-SNAPSHOT`


<!-- PROJECT-STATUS-FAMILY-DISCOVERY: v5 -->
## Status vocabulary

- `OPEN` — known implementation item not yet ready or not yet started.
- `READY` — dependencies are satisfied and implementation may begin.
- `IN_PROGRESS` — work is actively underway; advisory only, never a lock.
- `BLOCKED` — a recorded blocker prevents progress; see the blocker ledger.
- `BLOCKED_BY_DEPENDENCIES` — prerequisite implementation items remain open.
- `CLOSED` — required implementation, validation, and publication are complete.

`CLOSED` requires successful required tests and publication to `main`. Patch
generation, static validation, or `READY_FOR_USER_VALIDATION` alone do not close
an item.

## Core implementation

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| I001 | args as Array | CLOSED | historical; not backfilled | — |
| I002 | uniform represented-value lookup | CLOSED | historical; not backfilled | — |
| I003 | Standard String | CLOSED | historical; not backfilled | — |
| I004 | Array completion | CLOSED | historical; not backfilled | — |
| I005 | Standard Map | CLOSED | historical; not backfilled | — |
| I006 | IdentityMap / identity hashing | CLOSED | historical; not backfilled | — |
| I007 | Core Error infrastructure | CLOSED | historical; not backfilled | — |
| I008 | Modules | CLOSED | historical; see git history / CHANGELOG | — |
| I009 | Future / Task | CLOSED | historical; see git history / CHANGELOG | I009A + I009B complete |
| I010 | Parallel Execution | CLOSED | `f94362d50f9c809e62d2a84665f75b091bead2ca` | I009 |
| I011 | Actors complete | IN_PROGRESS | — | build on I009/I010; see the verified I011 slice ledger below |
| I012 | Standard Bytes | CLOSED | historical; see git history / CHANGELOG | — |
| I013 | Standard Path | CLOSED | historical; see git history / CHANGELOG | — |
| I014 | Standard Byte I/O | CLOSED | `2462ba74298e94181489e13de4e25dbbb82b21f9` | I009 + I012 |
| I015 | Encoding / Text I/O | READY | — | I014 closed |
| I016 | Filesystem / File | IN_PROGRESS | — | I013 + I014 closed |
| I017 | Process I/O / bootstrap | BLOCKED_BY_DEPENDENCIES | — | requires I016; re-audit exact current dependencies before starting |
| I018 | Core self-hosting / bootstrap minimization | IN_PROGRESS | — | independent implementation-placement initiative; I016-C was already published concurrently and I018 freezes further I016 progress |

### I011 — Actors

Status: IN_PROGRESS

Description: Core Actor runtime and communication semantics, implemented incrementally on the
existing I009 Future/Task and I010 isolated-parallel infrastructure.

Normative owners:
- `spec/concurrency/ACTORS.md`
- `spec/semantics/MODULES.md` for Actor-local bootstrap module loading
- `spec/concurrency/FUTURES_AND_TASKS.md` for Future/Task integration
- `spec/concurrency/PARALLEL_EXECUTION.md` for Actor/P isolation interaction

Published slices:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I011-1 | CLOSED | `0.2.81-SNAPSHOT` | `518aac11e2d3815a6bc790b66d93f214250cfd1d` | Actor incarnation identity; centralized lifecycle state machine; ActorRef permanently bound to one incarnation; deterministic lifecycle/concurrency coverage. |
| I011-2 | CLOSED | `0.2.82-SNAPSHOT` | `0c65053e4f6fbc1c090087c2bde6cd5ccd4322bd` | ActorRef as opaque communication capability; Actor-boundary rematerialization preserving semantic identity, identityHash, delegation parent, and target; no retargeting after termination. |
| I011-3 | CLOSED | `0.2.84-SNAPSHOT` | `SAME_COMMIT` | Destination-local bootstrap by canonical ModuleKey; exact local bootstrap-binding selection; invocation with already-transferred arguments; exact behavior installation and READY cutover; Actor execution-domain ownership/current-ActorRef substrate; initialization-failure termination. |
| I011-4 | CLOSED | `0.2.85-SNAPSHOT` | `SAME_COMMIT` | Actor-boundary graph snapshot/value-transfer foundation: atomic copy/validation for currently integrated transferable value families; alias/cycle preservation across roots; ActorRef capability rematerialization; NonTransferableValue rejection for non-transferable execution/resource values. |
| I011-5 | CLOSED | `0.2.86-SNAPSHOT` | `SAME_COMMIT` | Public frozen Actor prelude surface with spawn/current; creator-side canonical module resolution and synchronous pre-creation validation; Actor-transfer-backed initialization vector; creation cutover with post-cutover destination-local bootstrap kickoff. |
| I011-6 | CLOSED | `0.2.88-SNAPSHOT` | `SAME_COMMIT` | Bounded accepted-message mailbox ownership; READY-gated implicit event-loop dispatch; automatic Actor-local scheduler wakeups; weak-fair cross-Actor scheduling with one non-preemptive segment per selection and no same-incarnation parallel execution. |
| I011-7 | CLOSED | `0.2.91-SNAPSHOT` | `SAME_COMMIT` | Concrete-Actor pre-acceptance delivery admission/backpressure foundation: pending operation ownership outside the bounded accepted mailbox; deterministic capacity wakeups; known pre-acceptance cancellation; same-sender FIFO preservation and weak admission fairness. |
| I011-8 | CLOSED | `0.2.93-SNAPSHOT` | `SAME_COMMIT` | Public `ActorRef.send(selector, arguments...)` over I011-7 admission: exact semantic-String validation, synchronous whole-graph snapshot, local identity-bearing SendOperation with exactly cancel/retry, normal behavior dispatch with ignored send result, and fresh explicit retry over the original snapshot after known delivery failure. |
| I011-9 | CLOSED | `0.2.99-SNAPSHOT` | `SAME_COMMIT` | Public `ActorRef.request(selector, arguments...)` over the shared delivery path: fresh caller-domain Future, reply-value Actor transfer without Future adoption/flattening, deterministic pre/post-acceptance cancellation mapping, `NonTransferableValue` reply failure, and `RequestOutcomeUncertain` for known accepted work lost before a normal reply. |
| I011-10 | CLOSED | `0.2.105-SNAPSHOT` | `SAME_COMMIT` | Graceful Actor lifecycle: public idempotent `ActorRef.stop()` and fresh independent `ActorRef.termination()` observation Futures; TERMINATING-cutover cancellation of Actor-local tasks, Actor-originated pending non-task Futures and I/O; accepted-undispatched loss preservation; TERMINATED only after required task cancellation unwind. |

Remaining implemented-surface gaps before top-level closure:
- distributed/Group routing and delivery uncertainty beyond the direct concrete-Actor path, including remaining SendOperation uncertainty finalization;
- remaining specialized transfer integrations whose state/authority has its own contract (including keyed collections and future GroupRef/Process capability materialization); reply transfer is wired for the value families already supported by Actor transfer;
- failure authority, RootActor/process integration, and final deterministic cross-domain race/conformance coverage.


### I016 — Filesystem / File

Status: IN_PROGRESS

Coordination note: the requested pause after I016-B was overtaken by the already-published I016-C slice before I018-A could run. I018 does not revert that published history and freezes further I016 progress at I016-C; the remaining I016 gaps below are not started by I018.

Description: Core Filesystem/File semantics implemented incrementally on I013 Path and I014 byte-I/O commitment/lifecycle infrastructure.

Normative owners:
- `spec/io/FILESYSTEM.md`
- `spec/io/IO_CORE.md`
- `spec/io/BYTE_IO.md`
- `spec/io/PROCESS_IO.md` for Root bootstrap provisioning and authority-transfer boundaries

Published slices:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I016-A | CLOSED | `0.2.87-SNAPSHOT` | `SAME_COMMIT` | Host-neutral Filesystem open preflight/acquisition substrate: exact local-option snapshot/defaults, invalid-combination precedence before backend authority, independent asynchronous opens, cancellation/portable-effect commitment handshake, backend result-custody cleanup, and Actor-termination cancellation integration. No public Filesystem/File capability surface is installed yet. |
| I016-B | CLOSED | `0.2.89-SNAPSHOT` | `SAME_COMMIT` | Positioned File capability core: stable zero-based logical cursor independent of native cursors; ordered read/write/position/seek/seekToEnd/size/truncate/sync; exact access/optional capability shape; bounded write admission; Closable lifecycle; Actor-termination/pre-commit cancellation integration. No public Filesystem.open surface and no append mode yet. |
| I016-C | CLOSED | `0.2.90-SNAPSHOT` | `SAME_COMMIT` | Append-mode File semantics: each non-empty write selects then-current EOF at contribution time; empty append preserves the cursor; failed-prefix aftermath is exact; same-resource File aliases use a backend-wide atomic append-placement boundary with nondeterministic relative order and no overlap/interleaving; pre-commit cancellation/Actor termination contribute nothing. |

Remaining implemented-surface gaps before top-level closure:
- language-visible Filesystem capability open surface once returned File capabilities are complete;
- confinement/stable-resource-binding backend integration and deterministic race/conformance coverage;
- capability transfer/provisioning boundaries plus final ledger/dependency closure.


### I018 — Core self-hosting / bootstrap minimization

Status: IN_PROGRESS

Description: Reduce Java-side Core bootstrap to irreducible host/runtime machinery and narrow representation/selector bridges, moving faithfully expressible distributable Core behavior to `protos/lib/core/` without changing observable Protos semantics.

Architecture owner:
- `docs/project/CORE_BOOTSTRAP_ARCHITECTURE.md`

Normative constraints:
- applicable normative semantic owners remain authoritative;
- I018 changes implementation placement only and must not redefine Protos behavior;
- source-backed Core behavior must continue to use ordinary objects, Closures, lookup, invocation, errors, and delegation.

Published slices:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I018-A | CLOSED | `0.2.92-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `Object.init` and `Object.!=` bodies loaded from `protos/lib/core/object.protos`; isolated frozen capture context prevents the process-global `Object` from retaining the main Core-construction bindings; Java retains only the current installation bridge and host-backed primitives needed by this slice. |
| I018-B | CLOSED | `0.2.94-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `Actor` prelude object loaded from `protos/lib/core/actor.protos`; Java no longer allocates the public Actor entry object and now only installs the host-backed `spawn`/`current` bridges into that exact source-created object before freezing it. |
| I018-C | CLOSED | `0.2.95-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `BufferedReader` and `BufferedWriter` frozen-prelude factory/prototype objects loaded from `protos/lib/core/buffered_reader.protos` and `protos/lib/core/buffered_writer.protos`; Java no longer allocates their factory identities and only installs the host-backed `call`/`owning` bridges into those exact source-created objects. |
| I018-D | CLOSED | `0.2.96-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `import` frozen-prelude facility loaded from `protos/lib/core/import.protos`; Java no longer allocates the public import-facility identity and only installs the host-backed `call` bridge that preserves exact semantic-String validation, host resolution, Actor-local module caching, cycles, and failure behavior. |
| I018-E | CLOSED | `0.2.97-SNAPSHOT` | `SAME_COMMIT` | Source-backed internal standard `Bytes` factory/prototype loaded from `protos/lib/core/bytes.protos`; Java no longer allocates the bootstrap Bytes identity used by buffered byte wrappers, installs the existing native Bytes protocol into the exact source-created object, and removes the construction-only `Bytes` binding before building the frozen prelude so Core v0.1 still exposes no required `Bytes` prelude binding. |
| I018-F | CLOSED | `0.2.98-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard ordinary `Integer.negated()` body defined in `protos/lib/core/integer.protos` as `0 - this`; Java no longer implements unary Integer negation directly and now only verifies that the installed `negated` slot is source-backed, while the exact Integer subtraction primitive remains host-backed and preserves the semantic Integer receiver domain. |
| I018-G | CLOSED | `0.2.100-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard ordinary `Integer.%` body defined in `protos/lib/core/integer.protos` through a temporary named Closure and installed under the symbolic `%` selector by a narrow Java selector bridge; the source body validates the original receiver through standard native Integer `+` before dispatching native `mod`, preserving Integer receiver-domain rejection while removing the duplicated native remainder body for `%`. |
| I018-H | CLOSED | `0.2.101-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard `Float.negated()` body defined in `protos/lib/core/float.protos` as `(0.0 - 1.0) * this`; Java no longer performs Float unary sign inversion directly and now only verifies source provenance, while native binary64 subtraction/multiplication preserve strict Float receiver-domain validation, signed-zero inversion, infinities, subnormals, and Core NaN semantics. |
| I018-I | CLOSED | `0.2.102-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard frozen-prelude bindings object constructed in `protos/lib/core/prelude.protos` with direct `Context` parent and the exact existing Core binding surface; Java no longer allocates or populates the prelude object slot-by-slot, and Error-taxonomy export is source-owned while Java retains taxonomy validation and the final freeze/ProtosPrelude boundary. |
| I018-J | CLOSED | `0.2.103-SNAPSHOT` | `SAME_COMMIT` | Source-backed default `Object.==` body defined in `protos/lib/core/object.protos` as primitive semantic identity (`this === other`) and promoted through the existing narrow symbolic-selector installation path; Java no longer duplicates default equality logic, while primitive `===`, `identityHash`, generic call/object construction, and other host/runtime boundaries remain native. |
| I018-K | CLOSED | `0.2.104-SNAPSHOT` | `SAME_COMMIT` | Source-backed internal standard `ActorRef` and `SendOperation` delegation prototypes are constructed in `protos/lib/core/actor.protos` and supplied to the Actor runtime installer; Java no longer allocates those standard prototype identities and retains only the Actor/runtime communication bridges (`send`/`request`, `cancel`/`retry`) plus representation, scheduling, transfer, and lifecycle machinery. The construction-only helper bindings are removed before prelude construction and remain absent from the public prelude. |

Remaining implementation work before top-level closure:
- complete the exhaustive inventory of every remaining Java-installed standard slot and classify it as host-irreducible or a narrow representation/selector bridge, explicitly proving that no source-expressible slot remains;
- add architectural regression coverage that prevents new source-expressible standard behavior from being introduced only in Java;
- close I018 and lift the I016-D pause only if that inventory/guard passes on the definitive `origin/main`.


## CLI implementation

| Item | Description | Status | Version | Closure evidence | Notes |
|---|---|---|---|---|---|
| CLI001 | Basic CLI + persistent REPL | CLOSED | — | historical; not backfilled | — |
| CLI002 | Interactive terminal UX | CLOSED | — | historical; not backfilled | — |
| CLI003 | Multiline REPL input | CLOSED | `0.2.80-SNAPSHOT` | `254c80c0fb9e70f1dd07ef711f06ce71faa93829` | published multiline REPL input; parser-EOF accumulation, one-unit bracketed paste, persistent top-level context, recovery/history/stream coverage; recursive factorial regression is covered after standard numeric ordering completion |


## Language Maturity

The `LMxxx` family records bounded language-maturity / dogfooding work:
language-level conformance programs, executable examples, tutorials, and
interaction/regression coverage that exercise already-implemented semantics
without itself defining new normative language behavior.

The first four LM identifiers were assigned retrospectively by the v6 historical
reconciliation because the work was published on `main` before the repository
persisted its operational LM identifiers. Their closure evidence is therefore
the already-published implementation commit.

| Item | Description | Status | Closure evidence | Notes |
|---|---|---|---|---|
| LM001 | Language-level coverage and examples | CLOSED | `99845b791a8e27798bc2c9cc8e47dc917d739a70` | Retrospective canonical ID; Map, IdentityMap, Path and regression conformance/examples. |
| LM002 | Language-level conformance and tutorials | CLOSED | `c0ba8b3f5bf3dc0a0997ca5fbfae6035db04d1d9` | Retrospective canonical ID; call-argument, Array and tutorial/example coverage. |
| LM003 | Language interaction conformance | CLOSED | `dbc086ed294ead0b4219952c02b00ec28a492472` | Retrospective canonical ID; delegation, dynamic receiver, captured-state and inherited-call interaction coverage. |
| LM004 | Extended language interaction conformance | CLOSED | `d390c0c642c5d2d907fcf9e384d1cbc080dd4783` | Retrospective canonical ID; deeper delegation/call-argument interactions plus Map/IdentityMap Path-key behavior. |

New Language Maturity work MUST allocate and persist its `LMxxx` identifier in
the repository at publication time rather than relying on chat/prompt history.

## P-label classification

`P57` and similar `Pnn` references found in historical conformance/changelog
text are specification/requirement paragraph labels, not a repository
project-work family analogous to `Ixxx`, `Bxxx`, `Dxxx`, `CLIxxx`, or `LMxxx`.

For example, the changelog records “P57 Integer conformance programs” as tests
covering the P57 Integer requirement. That evidence MUST NOT be auto-promoted
to a project-status item named `P057` or `P57`.

If a future project-work family named `Pxxx` is introduced, it must be declared
explicitly by a canonical project ledger; identifier resemblance alone is not
sufficient.

<!-- PROJECT-STATUS-HISTORICAL-RECONCILIATION: v6 -->

<!-- BEGIN AUTO-DISCOVERED WORK REGISTRY -->

## Formally tracked project work

This auto-generated registry complements, but does not duplicate, the curated implementation and CLI tables above. It indexes formal non-I/CLI work families from authoritative project records and published specification decisions.

Identifier shape alone is insufficient: incidental IDs from design ideas, tests, benchmarks, examples, and arbitrary prose are intentionally excluded.

`RECORDED` means the owning project record contains the item without an explicit lifecycle state. Published decisions in `spec/PROTOS_SPEC_CHANGELOG.md` are `CLOSED`.

### B family

| Item | Title | Status | What it records / establishes | Owning source(s) |
|---|---|---|---|---|
| B001 | Empty Sequence execution | CLOSED | Implementation area: Truffle lowering / execution of a `CanonicalSequence` containing zero expressions. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B002 | Delegation parent of `without` / `alias` result objects | CLOSED | Implementation area: Standard `Object.without(name)` and `Object.alias(sourceName, aliasName)` message behavior and any runtime helper that constructs their result objects. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B003 | Delegation parent / lookup chain of canonical Boolean values | CLOSED | Implementation area: Standard prototype/delegation bridge for the canonical `true` and `false` runtime representations, including ordinary member lookup and polymorphic invocation through their delegation chains. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |

### D family

| Item | Title | Status | What it records / establishes | Owning source(s) |
|---|---|---|---|---|
| D017 | Actor API closure cleanup | CLOSED | Removed residual wording that presented already-closed Core Actor API decisions as open or implementation-selectable. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D018 | Canonical Process bootstrap snapshot identity | CLOSED | Administratively records the already-published Process bootstrap snapshot identity semantics: each logical Process has one canonical identity-bearing `process.args()` snapshot and one canonical identity-bearing `proce... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D019 | Actor creator capability discipline | CLOSED | Removed the stale `parentActor` ambient capability from Core Actor semantics: creation genealogy alone grants no reverse `ActorRef`, creator lookup, or implicit reply channel to the created Actor. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D020 | String transformation surface | CLOSED | Closed the Core v0.1 status of `uppercase()` and `replace(...)`: neither selector is a standard Core String operation. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D021 | GroupRef semantic identity | CLOSED | Distinguished Group identity, semantic `GroupRef` object identity, and physical proxy/wrapper representation. Same-Group references are not automatically the same `GroupRef`. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D022 | Standard Object.init normal result | CLOSED | Defines the inherited standard `Object.init()` normal result as its receiver (`this`), making direct invocation portable. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D023 | Slot-write expression normal result | CLOSED | Defines the normal result of `x: value`, `object.x: value`, `x = value`, and `object.x = value` as the exact object produced by right-hand-side evaluation. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D024 | Receiver-bound Closure semantic identity | CLOSED | Defines every successful receiver member-read selecting a Closure as producing a fresh identity-bearing Closure value distinct from both the stored Closure and every other extraction result, including repeated identic... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D025 | Closure asynchronous-method ownership | CLOSED | Fixes the standard Closure-specific selectors `future` and `parallel` as ordinary local Closure-valued slots of `Object`; every Core Closure reaches them through its D027 direct delegation edge to `Object`, with no st... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D026 | Boolean standard-object surface | CLOSED | Resolves the normative contradiction over a standard `Boolean` object: Core v0.1 defines exactly the canonical Boolean values `true` and `false` and installs no standard prelude binding, object, or prototype named `Bo... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D027 | Portable Core delegation topology | CLOSED | Closes the observable standard-object topology with one general rule: every Core-standard visible object whose immediate parent is not otherwise specified delegates directly to `Object`. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D028 | Path parent-component selector disambiguation | CLOSED | Renames the standard Path operation that appends one parent-traversal component from `parent()` to `parentComponent()`. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D029 | Standard Integer result family | CLOSED | Defines one general result-only rule in the Values and Collections numeric owner: when a Core-standard operation returns or resolves simply to `Integer`, without naming a more specific numeric family, the result is an... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D030 | Callback-domain and eager-validation closure | CLOSED | Defines `Future.then(transform)` against the existing ordinary-invokable protocol rather than a hidden or Closure-only callback category. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D031 | Idempotent lifecycle Future identity | CLOSED | Defines one cross-cutting I/O lifecycle rule: every invocation of a standardized Future-returning idempotent lifecycle operation produces a fresh standard Future identity, even when calls observe the same pending or a... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D032 | Fresh reflection Array identity | CLOSED | Defines every successful `slotNames()` call as producing a fresh identity-bearing standard Array, including repeated observations of an unchanged object and empty results. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D033 | Reflective local-slot name argument domain | CLOSED | Defines the standard `hasSlot(name)`, `slotValue(name)`, and `removeSlot(name)` argument domain uniformly as semantic `String` values. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D034 | Callback-domain and eager-validation closure | CLOSED | Defines `Future.then(transform)` against the existing ordinary-invokable protocol rather than a hidden or Closure-only callback category. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D035 | Fresh and independent standard Bytes results | CLOSED | Defines every successful Core-standard operation that produces a logical new `Bytes` result as returning a fresh open standard Bytes identity, including empty results, unless that operation expressly returns an existi... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D036 | Future result identity semantics | CLOSED | Defines the general Core-standard Future result-identity rule: unless an operation expressly returns an already-existing Future, every successfully dispatched invocation that produces a Future result produces a fresh... | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D037 | Path equality versus semantic identity | CLOSED | Clarifies that portable Path equality is structural and filesystem-independent, using rootedness plus the ordered component sequence, while Path semantic identity remains ordinary individual object identity. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D038 | Encoding semantic-family membership and receiver domain | CLOSED | Defines Encoding descriptors positively as Encoding semantic values produced or provisioned by normative Encoding-producing operations or explicit permitted host Encoding-provisioning boundaries. | `spec/PROTOS_SPEC_CHANGELOG.md` |

<!-- END AUTO-DISCOVERED WORK REGISTRY -->

## Update protocol

When publishing an implementation item:

1. verify this file against the current `origin/main`;
2. update the item's status in the same patch whenever practical;
3. record the implementation version if the item changes it;
4. record closure evidence:
   - use the concrete implementation SHA when it is already known, or
   - use `SAME_COMMIT` when the implementation and ledger update are the same
     commit;
5. update dependency transitions made possible by the closure;
6. keep unrelated rows unchanged;
7. do not use this ledger as a substitute for normative audit;
8. when any formal tracked work item or family is added or materially changes
   lifecycle state, update the appropriate curated table/ledger or regenerate
   the tracked-work registry in the same change whenever practical;
9. keep item explanations concise and point to the owning source rather than
   duplicating normative/design text.

For Language Maturity work, allocate the next unused `LMxxx` identifier before
publication and include it in the same repository change. Do not create LM IDs
only in prompts or chat history.

If an item is implemented through slices, the top-level item remains
`IN_PROGRESS` until every requirement assigned to that item is integrated,
validated, and published. Slice progress may be recorded in a dedicated
subsection when useful, but partial slice publication does not imply top-level
closure.

## Related project ledgers

- `docs/project/IMPLEMENTATION_BLOCKERS.md` — normative implementation blockers.
- `docs/project/OPEN_TASKS.md` — operational/project backlog.
- `docs/project/IMPLEMENTATION_STATUS.md` — implementation progress and
  dependency state.

<!-- END CANONICAL IMPLEMENTATION STATUS -->
