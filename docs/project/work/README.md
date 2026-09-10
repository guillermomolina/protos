# Work-item records

`work/` contains durable non-normative records whose primary owner is one formal
tracked work item. Records are grouped by the **individual identifier**, for
example `I026/`, `LIB001/`, `TOOL001/`, or `DOC002/`, rather than by broad family
buckets.

A formal identifier is not created merely to classify documentation. Existing
flat work-item records remain at their current paths until a bounded DOC002
migration moves them; new unambiguous records use `work/<formal-work-item>/`.

Current role-first DOC002 records:

- [`TOOL002/TOOL002_TEST_TOOL.md`](TOOL002/TOOL002_TEST_TOOL.md)
  — canonical non-normative TOOL002 Test Tool lifecycle and implementation record.

- [`TOOL001/TOOL001_PACKAGE_TOOL.md`](TOOL001/TOOL001_PACKAGE_TOOL.md)
  — canonical non-normative TOOL001 Package Tool lifecycle record.
  - [`TOOL001-F2D workspace execution preflight`](TOOL001/TOOL001_F2D_EXECUTION_PREFLIGHT.md)
  - [`TOOL001-F2E external immutable-package execution`](TOOL001/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md)

- [`PERF004/PERF004_RUNTIME_PERFORMANCE_CHARACTERIZATION.md`](PERF004/PERF004_RUNTIME_PERFORMANCE_CHARACTERIZATION.md)
  — canonical non-normative PERF004 cross-language runtime-performance characterization record.

- [`PERF001/PERF001_BENCHMARKING.md`](PERF001/PERF001_BENCHMARKING.md)
  — PERF001 non-normative benchmark ownership, reproducibility and evidence plan.
  - [`PERF001-F concurrency methodology`](PERF001/PERF001_F_CONCURRENCY_METHODOLOGY.md)

- [`LM008/LM008_CORE_LANGUAGE_SURFACE_COMPLETENESS.md`](LM008/LM008_CORE_LANGUAGE_SURFACE_COMPLETENESS.md)
  — LM008 Core-language surface completeness parent record.
  - [`LM008-B`](LM008/LM008_B_GRAMMAR_EVALUATION_BINDING_CALLABLE_AUDIT.md)
  - [`LM008-C`](LM008/LM008_C_OBJECT_STRUCTURAL_REFLECTION_MUTATION_AUDIT.md)
  - [`LM008-D`](LM008/LM008_D_VALUES_CORE_COLLECTIONS_AUDIT.md)

- [`AUD003/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md`](AUD003/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md)
  — canonical non-normative AUD003 source-style conformance audit record.

- [`DOC001/DOC001_PROGRAMMING_DOCUMENTATION.md`](DOC001/DOC001_PROGRAMMING_DOCUMENTATION.md)
  — canonical non-normative DOC001 programming-documentation work record.

- [`DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md`](DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md)
  — ratified role-first path and compatibility contract.
- [`DOC002/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md`](DOC002/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md)
  — historical DOC002-A information-architecture audit and Option A decision packet.
- [`DOC002/DOC002_NAVIGATION_FOUNDATION.md`](DOC002/DOC002_NAVIGATION_FOUNDATION.md)
  — DOC002-C2 navigation-foundation closure record.

- [`DOC003/DOC003_DOCUMENTATION_BRANDING.md`](DOC003/DOC003_DOCUMENTATION_BRANDING.md)
  — approved transparent Protos logo/symbol identity and documentation-integration closure.

Live status, priority, assignment, and execution discussion remain in GitHub
Issues and the Protos Development Project.
