# D099 — Publication evidence

Evidence owner: `D099 — Test Tool resource catalog source and CLI acquisition contract`

Decision issue: GitHub #414

Evidence date: **2026-09-12**

This file is immutable publication/closure evidence. It does not add or modify the
D099 contract.

## Ratification publication

The owner-approved D099 Candidate A′ refined ratification was published from:

```text
publication_base=17c6dd115afbfdf0f9be6e8d002a234a53c6c9a3
sha=327e87a52b01b7d3bbeff0042a317c6e64e65226
validation_class=GOVERNANCE_DOCUMENTATION_ONLY
```

Published slice evidence:

```text
slice=D099-RATIFICATION
D099_STATUS=RATIFIED
D099_SELECTED_CANDIDATE=A_PRIME_REFINED
RESOURCE_CATALOG_OPTION=--resource-catalog
RESOURCE_CATALOG_SOURCE_CARDINALITY=ZERO_OR_ONE
RESOURCE_CATALOG_V1_SOURCE=EXPLICIT_LOCAL_PATH
RESOURCE_CATALOG_DISCOVERY=NO
RESOURCE_CATALOG_ENV_FALLBACK=NO
RESOURCE_CATALOG_MERGE=NO
RESOURCE_CATALOG_STDIN=NO
RESOURCE_CATALOG_URI=NO
RESOURCE_CATALOG_CORPUS_BINDING=NO
HOST_PATH_DURABLE_IDENTITY=NO
UNSATISFIED_RESOURCE_WITH_EMPTY_CATALOG_STARTS_CASE=NO
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
docs/project/decisions/tooling/D099_TEST_TOOL_RESOURCE_CATALOG_SOURCE_AND_CLI_ACQUISITION_CONTRACT.md
docs/project/work/TOOL002/TOOL002_TEST_TOOL.md
```

No executable implementation, Protos specification, Maven implementation version
or native boundary was changed by the ratification.

## Failed pre-publication attempt

The first D099 ratification launcher did not commit or publish a candidate.

It reached bounded governance validation after materializing the approved content
but the validation script searched for the impossible literal substring:

```text
Future resilience | **4.9**
```

The decision document correctly represented that score inside the complete
Markdown table row:

```text
| **A′ — one explicit local catalog path** | **4.9** | **5.0** | **5.0** | **SELECTED** |
```

Observed safe-abort evidence:

```text
D099_CONTRACT_VALIDATION_FAILED: missing decision anchor: Future resilience | **4.9**
CALLER_WORKTREE_TOUCHED_BY_LAUNCHER: NO
CLEANUP: PASS
```

The replacement v2 changed only validator mechanics so the assertion matched the
actual approved Markdown table row. D099 semantics/content were unchanged.

## GitHub closure

After commit `327e87a52b01b7d3bbeff0042a317c6e64e65226` was confirmed published,
the PUBLISHED evidence was recorded on GitHub #414 and the issue was closed with
state reason `completed`.

Final issue labels:

```text
family:D
priority:p1
status:closed
```

The live Issue is coordination evidence. The durable ratified contract remains:

`docs/project/decisions/tooling/D099_TEST_TOOL_RESOURCE_CATALOG_SOURCE_AND_CLI_ACQUISITION_CONTRACT.md`.

## Ratified acquisition contract

D099 fixes the v1 catalog acquisition boundary to:

```text
protos test [--resource-catalog PATH] ...
```

with exactly zero or one explicit local path.

No option means the D077 empty catalog. Repeating the option is an error.
There is no discovery, environment-variable fallback, merge, stdin, URI source,
corpus binding or durable host-path identity.

A resourceful case remains subject to D076 admission even when no catalog is
provided; unsatisfied requirements do not start the case.

## Downstream effect

D099 does not implement the CLI option or catalog parser.

`TOOL002-I6E` remains the next already-bounded executable TOOL002 slice. Its
independent executable-validation state is not changed by D099.
