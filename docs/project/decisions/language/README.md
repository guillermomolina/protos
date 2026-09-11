# Language decision records

This role contains durable non-normative decision records whose primary
domain is observable language or specification semantics. These files record
decision process and rationale; they do not replace the normative specification
under `spec/`.

Many such records use the `Dxxx` family, but `Dxxx` does not mean
"language" by itself. New unambiguous language/specification decision records
use this directory. Implementation-independent tool/package decisions use
[`../tooling/`](../tooling/README.md), while host/runtime decisions use
[`../platform/`](../platform/README.md). DOC002-F1 completed the legacy flat
language-decision migration with link, role, compatibility, and authority
checks; new unambiguous language/specification decision records use this
directory directly.

## Migrated language-domain decisions

DOC002-F1 migrated the current ratified legacy language-domain set:

- [`D047_NETWORKING_DECISION.md`](D047_NETWORKING_DECISION.md)
- [`D048_IP_ADDRESS_ENDPOINT_CONSTRUCTION.md`](D048_IP_ADDRESS_ENDPOINT_CONSTRUCTION.md)
- [`D049_SHARED_STANDARD_OBJECT_PUBLICATION.md`](D049_SHARED_STANDARD_OBJECT_PUBLICATION.md)
- [`D051_CONDITIONAL_SURFACE_BOUNDARY.md`](D051_CONDITIONAL_SURFACE_BOUNDARY.md)
- [`D052_TCP_LIVE_RESOURCE_OBJECT_TOPOLOGY.md`](D052_TCP_LIVE_RESOURCE_OBJECT_TOPOLOGY.md)

Each file is a **non-normative decision/rationale record**. Its ratified
specification revision and decision outcome are unchanged by the move; observable
Protos semantics remain authoritative under `spec/`.

This list is not a closed manifest for future language decisions.

## Subsequent language-domain decisions

- [`D071_MULTI_WAY_MATCHING_ARCHITECTURE.md`](D071_MULTI_WAY_MATCHING_ARCHITECTURE.md)
- [`D072_MATCHER_OUTCOME_CAPTURE_CARRIER.md`](D072_MATCHER_OUTCOME_CAPTURE_CARRIER.md)
- [`D073_MATCHER_INVOCATION_AUTHORITY.md`](D073_MATCHER_INVOCATION_AUTHORITY.md)
- [`D074_STRUCTURAL_DECONSTRUCTION_VIEW_ARCHITECTURE.md`](D074_STRUCTURAL_DECONSTRUCTION_VIEW_ARCHITECTURE.md)
- [`D075_NAMED_PROJECTION_REQUEST_RESULT_FAILURE_CONTRACT.md`](D075_NAMED_PROJECTION_REQUEST_RESULT_FAILURE_CONTRACT.md)
- [`D078_COMPLETE_NAMED_STRUCTURAL_VIEW_REMAINDER_CONTRACT.md`](D078_COMPLETE_NAMED_STRUCTURAL_VIEW_REMAINDER_CONTRACT.md)
- [`D080_POSITIONAL_SUBJECT_DECONSTRUCTION_ARCHITECTURE.md`](D080_POSITIONAL_SUBJECT_DECONSTRUCTION_ARCHITECTURE.md)
