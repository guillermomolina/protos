# Protos incubator ideas

These instructions apply to `INCxxx`: experimental, speculative, or still
immature ideas that merit formal identity and tracking before they are a
decision or implementable work.

Investigation contract: lightweight. Incubation carries no obligation of
exhaustive research, architectural decision, implementation, priority, or
schedule.

## Scope

Suitable examples: UI models shared across TUI/WUI/GUI, Smalltalk-like
environments, experimental tools, alternative paradigms, conceptual
prototypes, and speculative future capabilities.

Not `INCxxx`: reproducible defects, concrete already-approved implementation,
formal decisions ready for ratification, and ordinary community questions,
which stay in Discussions. Adopting an idea as `INCxxx` is a maintainer
decision under the allocation rules in `AGENTS.work/COORDINATION.md`.

## Intake

An idea needs only:

- what is imagined;
- why it may be interesting; and
- what remains unknown, when applicable.

Do not require matrices, scoring, cross-system research, estimates,
implementation criteria, API design, detailed plans, or tests to record an
idea.

## Exploration

Discussion, analogies, references, hypotheses, discarded directions, and
incremental exploration are all welcome. The exhaustive investigation of
`AGENTS.work/DESIGN.md` is not required while an idea is incubating and no
substantive decision is being selected.

An isolated proof of concept may be proposed, but `INCxxx` alone authorizes no
product, specification, public API, or stable architecture change. Any
experimental code edit needs its own authorization and the applicable
implementation contract.

## Authority boundary

Incubator content is non-normative, is not an approved decision, and
authorizes no implementation. Selecting observable semantics or durable
architecture routes through `Dxxx`/`PLATxxx` and `AGENTS.work/DESIGN.md`,
wherever the question was discovered. Formal implementation work uses its own
family and contract.

## Lifecycle

Use only the canonical `status:*` labels; there is no incubation-specific
status. The usual initial state is `status:inbox` with priority and assignee
unset. `status:ready` means genuinely actionable exploratory work, never design
approval. `status:paused` keeps an idea without active work. An idea may close
as `not_planned` with a brief explanation in the Issue; that is not a failure.

## Promotion

Promotion is explicit and allocates a new formal identifier in the appropriate
family (`Dxxx`, `PLATxxx`, `Ixxx`, `LIBxxx`, `TOOLxxx`, or other). The
`INCxxx` Issue keeps its identity, family, and history; cross-reference the
`INCxxx` Issue and the new Issue. A reference implies no dependency,
parent/child relation, priority, or approval; create a native parent relation
only when a real coordination hierarchy exists under
`AGENTS.work/COORDINATION.md`. Promotion grants no approval.

## Evidence and closure

Do not create durable documents or ratification records per idea. Keep
findings in the Issue unless there is a concrete reason to publish durable
evidence. The closure and traceability rules of `AGENTS.work/COORDINATION.md`
apply, proportionate to the exploratory outcome.
