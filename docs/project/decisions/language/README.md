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
