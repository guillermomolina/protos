# Tooling decision records

This role contains durable **non-normative** decision records whose primary
domain is implementation-independent tooling or package-system policy, including
Package Tool, Test Tool and similar project-tool contracts.

A tooling decision belongs here when it:

- does not define observable Protos language or Standard Library semantics;
- does not select a concrete host/runtime/VM/OS architecture; and
- is durable enough to outlive the implementation slice that consumes it.

The identifier family is orthogonal to this role. Existing `Dxxx` identifiers
are retained when already allocated; moving such a record here does not rename
the decision or change its outcome.

DOC002-F2 migrated the initial ratified tooling-domain set into this role:

- [`D053_PACKAGE_EXECUTION_PLAN_ABI_EVOLUTION.md`](D053_PACKAGE_EXECUTION_PLAN_ABI_EVOLUTION.md)
- [`D055_ASYNCHRONOUS_EXACT_EXECUTION_BOUNDARY.md`](D055_ASYNCHRONOUS_EXACT_EXECUTION_BOUNDARY.md)
- [`D056_EXTERNAL_PACKAGE_PATH_DEPENDENCY_SEMANTICS.md`](D056_EXTERNAL_PACKAGE_PATH_DEPENDENCY_SEMANTICS.md)
- [`D057_WORKSPACE_SEMANTICS_FOR_IMMUTABLE_EXTERNAL_PACKAGES.md`](D057_WORKSPACE_SEMANTICS_FOR_IMMUTABLE_EXTERNAL_PACKAGES.md)

Later ratified tooling decisions include:

- [`D060_PUBLIC_DEBUGGER_LAUNCHER_READINESS.md`](D060_PUBLIC_DEBUGGER_LAUNCHER_READINESS.md)

Their existing identifiers and decision outcomes are unchanged. This list is
not a closed manifest for future tooling decisions.

Observable Protos semantics remain authoritative under `spec/`. Concrete
runtime/host architecture belongs under [`../platform/`](../platform/README.md).
Language/specification decision rationale belongs under
[`../language/`](../language/README.md).

DOC002-F0 ratified this role by explicit project-owner approval on 2026-09-10.
