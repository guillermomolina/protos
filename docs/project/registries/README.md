# Durable registries

`registries/` contains durable project registries and closure/evidence ledgers.
A registry is not a live scheduling or assignment surface: GitHub Issues and the
Protos Development Project own live work state.

DOC002-F5 migrates the legacy high-value registries in bounded sub-slices so
reference radius and authority can be checked independently:

- [`PLATFORM_ARCHITECTURE_DECISIONS.md`](PLATFORM_ARCHITECTURE_DECISIONS.md) —
  platform/runtime architecture decision registry, migrated by F5A;
- `IMPLEMENTATION_BLOCKERS.md` — normative-unblock-condition ledger, pending F5B;
- `IMPLEMENTATION_STATUS.md` — durable implementation/closure registry, pending F5C.

The latter two remain at their current legacy paths until their owning sub-slice
completes. Registry location never makes a file a live scheduling surface or a
normative language specification.
