# Protos source guidelines

These instructions apply to hand-written Protos source under this tree:
`protos/lib/`, `protos/tools/`, `protos/tests/`, `protos/examples/`,
`protos/tutorials/`, and `protos/benchmarks/`, in addition to the
repository-root `AGENTS.md`.

## Protos source style: idiomatic syntax and canonical forms

For hand-written Protos source, agents MUST treat stable, specified idiomatic
syntactic forms as the default spelling for ordinary code instead of
systematically expanding them to their canonical protocol/desugared form merely
because that form explains the semantics.

This applies to Standard Library source, ordinary Protos tools/programs,
tutorials, examples, documentation snippets, and Protos-source tests whose
purpose is not the lowering itself. Use the explicit canonical/protocol form when
implementing or testing that mechanism directly, when bootstrap/layering would
otherwise become circular, when reflection/dispatch is the subject, or when the
explicit form is materially clearer.

This is a source-style rule, not language authority. It does not approve new
syntax, change specified equivalence, or permit an agent to invent sugar for an
unresolved design. Do not mechanically rewrite unrelated source only to satisfy
this preference. See `docs/guide/SOURCE_STYLE.md` for the human-facing policy and
examples.

## Protos capability and library reuse
<!-- GITHUB023 IDIOMATIC-PROTOS-CAPABILITY-LIBRARY-REUSE -->

For hand-written Protos source, agents MUST inspect the existing Protos language,
protocols, Standard Library, and bundled libraries before implementing equivalent
functionality locally. When an established Protos facility directly satisfies the
required semantics, prefer that facility over a private parallel implementation.

Use the highest-level established Protos capability that accurately and clearly
expresses the required behavior. This includes ordinary language/protocol
operations, existing collection selection/transformation/lookup/traversal
facilities, Standard Library and bundled-library APIs, and the established Protos
documentation mechanism when public Protos APIs require developer-facing
documentation.

In particular, a Tool or application MUST NOT embed a private parser, serializer,
collection utility, version parser, testing helper, filesystem/process facility,
or equivalent subsystem merely for implementation convenience when an existing
Protos facility satisfies the same contract. For example, when a Tool needs TOML
and the ratified Protos TOML library satisfies its requirements, use that library
instead of introducing a Tool-local TOML parser. Apply the same rule to JSON,
text/encoding, semantic versions, testing, collections, filesystem/process
facilities, and other already-supported domains.

Reuse is not mechanical feature usage. Do not use an abstraction merely because
it exists when a simpler or lower-level form is materially clearer or when the
existing facility does not provide the required semantics. A local implementation
is appropriate when reuse would create a bootstrap or layering cycle, violate an
isolation/authority/ownership boundary, when the existing capability is itself
being implemented or directly tested, or when measured requirements justify a
distinct implementation. Make a non-obvious exception explicit in the code,
work item, or implementation report.

If the required reusable capability is missing or semantically insufficient, do
not silently work around the gap by growing a private substitute inside the
consumer. Route the missing capability to the proper language, library, Tool, or
other owning work item unless the consumer-specific implementation is itself the
correct established owner.

This policy complements the syntactic source-style rule above: that rule governs
how equivalent Protos syntax is normally spelled; this rule governs which
existing Protos capabilities ordinary repository code should reuse.
