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
| `LM007-B` | READY | Deep prototype chains, method extraction and mutation after capture/extraction, including repeated invocation and mixed inherited/local state. |
| `LM007-C` | READY | Error boundaries across delegated methods, lexical handler state, receiver state and collection callbacks. |
| `LM007-D` | READY | Long-form registries/pipelines with repeated state transitions and equality/identity-sensitive collection lookup. |
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
