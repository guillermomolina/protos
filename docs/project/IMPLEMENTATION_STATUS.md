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

## Governance and decision audits

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| AUD001 | Retrospective D001-D045 design-decision ratification audit | OPEN | — | HIGH priority; D046 excluded. The specification `0.1.90` structured-child outcome policy is RATIFIED by explicit owner approval on 2026-09-08; its follow-up is closed. D045's task-scoped ownership core is RATIFIED by explicit project-owner approval on 2026-09-08. D044 review is in progress. D037 is RATIFIED from recovered explicit project-owner confirmation; D038 already has explicit owner confirmation. D043 is RATIFIED by explicit project-owner approval on 2026-09-08; clarification is specification `0.1.385`. D039's public ActorGroup acquisition is RATIFIED by explicit project-owner approval on 2026-09-08. D035's fresh/independent standard Bytes-result semantics are RATIFIED from recovered explicit project-owner selection on 2026-09-04. D036's Future result-identity semantics are RATIFIED by explicit project-owner approval on 2026-09-08. D033's strict semantic-String reflective local-slot name domain is RATIFIED by explicit project-owner approval on 2026-09-08 after comparative and future-scalability review. D032's fresh independent `slotNames()` reflection-Array semantics are RATIFIED by explicit project-owner approval on 2026-09-08 after comparative and future-scalability review. D020's Core-v0.1 String transformation boundary is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review. D030's ordinary-invokable eager-validation `Future.then` semantics are RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review. D029's general bare-Integer standard-result family is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review. D027's portable Core-v0.1 delegation topology is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review; D025/D026 remain independent AUD001 decisions. D026's no-standard-Boolean-object Core surface is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review. D025's ordinary `Object.future`/`Object.parallel` Closure-specific ownership is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review; D024/D026 and later `ensure`/`while` ownership remain independent decisions. D023's exact-RHS slot-write expression result is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review. D022's standard inherited `Object.init()` normal-result semantics are RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review; construction still returns the fresh instance independently of an override's ordinary normal result, and D021/D023/D024 remain independent AUD001 decisions. D021's GroupRef semantic capability identity is RATIFIED by explicit project-owner approval on 2026-09-08 after distributed-systems, object-capability, adversarial and future-scalability review. D001's empty semantic `Sequence` normal-result rule is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review; zero-expression normal completion yields canonical `null`, non-empty normal completion preserves the final expression's exact result, escaping control transfers are not converted, object-body construction remains separate, and D002-D019 remain independent AUD001 decisions. D003 is RATIFIED AS AMENDED by explicit project-owner approval on 2026-09-08; specification `0.1.386` retains the D003/0.1.340 deterministic binding core and requires required non-rest Closure parameters before defaulted parameters; `I025` is READY for parser/conformance alignment. D004's standard Boolean protocol is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review, retaining ordinary-message control, strict canonical-Boolean standard receiver/result rules, selected-only callback invocation, no truthiness or hidden asynchronous behavior, and mandatory `&&`/`||` lowering through RHS Closures; no other Dxxx decision is classified by this slice. D006's Core numeric-family/arithmetic model is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review; specification `0.1.344` is retained without normative amendment. D007's strict import/resolver boundary is RATIFIED by explicit project-owner approval on 2026-09-08 after corrected cross-language, adversarial, package-system and future-scalability review; semantic String validation remains separate from host interpretation and canonical ModuleKey identity. D005's Error occurrence, identity and portable-taxonomy policy is RATIFIED by explicit project-owner approval on 2026-09-08 after cross-language, adversarial and future-scalability review; fresh standard failure occurrences, exact re-signaling/recorded Error identity, fresh per-observation `Cancelled`, domain-local identity, non-resumable signaling and the deliberately shallow D005 taxonomy are retained, while D002/D006-D019 and later Error/handler/cleanup decisions remain independent AUD001 work. See docs/project/OPEN_TASKS.md. |

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
| I011 | Actors complete | CLOSED | `SAME_COMMIT` | I011-1 through I011-21 complete; D039 acquisition implemented; B004 closed |
| I012 | Standard Bytes | CLOSED | historical; see git history / CHANGELOG | — |
| I013 | Standard Path | CLOSED | historical; see git history / CHANGELOG | — |
| I014 | Standard Byte I/O | CLOSED | `2462ba74298e94181489e13de4e25dbbb82b21f9` | I009 + I012 |
| I015 | Encoding / Text I/O | CLOSED | `SAME_COMMIT` | I015-A/B/C/D/E complete; portable and explicit host Encoding descriptors share final one-shot/streaming TextReader/TextWriter semantics; post-I015 I018 audit unchanged |
| I016 | Filesystem / File | CLOSED | `SAME_COMMIT` | I013 + I014; I016-A/B/C/D1/D2/D3/D4 complete |
| I017 | Process I/O / bootstrap | CLOSED | `SAME_COMMIT` | I017-A/B/C/D1/D2/E1/E2/E3/F complete; final authority/termination/CLI/native-boundary conformance published |
| I018 | Core self-hosting / bootstrap minimization | CLOSED | `SAME_COMMIT` | I018-L exhaustive native-boundary inventory and architectural guard complete; I016-D pause lifted |
| I019 | Core source naming reconciliation | CLOSED | `SAME_COMMIT` | 29 dominant-owner distributable Core sources use exact canonical Protos names/case after I019-A; private subordinate bootstrap helpers do not defeat public conceptual ownership; all live explicit Core physical-path references reconciled; true aggregation/responsibility sources remain descriptive; no normative or native-boundary change |
| I019-A | Actor source dominant-owner naming correction | CLOSED | `SAME_COMMIT` | `actor.protos` -> `Actor.protos`; public `Actor` is the dominant conceptual owner and private ActorRef/GroupRef/SendOperation prototype bindings are subordinate bootstrap helpers; naming guard and architecture classification reconciled |
| I020 | Post-Ixxx implementation audit reconciliation | CLOSED | `SAME_COMMIT` | I020-A/B/C/D complete; D040 missing-`methodHome` `InvalidSuper` implemented; B005 closed |
| I021 | Filesystem namespace replacement/removal | CLOSED | `SAME_COMMIT` | I021-A/B/C complete; D042 / spec `0.1.379`; production confined namespace backend and Protos-visible integrated conformance published; B006 CLOSED by package-tool Filesystem Slice 2B metadata publication integration |
| I022 | Dynamic Error handlers / unwind-safe cleanup | CLOSED | `SAME_COMMIT` | I022-A/B/C/D/E/F complete; D043 / spec `0.1.380`; replay-stable Error handlers, unwind-safe `ensure`, suspension, later-transfer precedence, cooperative cancellation, structured lifetime and task/Actor isolation have final adversarial closure evidence |
| I023 | Standard `while` protocol | CLOSED | `SAME_COMMIT` | D044 / spec `0.1.381` + D045 / spec `0.1.382`; I023-A/B/C/D complete; B007 CLOSED; DOC001-E subsequently published and CLOSED. |
| I024 | Filesystem directory observation + captured-tree capability | CLOSED | I024-A/A2/B/C/D `SAME_COMMIT`; D046 / spec `0.1.384`; B009 CLOSED | D046 is fully implemented and integrated conformance is published; B009 closes and TOOL001-F2E2 is READY. |
| I025 | D003 required-before-default Closure parameter ordering conformance | READY | — | Implement specification `0.1.386`: reject any required non-rest parameter after the first defaulted parameter, preserve optional-final-rest and all existing D003 binding semantics, and add focused parser plus Protos-visible syntax conformance. |

### I024 — Filesystem directory observation + captured-tree capability

Status: CLOSED

Purpose: implement D046 as amended by specification revision `0.1.384` as one
general capability-confined Filesystem mechanism, close B009, and then unblock
TOOL001-F2E2 without adding a package-specific host filesystem path.

Planned slices:

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| I024-A | CLOSED | `0.2.249-SNAPSHOT` | `SAME_COMMIT` | Published host-neutral D046 tree-observation flow. It remains implementation evidence, not design authority; A2 must verify it against the explicitly approved 0.1.384 amendment before dependent public work proceeds. |
| I024-A2 | CLOSED | — | `SAME_COMMIT` | Post-D046/0.1.384 compatibility re-audit found no production incompatibility. A's complete `List<Entry>` result and defensive snapshot match eager v0.1 entries; exact duplicate names fail closed; `CapturedTree` remains an internal opaque custody token with no public Directory/stream identity; cancellation/materialization/late-result paths release only untransferred custody, while successful terminal transfer does not invoke the release callback; Filesystem remains non-Closable. Existing focal and full-suite validation close the audit. |
| I024-B | CLOSED | `0.2.251-SNAPSHOT` | `SAME_COMMIT` | Publish both D046 selectors through the existing shared standard Filesystem native-Closure helper. `entries` materializes a fresh standard Array of fresh frozen ordinary name/kind descriptors. `captureTree` materializes a fresh captured Filesystem capability through an internal structurally read-only backend contract with no standard `close`; existing backends default-fail the new operations until they implement them. |
| I024-C | CLOSED | `0.2.253-SNAPSHOT` | `SAME_COMMIT` | Secure NIO direct-child no-follow observation and recursive capture are implemented by the existing complete-tree backend. Regular bytes spill into private managed backing; immutable metadata, captured-subtree sharing and internal Cleaner/lease custody preserve source independence without a public Directory or Filesystem close protocol. |
| I024-D | CLOSED | — | `SAME_COMMIT` | Integrated Protos-visible D046 conformance composes the production NIO backend with the standard Filesystem surface, covers complete exact no-follow entries, immutable read-only capture, source independence, cancellation/custody and verify-use stability, and revalidates the native boundary. Closes I024/B009 and makes TOOL001-F2E2 READY. |

Dependencies:
- D046 / specification revision `0.1.384` — CLOSED normative contract after explicit re-evaluation/amendment;
- I009 Future/Task — CLOSED;
- I013 Path — CLOSED;
- I016 Filesystem/File — CLOSED;
- I021 Filesystem namespace mutation — CLOSED;
- B009 — READY until final I024 implementation/conformance closes it.

I024-A deliberately does not publish the new messages. It establishes the
host-neutral producer/result lifecycle so later materialization and NIO work do
not duplicate cancellation/custody semantics. I024-A2 has now re-audited that
machinery against the explicitly approved D046/0.1.384 contract and found no
production incompatibility: eager complete-result representation, exact-name
uniqueness, cancellation/failure cleanup and successful custody transfer already
match the amended semantics. The internal release callback is strictly an
untransferred-custody mechanism and does not define a captured-Filesystem close
obligation. I024-B is now CLOSED: the standard Filesystem bridge exposes `entries` and `captureTree`, materializes their approved result shapes, and keeps existing backends default-fail until implementation support exists. I024-C is CLOSED: the complete-tree NIO backend provides secure no-follow directory observation and recursive immutable capture into implementation-managed backing while preserving the captured-Filesystem no-close contract. I024-D is also CLOSED: integrated Protos-visible conformance validates that backend through the standard surface, the native-boundary guard is reconciled, B009 is CLOSED, and TOOL001-F2E2 is READY.

### I023 — Standard `while` protocol

Status: CLOSED

Purpose: Implement D044 / specification revision `0.1.381` faithfully as the
standard Closure-specific `while(body)` protocol and close B007 without adding
new syntax, truthiness, a `Closure` prototype, hidden scheduling, or a second
callback/invocation model.

Normative owners:
- `spec/semantics/EXECUTION_AND_CONTROL.md` §17 for validation timing, exact
  condition/body activation order, strict canonical Boolean condition results,
  ignored body results, canonical `null` completion and control/concurrency
  composition;
- `spec/semantics/CALLABLES.md` for ordinary `Object.while` placement,
  Closure-family receiver domain, lookup/reflection/extraction/shadowing and
  Closure activation semantics;
- `spec/PROTOS_GRAMMAR.md` for ordinary call plus trailing-Closure syntax only;
- `spec/semantics/VALUES_AND_COLLECTIONS.md`, `ERRORS.md`, and
  `spec/concurrency/FUTURES_AND_TASKS.md` for the referenced Boolean, Error,
  suspension/cancellation and structured-ownership rules.

Planned slices:

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| I023-A | CLOSED | `0.2.210-SNAPSHOT` | `SAME_COMMIT` | Publish the standard `Object.while` selector with Closure receiver/body validation, exact one-argument contract, zero-argument pre-test condition/body activation, strict true/false loop decision, ignored body values, canonical `null` normal completion, zero/multiple-iteration Protos conformance, and no implementation-version-independent semantic additions. |
| I023-B | CLOSED | — | I023-B1 + I023-B2 published | Bounded replay retention plus complete adversarial synchronous closure are published; B closes and I023-C becomes READY. |
| I023-B1 | CLOSED | `0.2.212-SNAPSHOT` | `SAME_COMMIT` | Bound iterative replay retention: every normally completed condition/body callback commits its evaluator child suffix back to one stable per-while checkpoint, discards completed callback activation keys, and reuses the callback ordinal while retaining the enclosing `while` activation. Retained replay state is independent of completed iteration count; the former monotonic callback counter/int-overflow path is removed. No language or native-boundary change. |
| I023-B2 | CLOSED | — | I023-B2A + I023-B2B + I023-B2C + I023-B2D published | Invalid condition results, callback activation timing, synchronous transfer, and Future/task-scoped structured ownership are all closed. |
| I023-B2A | CLOSED | — | `SAME_COMMIT` | Adversarial Protos-source conformance proves that normal `null`, Integer, ordinary `Object`, and Future condition results all produce the D044 fresh generic standard Error before any body activation. Two independent invalid-null occurrences prove fresh Error identity and direct `Error` parentage; the Future case proves no implicit await/adoption. No runtime, specification, native-boundary, license, or implementation-version change. |
| I023-B2B | CLOSED | — | `SAME_COMMIT` | Protos-source conformance proves the D044 callback activation contract uses ordinary zero-argument Closure binding only when each callback is actually reached: earlier defaults execute before a later missing-required failure, an unreachable invalid body binding is untouched, a reached invalid body binds only after the true condition, and zero-argument defaults run normally on reached condition/body activations. The four immediately preceding B2A sources are also reconciled with the required APL Part 5 notice; no runtime, specification, native-boundary, license-term, or implementation-version change. |
| I023-B2C | CLOSED | — | `SAME_COMMIT` | Protos-source conformance proves synchronous D044 transfers from either reached callback cross the standard loop unchanged: condition/body Error signaling preserves the exact Error object selected by an outer handler; condition/body non-local return preserves the exact returned object identity and reaches the existing lexical return home; no body/next-condition/post-loop effect executes after the transfer, while effects completed before it remain visible. No runtime, specification, native-boundary, license-term, or implementation-version change. |
| I023-B2D | CLOSED | — | I023-B2D1 + I023-B2D2 published | D044 Future-result boundary plus D045 task-scoped structured-ownership reconciliation are published with no loop-specific wait/adopt/cancel/detach/re-parent behavior. |
| I023-B2D1 | CLOSED | — | `SAME_COMMIT` | Protos-source conformance proves that a normal Future returned by a reached body remains only an ignored body result: a failed child Future is not synchronously awaited/adopted and cannot stop later loop iterations, while a retained successful Future remains observable after the loop and is not cancelled/replaced by `while`. No runtime, specification, native-boundary, license-term, or implementation-version change. |
| I023-B2D2 | CLOSED | — | `SAME_COMMIT` | Protos-source conformance proves that a Future returned as the body result is not drained at the synchronous while-body activation: the post-loop parent effect occurs first. The enclosing asynchronous task nevertheless retains the child and cannot terminalize until that child finishes. Cross-B2 D4 execution and the retained JSON overlap canary pass; no runtime/specification/version/native-boundary change. |
| I023-C | CLOSED | — | I023-C1 + I023-C2 + I023-C3 + I023-C4 published | Condition/body replay, cancellation/ensure composition, repeated cross-phase suspension and bounded retained-state closure are all published. I023-D is READY for final cross-slice closure. |
| I023-C1 | CLOSED | — | I023-C1A + I023-C1B published | Single-suspension and every-logical-condition suspension both preserve exact-once effects across replay and completed-iteration compaction. |
| I023-C1A | CLOSED | — | `SAME_COMMIT` | Protos-source conformance forces the first reached condition activation to suspend at `Future.value()`. Replay resumes the same logical activation without duplicating the pre-suspension increment; after resume the first condition is true, one body runs, and the second condition is false, yielding exact encoded result `221`. No production runtime, specification, native-boundary, license-term, or implementation-version change. |
| I023-C1B | CLOSED | — | `SAME_COMMIT` | Protos-source conformance makes all three logical condition activations create a fresh child Future and suspend at `value()`. Exact result `33236` proves 3 pre-suspend effects, 3 post-resume effects, 2 bodies, 3 distinct child executions and observed values 1+2+3, so replay/compaction neither duplicates nor skips work. No runtime/spec/version/native-boundary/license-term change. |
| I023-C2 | CLOSED | — | `SAME_COMMIT` | Protos-source conformance makes both reached body activations create distinct child Futures and suspend at value(). Exact result `322223` proves 3 condition activations, 2 pre-suspend body effects, 2 post-resume effects, 2 body activations, 2 child executions and observed values 1+2. Replay and completed-iteration compaction neither duplicate nor skip body work. No runtime/spec/version/native-boundary/license-term change. |
| I023-C3 | CLOSED | — | `SAME_COMMIT` | Protos-source conformance self-requests cancellation in the first body, then proves while adds no cancellation poll by reaching the second condition and second body before ordinary Future.value() observes the request. Cancellation then unwinds through while, ensure cleanup runs exactly once, and code after the observation boundary does not run. Exact observer result `22110`. No runtime/spec/version/native-boundary/license-term change. |
| I023-C4 | CLOSED | — | `SAME_COMMIT` | Protos conformance forces four condition suspensions and three body suspensions with exact-once counters/child executions/result `443343016`. Java mechanism evidence compares the same in-condition suspension point after 8 vs 1024 completed suspending iterations and requires identical bounded retained event/activation counts. No runtime/spec/version/native-boundary/license-term change. |
| I023-D | CLOSED | — | `SAME_COMMIT` | Final cross-slice manifest/conformance validation, replay-retention and native-boundary architecture guards, full suite, B007 closure, native-inventory reconciliation, and DOC001-E dependency re-audit published. No production/runtime/spec/version/native-boundary/license-term change. |

Dependencies:
- D044 / specification revision `0.1.381` — normative loop contract;
- I007 Error infrastructure — CLOSED;
- I009 Future / Task — CLOSED;
- I022 dynamic handlers / unwind-safe cleanup — CLOSED.
- B008 returned-Future structured-ownership boundary — CLOSED via D045 / spec `0.1.382` plus published I023-B2D2 conformance; no runtime ownership change was required.

B007 is CLOSED by I023-D: the D044/D045 implementation and conformance boundary is fully published.
D044 itself does not publish runnable `while` behavior.

Final I023 closure boundary:
- the standard behavior remains one ordinary inherited `Object.while` Closure-specific selector;
- D044 validation order, strict canonical Boolean decision, pre-test ordering, ignored body result and canonical `null` completion are implemented and covered by retained Protos conformance;
- synchronous Error/non-local-return transfer, returned-Future non-adoption, D045 task-scoped ownership, condition/body suspension replay and cooperative cancellation/ensure composition all pass together;
- replay retention remains bounded across both normal and repeatedly suspending iterations;
- the Core native boundary remains 111 production `nativeClosure` construction sites across 30 providers, with `ProtosStandardObjectProtocol` at five sites and no post-I023-A expansion;
- B007 and I023 are CLOSED; DOC001-E is dependency-unblocked and re-audited READY for its own documentation slice.

### I022 — Dynamic Error handlers and unwind-safe cleanup

Status: CLOSED

Purpose: Implement the already-normative dynamic `Error.handle(body, handler)`
control substrate together with D043's standard Closure `ensure(cleanup)`
surface, including replay-stable dynamic control state, exactly-once cleanup,
handler deactivation ordering, suspension, non-local return, and cooperative
cancellation unwind. This is the general Core prerequisite required before
resource-owning or in-flight-I/O-owning LIB004 conveniences may rely on cleanup.

Normative owners:
- `spec/semantics/ERRORS.md` for `Error.handle`, matching, non-resumability, and
  selected-handler deactivation before cleanup unwind;
- `spec/semantics/EXECUTION_AND_CONTROL.md` for D043 standard `ensure`, protected
  extents, cleanup triggering, later-transfer precedence, and cancellation-safe
  suspending cleanup;
- `spec/semantics/CALLABLES.md` for the ordinary `Object.ensure` Closure-specific
  selector placement and receiver-domain behavior;
- `spec/concurrency/FUTURES_AND_TASKS.md` for structured task ownership,
  suspension/resume cancellation boundaries, and terminal Future outcomes.

Planned slices:

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| I022-A | CLOSED | `0.2.172-SNAPSHOT` | `SAME_COMMIT` | Internal lazy task-local `ProtosDynamicControlState`: replay-stable Handler/Ensure frame identity keyed by stable invocation identity, explicit semantic deactivation plus LIFO extent removal, one replaceable active unwind-transfer record, and child-task non-inheritance. No language-visible `handle`/`ensure` surface or native-Closure site is published. |
| I022-B | CLOSED | `0.2.173-SNAPSHOT` | `SAME_COMMIT` | Publish already-normative `Error.handle(body, handler)` over I022-A with Closure-only eager validation, exact/delegation matching, innermost selection, pre-unwind deactivation, exact Error identity, non-resumable transfer, task/direct-flow dynamic-state propagation, runtime-failure selection at Closure boundaries, and Protos conformance; native Error provider +1 site. |
| I022-C | CLOSED | `0.2.176-SNAPSHOT` | `SAME_COMMIT` | Publish D043 standard Closure `ensure(cleanup)` as one audited `Object` native control primitive: eager Closure-only validation before protected extent entry, exact normal-result preservation, ignored normal cleanup result, exactly-once synchronous normal/non-local-return/Error cleanup, nested LIFO ordering, later cleanup transfer precedence, and selected-handler deactivation integration. Suspension does not trigger premature cleanup; exhaustive body/cleanup replay phase conformance remains I022-D and cancellation unwind remains I022-E. |
| I022-D | CLOSED | `0.2.179-SNAPSHOT` | `SAME_COMMIT` | Suspension/replay closure: the cooperative Task producer keeps one replay-stable root Closure activation across segments, preserving lexical bindings and ReturnHome identity; an ENSURE frame records BODY/CLEANUP phase, exact pending normal/Error/return outcome and the body replay boundary; cleanup replay skips the semantically exited body while consuming its direct invocation ordinal and advancing the existing evaluator tape, preserving the same cleanup activation. Protos conformance covers root binding/ReturnHome stability, body/cleanup suspension, exact result/Error identity, selected-handler inactivity, pending non-local return, nested LIFO cleanup and repeated body+cleanup suspensions without duplicated effects. |
| I022-E | CLOSED | `0.2.191-SNAPSHOT` | `SAME_COMMIT` | E1/E2/E3 complete the cooperative cancellation unwind: request lifecycle is explicit; cancellation is an exact `ensure` outcome; the already-honored request stays shielded across cleanup suspension/replay; the producer Future remains pending while cleanup is suspended; cleanup Error/return precedence remains permanent; cleanup may create/await structured async work; and any children still owned after successful cleanup are cancelled and drained before terminal `CANCELLED` publication. |
| I022-E1 | CLOSED | `0.2.184-SNAPSHOT` | `SAME_COMMIT` | Internal-only cancellation lifecycle substrate: one idempotently recorded request has explicit NONE/REQUESTED/UNWINDING/TERMINAL phases; only REQUESTED is pending for portable observation; existing structured-child cancellation drain occupies UNWINDING and terminalizes without re-entering parent ordinary code. No `ensure`, public Future protocol, spec or native boundary change. |
| I022-E2 | CLOSED | `0.2.187-SNAPSHOT` | `SAME_COMMIT` | Cancellation is a fourth exact pending `ensure` outcome. Observation with an active ensure extent defers terminal cancellation; synchronous cleanup runs in LIFO order; normal cleanup rethrows the exact cancellation transfer and terminalizes through Task unwind completion; cleanup Error/non-local return marks the delivered request SUPERSEDED and proceeds through the ordinary later-transfer path. Protos conformance covers cleanup execution, exact cleanup Error identity, `^` precedence and nested LIFO cleanup. |
| I022-E3 | CLOSED | `0.2.191-SNAPSHOT` | `SAME_COMMIT` | Cancellation cleanup may suspend and replay under the existing D043/D machinery without re-observing the same request. An active ensure extent keeps the parent running even when pre-existing structured children are being cancelled, so cleanup runs first; the task-backed Future remains PENDING while cleanup is suspended. After successful cleanup, all children still owned at the cutover—including cleanup-created unawaited children—receive cancellation and drain before terminal `CANCELLED`; cleanup-created awaited children may complete normally during cleanup. Suspended cleanup Error still supersedes cancellation with exact Error identity. |
| I022-F | CLOSED | — | `SAME_COMMIT` | Final adversarial closure composes selected-handler deactivation with cleanup-initiated cancellation; cancellation cleanup Error with an outer handler; Error/non-local-return supersession with structured children; child-Future handler non-inheritance and consumer-context re-signaling; and Actor termination with suspending cancellation cleanup. The Core native boundary remains 109 sites / 30 providers and the complete suite is required for publication. No production or specification change and no implementation-version increment. |

Dependencies:
- I007 Core Error infrastructure — CLOSED; its historical scope intentionally did
  not fake `Error.handle` before handler-frame/unwind machinery existed;
- I009 Future / Task — CLOSED;
- D043 / specification revision `0.1.380` — normative public `ensure` ambiguity
  resolved.


Final I022 closure boundary:
- handler installation/selection, deactivation, unwind cleanup and Future
  re-signaling compose without handler state crossing Task or Actor boundaries;
- `ensure` preserves exact pending outcomes across replay, lets later cleanup
  Error/non-local-return/cancellation transfers win permanently, and runs
  cancellation cleanup through ordinary suspension without re-delivering the
  already-honored request;
- structured children remain owned across normal/error/return/cancellation
  completion, including cleanup-created work, and Actor termination cannot
  bypass cleanup by terminalizing the Actor while an unwind task remains live;
- the Core native boundary is unchanged by I022-D/E/F and is definitively guarded
  at 109 construction sites across 30 providers;
- no implementation blocker remains for the general I022 control substrate.

### I021 — Filesystem namespace replacement/removal

Status: CLOSED

Purpose: Implement D041's Filesystem replacement/removal semantics as corrected
by D042 before any package-tool repository-metadata mutation relies on them.

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I021-A | CLOSED | `0.2.159-SNAPSHOT` | `SAME_COMMIT` | Host-neutral asynchronous namespace-mutation substrate plus language-visible host-provisioned `Filesystem.replace`/`remove` dispatch. Valid Path arguments reach only the provisioned backend; unsupported backends fail `IOError`; pre-commit cancellation prevents the effect; one per-operation effect/commit cutover makes a successful atomic backend effect and Protos commitment indivisible. The existing Filesystem provider remains one native-Closure construction site through a shared operation helper. |
| I021-B | CLOSED | `0.2.160-SNAPSHOT` | `SAME_COMMIT` | Production `ProtosNioConfinedFilesystemBackend`: pinned `SecureDirectoryStream` namespace authority, independent readable/mutable direct-child allowlists, atomic relative move for replace, final-entry `deleteFile` for non-recursive remove, no final-entry preclassification/following, provider failure mapped through the I021-A `IOError` cutover, and host-boundary conformance including hard-link alias no-op and symlink non-follow behavior. Current package-tool provisioning remains read-only. |
| I021-C | CLOSED | — | `SAME_COMMIT` | Protos-source integrated conformance executes ordinary `Filesystem.replace`/`remove` through the production confined NIO backend, covering exact receiver results, fresh Future identity, authority `IOError`, same-resource no-op, final-symlink non-follow behavior, and non-recursive removal; final architecture/status reconciliation closes I021 while B006 remains READY. |

I021 remains CLOSED after I021-A/B/C. Package-tool Filesystem Slice 2B now closes
B006 by provisioning the bundled tool with explicit read, staging-write, and
namespace-mutation authority and publishing staged metadata through ordinary
File/Filesystem operations; ordinary application CLI sessions still receive no
Filesystem authority.

### I020 — Post-Ixxx implementation audit reconciliation

Status: CLOSED

Purpose: Reconcile implementation gaps and validation weaknesses found by the
post-I001..I019 implementation audit without redefining already-closed normative
semantics.

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I020-A | CLOSED | `0.2.150-SNAPSHOT` | `SAME_COMMIT` | Execute canonical `super.message(arguments...)` end-to-end when a physical `methodHome` exists: lookup starts at `parent(methodHome)`, the original dynamic receiver is preserved, the newly selected lookup home is rebound through the ordinary Closure invocation path, spreads use the ordinary ordered argument-vector machinery, nested Closures retain captured receiver/methodHome semantics, and absence after the lookup origin signals `SlotNotFound`. Adds Java and `.protos` conformance. No native-Closure boundary expansion and no normative spec change. |
| I020-B | CLOSED | — | `SAME_COMMIT` | Test-only reliability closure: both real ExecutorService scheduler tests wait for accepted turns to finish before teardown; managed carrier pools shut down gracefully and propagate any uncaught carrier-thread Throwable back through JUnit instead of permitting Maven/Surefire to report a false green. No production/runtime/specification behavior or implementation version changes. |
| I020-C | CLOSED | — | `SAME_COMMIT` | Documentation-only reconciliation: repair the literal row-separator escape introduced by I020-A; reconcile the I019 canonical one-owner source count with the executable naming architecture guard; verify and preserve the newer LIB003-A summary/detail dependency reconciliation. No production, test, library, specification or implementation-version change. |
| I020-D | CLOSED | `0.2.152-SNAPSHOT` | `SAME_COMMIT` | D040 missing-`methodHome` super semantics: source-backed standard `InvalidSuper -> Error`; caller-supplied arguments/spreads complete first exactly once left-to-right; absent `methodHome` then signals one fresh `InvalidSuper` with no lookup/fallback; present root `methodHome` and exhausted valid lookup remain `SlotNotFound`. Adds focused Java and language conformance and closes B005/I020 without a normative change. |

Final I020 closure boundary:
- valid method-bound `super` is no longer an unsupported canonical expression;
- the Actor scheduler's real-carrier tests now wait for turn completion and surface uncaught carrier failures to JUnit, eliminating the audit-observed path where an `AssertionError` could be printed from a pool thread while Surefire still reported PASS;
- project-status drift is reconciled against current executable evidence: I019 records the live canonical one-owner source count, the I020 table separator is valid Markdown again, and the independently published LIB003-A reconciliation is preserved rather than overwritten;
- dispatch reuses `ProtosValueLookup`, existing Closure binding, activation receiver and physical `methodHome`; there is no parallel method system or runtime `super` object;
- a nested Closure created during method execution continues to use its captured receiver and `methodHome`, so `super` remains tied to the physical method lookup origin while `this` stays dynamic;
- D040 missing-`methodHome` semantics are implemented: after ordinary argument-vector evaluation, absence of `methodHome` signals a fresh `InvalidSuper` before lookup; valid method-bound and root/exhausted `SlotNotFound` paths remain distinct. B005 and I020-D are CLOSED, completing I020.
- I018 remains unchanged because I020-A adds execution machinery only and no `ProtosClosureValue.nativeClosure(...)` construction site.

### I011 — Actors

Status: CLOSED

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
| I011-11 | CLOSED | `0.2.107-SNAPSHOT` | `SAME_COMMIT` | Actor-boundary `Map`/`IdentityMap` keyed-state transfer with alias/cycle and mutation-state preservation; destination hash/identity-hash bookkeeping rebuilt without executing user comparison/hash code; default Object hash uses semantic identity so rematerialized ActorRef capabilities remain valid keyed identities. |
| I011-12 | CLOSED | `0.2.108-SNAPSHOT` | `SAME_COMMIT` | GroupRef capability-identity and Actor-transfer foundation: semantic GroupRef identity is distinct from Group identity and physical wrappers; repeated transfer/rematerialization preserves identityHash, target Group identity, and effective restriction descriptor; independent GroupRef acquisitions remain distinct. No public Group routing/acquisition surface is introduced. |
| I011-13 | CLOSED | `0.2.112-SNAPSHOT` | `SAME_COMMIT` | Internal Process failure-domain / failure-authority substrate: one RootActor plus hosted Actor set; non-root fatal failure terminates only that incarnation; RootActor fatal failure terminates the Process and all hosted Actors; Process termination waits for Actor-required cancellation unwind; process-bound `Actor.spawn` retains local Process hosting. No public Process capability or launcher provisioning yet. |
| I011-14 | CLOSED | `0.2.114-SNAPSHOT` | `SAME_COMMIT` | Process capability Actor-delegation foundation: runtime-provisioned represented proxies carry authority into one existing logical Process; Actor transfer rematerializes a fresh wrapper to that same authority, preserves aliases within one graph transfer, and rebuilds authority-bearing descendants over the destination proxy. Process has no P-transfer contract. No public Process prototype/bootstrap slot or I/O surface is introduced. |
| I011-15 | CLOSED | `0.2.116-SNAPSHOT` | `SAME_COMMIT` | Internal ActorGroup membership/routing foundation: stable Group identity independent of membership; live Groups may have zero members; explicit membership changes do not change Group identity; only READY members are routing-eligible; runtime GroupRef acquisition binds to that concrete Group while preserving independent GroupRef identity and transfer rematerialization. No public Group acquisition/send/request/controller/Authority surface is introduced. |
| I011-16 | CLOSED | `0.2.121-SNAPSHOT` | `SAME_COMMIT` | Local ActorGroup communication-operation foundation: pending Group routing survives zero eligible membership; INITIALIZING->READY wakes routing; selected-member removal/pre-acceptance loss may requeue only before concrete acceptance; Group termination fails only still-pre-acceptance work; accepted work is never rerouted; Group request accepted-loss maps to RequestOutcomeUncertain; Group send reuses standard SendOperation cancel/retry control; all SendOperation implementations remain Actor/P non-transferable. No public Group acquisition/GroupRef selector installation or remote transport is introduced. |
| I011-17 | CLOSED | `0.2.124-SNAPSHOT` | `SAME_COMMIT` | Language-visible GroupRef communication surface: hidden source-backed GroupRef prototype with exactly `send`/`request`; shared audited communication-Closure construction helpers dispatch by receiver without adding native construction sites; Group calls reuse I011-16 local routing/backpressure/rerouting, concrete acceptance authority, SendOperation cancel/retry, request reply transfer and accepted-loss uncertainty. No public Group acquisition/discovery, controller/Authority, broadcast, or genuinely remote transport is introduced. |
| I011-18 | CLOSED | `0.2.126-SNAPSHOT` | `SAME_COMMIT` | Final local/cross-Process ActorGroup race/conformance slice: GroupRef continuity across member replacement; Process-loss rerouting only before concrete acceptance; accepted Group request loss maps to RequestOutcomeUncertain with no fallback replay; READY/cancel races admit exactly one legal outcome without duplicate execution. B004 records the still-undefined public Group/GroupRef acquisition/discovery API. |
| I011-19 | CLOSED | `0.2.129-SNAPSHOT` | `SAME_COMMIT` | Host-neutral remote ActorRef communication-route foundation: preserved ActorRef identity; transport-owned acceptance knowledge; send cancellation/retry over known failure or uncertainty; request uncertainty (including cancellation ambiguity) -> RequestOutcomeUncertain; normal replies re-snapshot at the caller boundary. No public transport/discovery API or physical transport policy is added. |
| I011-20 | CLOSED | `0.2.133-SNAPSHOT` | `SAME_COMMIT` | Remote ActorGroup routing over the I011-19 Actor transport boundary: runtime-only remote ActorRef members participate in ordinary Group selection; known pre-acceptance transport failure may reroute to another eligible member; accepted/acceptance-uncertain transport outcomes never transparently reroute; Group send uncertainty is explicitly retryable and Group request uncertainty maps to RequestOutcomeUncertain. No public Group membership/discovery/transport API is introduced. |
| I011-21 | CLOSED | `0.2.137-SNAPSHOT` | `SAME_COMMIT` | D039 public ActorGroup acquisition: frozen `Actor.group(firstMember, additionalMembers...) -> GroupRef` validates the complete ActorRef vector before cutover, creates fresh Group/GroupRef identities with deduplicated initial membership, binds Group lifetime to the caller Actor's Process, preserves READY-gated/remote-capable routing, and terminates owned Groups at Process termination without stopping members. B004 closes; no public Group/controller/discovery/placement/endpoint API is added. |

Top-level closure reconciliation:
- B004 is CLOSED: D039's exact Core v0.1 `Actor.group(firstMember, additionalMembers...) -> GroupRef` acquisition surface and caller-Process-owned Group lifetime are implemented and validated; service discovery and post-creation Group control remain explicitly outside Core v0.1 rather than implementation gaps.
- I017 Process/bootstrap integration is CLOSED, so every Core Actor executes inside the Process ownership model required by D039.
- I011-1 through I011-21 now cover Actor identity/lifecycle/bootstrap, transfer, bounded messaging, Future integration, graceful termination, GroupRef identity/routing, local/cross-Process/remote acceptance uncertainty, and the final public Group acquisition surface. No remaining Core v0.1 Actor implementation gap is recorded.


### I015 — Encoding / Text I/O

Status: CLOSED

Normative owners:
- `spec/io/TEXT_IO.md`
- `spec/io/IO_CORE.md`
- `spec/io/BYTE_IO.md` for underlying byte capability/Future/lifecycle semantics
- `spec/semantics/OBJECT_MODEL.md` for Encoding semantic-family receiver domains

Implementation plan:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I015-A | CLOSED | `0.2.122-SNAPSHOT` | `SAME_COMMIT` | Source-backed standard Encoding factory/prototype identity; four mandatory portable immutable descriptors; exact semantic-family receiver checks; strict synchronous one-shot encode/decode; fresh Bytes results; UTF validity and initial matching-BOM consumption; ISO-8859-1 Latin1; explicit host Encoding-provisioning boundary; authority-free Actor/P transfer; reviewed two-site I018 representation bridge. |
| I015-B | CLOSED | `0.2.140-SNAPSHOT` | `SAME_COMMIT` | Source-backed frozen `TextReader` factory; borrowing/owning validation; fresh wrappers with exactly `readText`/`close`; transactional strict incremental UTF8/UTF16LE/UTF16BE/Latin1 decoding; initial matching BOM; progress before extra read-ahead; valid-prefix/deferred-error ordering; permanent failure lifecycle; cancellation zero logical consumption; borrowing/owning close integration; two-site I018 resource/capability bridge. Host-provided incremental Encoding integration remains I015-E. |
| I015-C | CLOSED | `0.2.142-SNAPSHOT` | `SAME_COMMIT` | `readLine()` / `readLine(maxBytes)` share I015-B's single ordered decoder/input domain; deterministic LF/CR/CRLF framing; CR immediate completion with deferred LF folding; EOF-final line/null semantics; exact pre-terminator encoded-octet budget excluding initial BOM/terminator; source-order EncodingError vs LineTooLong precedence; permanent failure and zero-consumption cancellation; no I018 construction-site expansion. |
| I015-D | CLOSED | `0.2.144-SNAPSHOT` | `SAME_COMMIT` | Source-backed frozen `TextWriter` factory; borrowing/owning capability validation; fresh wrappers with exactly `writeText`/`writeLine`/`flush`/`close`; complete portable encoding validation before output; canonical LF line output; empty-write zero-transition/no-target-I/O semantics; ordered commitment and permanent downstream-failure frontier; compositional flush; close cutover and explicit ownership/primary-failure precedence; two-site I018 bridge. |
| I015-E | CLOSED | `0.2.147-SNAPSHOT` | `SAME_COMMIT` | Final cross-slice closure: explicit host Encoding boundary upgraded to fresh transactional streaming decoder/encoder state; all Encoding descriptors accepted by TextReader/TextWriter; strict default plus explicit deterministic replacement and initial-BOM policy; Unicode maximal-subpart portable replacement; stateful host writer close finalization; A/B/C/D regression and Actor/P descriptor invariants; post-I015 I018 boundary unchanged. |

Dependency chain: `I015-A -> I015-B -> I015-C -> I015-D -> I015-E`.

Coordination with I017:
- I017 is CLOSED. Its D2 standard-stream Encoding dependency was satisfied by I015-A and does not constrain the remaining I015 text-wrapper slices.
- TextReader/TextWriter remain explicit layering facilities; Process standard byte streams are not implicitly wrapped or converted by I015-B/C/D.
Final implementation boundary after I015-E / I015 closure:
- `Encoding` is a source-backed frozen authority-free factory/prototype with exactly four mandatory public portable descriptors; descriptors are immutable semantic-family values and may also be explicitly host-provisioned without registry/discovery authority;
- each descriptor supplies fresh per-flow transactional decoder/encoder state, so descriptor reuse shares configuration only and never mutable codec state; the trusted host boundary must expose deterministic semantic decoded units/source-byte extents and reversible next-state checkpoints rather than leaking converter-call chunking;
- strict/fatal decoding remains the default. Explicit replacement configuration consumes malformed input and emits U+FFFD; portable UTF replacement uses Unicode maximal subparts independent of source chunking, Latin1 has no malformed octets, and host replacement grouping is part of the host descriptor contract;
- matching initial portable UTF BOM is consumed by default; an explicitly BOM-preserving configured descriptor exposes U+FEFF as ordinary text. Preserved BOM source bytes participate in bounded-line accounting, while consumed initial setup remains excluded;
- `TextReader` accepts every semantic Encoding descriptor and has one ordered transactional readText/readLine decoder/input domain with progress, exact encoded-octet line limits, deterministic LF/CR/CRLF, zero-consumption cancellation, permanent text EOF and permanent committed decoding/I/O/LineTooLong failure;
- `TextWriter` accepts every semantic Encoding descriptor and has one ordered transactional encoder/output domain; complete encoding validation precedes target contribution, empty text performs no encoder state transition, writeLine appends LF atomically, downstream uncertainty/failure permanently blocks later output, flush composes through Flushable, and close finalizes committed encoder state before optional owned-target release;
- one-shot Encoding encode/decode uses a fresh codec flow and therefore shares the same configured strict/replacement/BOM semantics without sharing mutable state; Actor/P transfer continues to share immutable descriptors only;
- I015-E adds no Protos selector and no Java `nativeClosure` construction site. The post-I015 I018 executable inventory remains the definitive baseline recorded in `CORE_NATIVE_BOUNDARY.md`.
### I016 — Filesystem / File

Status: CLOSED

Coordination result: I016-D resumed only after a post-I018 current-main audit. Because I018-L guards every Java native-Closure provider, each I016-D substep that changes that boundary must update the inventory and executable guard in the same published change.

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
| I016-D1 | CLOSED | `0.2.109-SNAPSHOT` | `SAME_COMMIT` | Host-provisioned open-only Filesystem capability; `open` integration with I016-A and standard File materialization from B/C; exact ordinary options domain; authority/confinement/stable-resource backend contract; minimal mandatory I018 native-boundary registration. |
| I016-D2 | CLOSED | `0.2.110-SNAPSHOT` | `SAME_COMMIT` | Explicit runtime File/Filesystem authority markers; actual standard protocol materialization uses those markers; direct live authority and authority-bearing ordinary descendants fail Actor transfer with `NonTransferableValue` and P transfer with `NonParallelValue`; no native-Closure boundary expansion. |
| I016-D3 | CLOSED | `0.2.111-SNAPSHOT` | `SAME_COMMIT` | Integrated authority/open conformance covers pre/post-commit cancellation, late custody cleanup, stable selected-resource binding, independent out-of-order opens, backend confinement-policy rejection, and descriptor-owned optional capability shape; post-D2 I018 boundary re-audit confirms 23 providers / 91 sites and no Filesystem prelude binding. |
| I016-D4 | CLOSED | `0.2.113-SNAPSHOT` | `SAME_COMMIT` | Final cross-slice focal validation plus full-suite publication; canonical I016 closure; post-D3 I018 boundary remains 23 providers / 91 sites; fresh I017 dependency audit found no relevant unresolved implementation blocker and released I017 to READY. |

Current I016-D completion plan:
- **D1 — Filesystem open integration (CLOSED by this slice):** expose only a host-provisioned `open` capability, connect A to B/C, enforce exact ordinary options, and register the unavoidable resource bridge with the I018 guard.
- **D2 — authority/transfer boundaries (CLOSED):** explicit File/Filesystem runtime authority markers now back the real standard capabilities; Actor/P reject direct and delegation-carried live authority without broadening the standard message surface.
- **D3 — deterministic authority/race conformance + I018 re-audit (CLOSED):** integrated tests now cover cancellation commitment, stable selected-resource custody, independent opens, backend authority rejection, and descriptor-owned capability shape; the post-D2 native boundary remains exactly 23 providers / 91 sites.
- **D4 — final I016 closure (CLOSED):** final cross-slice focal validation and the complete Maven suite passed on the definitive publication baseline; I016 is CLOSED and I017 is READY after a fresh dependency audit.

Dependency chain: `I016-D1 -> I016-D2 -> I016-D3 -> I016-D4`.

Closure result:
- I016-A closes exact `Filesystem.open` option capture, validation precedence, independent acquisition, cancellation, commitment and result-custody semantics;
- I016-B closes positioned File cursor, read/write, seek/size/truncate/sync capability shape, lifecycle, close cutover and Actor-originated cancellation;
- I016-C closes append placement at then-current EOF, alias-wide no-overlap/no-interleaving, empty-append and failed-prefix aftermath;
- I016-D1 exposes only host-provisioned `Filesystem.open`, binds successful opens to stable selected standard File resources, and records the authority/confinement backend contract without creating a Core-global Filesystem;
- I016-D2 makes File/Filesystem live authority explicit and rejects direct or delegation-carried authority at Actor/P copy boundaries without implicit proxy/reopen behavior;
- I016-D3 provides deterministic integrated authority/open conformance and re-audits I018 at exactly 23 native providers / 91 construction sites;
- I016-D4 validates all I016 slices together plus the full suite on the definitive baseline, closes I016, and releases I017 to READY. The current baseline already includes I011-13's internal Process failure-domain / RootActor substrate; I017 still requires its own mandatory current-main audit before implementation and coordination with whatever additional I011 work is then current.


### I017 — Process I/O / bootstrap

Status: CLOSED

Audit/reconciliation result:
- I016 is CLOSED and no relevant implementation blocker is currently BLOCKED;
- I011-13 already owns the internal Process failure domain, unique RootActor and hosted-Actor termination authority;
- I011-14 landed concurrently before I017-A and already implements the exact Process capability Actor-delegation/P-exclusion foundation originally planned for I017-A, so I017 reuses that published substrate instead of creating a competing representation;
- I015 Encoding / Text I/O is not yet guaranteed CLOSED on every I017 baseline, so the standard `stdinEncoding()`, `stdoutEncoding()`, and `stderrEncoding()` integration has an explicit external dependency on I015.

Normative owners:
- `spec/io/PROCESS_IO.md`
- `spec/io/IO_CORE.md`
- `spec/io/BYTE_IO.md` for standard-stream byte protocols
- `spec/io/TEXT_IO.md` for Encoding values returned by the `*Encoding()` accessors
- `spec/concurrency/ACTORS.md` for explicit Process delegation during Actor transfer/spawn
- `spec/concurrency/PARALLEL_EXECUTION.md` for the absence of a Process P-transfer contract
- `spec/semantics/MODULES.md` for RootActor initial-module `moduleContext` provisioning

Published / planned slices:

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| I017-A | CLOSED | `0.2.115-SNAPSHOT` | `SAME_COMMIT + I011-14_REUSED` | Reconciled/validated Process capability foundation already published by I011-14: runtime-provisioned Actor-local proxies; explicit fresh Actor rematerialization to the same logical Process authority; graph alias preservation; authority-bearing descendants rebuilt over the destination proxy; Process excluded from P; provisioning rejected after Process termination begins. No duplicate Process representation or public accessor surface introduced. |
| I017-B | CLOSED | `0.2.117-SNAPSHOT` | `SAME_COMMIT` | Canonical immutable Process-argument snapshot: one-time complete host capture with stable representability outcome; exact `size`/zero-based `at`/polymorphic ordered `each`; same canonical identity on repeated Process acquisition; fresh destination identity with alias preservation for ordinary Actor/P transfer; reviewed I018 representation bridge at 3 native sites. |
| I017-C | CLOSED | `0.2.118-SNAPSHOT` | `SAME_COMMIT` | Canonical read-only Environment snapshot with stable Process acquisition outcome/identity; duplicate-native-name rejection; exact query representability and native identity; selective value decoding for get/contains; polymorphic each with whole-snapshot String prevalidation and canonical Unicode-scalar ordering; ordinary Actor/P value-copy identity; reviewed 3-site I018 representation bridge. |
| I017-D1 | CLOSED | `0.2.119-SNAPSHOT` | `SAME_COMMIT` | Stable independently optional stdin/stdout/stderr bindings; repeated/Actor-delegated views share one logical per-binding queue; exact read-only/write-only surface with no implicit lifecycle/File/text authority; Actor-local Futures use I014 cancellation/commitment machinery; termination blocks new work; P rejects live stream authority; reviewed 2-site I018 resource bridge. |
| I017-D2 | CLOSED | `0.2.123-SNAPSHOT` | `SAME_COMMIT` | Stable exactly-once stdin/stdout/stderr Encoding association states coupled to D1 stream availability; portable or host-provided immutable Encoding accepted; unavailable is distinct from invalid availability/Encoding mismatch; no hidden host lookup/default and no native-boundary expansion. |
| I017-E | CLOSED | `0.2.130-SNAPSHOT` | `SAME_COMMIT` | E1 public Process accessors, E2 RootActor bootstrap-local authority provisioning/import confinement, and E3 standalone host/CLI bootstrap capture/wiring are all published and integrated. |
| I017-E1 | CLOSED | `0.2.127-SNAPSHOT` | `SAME_COMMIT` | Source-backed frozen authority-free `Process` prototype; exactly eight synchronous Process accessors over A/B/C/D1/D2 state; exact represented-capability receiver domain; canonical snapshot acquisition; stable stream/Encoding lookup failure; all proxies rejected after Process termination; one audited native Closure construction helper. |
| I017-E2 | CLOSED | `0.2.128-SNAPSHOT` | `SAME_COMMIT` | RootActor canonical initial-module context receives bootstrap-local `process` before first source expression and optional exact host-granted `filesystem`; cache-before-execute/cycles preserved; ordinary imports and non-root Actor bootstrap receive no ambient Process/Filesystem authority; explicit Process Actor delegation remains the only transfer path; no I018 boundary expansion. |
| I017-E3 | CLOSED | `0.2.130-SNAPSHOT` | `SAME_COMMIT` | Standalone host/CLI captures trailing application args excluding launcher identity, one native Environment snapshot/domain, stdin/stdout/stderr byte bindings and explicit UTF-8 host associations before first source expression; non-importable entries use the E2 RootActor authority model; optional Filesystem grants are accepted explicitly but the standard CLI grants none rather than converting launcher source-read authority into ambient application authority; exact hidden ActorRef/Bytes Core identities retained; no I018 expansion. |
| I017-F | CLOSED | `0.2.132-SNAPSHOT` | `SAME_COMMIT` | Final cross-slice Process authority/identity/Actor/P/termination conformance; E1/E2/E3 and CLI regression suite revalidated; post-I017 I018 guard/inventory re-audited unchanged at 28 providers / 102 sites; canonical I017 closure published. |

Dependency chain: `I017-A -> I017-B -> I017-C -> I017-D1 -> I017-D2 -> I017-E1 -> I017-E2 -> I017-E3 -> I017-F`. I017-E is the coordinating parent for E1/E2/E3. The Encoding-family dependency of D2 was satisfied by published I015-A; remaining I015 TextReader/TextWriter work is independent.

Final implementation boundary after I017-F / I017 closure:
- `Process` is a frozen source-backed authority-free Core prototype with exactly eight synchronous runtime-backed accessors and no constructor, Filesystem recovery path, or ambient authority;
- every logical Process has one RootActor and stable bootstrap args/environment/standard-stream/Encoding state; canonical args/environment acquisition is shared across Actor-local Process proxy wrappers while ordinary transfer of an already-acquired immutable snapshot is an independent destination value;
- RootActor initial entry contexts receive local `process` before first source execution and local `filesystem` only when the embedding host explicitly grants one; imported modules and non-root Actors receive neither ambient capability;
- Process capability Actor transfer rematerializes a fresh proxy to the same logical Process without amplification; Process and live Process standard-stream authority have no P-transfer contract, and Filesystem transfer remains governed independently by I016;
- stdin/stdout/stderr remain independently optional byte capabilities with one Process-local ordering domain per binding and bootstrap-stable host-selected Encoding associations; no Closable/File/text/seek/flush authority is inferred;
- the standard CLI captures trailing application args, one host Environment snapshot, supplied stdin/stdout/stderr byte streams and explicit UTF-8 associations before first source expression; launcher source-read authority is not converted into application Filesystem authority;
- once Process termination commits, no existing Process proxy may acquire any bootstrap capability/value and no new Process proxy may be provisioned; I014/I017 stream lifecycle rules govern pending/new byte operations without synthesizing close/flush/sync semantics;
- I017-F adds conformance and project-state reconciliation only. The post-I017 I018 boundary remains exactly 28 providers / 102 native-Closure construction sites.
### I018 — Core self-hosting / bootstrap minimization

Status: CLOSED

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
| I018-L | CLOSED | `0.2.106-SNAPSHOT` | `SAME_COMMIT` | Final I018 closure: exhaustive inventory of all remaining Java native-Closure providers classifies every retained boundary as host-irreducible, representation-backed, concurrency/runtime-backed, or resource/capability-backed; no source-expressible standard slot remains. A regression guard fixes the exact 22-provider/90-construction-site native boundary, verifies helper-backed runtime selector surfaces and migrated source provenance, and prevents silent Java-only Core growth. I018 is CLOSED and the I016-D coordination pause is lifted. |\n
Closure result:
- exhaustive audit found no remaining source-expressible standard slot implemented only in Java;
- every retained Java-native standard Closure is recorded in `docs/project/CORE_NATIVE_BOUNDARY.md` as an irreducible host, representation, concurrency/runtime, or resource/capability bridge;
- `ProtosCoreNativeBoundaryArchitectureTest` guards the exact provider/construction-site boundary, helper-backed standard selector surfaces, source-backed provenance, and Core-bootstrap allocation boundary;
- I018 is complete. The temporary coordination pause after published I016-C is lifted; I016 may resume from I016-D after re-auditing the then-current `origin/main`.


## CLI implementation

| Item | Description | Status | Version | Closure evidence | Notes |
|---|---|---|---|---|---|
| CLI001 | Basic CLI + persistent REPL | CLOSED | — | historical; not backfilled | — |
| CLI002 | Interactive terminal UX | CLOSED | — | historical; not backfilled | — |
| CLI003 | Multiline REPL input | CLOSED | `0.2.80-SNAPSHOT` | `254c80c0fb9e70f1dd07ef711f06ce71faa93829` | published multiline REPL input; parser-EOF accumulation, one-unit bracketed paste, persistent top-level context, recovery/history/stream coverage; recursive factorial regression is covered after standard numeric ordering completion |
| CLI004 | Standard-library module resolution | CLOSED | `0.2.120-SNAPSHOT` | `SAME_COMMIT` | official CLI host resolver for reserved `std:<logical-name>` distribution modules; logical relocation-independent ModuleKey identity; no search-path shadowing/fallback; bootstrap `core/` excluded; no normative Core module change |
| CLI005 | Portable Standard Library naming | CLOSED | `0.2.131-SNAPSHOT` | `SAME_COMMIT` | case-significant ASCII `std:` identities; exact distributed path spelling independent of host filesystem case rules; case-fold ambiguity, bootstrap `core`, and Windows reserved device-name segments rejected; canonical LIB001 `Set`/`IdentitySet` spellings |
| CLI006 | Standalone CLI print facility | CLOSED | `0.2.154-SNAPSHOT` | `SAME_COMMIT` | ordinary initial-context print Closure; borrowing TextWriter over Process stdout/Encoding; file/-e explicit-output-only policy; REPL result display retained; bundled tools remain unmodified; Core native boundary unchanged |
| CLI007 | Standalone RootActor task entry execution and print-learning-material validation | CLOSED | `0.2.157-SNAPSHOT` | `SAME_COMMIT` | non-interactive file/-e and bundled-tool entries run as real RootActor ProtosTask work; pending Future.value may suspend/resume; all shipped print-dependent examples/tutorials execute unchanged through standalone CLI; REPL task model unchanged; no spec/native-boundary change |


## Standard Library

The `LIBxxx` family records distributable standard-library functionality
implemented primarily as ordinary Protos modules outside `protos/lib/core/`.
Library work builds on existing language/Core semantics; it does not define new
normative language behavior. If a library requires a missing Core/runtime
semantic prerequisite, that prerequisite must be resolved and tracked through
the applicable specification/design and implementation work before the library
relies on it.

Core implementation source under `protos/lib/core/` remains owned by the
applicable `Ixxx` work. Its location in `protos/lib/` does not by itself make it
Standard Library work.

LIB identifiers are stable work-item identities, not a mandatory execution
order. Later library work uses the next unused `LIBxxx` identifier; do not
renumber existing items merely to express priority. Readiness and actual
implementation order follow real dependencies, so independently ready library
work may proceed without waiting for an earlier-numbered roadmap item.

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| LIB001 | Collections library | CLOSED | `SAME_COMMIT` | LIB001-A/B/C/D/E closed; initial Set/IdentitySet and eager sequential Array algorithm surfaces are fully published with no new runtime collection family, generic hierarchy, or production Java boundary. |
| LIB002 | Text / encoding conveniences | CLOSED | `SAME_COMMIT` | LIB002-A published the four audited ordinary portable-Encoding convenience modules with real-`std:` Protos conformance; initial LIB002 scope is closed with no Core, native-boundary, registry/default, or distributed-runtime semantic change. |
| LIB003 | JSON | CLOSED | `SAME_COMMIT` | LIB003-A/B/C/D/E closed; the bounded initial strict JSON tree, exact-decimal parser/encoder, JSON-specific event streaming, explicit TextReader/TextWriter composition, final stress/Actor-transfer evidence, and architecture audit are fully published without a generic serialization or object-persistence boundary. |
| LIB004 | Filesystem / process conveniences | IN_PROGRESS | — | LIB004-0 design CLOSED; LIB004-A implementation IN_PROGRESS through subdivided A1/A2/A3, with A1 published in ordinary `std:io/Files`; LIB004-D remains independently READY. |
| LIB005 | Networking | OPEN | — | Roadmap item only; `spec/io/IO_CORE.md` currently leaves network authority acquisition, socket APIs, DNS/name resolution, and transport configuration outside its standardized scope. Re-audit and establish prerequisites before implementation. |
| LIB006 | Deterministic hashing | CLOSED | `SAME_COMMIT` | LIB006-A design + LIB006-B pure-Protos SHA-256 implementation complete the bounded initial one-shot hashing surface. |
| LIB006-A | SHA-256 API/security/boundary design | CLOSED | `SAME_COMMIT` | Freeze one-shot Bytes->fresh 32-byte SHA-256, pure-Protos initial implementation, no entropy/keyed crypto/native bridge, and conformance boundary. |
| LIB006-B | pure-Protos SHA-256 implementation | CLOSED | `SAME_COMMIT` | `std:crypto/SHA256.digest(Bytes)` implements standard SHA-256 in ordinary Protos with fixed known-answer conformance and no native/host crypto boundary. |


### LIB001 — Collections

Status: CLOSED

Description: General-purpose collection data structures and algorithms built as
ordinary Protos library modules on top of the existing Core collection and
module facilities.

Design record:
- `docs/project/LIB001_COLLECTIONS_DESIGN.md` records the completed comparative
  architecture audit and the focused Set/IdentitySet API audit;
- the record is non-normative: it constrains Standard Library implementation but
  does not redefine Core semantics; CLI004 introduced the `std:` host/distribution boundary and CLI005 closes
  its portable case-significant naming policy; neither changes Core module
  semantics;
- the focused audit closes the initial Set/IdentitySet representation and public
  contract, including the correction from "arbitrary Map role" to a well-formed
  ordinary Map/IdentityMap `key -> true` library invariant.

Implementation boundary:
- distributable library source belongs under `protos/lib/collections/`;
- existing Core `Array`, `Map`, and `IdentityMap` semantics and prototypes remain
  Core and are not reclassified as library work;
- Set/IdentitySet add no runtime value family, tag, wrapper, privileged transfer
  identity, generic Collection hierarchy, or native boundary;
- portable modules are `std:collections/Set` and
  `std:collections/IdentitySet`;
- Set stores every introduced member as an ordinary Map key mapped to canonical
  `true`; IdentitySet does the same over IdentityMap;
- module invocation is the constructor (`sets(...)` / `identitySets(...)`), and
  the initial closed Set surface is `contains`, `add`, `remove`, `size`, `each`,
  `union`, `intersection`, `difference`, `sameMembers`, `isSubset`,
  `isSuperset`, and `isDisjoint`;
- `add`/`remove` return the exact Set argument after success; removal absence
  follows the underlying Map Error; `each` invokes callbacks with exactly one
  member and uses the underlying keyed snapshot/order;
- Set algebra returns fresh open Sets with deterministic left-derived order as
  recorded in the design document; ordinary Map `==`/`hash` remain unchanged;
- `std:collections/Array` supplies the complete initial eager sequential
  `map`, `filter`, `findIndex`, `reduce`, and stable `sort` surface over
  pre-callback shallow snapshots; callbacks remain ordinary module behavior,
  strict predicate/comparator result laws are explicit, and no parallel/P
  machinery is inherited by sequential operations.

Planned slices:

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| LIB001-A | CLOSED | `0.2.125-SNAPSHOT` | `SAME_COMMIT` | Set/IdentitySet ordinary modules: `key -> true` representation, variadic module-call construction, `contains`, `size`, focal real-`std:` conformance. |
| LIB001-B | CLOSED | `0.2.138-SNAPSHOT` | `SAME_COMMIT` | `Set`/`IdentitySet` `add` and `remove` delegate exactly once to the underlying keyed mutation and return the exact receiver; one-argument `each` adapts the existing insertion-order shallow Map/IdentityMap snapshot; open/closed/frozen, absent-remove, callback-arity/invocation, order and mutation-during-iteration conformance published. |
| LIB001-C | CLOSED | `0.2.139-SNAPSHOT` | `SAME_COMMIT` | Fresh `union`/`intersection`/`difference` and explicit membership predicates published for Set/IdentitySet with deterministic left-derived order, short-circuit traversal, Map-vs-membership equality boundary, fresh/open result conformance, and Actor-local behavior versus transferable Map-backed data conformance. |
| LIB001-D | CLOSED | `0.2.141-SNAPSHOT` | `SAME_COMMIT` | `std:collections/Array` publishes eager sequential `map`, `filter`, and `findIndex` over a pre-callback shallow Array snapshot; one-argument ordinary callbacks, strict canonical-Boolean predicates, fresh open Array results, `findIndex` first-match/`null` absence, empty-input callback non-inspection, mutation snapshot and failure conformance published. |
| LIB001-E | CLOSED | `0.2.143-SNAPSHOT` | `SAME_COMMIT` | `std:collections/Array` adds strict left-fold `reduce` with zero-or-one optional initial value and stable fresh-result `sort` with the canonical sequential merge tree, strict Boolean two-direction comparator law, `InvalidComparatorResult`/`InvalidComparatorOrder`, snapshot/effect/failure conformance, and final cross-slice LIB001 validation. |

Dependencies:
- I004 Array completion — CLOSED;
- I005 Standard Map — CLOSED;
- I006 IdentityMap / identity hashing — CLOSED;
- I008 Modules — CLOSED;
- CLI004 Standard-library module resolution — CLOSED;
- CLI005 Portable Standard Library naming — CLOSED.

### LIB002 — Text / encoding conveniences

Status: CLOSED

Description: Ergonomic text and encoding helpers implemented as ordinary Protos
library functionality on top of finalized Core Encoding/Text I/O semantics.

Design record:
- `docs/project/LIB002_TEXT_ENCODING_DESIGN.md` records the completed focused
  normative, comparative, falsification, and future-proofing audit;
- the record compares Pharo/Smalltalk, Self, Io, Erlang/Elixir, C#/.NET, Java,
  C++, Python, Ruby, Rust, and Swift against the Protos object, module, I/O,
  Actor, Process, and distributed-runtime model;
- the record is non-normative and does not redefine Core semantics.

Implementation boundary:
- Core Encoding, TextReader, and TextWriter semantics remain owned by I015 and
  the normative I/O specification;
- the closed initial convenience surface is four ordinary Actor-local modules:
  `std:text/UTF8`, `std:text/UTF16LE`, `std:text/UTF16BE`, and
  `std:text/Latin1`;
- each module supplies exactly `encode`, `decode`, `reader`, `owningReader`,
  `writer`, and `owningWriter` by delegating to its corresponding mandatory
  Core Encoding descriptor and TextReader/TextWriter factory;
- helper modules are not Encoding semantic values and acquire no Encoding
  identity, per-flow codec state, I/O authority, registry role, or ambient
  default behavior;
- host-provided Encoding values remain first-class Core inputs and require no
  Standard Library registration or wrapper;
- no production Java/native boundary, shared mutable codec state, global/default
  Encoding, automatic encoding detection, or second I/O ordering domain is
  introduced;
- `readAll`/`readLines`/`writeAll`, Unicode transformations, normalization,
  locale/collation, encoding discovery/registries, and reciprocal Core
  String/Bytes augmentation remain outside this closed initial LIB002 scope and
  require separate focused design if pursued.

Planned slices:

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| LIB002-A | CLOSED | `0.2.166-SNAPSHOT` | `SAME_COMMIT` | Published `std:text/UTF8`, `UTF16LE`, `UTF16BE`, and `Latin1` as ordinary Protos modules with exact Core-preserving one-shot conversion plus borrowing/owning TextReader/TextWriter construction; real-`std:` Protos conformance covers conversion, freshness/open Bytes, strict failures, wrapper construction/capability validation, import identity, and the non-Encoding helper boundary; no production Java/native-boundary growth. |

Dependencies:
- I003 Standard String — CLOSED;
- I012 Standard Bytes — CLOSED;
- I015 Encoding / Text I/O — CLOSED.

### LIB003 — JSON

Status: CLOSED

Description: Strict JSON structured-data facilities implemented through an
explicit JSON-specific data model made from ordinary Protos values and modules.

Design record:
- `docs/project/LIB003_JSON_DESIGN.md` records the completed comparative
  JSON/YAML/XML/object-persistence audit and initial JSON data-model/codec
  decisions;
- `docs/design/STRUCTURED_DATA_AND_SERIALIZATION.md` remains the broader
  cross-format architecture record;
- neither document redefines Core semantics.

Implementation boundary:
- canonical module identity is `std:json/JSON`, physically
  `protos/lib/json/JSON.protos`;
- JSON data is explicit ordinary fresh/open data, not arbitrary application objects;
- no `typeOf`, runtime JSON family/tag, implicit conversion, reflection-based
  serializer, `toJSON` hook or generic Serializer hierarchy is introduced;
- JSON Number is exact decimal data represented by unbounded Integer
  coefficient/exponent with mathematical value `coefficient * 10^exponent`;
- JSON objects use fresh open ordinary Map storage with semantic String names,
  duplicate-name rejection and deterministic retained insertion order;
- JSON arrays use the fresh frozen standard Array produced by trailing-rest capture;
- the JSON module contains behavior and remains Actor-local while pure
  constructor-created data remains eligible for ordinary Actor transfer;
- YAML, XML, canonical/lossless JSON and object-graph persistence remain separate
  future concerns.

Planned slices:

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| LIB003-A | CLOSED | `0.2.149-SNAPSHOT` | `SAME_COMMIT` | Exact-case `std:json/JSON`; six fresh ordinary JSON node constructors; exact decimal coefficient/exponent Number data; standard frozen rest-capture Array payloads; duplicate object-name rejection; module-local behavior vs transferable pure-data conformance; focused design record persisted. |
| LIB003-B | CLOSED | `0.2.151-SNAPSHOT` | `SAME_COMMIT` | Strict RFC-8259-oriented semantic-String parser; UTF-8 octet scanner; explicit non-recursive JSON container stack; strict escapes/surrogate pairs; decoded duplicate-name rejection; exact coefficient/exponent decimal parsing; balanced Array chunk materialization; top-level scalar support. |
| LIB003-C | CLOSED | `0.2.163-SNAPSHOT` | `SAME_COMMIT` | Ordinary `JSON.encode(node)` in Protos source: complete representation validation, exact coefficient/exponent decimal emission without Float formatting, JSON String escaping, retained Map traversal order, active-path IdentityMap cycle rejection, and shared non-cyclic subtree support. |
| LIB003-D | CLOSED | `0.2.167-SNAPSHOT` | `SAME_COMMIT` | D1 incremental parser events, D2 deterministic event writing, and D3 explicit TextReader/TextWriter Future-shaped composition published; no generic Serializer hierarchy or hidden I/O ownership. |
| LIB003-D1 | CLOSED | `0.2.164-SNAPSHOT` | `SAME_COMMIT` | Fresh ordinary `JSON.eventParser(consumer)` with synchronous `feed(String)` / `finish()`; JSON-specific structural/scalar/name events; strict chunk-invariant syntax, Unicode, duplicate-name and exact-decimal handling; explicit non-recursive container stack; no JSON tree materialization. |
| LIB003-D2 | CLOSED | `0.2.165-SNAPSHOT` | `SAME_COMMIT` | Fresh ordinary `JSON.eventWriter(consumer)` with synchronous `feed(event)` / `finish()`; D1 JSON-specific vocabulary validation, deterministic punctuation/order, duplicate object-name rejection, exact scalar emission by reusing the published encoder, one String chunk per accepted event, and terminal non-reentrant consumer failure semantics. |
| LIB003-D3 | CLOSED | `0.2.167-SNAPSHOT` | `SAME_COMMIT` | Ordinary `readEvents(textReader, consumer)` and `writeEvents(textWriter)` adapters; one outstanding operation per adapter, explicit backpressure, D1/D2 reuse, Future-shaped TextReader/TextWriter composition, empty-write finish barrier, no hidden Encoding/ownership/lifecycle semantics. |
| LIB003-E | CLOSED | — | `SAME_COMMIT` | Final closure decomposed into E1 executable conformance/stress and E2 architecture/status reconciliation; the bounded initial JSON surface is complete with no new runtime family, generic serializer, hidden I/O policy, or persistence semantics. |
| LIB003-E1 | CLOSED | — | `e215952e5459782e95f0d9c73c7bffc948bac943` | Test-impact tranche: seven Protos final conformance cases plus existing Java-specific parser-stress and Actor-transfer boundary regressions and the complete Maven suite; no implementation-version change. |
| LIB003-E2 | CLOSED | — | `SAME_COMMIT` | Documentation/governance-only final architecture audit and ledger/design/changelog reconciliation; confirms exact-case single-module scope and deferred cross-format/object-persistence boundaries. |

Dependencies:
- I003 Standard String — CLOSED;
- I004 Array completion — CLOSED;
- I005 Standard Map — CLOSED;
- I007 Error infrastructure — CLOSED;
- I008 Modules — CLOSED;
- I012 Standard Bytes — CLOSED;
- I015 Encoding / Text I/O — CLOSED;
- LIB001 Collections — CLOSED and useful design precedent, but not a runtime
  dependency of the JSON data model;
- LIB002 convenience helpers are not required by LIB003-A/B/C core work.
### LIB004 — Filesystem / process conveniences

Status: IN_PROGRESS

Description: Higher-level filesystem and Process conveniences layered over the
standard capability-based File/Filesystem and Process I/O surfaces.

Design record:
- `docs/project/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md` records the
  completed non-normative LIB004-0 design closure;
- the bounded initial filesystem module is `std:io/Files`, with exactly
  `readAllBytes`, `writeAllBytes`, `readAllText`, and `writeAllText`, explicit
  Filesystem/Path authority, and explicit Encoding for text;
- the bounded Process module is `std:io/ProcessStreams`, with exactly
  `stdinReader`, `stdoutWriter`, and `stderrWriter` as fresh borrowing wrappers
  over explicitly supplied Process streams and Process-provided Encoding;
- whole-file resource custody is an internal ordinary-Protos pattern built on
  Future/Error/`ensure`; no public `withOpen`/resource type or File-specific Java
  cleanup primitive is introduced;
- copy, staged publication, OpenOptions recipes, directory/exists APIs,
  subprocess/OS-process control, ambient authority/default Encoding, and generic
  resource scopes are outside this bounded initial closure;
- LIB004 implementation must preserve `NATIVE_CLOSURE_BOUNDARY_DELTA: 0`; the
  absolute native-boundary inventory remains owned by the then-current
  architecture ledger/guard.

Planned slices:

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| LIB004-0 | CLOSED | — | `SAME_COMMIT` | Documentation/governance design closure, current-main dependency reconciliation, exact bounded modules/contracts, exclusions and slice assignment; no implementation-version or specification-revision change. |
| LIB004-A | IN_PROGRESS | — | — | Subdivided after implementation audit into A1/A2/A3 so the resource-custody races can be validated independently without publishing temporary semantics. |
| LIB004-A1 | CLOSED | `0.2.203-SNAPSHOT` | `SAME_COMMIT` | Publish `std:io/Files.readAllBytes` in ordinary Protos with a retained-open-Future/`ensure` custody path, finite 16×65536 read windows, fresh open whole-result Bytes, explicit Filesystem/Path authority, and source-level empty/multi-window/open-failure conformance. No production Java/native operation added. |
| LIB004-A2 | READY | — | — | Adversarial conformance for cancellation after File acquisition, accepted/pending reads, cleanup suspension, read failure and close failure/precedence without changing the A1 public surface. |
| LIB004-A3 | BLOCKED_BY_DEPENDENCIES | — | — | After A2, adversarial cancellation during pending open and late successful acquisition, final owned-open custody audit, native boundary/status reconciliation, and closure of parent LIB004-A. |
| LIB004-B | BLOCKED_BY_DEPENDENCIES | — | — | After A, `writeAllBytes`; invocation-time private Bytes snapshot; fixed writable create-or-truncate positioned policy; bounded sequential writes; committed-prefix/failure/cancellation aftermath. |
| LIB004-C | BLOCKED_BY_DEPENDENCIES | — | — | After A/B, explicit-Encoding `readAllText`/`writeAllText`; one-shot codec composition; no default Encoding; complete text/cleanup conformance. |
| LIB004-D | READY | — | — | Independently publish `std:io/ProcessStreams` as exact fresh borrowing `TextReader`/`TextWriter` composition over explicit Process streams and Process-provided Encoding. |
| LIB004-E | BLOCKED_BY_DEPENDENCIES | — | — | After A/B/C/D, final cross-slice Protos conformance, architecture/native-boundary audit, project-state reconciliation and bounded parent closure. |

Implementation boundary:
- every operation receives authority explicitly; no module may recover a
  bootstrap-local Filesystem or Process, current directory, temp namespace, or
  host process API;
- File acquisition/close, read/write contribution, cancellation and cleanup
  follow the standardized Core commitment/lifecycle rules rather than a
  library-specific rollback model;
- whole-file writes are create-or-truncate operations, not atomic replacement or
  crash-durable publication, and failed writes are never retried transparently;
- text helpers require an explicit Encoding and preserve that descriptor's exact
  one-shot strict/replacement/BOM behavior;
- Process adapter calls return fresh borrowing wrappers and never cache codec
  state or own the underlying Process standard streams;
- implementation belongs in ordinary Protos Standard Library modules under
  `protos/lib/io/` and adds no Java/native standard operation.

Dependencies:
- I013 Standard Path — CLOSED;
- I014 Standard Byte I/O — CLOSED;
- I015 Encoding / Text I/O — CLOSED;
- I016 Filesystem / File — CLOSED;
- I017 Process I/O / bootstrap — CLOSED;
- I022 Dynamic Error handlers / unwind-safe cleanup — CLOSED;
- no relevant entry in `docs/project/IMPLEMENTATION_BLOCKERS.md` may be
  unresolved for this bounded LIB004 surface.

### LIB005 — Networking

Status: OPEN

Description: Future standard-library networking facilities, to be designed only
after Protos has an explicit portable authority and transport substrate suitable
for library code to consume.

Planning boundary:
- the current Core I/O model does not standardize network authority acquisition,
  socket creation/connect/bind/listen/accept, DNS/name resolution, datagram
  addressing, or transport-configuration APIs;
- LIB005 must not smuggle those missing host/runtime capabilities into ordinary
  library code;
- if portable networking requires new normative Core/runtime semantics, establish
  and track that prerequisite outside LIB005 before the library relies on it;
- no protocol stack, socket API, HTTP API, import spelling, module layout, or
  implementation slices are assigned by this roadmap entry.

Dependencies:
- exact Core/runtime networking prerequisites are not yet established;
- re-audit the then-current specification and implementation before changing
  LIB005 from OPEN to a ready/blocked implementation state.


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
| LM005 | Concurrent Language Maturity | CLOSED | `SAME_COMMIT` | LM005-A Future, LM005-B Actor, and LM005-C Group/GroupRef conformance/examples/tutorials complete; no runtime or normative feature added. |

### LM005 — Concurrent Language Maturity

Status: CLOSED

Scope: dogfood already-implemented Core concurrency semantics through portable
Protos conformance programs, executable examples and tutorials. LM005 does not
define new language/runtime behavior; a reproducible mismatch against already
closed semantics is a bug to report rather than a license to add a feature.

| Slice | Status | Closure evidence | Surface |
|---|---|---|---|
| LM005-A | CLOSED | `SAME_COMMIT` | Future conformance runner support for terminal Future expectations; closure `future()`, fresh Future identity, `then` transformation/flattening, `Future.all`, pre-start cancellation; executable Future example and tutorial progression. |
| LM005-B | CLOSED | `SAME_COMMIT` | Actor-aware language conformance plus Actor.current identity, module bootstrap/spawn, request/reply transfer, synchronous snapshotting, Actor-local state, same-sender send/request FIFO, graceful stop/termination, negative validation cases, examples and tutorial progression. No runtime or normative behavior added. |
| LM005-C | CLOSED | `SAME_COMMIT` | Group/GroupRef acquisition, stable/fresh capability identity, readiness-gated and member-agnostic routing, argument snapshotting, GroupRef Actor transfer, terminated-member exclusion, communication-only surface, examples and tutorial progression. No runtime or normative behavior added. |

Dependency chain: `LM005-A -> LM005-B -> LM005-C`.

Closure reconciliation:
- LM005-A, LM005-B and LM005-C are all published and closed.
- The corpus dogfoods Future, Actor/ActorRef and Group/GroupRef semantics already owned by closed Core implementation items; LM005 added no production runtime or normative behavior.
- Group routing conformance asserts only outcomes permitted by eligible-member selection and deliberately does not pin a scheduler-selected member.

New Language Maturity work MUST allocate and persist its `LMxxx` identifier in
the repository at publication time rather than relying on chat/prompt history.


## Documentation work

The `DOCxxx` family records substantial documentation initiatives whose primary
deliverable has an independently meaningful lifecycle. It does not own language
semantics, implementation behavior, executable conformance policy, or bundled
documentation tooling.

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| DOC001 | Protos Programming Documentation | IN_PROGRESS | `docs/project/DOC001_PROGRAMMING_DOCUMENTATION.md` | A/B/C/D/E/F/G/H/I/J/K/L CLOSED; I023/B007 CLOSED; M toolchain-gated; N final closure. |

### DOC001 — Protos Programming Documentation

Status: IN_PROGRESS

| Slice | Status | Closure evidence | Scope / dependency |
|---|---|---|---|
| DOC001-A | CLOSED | `bd3cd38218cfccdca8de529f4d6c26fede7ad771` | README positioning, documentation architecture, learning navigation, and current-status correction. |
| DOC001-B | CLOSED | `bd3cd38218cfccdca8de529f4d6c26fede7ad771` | Guide 01: bindings, execution contexts, lexical lookup, and receiver state. |
| DOC001-C | CLOSED | `8ab9463b8466974fc5f23f0c7304faeacb3db641` | Guide 02: objects, delegation, composition, structural state, and reflection. |
| DOC001-D | CLOSED | `01470dca9df787ed216b2c19faaead965fb18cc8` | Guide 03: Closures, methods, receivers, extraction, `this`, `context`, `super`, and non-local return. |
| DOC001-E | CLOSED | `0907b1c2682e8330dd1a3a39144cf0a2d8d9beb1` | Guide 04: control flow through ordinary protocols; B007/I023 dependency closed before publication. |
| DOC001-F | CLOSED | `9650a386dce901d630ccd31dc2c377124c829d85` | Guide 05: values, identity, equality, and collections. |
| DOC001-G | CLOSED | `37c47498b57dca25912f682439416c6ca3ccd46c` | Guide 06: modules and imports. |
| DOC001-H | CLOSED | `3e5a1176ecaaa3c02980041a96e3f9021774180d` | Guide 07: Errors, handlers, `ensure`, and resource lifetime. |
| DOC001-I | CLOSED | `fdde9c1376aea39988bc139dbd2c604362114061` | Guide 08: Futures and structured concurrency. |
| DOC001-J | CLOSED | `SAME_COMMIT` | Guide 09: isolated parallel execution. |
| DOC001-K | CLOSED | `SAME_COMMIT` | Guide 10: Actors, ActorRefs, Groups/GroupRefs, messaging, isolation, ownership, and lifecycle. |
| DOC001-L | CLOSED | `SAME_COMMIT` | Guide 11: Process, byte/text I/O, Filesystem/File capabilities, Path, authority, resource boundaries, and explicit D046/I024 current-status distinction. |
| DOC001-M | BLOCKED_BY_DEPENDENCIES | — | Packages, testing, and bundled toolchain; final closure requires TOOL001 and TOOL002 CLOSED. |
| DOC001-N | BLOCKED_BY_DEPENDENCIES | — | Final navigation, stale-status/link audit, cross-document consistency, and DOC001 closure after E-M. |

Owning record:
`docs/project/DOC001_PROGRAMMING_DOCUMENTATION.md`.

DOC001 remains IN_PROGRESS as a whole. I023-D closes B007 and removes
DOC001-E's implementation dependency; the fresh I023-D current-main audit makes
DOC001-E READY. Independent READY slices remain unaffected and still perform
their own current-main audit when started.

## Toolchain tools

The `TOOLxxx` family records official toolchain-bundled developer tools whose
implementation lifecycle and policy are independently meaningful. Public command
spelling is orthogonal to ownership: `CLIxxx` remains the driver/terminal layer,
while a bundled tool reached through `protos` remains `TOOLxxx`. `PERFxxx`
continues to record project performance engineering, and `LIBxxx` continues to
record distributable Standard Library functionality.

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| TOOL001 | Package Tool | IN_PROGRESS | `docs/project/TOOL001_PACKAGE_TOOL.md` | D/E/F1 CLOSED; F2 now includes lock I/O, stale/root assembly and F2D1 workspace execution-plan contract. F2D2 pure plan construction READY. |
| TOOL002 | Test Tool | IN_PROGRESS | `docs/project/TOOL002_TEST_TOOL.md` | A/B/C/D/E CLOSED; TOOL002-F IN_PROGRESS, F3A/F3B/F3C/F3D CLOSED, F3E IN_PROGRESS (E1 CLOSED; E2 READY), then F4 and G-J dependency-ordered. |

### TOOL001 — Package Tool

Status: IN_PROGRESS

`TOOL001` is a tracking reconciliation over package-tool work that began before
the `TOOLxxx` family existed. The legacy labels remain immutable historical
evidence; TOOL001 supplies the canonical parent/slice lifecycle from this point
forward.

| TOOL001 slice | Legacy published label | Status | Closure evidence | Scope / notes |
|---|---|---|---|---|
| TOOL001-A | bundled package-tool bootstrap slice | CLOSED | `9c336932c502163c97ab02d3e6ba0c6ee6d10c26` | Exact toolchain-bundled `protos package` entry under `protos/tools/package`; no project graph used to acquire the tool itself. |
| TOOL001-B1 | Package-tool Filesystem Slice 2A | CLOSED | `f5738f8d1063cdf6e2d969b792d786177bfa8a36` | Explicit read-only confined project Filesystem authority for package metadata. |
| TOOL001-B2 | Package-tool Filesystem Slice 2B / B006 | CLOSED | `f128293fbe769cc8806879b0784262b56a08a4ba` | Explicit staging-write and namespace-mutation authority; metadata publication through ordinary File/Filesystem operations. |
| TOOL001-C1 | package-tool manifest Slice 3A | CLOSED | `8150d8219664b50ec66748639a47a1629638a8ed` | Internal Protos `self:TomlSyntax` parser foundation; schema-v1 design prerequisite subsequently published at `85ac538d378eeb2153e318145cae69114563e858`. |
| TOOL001-C2 | package-tool manifest Slice 3B1 | CLOSED | `dc82976cd95ad4f08d446fbb7aedb43adf818612` | Complete TOML 1.0 String surface required by manifest schema v1 while retaining package meaning/schema validation outside the syntax engine. |
| TOOL001-C3 | package-tool manifest Slice 3B2-A | CLOSED | `SAME_COMMIT` | Ordinary-Protos document scanner segments logical TOML statements across comments/strings/nesting without acquiring table/schema meaning. |
| TOOL001-C4 | package-tool manifest Slice 3B2-B | CLOSED | `SAME_COMMIT` | Canonical TOML table model complete: ordinary headers/dotted/inline tables plus arrays-of-tables and ownership/conflict invariants. |
| TOOL001-C4A | package-tool manifest Slice 3B2-B1 | CLOSED | `SAME_COMMIT` | Ordinary-Protos canonical table assembly for headers, dotted keys and inline-table equivalence with TOML duplicate/redefinition ownership checks. |
| TOOL001-C4B | package-tool manifest Slice 3B2-B2 | CLOSED | `SAME_COMMIT` | TOML 1.0 arrays-of-tables, latest-element nesting and final table-model conformance. |
| TOOL001-C5 | package-tool manifest Slice 3C | CLOSED | `SAME_COMMIT` | Complete schema-v1 structural validation and ordinary ManifestV1 construction across C5A/C5B/C5C/C5D, including exact dependency source-form exclusivity. |
| TOOL001-C5A | package-tool manifest Slice 3C prerequisite | CLOSED | `SAME_COMMIT` | Remove manifest-scale linear stack growth from C3/C4 document traversal without changing TOML semantics; one long Protos regression. |
| TOOL001-C5B | package-tool manifest Slice 3C1 | CLOSED | `SAME_COMMIT` | Stable base helper validates root names, exact manifest generation 1 and required package identity/version/optional locator using ordinary Protos data. |
| TOOL001-C5C | package-tool manifest Slice 3C2 | CLOSED | `SAME_COMMIT` | Optional compatibility, exports and workspace structural model over C5B, including fail-closed owned fields and duplicate-free workspace members; dependency declarations remain C5D. |
| TOOL001-C5D | package-tool manifest Slice 3C3 | CLOSED | `SAME_COMMIT` | Dependency alias declarations, registry/Git/path exclusivity, complete ordinary ManifestV1 construction and final C5 cross-schema conformance. |
| TOOL001-C6 | package-tool manifest Slice 3D | CLOSED | `SAME_COMMIT` | Ordinary-Protos ManifestCommand reads exactly `protos.toml` through confined Filesystem authority, consumes complete UTF-8 TextReader chunks, invokes closed schema v1 and emits read-vs-schema diagnostics; the host only selects exact bundled `ManifestMain` for the public manifest command while historical bare Main stays unchanged. |
| TOOL001-C7 | package-tool manifest Slice 3 closure | CLOSED | `SAME_COMMIT` | Documentation/governance-only final cross-slice validation and architecture/status reconciliation; no executable or implementation-version change. |
| TOOL001-C | legacy package-tool manifest Slice 3 parent | CLOSED | `SAME_COMMIT` | C1-C7 complete the bounded manifest surface: TOML, schema-v1 structural model, confined project-manifest read/diagnostics and final reconciliation. Later package-resolution concerns are separately scoped. |
| TOOL001-D | release-version / dependency-constraint value policy | CLOSED | `SAME_COMMIT` | D1 ReleaseVersion and D2A-D2D complete strict version parsing/precedence plus exact/caret/bounded/same-core-prerelease constraint v1 policy in bundled Protos. |
| TOOL001-D1 | strict ReleaseVersion value + precedence | CLOSED | `SAME_COMMIT` | Ordinary bundled-Protos parser/model/ordering for strict SemVer-derived ReleaseVersion, with no build metadata or dependency-selection policy. |
| TOOL001-D2 | dependency constraint language v1 | CLOSED | `SAME_COMMIT` | Exact, caret, bounded interval and explicit same-core prerelease-admission behavior are complete with cross-form Protos conformance. |
| TOOL001-D2A | exact dependency constraints | CLOSED | `SAME_COMMIT` | Bare full ReleaseVersion parse + exact satisfaction in ordinary bundled Protos; no range/candidate/lock policy. |
| TOOL001-D2B | caret dependency constraints | CLOSED | `SAME_COMMIT` | Caret bounds and stable-candidate satisfaction, including zero-major rules; prerelease admission deferred to D2D. |
| TOOL001-D2C | explicit bounded intervals | CLOSED | `SAME_COMMIT` | Two-comparison bounded interval parsing and stable-candidate satisfaction; prerelease admission deferred to D2D. |
| TOOL001-D2D | prerelease admission + D2 closure | CLOSED | `SAME_COMMIT` | Final prerelease admission + cross-form conformance closes D2 and parent D; resolver/lock policy remains outside this parent. |
| TOOL001-E | local/offline version selection policy | CLOSED | `SAME_COMMIT` | E1 fresh highest-satisfying selection and E2 retained exact-version preference complete pure selection over already-known ReleaseVersion candidates. |
| TOOL001-E1 | fresh highest-satisfying ReleaseVersion selection | CLOSED | `SAME_COMMIT` | Pure bundled-Protos selection over already-known candidates using closed D2 satisfaction and D1 precedence; no-match fails closed. |
| TOOL001-E2 | retained exact-version preference | CLOSED | `SAME_COMMIT` | Preserve an available exact retained version while it satisfies D2; otherwise fall back to E1. No physical lockfile/identity/discovery behavior. |
| TOOL001-F | canonical physical lockfile v1 | IN_PROGRESS | TOOL001-F1 CLOSED; F2D CLOSED; F2E0 `SAME_COMMIT` | Canonical lockfile + workspace execution are published; F2E external immutable-package execution is the active continuation. |
| TOOL001-F1A | canonical lock header grammar | CLOSED | `SAME_COMMIT` | Design/governance-only freeze of exact header keywords, separators, canonical decimals/tokens/digest spelling and one blank line before body records. |
| TOOL001-F1B | canonical lock body node/edge grammar | CLOSED | `SAME_COMMIT` | F1B1/F1B2/F1B3 freeze scalar/reference, root/workspace, external node, dependency and total canonical body grammar. |
| TOOL001-F1B1 | canonical scalar strings + typed node references | CLOSED | `SAME_COMMIT` | Deterministic quoted UTF-8 body scalars and typed registry/git/workspace identity tuples; does not freeze PackageId's public textual encoding. |
| TOOL001-F1B2 | root/workspace representation | CLOSED | `SAME_COMMIT` | One root workspace-ref plus canonically ordered mappings from explicit workspace.members strings to member workspace refs; no virtual root or path semantics. |
| TOOL001-F1B3 | external node blocks + dependency edges + F1B closure | CLOSED | `SAME_COMMIT` | Registry/git external records, ContentIdentity/provenance, dependency edges and final ordering/separation close F1B. |
| TOOL001-F1C | canonical lock parser/writer + round-trip conformance | CLOSED | `SAME_COMMIT` | F1C1/F1C2/F1C3 complete lexical, structural and canonical writer/round-trip behavior. |
| TOOL001-F2 | physical lock integration | IN_PROGRESS | TOOL001-F2A/F2B/F2C/F2D CLOSED; F2E0 `SAME_COMMIT` | Workspace-only normal execution is CLOSED. F2E external immutable-package execution is allocated; E1 canonical ContentIdentity tree contract is READY. |
| TOOL001-F2A | confined `protos.lock` read/publish substrate | CLOSED | `SAME_COMMIT` | Canonical load plus validation-before-staging atomic lock publication through existing confined Filesystem authority. |
| TOOL001-F2B | semantic resolution-input + stale detection | CLOSED | `SAME_COMMIT` | F2B1 projection + F2B2 semantic root assembly + F2B3 canonical bytes/SHA-256/header stale comparison complete the bounded stale-input layer. |
| TOOL001-F2C | physical resolution-root assembly | CLOSED | `SAME_COMMIT` | `self:ResolutionRoot.assemble(projectTreeFilesystem)` loads root + explicit member manifests, projects D1/D2 dependencies and normalizes in-root path targets into F2B ResolutionRootV1 using existing confined read-only tree authority. |
| TOOL001-F2D | workspace-only normal-execution preflight + PackageExecutionPlan | CLOSED | `SAME_COMMIT` | Bounded workspace-only normal execution is complete: canonical non-stale workspace locks preflight read-only and run through exact package-backed modules. External registry/Git materialization remains future F2 work. |
| TOOL001-F2D1 | PackageExecutionPlan ABI + runtime-name/preflight contract | CLOSED | `SAME_COMMIT` | Workspace lock reconciliation, portable alias/export/module names, inert plan/authority boundary and external-node rejection frozen. |
| TOOL001-F2D2 | pure workspace execution-state + plan construction | CLOSED | `SAME_COMMIT` | ResolutionRoot single-pass execution state + RuntimeNames + canonical non-stale workspace lock reconciliation produce fresh inert PackageExecutionPlanV1. |
| TOOL001-F2D3 | mechanical host resolver handoff + command-scoped workspace preflight | CLOSED | `SAME_COMMIT` | Immutable plan detach, exact workspace resolver and command-scoped workspace run handoff are fully published. |
| TOOL001-F2D3A | immutable host DTO + defensive plan detach | CLOSED | `SAME_COMMIT` | Mechanical ABI boundary only: exact shape/domain/location/edge validation and recursive immutable copy. |
| TOOL001-F2D3B | exact workspace package-backed module resolver | CLOSED | `SAME_COMMIT` | B1 identity/source plus B2 self:/dep:/std: routing complete the workspace resolver. |
| TOOL001-F2D3B1 | package identity + source mechanism | CLOSED | `SAME_COMMIT` | Canonical workspace ModuleKey identity plus exact package-directory and confined logical-source mapping complete B1. |
| TOOL001-F2D3B1A | canonical workspace ModuleKey codec | CLOSED | `SAME_COMMIT` | Workspace-domain canonical ModuleKey codec over exact PackageId + internal logical module; host-only and filesystem-free. |
| TOOL001-F2D3B1B | physical source mechanism | CLOSED | `SAME_COMMIT` | Root/index, exact member-directory binding and logical-module -> exact regular confined `.protos` mapping complete B1B. |
| TOOL001-F2D3B1B1 | selected project-root anchor + detached package index | CLOSED | `SAME_COMMIT` | Host-only representation step; no non-root filesystem traversal. |
| TOOL001-F2D3B1B2 | exact member-location directory binding | CLOSED | `SAME_COMMIT` | Exact child lookup + confined traversal + immutable package-directory binding closed across B1B2A/B/C. |
| TOOL001-F2D3B1B2A | exact direct-child directory lookup | CLOSED | `SAME_COMMIT` | Host-only one-component exact stored-name + directory-type primitive; no multi-component path traversal. |
| TOOL001-F2D3B1B2B | confined canonical member-location traversal | CLOSED | `SAME_COMMIT` | Canonical multi-component traversal; every selected real directory must remain under the real project root, so only in-root symlinks survive. |
| TOOL001-F2D3B1B2C | immutable package -> physical-directory binding | CLOSED | `SAME_COMMIT` | Immutable exact PackageId/location -> PackageNode + real-directory bindings over the closed B1B1/B1B2B mechanics. |
| TOOL001-F2D3B1B3 | logical module -> exact regular `.protos` source | CLOSED | `SAME_COMMIT` | Portable logical names map by exact spelling to one case-unambiguous regular real `.protos` source confined beneath the selected package root. |
| TOOL001-F2D3B2 | resolver routing | CLOSED | `SAME_COMMIT` | self:/dep:/std: routing complete; unsupported spellings fail closed. |
| TOOL001-F2D3B2A | self: routing | CLOSED | `SAME_COMMIT` | Explicit root entry key plus importer-relative self: resolution over exact plan PackageId and B1B3 source; Protos-source import execution covered. |
| TOOL001-F2D3B2B | dep: edge/export routing | CLOSED | `SAME_COMMIT` | Importer PackageId + exact alias selects one detached target edge; exact target export selects the internal logical module, with no physical/internal-name bypass. |
| TOOL001-F2D3B2C | std: delegation + resolver closure | CLOSED | `SAME_COMMIT` | Exact std: resolution/source loading delegates to the explicitly selected Standard Library resolver. |
| TOOL001-F2D3C | command-scoped workspace preflight + authority separation | CLOSED | `SAME_COMMIT` | Read-only tool preflight, detached-plan application execution, authority-isolation proof and public workspace-run wiring are complete. |
| TOOL001-F2D3C1 | read-only Package Tool preflight -> detached plan | CLOSED | `SAME_COMMIT` | Fresh tool Process receives only confined read-only projectTreeFilesystem; ExecutionPlan.build result is detached before tool Process termination. |
| TOOL001-F2D3C2 | detached plan -> separately-authorized application Process | CLOSED | `SAME_COMMIT` | C2A canonical initial-module execution, C2B fresh application Process wiring and C2C C1->C2 authority-isolation integration are all closed. |
| TOOL001-F2D3C2A | TOOL001 F2D3 application execution: canonical initial-module execution primitive | CLOSED | `SAME_COMMIT` | Cache the supplied RootActor bootstrap module context under one canonical ModuleKey before task execution; mark READY on completion and remove on failure/cancellation. No package/workspace/Process lifecycle policy. |
| TOOL001-F2D3C2B | TOOL001 F2D3 application execution: detached plan -> fresh application Process wiring | CLOSED | `SAME_COMMIT` | Reconstruct the exact workspace resolver from detached DTO data, select the explicit root-package entry, bootstrap one fresh application Process from copied args/environment plus explicitly supplied streams/Encoding bindings, execute through C2A, and terminate the Process. |
| TOOL001-F2D3C2C | TOOL001 F2D3 application execution: C1->C2 authority-isolation integration + closure | CLOSED | `SAME_COMMIT` | Real C1->C2B integration proves the detached DTO is the only Package Tool result crossing the boundary, tool/application Processes are distinct and terminated, and projectTreeFilesystem is unavailable to application source. |
| TOOL001-F2D3C3 | public workspace-run wiring + F2D3/F2D closure | CLOSED | `SAME_COMMIT` | C3A CLI-neutral driver plus C3B explicit public `protos run <entry> [args...]` wiring complete the workspace-run boundary. |
| TOOL001-F2D3C3A | TOOL001 F2D3 public-run integration: CLI-neutral workspace-run driver | CLOSED | `8769e106dceeb7b7d6bf2c888a24a74f18b08e6e` | CLI-neutral C1->C2 workspace-run driver with explicit project root and logical entry. |
| TOOL001-F2D3C3B | TOOL001 F2D3 public-run integration: public `protos run` wiring + final F2D3/F2D closure | CLOSED | `SAME_COMMIT` | Public CLI selects the current directory as project root and requires an explicit root-package logical entry; application args start after the entry, diagnostics translate the closed driver outcome, and no default application Filesystem is granted. |
| TOOL001-F2E | external immutable-package execution continuation | IN_PROGRESS | F2E0/F2E1 CLOSED; I024/B009 CLOSED | ContentIdentity plus the general immutable-capture capability are published; F2E2 verified read-only store binding is READY. |
| TOOL001-F2E0 | external materialization prerequisite audit + decomposition | CLOSED | `SAME_COMMIT` | Fresh post-F2D audit preserves fail-closed external execution and allocates E1-E5 without an executable shortcut. |
| TOOL001-F2E1 | `protos-package-tree-v1` ContentIdentity canonical tree contract | CLOSED | `SAME_COMMIT` | E1A logical tree/path domain, E1B canonical stream + sha256 relation, and E1C independent fixed vectors are published; protos-package-tree-v1 is frozen. |
| TOOL001-F2E1A | ContentIdentity logical-tree domain + portable path/entry-kind contract | CLOSED | `SAME_COMMIT` | Define ContentIdentity over the already-materialized payload, include every valid regular-file path+bytes, require root `protos.toml`, make directories structural/empty directories non-semantic, reject symlink/special entries, ignore host metadata and freeze conservative portable ASCII artifact paths/collision rules. |
| TOOL001-F2E1B | canonical byte stream + method/hash contract | CLOSED | `SAME_COMMIT` | Serialize the E1A path->bytes map as one method-domain-separated binary stream: exact ASCII path-byte order, FILE/END tags, minimal arbitrary-precision base-128 lengths, direct content bytes, and sha256 as the initial mandatory digest algorithm. |
| TOOL001-F2E1C | independent conformance vectors + F2E1 closure | CLOSED | `SAME_COMMIT` | Independent fixed streams/digests plus a separate Java reference oracle cover ordering, framing, varuint boundaries, mutations, invalid paths/collisions, special-entry rejection and metadata/layout non-semantics. |
| TOOL001-F2E2 | verified read-only package-store binding | IN_PROGRESS | I024/B009 CLOSED; F2E2A READY | Decomposed into Protos-only captured-tree ContentIdentity verification (A), exact already-selected root capture/custody handoff (B), and integrated same-capture closure evidence (C). |
| TOOL001-F2E2A | captured-Filesystem ContentIdentity canonicalizer/verifier | READY | F2E1 + I024 CLOSED | Implement `self:ContentIdentity` in bundled Protos over an already-captured read-only Filesystem: validate the frozen protos-package-tree-v1 domain, canonicalize exact path/content bytes, sha256/hash, compare the locked ContentIdentity, and return the same supplied capture on success. No store lookup, capture acquisition, host custody, plan extension or resolver work. |
| TOOL001-F2E2B | exact selected-root capture + verified-capture host custody | BLOCKED_BY_DEPENDENCIES | TOOL001-F2E2A | Own the host-mechanical boundary for one already-selected store root: provision/confine general Filesystem authority, obtain D046 immutable capture and preserve that exact captured backing across the Package Tool verification/handoff boundary. No ambient scan, fetch, lock mutation, ContentIdentity policy duplication or execution-plan extension. |
| TOOL001-F2E2C | same-capture integration + F2E2 closure | BLOCKED_BY_DEPENDENCIES | TOOL001-F2E2B | Compose fixed ContentIdentity evidence with the exact B capture/custody path, prove verification and subsequent handoff consume the same immutable capture, close F2E2 and make F2E3 READY. No external resolver/public-run integration. |
| TOOL001-F2E3 | external-node execution-plan construction | BLOCKED_BY_DEPENDENCIES | — | Depends on F2E2. Extend Protos-owned preflight only from exact store-confined ContentIdentity-verified registry/Git nodes. |
| TOOL001-F2E4 | external canonical ModuleKey + source resolver | BLOCKED_BY_DEPENDENCIES | — | Depends on F2E3. Mechanical host identity uses exact immutable package instance + internal logical module, never locator/cache path/alias. |
| TOOL001-F2E5 | public run integration + F2 external-execution closure | BLOCKED_BY_DEPENDENCIES | — | Depends on F2E4. Extend normal run without version solving, implicit fetch, package-store authority leakage or lock mutation. |


| TOOL001-F2B1 | per-manifest semantic resolution-input projection design | CLOSED | `SAME_COMMIT` | Resolver-affecting field matrix, semantic normalization ownership, deterministic ordering and fail-closed unresolved-owner rule frozen. |
| TOOL001-F2B2 | resolution-root/workspace semantic assembly design | CLOSED | `SAME_COMMIT` | Canonical root/member/path/compatibility assembly frozen without raw-source/host-path fallback. |
| TOOL001-F2B3 | resolution-input digest + stale comparison | CLOSED | `SAME_COMMIT` | `self:ResolutionInput` canonicalizes semantic root bytes, hashes with std:crypto/SHA256, renders lowercase hex and compares lock-format/resolver/input header; LockFile adds read-only isStale. |


| TOOL001-F1C1 | lock lexical/header/qstring/node-ref primitives | CLOSED | `SAME_COMMIT` | Bundled-Protos LockSyntax plus Protos-owned conformance; no body graph parsing or I/O. |
| TOOL001-F1C2 | body record/model + structural validation | CLOSED | `SAME_COMMIT` | Complete in-memory body model with duplicate/dangling structural rejection; no canonical ordering rejection yet. |
| TOOL001-F1C3 | total writer + canonical rejection + F1C closure | CLOSED | `SAME_COMMIT` | F1B total ordering, canonical writer, non-canonical rejection and cross-slice round trips close F1C. |





Detailed migration and continuation rules live in
`docs/project/TOOL001_PACKAGE_TOOL.md`.

### TOOL002 — Test Tool

Status: IN_PROGRESS

The initial TOOL002 implementation sequence is promoted directly from the
selected test-tool architecture. Hard timeout / OS-worker recovery remains a
separate explicitly deferred design and is not silently made a prerequisite for
the initial TOOL002 closure.

| Slice | Status | Dependency / scope |
|---|---|---|
| TOOL002-A | CLOSED | `0.2.168-SNAPSHOT` / `SAME_COMMIT`; exact bundled `protos test` dispatch plus a tiny ordinary-Protos entry, with no corpus migration or test policy. |
| TOOL002-B | CLOSED | `0.2.169-SNAPSHOT` / `SAME_COMMIT`; local test-neutral fresh-Process exact-entry executor, shared RootActor cooperative terminal dispatch, inert COMPLETED/FAILED/CANCELLED outcome, explicit bootstrap authority and Process termination before return; no test policy. |
| TOOL002-C | CLOSED | `0.2.171-SNAPSHOT` / `SAME_COMMIT`; one exact compiled entry executes through TOOL002-B in a fresh Process with private stdin/stdout/stderr and detached captured output; no manifest/expectation/scheduler/result-transfer policy. |
| TOOL002-D | CLOSED | D1-D4 complete through `0.2.211-SNAPSHOT`: bundled Protos owns all retained non-Future main-manifest expectation families, including child-local Closure/Error freshness; direct Java conformance ownership remains only for `future-*` pending TOOL002-F. |
| TOOL002-E | CLOSED | E1A/E1B/E2A/E2B/E3/E4 complete the retained Package/TOML migration: bundled Protos owns corpus planning/loading/execution/Boolean-Error policy/aggregation and the duplicate Java TOML owner is retired. |
| TOOL002-E2A | CLOSED | E2A1 selected resolver/fresh-Process normal execution plus E2A2 cross-Prelude failed/Error observation complete through `0.2.222-SNAPSHOT`; E2A2C closes the cross-slice ownership/confinement boundary without executable changes. |
| TOOL002-E2A1 | CLOSED | `0.2.219-SNAPSHOT` / `SAME_COMMIT`; named exact-source facility over an already-selected Prelude plus bootstrap-local `packageExecution` using the existing bundled Package Tool resolver; one real `self:TomlSyntax` fixture completes normally in a fresh Process. |
| TOOL002-E2A2 | CLOSED | E2A2A/E2A2B executable work is complete through `0.2.222-SNAPSHOT`; E2A2C closes the documentation/governance reconciliation with no implementation-version change. |
| TOOL002-E2A2A | CLOSED | `0.2.220-SNAPSHOT` / `SAME_COMMIT`; source/destination Prelude-aware detached standard Error taxonomy rematerialization; no Package integration. |
| TOOL002-E2A2B | CLOSED | `0.2.222-SNAPSHOT` / `SAME_COMMIT`; selected Package Prelude feeds detached observation and one retained failed TOML fixture is rematerialized as a fresh Test Tool-domain Error. |
| TOOL002-E2A2C | CLOSED | Documentation/governance-only cross-slice validation confirms Package resolver selection, fresh-Process/private-capture execution, separate confined caller corpus authority, caller-domain Error rematerialization and absence of E2B expectation/TestPlan policy; no implementation-version change. |
| TOOL002-E2B | CLOSED | E2B1 + E2B2 complete through `0.2.226-SNAPSHOT`; bundled Protos now owns retained Package/TOML planning, confined source loading, Package execution, Boolean/Error expectation interpretation and full-corpus aggregation. |
| TOOL002-E2B1 | CLOSED | `0.2.224-SNAPSHOT` / `SAME_COMMIT`; retained Package/TOML `error` rows now emit D3A2's canonical generic-error expected sentinel `-`; no fixture execution or runner-policy change. |
| TOOL002-E2B2 | CLOSED | `0.2.226-SNAPSHOT` / `SAME_COMMIT`; public `Main.protos` executes the full retained Package/TOML plan through generic `Runner` + `packageExecution`; Protos evidence requires selected==plan cases, skipped==0, passed==plan cases and complete result cardinality. |
| TOOL002-E3 | CLOSED | Legacy Java TOML corpus owner removed: no JUnit path parses the retained manifest, interprets its `true`/`error` expectations or directly executes every fixture. Existing Java tests retain host-mechanical coverage only; no implementation-version change. |
| TOOL002-E4 | CLOSED | Documentation/governance-only final E reconciliation; no executable, normative or implementation-version change. Parent TOOL002-E is CLOSED and TOOL002-F is READY. |
| TOOL002-F | IN_PROGRESS | F1/F2/F3 are CLOSED; F4 READY for the atomic public Future expectation ownership cutover; G-J remain dependency-ordered. |
| TOOL002-F1 | CLOSED | `0.2.228-SNAPSHOT` / `SAME_COMMIT`; bundled Protos can evaluate `future-integer/null/boolean` wholly inside one fresh child Process through ordinary `Future.value()` suspension/resume and return only Boolean evidence. Public `runSimple` selection is unchanged. |
| TOOL002-F2 | CLOSED | `0.2.229-SNAPSHOT` / `SAME_COMMIT`; repeated child-local `Future.value()` observations distinguish FAILED stored-Error identity from CANCELLED fresh `Cancelled` occurrences and preserve exact immediate Error-parent policy; not yet selected by `runSimple`. |
| TOOL002-F3 | CLOSED | F3A/F3B/F3C/F3D plus F3E E1-E4 complete retained stored/fresh Future-observation evidence and private policy; public activation remains exclusively F4. |
| TOOL002-F3A | CLOSED | F3A1 `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` + F3A2 `119c90032088ccf428ace37185187b854966e0cb` + F3A3 `SAME_COMMIT`; base stored-Future Error identity is established independently from Test Tool mechanism. |
| TOOL002-F3A1 | CLOSED | `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`; test-impact Protos conformance proves repeated failed-Future observations re-signal one Error identity. |
| TOOL002-F3A2 | CLOSED | `119c90032088ccf428ace37185187b854966e0cb`; test-impact Protos conformance proves a failed-Future observation is exactly the producer-created same-domain Error. |
| TOOL002-F3A3 | CLOSED | `SAME_COMMIT`; documentation/governance reconciliation only; persists fine-grained F3 continuation and makes F3B/F3B1 READY. |
| TOOL002-F3B | CLOSED | B1 exact shape + B2 first exact local Error observation + B3 second repeated exact identity complete retained stored-fixture evidence outside Test Tool policy. |
| TOOL002-F3B1 | CLOSED | `SAME_COMMIT`; direct execution of the exact retained stored fixture yields exactly local `future`/`error`/`observe`, typed as Future/Object/source Closure, with zero observe parameters. No terminal dispatch or observe invocation. |
| TOOL002-F3B2 | CLOSED | `SAME_COMMIT`; retained Future is progressed to FAILED without observation, retained `observe` is invoked exactly once in the original activation, and the resulting signal carries the exact retained local `error`. No second observation. |
| TOOL002-F3B3 | CLOSED | `SAME_COMMIT`; after terminalization without observation, two retained `observe` invocations each signal the exact local `error` and therefore satisfy `first === second === error`. Closes F3B. |
| TOOL002-F3C | CLOSED | C1 positive stored policy plus C2 malformed/identity-mismatch/immediate-parent negative evidence complete through `0.2.252-SNAPSHOT`; no public Future activation. |
| TOOL002-F3C1 | CLOSED | C1A `0.2.241-SNAPSHOT` / `SAME_COMMIT` + C1B through `0.2.250-SNAPSHOT`; generic live-result inspection and positive retained `stored:Error` bundled-Protos policy are complete with no public Future activation. |
| TOOL002-F3C1A | CLOSED | `0.2.241-SNAPSHOT` / `SAME_COMMIT`; generic `executionInspect` executes exact source directly in a fresh Process, drains cooperative Actor work to idle with zero live tasks, invokes an exact inspector Closure with the live source result in the same activation/domain, and detaches only the inspector terminal observation. No Test policy. |
| TOOL002-F3C1B | CLOSED | `0.2.250-SNAPSHOT` / `SAME_COMMIT`; B1 exact stored CaseSpec classifier + B2 retained live-inspection evidence + B3 bundled-Protos evaluator composition + B4 governance reconciliation close positive `stored:Error` policy. No fresh/negative/public `runSimple` activation and Java remains temporary Future owner until F4. |
| TOOL002-F3C2 | CLOSED | `0.2.252-SNAPSHOT` / `SAME_COMMIT`; bundled Protos fails closed on malformed stored policy, returns inert false evidence for identity/parent mismatches, and requires exact stored identity plus immediate `Error` parent; closes F3C. |
| TOOL002-F3D | CLOSED | F3D1 first cancelled-parent evidence + F3D2 second-observation fresh identity complete retained `fresh:Cancelled` evidence. |
| TOOL002-F3D1 | CLOSED | `SAME_COMMIT`; test-impact Protos evidence invokes the retained cancelled fixture's exact `observe` Closure once inside `executionInspect` and requires the caught Error's immediate parent to be `Cancelled`. No second-observation freshness policy. |
| TOOL002-F3D2 | CLOSED | `SAME_COMMIT`; test-impact Protos evidence observes the same retained cancelled Future twice, requires the second caught Error's immediate parent to be `Cancelled`, and requires `first !== second`; closes F3D. |
| TOOL002-F3E | CLOSED | E1 parser + E2 fresh evaluator + E3A unified private composition + E3B integrated negatives + E4 governance reconciliation complete; F4 READY. |
| TOOL002-F3E1 | CLOSED | `SAME_COMMIT`; private bundled-Protos parser accepts retained `stored`/`fresh` `MODE:ErrorPrototype`, resolves the named standard immediate parent, and fails closed on malformed format/mode/prototype; no observation execution or public selection. |
| TOOL002-F3E2 | CLOSED | `SAME_COMMIT`; private `fresh` evaluator parses the retained mode/parent, invokes `observe` exactly twice inside `executionInspect`, requires both immediate parents and `first !== second`; no public selection. |
| TOOL002-F3E3A | CLOSED | `SAME_COMMIT`; one private observation entry parses retained policy and dispatches positive `stored`/`fresh` cases to their already-closed evaluators; no public selection. |
| TOOL002-F3E3B | CLOSED | `SAME_COMMIT`; unified private observation policy rejects malformed input before inspection and returns inert false evidence for stored identity/parent and fresh identity/parent mismatches; no public selection. |
| TOOL002-F3E4 | CLOSED | Documentation/governance-only reconciliation closes F3E/F3 after E1-E3B evidence and makes F4 READY; no executable, normative or implementation-version change. |
| TOOL002-F4 | READY | TOOL002-F3 CLOSED; atomically activate retained `future-*` expectations in generic `runSimple`, prove complete main-manifest ownership, retire Java Future expectation policy and close F; make G READY. |
| TOOL002-G | BLOCKED_BY_DEPENDENCIES | TOOL002-F; migrate Actor/Group scheduler-sensitive language coverage without a test-only concurrency model. |
| TOOL002-H | BLOCKED_BY_DEPENDENCIES | TOOL002-G; bounded parallel scheduling of independent fresh Processes with independent output capture and deterministic reporting. |
| TOOL002-I | BLOCKED_BY_DEPENDENCIES | TOOL002-H; explicit resource constraints/private capabilities for real external-resource sharing. |
| TOOL002-J | BLOCKED_BY_DEPENDENCIES | TOOL002-I; CI/launcher integration: Java implementation tests first, then the Protos test tool for the Protos corpus. |

Detailed scope and architecture references live in
`docs/project/TOOL002_TEST_TOOL.md`.

## Performance

The `PERFxxx` family records non-normative performance engineering over
already-defined Protos behavior: reproducible benchmark suites, profiling and
performance investigations, optimization work, and performance-regression
protection. It does not define language semantics.

Performance work follows these project rules:

- correctness and conformance validation precede performance conclusions;
- cold/startup measurements are reported separately from warmed steady-state
  measurements when a JIT/runtime warmup distinction exists;
- benchmark evidence records the materially relevant Protos revision,
  implementation/runtime/JDK/GraalVM versions, host/hardware, workload,
  warmup policy, measurement policy, and aggregation method;
- cross-language comparisons use materially equivalent algorithms and inputs,
  identify materially different runtime modes, and do not generalize one
  microbenchmark into a claim about whole-language performance;
- benchmark-specific hidden semantics, privileged language objects, or
  correctness shortcuts are not permitted;
- a finding that requires an observable semantic change leaves PERF work and
  follows the applicable specification/design and implementation process first.

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| PERF001 | Core v0.1 baseline benchmark suite | IN_PROGRESS | — | PERF001-A/B/C/D/E CLOSED; PERF001-E reference collection evidence is `guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565`, generated by harness `280173d743b2ed838a89be0ad930b20828d89558` against exact corpus revision `86b35d8bb2d7ab2ad54bc2947e1bf7fbff1fca15`. PERF001 remains open for the focused concurrency slice and final reproducibility/reporting; PERF001-E's two `GraphTooBig` findings are tracked separately by PERF003. |
| PERF002 | Truffle compilability and dispatch optimization | CLOSED | `guillermomolina/protos-benchmarks@c69248714a60dd62894164bf5332b55f780d6fe4` | PERF002-A implementation/conformance CLOSED at `0.2.162-SNAPSHOT`; PERF002-B CLOSED with harness `224ce852f550a7d9126fad9f5923a9a2fd8194cc` and retained external evidence `c69248714a60dd62894164bf5332b55f780d6fe4` against exact Protos revision `3c93912a5579326374782a43527fbb51046f8f91`. Semantic smoke, canonical 11x2 interpreter/Truffle correctness, known bailout/runtime guards, and 10/10 polymorphic-dispatch stability PASS at `-Xss128m`; no timing results were published. |
| PERF003 | Collection algorithm Truffle compilability | CLOSED | `guillermomolina/protos-benchmarks@433ebb8075148493d4eae8da701803b21ab50c09` | PERF003-A/B closed by final A4 reconciliation. Correctness remains PASS; the deterministic residual GraphTooBig threshold is characterized and accepted without a broad production Truffle boundary or further threshold-shaving edits. No implementation/specification/version change. |
| PERF004 | Cross-language runtime performance characterization | OPEN | — | Intentionally planned but not started. A: correctness-first cross-language baseline; B: attribution of material gaps; C: evidence-backed optimization priorities. Reuses PERF001 methodology and PERF003 lessons; no optimization is authorized by opening this item. |


### PERF003 — Collection algorithm Truffle compilability

Status: IN_PROGRESS

Originating baseline:
- `guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565`;
- harness `280173d743b2ed838a89be0ad930b20828d89558`;
- Protos corpus `86b35d8bb2d7ab2ad54bc2947e1bf7fbff1fca15`;
- `array-reduce`: `rc=0`, `opt_done=236`, `opt_failed=40`, `GraphTooBig=40`;
- `array-sort`: `rc=0`, `opt_done=650`, `opt_failed=40`, `GraphTooBig=40`.

| Slice | Status | Version | Closure evidence | Scope / unblock condition |
|---|---|---|---|---|
| PERF003-A | IN_PROGRESS | `0.2.180-SNAPSHOT` | `THIRD_CORRECTIVE_IMPLEMENTATION_SAME_COMMIT` | Valid external evidence against `5404667964dec84b8b8de2ff8dbe7923a5d1dd2e` shows reduce correctness PASS and 2 remaining `GraphTooBig` failures at graph size 150036 / limit 150000. This third corrective phase removes redundant `hasInitial`/mutable `startIndex` state and derives the balanced strict-left-fold start from zero-or-one `initialSize`; exact external no-GraphTooBig validation against this new commit is still required. |
| PERF003-B | BLOCKED_BY_DEPENDENCIES | — | — | After A, companion correctness-first external validation against the exact optimized Protos revision, retaining pre/post evidence and diagnostics without rewriting PERF001-E baseline evidence. |

Project record: `docs/project/PERF003_COLLECTION_COMPILABILITY.md`.

### PERF002 — Truffle compilability and dispatch optimization

Status: CLOSED

Purpose: Preserve the existing callable, extraction, receiver, `methodHome` and
task-continuation semantics while improving optimizing-Truffle compilability,
without making the Protos repository depend on the external benchmark runtime.

| Slice | Status | Version | Closure evidence | Implemented surface |
|---|---|---|---|---|
| PERF002-A | CLOSED | `0.2.162-SNAPSHOT` | `SAME_COMMIT` | Sequence/argument-vector compiler structure, ordinary evaluator fast path, immediate selected-method activation metadata, extracted-method materialization boundary, Protos-source semantic conformance, full Maven/package/license validation. |
| PERF002-B | CLOSED | — | `guillermomolina/protos-benchmarks@c69248714a60dd62894164bf5332b55f780d6fe4` | Exact harness `224ce852f550a7d9126fad9f5923a9a2fd8194cc` validates Protos `3c93912a5579326374782a43527fbb51046f8f91` with GraalVM Community JDK 22, external `truffle-runtime:24.0.0`, semantic smoke PASS, canonical 11x2 correctness PASS, known bailout/runtime guards PASS, and 10/10 polymorphic-dispatch Truffle stability PASS at `-Xss128m`; no timing results published. |

PERF002-A invariants:
- fixed child arrays expose compilation-constant structure to Truffle;
- active cooperative task segments retain the evaluator bridge and interpreter
  transfer;
- immediate selected-method calls preserve original receiver and physical
  `methodHome` without manufacturing an unobservable extraction;
- actual Closure-valued member reads still create a fresh receiver-bound Closure;
- no direct Closure-type `call` bypass exists, so ordinary lookup/shadowing stays
  intact;
- Protos remains independently buildable/testable/publishable with no Docker,
  GraalVM or `protos-benchmarks` dependency.

Project record: `docs/project/PERF002_TRUFFLE_COMPILABILITY.md`.

Closure evidence:
- PERF002-A implementation commit: `3c93912a5579326374782a43527fbb51046f8f91`;
- PERF002-B harness commit: `guillermomolina/protos-benchmarks@224ce852f550a7d9126fad9f5923a9a2fd8194cc`;
- PERF002-B evidence commit: `guillermomolina/protos-benchmarks@c69248714a60dd62894164bf5332b55f780d6fe4`;
- optimizing runtime: `HotSpotTruffleRuntime`, external Truffle `24.0.0`, `-Xss128m`;
- validation: semantic smoke PASS, canonical 11x2 correctness PASS, known bailout/runtime guards PASS, polymorphic stability 10/10 PASS, `opt_done=238`, `opt_failed=0`.

### PERF001 — Core v0.1 baseline benchmark suite

Status: IN_PROGRESS

Project-side benchmark methodology and repository ownership are recorded in
`docs/project/PERF001_BENCHMARKING.md`. The canonical Protos workload corpus
remains under `protos/benchmarks/`; the future companion benchmark repository
owns execution infrastructure and cross-language evidence rather than PERF
lifecycle state.

| Slice | Status | Closure evidence | Scope / unblock condition |
|---|---|---|---|
| PERF001-A | CLOSED | `SAME_COMMIT` | Publish the Protos-side benchmark contract: ownership split, correctness gate, cross-language equivalence, startup/warmup/steady-state measurement classes, Docker timing boundary, reproducibility metadata, baseline-vs-optimization rule, and canonical corpus handoff. |
| PERF001-B | CLOSED | `guillermomolina/protos-benchmarks@4e809acd839a0193250140c5b6dde48051d9aa9a` | Companion Docker harness published and validated while consuming pinned Protos revision `509b09562233b925d8414ce9a65196efd08da472`; runtime definitions, host/runtime inventory capture, CPU-affinity/resource policy, raw-result schema, and correctness-gated Protos/Python smoke are present. No timing results are published by this slice. |
| PERF001-C | CLOSED | `guillermomolina/protos-benchmarks@2da26df49f9b0673c56a9150a2d2f8cfc4a77c17` | Published and validated algorithm-equivalent Protos/Python/JavaScript coverage for all 11 existing canonical micro/runtime/algorithm workloads at pinned Protos revision `42b8264a36254dafbd97d80f5181790e28b9de12`; 33 correctness cases pass, Protos records `-Xss64m`, Python records recursion limit 50000, Node records `--stack-size=32768`, and no timing results are published by this slice. |
| PERF001-D | CLOSED | `guillermomolina/protos-benchmarks@52b083cef5f8726f73be869c56f3cd2933e919ab` | Published reference Protos startup/warmup/steady evidence from exact harness `0a406373c497df1173ff26a3ed4fcada015e0879`: 11 canonical workloads × interpreter/Truffle × pre/post-PERF002 revisions `8f363d0146164f99e72210eb44667f4efb7b88e7` / `3c93912a5579326374782a43527fbb51046f8f91`, with 10 startup samples, 20 retained warmup iterations, 20 steady-state samples, raw/environment identity, and separate non-timing diagnostics. Post-PERF002 diagnostics have zero optimization failures and zero known bailout/runtime failures. |
| PERF001-E | CLOSED | `guillermomolina/protos-benchmarks@4bff9f7f6c5e0e006530f166c188e0e988acf565` | Exact harness `280173d743b2ed838a89be0ad930b20828d89558` consumes Protos corpus `86b35d8bb2d7ab2ad54bc2947e1bf7fbff1fca15`; 18/18 cross-language correctness PASS and 54 startup/warmup/steady result records are retained. Non-timing diagnostics preserve two correct-execution optimization findings: `array-reduce` and `array-sort` each record 40 `GraphTooBig` failures. Baseline evidence remains unchanged; optimization is routed to PERF003. |
| PERF001-F | BLOCKED_BY_DEPENDENCIES | — | After B and a focused concurrency audit, add Future/P/Actor measurements with explicit CPU-set and scheduling methodology. |
| PERF001-G | BLOCKED_BY_DEPENDENCIES | — | Final reproducibility run and baseline report; complete-Core-v0.1 labelling additionally requires I015 CLOSED. |

Dependency outline: `PERF001-A -> PERF001-B -> PERF001-C -> PERF001-D/E`; PERF001-F depends on PERF001-B plus its focused concurrency audit; PERF001-G requires all required preceding PERF001 slices.

New Performance work MUST allocate and persist its `PERFxxx` identifier in the
repository when formally introduced rather than relying on chat/prompt history.

## Distribution and release engineering

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| DIST001 | End-user distribution and release engineering | CLOSED | `SAME_COMMIT` | First public pre-release `v0.2.236` is published from exact candidate `957b1e16793a682de1d6406e37b5734c44d32d19`; public ZIP `protos-0.2.236-posix-jvm.zip` SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`, checksum and manifest were independently downloaded/verified. DIST001-E is CLOSED. |
| DIST001-A | Relocatable portable distribution layout and runtime contract | CLOSED | `SAME_COMMIT` | Constructible POSIX/JVM ZIP; shared checkout/distribution launcher preserves caller CWD through `PROTOS_HOME`; exact source/runtime metadata and checksums; initial supported optimizing stack is GraalVM Community JDK 22 + external `truffle-runtime:24.0.0`; no tag/release. |
| DIST001-B | Extracted-distribution smoke/conformance | CLOSED | DIST001-B1/B2/B3/B4A/B4B/B5 published | One exact clean-source ZIP passes archive/checksum identity, outside-checkout caller-CWD + Package Tool, bundled Test Tool, and exact GraalVM Community JDK22/Truffle24 `HotSpotTruffleRuntime` evidence through the composed B5 gate. |
| DIST001-B1 | Validation hygiene and bounded smoke decomposition | CLOSED | `SAME_COMMIT` | Ignore Python bytecode/cache outputs and persist the bounded B1..B5 validation plan; no executable distribution behavior changes. |
| DIST001-B2 | Clean-source archive identity | CLOSED | `SAME_COMMIT` | Rebuild the A archive from the exact clean committed candidate; direct ZIP verification proves CRC/single-root safety, exact `SOURCE.txt` HEAD identity with `source_dirty=false`, and complete SHA-256 coverage/value integrity for every distributed file except `SHA256SUMS` itself. |
| DIST001-B3 | Outside-checkout CWD and Package Tool smoke | CLOSED | `SAME_COMMIT` | Extract the B2-validated ZIP outside the checkout into a dedicated toolchain tree and use a distinct caller project CWD for a relative Protos source plus public `protos package manifest`. A host outside the selected JDK22 contract disables optimizer JARs only in the disposable extraction and uses the explicit fallback/override path; B4 retains exact optimizing-runtime ownership. |
| DIST001-B4 | Bundled Test Tool and optimizing-runtime probe | CLOSED | DIST001-B4A + DIST001-B4B published | B4A proves extracted Test Tool portability; B4B preserves optimizer JARs intact and proves exact GraalVM Community JDK 22.0.0 + Truffle 24.0.0 resolves `com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime`. |
| DIST001-B4A | Extracted bundled Test Tool smoke | CLOSED | `SAME_COMMIT` | Extract the validated distribution outside the checkout and run public `protos test`; on a non-selected validation JDK isolate optimizer JARs only in the disposable copy and require fallback/override diagnostics. |
| DIST001-B4B | Exact optimizing-runtime probe | CLOSED | `SAME_COMMIT` | Exact GraalVM Community JDK 22.0.0 passes the extracted launcher supported-runtime gate with no override; the intact distribution Truffle 24.0.0 classpath resolves exact `com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime`. |
| DIST001-B5 | Cross-slice distribution closure | CLOSED | `SAME_COMMIT` | `dist/validate_portable.sh` composes B2/B3/B4A/B4B against one exact archive and proves the archive SHA-256 is unchanged across validation; parent B closes and D becomes READY. |
| DIST001-C | Release selection and publication policy | CLOSED | `SAME_COMMIT` | Non-normative policy in `docs/project/DIST001_RELEASE_POLICY.md`; implementation snapshots are not releases; selected public releases may skip internal versions. |
| DIST001-D | CI snapshot artifact | CLOSED | DIST001-D1 + DIST001-D2A + DIST001-D2B published | Workflow definition, portable external checksum, and observed real artifact are closed. Run 34101588533 for 994429173b6ec0fc086f307f4a49815f219c6523 completed green and artifact 10010752033 verified after download. |
| DIST001-D1 | CI snapshot workflow definition | CLOSED | `SAME_COMMIT` | Add a `main`/manual GitHub Actions workflow using exact GraalVM Community JDK 22.0.0, full Maven suite, clean portable build, complete B5 gate, outer SHA-256, and `actions/upload-artifact@v7`; no tag/release. |
| DIST001-D2 | Observed CI snapshot artifact closure | CLOSED | DIST001-D2A + DIST001-D2B published | Repaired run 34101588533 for exact source 994429173b6ec0fc086f307f4a49815f219c6523 completed green; artifact 10010752033 contained the expected portable ZIP and basename-only `.sha256`, which passed independent `sha256sum -c` after download. |
| DIST001-D2A | Portable external snapshot checksum repair | CLOSED | `SAME_COMMIT` | Generate `.sha256` from the archive directory so it records only the ZIP basename; require `sha256sum -c` to pass in CI before upload. The first D1 artifact digest value itself was correct; only its absolute runner pathname was non-portable. |
| DIST001-D2B | Observed repaired CI snapshot artifact closure | CLOSED | `SAME_COMMIT` | Run 34101588533; source `994429173b6ec0fc086f307f4a49815f219c6523`; artifact `protos-snapshot-994429173b6ec0fc086f307f4a49815f219c6523` / id `10010752033`; `protos-0.2.230-SNAPSHOT-posix-jvm.zip` SHA-256 `f66f011ba9a7c579f81b5aad7098bd4ec421ebc8117b3c4c8954374441e94337`; downloaded basename-only checksum verified successfully outside the runner workspace. |
| DIST001-E | First selected GitHub pre-release | CLOSED | `SAME_COMMIT` | E1-E6 complete. `Protos 0.2.236` / `v0.2.236` is a public non-draft pre-release from exact candidate `957b1e16793a682de1d6406e37b5734c44d32d19` with verified downloadable assets and runtime/source disclosures. |
| DIST001-E1 | First pre-release readiness and candidate envelope | CLOSED | `SAME_COMMIT` | A-D readiness is sufficient to begin bounded preparation; persist candidate eligibility, current limitation/claim boundary, E1-E6 decomposition, and explicit approval boundary. No candidate/version selected and no tag/release/assets published. |
| DIST001-E2 | Coherent public pre-release version contract | CLOSED | `SAME_COMMIT` | Selected generic mapping: development `V-SNAPSHOT` -> public `V`; tag `vV`; title `Protos V`; GitHub `prerelease=true`; candidate commit derives from selected baseline without converting active `main`; no concrete candidate/version selected. |
| DIST001-E3 | Release metadata, assets and validation preparation | CLOSED | DIST001-E3A/E3B/E3C published | Release-mode builder/provenance, deterministic metadata envelope, release-aware B5, independent envelope verification, and composed candidate gate are published; no concrete candidate/tag/Release selected. |
| DIST001-E3A | Release-mode builder and provenance/version guards | CLOSED | `SAME_COMMIT` | Default builder remains development-only `V-SNAPSHOT`; explicit public-prerelease mode requires clean public `V`, exact baseline SHA with `V-SNAPSHOT`, baseline ancestry, and emits baseline/candidate SOURCE provenance plus `public_release=true`. Generic fixtures only; no candidate selected. |
| DIST001-E3B | Release notes and asset/checksum manifest envelope | CLOSED | `SAME_COMMIT` | Generic public-prerelease ZIP metadata is rendered deterministically into RELEASE_NOTES.md, RELEASE_MANIFEST.txt and a basename-only archive `.sha256`; specification/capability/limitation claims are explicit inputs; no candidate selected. |
| DIST001-E3C | Candidate validation entry point and E3 closure | CLOSED | DIST001-E3C1/E3C2/E3C3 published | Release-aware B5, independent envelope verifier, and composed candidate/tag/claim-audit guards are complete. |
| DIST001-E3C1 | Release-aware B2/B5 identity and conformance plumbing | CLOSED | `SAME_COMMIT` | Development `V-SNAPSHOT` remains default; explicit public-prerelease verification requires clean public `V`, exact candidate HEAD and `V-SNAPSHOT` baseline provenance before the unchanged B3/B4A/B4B checks run against the same ZIP. Generic fixtures only; no candidate selected. |
| DIST001-E3C2 | Independent release-envelope verifier | CLOSED | `SAME_COMMIT` | Verify exact E3B envelope file set, manifest identity/schema, SOURCE/RUNTIME provenance, archive/checksum/notes digests, basename-only checksum content and populated capability/limitation sections; generic corruption fixtures only, no candidate/publication. |
| DIST001-E3C3 | Candidate gate composition and E3 closure | CLOSED | `SAME_COMMIT` | Compose candidate checkout identity, E3C2 envelope verification, explicit user-selection/claim audit, current spec revision, local/origin tag availability and E3C1 B5; candidate audit must keep release publication unauthorized. |
| DIST001-E4 | Exact candidate selection and immutable validation | CLOSED | `SAME_COMMIT` | E4A/B/C/D complete for exact candidate `957b1e16793a682de1d6406e37b5734c44d32d19`. D1-D3 retained evidence plus direct-publication D4-D6 evidence prove collision guard, final candidate Maven/envelope verification and frozen asset identity. |
| DIST001-E4A | Exact first-prerelease selection freeze | CLOSED | `SAME_COMMIT` | Explicit user decision freezes baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` / `0.2.236-SNAPSHOT` -> `0.2.236`, tag `v0.2.236`, spec `0.1.382`; I023/B007 closed; candidate SHA unmaterialized and publication unauthorized. |
| DIST001-E4B | Detached candidate materialization | CLOSED | `SAME_COMMIT` | E4B1/B2/B3/B4 complete. Candidate `957b1e16793a682de1d6406e37b5734c44d32d19` has exact parent `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`, only root `0.2.236-SNAPSHOT` -> `0.2.236` POM transition, clean detached local-only worktree reachability, and no tag/release publication. |
| DIST001-E4B1 | Selected-baseline detached-worktree guard | CLOSED | `SAME_COMMIT` | Publish and test a fail-closed detached worktree primitive bound to E4A selection `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`; new destination outside main, exact clean detached baseline HEAD/version, no branch-ref change; no real candidate mutation/publication. |
| DIST001-E4B2 | Exact candidate POM version transition | CLOSED | `SAME_COMMIT` | Publish/test exact root project `0.2.236-SNAPSHOT` -> `0.2.236` mutation over an E4B1 detached baseline worktree; only pom.xml may become dirty, nothing is staged/committed, and no real candidate is materialized by this slice. |
| DIST001-E4B3 | Detached candidate commit creation | CLOSED | `SAME_COMMIT` | E4B3A commit primitive plus E4B3B composition/real materialization complete. Exact detached candidate `957b1e16793a682de1d6406e37b5734c44d32d19` has parent `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` and persisted public identity `0.2.236`; no branch/tag/remote publication. |
| DIST001-E4B3A | Candidate-commit primitive + guards | CLOSED | `SAME_COMMIT` | Publish/test detached commit creation from exact E4B2 input: stage only pom.xml, parent exactly `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`, release-only diff, clean detached result, unchanged branch/tag refs, no push/tag/release. Fixture candidates only. |
| DIST001-E4B3B | Real detached candidate materialization | CLOSED | `SAME_COMMIT` | B3B1 composition/recovery guard plus B3B2 real materialization complete; exact candidate `957b1e16793a682de1d6406e37b5734c44d32d19` is persisted and remains locally reachable only through its detached worktree. |
| DIST001-E4B3B1 | Candidate materialization composition + recovery/idempotency guard | CLOSED | `SAME_COMMIT` | Publish/test B1 -> B2 -> B3A composition with exact B1/B2 resume, exact candidate reuse, incomplete-state cleanup, detached local reachability and no branch/tag/push/release. Fixture candidates only. |
| DIST001-E4B3B2 | Real candidate creation + SHA persistence | CLOSED | `SAME_COMMIT` | Materialized exact candidate `957b1e16793a682de1d6406e37b5734c44d32d19` from frozen baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` with public version `0.2.236`, ran candidate validation before persistence, and retained detached local reachability; no branch/tag/GitHub Release/assets. |
| DIST001-E4B4 | Release-only candidate lineage verification | CLOSED | `SAME_COMMIT` | Independent verifier proves `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047` -> `957b1e16793a682de1d6406e37b5734c44d32d19` single-parent lineage, exact modified path `pom.xml`, byte-exact root `0.2.236-SNAPSHOT` -> `0.2.236` transition, clean detached registered worktree, no candidate refs, future tag availability and publication=false. |
| DIST001-E4C | Candidate archive/envelope/audit preparation | CLOSED | `SAME_COMMIT` | E4C1-C5 complete: archive `protos-0.2.236-posix-jvm.zip` / `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`, frozen claims `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33`, deterministic E3C2-verified envelope, and exact E3C3 candidate audit `0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b` with publication=false. |
| DIST001-E4C1 | Public-prerelease portable ZIP build | CLOSED | `SAME_COMMIT` | Built `protos-0.2.236-posix-jvm.zip` from clean detached candidate `957b1e16793a682de1d6406e37b5734c44d32d19` with release baseline `3c23eaaccecbdcc7c2bcd86bc30c445403cfb047`; SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` persisted in `DIST001_E4_CANDIDATE_ARTIFACT.txt`. Independent identity verification remains E4C2. |
| DIST001-E4C2 | Archive identity + SOURCE/RUNTIME verification | CLOSED | `SAME_COMMIT` | Independently verified persisted `protos-0.2.236-posix-jvm.zip` SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`: single root/CRC, exact candidate `957b1e16793a682de1d6406e37b5734c44d32d19` SOURCE identity, exact GraalVM JDK22/Truffle 24.0.0 runtime metadata, complete internal checksums and JAR `Implementation-Version: 0.2.236`. No regeneration/publication. |
| DIST001-E4C3 | Release-note claims selection | CLOSED | `SAME_COMMIT` | Freeze ordered claims for candidate `957b1e16793a682de1d6406e37b5734c44d32d19` / archive `protos-0.2.236-posix-jvm.zip`: portable CLI/Core/concurrency/standard-library capabilities; experimental/draft runtime, Package Tool, Test Tool and documentation limitations; candidate B001-B008 blocker audit PASS. |
| DIST001-E4C4 | Deterministic release-envelope generation | CLOSED | `SAME_COMMIT` | Generated byte-identical E3B envelopes twice from frozen claims SHA-256 `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33` and verified the retained envelope against `protos-0.2.236-posix-jvm.zip` / `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` with E3C2. Notes `98684922feebb1cec41da2a51fca6776cd76aa5c99561b37adb733e5b944ed36`, manifest `2d71e48bf27e52d76bd4bd9166dca4298487532b1de08e832758c7f2abe7dcd4`, checksum file `34b2d9a86c0e136ac2f9d92c8edf94563b9ea941c896f54eddce929680969035`; no audit/publication. |
| DIST001-E4C5 | Candidate-audit materialization | CLOSED | `SAME_COMMIT` | Materialized exact E3C3 audit for `957b1e16793a682de1d6406e37b5734c44d32d19` in main and separate candidate-local audit state; SHA-256 `0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b`, selection authorized, claims/blockers reviews PASS, publication=false. Full candidate/B5 validation remains E4D. |
| DIST001-E4D | Immutable full candidate validation and E4 closure | CLOSED | `SAME_COMMIT` | D1-D6 complete; D4-D6 are reconciled from the successful direct publication transaction and independent public verification rather than rerun against moving main. |
| DIST001-E4D1 | Envelope/audit/record consistency | CLOSED | `SAME_COMMIT` | Independent bundle verifier cross-checks exact selection/artifact/claims/envelope/audit identity and candidate-local archive/envelope/audit bytes for `957b1e16793a682de1d6406e37b5734c44d32d19`; clean detached worktree, exact three-file envelope, basename-only checksum and main/local audit byte identity all PASS. |
| DIST001-E4D2 | Extracted release-aware B5 candidate gate | CLOSED | `SAME_COMMIT` | Exact candidate `957b1e16793a682de1d6406e37b5734c44d32d19` / `protos-0.2.236-posix-jvm.zip` SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296` passes E3C1 release-aware B5 with public-prerelease + clean-source identity, outside-checkout Package Tool and Test Tool smokes, exact optimizing GraalVM JDK22/Truffle24 runtime gate, and unchanged archive bytes. |
| DIST001-E4D3 | Candidate claims/blockers/spec audit | CLOSED | `SAME_COMMIT` | Independent candidate-time audit proves exact frozen claims SHA-256 `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33` (4 capabilities / 5 limitations), B001-B008 all CLOSED, newest global specification revision exactly `0.1.382`, relevant normative owners remain v0.1 Draft, and all claims are supported by candidate-only evidence. |
| DIST001-E4D4 | Tag/Release collision + publication guard | CLOSED | `SAME_COMMIT` | Immediately before publication `v0.2.236` and its GitHub Release were absent; explicit user authorization then permitted creation. Published tag identity was independently rechecked against `957b1e16793a682de1d6406e37b5734c44d32d19`. |
| DIST001-E4D5 | Full Maven + cross-E3/E4 validation | CLOSED | `SAME_COMMIT` | Direct publication transaction ran the final candidate `mvn test` on cached GraalVM JDK22 and independent release-envelope verification over the frozen `protos-0.2.236-posix-jvm.zip` SHA-256 `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`; both PASS. |
| DIST001-E4D6 | Freeze candidate SHA/assets + close E4 | CLOSED | `SAME_COMMIT` | Final release identity frozen as candidate `957b1e16793a682de1d6406e37b5734c44d32d19`, archive `protos-0.2.236-posix-jvm.zip` / `b1a58ba445d082156bd4eb637ee6df70c046abdee600d468c0fac29be065e296`, claims `ad0b77ae5bd41bc16e306487072620c99581643d68ed5141011e7537c0439e33`, notes `98684922feebb1cec41da2a51fca6776cd76aa5c99561b37adb733e5b944ed36`, manifest `2d71e48bf27e52d76bd4bd9166dca4298487532b1de08e832758c7f2abe7dcd4`, checksum-file `34b2d9a86c0e136ac2f9d92c8edf94563b9ea941c896f54eddce929680969035` and audit `0f3ea9a321a462e977f4f33a2b4c24754a5cacbd8e45e0df8bc6639d4286692b`. |
| DIST001-E5 | First GitHub pre-release publication | CLOSED | `SAME_COMMIT` | Explicitly authorized publication created lightweight tag `v0.2.236` at `957b1e16793a682de1d6406e37b5734c44d32d19` and GitHub pre-release `Protos 0.2.236` with ZIP, basename-only `.sha256` and `RELEASE_MANIFEST.txt` assets. |
| DIST001-E6 | Published pre-release verification and DIST001 closure | CLOSED | `SAME_COMMIT` | Public tag resolves exactly to `957b1e16793a682de1d6406e37b5734c44d32d19`; Release metadata is non-draft/prerelease; re-downloaded ZIP/checksum/manifest match frozen SHA-256 values. DIST001 CLOSED. |
| DIST002 | Development/release toolchain alignment | CLOSED | DIST002-A `b66e94ca31bd83147f2c0e39a268d5de9323bf14`; DIST002-B `SAME_COMMIT`; DIST002-C `SAME_COMMIT`; DIST002-D1 `SAME_COMMIT`; DIST002-D2 `SAME_COMMIT`; DIST002-D3 `SAME_COMMIT`; DIST002-D `SAME_COMMIT` | Deferred follow-up, intentionally not a dependency of the frozen DIST001 0.2.236 candidate. Centralize Java bytecode/GraalVM/JDK/Truffle/Maven coordinates; align devcontainer + ordinary CI + release validation; pre-provision the primary runtime so normal gates do not download JDKs on demand; make future runtime migrations explicit. See `docs/project/DIST002_TOOLCHAIN_ALIGNMENT.md`. DIST002-A CLOSED after explicit owner selection: root `toolchain.json` is the canonical Java21/GraalVM25.3.4.1-JDK25.0.4.1/Truffle25.3.4.1/Maven3.9.9 contract; DIST002-B READY. See `docs/project/DIST002_TOOLCHAIN_ALIGNMENT.md`. DIST002-B CLOSED on the canonical JDK25/Maven3.9.9 development/ordinary-CI environment; DIST002-C READY. DIST002-C CLOSED: live post-DIST001 dependency/distribution bindings are zero-drift on JDK25.0.4.1 + Graal/Truffle25.3.4.1; DIST002-D READY. DIST002-D1 closes the observed Maven digest-only SHA-512 CI bootstrap defect; D remains READY pending green post-D1 primary workflow evidence. DIST002-D2 closes the observed EOF-without-newline shell-read defect; D remains READY pending green post-D2 primary workflow evidence. DIST002-D3 closes the observed Actions safe-directory HOME defect after a green Tests run and a distribution run that reached the builder; D remains READY pending green post-D3 primary workflow evidence. Final DIST002-D closure records green post-D3 Tests run 34224766060 and Distribution snapshot run 34224766104 on revision `6977f06441624267147b1f146fac340d06f464c3`, plus the final local zero-drift/full-test/portable-distribution gate; DIST002 CLOSED. |
| DIST002-A | Canonical toolchain coordinate authority + drift audit | CLOSED | `SAME_COMMIT` | Explicit owner selection persisted in root `toolchain.json`: Java 21 bytecode; GraalVM Community 25.3.4.1 / JDK 25.0.4.1 (`25i3`); Graal/Truffle 25.3.4.1; Maven 3.9.9. Tested static drift auditor published; historical JDK22 and isolated IGV/JDK17 evidence preserved. |
| DIST002-B | Align devcontainer + ordinary CI primary runtime | CLOSED | `SAME_COMMIT` | Existing devcontainer already matched the A contract. Ordinary Tests CI now runs inside the exact primary GraalVM image, bootstraps checksum-verified Maven 3.9.9, verifies actual GraalVM/JDK/Maven identity, and requires zero development-scope toolchain drift before the full suite. |
| DIST002-C | Align distribution/runtime metadata + validation | CLOSED | `SAME_COMMIT` | `0.2.257-SNAPSHOT`; root/runtime Graal-Truffle25.3.4.1, exact JDK25.0.4.1 distribution metadata/launcher gate, primary-image distribution CI, B4B/B5 optimizer validation, and zero static toolchain drift published. Historical DIST001 release evidence unchanged. |
| DIST002-D1 | CI bootstrap checksum portability correction | CLOSED | `SAME_COMMIT` | Observed C runs Tests 34221407013 and Distribution snapshot 34221407000 failed before checkout because Apache Maven 3.9.9 publishes digest-only `.sha512`; both workflows now validate the exact 128-hex digest against the downloaded archive without weakening SHA-512 integrity. |
| DIST002-D2 | CI checksum metadata EOF correction | CLOSED | `SAME_COMMIT` | Post-D1 runs Tests 34222276908 and Distribution snapshot 34222276919 failed before checkout because Apache's exact 128-hex `.sha512` has no trailing newline and shell `read` returns non-zero at EOF under `set -e`; both workflows now load the exact body without weakening any digest validation. |
| DIST002-D3 | CI distribution workspace Git trust correction | CLOSED | `SAME_COMMIT` | Post-D2 revision `fab881f88b9bf89b36326bc21aeaba7257a73872`: Tests 34223295064 SUCCESS; Distribution snapshot 34223295074 passed bootstrap, zero-drift toolchain verification and 1096 tests, then failed only because builder `git status` lost checkout's temporary-HOME safe-directory trust. Distribution CI now trusts only `$GITHUB_WORKSPACE` in the real job HOME and proves the exact git-status probe before building. |
| DIST002-D | Cross-environment conformance + closure | CLOSED | `SAME_COMMIT` | Green post-D3 Tests run 34224766060 and Distribution snapshot run 34224766104 on revision `6977f06441624267147b1f146fac340d06f464c3`; final publication candidate revalidated exact JDK/Graal/Truffle/Maven identity, toolchain drift 0, full Maven suite, portable build and complete extracted-distribution gate. DIST002 CLOSED. |

DIST001-A/B/C/D and DIST001-E1/E2/E3 are closed. DIST001-E4 is READY for an
explicit exact development-baseline/public-version selection decision. The
generic candidate gate composes release-aware B5, independent envelope
verification, explicit candidate/claims audit, current specification identity
and tag-availability guards. No concrete baseline, candidate, public version,
tag, GitHub Release, or release asset is currently selected or authorized.

## P-label classification

`P57` and similar `Pnn` references found in historical conformance/changelog
text are specification/requirement paragraph labels, not a repository
project-work family analogous to `Ixxx`, `Bxxx`, `Dxxx`, `CLIxxx`, `LIBxxx`, `LMxxx`, or `PERFxxx`.

For example, the changelog records “P57 Integer conformance programs” as tests
covering the P57 Integer requirement. That evidence MUST NOT be auto-promoted
to a project-status item named `P057` or `P57`.

If a future project-work family named `Pxxx` is introduced, it must be declared
explicitly by a canonical project ledger; identifier resemblance alone is not
sufficient.

<!-- PROJECT-STATUS-HISTORICAL-RECONCILIATION: v6 -->

<!-- BEGIN AUTO-DISCOVERED WORK REGISTRY -->

## Formally tracked project work

This auto-generated registry complements, but does not duplicate, the curated implementation, CLI, Standard Library, Language Maturity, Toolchain Tool, and Performance tables above. It indexes other formal work families from authoritative project records and published specification decisions.

Identifier shape alone is insufficient: incidental IDs from design ideas, tests, benchmarks, examples, and arbitrary prose are intentionally excluded.

`RECORDED` means the owning project record contains the item without an explicit lifecycle state. Published decisions in `spec/PROTOS_SPEC_CHANGELOG.md` are `CLOSED`.

### B family

| Item | Title | Status | What it records / establishes | Owning source(s) |
|---|---|---|---|---|
| B001 | Empty Sequence execution | CLOSED | Implementation area: Truffle lowering / execution of a `CanonicalSequence` containing zero expressions. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B002 | Delegation parent of `without` / `alias` result objects | CLOSED | Implementation area: Standard `Object.without(name)` and `Object.alias(sourceName, aliasName)` message behavior and any runtime helper that constructs their result objects. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B003 | Delegation parent / lookup chain of canonical Boolean values | CLOSED | Implementation area: Standard prototype/delegation bridge for the canonical `true` and `false` runtime representations, including ordinary member lookup and polymorphic invocation through their delegation chains. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B004 | Public Group/GroupRef acquisition and discovery API | CLOSED | D039 defines and I011-21 implements the exact Core v0.1 `Actor.group(...) -> GroupRef` acquisition surface; portable service discovery remains outside Core v0.1. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B005 | `super` without a physical methodHome | CLOSED | D040 defines missing-`methodHome` `InvalidSuper` semantics and I020-D implements them. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B006 | Atomic package metadata replacement | CLOSED | D042 + closed I021 provide the general semantics/backend; package-tool Filesystem Slice 2B provisions confined staging-write/mutation authority and publishes metadata through standard File/Filesystem operations. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |
| B007 | Standard `while` protocol semantics | READY | D044 / spec `0.1.381` defines the complete observable standard Closure `while` protocol; I023 implementation/conformance remains required before closure. | `docs/project/IMPLEMENTATION_BLOCKERS.md` |

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
| D039 | Public ActorGroup acquisition | CLOSED | Defines `Actor.group(firstMember, additionalMembers...) -> GroupRef` as the only Core v0.1 direct Group acquisition surface, with explicit ActorRef initial membership, caller-Process ownership, communication-only GroupRef authority, and service discovery outside Core. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D040 | Dynamic super-dispatch context | CLOSED | Defines no-`methodHome` super execution as fresh `InvalidSuper` after ordinary argument-vector evaluation while preserving valid super lookup and `SlotNotFound` behavior. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D041 | Failure-atomic Filesystem namespace replacement/removal | CLOSED | Defines confined file-entry `Filesystem.replace`/`remove`, atomic visibility, commitment/cancellation/failure aftermath, stable open-File binding, and the explicit namespace-durability boundary. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D042 | Race-safe Filesystem namespace-entry selection | CLOSED | Corrects D041's ordinary-file-only preclassification: final components are selected as namespace entries without following them; unsupported atomic entry-kind combinations fail `IOError`; removal is non-recursive while D041's atomicity/cancellation/durability rules remain. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D043 | Standard Closure `ensure` protocol | CLOSED | Defines ordinary `Object.ensure` Closure ownership plus exact protected-extent cleanup, suspension, cancellation and later-transfer precedence semantics. | `spec/PROTOS_SPEC_CHANGELOG.md` |
| D044 | Standard Closure `while` protocol | CLOSED | Defines ordinary `Object.while` Closure ownership, strict pre-test Boolean loop semantics, canonical `null` completion and exact control/suspension/cancellation/Future composition. | `spec/PROTOS_SPEC_CHANGELOG.md` |

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

For Standard Library work, allocate the next unused `LIBxxx` identifier when the
work is formally introduced and record it in the curated Standard Library
section. Keep Core/runtime prerequisites under their applicable implementation
families rather than hiding them inside a library item.

For Performance work, allocate the next unused `PERFxxx` identifier when the
work is formally introduced and record it in the curated Performance section.
Keep performance work non-normative; semantic changes must be resolved through
their applicable specification/design and implementation owners first.

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
