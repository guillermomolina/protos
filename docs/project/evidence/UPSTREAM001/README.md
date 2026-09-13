# UPSTREAM001 evidence

UPSTREAM001 owns retained Protos-side evidence for Oracle/Graal PR #14434,
`[GR-79562] Support configurable Bytecode DSL unwind exceptions`.

Coordination remains in GitHub Issue #480. The retained evidence is deliberately
separate from live issue state: this directory records what was actually tested,
with exact external/runtime baselines and explicit limits on the conclusions.

## Records

- [`UPSTREAM001_GRAAL_PR14434_UNWIND_EXPERIMENT.md`](UPSTREAM001_GRAAL_PR14434_UNWIND_EXPERIMENT.md)
  — exact disposable Protos integration, semantic validation, JVMCI/runtime
  alignment, IGV experiments, optimizer findings, and non-conclusions.

## Authority boundary

This evidence is non-normative. It does not authorize an upstream version
upgrade, production adoption, semantic change, PLAT021 reopening, or PERF006
dependency. UPSTREAM001 is currently classified
`BENEFICIAL_BUT_NOT_ACTIONABLE`.
