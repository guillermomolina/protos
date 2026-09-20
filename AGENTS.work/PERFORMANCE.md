# Protos performance work

These instructions apply to `PERFxxx`.

Investigation contract: measured behavior of already-defined semantics plus
the governing specification sections for the semantics that must be
preserved. No design-space re-research is required; an optimization that
needs an observable semantic change routes through `AGENTS.work/DESIGN.md`
first.

Apply the shared implementation discipline in `AGENTS.work/IMPLEMENTATION.md`
for validation, versioning, changelog, and publication.

## Performance work

Performance work uses the `PERFxxx` family. It records non-normative benchmark
suites, profiling and performance investigations, optimization work, and
performance-regression protection over behavior that is already defined by the
applicable specification and implementation owners.

`PERFxxx` work MUST NOT be used to redefine, relax, or bypass observable Protos
semantics, correctness requirements, conformance requirements, capability
boundaries, or lifecycle guarantees for the sake of a benchmark result. If a
performance improvement requires an observable semantic change, route that
change through the applicable specification/design process and implementation
family before treating the resulting implementation as performance work.

New Performance work uses the next unused `PERFxxx` identifier and creates/uses
its GitHub Issue for live coordination when formally introduced. Benchmark
workloads, result files, test names, commit messages, or incidental prose do not
become tracked work items merely because they contain a PERF-shaped token; the
Issue/Project coordination layer owns live lifecycle state while durable
repository records retain evidence.

Performance changes that modify executable implementation source under `src/`
or distributable Protos library source under `protos/lib/` remain subject to the
normal implementation versioning, changelog, validation, and publication rules.
