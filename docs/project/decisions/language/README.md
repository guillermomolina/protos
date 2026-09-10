# Language decision records

This role contains durable non-normative decision records whose primary
domain is observable language or specification semantics. These files record
decision process and rationale; they do not replace the normative specification
under `spec/`.

Many such records use the `Dxxx` family, but `Dxxx` does not mean
"language" by itself. New unambiguous language/specification decision records
use this directory. Implementation-independent tool/package decisions use
[`../tooling/`](../tooling/README.md), while host/runtime decisions use
[`../platform/`](../platform/README.md). Existing flat decision records remain
at their current paths until the bounded DOC002 decision-migration slice moves
them with link, role, compatibility, and authority checks.

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
