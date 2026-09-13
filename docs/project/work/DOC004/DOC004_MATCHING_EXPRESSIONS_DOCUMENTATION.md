# DOC004 — Matching expressions programming documentation

GitHub coordination: Issue [#447](https://github.com/guillermomolina/protos/issues/447), labelled `family:DOC`.

Status: **CLOSED**

## Purpose

DOC004 documents the complete programmer-facing matching functionality already
implemented and closed by I038/#391.

The work is explanatory only. It does not select new matching semantics, change
the grammar, change matcher/runtime behavior, or reopen ratified D071-D096/D103
decisions.

## Authority

Primary normative owners:

- [`spec/semantics/MATCHING.md`](../../../../spec/semantics/MATCHING.md);
- [`spec/PROTOS_GRAMMAR.md`](../../../../spec/PROTOS_GRAMMAR.md).

Implementation/conformance authority remains with I038 and its retained tests.
The Programming Guide is non-normative; specification text wins if a future
disagreement is discovered.

## Delivered documentation

DOC004 publishes
[Programming Guide chapter 12 — Matching Expressions](../../../guide/12-matching-expressions.md)
and adds it to the maintained guide navigation.

The chapter covers the complete #447 programmer-facing scope:

- postfix expression-valued `match { case ... }` and exactly-once subject
  evaluation;
- ordinary pattern-owned `pattern.match(subject)` authority;
- inherited `Object.match(subject)` value-pattern behavior;
- exact matcher result/capture carrier behavior;
- `@name`, `_`, aliases and `captures(...)`;
- D103 dynamic capture-rest terminality;
- standard Array patterns and fresh frozen Array remainders;
- standard normal-Map open/exact/remainder matching;
- ordered OR and first-success commitment;
- guards, strict Boolean guard results and arm continuation;
- ordinary Error propagation and terminal no-selection;
- fixed binding linearity and OR-interface compatibility;
- D096 structural unreachability/conservative coverage boundaries;
- D100 guard/arm `=>` delimiter ownership; and
- composed examples connecting protocol matching with structural source forms.

## Example provenance

Examples were checked against the current published parser/specification
surface rather than invented from design records.

Primary retained evidence includes:

- [`ProtosParserMatchTest.java`](../../../../src/test/java/com/guillermomolina/protos/parser/ProtosParserMatchTest.java);
- [`binder-wildcard.protos`](../../../../protos/tests/conformance/matching/binder-wildcard.protos);
- [`array-remainder-bindings.protos`](../../../../protos/tests/conformance/matching/array-remainder-bindings.protos);
- [`or-same-subject.protos`](../../../../protos/tests/conformance/matching/or-same-subject.protos);
- [`or-dynamic-captures.protos`](../../../../protos/tests/conformance/matching/or-dynamic-captures.protos); and
- [`guard-dynamic-rest-visible.protos`](../../../../protos/tests/conformance/matching/guard-dynamic-rest-visible.protos).

The documentation validation launcher checks that representative parser source
forms and these retained conformance fixtures are still present at publication
time. It does not execute the test suite.

## Documentation boundaries

DOC004 deliberately does not:

- duplicate the normative specification verbatim;
- define new optional/repetition/search/backtracking pattern families;
- infer generic sequence or mapping participation from lookalike protocols;
- introduce a first-class Pattern hierarchy or matcher registry;
- alter no-match Error semantics;
- expand the Core static source-error set beyond ratified rules; or
- modify executable implementation, tests, specification semantics, or the
  implementation version.

If documentation work uncovers a semantic or implementation/specification
inconsistency, DOC004 must stop at that discrepancy and route it to the owning
work/decision process rather than silently changing behavior.

## Validation and closure

DOC004 closes after publication only if:

- chapter 12 is linked from `docs/guide/README.md`;
- the DOC004 work record is linked from `docs/project/work/README.md`;
- relative links in the touched documentation resolve;
- `git diff --check` passes before publication;
- representative matching examples remain backed by the current parser and
  retained I038 conformance sources;
- no file under `spec/`, `src/`, `protos/`, or build/runtime configuration is
  modified by the slice;
- no specification semantic change is made;
- no executable implementation change is made;
- no implementation version change is made; and
- the full or focal executable test suite is **not run**, because this slice is
  documentation-only and #447 explicitly permits documentation-only
  validation.

Validation class: **GOVERNANCE_DOCUMENTATION_ONLY**.
