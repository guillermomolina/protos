# Markdown evidence workloads

`Corpus.protos` accepts caller-supplied `{ source }` document records and an exact
corpus revision. It runs both profiles with and without positions. Acquisition
and authority belong to the caller. Its report records actual dimensions and
compares them with the investigation baseline of 90 documents and a largest
259,013-byte document. Matching dimensions alone does not establish historical
byte identity; record the exact corpus revision and input checksums separately.
The current checkout is not automatically the pinned historical corpus.

`Workloads.protos` constructs deterministic adversarial inputs and exposes a
size-doubling series callback. An external authorized harness owns execution,
warmup, repetitions, CPU/allocation measurement and result retention. Nested
containers, wide tables and fanout may intentionally reach a specified resource
limit; preserve the resulting Error as evidence. Large workloads do not belong
in the ordinary small regression corpus. Resource-boundary conformance tests
remain independently attributable in `resource-limits.protos`.

No performance measurements have been executed for this candidate. The target is
approximately O(N log(N+1) + V + T) work and O(N + V + T) live space, with linear
behavior preferred. This target does not authorize changing accepted syntax.

The retained upstream inputs and generated data are documented in
`protos/tests/conformance/library/markdown/official/upstream/README.md`.
Regenerate the projections offline when changing their retained inputs.
Corpus generation does not establish parser conformance or performance.
