# GITHUB010 — Exhaustive Dxxx/PLATxxx comparative decision research policy

Status: **CLOSED / ACTIVE**

Owning live Issue: GitHub #324.

This record strengthens the pre-approval research requirements for substantive
`Dxxx` and `PLATxxx` decisions. It is repository governance only: it does not
change Protos semantics, runtime behavior, implementation version, existing
ratified decisions, or the authority of the project owner.

## Problem

The existing explicit design-approval gate already required prior art,
alternatives, trade-offs, scaling/failure analysis, counterexamples and a
recommendation. That was directionally correct but left research breadth and
depth largely to agent judgment.

In practice, an agent could satisfy the wording with a narrow comparison,
converge too quickly on a locally plausible solution, and present a decision
packet whose conclusion was stronger than its evidence.

The project owner explicitly requested that the recurring instruction to
"compare other languages/runtimes/systems exhaustively, test future options and
scalability, and pick the most Protos-aligned option" become durable repository
policy.

## Selected rule

`AGENTS.md` now requires exhaustive comparative research before approval is
requested for substantive `Dxxx` and `PLATxxx` decisions.

For `Dxxx`, the default minimum is at least five credible systems across at
least three materially different design approaches, unless the domain genuinely
has fewer relevant precedents.

For Truffle-related `PLATxxx`, agents must survey the relevant public Truffle
implementation space, not just one familiar language, and include Apple Pkl when
its implementation surface is materially comparable (or explicitly explain why
it is not). Mature non-Truffle runtimes, compiler systems, operating systems or
other substrates must also be examined when they contribute relevant evidence.

The rule deliberately requires comparison of underlying semantics and
architecture rather than syntax/API similarity.

## Common evaluation dimensions

Every surviving candidate is scored 1–5 on ten shared dimensions:

1. correctness / invariant preservation;
2. Protos alignment;
3. future-option resilience;
4. scalability;
5. conceptual simplicity;
6. portability / implementation freedom;
7. runtime / resource cost;
8. failure / operability;
9. reversibility / migration cost; and
10. evidence maturity / implementation risk.

Short score justifications are mandatory, and uncertain evidence carries
`HIGH`/`MEDIUM`/`LOW` confidence.

The numerical matrix is not a decision algorithm. Hard semantic constraints,
qualitative thresholds, catastrophic failure modes, unacceptable lock-in, or a
fundamental conflict with Protos philosophy may disqualify a candidate
regardless of aggregate score.

## Future stress

Every substantive packet must test plausible future requirements beyond the
motivating case and explicitly answer:

- What plausible future requirement would make us regret this option?
- If that happens, what escape path remains?

This turns "future-proofing" from a vague preference into a visible design
criterion.

## Required recommendation packet

Before the owner is asked to decide, the agent must present the problem,
constraints, prior-art survey, meaningful candidate set, scoring matrix,
failure/counterexample analysis, future/scaling stress analysis,
implementation/resource consequences, portability/migration/reversibility,
deferred questions, recommendation, and the strongest argument against that
recommendation.

The packet still ends at the existing explicit approval gate. Research rigor
does not transfer design authority from the project owner to the agent.

## Relationship to existing decisions

GITHUB010 is prospective governance. It does not reopen already-ratified
`Dxxx`/`PLATxxx` decisions merely because their historical analysis used a
different format.

If an already-ratified decision is explicitly reopened for review, the new
investigation must follow the GITHUB010 rule.

## Closure

GITHUB010 closes with publication of the policy because no runtime migration,
live GitHub graph reconciliation, implementation work or specification update is
required. The policy remains active for all future substantive Dxxx/PLATxxx
decision work.
