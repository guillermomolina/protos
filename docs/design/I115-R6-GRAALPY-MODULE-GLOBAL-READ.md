# I115-R6 — GraalPy-style module-global read

> **Status:** experimental implementation note for the I115 branch, non-normative.
> **Scope:** bare reads statically resolved to a module (ROOT) declaration.
> **Authority:** Protos `spec/` remains normative. This note records an
> experiment of I115; it does **not** declare a new normative D decision and
> does not amend the specification or its changelog.

## 1. Motivation

| Measurement | `run()` FULL DSL instructions |
|---|---|
| Protos I115-R5 factorial `run()` | 17 |
| GraalPy 25.4.4.1.1 `run()` | 6 |
| Protos I115-R6 target | 6 |

In R5, the read of `factorial` inside `run: () => { factorial(20) }` selects
the captured materialized owner frame under a nearer-scope absence guard,
branches, and merges a conditional fallback (`SelectCapturedMaterializedOwnerFrameAtRoot`,
`IsCapturedOwnerFrameSelected`, `load.local.mat`, `ReadCapturedFallbackAtRoot`,
`merge.conditional`, ...).

## 2. New sequence

```
c.EnterRoot
c.ReadGlobal
load.constant
c.InvokeClosureOneTail
c.EpilogForReturn
return
```

GraalPy reference: `c.EnterCalleeContext`, `c.ReadGlobal`, `load.constant`,
`c.CallUnaryMethod`, `c.EpilogForReturn`, `return`.

## 3. Recognition

A lookup qualifies when its resolution is
`CanonicalBindingResolution.CapturedResolved` and
`identity().owner().kind() == CanonicalLexicalScope.Kind.ROOT`. The existing
`identity()` and `lexicalDepth()` carry everything required. No other
namespace and no new resolution type is introduced.

Such reads are not admitted as direct parent captures for physical inlining
into a caller frame (`CanonicalBindingAnalysis`), because the caller's
captured environment need not contain the module at the same depth.

Inlining of callbacks that contain ROOT reads is **not admitted** in this
slice. If such a read ever reaches the lowering of an inline callback frame,
the lowerer raises an internal `AssertionError`: an admission failure is a
lowering bug, never an authorization to run the R5 capture algorithm.

## 4. Execution

`ReadGlobal(frameOrdinal, name, lexicalDepth)`:

1. Selects the exact module scope: `ProtosLexicalEnvironment.at(captured, lexicalDepth - 1)`
   of the invoked Closure's own captured chain (compact or already-published
   activation; the read never materializes the activation).
2. Reads the module's frame-backed authority directly; then, on a miss, its
   general authority (`readLocalSlotForRuntime`), which covers map-backed
   authority and representation changes.
3. Presence and values are live: every execution reads the Actor-local
   module binding again. `PRESENT(null)` returns guest null.
4. Only ABSENT consults the scopes outside the module (the prelude), in a cold
   `@TruffleBoundary` path; if still absent it raises `SlotNotFound`.
5. It never falls back to the receiver, never consults scopes between the
   Closure and the module, and never calls `ProtosLexicalFallback.readByName`.

## 5. Experimental semantic divergence

This breaks the D179 rule that the late creation of a nearer binding may
retarget any bare read. The exception affects **only** reads bound to ROOT.
CLOSURE-owned captures and bare assignments (`=`) keep their previous rules.

Consequently, a bare assignment may still select a nearer binding created
later, while a read of the same name statically resolved to ROOT stays bound
to the module. `lateNearerCreationRetargetsCapturedWrite` keeps independent
checks of both destinations: the nearer slot holds the assigned value, the
module slot is unchanged, and the later read returns the module binding.

I112 expectations: identity's read of its own parameter uses `load.local`,
due to earlier lowering compaction. It is not part of `ReadGlobal` and is not
a change introduced by R6.

## 6. Integrity

- B5 ABI unchanged (no new argument position).
- I112 terminal call unchanged.
- One interpreter, one `ProtosBytecodeRootNode`.
- Actor isolation unchanged: the binding remains mutable and Actor-local.
- `PRESENT(null)` remains distinct from ABSENT.
- No cache of global values.
- Rematerialization preserves the ROOT owner and `lexicalDepth`
  (`portableCapturedResolution`).

## 7. Comparison

- GraalPy: `ReadGlobalOrBuiltinNode`, globals from `PArguments.getGlobals`.
- GraalJS: `GlobalPropertyNode`.
- TruffleRuby: `ReadGlobalVariableNode`.
- SimpleLanguage: `SLFunctionLiteralNode` with `SLFunctionRegistry`.

## 8. Caveat

Six DSL operations do not by themselves prove an equivalent reduction of
Tier 1 or Tier 2 graph nodes or of execution time. Those measurements are a
later validation performed by the user.
