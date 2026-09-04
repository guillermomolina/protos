# Protos Implementation Blockers

This file records implementation work that is blocked by unresolved normative
semantics. It is implementation state, not a normative specification.

Agents must re-check every blocker against the current normative specification
on the current `main` branch. The specification state recorded when a blocker
was created is historical context only.

Blocker states:

- `BLOCKED`: the normative unblock condition is not yet satisfied.
- `READY`: the current normative specification satisfies the unblock condition,
  but the blocked implementation work has not yet been completed.
- `CLOSED`: the dependency was resolved and the corresponding implementation
  work was completed or made obsolete.

Do not mark a blocker `READY` merely because a relevant specification file
changed. Verify the stated unblock condition against the current normative text.

## B001 — Empty Sequence execution

Status: BLOCKED

Implementation area:
Truffle lowering / execution of a `CanonicalSequence` containing zero
expressions.

Normative dependency:
The observable result of evaluating an empty `Sequence` must be unambiguous,
and the runtime must provide the corresponding Protos value without treating
Java `null` as Protos `null`.

Specification authority:
- `spec/PROTOS_RUNTIME_SEMANTICS.md`
- `spec/PROTOS_LANGUAGE_SPEC.md`, if language-level clarification is required

Unblock condition:
The current normative specifications uniquely determine the result of
evaluating an empty `Sequence`, and the implementation has enough normative
runtime-value semantics to represent that result without exposing Java `null`
as a Protos language value.

Independent work:
Non-empty `Sequence` execution and unrelated Truffle/runtime work may continue.
