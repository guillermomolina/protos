# Protos Language Specification Changelog

All notable changes to the Protos language specification will be documented in this file.

Specification version follows the document revision number: 0.1.X where X is the revision.

## [0.1.75] - 2026-09-02

### Changed
- Closed audit issue D6: an Actor's initial module participates in module identity and caching exactly like an imported module when it has an importable canonical identity. When the initial module can be resolved by `import()` to a canonical `ModuleKey`, the runtime determines or assigns that key, creates the module instance and its `moduleContext`, inserts it into the Actor-local module cache in state `INITIALIZING`, executes the module body, transitions it to `READY` on success, and applies the same cache-removal rule as any other failed module initialization. The cache-before-execute invariant therefore covers an importable initial module, which is not a special mutable module instance that exists outside the cache.
- A cyclic import back to the initial module returns that same cached instance and cannot create a second initial-module instance. When the initial module `main` imports `b` and `b` imports `main`, the recursive import returns the original `main#1`; no `main#2` is created.
- A host entry point with no importable canonical identity (a host-defined startup mechanism that the module resolver cannot map to a `ModuleKey`) need not be assigned a fabricated filesystem/package identity. It remains Actor-local, its mutable `moduleContext` is not shared with another Actor, it must not alias an imported module, and the normal canonical-identity rules apply if the host later gives it a canonical identity.
- "Module singleton per Actor" is clarified to mean at most one active cached module instance per canonical `ModuleKey`: the Actor-local module cache maps `ModuleKey` to the current active module record, with at most one record per key at a time. The phrase does not guarantee that only one object per canonical module identity can ever remain reachable during the Actor's lifetime.
- Cache membership and ordinary object reachability are distinct concepts. Escaped references to a failed partial instance may remain reachable after its cache entry is removed, and such an instance may coexist with a later fresh cached instance (`foo#1 !== foo#2`); only the later instance is the Actor's active cached module instance for that `ModuleKey`. Both objects belong to the same Actor, so coexistence does not violate Actor isolation.
- Failed-instance references are not revoked, not rolled back, and do not enter a hidden invalid-object state; removing a cache entry does not invalidate the instance object. No tombstone, revocation, identity mutation, or hidden invalidation is introduced.
- Runtime pseudocode in `PROTOS_RUNTIME_SEMANTICS.md` is factored so that ordinary import and Actor startup of an importable initial module share one module-instance lifecycle (`ensureModuleInstance`, called by `importModule` and `executeInitialModule`), making creation of a duplicate instance for the same canonical identity impossible. A non-importable host entry point is executed directly by `executeStandaloneEntry` without fake cache registration.
- No syntax and no grammar production changed; D5 semantics remain unchanged in full (module instance = `moduleContext`; Actor-local module caches; canonical `ModuleKey`; cache-before-execute; `INITIALIZING` and `READY`; cyclic imports are legal; recursive import of an `INITIALIZING` module returns the same partial instance immediately; no hidden suspension; no module TDZ, slot predeclaration, or hoisting; ordinary missing-slot semantics; successful initialization retains the cached instance; failed initialization removes the cache entry and a later import may retry with a fresh instance; no rollback of side effects; escaped references to failed partial instances are not revoked; host-specific module-specifier resolution remains host-defined; module instances remain Actor-local; immutable compiled/code artifacts may be shared invisibly).
- Updated canonical documents to revision 75: `PROTOS_LANGUAGE_SPEC.md` (initial-module cache registration, module-cache authority, escaped-failed-instance coexistence) and `PROTOS_RUNTIME_SEMANTICS.md` (factored module-instance lifecycle pseudocode). `PROTOS_GRAMMAR.md` was not modified. `PROTOS_CONCURRENCY_MODEL.md` (a design ledger, not a canonical document) was updated to its independent document revision 06 to replace the imprecise "module singleton per Actor" wording with the active-cached-instance invariant and to confirm that the RootActor's initial module is not outside the module model merely because it started the Process.

## [0.1.74] - 2026-09-02

### Changed
- Closed audit issue D5: module instances are Actor-local. A module instance belongs to exactly one Actor, and each Actor owns an independent module cache. Importing a module never provides access to mutable module state belonging to another Actor, and there are no process-global mutable module instances. The rule is explicitly consistent with the Actor isolation principle that no shared mutable Protos memory exists between Actors.
- Normative decision: a module instance is the module's `moduleContext` object. The module body executes with the module instance as its current execution context, top-level `:`-created bindings are local slots of the module instance, `import(specifier)` yields the module instance, and reading a member of a module instance observes the top-level binding slots exactly as they exist at that moment. There is no separate namespace object, wrapper, copy, or proxy, and module identity is ordinary object identity (`===`).
- Immutable compiled/code artifacts (parsed syntax, bytecode, machine code, immutable metadata, immutable constant data where otherwise semantically valid) may be physically shared between Actors, provided such sharing never exposes shared mutable Protos state. The observable `moduleContext` and mutable module state remain Actor-local.
- Normative decision: cache-before-execute. When an Actor imports a canonical module absent from that Actor's cache, the runtime creates the module instance, inserts it into the Actor-local module cache in state `INITIALIZING`, and only then executes the module body in that instance's `moduleContext`. The module is therefore discoverable through recursive imports before its body finishes executing.
- Module initialization states conceptually include `INITIALIZING` and `READY`. These are semantic concepts and are not exposed through a public state-inspection or reflection API. A transient internal failure state is permitted while a failed initialization is handled, but a failed initialization must not remain cached as a successfully importable module.
- Repeated imports within one Actor of the same canonical module identity reuse the same Actor-local module instance (`a === b` for `a: import("foo")` followed by `b: import("foo")` when both resolve to the same canonical identity); the module body is not executed again for the second import. Across Actors the same canonical module identity produces distinct Actor-local module instances with distinct mutable `moduleContext`s.
- Cyclic imports are legal. A cycle is not rejected merely because it is cyclic, and no `ModuleInitializationCycle` error is signaled. Recursive import of a module already `INITIALIZING` returns the same partially initialized module instance immediately and does not create a hidden suspension point; suspending would deadlock ordinary cyclic imports within the same Actor, and Actor reentrancy remains identifiable only from explicit suspension operations.
- Partially initialized modules are observable: only slots whose creating top-level statement has already executed are present, and reading a slot that has not yet been created follows the ordinary Protos missing-slot / lookup error semantics. No module-specific temporal-dead-zone mechanism, no predeclaration of module slots, and no hoisting of future slot creations is introduced; normal Protos slot semantics remain authoritative. A partially initialized module is the real module instance in its current state, not a placeholder copy.
- Successful initialization transitions the cached module instance from `INITIALIZING` to `READY`. The same module instance and the same `moduleContext` remain cached, and a later import in that Actor returns that instance without re-executing the module body. No new module identity is created because initialization completed.
- Failed initialization removes that attempt's entry from the Actor's module cache; the initiating `import()` fails with that error according to the normal error-propagation model, and a later import may attempt initialization again and may create a fresh module instance. A failed attempt does not permanently poison the Actor's module cache, and a failed partial module instance is not defined as reusable by a later independent import.
- Failed initialization does not roll back effects already performed. Removing the failed module from the module cache does not time-travel or undo side effects already executed during its failed initialization, and references to a partially initialized instance obtained by cyclic participants before failure are not revoked.
- Actor lifetime and module lifetime coincide: an Actor's module cache and its Actor-local module instances die with the Actor, and creating a new Actor does not inherit the creator's module cache or live module contexts. An Actor's initial module follows the same module-context model and is Actor-local rather than process-global.
- `import()` is not redefined to inject bindings into the importing lexical context. The module specifier remains an ordinary expression, cross-module visibility remains explicit, and no ES-module-style static binding declarations, CommonJS `exports`, Python namespaces, or analogous module syntax is introduced. Host-specific module-specifier resolution remains host-defined; module identity, Actor-local instance, cache, initialization, cycles, and failure are defined by Protos semantics.
- Updated canonical documents to revision 74: `PROTOS_LANGUAGE_SPEC.md` (Actor-local module instance identity, caching, initialization, cycles, and failure) and `PROTOS_RUNTIME_SEMANTICS.md` (Actor-local module cache pseudocode and module states). No grammar production and no grammar prose changed; `PROTOS_GRAMMAR.md` was not modified.

## [0.1.73] - 2026-09-02

### Changed
- Closed audit issue D4: blank-content-line whitespace stripping is now explicitly scoped to multiline String literals whose closing `"""` delimiter establishes a structural indentation prefix.
- Normative decision: blank-line whitespace stripping is part of multiline indentation normalization and occurs only when a structural indentation prefix exists. Where such a prefix exists, blank content lines remain exempt from prefix matching and need not contain the complete structural prefix, and their SPACE/TAB characters are removed as incidental source-formatting indentation so that a source blank line contributes an empty logical line.
- Where content flows into the closing delimiter on its source line and no structural indentation prefix exists, no indentation normalization is performed and no indentation or other whitespace is removed from any content line, including whitespace-only content lines, whose SPACE and TAB characters are ordinary String content and are preserved verbatim. No separate unconditional blank-line-cleanup rule is introduced.
- The conceptual invariant is now explicit in all canonical documents: no structural indentation prefix ⇒ no indentation normalization.
- No other multiline String semantics changed. Revision 72 semantics are preserved in full: the closing delimiter is the sole source of the structural indentation prefix; there is no minimum-common-indent, longest-common-prefix, visual-column, or editor-TAB-width computation; SPACE and TAB remain distinct exact source characters; mixed SPACE/TAB prefixes remain legal when matched exactly; prefix mismatch on a non-blank content line remains a lexical error; prefix matching/removal precedes escape interpretation and escape sequences never satisfy the prefix; opening structural-newline removal and closing structural newline/trailing-line removal are unchanged; retained LF/CR/CRLF code points are unchanged; same-line delimiter behavior remains valid; triple-double-quoted Strings remain non-raw; single-quoted and double-quoted String semantics are unchanged; String interpolation remains unsupported; and the String lexical grammar and quote-run rules are unchanged. No grammar production was modified.
- Updated canonical documents to revision 73: `PROTOS_LANGUAGE_SPEC.md` (normative indentation-normalization semantics and examples), `PROTOS_GRAMMAR.md` (lexical indentation rules, notes, and Compact EBNF explanatory prose), and `PROTOS_RUNTIME_SEMANTICS.md` (String-value semantics).

## [0.1.72] - 2026-09-02

### Changed
- Closed audit issue D3 (triple-double-quoted String indentation): the structural indentation prefix is established solely by the closing `"""` delimiter, never by the content lines.
- When the closing delimiter terminates an indentation-only trailing line, the structural indentation prefix is exactly the sequence of SPACE and TAB characters on that source line immediately preceding the closing delimiter and may be empty. When content flows into the closing delimiter on its source line, no structural indentation prefix exists and no indentation is removed.
- Matching and removal operate on exact source characters. Every non-blank content line must begin with exactly the structural indentation prefix; the prefix is removed exactly once from each such line, and any additional indentation after the prefix is content and is preserved.
- SPACE and TAB are distinct source characters and are never equivalent for indentation purposes; a TAB equals no number of SPACE characters, and Core v0.1 defines no semantic TAB width. Matching is by exact source-character prefix, not by visual column, and no editor-tab-stop rule exists. Mixed SPACE/TAB prefixes are legal when each content line begins with exactly the same prefix.
- No minimum-indent, common-visual-column, or longest-common-prefix algorithm is used to compute indentation from the content lines.
- A non-blank content line that does not begin with the exact structural indentation prefix — fewer prefix characters, SPACE where the prefix requires TAB, TAB where the prefix requires SPACE, or any other difference — makes the triple-double-quoted String invalid. Consistent with the existing String-literal lexical-error model this is a lexical error: no String token and no String value is produced, and no recovery behavior is defined.
- Blank content lines are exempt from the prefix requirement and need not contain the complete structural indentation prefix; their SPACE/TAB characters are removed as incidental source-formatting indentation, so a source blank line contributes an empty logical line. No intentional whitespace is removed from a non-blank content line beyond the single structural prefix.
- Indentation matching and stripping are based on the raw source characters at the beginning of each content line, before escape processing; an escape sequence never counts as source indentation and never satisfies the structural prefix.
- The existing triple-String delimiter, quote-run, escape-set, unterminated-literal, and opening/closing structural-newline rules are unchanged, and single-quoted and double-quoted String semantics are unchanged.
- Updated canonical documents to revision 72: `PROTOS_LANGUAGE_SPEC.md` (normative multiline indentation semantics and examples), `PROTOS_GRAMMAR.md` (lexical indentation rules, notes, and examples), and `PROTOS_RUNTIME_SEMANTICS.md` (String-value semantics).

## [0.1.71] - 2026-09-02

### Changed
- Closed audit issue D2: every Protos object may serve as the delegation parent of another object.
- "Prototype" describes a role that an object plays when another object delegates to it; it is not a distinct object category. No parentability capability, flag, type, predicate, or hidden classification is introduced.
- The rule applies without exception to ordinary objects, built-in objects, immutable value objects, singleton values, execution-context objects, and every other Protos object. Values such as `this`, `context`, `args`, `true`, `false`, `null`, Number values such as `42`, and String values such as `"hello"` may serve as delegation parents, as may the standard built-in prototype objects (`Object`, `Number`, `Integer`, `Float`, `String`, `Boolean`).
- Using an object as a delegation parent does not make the newly created child identical to that parent and does not transfer the parent's value identity or value-category membership to the child. For example, `answer: (42) { ... }` creates an ordinary identity-bearing object delegating to the Number value `42`, so `answer === 42` is false. No coercion or value inheritance is introduced: `answer + 1` is not specified to behave as numeric `43` merely because `answer` delegates to `42`.
- Delegated message lookup through such a parent preserves the original receiver under the existing receiver-preserving delegation rules; `this` remains the child.
- No parentability classification or runtime check is introduced, and the runtime is not required to allocate a unique heap object for a value parent such as `42`; immediate/tagged representations of value parents remain permitted.
- The `parent-expression` grammar is unchanged and was neither broadened nor restricted; no inconsistency requiring correction was discovered during audit. Its broad forms (`identifier`, `intrinsic-reference`, `member-expression`, `parenthesized-expression`) are confirmed as intentional, and literal parents continue to require parentheses (`(true)`, `(42)`, `("hello")`, ...).
- `Object` remains the unique root and has no delegation parent, but may still serve as the parent of another object; bare `{ ... }` already creates an object whose parent is `Object`.
- Value identity of `Number`/`String`/`Boolean`/`null`, `true`/`false`/`null` singleton semantics, immediate/tagged representation freedom, receiver-preserving delegation, `Object` as unique root, immutable delegation parents, and open/closed/frozen object semantics are unchanged.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.70] - 2026-09-02

### Changed
- Closed audit issue D1: all seven Core v0.1 reserved-word spellings (`this`, `context`, `args`, `super`, `true`, `false`, `null`) are now valid contextual member names in the structural position immediately following a member-access `.`.
- Introduced the grammar category `member-name` in `PROTOS_GRAMMAR.md`: `member-name = identifier | "this" | "context" | "args" | "super" | "true" | "false" | "null"`. `member-name` is used only where the grammar structurally expects a name immediately after `.`: `member-suffix`, `member-expression`, and `super-message-send`. The Compact EBNF contains the same definition and updates. `identifier` remains the ordinary lexical/binding-name grammar category and was not globally replaced by `member-name`.
- A reserved spelling used as a `member-name` denotes an ordinary slot or message name and does not retain its expression-level intrinsic/literal/special meaning. Therefore `obj.this`, `obj.context`, `obj.args`, `obj.super`, `obj.true`, `obj.false`, `obj.null`, `obj.true()`, `obj.null = value`, `obj.super: value`, `obj.a.this`, and `f: obj.true` are valid structural member operations, subject to the existing runtime rules for reading, invoking, modifying, or creating the selected slot.
- Lexical reserved-word classification is unchanged: the lexer continues to tokenize the seven spellings as their dedicated reserved tokens rather than as ordinary identifier tokens. This revision introduces no contextual lexing; the parser accepts either an identifier token or one of the seven reserved tokens when parsing `member-name`.
- Bare reserved-word semantics are unchanged: `this`, `context`, `args`, `true`, `false`, and `null` retain their ordinary expression-level meanings, and bare `super` remains invalid. Reserved words remain invalid where the grammar expects `identifier`, including parameter names, rest-parameter names, bare assignment targets, and bare slot-creation targets: `this: value`, `context: value`, `args: value`, `super: value`, `true: value`, `false: value`, `null: value`, `(a, true) => { ... }`, `(...super) => { ... }`, bare `super`, `foo(super)`, and `f: super.foo` all remain invalid.
- The leading `super` of `super.foo()` continues to introduce the existing `super-message-send`; the name after the dot is now a `member-name`, so reserved spellings are valid super message names: `super.true()`, `super.this()`, and `super.super()` are syntactically valid super message sends whose message names are `true`, `this`, and `super`, respectively. This does not make `super` a first-class value, and method extraction (`f: super.foo`) remains unsupported.
- No reflection API and no arbitrary-String slot-name rule are introduced by this revision. The revision decides only the relationship between ordinary identifier spellings, the seven reserved-word spellings, and structural member syntax after `.`.
- No runtime semantics changed: `member-name` is a grammar-level category, and the runtime continues to operate on ordinary slot/message names.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.69] - 2026-09-02

### Changed
- Closed audit issue C3d: the normative grammar in `PROTOS_GRAMMAR.md` is now self-contained for parser productions. Every parser nonterminal referenced by a normative EBNF production is now defined in the normative body, either directly in the relevant normative section or by an explicit normative cross-reference to a production defined elsewhere in the same document.
- Added the normative operator/expression hierarchy to the Operators section, preserving exactly the existing Compact EBNF structure and precedence semantics: `binary-expression`, `logical-or-expression`, `logical-and-expression`, `equality-expression`, `equality-operator`, `comparison-expression`, `comparison-operator`, `additive-expression`, `additive-operator`, `multiplicative-expression`, `multiplicative-operator`, `custom-binary-expression`, `unary-expression`, and `unary-operator`.
- Added the normative `member-expression` production in the Member Access, Calls, Indexing, and Postfix Expressions section, preserving the existing grammar exactly: `member-expression = primary-expression, { postfix-operation }, ".", identifier`.
- Added the normative `intrinsic-reference` production in the Primary Expressions section, preserving revision 68 / C6 exactly: `intrinsic-reference = "this" | "context" | "args"`. `true`, `false`, and `null` remain literals only; `this`, `context`, and `args` remain intrinsic references only; `super` remains governed exclusively by `super-message-send`.
- No syntax, precedence, associativity, tokenization, parsing behavior, or desugaring changed: the normative additions are the same productions already present in the Compact EBNF, moved/aligned into the normative body, and no new parser alternative or lexical rule was introduced.
- The Compact EBNF remains the compact consolidated view of the same grammar and is unchanged apart from its revision meta-note, updated to revision 69.
- No runtime semantics changed.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.68] - 2026-09-02

### Changed
- Closed audit issue C6: removed the duplicate syntactic classification of `true`, `false`, and `null`.
- `true`, `false`, and `null` are literals only; `this`, `context`, and `args` are intrinsic references only. `primary-expression` continues to contain both `literal` and `intrinsic-reference`, so each of `true`, `false`, and `null` now has exactly one syntactic derivation from `primary-expression`, through `literal`, while `this`, `context`, and `args` remain valid through `intrinsic-reference`.
- The Compact EBNF `intrinsic-reference` production now contains only `"this"`, `"context"`, and `"args"`.
- The `literal` production is unchanged and remains `number-literal | string-literal | "true" | "false" | "null"`.
- `super` remains governed exclusively by `super-message-send` and is not added to `intrinsic-reference`.
- All seven reserved words (`this`, `context`, `args`, `super`, `true`, `false`, `null`) are unchanged: the lexical reserved-word rules are untouched.
- No runtime semantics changed: the values produced by these literals and intrinsic references are unchanged.

### Unresolved
- C3d (broader normative-grammar self-containment beyond the String forms) remains unresolved and is unchanged by this revision.

## [0.1.67] - 2026-09-02

### Changed
- Closed issue C3c: all three supported Core v0.1 String literal forms now have formal lexical grammar.
- The normative lexical grammar in `PROTOS_GRAMMAR.md` now formally defines `single-quoted-string`, `double-quoted-string`, and `triple-double-quoted-string`, together with the helper productions they require.
- The valid escape set is formally encoded unchanged: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`. No escape is added, removed, or reinterpreted; `\0`, `\xNN`, octal escapes, named Unicode escapes, and interpolation escapes remain unsupported.
- The Unicode escape source shape is formally encoded as `\u{` followed by 1 to 6 hexadecimal digits followed by `}`; hexadecimal digits are `0`-`9`, `a`-`f`, and `A`-`F`, and underscore separators are not part of the Unicode escape shape.
- Unicode scalar validity remains lexically enforced: a `\u{HEX}` value that is not a valid Unicode scalar value (surrogates, or values greater than U+10FFFF) is a lexical error.
- Single-quoted and double-quoted raw-newline restrictions are unchanged: a logical source newline before the matching closing quote remains a lexical error, and both forms remain single-line literals.
- Triple-double-quoted String logical-newline and indentation behavior is unchanged; the lexical grammar permits logical source newlines in triple-double content and does not alter opening/trailing newline removal or indentation normalization.
- C8 quote-run behavior is unchanged: triple-double opening priority, first-three-unescaped-quotes closing, exact-three-quote delimiters, escaped-quote non-participation, and post-delimiter quote lexing are all preserved by the formal grammar, with the C8 prose remaining authoritative.
- C7 unterminated behavior is unchanged: the valid-token grammar requires a closing delimiter, and no EOF alternative or partial String token is introduced.
- C4 malformed/incomplete escape behavior is unchanged: only valid escape sequences are listed, and a backslash followed by anything outside the valid escape grammar remains a lexical error.
- Triple-single-quoted strings remain unsupported.
- String interpolation remains unsupported.
- No implicit String-literal concatenation is introduced.
- Indentation normalization is unchanged.
- The Compact EBNF now defines `string-literal` and references the normative String lexical productions instead of leaving them dangling; the Compact EBNF revision meta-note was updated to revision 67.
- No runtime semantics changed: this revision formalizes source spelling only.

### Unresolved
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.
- C3d (broader normative-grammar self-containment beyond the String forms) remains unresolved and is unchanged by this revision.

## [0.1.66] - 2026-09-02

### Changed
- Closed issue C8: triple-double quote-run tokenization is now deterministic.
- Triple-double-quoted String opening-delimiter recognition takes priority when `"""` occurs at the current lexical position outside a String: three consecutive unescaped double quotes begin a triple-double-quoted String rather than an ordinary double-quoted String opener followed by another double quote.
- Inside a triple-double-quoted String, the first three consecutive unescaped double-quote characters form the closing delimiter.
- Opening and closing delimiters consume exactly three double quotes; there is no greedy rule that consumes a run of four, five, six, or more quotes as one delimiter.
- One or two consecutive unescaped double quotes inside triple-double content are ordinary content when they do not begin a closing delimiter.
- Quotes remaining after a closing delimiter are lexed normally from the next lexical position.
- An escaped double quote (`\"`) is String content and does not participate in closing-delimiter recognition; no new triple-quote escape is introduced, and the meaning of `\"` is unchanged.
- No lexical backtracking occurs to rescue later malformed tokenization: quote-run decisions are not revised based on whether later tokenization or parsing succeeds.
- Empty triple-double-quoted Strings remain valid (`""""""`).
- No implicit adjacent String-literal concatenation was introduced; a lexically valid sequence of adjacent String tokens remains subject to the ordinary expression grammar.
- C7 behavior is unchanged: if quote-run tokenization begins a new String lexical construct that reaches the end of source before its required closing delimiter, the source is rejected as a lexical error.
- C4 escape behavior is unchanged: malformed or incomplete escape sequences remain lexical errors, and quote-run recognition must not reinterpret characters already consumed as part of an escape.
- Multiline newline and indentation behavior is unchanged: logical source newlines, retained newline code points, opening/trailing newline removal, and indentation normalization in triple-double-quoted Strings are unaffected.
- No runtime semantics changed: quote-run recognition is lexical behavior.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.65] - 2026-09-02

### Changed
- Closed issue C7: reaching the end of source before the required closing delimiter of a String literal is now a lexical error.
- The rule applies to all three Core v0.1 String forms: single-quoted (`'...'`), double-quoted (`"..."`), and triple-double-quoted (`"""..."""`).
- An unterminated String literal never produces a partial String token. The lexer must not recover by treating the opening quote as another token, emitting the accumulated content as a partial String, splitting the malformed literal into otherwise valid tokens, implicitly inserting a closing delimiter, or interpreting the end of source as the closing delimiter; the parser never receives a successfully formed String token for an unterminated literal.
- Existing single-quoted and double-quoted raw-newline lexical errors are unchanged: a logical source newline before the matching closing quote remains a lexical error and terminates String recognition before any end-of-source determination.
- Existing malformed/incomplete escape lexical errors are unchanged. Reaching the end of source after a backslash or during an incomplete escape while String recognition is active is lexically invalid, without requiring a normative diagnostic-category precedence between an "incomplete escape" and an "unterminated String".
- Triple-double-quoted multiline String newline, indentation, and delimiter behavior is unchanged.
- No runtime semantics changed: malformed source literals are rejected during lexing, before parser/runtime semantics.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.64] - 2026-09-02

### Changed
- Closed issue C4: invalid or incomplete String escape sequences are now classified as lexical errors rather than syntax errors.
- Escape validation is part of String-token lexing: the lexer rejects malformed escape syntax before the parser receives a String token, and the parser does not inspect the interior of a successfully formed String token in order to validate escape syntax.
- The existing Core v0.1 valid escape set is unchanged: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- `\u{HEX}` retains its existing requirements: exactly 1 to 6 hexadecimal digits, and the resulting value must denote a valid Unicode scalar value. Values outside the Unicode scalar-value range and surrogate code points are invalid escapes and produce a lexical error.
- No valid escape sequence is added, removed, or reinterpreted; `\xNN`, octal, `\0`, named Unicode, and interpolation escapes remain unsupported.
- Raw-newline behavior is unchanged: a logical source newline before the matching closing quote in single-quoted and double-quoted String literals remains a lexical error.
- Triple-double-quoted String newline and indentation behavior is unchanged, and triple-double-quoted strings remain non-raw strings using the same escape set.
- String interpolation remains absent, and triple-single-quoted strings remain unsupported.
- No runtime semantics changed: malformed source literals are rejected during lexing, before parser/runtime semantics.

### Unresolved
- C3c (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`) remains unresolved and is unchanged by this revision.
- D3 (triple-double-quoted String indentation details: TAB width, visual columns, TAB-to-SPACE equivalence, how common indentation is computed when SPACE and TAB are mixed, and whether mixed SPACE/TAB indentation is legal) remains unresolved and is unchanged by this revision.

## [0.1.63] - 2026-09-02

### Changed
- Closed issue C3b by giving `custom-binary-operator` a complete normative lexical definition; it is no longer an undefined EBNF reference.
- Formalized the existing custom symbolic operator character alphabet unchanged: `! $ % & * + - / < = > ? @ \ ^ | ~`.
- The normative lexical grammar now defines `operator-character`, `symbolic-operator-spelling`, and `custom-binary-operator`; the candidate maximal symbolic token is a non-empty sequence of consecutive `operator-character` code points.
- Maximal-munch and reserved-spelling classification are formalized without semantic changes: the lexer first forms the longest valid symbolic spelling at a source position, then classifies the complete spelling as a reserved/standard token when it exactly matches a reserved/standard symbolic spelling and as `CUSTOM_OPERATOR` otherwise; a longer custom spelling is never split in order to prefer a shorter reserved/standard token.
- Exact standalone `!` remains prefix logical negation (`not()` lowering) and exact standalone `^` remains non-local return; neither is a `custom-binary-operator`.
- Standard operator spellings (`=>`, `=`, `==`, `===`, `!=`, `!==`, `<=`, `>=`, `&&`, `||`, `+`, `-`, `*`, `/`, `%`, `<`, `>`) and the exact `!` and `^` remain excluded from the custom operator category and keep their dedicated grammar roles and precedence.
- Longer non-reserved symbolic spellings, including those containing or beginning with standard-operator characters, remain custom binary operators where already specified (for example `!!`, `^^`, `!^`, `^!`, `@`, `|>`, `<=>`).
- Precedence and associativity are unchanged: all custom binary operators share one precedence domain, associate left-to-right, and mixing them with standard binary operators without explicit grouping remains a syntax error.
- Structural punctuation and special syntax are unchanged: `.`, `:`, `;`, `,`, `(`, `)`, `{`, `}`, `[`, `]`, and the `...` ellipsis token do not participate in custom operator tokens; decimal-dot, ellipsis, closure `=>`, assignment `=`, and slot-creation `:` behavior are unchanged.
- No runtime semantics changed: message-send lowering, operator dispatch, Boolean laziness, non-local return, and precedence behavior are unchanged.

### Unresolved
- The remaining C3 items (`single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`, and broader normative-grammar self-containment) remain unresolved and are unchanged by this revision.

## [0.1.62] - 2026-09-02

### Changed
- Closed issue C3a by giving `number-literal` a complete normative lexical grammar; `number-literal` is no longer an undefined EBNF reference.
- The new grammar makes the numeric token families explicit: `decimal-number-literal`, `binary-integer-literal`, `octal-integer-literal`, and `hexadecimal-integer-literal`, with helper productions for decimal digits, digit sequences, fractional parts, exponent parts, radix prefixes, and radix digits.
- Existing numeric syntax was formalized without semantic changes: decimal integers (including leading zeroes), decimal fractional literals, decimal exponent forms, binary/octal/hexadecimal integer literals, and `_` digit separators as previously specified.
- The previously accepted radix prefix case behavior is preserved exactly: `0x`/`0X`, `0b`/`0B`, and `0o`/`0O`.
- Decimal-dot tokenization is unchanged: a `.` belongs to a decimal numeric literal only when immediately followed by a decimal digit, so `1.` remains an integer literal followed by a structural dot and `.5` remains a structural dot followed by an integer literal.
- Malformed numeric continuations remain lexical errors rather than token splitting: `0x`, `0xG`, `0b2`, `0o8`, `2e`, `2e+`, `1__2`, `1_`, `0x_FF`, and `123abc` remain lexical errors.
- Unsupported radix floating-point attempts remain lexical errors: `0b10.5`, `0o17.2`, and `0xFF.1` are not split into radix integer, dot, and integer tokens.
- No runtime semantics changed: numeric value families, literal value production, equality, identity, and arithmetic behavior are unchanged, and no runtime lexing machinery is added.

### Unresolved
- The remaining C3 items (`custom-binary-operator`, `single-quoted-string`, `double-quoted-string`, `triple-double-quoted-string`, and broader normative-grammar self-containment) remain unresolved and are unchanged by this revision.

## [0.1.61] - 2026-09-01

### Changed
- Closed issue C2 by unifying the conflicting `object-body` definitions: the normative grammar now defines `object-body = "{", object-body-sequence, "}"`, and the stale `object-body = "{", expression-sequence, "}"` definition is removed.
- Object bodies use `object-body-sequence`, with `object-body-line-items`, `object-body-line`, `object-body-item`, and `composition-item` productions that mirror the ordinary `expression-sequence` separator structure.
- An object-body item is either an ordinary `expression` or a contextual `composition-item` of the form `...expression`; composition items and ordinary expressions share the same logical-`NEWLINE`/inline-`;` separator rules, and there is no implicit adjacency separator.
- `...expression` remains contextual to object bodies and is not a general expression: it is not added to `expression`, `primary-expression`, or `closure-body`. Closure bodies therefore continue to contain only ordinary expressions, and `() => { ...base }` does not become valid merely because closure bodies use braces.
- No runtime composition semantics changed: conflict handling, binding copying, evaluation order, error behavior, and the existing composition representation are unchanged.

### Unresolved
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.60] - 2026-09-01

### Changed
- Resolved newline placement for trailing closures (issue B7): a trailing closure must have no intervening logical `NEWLINE` token after the completed call. `foo() { ... }` attaches the braces as a parameterless trailing closure appended as the final call argument, while `foo()` followed by `{ ... }` on a later source line does not attach them.
- A completed call is syntactically complete, so a following logical `NEWLINE` acts as the ordinary expression separator under the B2 complete-expression newline rule; repeated separating `NEWLINE` tokens (blank lines) under B4 likewise do not permit trailing-closure attachment, and a `;` between the call and the braces does not attach them.
- `{` is not added as a complete-expression newline continuation exception: the only complete-before-newline continuation exception remains the existing leading structural/member `.` rule from revision 55 (B2).
- Horizontal whitespace between the completed call and the closure body remains ordinary lexical separation and is permitted.
- Block comments may appear between the call and the trailing closure, including block comments containing source newlines, because newlines inside a block comment produce no `NEWLINE` tokens (A6). A line comment prevents same-sequence attachment because its terminating newline remains tokenized as a separating `NEWLINE`.
- Indentation plays no role in trailing-closure attachment: the decision concerns logical `NEWLINE` tokens, not physical source formatting.
- The trailing-closure production `trailing-closure = closure-body ;` and the call-suffix form `argument-list, [ trailing-closure ]` are preserved; the no-intervening-`NEWLINE` restriction is stated normatively rather than through new grammar machinery, and no special `same-line` lexical token is introduced.
- B2, B4, A4, and A6 semantics are unchanged, and revision 59 semantics are unchanged: trailing closures remain parameterless and B6 remains closed; `foo() (x) { body }` is still not trailing-closure syntax, and `foo((x) => { body })` remains an ordinary call.
- No runtime mechanism is added: only syntactically attached trailing closures reach trailing-closure lowering.

### Unresolved
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.59] - 2026-09-01

### Changed
- Simplified trailing-closure syntax (resolves issue B6): a trailing closure is now always parameterless. `foo(args...) { body }` remains supported and still appends `() => { body }` as the final call argument.
- The parameterized trailing-closure form `foo(args...) (params...) { body }` is removed from Core v0.1: it is no longer recognized as trailing-closure syntax.
- A closure that requires parameters is written as an ordinary explicit closure expression in ordinary call-argument position, for example `items.each((item) => { print(item) })` and `collection.reduce(initial, (acc, item) => { ... })`.
- A trailing closure never has a parameter list. `(x)` remains an ordinary parenthesized expression and `(x) => { body }` remains an ordinary closure expression; there is no third interpretation of `(x)` as trailing-closure parameters.
- B6 is therefore resolved structurally: parentheses are no longer reused as a trailing-closure parameter list, so no parser lookahead, no speculative parsing, and no semantic/type-based disambiguation rule is required for trailing-closure parameters.
- This revision supersedes only the parameterized-trailing-closure portion of revision 54; the parameterless trailing-closure portion of revision 54 is unchanged.

### Unresolved
- Newline placement between a completed call and a trailing closure remains unresolved (issue B7).
- Issue D3 remains unresolved and is unchanged by this revision.

## [0.1.58] - 2026-09-01

### Changed
- Resolved indexed access and assignment versus slot creation (issue B5): slot/member access and indexed access are distinct mechanisms. `object.name` performs ordinary slot lookup, while `object[key]` lowers to the `at(key)` message and is not dynamic slot access.
- `object["foo"]` is not defined to be equivalent to `object.foo`; the two expressions may return completely different values, and an object does not automatically become indexable merely because it has slots.
- Indexable objects remain ordinary objects and may have ordinary slots, methods, delegation, and openness/frozen state; indexed contents and object slots are independent, so `map.description: "users"` and `map["description"] = user` may coexist and refer to entirely different things.
- `:` is specifically the slot-creation operator and can no longer target an index: `object[index]: value`, `object["foo"]: value`, and `object.foo[index]: value` are syntax errors. There is no indexed slot creation and no `atCreate`-style protocol.
- Indexed assignment `object[index] = value` remains valid and lowers to `atPut(index, value)`; the `=` in indexed assignment does not require an already-existing indexed entry, and whether `atPut` creates, replaces, extends, or rejects a missing key/index is defined by the receiver's `atPut` protocol.
- The grammar now distinguishes the legal final targets of `:` and `=`: `slot-creation-target` is a bare identifier or a member target, while `assignment-target` may additionally end in an index suffix. Chained postfix forms whose final operation is a member (for example `object[index].member: value`) remain valid slot-creation targets, and indexed assignment retains its existing evaluation order and written-value result.

### Unresolved
- Issues B6 and D3 remain unresolved and are unchanged by this revision.
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.57] - 2026-09-01

### Changed
- Resolved expression-separation multiplicity and blank-line grammar (issue B4): Core v0.1 has two distinct expression-separation mechanisms — `;` is the inline expression separator between expressions on the same logical source line, and a logical `NEWLINE` is the ordinary separator between expressions on different logical source lines. They are distinct syntactic roles, not interchangeable spellings of one generic separator.
- `;` is a separator, not a terminator: it requires an expression before it and an expression after it on the same logical source line. Leading, trailing, and consecutive semicolons are syntax errors, and a `;` cannot separate an expression from an expression on a following source line; `;\n` is not a redundant separator pair.
- Repeated separating logical `NEWLINE` tokens have the same effect as one separating `NEWLINE`: blank lines are permitted between, before, and after expressions and create no empty expressions, no semantic AST nodes, and no runtime behavior.
- The grammar's `layout` production now permits one or more consecutive logical `NEWLINE` tokens (`newline, { newline }`): blank lines inside open delimited constructs such as argument lists and parameter lists are layout formatting with the same effect as a single layout newline, and never substitute for a required comma.
- The generic `separator = ";" | newline` expression-sequence model is replaced by productions that distinguish same-line `;`-separated expressions from cross-line newline separation; no production permits an optional trailing `;`, and no production creates empty expressions.
- Neither `;` nor a separating, continuation, or layout `NEWLINE` becomes a semantic AST node; expression separation still produces the existing `Sequence(expressions)` representation with strictly left-to-right evaluation.
- The revision 55 newline-continuation rules (B2) and the revision 56 comma-separated list rules (B3) are unchanged: trailing commas remain syntax errors, and newlines remain non-separators inside comma-separated lists.

### Unresolved
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.56] - 2026-09-01

### Changed
- Resolved comma-separated list separators (issue B3): `,` is the only separator between elements of Core v0.1 comma-separated lists, including call arguments and closure parameters.
- A comma is strictly a separator between two list elements; it is not a terminator and does not represent an empty or omitted element. A comma must have a list element on both sides within the same list, so a trailing comma before the closing delimiter is a syntax error.
- A logical `NEWLINE` is no longer an argument or parameter separator. Newlines inside the delimiters of a list are continuation/layout under the revision 55 (B2) rules: formatting within a necessarily-incomplete construct, never a substitute for a required comma.
- Multiline calls and parameter lists remain valid through B2 continuation/layout: elements are separated by commas on their lines, and the closing delimiter may appear on a following line without a trailing comma.
- Removed the `argument-separator = "," | newline` grammar production; argument and parameter items are separated by `,` only, with an optional `layout` continuation-newline helper production for formatting inside the delimiters.
- Indexing is unchanged: Core v0.1 indexing contains one expression, not a comma-separated list, and this revision introduces no multi-index syntax.
- Trailing-closure semantics from revision 54 are unchanged: call arguments and trailing-closure parameters are distinct lists that each follow the comma-only separator rule.
- Commas and continuation newlines are resolved entirely during parsing and introduce no runtime behavior.

### Unresolved
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.55] - 2026-09-01

### Changed
- Resolved newline continuation (issue B2): a logical `NEWLINE` token normally separates expressions when the expression before it may legally end at that point, and does not separate expressions while the syntactic construct before it is necessarily incomplete and requires further input.
- Added the explicit leading-dot postfix continuation: a logical newline immediately before a leading structural `.` continues the preceding postfix/member expression. This is the sole accepted complete-before-newline continuation exception in B2.
- No general leading-operator continuation: a binary or custom symbolic operator at the beginning of the following line does not continue a preceding complete expression.
- Newline continuation is a syntactic/parser rule based on grammatical incompleteness, not on a hard-coded list of token spellings; it is independent of indentation, visual alignment, tab width, and source line-ending spelling.
- No Automatic Semicolon Insertion: the parser decides whether an existing logical `NEWLINE` token separates expressions or is consumed as continuation. An explicit `;` remains an expression separator.
- Newline continuation is resolved entirely during parsing and has no runtime semantic effect; only expressions actually separated by parser-level expression separators become distinct sequence elements.

### Unresolved
- Separators before closing `)` / `]`, trailing commas, and related list-end questions remain unresolved (issue B3).
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.54] - 2026-09-01

### Changed
- Unambiguous trailing-closure call syntax (resolves issue B1): the parentheses of a call always contain call arguments; they are never contextually reinterpreted as the parameter list of a following trailing closure.
- A parameterized trailing closure has its own parameter list placed after the completed call: `foo(args...) (params...) { body }` appends `(params...) => { body }` as the final call argument.
- `foo(args...) { body }` appends a parameterless `() => { body }` closure as the final call argument.
- Fixed the contradictory `items.each(item) { print(item) }` example/desugaring: the form now means one explicit `item` call argument plus a parameterless trailing closure, i.e. `items.each(item, () => { print(item) })`. It is not `items.each((item) => { print(item) })`.
- The parameterized form is written `items.each() (item) { print(item) }`, which desugars to `items.each((item) => { print(item) })`.
- Trailing closures remain ordinary Closure arguments after desugaring: no new runtime value kind and no special runtime trailing-block construct are introduced.
- The object-construction distinction is unchanged: `foo { ... }` creates an object whose parent expression is `foo`; `foo() { ... }` invokes `foo` with a parameterless trailing closure.

### Unresolved
- Newline placement between a completed call and a trailing closure, and before a trailing closure's own parameter list, remains unresolved (issue B7).

## [0.1.53] - 2026-09-01

### Added
- A `/* ... */` block comment is one lexical construct that consumes all source characters from its opening `/*` through its matching closing `*/`; Core v0.1 block comments do not nest and the first `*/` terminates the comment (resolves audit item A6).
- Logical source newlines inside a block comment are consumed as part of the comment: embedded `LF`, `CR`, and `CRLF` emit no `NEWLINE` token. An embedded `CRLF` remains one logical source newline for source-position and logical-line accounting.
- Single-line and multiline block comments have the same token-separation effect: logical newlines inside `/* ... */` cannot themselves separate expressions.
- Newlines outside a block comment remain governed by the revision 51 logical-newline rules.
- `//` line comments are unchanged: they terminate immediately before their terminating logical source newline, which remains available for ordinary `NEWLINE` tokenization.
- Comments remain lexical constructs with whitespace-like token-separation behavior; they do not add code points to the horizontal-whitespace set (revision 52: exactly `SPACE` and `TAB`).

### Unresolved
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).

## [0.1.52] - 2026-09-01

### Added
- Core v0.1 horizontal whitespace is exactly `SPACE` (U+0020) and `CHARACTER TABULATION` (U+0009, TAB); no other code point is horizontal whitespace, and the set does not depend on Unicode whitespace properties or host whitespace classification.
- Outside lexical constructs that consume their own contents, SPACE and TAB are insignificant horizontal whitespace: they separate tokens where separation is required and otherwise emit no parser token.
- Logical source newlines remain a separate lexical category under the revision 51 `LF` / `CR` / `CRLF` rules; they are not horizontal whitespace.
- Other Unicode whitespace-like code points are not implicitly accepted as whitespace: in particular U+000B VERTICAL TAB, U+000C FORM FEED, U+0085 NEXT LINE, U+00A0 NO-BREAK SPACE, U+1680 OGHAM SPACE MARK, U+2000..U+200A Unicode space characters, U+2028 LINE SEPARATOR, U+2029 PARAGRAPH SEPARATOR, U+202F NARROW NO-BREAK SPACE, U+205F MEDIUM MATHEMATICAL SPACE, U+3000 IDEOGRAPHIC SPACE, and U+FEFF ZERO WIDTH NO-BREAK SPACE are not Core v0.1 whitespace (illustrative list, not an open-ended definition).
- A source code point that is neither part of a valid lexical token, nor SPACE or TAB horizontal whitespace, nor a logical source newline, nor consumed inside a lexical construct such as a String or comment is a lexical error; the lexer must not silently discard unknown Unicode whitespace-like or format characters.
- Multiline triple-double-quoted String indentation whitespace consists only of SPACE (U+0020) and TAB (U+0009). Excluded whitespace-like characters remain valid String content where the ordinary String literal rules permit them.
- U+FEFF is not defined as lexical whitespace; source-byte decoding and source-encoding signature behavior remain outside this revision.
- Comments continue to consume their contents according to the existing comment lexical rules.

### Unresolved
- Tab width, visual columns, whether a TAB is equivalent to some number of SPACE characters, and how common indentation is computed when SPACE and TAB are mixed remain open (part of the separate multiline-String indentation question).
- Newline behavior inside `/* ... */` block comments remains unresolved (audit item A6).
- Separator multiplicity and blank-line grammar remain unresolved (issue B4).

## [0.1.51] - 2026-09-01

### Added
- Logical source-newline definition: a logical source newline is exactly one of `LF` (U+000A), `CR` (U+000D), or `CRLF` (U+000D U+000A); `CRLF` is consumed atomically as one logical newline, never two.
- Each logical source newline that is not consumed by another lexical construct produces exactly one `NEWLINE` token for the parser; the parser-level `newline` used in grammar productions denotes this logical `NEWLINE` token.
- Mixed line-ending styles (`LF`, `CR`, and `CRLF`) within one source file are permitted and are not lexical errors.
- `//` line comments terminate immediately before the next logical source newline or at end of file; the terminating logical source newline is not consumed by the comment and remains available for ordinary newline tokenization.
- Single-quoted and double-quoted String literals reject any logical source newline (`LF`, `CR`, or `CRLF`) before the matching closing quote as a lexical error.
- Triple-double-quoted String literals count each logical source newline as one logical newline for structural processing (delimiter placement, content-line splitting, indentation normalization), while retained source newlines preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A; there is no implicit newline normalization of String content.
- Opening/trailing newline removal in triple-double-quoted String literals removes the complete logical newline sequence, including both code points of a removable `CRLF`.
- Newline handling is independent of the host operating system, editor settings, Git line-ending conversion, and host line-separator conventions.

### Unresolved
- Newline behavior inside `/* ... */` block comments remains a separate open question (audit item A6); this revision decides nothing about it.
- Separator multiplicity and blank-line grammar (consecutive or mixed separators) remain a separate open question (issue B4); this revision does not change separator multiplicity.

## [0.1.50] - 2026-09-01

### Added
- Standalone `!` and `^` are standard/reserved symbolic tokens; their existing prefix and non-local-return meanings are unchanged.
- The exact one-character spellings `!` and `^` are not custom binary operators: `a ! b` and `a ^ b` are syntax errors.
- `!` and `^` remain characters in the custom operator alphabet and may participate in longer custom spellings such as `!!`, `^^`, `!^`, and `^!`.
- Symbolic token classification follows maximal munch (longest spelling first) and is independent of parser position; there is no prefix-position exception, so `!!x` tokenizes as `CUSTOM_OPERATOR("!!")` `IDENTIFIER("x")` and is a syntax error.

### Changed
- Reconciled the stale statement that the custom symbolic operator alphabet "remains to be finalized separately" with the already-fixed Core v0.1 alphabet; the alphabet is fixed by the language grammar.

## [0.1.49] - 2026-09-01

### Added
- Radix-Integer-dot boundary: a `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token when not immediately followed by a decimal digit; `0b10.foo` tokenizes as `INTEGER("0b10")` `.` `IDENTIFIER("foo")`. When the `.` is immediately followed by a decimal digit, the source sequence is an attempted unsupported radix Float literal and is a lexical error: `0b10.5`, `0o17.25`, and `0x1.8` are lexical errors rather than being split into `INTEGER` `.` `INTEGER` tokens. The decimal-point vs. member-access lexing rules for decimal literals are unchanged.

## [0.1.48] - 2026-09-01

### Added
- Numeric token termination and malformed numeric boundaries: once a source sequence has begun as a numeric literal, a malformed continuation or an invalid numeric/identifier boundary is a lexical error; the lexer must not split the malformed sequence into otherwise valid tokens in order to recover it.
- Radix prefixes (`0x`, `0X`, `0b`, `0B`, `0o`, `0O`) must be followed by a valid digit for that radix: `0x`, `0xG`, `0b2`, and `0o8` are lexical errors, without fallback to `INTEGER("0")` plus another token.
- An exponent begun by `e` or `E` must be complete: `2e`, `2e+`, and `2e-` are lexical errors.
- Invalid underscore placement inside or immediately adjacent to a numeric literal is a lexical error: `1__2`, `1_`, and `0x_FF`.
- An identifier cannot begin immediately after a numeric literal without a lexical boundary: `123abc` is a lexical error, not `INTEGER("123")` followed by `IDENTIFIER("abc")`.
- Valid token boundaries (punctuation, whitespace, structural delimiters, operators) remain unaffected.

## [0.1.47] - 2026-09-01

### Changed
- Clarified decimal-point vs. member-access dot lexing: a `.` belongs to a decimal numeric literal only when it is immediately followed by a decimal digit. `1.0` is a `Float` literal; `1.` tokenizes as `INTEGER("1")` followed by `.` and `.5` as `.` followed by `INTEGER("5")`; `1.to(10)` tokenizes as `INTEGER("1")` `.` `IDENTIFIER("to")` `(` `INTEGER("10")` `)`. `1.` and `.5` are not numeric literals as complete source sequences; this does not make either sequence necessarily a lexical error — whether the resulting token sequence is syntactically valid is the parser's responsibility.

## [0.1.46] - 2026-08-31

### Added
- Core v0.1 ellipsis token (`...`) definition: single lexical token, greedy recognition, context-dependent meaning.
- Maximal-munch tokenization rule for symbolic operators.
- String escape validation as part of lexical analysis: invalid, incomplete, or unsupported escapes are lexical errors.
- Validation of `\u{HEX}` format and Unicode scalar value constraints.
- Clarification that no new operator semantics are introduced in this revision.

## [0.1.45] - 2026-08-31

### Added
- Core v0.1 newline handling rules for String literals.
- Single-quoted and double-quoted String literals are single-line; raw newlines are lexical errors in these forms.
- Newlines may be represented using `\n` and `\r` escape sequences in single-line literals.
- Triple-double-quoted String literals support raw source newlines as part of multiline content.
- Lexical rules for enforcing single-line constraints on non-multiline String forms.

## [0.1.44] - 2026-08-31

### Added
- Complete Core v0.1 reserved-word set definition: `this`, `context`, `args`, `super`, `true`, `false`, `null`.
- Reserved-word recognition rules: case-sensitive matching after lexical identifier recognition.
- Clarification that prelude names (`Object`, `Future`, `Number`, `String`, `Map`, `IdentityMap`, etc.) are not reserved words.
- Clarification that Core v0.1 does not reserve control-flow or declaration keywords such as `if`, `else`, `while`, `for`, `class`, `function`, `try`, `catch`, `throw`, `async`, or `await`.

## [0.1.43] - 2026-08-31

### Added
- Core v0.1 identifier syntax definition: Unicode-aware, case-sensitive, begin with `_` or `XID_Start`, continue with `XID_Continue`.
- Core v0.1 identifier normalization requirement: all identifiers must be in Unicode NFC form, implementations must reject non-NFC identifiers.
- Clarification that identifier normalization applies to spelling only, not to `String` values.
- Reserved word recognition rules after lexical identifier recognition.

## [0.1.42] - 2026-08-31

### Added
- Core v0.1 numeric literal syntax definition: decimal and radix literals (`0x`, `0b`, `0o`), digit separators, decimal point and exponent handling.
- Core v0.1 prefix operator semantics: prefix `-` lowers to `negated()`, prefix `!` lowers to `not()`, prefix `+` is unsupported, operators apply to arbitrary expressions.

### Changed
- Clarified numeric literal constraints: no leading sign as part of literal, no type suffixes, no `NaN`/`Infinity` literal syntax, unsupported hex/binary/octal Float forms.
- Clarified prefix operator lowering as protocol-based message dispatch rather than privileged intrinsics.

## [0.1.41] - 2026-08-31

### Added
- Core v0.1 string literal syntax: single-quoted, double-quoted, and triple-double-quoted forms with unified escape rules.
- Core v0.1 escape sequences: `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`.
- Core v0.1 multiline string indentation normalization rule.
- Core v0.1 comment syntax: `//` line comments, `/* ... */` block comments, no nesting, no `#` syntax, no documentation-comment feature.

### Changed
- Specified constraint: no octal escapes, no `\xNN` escapes, no string interpolation.
- Clarified that Triple-single-quoted strings are not supported.
