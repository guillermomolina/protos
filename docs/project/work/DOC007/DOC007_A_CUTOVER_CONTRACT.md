# DOC007-A — Project-document repository cutover contract and inventory

Status: **CLOSED on publication**

Live coordination: GitHub `DOC007 / #532`

Validation class: `DOCUMENTATION_ARCHITECTURE_AND_REPOSITORY_MIGRATION`

## Purpose

DOC007-A pins the source corpus and defines the cutover contract required before
history extraction or creation of the destination repository. It implements the
owner-approved AUD010 Candidate B-prime boundary without moving any repository,
Issue, Project, formal identifier, specification, or product authority.

## Pinned source baseline

```text
PROTOS_REPOSITORY=guillermomolina/protos
PROTOS_BASE=fb71be3fa6683b678133bca75976e426d2a28ed8
DESTINATION_REPOSITORY=UNSELECTED
DESTINATION_REPOSITORY_CREATED=NO
```

The two connector-probe add/delete pairs immediately preceding this baseline
leave the repository tree content unchanged with respect to the pre-probe
product state. DOC007 uses the post-cleanup HEAD so later work never relies on a
baseline at which the accidental path exists.

## Exact corpus inventory at the pinned base

The role-first `docs/project/**` corpus contains 296 files at the pinned base:

```text
135  docs/project/work
122  docs/project/decisions
 26  docs/project/evidence
  4  docs/project/registries
  3  docs/project/architecture
  3  docs/project/governance
  2  docs/project/history
  1  docs/project/README.md
---
296  total
```

DOC007-A itself is one additional intentional `work/DOC007` record, so the
post-publication corpus is expected to contain 297 files. DOC007-B must pin its
own execution base and prove the exact tree again rather than assuming no
intervening project record was published.

The top-level role tree remains unchanged:

```text
docs/project/
  README.md
  work/
  decisions/
  architecture/
  governance/
  registries/
  evidence/
  history/
```

No role rename or flattening is part of DOC007.

## Incoming maintained-reference inventory

Maintained references from the Protos product repository into the project corpus
exist in these active surfaces and must be reconciled during DOC007-D:

```text
AGENTS.md
README.md
ROADMAP.md
docs/README.md
src/AGENTS.md

# Programming / user documentation
docs/guide/README.md
docs/guide/SOURCE_STYLE.md
docs/guide/09-isolated-parallel-execution.md
docs/guide/10-actors-actorrefs-and-groups.md
docs/guide/11-process-io-filesystems-and-authority.md
docs/guide/tools/README.md
docs/guide/tools/test-tool.md

# Product design documentation
docs/design/IDEAS.md
docs/design/PACKAGE_CONTENT_IDENTITY.md
docs/design/PACKAGE_TOOL_ARCHITECTURE.md
docs/design/STRUCTURED_DATA_AND_SERIALIZATION.md
docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md
docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md

# Product-local README/reference surfaces
protos/lib/core/README.md
protos/benchmarks/README.md
protos/benchmarks/concurrency/README.md

# Validation fixture
scripts/test_validation_impact.py
```

`spec/**` and `.github/**` have no direct `docs/project/**` dependency at this
baseline. Executable runtime/build behavior does not require the project corpus
in the same checkout.

The validation-impact script does not consume project documents as runtime
input; its test fixture contains representative `docs/project/**` path strings.
That fixture must be updated when current paths cease to be repository-local.

## Historical-reference classification

Literal legacy `docs/project/...` paths embedded in immutable evidence,
retired history, migration records, release claims, old chronology, or other
records whose purpose is to describe a historical repository state are evidence,
not current navigation instructions.

DOC007 therefore uses this rule:

```text
ACTIVE_MAINTAINED_REFERENCE -> REWRITE_AT_CUTOVER
HISTORICAL_EVIDENCE_STRING  -> PRESERVE_LITERAL
AMBIGUOUS_REFERENCE          -> FAIL_CLOSED_AND_CLASSIFY
```

There is no repository-wide textual substitution of `docs/project/`.

## Outgoing reference contract

Project records frequently refer back to `spec/**`, `docs/design/**`, source,
tests, product documentation, Issues, commits, and other repository-local paths.
After extraction, a relative path can no longer silently mean a path in
`guillermomolina/protos`.

For maintained project records, DOC007-D must convert such references to an
explicit Protos repository target. Historical evidence strings remain unchanged.

Two link classes are selected:

### Maintained navigation

A maintained pointer whose purpose is to find the current canonical document may
follow the destination default branch:

```text
https://github.com/guillermomolina/<DESTINATION_REPOSITORY>/blob/main/docs/project/<path>
```

The concrete repository component remains unresolved until the owner selects the
name before DOC007-C.

### Revision-bound evidence

A claim about a particular product or project-record state must use the exact
revision, not a moving branch:

```text
https://github.com/guillermomolina/protos/blob/<PROTOS_SHA>/<path>
https://github.com/guillermomolina/protos/commit/<PROTOS_SHA>

https://github.com/guillermomolina/<DESTINATION_REPOSITORY>/blob/<PROJECT_RECORD_SHA>/docs/project/<path>
```

## Cross-repository revision contract

When durable project evidence corresponds to a product revision, the durable
record must carry the exact product revision explicitly:

```text
PROTOS_REVISION=<exact SHA>
```

When closure requires both repositories, the live Issue is the rendezvous point
and closure evidence must establish:

```text
PROTOS_REVISION=<exact SHA>
PROJECT_RECORD_REVISION=<exact SHA>
CROSS_REFERENCES=PASS
REQUIRED_DURABLE_PUBLICATION=PASS
```

A moving `main` link is insufficient for revision-bound closure evidence.

## Publication ordering

Git cannot provide an atomic commit across two independent repositories. DOC007
does not emulate one.

The selected fail-closed ordering is:

### Decision / approval publication before dependent product work

```text
owner approval
  -> durable project decision record publishes
  -> PROJECT_RECORD_REVISION is verified
  -> dependent product work may publish
```

This preserves the existing GITHUB015 rule that approval alone is not durable
ratification when a durable record is required.

### Product implementation plus closure record

```text
product implementation publishes
  -> exact PROTOS_REVISION exists
  -> durable closure/evidence record publishes and names that SHA
  -> PROJECT_RECORD_REVISION exists
  -> live Issue verifies both sides
  -> Issue may close
```

If the project-record publication fails after the product commit exists, the
product commit is not rolled back merely to imitate a distributed transaction;
the Issue remains open and `REQUIRED_DURABLE_PUBLICATION` remains unsatisfied.

### Project-document-only work

A project-document-only change requires only the destination publication and its
normal live-Issue postconditions.

## Repository-name checkpoint

DOC007-A does not choose the destination repository name.

The required checkpoint is:

```text
DOC007-B history-extraction proof     -> MAY PROCEED WITHOUT NAME
DOC007-C repository bootstrap         -> BLOCKED UNTIL OWNER SELECTS NAME
```

`protos-project` remains a historical working name only. No script, URL, remote,
or authority statement may treat it as selected before explicit owner approval.

## Authority invariants

Throughout DOC007:

```text
CONTROL_PLANE=guillermomolina/protos
LIVE_WORK_AUTHORITY=protos GitHub Issues
SCHEDULING=Protos Development Project
FORMAL_IDENTIFIER_AUTHORITY=protos
NORMATIVE_AUTHORITY=protos/spec
DURABLE_PROJECT_RECORD_AUTHORITY=destination repository after cutover
ISSUE_MIGRATION=NO
FORMAL_ID_FEDERATION=NO
PROTOS_HISTORY_REWRITE=NO
```

The destination repository stores durable records. It does not become a second
operational governance control plane.

## DOC007-A result

```text
DOC007_A_STATUS=CLOSED
PINNED_BASE=fb71be3fa6683b678133bca75976e426d2a28ed8
PINNED_CORPUS_FILES=296
POST_PUBLICATION_EXPECTED_FILES=297
ACTIVE_REFERENCE_POLICY=DEFINED
HISTORICAL_REFERENCE_POLICY=PRESERVE_LITERAL
CROSS_REPOSITORY_LINK_POLICY=DEFINED
REVISION_COUPLING=EXACT_SHA
CROSS_REPOSITORY_ATOMIC_COMMIT=NO
FAIL_CLOSED_ISSUE_CLOSURE=YES
DESTINATION_NAME_SELECTED=NO
DOC007_B_STATUS=READY
DOC007_C_STATUS=BLOCKED_BY_REPOSITORY_NAME_DECISION
SPECIFICATION_CHANGED=NO
PROTOS_IMPLEMENTATION_CHANGED=NO
```
