# LM008-E — Control, Errors, Modules and Prelude surface audit

Status: IN_PROGRESS

Parent: `LM008 — Core Language Surface Completeness`

Durable coordination: GitHub Issue `#103`

Nature: non-normative audit/evidence record

## Scope decomposition

`LM008-E` remains one durable work item. For bounded execution it is audited in
four mechanical checkpoints that do not allocate new formal project identifiers:

- `E1` — standard Closure `while` and `ensure` control surface;
- `E2` — Core Error signaling, handler installation, identity and mandatory
  Error-prototype taxonomy;
- `E3` — module context, import, module identity/cache and isolation-visible
  surface;
- `E4` — required/forbidden Core prelude bindings plus final E reconciliation.

This decomposition selects no semantics. If a checkpoint exposes an unresolved
semantic or durable architecture choice, the affected row stops at the normal
Dxxx/PLATxxx approval gate. A reproducible implementation mismatch is routed to
its proper implementation owner rather than repaired inside LM008-E.

## E1 checkpoint — control

Checkpoint state: COMPLETE

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

Normative authority:

- `spec/semantics/EXECUTION_AND_CONTROL.md` for loop behavior, cleanup/unwind,
  result/control-transfer, suspension and cancellation composition;
- `spec/semantics/CALLABLES.md` for ordinary `Object.while` / `Object.ensure`
  slot placement, Closure-family receiver domain, extraction/shadowing and
  Closure activation;
- `spec/PROTOS_GRAMMAR.md` for ordinary call and trailing-Closure syntax only;
- `spec/semantics/ERRORS.md` where Error selection/propagation composes with
  `ensure` cleanup.

Implementation owners `I022 — Dynamic Error handlers / unwind-safe cleanup` and
`I023 — Standard while protocol` are CLOSED. E1 re-audits their current
language-visible surface; it does not reopen their design or implementation.

### Evidence matrix

| Surface row | Normative requirement | Retained language-level evidence | Current implementation/mechanism evidence | Classification |
|---|---|---|---|---|
| Standard selector placement and receiver domain | `while` and `ensure` are ordinary inherited `Object` messages whose standard behavior accepts only semantic Closure receivers; copying/extracting the standard behavior cannot confer Closure membership. | `control/while-invalid-receiver.protos`, `control/ensure-nonclosure-receiver.protos`, plus ordinary inherited invocation throughout the retained control corpus. | `ProtosStandardObjectProtocol` installs both selectors on root `Object` and validates `ProtosClosureValue` before entering the corresponding dynamic-control frame. | `COVERED` |
| `while` argument/domain/activation boundary | Standard `condition.while(body)` takes exactly one semantic Closure body; condition and body are activated directly as Closures with zero supplied arguments and no callback-arity preflight. | `control/while-arity-error.protos`, `while-eager-body-validation.protos`, `while-condition-binding-failure-not-preflighted.protos`, `while-unreachable-body-binding-failure-not-preflighted.protos`, `while-body-binding-failure-when-reached.protos`, `while-default-parameter-zero-arg-activation.protos`. | `ProtosStandardObjectProtocol.whileLoop` validates receiver/body family and supplied count, then invokes each Closure through the ordinary Closure invoker only when its semantic phase is reached. | `COVERED` |
| `while` pre-test order and normal result | Condition runs before each body activation; false terminates without body; body normal results are ignored; normal loop result is canonical `null`. | `control/while-zero-iterations-null.protos`, `while-multiple-iterations.protos`, `while-body-result-ignored.protos`. | While frame phases preserve condition-before-body ordering and return canonical `null` only from the false condition path. | `COVERED` |
| Strict condition result / no truthiness or implicit Future adoption | A normal condition result must be exactly canonical Boolean; `null`, Integer, ordinary object or Future is invalid and body is not run for that test. | `control/while-invalid-condition-null.protos`, `while-invalid-condition-integer.protos`, `while-invalid-condition-object.protos`, `while-invalid-condition-future.protos`. | Standard loop compares only against canonical Boolean singleton values and otherwise signals the ordinary standard Error. | `COVERED` |
| Loop Error and non-local-return propagation | Error, valid non-local return, InvalidReturn or another non-normal transfer from condition/body propagates unchanged; no later callback begins and effects are not rolled back. | `control/while-condition-error-transfer-preserves-identity.protos`, `while-body-error-transfer-preserves-identity.protos`, `while-condition-nonlocal-return-propagates.protos`, `while-body-nonlocal-return-propagates.protos`. | `whileLoop` removes the frame and rethrows control-transfer runtime objects without wrapping or retrying. | `COVERED` |
| Loop Future results and task ownership | Body Future results are ignored without observation/adoption/cancellation; work created by callbacks follows the existing task-scoped structured-ownership contract rather than a loop-created scope. | `control/while-body-failed-future-not-awaited.protos`, `while-body-future-not-cancelled.protos`, `while-body-future-task-scoped-ownership.protos`. | Loop machinery introduces no Future/task and uses ordinary callback activation/ownership. | `COVERED` |
| Loop suspension/replay exactness | Explicit condition/body suspension resumes at the same semantic point; completed callbacks are not replayed and repeated suspensions remain exactly-once per logical activation. | `control/while-condition-suspension-exact-once.protos`, `while-condition-every-iteration-suspends-exact-once.protos`, `while-body-every-iteration-suspends-exact-once.protos`, `while-condition-body-repeated-suspension-exact-once.protos`. | Replay-stable while frame phase and callback checkpoint compaction preserve logical iteration identity across evaluator suspension. | `COVERED` |
| Loop cancellation composition | `while` adds no polling/cancellation point; once ordinary cancellation is honored, normal unwind/`ensure` rules apply exactly once. | `control/while-cancellation-no-loop-poll-ensure-once.protos`. | Standard loop adds no cancellation check of its own and lets the existing task/control substrate perform unwind. | `COVERED` |
| `ensure` validation and protected-extent entry | `body.ensure(cleanup)` requires a Closure receiver, exactly one Closure cleanup and validates cleanup before executing body; no protected frame is entered on validation failure. | `control/ensure-nonclosure-receiver.protos`, `ensure-wrong-arity-zero.protos`, `ensure-wrong-arity-two.protos`, `ensure-cleanup-invokable-not-closure.protos`, `ensure-invalid-cleanup-before-body.protos`. | `ProtosStandardObjectProtocol.ensure` validates receiver/count/cleanup before entering the replay-stable ENSURE frame. | `COVERED` |
| `ensure` normal result / cleanup result | Cleanup runs once on normal exit; its normal result is ignored and the exact body result is preserved. | `control/ensure-cleanup-runs-normal.protos`, `ensure-normal-result.protos`, `ensure-exact-result-identity.protos`, `ensure-cleanup-result-ignored.protos`. | Ensure frame records the pending normal outcome before cleanup and restores that exact outcome after normal cleanup. | `COVERED` |
| Cleanup on Error/non-local return and LIFO nesting | Crossing ensure frames runs cleanup exactly once in innermost-first order for Error and non-local return as well as normal exit. | `control/ensure-error-cleanup-preserves-original.protos`, `ensure-body-nonlocal-return-runs-cleanup.protos`, `ensure-nested-lifo.protos`. | I022 dynamic-control frames retain replay-stable extent order and the standard ensure bridge records the pending transfer before cleanup. | `COVERED` |
| Cleanup transfer precedence / selected handler deactivation | A cleanup Error/non-local return that escapes supersedes the pending normal/error/return transfer; a handler selected for the original Error is inactive while crossed cleanup runs. | `control/ensure-cleanup-error-supersedes-normal.protos`, `ensure-cleanup-nonlocal-return-supersedes-normal.protos`, `ensure-cleanup-nonlocal-return-supersedes-pending-return.protos`, `ensure-selected-handler-inactive-during-cleanup.protos`. | Dynamic handler selection consumes the selected frame before unwind; ensure completion rethrows or replaces the recorded transfer according to the normative precedence. | `COVERED` |
| Ensure suspension/replay exactness | Suspension in body or cleanup preserves the one logical extent; cleanup and prior body effects are not duplicated, including nested cleanup. | `control/ensure-body-suspension-exact-once.protos`, `ensure-cleanup-suspension-exact-once.protos`, `ensure-cleanup-suspension-exact-result-identity.protos`, `ensure-error-cleanup-suspension-exact-error-identity.protos`, `ensure-nested-cleanup-suspension-lifo.protos`, `ensure-return-cleanup-suspension-preserves-return.protos`, `ensure-selected-handler-inactive-across-cleanup-suspension.protos`. | I022 replay-stable Ensure frame identity and explicit cleanup phase preserve pending outcome/control state across suspension. | `COVERED` |
| Cancellation cleanup, shielding and structured completion | Honored cancellation unwinds through ensure; cleanup runs once, is not interrupted by the already-honored request, and cleanup-created structured children reach the required terminal state before the transfer completes; later cleanup transfer can supersede cancellation. | `control/ensure-cancellation-sync-cleanup-runs.protos`, `ensure-cancellation-nested-lifo.protos`, `ensure-cancellation-suspended-cleanup-shields-request.protos`, `ensure-cancellation-cleanup-created-child-awaited.protos`, `ensure-cancellation-cleanup-created-child-drained.protos`, `ensure-cancellation-cleanup-error-supersedes.protos`, `ensure-cancellation-cleanup-return-supersedes.protos`, `ensure-cancellation-suspended-cleanup-error-supersedes.protos`, `ensure-cancellation-cleanup-error-child-structured-completion.protos`, `ensure-cancellation-cleanup-return-child-structured-completion.protos`. | Closed I022 cancellation/unwind integration owns cleanup shielding and structured-child drain without adding a second cancellation mechanism to `ensure`. | `COVERED` |

### E1 audit result

No E1 row requires a new Dxxx or PLATxxx decision, and no current implementation
or guest-publication mismatch is found. The retained central corpus already gives
direct ordinary-Protos evidence for every audited control row, so E1 adds no new
conformance program and does not duplicate I022/I023 regression families.

`LM008-E` remains `IN_PROGRESS`. E2 is next and audits `Error.signal`,
`Error.handle`, standard failure identity, dynamic task-local handler behavior and
the complete mandatory Core Error-prototype taxonomy.

## E2 checkpoint — Error

Checkpoint state: COMPLETE

Validation class: `TEST_IMPACT`

Normative authority: `spec/semantics/ERRORS.md`, with control-transfer and
Future/task composition delegated to their existing normative owners.

E2 audits the existing Error surface only. It introduces no new Error category,
matching rule, resumability mechanism, payload, syntax or implementation owner.

### Evidence matrix

| Surface row | Normative requirement | Retained language-level evidence | Current implementation/mechanism evidence | Classification |
|---|---|---|---|---|
| Standard `signal` / `handle` placement and receiver domain | `Error` provides ordinary inherited `signal()` and `handle(body, handler)`; their standard behavior accepts only `Error` itself or values whose delegation chain contains `Error`. Copying/extracting either implementation does not make a non-Error receiver signalable/handle-capable. | `error/copied-signal-nonerror-receiver.protos`, `error/copied-handle-nonerror-receiver.protos`, plus inherited use throughout the Error corpus. | `ProtosStandardErrorProtocol` installs both local selectors on the standard Error prototype and validates the receiver with `ProtosCoreErrors.isError`. | `COVERED` |
| Signaling identity, arity and non-resumability | `error.signal()` takes zero arguments, transfers the exact receiver object without cloning/wrapping, and never resumes the abandoned signaling continuation. | `error/signal-instance.protos`, `signal-prototype.protos`, `signal-invalid-return-instance.protos`, `signal-deep-io-prototype.protos`, `signal-wrong-arity.protos`, `handle-exact-error-identity.protos`, `handle-nonresumable-body.protos`. | `ProtosStandardErrorProtocol.signal` delegates to `ProtosCoreErrors.signal` with the exact validated receiver; the host transfer carries that same Error object. | `COVERED` |
| Handler argument validation and normal result | `matchPrototype.handle(body, handler)` requires exactly two semantic Closures, validates both before installing the frame or running body, and returns the exact normal body result without invoking the handler when no Error escapes. | `error/handle-body-invokable-not-closure.protos`, `handle-handler-invokable-not-closure.protos`, `handle-invalid-body.protos`, `handle-invalid-handler-before-body.protos`, `handle-wrong-arity.protos`, `handle-normal-result.protos`. | `ProtosStandardErrorProtocol.handle` validates receiver/count/body/handler before `enterHandlerFrame`, invokes body with zero supplied arguments and returns its normal result after leaving the frame. | `COVERED` |
| Delegation matching, dynamic innermost selection and selected-frame inactivity | Matching follows the signaled Error object's ordinary delegation chain; exact-instance matching is allowed; the dynamically innermost matching frame wins, a nonmatching frame propagates, and the selected frame is inactive while its handler executes. | `error/handle-error-ancestor-match.protos`, `handle-exact-instance-match.protos`, `handle-exact-prototype-match.protos`, `handle-innermost-matching.protos`, `handle-nonmatching-propagates.protos`, `handle-selected-inactive.protos`, plus E1 ensure/selected-handler interaction probes. | `ProtosDynamicControlState` selects the matching handler frame before unwind and `ProtosStandardErrorProtocol.handle` removes its selected frame before invoking the handler Closure. | `COVERED` |
| Standard failure freshness and recorded Error identity | A new standard failure occurrence has fresh identity; when semantics already names an Error object, re-signaling preserves that exact object; one recorded failed-Future outcome re-signals its recorded Error rather than manufacturing another occurrence. | `error/ordinary-error-construction-fresh.protos`, `regression/super-without-method-home-fresh-invalid-super.protos`, `future/failed-value-original-error-identity.protos`, `future/failed-value-repeated-error-identity.protos`, `future/failed-value-resignals-recorded-error.protos`, `future/cancelled-value-fresh-error.protos`. | `ProtosCoreErrors.newOccurrence` constructs a fresh ordinary child of the selected prototype while `signal` carries an already-existing Error unchanged; Future failure storage retains the recorded outcome under its own owner. | `COVERED` |
| Dynamic handler task locality / asynchronous boundary | Handler state belongs to the current task continuation, is not inherited by a distinct child Future/task, and a later observation of a failed Future re-signals into the consumer's then-current handler context. | `error/handle-parent-task-does-not-flow-into-child-future.protos`, `future/failed-value-resignals-recorded-error.protos`, together with retained I022 suspension/ensure interaction evidence. | I022's replay-stable `ProtosDynamicControlState` is task-local; separate tasks do not copy the handler stack, while later Future observation performs ordinary Error signaling in the observing activation. | `COVERED` |
| Mandatory Core Error taxonomy and direct parent topology | Core requires `Error -> Object`; all named non-I/O standard categories are direct `Error` children; `IOError -> Error`; and `InvalidIOArgument`, `IOLifecycleError`, `IOCapacityExhausted`, `EncodingError`, `LineTooLong` are direct `IOError` children. | Expanded `reflection/parent-error-taxonomy.protos` now checks all 21 mandatory direct-parent relations from ordinary Protos; the bindings are also exercised individually by domain-specific Error expectations across the corpus. | `error_taxonomy.protos` constructs the specified hierarchy; `InvalidReturn.protos` supplies its direct Error child; `prelude.protos` publishes every mandatory prototype; `ProtosCoreErrors.StandardError` retains the closed runtime construction map. | `COVERED` |

### E2 audit result

No E2 row exposes a new semantic/platform decision or production implementation
mismatch. Existing signaling, handler-selection and failure-identity conformance
already covers the behavioral contract. E2 strengthens only the previously
partial guest-visible taxonomy evidence by expanding the existing
`reflection/parent-error-taxonomy.protos` fixture to all mandatory direct-parent
relations.

`LM008-E` remains `IN_PROGRESS`. E3 is next and audits module context, import,
module identity/cache and isolation-visible module surface.
