# LM007 — Object Model Maturity

Status: IN_PROGRESS

## Objective

LM007 dogfoods the already-defined Protos object model in integrated ordinary-Protos
programs. It does not define new language/runtime behavior. Its purpose is to make
individually closed semantics interact for long enough, and across enough state
transitions, that receiver, lookup, capture, failure and collection bugs become
observable.

LM007 is intentionally independent of LM006. LM006 owns system/resource maturity;
LM007 owns object-model interaction maturity and requires no unfinished LM006
resource surface.

## Semantic surfaces under stress

LM007 deliberately combines rather than isolates:

- single-parent delegation and prototype chains;
- local versus inherited slot lookup and local slot mutation;
- lexical closure capture;
- method receiver preservation, including inherited and extracted methods;
- `Error.handle`, signaling, handler result flow and captured handler state;
- `Array`, `Map` and `IdentityMap` as carriers of objects and Closures.

A reproducible mismatch against already-closed semantics is a defect/finding to
track under the proper owner. LM007 MUST NOT silently choose new semantics in a
test. A genuinely uncovered semantic choice must pass the normal Dxxx approval
gate before dependent maturity work continues.

## Test design rules

1. Prefer ordinary `.protos` programs in the central conformance manifest.
2. Make each maturity case cross several semantic boundaries; do not duplicate
   already-existing microtests merely under an LM007 path.
3. Mutate receiver-local state after method extraction or Closure creation where
   useful, so the program distinguishes receiver preservation from value
   snapshotting.
4. Pass receiver-preserving Closures through collections and retrieve them again
   before invocation.
5. Cross Error handler boundaries while preserving ordinary lexical and receiver
   state; handler fallbacks are part of normal program flow, not a host assertion.
6. Keep assertions in the existing Test Tool expectation layer. Do not add
   testing-only language syntax, privileged objects or Java policy.
7. Production runtime changes are outside a maturity slice. If a case exposes an
   implementation bug, split the fix to its proper implementation owner and keep
   the minimal reproducer as LM007 regression evidence after the fix closes.

## Slices

| Slice | State | Purpose |
|---|---|---|
| `LM007-A` | CLOSED | Integrated object workflows combining delegation, local slot mutation, nested/extracted receiver-preserving Closures, Error handling and Array/Map/IdentityMap transport. |
| `LM007-B` | CLOSED | Deep prototype chains, method extraction and mutation after capture/extraction, including repeated invocation, retained `super` lookup origin, post-capture local shadowing, ancestor mutation, and method-slot replacement. |
| `LM007-C` | CLOSED | Error boundaries across delegated methods and collection callbacks, including whole-operation unwind, repeated per-callback recovery, selected-handler deactivation/re-signal, exact Error identity and nested delegation-category matching. |
| `LM007-D` | CLOSED | Long-form registries/pipelines keep equality-keyed and identity-keyed state coherent across repeated insert/replace/remove/reinsert transitions, numeric family distinctions, exact Closure identities and stable custom equality/hash keys over mutable receivers. |
| `LM007-E` | READY | Reconcile findings, reduce failures to minimal regressions, document gaps and close LM007 only after the integrated invariants remain stable. |

## LM007-A coverage

| Program | Delegation / slots | Closures / receivers | Errors | Collections |
|---|---|---|---|---|
| `delegated-closure-collection-workflow.protos` | Three descendants with local factor/bias overrides and post-capture mutation | Nested jobs call inherited behavior on the original receiver | — | Array of jobs, mapped results, Map summary, reduction |
| `error-handled-delegated-pipeline.protos` | Validator descendants override limit/bonus | Tasks retain the validator receiver through a collection callback | Signaling + `Error.handle`; handler mutates captured fallback state | Array task pipeline, map/reduce, Map summary |
| `extracted-methods-through-collection.protos` | Multi-level delegation plus post-extraction local mutation | Extracted inherited methods and returned nested Closures retain their receivers | — | Array transports four callables; map/reduce executes them |
| `identity-map-receiver-registry.protos` | Three descendants mutate local state after registration | Registry stores receiver-preserving reader Closures | — | IdentityMap object keys plus Array map/reduce |

The four deterministic integer expectations are `118663`, `3171`, `72` and
`325` respectively.

## LM007-B coverage

| Program | Deep lookup / mutation pressure | Extraction / receiver pressure |
|---|---|---|
| `deep-extracted-super-chain-mutation.protos` | Four delegation edges plus receiver-local and two ancestor-local mutations after extraction | Extracted overriding method and returned nested Closure retain the leaf receiver and the level-2 `methodHome`; both repeatedly execute `super` after the creator invocation has returned |
| `sibling-extractions-repeated-rounds.protos` | Two sibling receivers share inherited behavior and ancestor offset, then diverge under receiver-local mutation; a later local slot is created on a descendant holder | Multiple extractions plus returned nested readers survive repeated Array transport/invocation; re-reading a stored Closure-valued member performs a fresh extraction for the holder receiver |
| `post-capture-deep-shadowing.protos` | A retained receiver observes inherited rate/bias, later local shadow creation, ancestor mutation, second local shadow creation and a mutation hidden by the new shadow | Nested Closure created by an inherited method remains receiver-bound throughout all post-capture lookup-topology changes |
| `method-slot-replacement-after-extraction.protos` | A three-level descendant observes later receiver-local value mutation after the prototype's method slot is replaced | The old extracted Closure keeps the originally selected implementation; fresh extractions see the replacement while all extracted values continue to use the descendant receiver |

Deterministic integer expectations are `2613462350255427`, `66155101`,
`245458606363`, and `6105011110231` respectively.

## LM007-C coverage

| Program | Error / unwind pressure | Receiver / lexical / collection pressure |
|---|---|---|
| `collection-unwind-preserves-receiver-state.protos` | Exact receiver-owned Error escapes the third Array reduce callback to one outer `Error.handle`; signaling continuation, later callback and post-collection continuation are abandoned | Delegated method mutations completed before signaling remain on the original receiver; handler mutates receiver recovery state plus captured lexical counters |
| `per-element-handler-lexical-state.protos` | Two occurrences of the same exact Error are independently handled by separately installed per-element handlers | Four Array callbacks share lexical fallback/handler counters while one delegated receiver separately tracks attempts, successes and failures |
| `handler-resignal-escapes-selected-handler.protos` | An inner `Error` handler catches the receiver Error and signals a replacement Error from its handler Closure; the selected inner frame is already inactive, so the still-active outer `Error` handler receives the exact replacement | The failure originates in an inherited method reached from an Array callback; pre-signal receiver state remains while the callback/collection continuations are abandoned |
| `delegated-error-category-selection-in-callbacks.protos` | Ordinary `ValidationError -> Error` and `FatalValidation -> ValidationError` categories prove nonmatching pass-through and dynamically innermost matching selection with exact caught identities | Nested handlers execute per Array callback around one delegated receiver whose success/recovery state spans all callbacks |

Deterministic integer expectations are `77321110`, `109422222`, `662111110`,
and `696532211` respectively.

## LM007-D coverage

| Program | Registry / transition pressure | Equality / identity / receiver pressure |
|---|---|---|
| `dual-index-entity-registry-transitions.protos` | One logical `Map` and one exact `IdentityMap` track three mutable delegated entities across replacement, remove/reinsert and five collection-driven transitions | Equal String logical keys intentionally replace routing while all three exact object identities remain independently addressable; stored receiver-bound readers observe later local mutation |
| `numeric-equality-identity-registry-transitions.protos` | Five staged actions repeatedly insert, replace, remove and reinsert ordinary Integer `1` and Float `1.0` | Normal `Map` follows numeric cross-family `==`/hash coherence and therefore holds one logical key; `IdentityMap` follows `===` and therefore retains the two numeric-family identities separately |
| `closure-identity-registry-lifecycle.protos` | A normal `Map` routes logical names to extracted callables while an `IdentityMap` separately records exact extracted Closure identities across alias replacement, fresh extraction and removal | Aliasing one extracted Closure preserves exact identity; independent extraction is fresh; all retained callables remain bound to the original receiver and observe later receiver mutation when invoked through the logical registry |
| `custom-equality-dual-map-live-state.protos` | Equal-but-distinct mutable entities move through logical replacement/removal/reinsertion while exact entries remain stable | `id` is the stable complete equality/hash key; `==` is introduced from the ordinary `equals` Closure through `alias("equals", "==")` while unrelated `state` mutates; `Map` collapses equal ids, `IdentityMap` preserves individual entities, and stored receiver-bound readers expose the current live state |

Deterministic integer expectations are `117079030032006238`,
`15040040300250010217`, `100030030006020111`, and
`66077066015042028028020311` respectively.

## Closure criteria

LM007 may close only when:

- all LM007 slices are closed or explicitly reconciled;
- each retained maturity program is owned by an existing observable semantic
  contract rather than a newly invented assumption;
- the central Test Tool conformance corpus and repository full test suite pass on
  the definitive publication candidate;
- discovered implementation defects have proper owners and retained regressions;
- no production specification, runtime API or implementation version is changed
  merely to make a maturity case convenient.
