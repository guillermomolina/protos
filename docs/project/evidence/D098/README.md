# D098 — Publication evidence

Evidence owner: `D098 — Test Tool resource catalog provider/profile identity contract`

Decision issue: GitHub #393

Evidence date: **2026-09-12**

This file is immutable publication/closure evidence. It does not add or modify the
D098 contract.

## Ratification publication

The owner-approved D098 Candidate A′ ratification was published from:

```text
publication_base=4d0777504da90f1adcb6ea54285fc25990098777
sha=6640948d2b0c60bb5ec3865b63c59f5bbd15d79d
validation_class=GOVERNANCE_DOCUMENTATION_ONLY
```

Published commit subject:

```text
Ratify D098 provider profile identity contract
```

The published slice reported:

```text
slice=D098-RATIFICATION
D098_STATUS=RATIFIED
D098_SELECTED_CANDIDATE=A_PRIME
PROVIDER_REQUIRED=YES
PROFILE_OPTIONAL=YES
PROVIDER_INFERRED_FROM_RESOURCE_KEY=NO
ARBITRARY_PROVIDER_CONFIG_MAP_V1=NO
SCHEDULER_INTERPRETS_PROVIDER_INTERNALS=NO
UNKNOWN_PROVIDER_PROFILE_FAIL_CLOSED=YES
TOOL002_I6E_STATUS=UNCHANGED_NEXT_EXECUTABLE
SPECIFICATION_CHANGED=NO
IMPLEMENTATION_CHANGED=NO
IMPLEMENTATION_VERSION_CHANGED=NO
NATIVE_BOUNDARY_CHANGED=NO
CALLER_WORKTREE_TOUCHED_BY_LAUNCHER=NO
CLEANUP=PASS
```

Exact ratification files:

```text
CHANGELOG.md
docs/project/decisions/tooling/D098_TEST_TOOL_RESOURCE_CATALOG_PROVIDER_PROFILE_IDENTITY_CONTRACT.md
docs/project/work/TOOL002/TOOL002_TEST_TOOL.md
```

No executable implementation, Protos specification, Maven implementation version
or native boundary was changed by the ratification.

## Failed pre-publication attempt

The first launcher attempt used the same publication base and passed the D098
semantic contract validation, but aborted before commit/publication because its
slice-scope check used an unstaged `git diff --name-only`, which does not enumerate
a newly-created untracked decision document.

Observed safe-abort evidence:

```text
D098_CONTRACT_VALIDATION: PASS
SLICE_SCOPE_FAILED: unexpected changed files
Expected:
CHANGELOG.md
docs/project/decisions/tooling/D098_TEST_TOOL_RESOURCE_CATALOG_PROVIDER_PROFILE_IDENTITY_CONTRACT.md
docs/project/work/TOOL002/TOOL002_TEST_TOOL.md
Actual:
CHANGELOG.md
docs/project/work/TOOL002/TOOL002_TEST_TOOL.md
CALLER_WORKTREE_TOUCHED_BY_LAUNCHER: NO
CLEANUP: PASS
```

No candidate was committed or published by that attempt.

The replacement launcher corrected only publication mechanics: it staged exactly
the three allowed paths first, validated the staged path set, and rejected any
remaining unexpected untracked path. D098 semantics were unchanged.

## GitHub closure

After commit `6640948d2b0c60bb5ec3865b63c59f5bbd15d79d` was confirmed published,
the publication evidence was recorded on GitHub #393 and the issue was closed
with state reason `completed`.

That closure is live coordination evidence. The durable ratified contract remains:

`docs/project/decisions/tooling/D098_TEST_TOOL_RESOURCE_CATALOG_PROVIDER_PROFILE_IDENTITY_CONTRACT.md`.

## Downstream effect

D098 did not unblock or alter `TOOL002-I6E`. I6E remains the already-bounded
requirements-sidecar acquisition slice and is independently publication-blocked
by the repository-wide executable validation state.

D098 instead closes the provider/profile identity ambiguity for later resource
catalog parsing and provider-resolution work.
