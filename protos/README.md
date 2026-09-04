# Protos Source Corpus

This directory contains source programs written in Protos.

Protos source files use the `.protos` filename extension.

The contents of this tree are non-normative. The language specification under
`spec/` remains the authority for Protos syntax and semantics.

## Structure

- `tutorials/` — Progressive examples intended for learning Protos.
- `examples/` — Task-oriented examples showing how common problems are expressed
  in Protos.
- `benchmarks/` — Reproducible workloads for measuring Protos implementations.
- `conformance/` — Specification-conformance programs, when introduced.
- `lib/` — Source libraries distributed with the language implementation.
  `lib/core/` contains ordinary Core behavior and standard objects that can be
  bootstrapped faithfully from Protos source rather than hardcoded in the host
  runtime.

Examples and benchmarks may intentionally contain similar programs. Their goals
are different: examples optimize for clarity, while benchmarks define stable
workloads suitable for measurement.
