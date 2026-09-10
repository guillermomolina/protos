# Durable registries

`registries/` contains durable project registries and closure/evidence ledgers.
A registry is not a live scheduling or assignment surface: GitHub Issues and the
Protos Development Project own live work state.

DOC002-F5 migrates the legacy high-value registries in bounded sub-slices so
reference radius and authority can be checked independently:

- [`PLATFORM_ARCHITECTURE_DECISIONS.md`](PLATFORM_ARCHITECTURE_DECISIONS.md) —
  platform/runtime architecture decision registry, migrated by F5A;
- [`IMPLEMENTATION_BLOCKERS.md`](IMPLEMENTATION_BLOCKERS.md) — durable Bxxx
  normative-unblock-condition ledger, migrated by F5B; it is implementation
  state, not a normative specification;
- `IMPLEMENTATION_STATUS.md` — durable implementation/closure registry, pending F5C.

`IMPLEMENTATION_STATUS.md` remains at its current legacy path until F5C
completes. Registry location never makes a file a live scheduling surface or a
normative language specification.
