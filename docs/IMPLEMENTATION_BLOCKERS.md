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

## B001 — Runtime value materialization

Status: BLOCKED

Implementation area:
Truffle execution of canonical expressions that produce language values,
including literals and the result of an empty `CanonicalSequence`.

Normative dependency:
The implementation needs a complete, implementation-independent runtime
representation contract for Core Protos values at the Truffle execution
boundary. In particular, execution must not expose Java `null` as a substitute
for the Protos `null` value, and the representation must preserve the semantic
distinctions required by the normative value model.

Specification authority:
- `spec/PROTOS_RUNTIME_SEMANTICS.md`
- `spec/PROTOS_LANGUAGE_SPEC.md`

Unblock condition:
The current normative specifications uniquely determine the observable value
produced by the affected canonical expressions and provide sufficient runtime
value semantics to implement that behavior without inventing a host-language
representation rule.

Current consequence:
`ProtosSequenceNode` deliberately requires at least one expression. Empty
sequence execution remains blocked. Literal execution is likewise not started
where doing so would require inventing the runtime representation contract.

Independent work:
Execution infrastructure and runtime work whose observable behavior does not
depend on unresolved value materialization may continue.
