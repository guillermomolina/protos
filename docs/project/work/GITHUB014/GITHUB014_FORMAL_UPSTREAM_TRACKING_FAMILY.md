# GITHUB014 — Formal upstream-impact tracking family

Status: **RATIFIED**
Project-owner approval: **2026-09-13**
Coordination Issue: **#479**
Nature: repository/governance and GitHub coordination tooling; no Protos semantic or runtime change

## Purpose

`UPSTREAMxxx` is the formal project family for external evolution that can affect
or benefit Protos without automatically becoming Protos implementation work. It
covers released and unreleased external features, releases, deprecations, defects,
compatibility changes, workaround retirement opportunities, and direct technical
collaboration with upstream maintainers.

The motivating case is Oracle/Graal PR #14434, whose experimental configurable
Bytecode DSL unwind support was validated deeply against a disposable Protos
adaptation. That evidence is valuable even though current production Protos does
not yet have the infrastructure required to adopt the mechanism. Treating the
experiment as a new PERF006 implementation phase would therefore misstate both
ownership and actionability.

The same distinction applies to future GraalVM/Truffle releases, JDK/OpenJDK
versions, JVMCI/Native Image evolution, build/runtime dependency changes, and
tooling such as DAP/LSP when upstream behavior changes independently of Protos.

## Family contract

An `UPSTREAMxxx` item owns evaluation and external coordination, not Protos
semantics or implementation authority. It may retain:

- exact external project, release, PR, Issue, branch, version, and SHA identity;
- compatibility/impact experiments and reproducible evidence;
- upstream maintainer questions, feedback, retest requests, and joint debugging;
- the exact Protos/toolchain baseline against which evidence was obtained;
- explicit non-conclusions and prerequisites that prevent premature adoption; and
- later re-evaluation triggers when upstream or Protos prerequisites change.

It does not by itself authorize upgrading, adopting, migrating, redesigning, or
shipping anything in Protos.

## Impact classifications

Every bounded evaluation should converge on one of three impact outcomes:

1. `NO_ACTION` — compatible, irrelevant, or no change is justified.
2. `BENEFICIAL_BUT_NOT_ACTIONABLE` — useful or promising, but current Protos
   prerequisites/production applicability are absent.
3. `ACTION_REQUIRED` — concrete Protos work is justified.

`ACTION_REQUIRED` routes work to the family that actually owns it. A runtime
implementation correction might become `Ixxx` or `BUGxxx`; a release/toolchain
migration might become `DISTxxx`; performance work might become `PERFxxx`; and
a substantive new semantic/platform choice still requires `Dxxx`/`PLATxxx`
approval. The upstream item remains provenance/evidence, not a substitute owner.

## Lifecycle and Project views

No new lifecycle label is introduced. `UPSTREAMxxx` uses the canonical GITHUB012
status vocabulary.

- active evaluation: ordinarily `Ready` or `In progress`;
- awaiting external feedback/release or unavailable Protos prerequisite: `Paused`;
- completed impact classification: close the Issue once any actionable derived
  work has its proper owner.

This deliberately means some upstream evaluations belong in the Work queue while
others do not. Family identity never overrides the canonical status/view contract.

## Durable records

DOC002 role-first paths remain authoritative:

```text
docs/project/work/UPSTREAMxxx/
    maintained work record primarily owned by the upstream item

docs/project/evidence/UPSTREAMxxx/
    immutable/snapshot-like experiment or compatibility evidence
```

No `docs/project/upstream/` classification tree is created.

## Intake integration

`scripts/issue_intake.py` recognizes `UPSTREAM` as a formal family. Before adding
a missing `family:<FAMILY>` label, the GitHub-backed intake ensures that the
family label exists. Label creation is idempotent and tolerates only the exact
concurrent-create race; other GitHub API failures remain fail-closed.

Retained self-test covers:

- title parsing of `UPSTREAM001`;
- formal-family derivation as `UPSTREAM`;
- family-label ensure/reconciliation; and
- the `UPSTREAM_FAMILY_RECONCILIATION: PASS` marker.

## Initial consumer

After this governance slice is published, the first formal consumer is:

```text
UPSTREAM001 — Oracle/Graal configurable Bytecode DSL unwind exceptions
external: oracle/graal#14434
upstream contact: @chumer
initial impact: BENEFICIAL_BUT_NOT_ACTIONABLE
```

Its completed experiment belongs under `docs/project/evidence/UPSTREAM001/`.
After that evidence is retained, the open coordination Issue may remain
`status:paused` while waiting for upstream follow-up or a future Protos
prerequisite.

## Non-goals

GITHUB014 does not:

- change the Protos specification, implementation, or implementation version;
- upgrade GraalVM, Truffle, Java, Maven, or any dependency;
- make successful upstream experiments production commitments;
- add a new Project lifecycle state;
- create a new semantic/architecture decision family; or
- require upstream maintainers to be Protos assignees.

## Validation

The publication gate is bounded to coordination tooling and documentation:

```text
python3 scripts/issue_intake.py --self-test
git diff --check
```

Maven/runtime/specification tests are not applicable.
