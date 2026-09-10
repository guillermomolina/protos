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
