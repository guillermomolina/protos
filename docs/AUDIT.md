've read all five documents in full: AGENTS.md, the language spec (rev 46), the grammar (rev 46), the runtime semantics (rev 46), and the changelog. Below is a fresh, independent audit focused on lexer/parser implementability. I have not modified any files.

Protos Core v0.1 Lexer/Parser Readiness Audit
Verdict
The documentation is NOT yet sufficient to implement the lexer without undocumented design decisions, and it is substantially further from sufficient for the parser.

Lexer: There are 4–5 genuine blockers (number-literal boundary rules, token classification of !/^, CRLF/newline token definition, whitespace character set, block-comment newlines). These are localized and each has a small number of plausible resolutions, but all are currently unspecified.
Parser: There are several hard blockers, the most severe being a direct contradiction about the meaning of receiver.m(args) { ... } (trailing-closure desugaring) and an underspecified newline-continuation model that determines whether a large class of common programs are even valid.
No findings below are resolved by the changelog; the changelog only records that revisions 42–46 addressed numbers, strings, identifiers, reserved words, newlines in strings, the ellipsis token, maximal munch, and escape validation — it does not define the boundary cases those rules still leave open.

Category A — Lexer blockers
A1. Decimal point vs. member-access dot: 1.to(10) contradicts "1. is invalid"
Documents/sections: Spec §17 (Iteration, example 1.to(10).each(i)), Spec "Numeric Model" ("A decimal point requires at least one digit on both sides: 1.0 is valid; 1. and .5 are invalid numeric literals"), Grammar "Numeric Literals".

Conflicting rules: The spec both (a) uses 1.to(10) as a valid program in §17, which requires 1 to terminate before the . so that .to is a member access, and (b) declares 1. an "invalid numeric literal", which implies the lexer consumes the . into the number. These cannot both hold without an additional lookahead rule.

Example: 1.to(10) (spec §17) vs. 1. and .5 (declared invalid literals).

Design question: When the lexer sees . immediately after digits, what rule decides whether the . starts a decimal fraction or ends the number and begins a member-access dot? In particular: is 1. at end-of-file a lexical error (invalid literal) or the tokens 1 + . (a parse error, missing member name)? What does obj.5 lex as? The rule must be stated (e.g., ". is part of a numeric literal iff a digit follows it"), and the status of 1. must then be restated consistently.

A2. Number-token termination and invalid-digit fallback are undefined
Documents/sections: Spec "Numeric Model", Grammar "Numeric Literals" (radix prefixes, _ separators, exponent rules, "invalid numeric literals").

Incomplete rules: The rules define the shape of valid literals but never define what happens on the boundary of a malformed literal, and never state what terminates a number token or whether a number may be immediately adjacent to an identifier.

Examples:

0b2, 0xG, 0o8, 0x, 0Ooops — invalid digit / missing digit after a radix prefix. Lexical error? Or fall back to 0 + identifier b2/G/… ?
2e (no exponent digit) vs. 2e3 (valid) vs. 2x — does 2e error while 2x lexes as 2 + x? The asymmetry between e (always exponent-start) and any other letter (identifier-start) is not stated.
1e -3, 1e +3 — is the sign consumed into the exponent (making this a lexical error) or is it 1e followed by -3?
123abc — number + adjacent identifier: valid token pair (then a parse error) or a lexical error?
1_, 1__0, 0x_FF, 0xFF_, 1e_3 — where may _ appear in radix-prefixed and exponent digits, and is a violating _ a lexical error or does the number just terminate earlier?
Design question: Exactly which character transitions end a number token, and which produce a lexical error rather than a shorter valid number followed by other tokens? (Rust, Python, and JavaScript each answer this differently.)

A3. ! and ^ are absent from the reserved/standard symbolic token list, yet the grammar gives them prefix roles
Documents/sections: Grammar "Custom Operator Lexing" (reserved/standard list => = == === != !== <= >= && || + - * / % < >), Grammar §21 (unary-operator = "!" | "-", non-local-return ^), Spec "Prefix Operators", Spec §13 (Return).

Conflicting/incomplete rules: The unary grammar requires a ! token (unary-operator = "!") and the non-local-return grammar requires a ^ token, but the Custom Operator Lexing section's standard-token list omits both ! and ^. - is listed (as standard additive), ! and ^ are not. Under a literal reading, a bare ! or ^ is "a remaining non-empty sequence made exclusively from operator characters" → CUSTOM_OPERATOR, making unary-operator = "!" and non-local-return = "^" unreachable. Both ! and ^ are also in the custom-operator alphabet, so each has an undocumented dual role: ! is unary-prefix and (infix) custom operator; ^ is non-local-return and (infix) custom operator.

Examples: !x, !a == b; ^value, x: ^value; a ! b, a ^ b (are these custom-operator expressions, and is that intended?).

Design question: Are bare ! and ^ emitted as dedicated tokens so the prefix productions are reachable? What is a bare ! or ^ in infix position — a custom binary operator (per the alphabet) or a syntax error? The token-classification rule must be stated.

A4. The newline token is never defined; CRLF is unparseable under the strict grammar
Documents/sections: Grammar §5 (separator = ";" | newline), §17 (argument-separator = "," | newline), Runtime "Tokenization Rules" ("A raw source newline character (U+000A line feed or U+000D carriage return)"), Spec §19.

Incomplete rules: newline appears in the EBNF but has no token definition. The runtime doc identifies both U+000A and U+000D as "newline characters", which, taken literally, makes a CRLF file two consecutive newline tokens. Combined with finding B4 (the strict expression-sequence allows exactly one separator between expressions), a: 1\r\nb: 2 would be a parse error.

Example: x: 1\r\n y: 2 in a CRLF file.

Design question: Is \r\n normalized to a single newline token? Is a lone \r a newline? Must the lexer emit one newline token per logical line break (handling CRLF/CR/LF)?

A5. The whitespace character set is not defined
Documents/sections: Spec §4 / Grammar §4 ("Spaces and tabs are token separators").

Incomplete rules: Only space (U+0020) and tab (U+0009) are named as separators. Form feed (U+000C), vertical tab (U+000B), NBSP (U+00A0), other Unicode Zs spaces, and a leading BOM (U+FEFF) are not classified: none are identifiers, so unless they are whitespace they are lexical errors.

Example: foo()\u{00A0}bar() — one call or a lexical error?

Design question: Which exact code points are token-separating whitespace, and is a BOM at file start accepted/discarded?

A6. Does a block comment containing a newline act as an expression separator?
Documents/sections: Spec §19 ("Comments are lexically equivalent to whitespace"), Grammar §38.

Incomplete rules: Block comments may contain newlines, and comments are "equivalent to whitespace". A newline is a significant token, but a comment is stripped by the lexer. It is unspecified whether a newline inside a stripped block comment still counts as a newline token for expression separation.

Example: foo() /* comment\n spanning lines */ bar().

Design question: Does the newline inside the block comment separate foo() and bar() (i.e., is this two expressions), or is the whole comment a single whitespace unit that does not separate them (making this a parse error)?

Category B — Parser blockers
B1. Trailing-closure semantics: the grammar contradicts its own example and the spec
Documents/sections: Grammar §19 (EBNF call-suffix = argument-list, [ trailing-closure ]; trailing-closure = [ parameter-list ], closure-body vs. its own "Mandatory desugaring" example), Spec §18 ("users.each(user) { print(user) } is conceptually equivalent to users.each((user) => { print(user) })"), Spec §17, Spec "Polymorphic Invocation Syntax" ("the braces denote a trailing closure passed to the invocation").

Conflicting rules: This is the most consequential finding. The spec and the grammar's own example desugar each(user) { body } to each((user) => { body }) — i.e., (user) is the trailing closure's parameter list and each receives one argument. The grammar production parses (user) as the call's argument-list and { body } as a parameter-less trailing closure, which desugars to each(user, () => body) — two arguments. The two readings cannot both hold for the same token stream. The "Polymorphic Invocation Syntax" note ("Point(args) { ... } … the braces denote a trailing closure passed to the invocation") leans toward the two-argument reading, keeping the documents internally inconsistent.

Example: users.each(user) { print(user) } — does each receive [closure] (block parameter user) or [user, closure]?

Design question: In receiver.m(x) { body }, does (x) bind as a call argument (desugar to m(x, () => body)) or as the trailing closure's parameter list (desugar to m((x) => body))? This determines the meaning of the language's single most common idiom and cannot be inferred from the docs, which assert both. (Note this also makes f() (x) { } vs. f()(x) { } ambiguous: spaces cannot change a parse, yet one reading makes f()(x) { } a postfix call chain and the other a closure-with-parameter.)

B2. The newline-continuation model is underspecified
Documents/sections: Spec §4 ("A line break may separate expressions when the grammar determines that the preceding expression is complete" + example object\n.foo()\n.bar()), Spec §19, Grammar §4.

Incomplete rules: The only documented case of an expression continuing across a newline is a leading . (postfix continuation). It is undefined whether a leading binary operator (+, *, @, …), a leading ( (call continuation), a leading [ (index continuation), a leading { (trailing closure / object body), or => on the next line continue the current expression or begin a new one.

Examples: a\n+ b; f\n(x); a\n[0]; (x)\n=> { }; foo\n{ }.

Design question: After a newline, which token classes are permitted to continue the enclosing production, and which force expression separation? This single decision determines whether a large class of programs are one expression or a parse error.

B3. Separators immediately before ) / ] and trailing commas in argument/parameter lists
Documents/sections: Grammar §16 (parameter-items), §17 (argument-items), §13 (parenthesized-expression), §18 (index-suffix), §39; contrast with expression-sequence and object-body-sequence, which allow a trailing [ separator ].

Incomplete rules: argument-items and parameter-items have no trailing [ separator ], so per the literal grammar a newline or comma immediately before ) is a parse error. This makes the most common multi-line call styles invalid without an additional, undocumented rule.

Examples: f(\n a,\n b\n); f(a, b,); (\n a\n); a[\n b\n].

Design question: Is a separator (comma and/or newline) permitted immediately before a closing ) or ] in argument/parameter lists? Is a trailing comma allowed? (The asymmetry with expression-sequences and object bodies is unexplained.)

B4. Consecutive separators: x: 1;\ny: 2 contradicts the spec's equivalence claim
Documents/sections: Grammar §5 / §39 (expression-sequence = [ expression, { separator, expression }, [ separator ] ]), Spec §19 ("foo(); bar(); baz() … is equivalent to: foo()\nbar()\nbaz()").

Conflicting rules: The EBNF allows exactly one separator between expressions (a second separator in a row is a parse error). The spec's own equivalence claim and ordinary style require foo();\nbar() (semicolon then newline) to be valid, but the grammar admits no two-separator sequence. Combined with A4 (CRLF = two newline tokens), strict reading makes common files unparseable.

Example: point: { x: 10; y: 20 } vs. the multi-line form the spec declares equivalent — and the mixed form x: 10;\ny: 20.

Design question: May separators combine/repeat (e.g., is ; followed by a newline one logical separator)? Is an empty line between expressions valid? The separator rule needs to be restated to match the spec's equivalence example.

B5. : (slot creation) with an indexed target is syntactically allowed but semantically undefined
Documents/sections: Grammar §39 (assignable-postfix-operation = "." identifier | "[", expression, "]"; slot-creation = assignable, ":", expression), Grammar "Indexed Access" (defines only receiver[index] = value → atPut), Runtime "Indexed Access Lowering" (also only =).

Incomplete rules: assignable may end in [ expression ], so receiver[index]: value is accepted by the grammar, but every lowering in the docs covers only = (→ atPut). There is no defined operation for "create via index" (no atCreate), and no statement that : with an index is invalid.

Example: map[key]: value; args[0]: value.

Design question: What does receiver[index]: value mean — a second atPut, a distinct create-only protocol, or a syntax error? (Note: args is immutable, so args[0]: value would be a runtime error even if the syntax is allowed.)

B6. (x) → closure parameter-list vs. parenthesized-expression requires an unstated lookahead rule
Documents/sections: Grammar §13 (parenthesized-expression), §16 (parameter-list), §39; Spec §15 (parent-expression may be a parenthesized expression).

Incomplete rules: The same token (x) is a parameter-list (when followed by =>), a parenthesized expression (when a primary), or a parent-expression (when followed by { }). The docs never state the disambiguation rule, and () is valid as a parameter-list/argument-list but invalid as a parenthesized expression.

Examples: (x) alone; (x) => { }; (x) { }; ().

Design question: How much lookahead decides whether ( begins a parameter list, and what happens when a parameter-list is not followed by => (e.g., is (x) + 1 a parenthesized x plus 1, or an error)?

B7. A newline between a parent-expression and {, or between a call and {, is unspecified
Documents/sections: Spec §20, Grammar §14 (object-expression = object-body | parent-expression, object-body), §19 (trailing closure), §26 (bare { } is an object expression).

Incomplete rules: On one line, animal { } is object-with-parent and foo() { } is a trailing closure (both documented). Across a newline, animal\n{ } and foo()\n{ } could be two expressions (animal/call, then a bare object literal) or a continued object/trailing-closure — changing the parse from "construct" to "evaluate an object literal as a separate discarded expression".

Examples: dog: animal\n{ name: "Rex" }; items.each(item)\n{ print(item) }.

Design question: Is a newline permitted between a parent-expression and its object body, and between a call-suffix and its trailing closure? If newlines always separate, there is no way to write call-then-object-literal with a bare { } except via ;.

Category C — Internal inconsistencies in the grammar document
C1. assignable is defined two ways
Grammar §10 (assignable = identifier | member-expression) vs. §39 (assignable = identifier | assignable-postfix-expression, which permits a trailing [ expression ]). The Indexed Access section requires the §39 form; §10 contradicts it.

C2. object-body is defined two ways
Grammar §14 (object-body = "{", expression-sequence, "}") vs. §24/§39 (object-body = "{", object-body-sequence, "}", which adds composition items). Composition is required by §24/§39 and the spec; §14 contradicts it.

C3. Referenced but undefined EBNF productions
number-literal, single-quoted-string, double-quoted-string, triple-double-quoted-string (§3), newline (§5), identifier, any-character-except-newline / any-character-except-block-end (§38) are all used in the EBNF but never given productions; the numeric and string forms exist only as prose.

C4. Escape-error classification differs
Spec "String Literal Semantics" and Grammar §3 call invalid escapes "syntax errors"; Runtime "String Escape Validation" makes them "lexical errors" to be rejected by the lexer before the parser sees the token. Both are errors, but the documented phase differs.

C5. Precedence table vs. grammar role of ^
Spec §21 / Grammar §21 list ^ as an operator level between logical and :/=, but the grammar defines non-local-return as a top-level expression alternative, not an operand production — so ^ cannot appear as a binary operand (a + ^b is a parse error), and as an infix token ^ is also a custom operator (see A3). The dual role is never reconciled.

C6. true/false/null appear in both literal and intrinsic-reference
Grammar §3 and §39 list them twice. Redundant rather than contradictory, but the parser must pick one and the duplication is unexplained.

Category D — Semantic issues surfaced by the lexical/syntactic definition (secondary)
D1. Reserved words cannot name slots, but reflection uses strings
Spec §1.1 and Grammar §2 reserve this, context, args, super, true, false, null and forbid their use "where the grammar expects an identifier" — this includes after . (member-suffix). Yet slots are named by identifiers, and reflective operations (removeSlot("name"), slotValue("name"), hasSlot("name")) take strings. So a slot named "true" or "args" can be inspected/removed by name but can never be created or accessed syntactically (obj.args is a parse error). Whether this is intended is undocumented.

D2. true { } / null { } are valid object constructions
Grammar §15 parent-expression includes intrinsic-reference (which includes null/true/false), so true { x: 1 }, null { }, this { } are all syntactically valid "object with parent <intrinsic>" forms. Whether delegating to a literal object is intended (and whether it should be a semantic error) is not addressed.

D3. Triple-double-quoted multiline string normalization has unspecified edges
Spec "String Literal Semantics" / Grammar §3 define the indentation rule but not: line-ending normalization (\r\n inside the literal — preserved as two chars or normalized to \n?); whether whitespace-only lines count as "empty"; which characters count as "indentation whitespace" (tabs vs. spaces; is common indentation counted by code point or visual column); and the treatment of tabs in the minimum-common-indentation calculation.

D4. Infix ! and ^ as custom operators
Spec/Grammar §21.1 give no examples of infix !/^; whether a ! b / a ^ b as custom selectors is intended (rather than an error) is unresolved by any example or rule beyond the alphabet (ties to A3).

Bottom line for the implementer
Lexer: implementable once A1–A6 are answered; the highest-impact single decision is the .-after-digits lookahead rule (A1), which the spec's own 1.to(10) example contradicts.
Parser: not implementable to spec until B1 (trailing-closure desugaring) and B2 (newline-continuation model) are resolved — these two decide the validity and meaning of the language's most common idioms — plus B3/B4 (delimiter and consecutive-separator handling), B5 (: with index), and B6/B7 (lookahead rules). The doc-internal contradictions in C1/C2 must also be reconciled.
Per AGENTS.md, I have not chosen any of these resolutions or changed any document; each item above is reported as a design decision awaiting your ruling.