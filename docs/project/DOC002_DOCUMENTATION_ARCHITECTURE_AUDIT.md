# DOC002 — Documentation information architecture audit

Status: **DOC002-A AUDIT COMPLETE; TARGET TAXONOMY PROPOSED / NEEDS_USER_DECISION**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information architecture and repository reorganization`.

Inventory publication base: `317aeb220092b05ce7777ff0aa6bc8bf64971c9b`.

This document is non-normative. It inventories and analyzes repository documentation; it does not define Protos semantics, and it is not a live status tracker. Normative language and Standard Library semantics remain under `spec/`. GitHub Issues and the Protos Development Project remain the live coordination/scheduling authority.

## DOC002-A scope and non-goals

DOC002-A performs the audit/inventory requested by Issue #156. It deliberately makes **no file moves or renames** and does not silently ratify a target information architecture. It classifies the complete current `docs/` file corpus, records link coupling as a migration-cost signal, identifies authority/lifecycle boundaries, compares target-tree alternatives, and proposes a staged migration for explicit project-owner selection.

The audit also lists repository-root documentation that constrains or frames `docs/`, but root policy/legal/community files are not migration candidates in DOC002-A.

## Authority model found

The existing `docs/README.md` already establishes a sound top-level authority boundary: `docs/` is non-normative; `spec/` owns normative language and Standard Library semantics. The information-architecture problem is therefore not a normative-authority conflict at the top level. It is primarily a **classification and navigation problem inside durable project records**, especially the flat `docs/project/` namespace.

No file under `docs/` is classified here as normative language authority. Files marked `historical evidence` are evidence/snapshots rather than semantic authority. Decision records under `docs/project/` record decisions, but their observable-language authority exists only where ratified semantics were incorporated into `spec/`.

## Inventory summary

Total regular files under `docs/` at the publication base (including this audit artifact): **107**.

Current-area counts:

- `README.md`: 1
- `assets`: 1
- `design`: 20
- `guide`: 13
- `project`: 72

Purpose counts:

- `architecture`: 2
- `decision record`: 22
- `decision registry`: 1
- `design exploration`: 19
- `evidence`: 7
- `governance`: 3
- `guide`: 11
- `history`: 2
- `navigation`: 2
- `registry`: 2
- `supporting asset`: 1
- `work-item record`: 35

Rows needing explicit manual lifecycle/path review after mechanical classification: **0**.

### Classification vocabulary

- **Authority** distinguishes non-normative maintained documentation, historical evidence, repository/legal governance, and normative material (none under `docs/`).
- **Purpose** distinguishes guide, design exploration, decision/work records, architecture, governance, registries, evidence, history, navigation, and supporting assets.
- **Lifecycle** describes the document artifact, not the live GitHub work status. `active/durable work record` is intentionally conservative where a work-item file may evolve while open and become durable after closure; live state must not be reverse-engineered from stale repository prose.
- **Owner** is assigned only where a formal owner is evident from the path/name or existing repository policy. Cross-cutting design material is not given a fake identifier merely for taxonomy.
- **Path clarity** separates a descriptive filename from the mixed responsibility of its current parent directory.
- **Migration cost** is a bounded first-pass signal from relative Markdown inbound/outbound coupling plus path-stability-sensitive categories. It is not a claim about unknown external hyperlinks.

## Complete `docs/` inventory

| Path | Authority | Purpose | Lifecycle | Owning item/role | Path clarity | In | Out | Move cost |
| --- | --- | --- | --- | --- | --- | ---: | ---: | --- |
| `docs/README.md` | non-normative | navigation | active evolving record | repository documentation | clear | 0 | 1 | HIGH |
| `docs/assets/social-preview.jpg` | non-normative | supporting asset | active evolving record | referencing documentation | clear | 0 | 0 | LOW |
| `docs/design/CONCURRENCY_DESIGN.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/FILESYSTEM_TREE_OBSERVATION.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/IDEAS.md` | historical evidence | history | immutable historical snapshot | none / cross-cutting | clear | 0 | 1 | LOW |
| `docs/design/PACKAGE_CONTENT_IDENTITY.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PACKAGE_CONTENT_IDENTITY_VECTORS.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PACKAGE_DISTRIBUTION.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PACKAGE_IDENTITY_VERSIONING.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PACKAGE_LOCKFILE_FORMAT.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PACKAGE_MANIFEST_FORMAT.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PACKAGE_MANIFEST_SCHEMA_V1.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PACKAGE_TOOL_ARCHITECTURE.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PACKAGE_VERSION_RESOLUTION.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/PROTOS_DESIGN_PHILOSOPHY.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/STANDARD_LIBRARY_IDEAS.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 1 | 0 | LOW |
| `docs/design/STRUCTURED_DATA_AND_SERIALIZATION.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/TEST_TOOL_ARCHITECTURE.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/design/TRUFFLE_GRAAL_OPTIMIZATION_INVESTIGATION_REFERENCE.md` | non-normative | design exploration | active evolving record | none / cross-cutting | clear | 0 | 0 | LOW |
| `docs/guide/01-bindings-contexts-and-state.md` | non-normative | guide | active evolving record | DOC001 | clear | 4 | 8 | HIGH |
| `docs/guide/02-objects-delegation-and-composition.md` | non-normative | guide | active evolving record | DOC001 | clear | 3 | 5 | HIGH |
| `docs/guide/03-closures-methods-and-receivers.md` | non-normative | guide | active evolving record | DOC001 | clear | 4 | 9 | HIGH |
| `docs/guide/04-control-flow-through-protocols.md` | non-normative | guide | active evolving record | DOC001 | clear | 4 | 16 | HIGH |
| `docs/guide/05-values-identity-equality-and-collections.md` | non-normative | guide | active evolving record | DOC001 | clear | 3 | 11 | HIGH |
| `docs/guide/06-modules-and-imports.md` | non-normative | guide | active evolving record | DOC001 | clear | 2 | 10 | HIGH |
| `docs/guide/07-errors-handlers-ensure-and-resource-lifetime.md` | non-normative | guide | active evolving record | DOC001 | clear | 5 | 14 | HIGH |
| `docs/guide/08-futures-and-structured-concurrency.md` | non-normative | guide | active evolving record | DOC001 | clear | 6 | 16 | HIGH |
| `docs/guide/09-isolated-parallel-execution.md` | non-normative | guide | active evolving record | DOC001 | clear | 4 | 9 | HIGH |
| `docs/guide/10-actors-actorrefs-and-groups.md` | non-normative | guide | active evolving record | DOC001 | clear | 3 | 24 | HIGH |
| `docs/guide/11-process-io-filesystems-and-authority.md` | non-normative | guide | active evolving record | DOC001 | clear | 2 | 25 | HIGH |
| `docs/guide/README.md` | non-normative | navigation | active evolving record | DOC001 | clear | 0 | 26 | HIGH |
| `docs/guide/SOURCE_STYLE.md` | non-normative | governance | active evolving record | DOC001 | clear | 1 | 0 | LOW |
| `docs/project/AUD002_GRAALVM_EDITOR_TOOLING_AUDIT.md` | non-normative | work-item record | active/durable work record | AUD002 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md` | non-normative | work-item record | active/durable work record | AUD003 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/CORE_BOOTSTRAP_ARCHITECTURE.md` | non-normative | architecture | active evolving record | cross-cutting Core architecture | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/CORE_NATIVE_BOUNDARY.md` | non-normative | architecture | active evolving record | cross-cutting Core architecture | clear name; mixed parent | 1 | 0 | LOW |
| `docs/project/D047_NETWORKING_DECISION.md` | non-normative | decision record | durable decision record | D047 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/D048_IP_ADDRESS_ENDPOINT_CONSTRUCTION.md` | non-normative | decision record | durable decision record | D048 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/D049_SHARED_STANDARD_OBJECT_PUBLICATION.md` | non-normative | decision record | durable decision record | D049 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/D051_CONDITIONAL_SURFACE_BOUNDARY.md` | non-normative | decision record | durable decision record | D051 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/D052_TCP_LIVE_RESOURCE_OBJECT_TOPOLOGY.md` | non-normative | decision record | durable decision record | D052 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/D053_PACKAGE_EXECUTION_PLAN_ABI_EVOLUTION.md` | non-normative | decision record | durable decision record | D053 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/D055_ASYNCHRONOUS_EXACT_EXECUTION_BOUNDARY.md` | non-normative | decision record | durable decision record | D055 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/D056_EXTERNAL_PACKAGE_PATH_DEPENDENCY_SEMANTICS.md` | non-normative | decision record | durable decision record | D056 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/D057_WORKSPACE_SEMANTICS_FOR_IMMUTABLE_EXTERNAL_PACKAGES.md` | non-normative | decision record | durable decision record | D057 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/DIST001_E4_CANDIDATE_ARTIFACT.txt` | historical evidence | evidence | immutable historical snapshot | DIST001 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/DIST001_E4_CANDIDATE_AUDIT.txt` | historical evidence | evidence | immutable historical snapshot | DIST001 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/DIST001_E4_RELEASE_CLAIMS.txt` | historical evidence | evidence | immutable historical snapshot | DIST001 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/DIST001_E4_RELEASE_ENVELOPE.txt` | historical evidence | evidence | immutable historical snapshot | DIST001 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/DIST001_E4_SELECTION.txt` | historical evidence | evidence | immutable historical snapshot | DIST001 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/DIST001_E4_VALIDATION.txt` | historical evidence | evidence | immutable historical snapshot | DIST001 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/DIST001_FIRST_PRERELEASE_PUBLICATION.txt` | historical evidence | evidence | immutable historical snapshot | DIST001 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/DIST001_FIRST_PRERELEASE_READINESS.md` | non-normative | work-item record | active/durable work record | DIST001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/DIST001_PRERELEASE_VERSION_CONTRACT.md` | non-normative | work-item record | active/durable work record | DIST001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/DIST001_RELEASE_POLICY.md` | non-normative | work-item record | active/durable work record | DIST001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/DIST002_TOOLCHAIN_ALIGNMENT.md` | non-normative | work-item record | active/durable work record | DIST002 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/DOC001_PROGRAMMING_DOCUMENTATION.md` | non-normative | work-item record | active/durable work record | DOC001 | clear name; mixed parent | 1 | 0 | LOW |
| `docs/project/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md` | non-normative | work-item record | active/durable work record | DOC002 | clear name; mixed parent | 1 | 0 | LOW |
| `docs/project/I026_A4B1_TRUFFLE_MULTITHREAD_SAFETY.md` | non-normative | work-item record | active/durable work record | I026 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/I026_A4B2A_PROCESS_CONTEXT_HOSTING.md` | non-normative | work-item record | active/durable work record | I026 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/I026_A4B2B1_ACTOR_CONTEXT_ROUTING.md` | non-normative | work-item record | active/durable work record | I026 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/I026_A4B2B2_P_CONTEXT_ROUTING.md` | non-normative | work-item record | active/durable work record | I026 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/I026_A4B2B3_CORE_PUBLICATION.md` | non-normative | work-item record | active/durable work record | I026 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/I026_A4B3_DRIVER_CUTOVER.md` | non-normative | work-item record | active/durable work record | I026 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/I026_TRUFFLE_TOOLING_FOUNDATION.md` | non-normative | work-item record | active/durable work record | I026 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/I028_NETWORKING_FOUNDATION.md` | non-normative | work-item record | active/durable work record | I028 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/IMPLEMENTATION_BLOCKERS.md` | non-normative | registry | active evolving record | repository project governance | clear name; mixed parent | 1 | 0 | MEDIUM |
| `docs/project/IMPLEMENTATION_STATUS.md` | historical evidence | registry | active evolving record | repository project governance | clear name; mixed parent | 4 | 0 | HIGH |
| `docs/project/LIB001_COLLECTIONS_DESIGN.md` | non-normative | work-item record | active/durable work record | LIB001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LIB002_TEXT_ENCODING_DESIGN.md` | non-normative | work-item record | active/durable work record | LIB002 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LIB003_JSON_DESIGN.md` | non-normative | work-item record | active/durable work record | LIB003 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md` | non-normative | work-item record | active/durable work record | LIB004 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LIB006_HASHING_DESIGN.md` | non-normative | work-item record | active/durable work record | LIB006 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LICENSING_RATIONALE.md` | non-normative | governance | durable closed/evolving policy | repository licensing governance | clear name; mixed parent | 0 | 2 | LOW |
| `docs/project/LM007_OBJECT_MODEL_MATURITY_PLAN.md` | non-normative | work-item record | active/durable work record | LM007 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LM008_B_GRAMMAR_EVALUATION_BINDING_CALLABLE_AUDIT.md` | non-normative | work-item record | active/durable work record | LM008 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LM008_CORE_LANGUAGE_SURFACE_COMPLETENESS.md` | non-normative | work-item record | active/durable work record | LM008 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LM008_C_OBJECT_STRUCTURAL_REFLECTION_MUTATION_AUDIT.md` | non-normative | work-item record | active/durable work record | LM008 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/LM008_D_VALUES_CORE_COLLECTIONS_AUDIT.md` | non-normative | work-item record | active/durable work record | LM008 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/OPEN_TASKS.md` | historical evidence | history | immutable historical snapshot | repository project governance | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PERF001_BENCHMARKING.md` | non-normative | work-item record | active/durable work record | PERF001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/PERF001_F_CONCURRENCY_METHODOLOGY.md` | non-normative | work-item record | active/durable work record | PERF001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/PERF002_TRUFFLE_COMPILABILITY.md` | non-normative | work-item record | active/durable work record | PERF002 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/PERF003_COLLECTION_COMPILABILITY.md` | non-normative | work-item record | active/durable work record | PERF003 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/PERF004_RUNTIME_PERFORMANCE_CHARACTERIZATION.md` | non-normative | work-item record | active/durable work record | PERF004 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/PLAT001_TRUFFLE_RUNTIME_HOSTING.md` | non-normative | decision record | durable decision record | PLAT001 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT002_NETWORK_CAPABILITY_REPRESENTATION.md` | non-normative | decision record | durable decision record | PLAT002 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT003_TCP_LIVE_RESOURCE_ARCHITECTURE.md` | non-normative | decision record | durable decision record | PLAT003 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT004_TRUFFLE_SOURCE_SECTION_OWNERSHIP.md` | non-normative | decision record | durable decision record | PLAT004 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT005_TRUFFLE_INSTRUMENTATION_ARCHITECTURE.md` | non-normative | decision record | durable decision record | PLAT005 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT006_TCP_HOST_IO_OPERATION_ENGINE.md` | non-normative | decision record | durable decision record | PLAT006 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT007_IPV6_ONLY_TCP_LISTENER_ENFORCEMENT.md` | non-normative | decision record | durable decision record | PLAT007 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT008_TRUFFLE_REPLAY_SITE_IDENTITY.md` | non-normative | decision record | durable decision record | PLAT008 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT009_BYTEWRITABLE_FIRST_EFFECT_GATE.md` | non-normative | decision record | durable decision record | PLAT009 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT010_ACTOR_PLATFORM_CARRIERS.md` | non-normative | decision record | durable decision record | PLAT010 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT011_RUNTIMEHOST_CARRIER_SUBSTRATE.md` | non-normative | decision record | durable decision record | PLAT011 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT012_VERIFIED_EXTERNAL_PACKAGE_CUSTODY_SOURCE_RESOLUTION.md` | non-normative | decision record | durable decision record | PLAT012 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLAT013_TRUFFLE_DEBUGGER_INTEROP_VALUE_PROJECTION.md` | non-normative | decision record | durable decision record | PLAT013 | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/PLATFORM_ARCHITECTURE_DECISIONS.md` | non-normative | decision registry | active evolving record | PLAT decision registry | clear name; mixed parent | 0 | 0 | MEDIUM |
| `docs/project/STANDARD_LIBRARY_NAMING.md` | non-normative | governance | durable closed/evolving policy | Standard Library governance | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/TOOL001_F2D_EXECUTION_PREFLIGHT.md` | non-normative | work-item record | active/durable work record | TOOL001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md` | non-normative | work-item record | active/durable work record | TOOL001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/TOOL001_PACKAGE_TOOL.md` | non-normative | work-item record | active/durable work record | TOOL001 | clear name; mixed parent | 0 | 0 | LOW |
| `docs/project/TOOL002_TEST_TOOL.md` | non-normative | work-item record | active/durable work record | TOOL002 | clear name; mixed parent | 0 | 0 | LOW |

## Repository-root documentation boundary

These files participate in repository documentation but should not be pulled into `docs/` merely for tidiness. Their root placement is part of their discoverability or platform convention.

| Path | Authority | Purpose | DOC002-A treatment |
| --- | --- | --- | --- |
| `README.md` | non-normative | repository entrypoint/navigation | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |
| `ROADMAP.md` | non-normative | repository entrypoint/navigation | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |
| `CONTRIBUTING.md` | repository governance | community/project policy | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |
| `CODE_OF_CONDUCT.md` | repository governance | community/project policy | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |
| `SECURITY.md` | repository governance | community/project policy | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |
| `SUPPORT.md` | repository governance | community/project policy | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |
| `THIRD_PARTY_NOTICES.md` | legal/project record | legal notices | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |
| `LICENSE.TXT` | legal authority | license authority | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |
| `AGENTS.md` | repository governance | agent governance | OUT_OF_SCOPE_FOR_MOVES_IN_DOC002-A |

## Findings

1. **The top-level `docs/` split is already conceptually healthy.** `guide/`, `design/`, and `project/` have distinct stated responsibilities. DOC002 should preserve that boundary rather than invent a new parallel hierarchy.
2. **`docs/project/` is the concentration point.** Its filenames currently encode multiple orthogonal classifications: formal work items (`Ixxx`, `LIBxxx`, `TOOLxxx`, `PERFxxx`, `AUDxxx`, `DISTxxx`, `DOCxxx`, `LMxxx`), language/platform decision records (`Dxxx`, `PLATxxx`), cross-cutting architecture (`CORE_*`), registries, governance/rationale, historical snapshots, and immutable release evidence.
3. **Identifier and document class are different axes.** A licensing rationale does not need a new `LICxxx` family. Likewise, a file can belong to `TOOL001` while its document purpose is design/evidence/work-record. DOC002 should organize documents without creating identifiers solely to make the tree symmetrical.
4. **GitHub migration changes what repository ledgers should do.** `OPEN_TASKS.md` is historical; `IMPLEMENTATION_STATUS.md` is durable closure/evidence registry; the owning GitHub Issue is live execution coordination. Any target tree that treats all three as equivalent 'status files' would recreate the ambiguity DOC002 is meant to remove.
5. **Historical and release evidence deserves stronger path semantics than ordinary work prose.** Immutable `DIST001_*` evidence mixed beside editable work records makes lifecycle unclear and makes retention rules harder to infer.
6. **Mass rename is the wrong first migration.** Relative links can be rewritten mechanically, but unknown external GitHub URLs, commit messages, issue comments and third-party references make durable path value asymmetric. Migration should be staged by role and link cost, preserving especially high-value stable paths until a specific move earns its churn.

## Target-tree alternatives (not ratified)

### Option A — role-first durable project tree

```text
docs/project/
  README.md
  work/
    DOC001/
    DOC002/
    I026/
    I028/
    LIB001/
    TOOL001/
    ...
  decisions/
    language/      # Dxxx records; non-normative records of semantic decisions
    platform/      # PLATxxx records
  architecture/    # CORE_* and other cross-cutting implementation architecture
  governance/      # naming/licensing/rationale policies without invented work IDs
  registries/      # durable registries, not live scheduling
  evidence/
    DIST001/       # immutable release/candidate evidence grouped by owner
  history/         # retired snapshots such as OPEN_TASKS
```

Strengths: path communicates document role; per-owner work directories scale when one item accumulates many slice records; decision authority is visually separated from work execution; evidence/history lifecycles become obvious. Weaknesses: highest migration churn and many current URLs change unless high-cost legacy files are retained or migrated later.

### Option B — family-first work tree

Group work records under `work/I/`, `work/LIB/`, `work/TOOL/`, etc., while keeping decisions/governance/evidence separate. This reduces directory count but becomes broad as families grow and does less to gather all durable material for a single complex item such as `I026` or `TOOL001`.

### Option C — keep `docs/project/` flat and add indexes only

This has the lowest migration risk and preserves every current URL, but path semantics remain weak and lifecycle/authority remain encoded mostly in filenames and prose. It improves navigation without actually solving the mixed-responsibility namespace.

## Recommendation — PROPOSAL pending explicit approval

Adopt **Option A** as the destination model, with one important compatibility rule: **target architecture does not imply immediate relocation of every legacy file**. Start applying the new role-first tree to newly created durable records after ratification, then migrate existing files in bounded batches ordered by semantic confidence and link cost. High-cost registries or historically cited paths may remain at their current location until a dedicated migration proves that the churn is justified.

This is the most Protos-like option because it minimizes overloaded responsibility instead of inventing more identifier families: work identity remains one axis, document role another, and normative semantics stay in their existing universe under `spec/`. It also scales by composition: adding many files to `I026`, `TOOL001`, or future work does not force the root project directory to grow without bound.

**Approval gate:** this recommendation is not a project decision. DOC002-B must not move files or declare this taxonomy selected until the project owner explicitly approves Option A or selects another alternative.

## Proposed staged migration after approval

1. **DOC002-B — taxonomy ratification and path contract.** Record the selected tree, define what remains intentionally at root, and define whether new work records use per-item directories immediately.
2. **DOC002-C — navigation foundation.** Add `docs/project/README.md` plus role indexes/directories without moving high-cost legacy files. Validate all new local links.
3. **DOC002-D — low-risk governance/history/evidence separation.** Move only clearly classified low/medium-cost material in bounded batches, with repository-wide relative-link rewrite and link validation per batch.
4. **DOC002-E — formal work-item records.** Migrate one owner/family batch at a time (for example DOC, then AUD/PERF, then selected I/LIB/TOOL owners), keeping issue references and historical commit references intact.
5. **DOC002-F — decisions and cross-cutting architecture.** Move `Dxxx`, `PLATxxx`, `CORE_*` and registries only after checking every durable inbound reference and reaffirming their authority wording.
6. **DOC002-G — final navigation/compatibility audit.** Repository-wide Markdown link check, stale-path search, documentation-entrypoint review, and closure evidence. Do not create redirect stubs that duplicate authoritative content unless a concrete external-compatibility requirement justifies them.

## Explicitly deferred

DOC002-A does not decide whether legacy high-cost paths must ever move, whether GitHub external links require compatibility stubs, or whether a future documentation linter should enforce path classes automatically. Those are implementation/compatibility choices for later DOC002 slices after the taxonomy itself is approved.

## DOC002-A closure criteria

DOC002-A is complete when this artifact is published with: the complete current `docs/` inventory, the authority/purpose/lifecycle/owner/path/link classification above, target-tree alternatives, an explicit non-ratified recommendation, and a staged migration plan; no existing documentation file may be moved or renamed by this slice.
