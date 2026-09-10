# LM008-F — Core Language Surface Final Reconciliation

Status: CLOSED

Parent: `LM008 — Core Language Surface Completeness` / GitHub #50

Live work item: GitHub #104

## Purpose

LM008-F closes the already-bounded Core language-surface audit. It defines no
new language semantics and adds no new guest-visible implementation. Its job is
to reconcile the published LM008-A through LM008-E evidence, cross-check the
advanced concurrency and system/resource domains already dogfooded by LM005 and
LM006, account for every implementation finding, and require one final executable
publication gate.

## Direct LM008 surface reconciliation

| Front | Final state | Durable evidence | Reconciliation |
|---|---|---|---|
| LM008-A | CLOSED | parent LM008 inventory/methodology record | Established normative-owner inventory, classification vocabulary and evidence hierarchy; the pre-existing I025 parser/conformance repair is a covered baseline rather than an LM008-owned implementation change. |
| LM008-B | CLOSED | `e0d804aab36914cef13ecda890c8dbf3a0f1b032` | Grammar, evaluation, binding and callable audit B1-B4 is complete. Every positive row has retained Protos evidence and no `RUNNABLE_UNCOVERED`, `SPECIFIED_NOT_GUEST_VISIBLE` or `NEEDS_DESIGN_DECISION` row remains. |
| LM008-C | CLOSED | `ff0e152bf8a1284050f48e65eeecab9719bf6a75` | Object structural/reflection/mutation audit is complete after I031 published the four previously missing guest surfaces and their regressions. |
| LM008-D | CLOSED | `a775297514808728a5e22d2bc0e7e01f1d74b53c` | Values/Core-collections D1-D4 is complete after I032/I034/I035/I036 and I031 state coverage resolved the discovered implementation gaps; all D rows are reconciled `COVERED`. |
| LM008-E | CLOSED | `0076a76ab8a441df973931421e107811e6308c41` | Control, Error, modules/import and Prelude E1-E4 is complete; all 51 required Prelude bindings have executable lookup evidence and normative intentional absences remain focused rather than strengthened into new requirements. |

## Advanced-domain evidence cross-check

LM008 deliberately does not duplicate the integrated maturity suites that already
exercise the advanced Core domains.

| Existing maturity owner | State | Evidence reused by LM008-F |
|---|---|---|
| LM005 — Concurrent Language Maturity | CLOSED | Future creation/result identity, `then`, `Future.all`, cancellation; Actor identity/bootstrap/message transfer/FIFO/lifecycle; Group/GroupRef acquisition, identity, routing, transfer and terminated-member behavior. |
| LM006 — System & Resource Language Maturity | CLOSED | Bytes/Encoding; Path/File/Filesystem; Process and standard streams; resource lifetime with Error/`ensure`/Future; explicit-authority end-to-end system/resource composition and executable learning material. |

Those suites remain owned by LM005/LM006. F consumes their retained evidence as
the required cross-check; it does not create shadow tests, a second authority
model, or an LM008-specific concurrency/resource semantics layer.

## Implementation-finding accounting

All implementation work needed by the LM008 audit is already closed under its
proper owner:

- I025 — required-before-default Closure parameter parser/conformance alignment,
  already closed when LM008-A recorded its baseline;
- I031 / GitHub #242 — `Object.slotNames()`, `removeSlot(name)`, structural
  `close()` and structural `freeze()`; BUG004 was the bounded I/O-Closable
  compatibility fallout closed with I031-C;
- I032 / GitHub #265 — fixed-width numeric arithmetic publication;
- I034 / GitHub #272 — Array semantic-Integer indexing alignment;
- I035 / GitHub #273 — Map single-hash insertion alignment;
- I036 / GitHub #275 — stable Map/IdentityMap association snapshot publication.

No LM008 finding remains as `TRACKED_IMPLEMENTATION_GAP`. No unresolved
`RUNNABLE_UNCOVERED`, `SPECIFIED_NOT_GUEST_VISIBLE` or
`NEEDS_DESIGN_DECISION` classification remains. Intentional absences and
`DEFERRED_NOT_NORMATIVE` rows retain the boundaries established by their
normative owners; LM008 does not turn optional or future facilities into Core
requirements merely to make the matrix look exhaustive.

## Final publication gate

The LM008-F publication candidate must run the complete central Test Tool corpus
and the repository-selected publication validation on that same candidate. A
failure aborts publication and LM008 remains open; no test may be excluded or
expectation weakened merely to obtain closure.

The successful publication therefore closes both LM008-F and parent LM008. It
changes no normative specification, production/runtime implementation,
implementation version, public API, Core native boundary, source-style rule,
license term, or Dxxx/PLATxxx decision.
