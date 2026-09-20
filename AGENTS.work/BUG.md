# Protos defect and regression work

These instructions apply to `BUGxxx`.

Investigation contract: targeted and incremental by default. A `BUGxxx`
names a defect in behavior Protos already intends to provide. Read the owning
Issue/record, the directly governing specification sections for the intended
behavior, and the affected implementation and failing tests. Expand only when
diagnosis shows that the intended behavior is itself ambiguous,
contradictory, or unsettled — then route the semantic question through
`Dxxx`/`PLATxxx` (`AGENTS.work/DESIGN.md`), record the normative dependency
in the blocker ledger (`AGENTS.work/IMPLEMENTATION.md`), and block only the
affected bug work.

Apply the shared implementation discipline in `AGENTS.work/IMPLEMENTATION.md`
for validation, versioning, changelog, and publication.

## Defect and regression work

Confirmed or credibly reproducible defects/regressions in behavior that Protos
already intends to provide use the `BUGxxx` family when the defect has durable,
independently trackable project identity. `BUGxxx` names the defect, not a
particular attempted fix.

Appropriate `BUGxxx` scope includes implementation regressions against already
defined semantics, build/test/package/CI failures, broken maintained tooling,
and other failures where the expected project behavior is already sufficiently
determined to diagnose and repair without inventing a new contract.

Keep these boundaries explicit:

- `BUGxxx` MUST NOT define new observable language semantics, public API policy,
  or durable platform/runtime architecture merely to make a failure disappear;
- when diagnosis exposes an unresolved substantive semantic choice, block only
  the affected bug work and route the choice through the applicable `Dxxx`
  decision and explicit approval gate;
- when diagnosis exposes a durable host/runtime architecture choice, route it
  through `PLATxxx` rather than embedding it in the bug fix;
- a performance regression whose primary problem is performance engineering may
  remain `PERFxxx`; a correctness defect found during performance work may use a
  `BUGxxx` item when it has independent defect identity;
- feature work, planned capability expansion, ordinary implementation slices,
  refactoring without a defect, and speculative cleanup do not become
  `BUGxxx` merely because they change code;
- one bug may require multiple diagnostic or patch attempts without allocating a
  new top-level `BUGxxx`; create a separate bug only for an independently
  meaningful defect; and
- if investigation proves the reported behavior is only local checkout
  divergence, unsupported environment state, duplicate reporting, or otherwise
  not a repository defect, close the Issue with that evidence rather than
  manufacturing a code change.

New `BUGxxx` identifiers follow the normal collision-safe allocation policy,
carry `family:BUG`, use GitHub Issues/Project for live coordination, and obey the
same audit, validation, publication, changelog, versioning, and design-authority
rules as the affected project surface.
