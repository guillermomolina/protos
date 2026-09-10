# Decision records

`decisions/` contains durable non-normative records of already-tracked design or
platform decisions. A decision record documents rationale and outcome; its path
does not create semantic authority.

- [`language/`](language/README.md) contains decision records whose primary
  domain is language/specification semantics. Observable language authority
  remains in the applicable ratified specification under `spec/`.
- [`tooling/`](tooling/README.md) contains implementation-independent tooling,
  package-system, Package Tool, Test Tool, and similar project-tool decisions
  that neither define observable Protos semantics nor select host/runtime
  architecture.
- [`platform/`](platform/README.md) contains `PLATxxx` runtime/platform
  architecture records. They remain non-normative and semantically invisible
  except where separate Protos semantics explicitly require otherwise.

Identifier family does not by itself select the role: existing `Dxxx` records
may be language-domain or tooling-domain decisions. DOC002-F1/F2 completed the
legacy flat Dxxx decision migration after durable-reference, authority, role, and
compatibility review. New unambiguous decisions use their canonical role
directory; DOC002-G owns only later residual/straggler reconciliation.
