# Cross-cutting implementation architecture

`architecture/` contains durable, non-normative implementation architecture that
is cross-cutting and is not primarily owned by one ordinary formal work item.
The existing `CORE_*` architecture records are the current representative class.

New records use this role only when that classification is clear. DOC002-F4
migrated the two legacy cross-cutting Core architecture records into this role
after execution-time reference and compatibility review.

## Current cross-cutting Core architecture

- [`CORE_BOOTSTRAP_ARCHITECTURE.md`](CORE_BOOTSTRAP_ARCHITECTURE.md) — non-normative Core bootstrap ownership and layering.
- [`CORE_NATIVE_BOUNDARY.md`](CORE_NATIVE_BOUNDARY.md) — evolving implementation-maintenance record for the audited Java-native Core boundary.

These records remain implementation architecture/maintenance documentation, not an alternate source of observable Protos semantics. Normative semantic authority remains under `spec/`.
