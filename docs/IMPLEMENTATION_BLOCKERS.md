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
The current normative specifications do not uniquely state the language value
produced by evaluating a `Sequence` containing no expressions.

Specification authority:
- `spec/PROTOS_RUNTIME_SEMANTICS.md`
- `spec/PROTOS_LANGUAGE_SPEC.md`

Unblock condition:
The current normative specifications explicitly and uniquely determine the
result of evaluating an empty `Sequence`.

Current consequence:
`ProtosSequenceNode` and Canonical-to-Truffle lowering deliberately reject empty
sequences. Non-empty sequence execution is implemented.

Independent work:
Literal/value representation and all other execution work whose observable
semantics are already defined may continue. Internal host representation is an
implementation choice and is not, by itself, a normative blocker.

History:
B001 was temporarily broadened to "Runtime value materialization". That was too
broad: repository policy and the runtime specification explicitly permit
implementation-specific internal representations when observable Protos
semantics are preserved. The blocker is therefore narrowed back to the actual
unresolved observable case: the result of an empty `Sequence`.
