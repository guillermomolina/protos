# Core Runtime Semantics v0.1

Language version: 0.1  
Document revision: 277
Status: Draft  
Last updated: 2026-09-04
This document defines executable-style pseudocode for the core runtime operations of the language.

It complements:

- `PROTOS_LANGUAGE_SPEC.md`
- `PROTOS_GRAMMAR.md`
- `PROTOS_IO_MODEL.md`

- the normative CLOSED sections of `PROTOS_CONCURRENCY_MODEL.md`

Unresolved sections and explicitly open subtopics in the mixed concurrency
document are non-normative.
The goal is not to mandate an implementation strategy, but to define observable behavior precisely enough that different interpreters or VMs can behave consistently.

---

# 1. Runtime Concepts

The pseudocode assumes the following conceptual runtime entities.

```text
Object
    localSlots
    parent
    state          // open, closed, frozen

Activation
    context
    lexicalParent   // lexical parent context of `context`; see lexicalParentOf
    isConstruction  // true only for object-body construction activations
    receiver        // this
    arguments       // caller-supplied positional arguments
    methodHome
    returnHome      // target of ^, optional outside callable execution
    ownsReturnHome  // true only for the invocation that established returnHome
    parentTask      // optional structured-concurrency owner

Closure
    code
    parameters
    lexicalContext
    capturedThis
    capturedMethodHome
    capturedReturnHome // optional home captured from enclosing callable activation
    boundReceiver      // optional
    boundMethodHome    // optional

LookupResult
    value
    home

SlotReference
    owner
    name

Task
    owner                  // structured-concurrency owner, or none when detached
    detached               // structured-lifetime participation
    future                 // Future representing this task's eventual outcome

Future
    state                  // pending, resolved, failed, cancelled
    value
    error
    cancellationRequested  // cooperative request flag while pending
    task                   // producing Task when task-backed; otherwise none
    adoptedSource          // pending Future whose outcome is being adopted, or none
```

These fields are conceptual. An implementation may represent them differently. The I/O-operation commitment state described in `PROTOS_IO_MODEL.md` is not an additional Future state: a Future remains exactly pending, resolved, failed, or cancelled.

**The Lexical Parent of an Execution Context:**

`lexicalParentOf(context)` is the conceptual operation returning the immediate lexical parent context of an execution-context object, or `null` when the context has no lexical parent. The association is established when the context begins to be used, and it is a lexical relationship, not a delegation relationship:

- An activation context created by `createActivation` has the closure's captured lexical context as its lexical parent; for every activation, `activation.lexicalParent` holds `lexicalParentOf(activation.context)`.
- A construction context — the object under construction in `createObject` — has the genuine lexical context chain of the enclosing activation as its lexical parent, so an object under construction is never a lexical parent of anything (see Object Construction and `lexicalContextForClosureCreation`).
- A `moduleContext` created by `createModuleContext` has the frozen prelude context as its lexical parent, associated when `executeModuleBody` executes the module body in it; it has no lexical parent before that association.
- The frozen prelude context is the root of the lexical chain: `lexicalParentOf(preludeContext)` is `null`.

Execution contexts delegate through `Context` to `Object`. `Context` is their delegation prototype, never their lexical parent: `lexicalParentOf` and `delegationParent` are different relationships, and the delegation chain `activationContext → Context → Object` is not the lexical chain. `lookupName` and `assignName` traverse lexical contexts only through `lexicalParentOf` and never walk the delegation chain of a context while searching for a bare name.

Identifiers are lexical constructs that must conform to Unicode `XID_Start` and `XID_Continue` properties and must be in Unicode NFC normalization form. The lexer must validate NFC compliance and reject non-NFC identifiers as syntax errors.

Reserved-word matching is case-sensitive. After lexical identifier recognition, the lexer must check whether the identifier spelling exactly matches one of the seven reserved words: `this`, `context`, `args`, `super`, `true`, `false`, or `null`. If it matches, the lexer tokenizes it as a reserved word token. Otherwise, it is an ordinary identifier token.

**String Literal Lexical Forms:**

The valid lexical token shapes for the three Core v0.1 String forms — single-quoted (`'...'`), double-quoted (`"..."`), and triple-double-quoted (`"""..."""`) — including the `escape-sequence` grammar, are defined formally in `PROTOS_GRAMMAR.md`; that grammar is the source spelling the lexer recognizes for String tokens.

**String Escape Validation:**

String escape validation is part of lexical analysis. An invalid, incomplete, or unsupported escape sequence in any Core v0.1 String literal is a lexical error. This includes:

- Malformed `\u{HEX}` escapes (missing braces, incomplete hex digits, extra characters)
- Unicode escape values that are not valid Unicode scalar values (e.g., surrogates, values > U+10FFFF)
- Unsupported escape sequences such as octal or `\xNN` forms

The lexer must validate escape sequences and reject invalid ones before the parser receives a String token. A String token passed to the parser must contain only valid escape sequences according to Core v0.1 rules.

**Logical Source Newlines (Lexer Contract):**

- A logical source newline is exactly one of `LF` (U+000A), `CR` (U+000D), or `CRLF` (U+000D U+000A).
- `CRLF` is consumed atomically as one logical source newline; it never produces two `NEWLINE` tokens.
- Each logical source newline produces exactly one `NEWLINE` token for the parser when it is not consumed by another lexical construct.
- Source files may freely mix `LF`, `CR`, and `CRLF` logical newlines; mixed line-ending styles are not lexical errors.
- Newline recognition does not depend on the host operating system, editor settings, Git line-ending conversion, or any host line-separator convention.
- A `//` line comment terminates immediately before the next logical source newline or at end of file. The terminating logical source newline is not consumed as part of the comment; it remains available for ordinary newline tokenization.
- Whether a `NEWLINE` token separates expressions or is consumed as continuation is decided entirely by the parser (see `PROTOS_GRAMMAR.md`); the lexer emits `NEWLINE` tokens uniformly. A continuation newline produces no runtime node and has no runtime semantic effect.
- Commas between elements of argument and parameter lists are likewise resolved entirely by the parser (see `PROTOS_GRAMMAR.md`). A comma separates list elements and produces no runtime node and no runtime semantic effect; only the parsed list elements appear in the semantic representation.

**Block Comments (Lexer Contract):**

- A `/* ... */` block comment is one lexical construct: the lexer consumes all source characters from the opening `/*` through the first following `*/`. Core v0.1 block comments do not nest; an unterminated block comment is a lexical error.
- Logical source newlines inside a block comment are consumed as part of the comment. Each embedded `LF`, `CR`, or `CRLF` is consumed; no `NEWLINE` token is emitted for an embedded logical newline, and an embedded `CRLF` never produces `NEWLINE` tokens.
- An embedded logical newline still counts as one logical source newline for source-position and logical-line accounting: line and column tracking advance normally through the comment, including the complete `CR` and `LF` of an embedded `CRLF`.
- The lexer emits no comment token and no other parser token for a block comment. A block comment has the token-separation effect of insignificant whitespace regardless of whether it contains logical newlines; embedded logical newlines do not become expression separators.
- Line comments are unchanged: a `//` line comment terminates immediately before its terminating logical source newline, which remains available for ordinary `NEWLINE` tokenization.

**Horizontal Whitespace (Lexer Contract):**

- `SPACE` (U+0020) is ignored horizontal whitespace.
- `CHARACTER TABULATION` (U+0009, TAB) is ignored horizontal whitespace.
- These two code points are the only Core v0.1 horizontal whitespace. Horizontal whitespace emits no parser token.
- The lexer must not use host-dependent or Unicode-generic whitespace predicates to expand this set; no other Unicode whitespace-like code point is implicitly ignored.
- A source code point that is neither part of a valid lexical token, nor SPACE or TAB horizontal whitespace, nor a logical source newline, nor consumed inside a lexical construct such as a String or comment is a lexical error. Otherwise-unrecognized whitespace-like or format code points must not be silently discarded.
- Logical `NEWLINE` handling is separate and unchanged.

**String Literal Newline Handling:**

The lexer must enforce the following rules for String literals:

- Single-quoted (`'...'`) and double-quoted (`"..."`) String literals are single-line literals.
- A logical source newline (`LF`, `CR`, or `CRLF`) encountered inside a single-quoted or double-quoted String literal before the matching closing quote is a lexical error.
- Newline characters must be represented in single-quoted and double-quoted literals using the escape sequences `\n` (line feed) or `\r` (carriage return); these escapes denote String content and are distinct from raw source-newline recognition.
- Triple-double-quoted (`"""..."""`) String literals permit logical source newlines as part of the literal content. Each logical source newline counts as one logical newline for structural processing — delimiter placement, content-line splitting, and indentation normalization — regardless of whether it is spelled `LF`, `CR`, or `CRLF`.
- Retained source newlines in triple-double-quoted literals preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A. There is no implicit newline normalization of String content.
- The escape-sequence rules for triple-double-quoted literals are unchanged; escape processing does not treat an escape sequence as source indentation. Opening/trailing newline removal removes the complete logical newline sequence. Multiline indentation normalization for triple-double-quoted literals follows the Core v0.1 closing-delimiter indentation rule defined in `PROTOS_GRAMMAR.md`; it does not change the valid triple-double token shapes.

**String Literal Delimiter Recognition (Lexer Contract):**

Triple-double quote-run recognition is lexer behavior, not runtime behavior:

- Outside a String lexical construct, three consecutive unescaped double-quote characters (`"""`) at the current lexical position begin a triple-double-quoted String; this takes priority over recognizing an ordinary double-quoted String opener at that position. The opening delimiter is exactly three double quotes.
- Inside a triple-double-quoted String, the first three consecutive unescaped double-quote characters form the closing delimiter, which consumes exactly those three quotes. One or two consecutive unescaped quotes that do not begin a closing delimiter are ordinary content.
- Any source characters immediately following a closing delimiter, including additional double quotes, are outside the completed String and are lexed normally from that point; there is no greedy rule that consumes a longer quote run as one delimiter, and quote-run decisions are not backtracked.
- An escaped double quote (`\"`) is String content and does not participate in a closing delimiter.

**Unterminated String Literals (Lexer Contract):**

- Reaching the end of source before the required closing delimiter of a single-quoted (`'...'`), double-quoted (`"..."`), or triple-double-quoted (`"""..."""`) String literal is a lexical error.
- An unterminated String literal never produces a partial String token. The parser never receives a successfully formed String token for the malformed literal, and the lexer must not recover by treating the opening quote as another token, emitting accumulated content as a partial String, splitting the literal into otherwise valid tokens, inserting a closing delimiter, or interpreting the end of source as the closing delimiter.
- The end-of-source rule applies only when the end of source is reached while String recognition is still active and no earlier lexical error has already terminated recognition. The existing single-line raw-newline rule is unchanged: a logical source newline before the matching closing quote is already a lexical error.
- The existing incomplete-escape rule is unchanged. If the end of source is reached after a backslash or during an incomplete escape while String recognition is still active, the source is a lexical error and no String token is emitted; no normative priority between an "incomplete escape" and an "unterminated String" classification is required.

**Tokenization Rules:**

- `...` is a single lexical token representing three consecutive periods. It is recognized greedily and is not parsed as three separate `.` tokens.
- Maximal-munch tokenization applies to symbolic operators: when multiple valid symbolic operator tokens can begin at the same source position, the lexer must consume the longest valid token.
- Symbolic token classification is lexical and independent of parser position: maximal munch first forms the longest valid symbolic spelling, which is classified as a reserved/standard token when it exactly matches a reserved/standard spelling and as `CUSTOM_OPERATOR` otherwise. The exact one-character spellings `!` and `^` are reserved/standard tokens with their existing prefix and non-local-return roles and are not custom binary operators; longer spellings containing those characters, such as `!!` or `^^`, are `CUSTOM_OPERATOR` tokens.
- Standard punctuation and structural tokens are tokenized separately from symbolic operators.
- A `.` immediately following a complete radix-prefixed Integer literal is a structural `.` token unless it is immediately followed by a decimal digit; a `.` immediately followed by a decimal digit makes the sequence an attempted unsupported radix Float literal and a lexical error (`0b10.5`), not `INTEGER("0b10")` `.` `INTEGER("5")`.

Comments are purely lexical: the lexer strips `//` line comments and `/* ... */` block comments, which have whitespace-like token-separation behavior but are not additional horizontal-whitespace code points. They do not produce runtime values, they do not participate in the language object model, and they do not have any special meaning inside String literals. `#` is not a comment delimiter, and no documentation-comment syntax is defined by Core v0.1.

`Object` is the unique root prototype. It has no delegation parent. Every other language object has exactly one immutable delegation parent, so every delegation chain terminates at `Object`. The absence of a parent on `Object` is structural; it is not represented by `null` or by any other language object. Reflective structural operations such as `removeSlot(name)`, `close()`, and `freeze()` are ordinary messages provided through `Object`, with runtime primitives implementing their structural effects.

`Context` is the standard prototype for execution-context objects, provided by the standard prelude. Execution contexts remain ordinary language objects: an activation context or a `moduleContext` is an ordinary object whose delegation chain runs through `Context` to `Object`, and behavior provided by `Context` is inherited through ordinary Protos delegation. There is no special runtime object category for execution contexts and no special lookup mechanism associated with `Context`.

---

# 2. Core Invariants

The algorithms below preserve these rules:

```text
Reads may delegate.

Writes never delegate.

: creates a local slot.

= modifies an existing writable slot.

A missing slot is an error.

null is not the result of failed lookup.

this is the original receiver of a message send.

super changes lookup origin, not receiver.

Closures capture lexical contexts by reference.

Object construction contexts are not captured as lexical scopes merely because they own method slots.

Extracted methods remain bound to their receiver.

^ returns to the owning activation.

Object has no delegation parent.

Every other object has exactly one delegation parent.

Every delegation chain terminates at Object.

Every object may serve as a delegation parent.

A delegation parent cannot change after object creation.
```

---

## 2.1 Standard semantic-family receiver validation

Built-in semantic-family classification is independent of delegation. A runtime
must therefore validate the original receiver when invoking standard behavior
whose contract requires membership in a semantic value family.

Conceptually:

```text
function requireSemanticFamilyReceiver(receiver, familyPredicate):
    if not familyPredicate(receiver):
        signal an Error for invalid standard-behavior receiver

    return receiver
```

`familyPredicate` denotes the corresponding semantic classifier, such as
`isSemanticNumberValue` or `isSemanticStringValue`; it is not message lookup and
does not test whether the receiver delegates to a family value or prototype.

A standard family-specific behavior conceptually performs this validation
before its family-specific primitive work. For example, standard numeric
hashing is equivalent to:

```text
function standardNumberHash(receiver):
    number = requireSemanticFamilyReceiver(
        receiver,
        isSemanticNumberValue
    )
    return standardNumericHash(number)
```

The same rule applies to other standard Number-family behavior and to
family-specific standard behavior for other semantic value families unless the
behavior's normative contract explicitly defines a wider receiver domain.

This validation happens after ordinary receiver/argument evaluation and after
ordinary message lookup has selected the behavior. If validation fails, the
error propagates normally; lookup is not resumed at an ancestor with another
slot of the same name.

The validation operation itself performs no user-message dispatch and grants no
new semantic-family membership. User-defined overrides remain ordinary Protos
behavior and are constrained only by their own contracts. Implementations may
inline, specialize, or otherwise eliminate explicit checks when semantic-family
membership is already proven, provided that incompatible receivers have the
same observable failure behavior.

# 3. Local Slot Lookup

```text
function lookupLocal(object, name):
    if object.localSlots contains name:
        return LookupResult(
            value = object.localSlots[name],
            home = object
        )

    return NOT_FOUND
```

This operation never checks the object's parent.

---

# 4. Delegating Object Lookup

```text
function lookupSlot(receiver, name, start = receiver):
    current = start

    loop:
        result = lookupLocal(current, name)

        if result != NOT_FOUND:
            return result

        if current is Object:
            signal SlotNotFound(
                receiver = receiver,
                name = name
            )

        current = delegationParent(current)
```

The returned `home` is the object where the slot was physically found.

Example:

```text
rex → dog → animal
```

If `speak` belongs to `animal`:

```text
lookupSlot(rex, "speak")
```

returns conceptually:

```text
value = animal.localSlots["speak"]
home  = animal
```

The receiver is still `rex`.

---

# 5. Unqualified Lookup

Unqualified lookup searches lexical contexts first, then the receiver delegation chain.

```text
function lookupName(activation, name):
    context = activation.context

    while context != null:
        result = lookupLocal(context, name)

        if result != NOT_FOUND:
            return result.value

        context = lexicalParentOf(context)

    if activation.receiver != null:
        result = lookupSlot(
            receiver = activation.receiver,
            name = name
        )

        return bindIfMethod(
            value = result.value,
            receiver = activation.receiver,
            methodHome = result.home
        )

    signal SlotNotFound(
        name = name
    )
```

The walk traverses lexical contexts only: `lookupLocal` inspects each context's own slots and `lexicalParentOf` moves to the next lexical context. The delegation chain of a context (`Context` → `Object`) is never searched for a bare name; after the lexical chain is exhausted, lookup continues through the receiver's delegation chain as shown.

A failed lookup never returns `null`.

---

# 6. Explicit Member Read

```text
function readMember(receiver, name):
    result = lookupSlot(
        receiver = receiver,
        name = name
    )

    return bindIfMethod(
        value = result.value,
        receiver = receiver,
        methodHome = result.home
    )
```

Therefore:

```js
f: dog.speak
```

produces a bound callable value if `speak` is a method closure.

---

# 7. Method Binding

There is no distinct runtime-language `Method` value category. The core executable value is `Closure`. A member read never executes a closure. If member lookup yields a closure, the runtime may represent the receiver binding as a lightweight `BoundClosure` wrapper/view, but this is binding metadata rather than a separate method object in the language semantics.

```text
function bindIfMethod(value, receiver, methodHome):
    if not isClosure(value):
        return value

    return BoundClosure(
        closure = value,
        boundReceiver = receiver,
        boundMethodHome = methodHome
    )
```

Binding a method does not copy its lexical environment.

It only records the receiver and the home from which the method was obtained.

---

# 8. Prefix Operator Lowering

Prefix `-` and prefix `!` are ordinary protocol operations, not privileged numeric/Boolean runtime intrinsics.

```text
function lowerUnary(operator, operand):
    if operator == "-":
        return send(receiver = operand, message = "negated")
    if operator == "!":
        return send(receiver = operand, message = "not")
    signal InvalidUnaryOperator(operator)
```

Prefix `+` is not supported.

These operators apply to arbitrary expressions under the normal expression grammar and are not restricted to numeric or literal operands.

---

# 9. Slot Creation

Unqualified creation:

```js
x: value
```

means:

```text
createSlot(
    target = activation.context,
    name = "x",
    value = value
)
```

Explicit creation:

```js
object.x: value
```

means:

```text
createSlot(
    target = object,
    name = "x",
    value = value
)
```

The operation is:

```text
function createSlot(target, name, value):
    if target.state == frozen:
        signal FrozenObject(target)

    if target.state == closed:
        signal ClosedObject(target)

    if target.localSlots contains name:
        signal SlotAlreadyExists(
            target = target,
            name = name
        )

    target.localSlots[name] = value

    return value
```

Creation never searches the delegation chain.

Indexed syntax never reaches this operation: `object[index]: value` is rejected by the grammar and has no runtime lowering (see Indexed Access Lowering). Slot creation and the indexing protocol remain distinct runtime paths.

---

# 10. Explicit Member Assignment

For:

```js
object.x = value
```

the runtime performs:

```text
function assignMember(target, name, value):
    if target.state == frozen:
        signal FrozenObject(target)

    if not target.localSlots contains name:
        signal SlotNotFound(
            receiver = target,
            name = name
        )

    target.localSlots[name] = value

    return value
```

No parent is ever searched.

Thus:

```text
reads delegate
writes do not delegate
```

is preserved.

---

# 11. Unqualified Assignment

For:

```js
x = value
```

the runtime searches writable lexical contexts first.

If none contains `x`, it may modify a slot belonging locally to `this`.

It never modifies a slot inherited by `this`.

```text
function assignName(activation, name, value):
    context = activation.context

    while context != null:
        if context.localSlots contains name:
            return assignMember(
                target = context,
                name = name,
                value = value
            )

        context = lexicalParentOf(context)

    receiver = activation.receiver

    if receiver != null and receiver.localSlots contains name:
        return assignMember(
            target = receiver,
            name = name,
            value = value
        )

    signal SlotNotFound(
        name = name
    )
```

As in Unqualified Lookup, the walk follows `lexicalParentOf` — the lexical chain — and never the contexts' `Context` → `Object` delegation chain.

---

# 12. Message Send

A receiver-aware message send preserves both the original receiver and the object where the slot was found.

For:

```js
dog.speak(a, b)
```

the runtime conceptually performs:

```text
send(
    receiver = dog,
    message = "speak",
    arguments = [a, b],
    lookupStart = dog
)
```

Algorithm:

```text
function send(receiver, message, arguments, lookupStart = receiver):
    result = lookupSlot(
        receiver = receiver,
        name = message,
        start = lookupStart
    )

    value = result.value

    if isClosure(value):
        return invokeClosureAsMethod(
            closure = value,
            receiver = receiver,
            methodHome = result.home,
            arguments = arguments
        )

    return invoke(
        receiver = value,
        arguments = arguments
    )
```

Closure-valued slots receive method semantics because `this` and `methodHome` must describe the receiver-aware send. Other invokable values use their own invocation protocol.

---

# 13. Closure Method Invocation

```text
function invokeClosureAsMethod(
    closure,
    receiver,
    methodHome,
    arguments
):
    activation = createActivation(
        closure = closure,
        arguments = arguments,
        receiver = receiver,
        methodHome = methodHome,
        establishReturnHome = true
    )

    return executeActivation(activation)
```

For:

```text
rex → dog → animal
```

if `rex.speak()` finds closure `speak` in `animal`:

```text
receiver   = rex
methodHome = animal
```

and inside the method:

```text
this === rex
```

---

# 14. Polymorphic Invocation

A plain call such as:

```js
f(a, b)
```

does not perform member lookup on the source-level identifier. It evaluates the call receiver and invokes that value.

```text
function invoke(receiver, arguments):
    if isBoundClosure(receiver):
        return invokeClosureAsMethod(
            closure = receiver.closure,
            receiver = receiver.boundReceiver,
            methodHome = receiver.boundMethodHome,
            arguments = arguments
        )

    if isClosure(receiver):
        activation = createActivation(
            closure = receiver,
            arguments = arguments,
            receiver = receiver.capturedThis,
            methodHome = receiver.capturedMethodHome,
            establishReturnHome =
                (receiver.capturedReturnHome == null)
        )

        return executeActivation(activation)

    callBehavior = lookupInvocationBehavior(receiver)

    if callBehavior == NOT_FOUND:
        signal NotCallable(receiver)

    return executeInvocationBehavior(
        callBehavior = callBehavior,
        invocationReceiver = receiver,
        arguments = arguments
    )
```

`lookupInvocationBehavior` is the runtime view of the language's ordinary invocation/call protocol. Standard `Closure` behavior executes code; ordinary prototypes inherit default construction behavior from `Object`; user objects may specialize the protocol.

The implementation may specialize these cases directly rather than literally allocating or sending an intermediate `call` message, provided observable semantics are unchanged.

# 15. Closure Creation

When evaluating:

```js
(x) => {
    body
}
```

the runtime creates:

```text
function createClosure(activation, parameters, code):
    return Closure(
        code = code,
        parameters = parameters,

        lexicalContext =
            lexicalContextForClosureCreation(activation),

        capturedThis =
            activation.receiver,

        capturedMethodHome =
            activation.methodHome,

        capturedReturnHome =
            activation.returnHome
    )
```

The lexical context is captured by reference.

The closure does not copy local slot values.


Object construction requires one important distinction: the object being constructed is a slot-creation context, but it is not automatically a lexical capture scope for method closures declared in its body.

Conceptually:

```text
function lexicalContextForClosureCreation(activation):
    if activation.isConstruction:
        return activation.lexicalParent

    return activation.context
```

A construction activation is created only by `createObject`, with `context` set to the object being constructed and `lexicalParent` set to `lexicalContextForClosureCreation` of the enclosing activation (see Object Construction). Its `lexicalParent` therefore is already a genuine lexical context — never another object under construction — so this rule skips every enclosing construction context transitively, not merely the nearest one.

Therefore a method closure installed on a prototype captures genuine enclosing lexical contexts, while bare object-state names are resolved later against the dynamic receiver (`this`) and its delegation chain.

For example, if `speak` is declared on `animal` and invoked as `dog.speak()`, a bare `name` inside `speak` resolves through `dog` before `animal`, unless a genuine lexical binding named `name` shadows it.

---

## Trailing Closure Lowering

A trailing closure is grammar-level sugar for an ordinary parameterless Closure appended as the final argument of a call. It introduces no new runtime value kind and no special runtime trailing-block construct.

```text
foo(args...) {
    body
}
```

lowers to:

```text
foo(
    args...,
    () => {
        body
    }
)
```

A trailing closure never has its own parameter list: Core v0.1 provides no parameterized trailing-closure syntax, so there is no lowering for `foo(args...) (params...) { body }`. A closure that requires parameters is an ordinary explicit closure expression in ordinary call-argument position and needs no trailing-closure lowering.

The lowering proceeds as follows:

1. The ordinary explicit call arguments, including spread arguments, are evaluated left-to-right according to the existing argument-evaluation rules.
2. The trailing Closure is created in the current activation using the ordinary closure creation semantics (see Closure Creation). It is an ordinary Closure with an empty parameter list, exactly as if `() => { body }` had been written in the same position.
3. The created Closure is appended as the final argument of the invocation.
4. The invocation then proceeds with ordinary call semantics (see Message Send, Polymorphic Invocation, and Activation Creation and Argument Binding).

The trailing Closure captures `this`, `context`, `methodHome`, and the return home exactly as an ordinary Closure written in the same source position would. A `^` inside a trailing closure therefore follows the ordinary non-local-return rules.

The runtime does not need to know whether a parameterless closure originated from trailing-closure syntax or from an explicit `() => { body }` expression: both produce the same ordinary Closure value. The call's argument parentheses always contain call arguments; a trailing closure has no parameter list.

Only a trailing closure that the parser attaches to the completed call reaches this lowering. Attachment requires that no logical `NEWLINE` token intervene between the completed call and the closure body (see the grammar's Trailing Closures section). A `{ ... }` separated from a preceding completed call by a separating logical `NEWLINE` is not a trailing closure, produces no trailing-closure lowering, and introduces no runtime concept here: no runtime newline mechanism exists.

---

## Parameter Signature Validation

Executable closure metadata must contain unique parameter names.

Duplicate names are a source/signature validation error and must not be resolved dynamically by overwriting or rebinding activation slots. This includes collisions involving a rest parameter.

The runtime may assume validated closure parameter metadata. Defensive implementations may still reject malformed internal metadata, but such rejection is not the normal language-level execution path.

---

# 16. Activation Creation and Argument Binding

The language uses a Smalltalk/Squeak-style **home activation** for `^`.

Every invocation records the caller-supplied positional arguments before default substitution. The reserved intrinsic `args` exposes an immutable view of that original vector.

```text
function createActivation(
    closure,
    arguments,
    receiver,
    methodHome,
    establishReturnHome
):
    context = new Object(
        parent = Context
    )

    activation = Activation(
        context = context,
        lexicalParent = closure.lexicalContext,
        receiver = receiver,
        arguments = immutableArgumentCollection(arguments),
        methodHome = methodHome
    )

    bindParametersLeftToRight(
        activation = activation,
        parameters = closure.parameters,
        arguments = arguments
    )

    if establishReturnHome:
        activation.returnHome = new ReturnTarget()
        activation.ownsReturnHome = true
    else:
        activation.returnHome = closure.capturedReturnHome
        activation.ownsReturnHome = false

    return activation
```

The new activation context's delegation parent is `Context`, while its lexical parent — `lexicalParentOf(context)` — is `closure.lexicalContext`, the context captured by the closure at creation. The two relationships are independent: the delegation chain `activationContext → Context → Object` is never used for bare-name lexical lookup.

Parameter binding is conceptually:

```text
function bindParametersLeftToRight(
    activation,
    parameters,
    arguments
):
    argumentIndex = 0

    for parameter in parameters from left to right:
        if parameter is rest:
            restValues = arguments[argumentIndex .. end]
            createSlot(
                activation.context,
                parameter.name,
                immutableArgumentCollection(restValues)
            )
            argumentIndex = length(arguments)
            continue

        if argumentIndex < length(arguments):
            value = arguments[argumentIndex]
            argumentIndex += 1
        else if parameter has defaultExpression:
            value = evaluate(
                parameter.defaultExpression,
                activation
            )
        else:
            signal ArgumentCountError()

        createSlot(
            activation.context,
            parameter.name,
            value
        )

    if argumentIndex < length(arguments):
        signal ArgumentCountError()
```

Default expressions therefore observe earlier parameter bindings and the invocation context. They do not alter `args`.

A method invocation dynamically supplies `receiver` and `methodHome` and establishes a fresh return home. A module-level function closure with no captured return home likewise establishes one. A nested closure preserves its captured home.

# 17. `super`

`super` preserves the original receiver but changes where lookup begins. It is not a runtime object or first-class value. The parser lowers only `super.message(arguments...)` to a super-send operation. Bare `super`, passing it as a value, assigning it, or extracting `super.message` without a call is invalid.

Conceptually, a super send uses execution metadata available through `context`: the receiver is the current receiver and the lookup origin is the parent of the current `methodHome`.

```text
function sendSuper(activation, message, arguments):
    if activation.methodHome == null:
        signal InvalidSuper()

    if activation.methodHome is Object:
        signal SlotNotFound(
            receiver = activation.receiver,
            name = message
        )

    lookupStart = delegationParent(activation.methodHome)

    return send(
        receiver = activation.receiver,
        message = message,
        arguments = arguments,
        lookupStart = lookupStart
    )
```

Thus:

```text
this
```

does not change.

Only the lookup origin changes.

---

# 18. Normal Return

The value of the final expression of an activation is its normal result.

Only the activation that **owns** a return home catches a matching non-local return and completes that home. Nested block activations sharing the same home must rethrow the control transfer so that it reaches the owner.

```text
function executeActivation(activation):
    if activation.ownsReturnHome:
        mark activation.returnHome as ACTIVE

    try:
        result = evaluateSequence(
            activation.code,
            activation
        )

        return result

    catch NonLocalReturn jump:
        if activation.ownsReturnHome
           and jump.target == activation.returnHome:
            return jump.value

        rethrow jump

    finally:
        if activation.ownsReturnHome:
            mark activation.returnHome as COMPLETED
```

---

# 19. Non-local Return

For:

```js
^value
```

the runtime performs:

```text
function nonLocalReturn(activation, value):
    target = activation.returnHome

    if target == null or target.state != ACTIVE:
        signal InvalidReturn()

    raise NonLocalReturn(
        target = target,
        value = value
    )
```

A direct `^` in a function or method and a `^` in any ordinary nested closure created during that invocation therefore target the same home activation.

The control transfer may be implemented using exceptions, continuations, stack unwinding, tagged jumps, or another mechanism.

Only the observable behavior is specified.

---

# 20. Escaped Closure Return

Example:

```js
make: () => {
    () => {
        ^42
    }
}

f: make()
f()
```

The inner closure captures the return home established by the invocation of `make`.

After `make()` returns, that home is `COMPLETED`. When `f()` later performs `^42`, `InvalidReturn` is signaled.

The runtime must not silently reinterpret the operation as a local return from `f`.

---

# 21. Object Construction

```text
function createObject(parent, body, activation):
    // Source-level object creation always supplies exactly one parent.
    // A bare object literal supplies Object. Only Object itself has no parent.
    require parent is a language object

    object = new Object(
        parent = parent,
        state = open
    )

    constructionActivation = Activation(
        context = object,
        lexicalParent = lexicalContextForClosureCreation(activation),
        isConstruction = true,
        receiver = object,
        methodHome = null,
        returnHome = activation.returnHome,
        ownsReturnHome = false
    )

    constructionActivation.compositionReservedNames =
        compositionReservedNames(body)

    evaluateSequence(
        body,
        constructionActivation
    )

    return object
```

This pseudocode expresses the language's uniform context model. The construction activation's `context` is the object under construction, so bare-name lookup and unqualified slot creation inside the body operate on that object's own local slots; its `lexicalParent` is the genuine lexical context chain of the enclosing activation (`lexicalContextForClosureCreation`), so the object under construction is neither captured as a lexical environment nor inserted into the lexical chain of closures created in its body.

Object construction evaluates the parent expression and uses the resulting object directly as the immutable delegation parent. No parentability test, classification, or permission check is performed: every successfully evaluated parent expression produces a Protos object, and every Protos object may serve as the delegation parent of another object, including immutable value objects, singleton values such as `true`, `false`, and `null`, and Number or String values such as `42` or `"hello"`.

The runtime is not required to allocate a distinct heap object for a value parent such as `42`. The semantic parent may be represented internally using an immediate, tagged, boxed, or heap representation, provided observable delegation behavior is identical.

Delegated lookup through such a parent preserves the original receiver under the ordinary rules of Delegating Object Lookup: if a message sent to an object whose delegation parent is a value object is found through that parent or its ancestors, `this` is the original receiver, not the parent. Construction and lookup semantics are otherwise unchanged.

An implementation may use a specialized construction context provided that observable lookup and creation semantics are identical.

---

# 22. Object Composition

For:

```js
target: parent {
    ...sourceA
    ...sourceB

    move: () => {
        ...
    }
}
```

composition is structural flattening of local slot bindings. A composition source is an ordinary object; there is no runtime `Trait` value kind.

A `...source` form is a **composition item**: a contextual object-body item recognized by the parser only inside an object body. It is not a general expression form. Recognizing a composition item is purely a parsing matter: it introduces no new runtime value kind and no new runtime operation. The item's source is an ordinary expression evaluated under ordinary expression semantics, and composition items are separated from other object-body items by the same logical-`NEWLINE`/inline-`;` rules as ordinary expressions.

Before evaluating an object body, the runtime conceptually determines the set
of slot names created by direct local slot-creation items of that body:

```text
function compositionReservedNames(body):
    names = emptySet

    for each direct item in body:
        if item is Create(
            targetExpr = null,
            name = name,
            valueExpr = ...
        ):
            names.add(name)

    return names
```

This inspection is structural. It does not evaluate any expression, create any
slot, invoke any operation, or make a reserved name visible to lookup.

An implementation need not materialize this set at runtime. It may encode the
same information in parsed or compiled representation provided that observable
behavior is identical.

The `compositionReservedNames` associated with the construction activation are
conceptual construction state. They are not observable slots of the Activation
or of the object under construction.

A composition item is evaluated as follows:

```text
function evaluateComposition(sourceExpr, activation):
    require activation.isConstruction

    source = evaluate(sourceExpr, activation)
    requireObject(source)

    target = activation.context
    reserved = activation.compositionReservedNames
    effectiveSlots = emptyCollection

    // Validation phase. No target slot is changed here.
    for each local slot in source:
        if reserved contains slot.name:
            continue

        if lookupLocal(target, slot.name) != NOT_FOUND:
            signal CompositionConflict(
                target = target,
                name = slot.name
            )

        effectiveSlots.append(slot)

    if effectiveSlots is not empty and target.state != open:
        if target.state == frozen:
            signal FrozenObject(target)

        signal ClosedObject(target)

    // Installation phase. Validation has succeeded for the whole item.
    for each slot in effectiveSlots:
        target.localSlots[slot.name] = slot.value

    return target
```

The source expression is evaluated exactly once. Effects caused by evaluating
that expression occur before composition validation and are not rolled back if
composition later fails.

The composition item's structural effect on the target is atomic: either every
effective contribution from that item is installed or none is. Consequently
the source object's internal slot-enumeration order is not observable through
partial installation.

The conceptual validation and installation phases do not require a particular
implementation strategy. Inspecting a local slot binding invokes no Protos
code, so an implementation may preflight and install by any equivalent
mechanism. It need not copy the source's slot table or allocate
`effectiveSlots` when the same observable atomic result can be guaranteed
otherwise.

A reserved name is ignored only by composition. Reservation does not create a
slot and does not participate in `lookupLocal`, `lookupSlot`, `lookupName`,
assignment, reflection, or delegation. Until the explicit declaration executes,
ordinary lookup behaves exactly as if no reservation existed.

After a successful composition item, each installed binding is an ordinary
local slot of the target and is immediately visible to subsequent object-body
items.

The slot **binding** is copied; `slot.value` itself is not cloned. If two
receivers compose a slot whose value is the same mutable object, both resulting
local slots initially refer to that same object.

If a non-reserved contribution would collide with an already local target slot,
the entire composition item signals `CompositionConflict` before installing any
of its contributions. No first-wins or last-wins rule exists.

Direct local declarations retain the ordinary `:` semantics when they execute.
The structural reservation merely prevents composition from occupying the
declaration's name beforehand; it does not perform the declaration early.

The rule is uniform for closure-valued slots, immutable values, mutable objects, and all other slot contents. Composition never changes `target.parent` and adds no alternate lookup path. Once construction succeeds, composed slots are ordinary local slots of `target`.

### Composition-source transformations

`without` and `alias` are ordinary object messages. They do not mutate their receiver and produce ordinary objects that may subsequently be used as composition sources.

Conceptually:

```text
function without(receiver, name):
    requireLocalSlot(receiver, name)

    result = newOrdinaryObject()
    for each local slot in receiver:
        if slot.name != name:
            createLocalSlot(result, slot.name, slot.value)

    return result
```

and:

```text
function alias(receiver, sourceName, aliasName):
    requireLocalSlot(receiver, sourceName)

    if receiver.hasLocalSlot(aliasName):
        signal AliasConflict(aliasName)

    result = copyLocalSlotBindingsIntoNewObject(receiver)
    createLocalSlot(result, aliasName, receiver.localSlot(sourceName).value)
    return result
```

`alias` preserves the original `sourceName`; it adds `aliasName`. Both names initially refer to the same stored object. Neither operation clones slot values.

Because the returned value is an ordinary object, the composition machinery itself remains unchanged: `...` simply evaluates its operand and composes that object's local slots. Missing source names and alias-name collisions are errors rather than silent no-ops or overwrites.

---

# 23. Removing Local Slots

`removeSlot(name)` is an ordinary message inherited from `Object`. Its primitive behavior affects only the receiver's local slot table and never delegates.

```text
function removeLocalSlot(object, name):
    if object.state == frozen:
        signal FrozenObject(object)

    if object.state == closed:
        signal ClosedObject(object)

    if not object.localSlots.contains(name):
        signal LocalSlotNotFound(object, name)

    value = object.localSlots[name]
    object.localSlots.remove(name)

    invalidateShapeAssumptions(object)

    return value
```

If a delegated slot with the same name exists, removing the local slot exposes that delegated slot to subsequent reads. No parent object is modified.

---

# 24. Closing Objects

```text
function closeObject(object):
    if object.state == frozen:
        return object

    object.state = closed

    return object
```

A closed object:

```text
may modify existing slots
may not create slots
may not delete slots
```

Closing is shallow.

---

# 25. Freezing Objects

```text
function freezeObject(object):
    object.state = frozen
    return object
```

A frozen object:

```text
may not create slots
may not delete slots
may not modify slot values
```

Freezing is shallow.

---

# 26. Identity

Identity is a semantic property and must not leak the runtime representation chosen for an object.

Core v0.1 has a closed set of value-identity categories. Only semantic Number
values, String values, the canonical Boolean values, and `null` use value
identity. Every other object uses individual object identity.

Conceptually:

```text
function coreValueIdentityKind(value):
    if isSemanticNumberValue(value):
        return NUMBER_VALUE

    if isSemanticStringValue(value):
        return STRING_VALUE

    if value === canonicalTrue or value === canonicalFalse:
        return BOOLEAN_VALUE

    if value === canonicalNull:
        return NULL_VALUE

    return NONE


function identical(a, b):
    aKind = coreValueIdentityKind(a)
    bKind = coreValueIdentityKind(b)

    if aKind != NONE or bKind != NONE:
        if aKind != bKind:
            return false

        return sameSemanticValue(a, b)

    return sameObjectIdentity(a, b)
```

The predicates above are semantic classification, not delegation tests.
Delegating to a Number, String, Boolean, or `null` value does not make an
ordinary child object a member of that value family.

`isSemanticNumberValue` includes the numeric families defined by the numeric
model. Numeric `sameSemanticValue` follows the already-defined numeric identity
rules: semantic numeric family participates in `===`, and Float NaN and signed
zero follow their dedicated identity rules.

`isSemanticStringValue` compares the String semantic value defined by the
String model; allocation, interning, rope/flat representation, encoding choice,
or storage sharing is not observable through identity.

The Boolean and `null` cases denote their canonical language values, not the
standard prototype objects named `Boolean` or any other ordinary object.

Immutability does not imply value identity. Closing or freezing an ordinary
object never changes its identity category. Interning, canonicalization,
boxing/unboxing, pointer tagging, deduplication, serialization strategy, or
other implementation machinery likewise cannot convert an identity-bearing
object into a value-identity object or vice versa.

The classification is normative and exhaustive for Core v0.1. Implementations
must not introduce implementation-specific value-identity kinds.


Consequences include:

```text
identical(1, 1)                         == true
identical("hello", "hello")             == true
identical("hel" + "lo", "hello")      == true
identical(true, true)                   == true
identical(null, null)                   == true

identical(newObject(), newObject())     == false
```

`Number` and `String` are immutable value objects. String operations never mutate an existing String in place; a changed textual value is another String value. A runtime may intern, share, inline, box, unbox, or otherwise optimize these values without changing `===`.

String-literal evaluation produces ordinary `String` values. Single-quoted and double-quoted literals are equivalent. There is no separate character type; `'a'` and `"a"` both denote a `String` containing the single-character text `a`. Single-quoted, double-quoted, and triple-double-quoted strings use the same escape rules. The supported escapes are exactly `\\`, `\'`, `\"`, `\n`, `\r`, `\t`, `\b`, `\f`, and `\u{HEX}`. `\u{HEX}` requires 1 to 6 hexadecimal digits and must denote a valid Unicode scalar value. Invalid or incomplete escape sequences are lexical errors. Octal escapes and `\xNN` escapes are not supported. Triple-double-quoted strings are multiline `String` values, not raw strings. Triple-single-quoted strings are invalid syntax. String interpolation is not part of Core v0.1, so `${...}` inside a literal is ordinary text.

Triple-double-quoted String evaluation produces a `String` value defined by the Core v0.1 multiline indentation normalization rule; normalization is applied to the literal source as part of defining that value, and no runtime String operation performs source indentation stripping. Logical source newlines delimit the content lines; each of `LF`, `CR`, and `CRLF` counts as one logical newline for structural processing. If the opening delimiter is immediately followed by a logical source newline, that newline is discarded. If the closing delimiter is preceded on its source line only by indentation whitespace (exactly SPACE or TAB; see Horizontal Whitespace (Lexer Contract)) after a logical source newline, that trailing newline and indentation-only trailing line are discarded. That indentation-only trailing line alone establishes the structural indentation prefix: exactly the sequence of SPACE and TAB characters immediately before the closing delimiter, possibly empty. When content flows into the closing delimiter on its source line, no structural indentation prefix exists and no indentation normalization is performed. The structural prefix is matched as exact source characters and removed exactly once from the beginning of each non-blank content line; any further leading SPACE or TAB characters are content and are preserved. SPACE and TAB are never equivalent for indentation, Core v0.1 defines no semantic tab width, and no minimum-indent, common-visual-column, or editor-tab-stop computation is used. Where a structural prefix exists, a non-blank content line that does not begin with the exact prefix makes the literal invalid; consistent with the existing String-literal lexical-error model this is a lexical error, and no String value is produced. Where a structural indentation prefix exists, blank content lines need not contain the complete structural prefix, and their SPACE/TAB characters are removed as incidental indentation so that a source blank line contributes an empty logical line. Where no structural indentation prefix exists, no indentation or other whitespace is removed from any content line, including whitespace-only content lines, whose SPACE and TAB characters are ordinary String content and are preserved verbatim; blank-line whitespace stripping is conditional on a structural indentation prefix, so there is no unconditional blank-line cleanup (no structural indentation prefix ⇒ no indentation normalization). When a multiline String begins or ends on the same line as the delimiters, no implicit leading or trailing newline trimming occurs. Opening/trailing newline removal removes the complete logical newline sequence, so a removable `CRLF` is removed as one logical newline. Retained logical source newlines preserve their original source code points in the resulting String: `LF` remains U+000A, `CR` remains U+000D, and `CRLF` remains U+000D U+000A. Escape processing still follows the standard Core v0.1 String escape rules, and triple-double-quoted strings remain non-raw strings; an escape sequence is not a source SPACE or TAB and never satisfies the structural indentation prefix.

`true`, `false`, and `null` are canonical singleton values.

`===` is not overrideable. Hash codes are not identity: a hash collision must never cause two distinct identity-bearing objects to compare identical.

### Exact String semantic-value comparison

`sameSemanticValue` for the `STRING_VALUE` identity category compares the exact
ordered sequence of Unicode scalar values represented by each String.

Conceptually:

```text
function sameStringSemanticValue(a, b):
    aScalars = semanticUnicodeScalarSequence(a)
    bScalars = semanticUnicodeScalarSequence(b)

    if length(aScalars) != length(bScalars):
        return false

    for i from 0 to length(aScalars) - 1:
        if aScalars[i] != bScalars[i]:
            return false

    return true
```

This pseudocode specifies observable semantics, not a required traversal or
storage representation. Implementations may use length metadata, hashes,
interning, vectorized comparison, ropes, or other optimizations provided the
result is exactly the same.

The runtime must not normalize either operand, apply canonical or compatibility
equivalence, case-fold, consult a locale, compare grapheme clusters, or compare
host encoding units as though those were the String semantic value.

`identityHashOf` and the ordinary unspecialized String `hash` must be coherent
with this exact semantic identity: Strings that have the same scalar sequence
must receive equal identity hashes during one Protos execution. Distinct scalar
sequences may collide, but an implementation must not make normalization,
locale, host encoding, or storage representation an observable source of
identity-hash disagreement for semantically identical Strings.

---

# 26.1 Strict Float Evaluation

The semantic Float format in Core v0.1 is exactly IEEE 754-2019 `binary64`.
Implementations may represent Float objects differently internally, but every
observable Float value and every standard Float basic arithmetic result must be
equivalent to that semantic format.

Conceptually:

```text
function roundFloat(realResult):
    return IEEE754_binary64_roundTiesToEven(realResult)
```

For standard Float `+`, `-`, `*`, and `/`, each primitive operation is evaluated
as one `binary64` operation. An implementation must not retain excess precision
across a semantic operation boundary when doing so would change a later
observable result.

Likewise, an implementation must not contract a sequence of distinct Protos
operations into a fused operation such as fused multiply-add when the fused
result differs from performing the specified `binary64` operations separately.

Subnormal values are supported with gradual underflow. Flush-to-zero and
denormals-are-zero host modes may not change Protos semantics.

The host floating-point rounding mode and floating-point exception/status flags
are not Protos execution state in Core v0.1. Implementations must produce the
specified result even when host state differs.

IEEE floating-point conditions from these basic operations are represented by
Float results:

```text
overflow          -> appropriately signed infinity
gradual underflow -> binary64 subnormal or appropriately signed zero
division by zero  -> IEEE binary64 result, including signed infinity where applicable
invalid operation -> NaN
```

They do not by themselves signal a Protos error.

NaN payload bits and a NaN sign bit are not part of the Core semantic Float
value. Implementations are not required to preserve them across operations,
storage, Actor transfer, optimization, or serialization unless a future
explicit binary-format protocol defines a separate representation contract.

---

# 27. Semantic Equality

```text
function semanticEqual(a, b):
    result = send(
        receiver = a,
        message = "==",
        arguments = [b]
    )

    if result !== true and result !== false:
        signal InvalidEqualityResult(result)

    return result
```

`==` is ordinary object behavior and may be customized, but it has a strict protocol result contract: the result must be canonical `true` or `false`, or the operation must signal an error.

---



### Standard String concatenation runtime semantics

Standard String `+` is conceptually:

```text
function standardStringAdd(receiver, right):
    leftString = requireSemanticString(receiver)
    rightString = requireSemanticString(right)

    return stringFromExactUnicodeScalarSequence(
        unicodeScalars(leftString) + unicodeScalars(rightString)
    )
```

`requireSemanticString` validates semantic String-family membership without
coercion and without invoking user behavior.

`stringFromExactUnicodeScalarSequence` produces the semantic String value for
the exact concatenated scalar sequence. It performs no normalization,
encoding/decoding, locale processing, callback, equality/hash dispatch, or
mutation.

The String-family receiver-domain rule applies to the original receiver. A
non-String right operand signals `Error` after ordinary left-to-right operand
evaluation and before any concatenation result is produced.

## Equality and Comparison Result Validation

Runtime implementations of the standard comparison protocol must validate their result when necessary:

```text
function requireBooleanComparisonResult(result):
    if result !== true and result !== false:
        signal InvalidComparisonResult(result)
    return result
```

The exact internal error object names are not fixed by Core v0.1, but arbitrary non-Boolean values must not be accepted as truthy or falsy comparison results.

---

# 28. Lazy Boolean Operators

```text
function evaluateAnd(leftExpression, rightExpression, activation):
    left = evaluate(leftExpression, activation)

    rightClosure = createClosure(
        activation = activation,
        parameters = [],
        code = rightExpression
    )

    return send(
        receiver = left,
        message = "and",
        arguments = [rightClosure]
    )
```

Likewise:

```text
function evaluateOr(leftExpression, rightExpression, activation):
    left = evaluate(leftExpression, activation)

    rightClosure = createClosure(
        activation = activation,
        parameters = [],
        code = rightExpression
    )

    return send(
        receiver = left,
        message = "or",
        arguments = [rightClosure]
    )
```

The right-hand expression is therefore evaluated only if the receiving boolean behavior chooses to invoke the closure.

---

# 29. Error Signaling

Errors are objects.

```text
function signal(error, activation):
    handlerContext = activation

    while handlerContext != null:
        handler = findMatchingHandler(
            handlerContext,
            error
        )

        if handler != NOT_FOUND:
            return invokeHandler(
                handler = handler,
                error = error
            )

        handlerContext =
            dynamicParentOf(handlerContext)

    terminateAtExecutionBoundary(error)
```

### User-visible `Error.signal()` protocol

The standard `Error` prototype exposes the semantic signaling operation through
the ordinary zero-argument message `signal()`.

Conceptually:

```text
function standardErrorSignal(receiver, activation):
    if not delegationChainContains(receiver, Error):
        signalCoreProtocolError(
            operation = "Error.signal",
            receiver = receiver,
            activation = activation
        )

    signalErrorObject(
        error = receiver,
        activation = activation
    )

    UNREACHABLE
```

`signalErrorObject` searches the task's currently active dynamic handler frames
using the rules below. The exact receiver object is used for prototype matching
and is passed unchanged to the selected handler. The operation performs no
language-visible mutation of that object merely because it is being signaled.

`signalErrorObject` has a semantic precondition that its argument is `Error` or
has `Error` in its delegation chain. Every normative Core/runtime failure must
construct or otherwise provide such an object before invoking this operation.
The invalid-receiver branch above is therefore validation of the user-visible
standard method, not a recursive attempt to signal an arbitrary non-error
object.

Once `signalErrorObject` begins, the caller's signaling continuation cannot
complete normally. Selection of a handler performs the already-defined unwind
to that handler; absence of a matching handler reaches the applicable execution
boundary. A selected handler's eventual return is the result of the enclosing
`handle` installation construct, never a return value from `signal()` to the
original signaling point.

The standard method accepts no arguments. Implementations must not add implicit
conversion from String values, prototypes, host exceptions, or arbitrary
objects into signalable errors. Error creation and population are separate from
signaling.

Runtime-detected Protos failures invoke the semantic signaling operation rather
than performing ordinary overridable message dispatch to a possibly modified
`signal` slot. Conversely, an explicit source-level `error.signal()` expression
is an ordinary message send and therefore follows ordinary lookup/override
rules before the standard behavior, if selected, reaches this primitive.



### Non-resumability invariant

Core error transfer is an unwind transfer, not a resumable call/return protocol.

After `signalError(error)` selects a matching handler, the signaling
continuation is dead for Core semantic purposes. Executing the handler does not
retain an implicit continuation token for the `Error.signal()` call and handler
return does not re-enter the signaling activation.

Conceptually:

```text
signalError(error):
    handler = selectInnermostMatchingActiveHandler(error)

    if handler exists:
        deactivate handler
        unwind to handler boundary
        return executeHandlerAtBoundary(handler, error)

    terminate or fail at the applicable outer execution boundary
```

The conceptual `return` above is the result delivered at the handler boundary;
it is not a return to the original `Error.signal()` caller.

Implementations must not expose host-language resumable exceptions,
continuations, stack reification, debugger facilities, fibers, or VM exception
machinery as an implicit way to resume a Core signaling point. Any future
restart/recovery facility requires an explicit Protos semantic contract.

### Standard Handler Installation

The standard `Error` prototype provides the ordinary message:

```text
matchPrototype.handle(body, handler)
```

The receiver is the handler's match prototype. It must be `Error` or an object
whose delegation chain contains `Error`. `body` and `handler` must be Closures.

Receiver and argument expressions are evaluated by the ordinary call machinery
before this method begins; the handler installed by the call cannot handle an
error raised while those expressions are being evaluated.

Conceptually, handler installation behaves as follows:

```text
function handleError(matchPrototype, body, handler, activation):
    requireErrorPrototype(matchPrototype)
    requireClosure(body)
    requireClosure(handler)

    frame = HandlerFrame(
        matchPrototype = matchPrototype,
        handler = handler,
        dynamicParent = activation
    )

    return invokeProtectedBody(body, frame)
```

`invokeProtectedBody` invokes `body` with zero arguments while `frame` is the
dynamically innermost handler frame for that task. If `body` returns normally,
the frame is removed and that value is returned from `handle`.

Signaling searches active handler frames from dynamically newest to oldest. A
frame matches exactly by the existing prototype-chain rule. When a matching
frame is selected, the runtime performs an unwind to that frame before invoking
its handler Closure. The signaling continuation and the unexecuted remainder of
the protected body are abandoned.

Conceptually:

```text
function transferToHandler(frame, error):
    unwindThroughProtectedExtent(frame)
    deactivate(frame)
    return invokeClosure(
        frame.handler,
        arguments = [error]
    )
```

The handler Closure's normal return value becomes the normal return value of the
corresponding `handle` invocation. The selected frame is already inactive while
the handler Closure runs. Therefore a new error signaled by that handler searches
only still-active outer frames and can never be caught recursively by the frame
that selected it.

A nonmatching frame is skipped without mutation. Nested installations require no
separate handler-list ordering: dynamic nesting alone determines priority.

Handler frames belong to the currently executing task's dynamic control state.
Suspending that task while its protected extent is still active preserves the
frame in that task's continuation. Running another Actor-local task does not make
the suspended task's handlers active in that other task.

Creating a distinct asynchronous task or Future does not copy active handler
frames into the new task. Future failure remains recorded in the Future; a later
`value()` re-signals the stored error in the consumer's current dynamic context.
Dynamic handler frames never cross an Actor boundary.

Unwinding across a handler frame interacts with `ensure` exactly like any other
unwind: cleanup scopes crossed on the way to the selected handler execute under
the existing cleanup rules. A non-local return or another control transfer that
leaves the protected extent removes the frame. No resumable continuation is
retained by Core v0.1.

This protocol fixes the standard handler API and its observable dynamic extent.
Implementations may represent handler frames, task continuations, and unwind
machinery differently provided that these semantics are preserved.

The runtime architecture should not prevent resumable conditions from being added later.

---

# 30. Future Creation

Calling:

```js
work.future()
```

eventually reaches a scheduling primitive.

Conceptually:

```text
function executeAsFuture(closure, parentActivation):
    future = new Future(
        state = pending
    )

    task = scheduler.createTask(
        owner = parentActivation,
        body = () => {
            try:
                result = invoke(
                    closure,
                    []
                )

                resolveFuture(
                    future,
                    result
                )

            catch error:
                failFuture(
                    future,
                    error
                )
        }
    )

    future.task = task
    task.future = future

    registerChildTask(
        parentActivation,
        task
    )

    scheduler.schedule(task)

    return future
```

The scheduler implementation is not observable semantics.

### Future cancellation

Cancellation of Future-producing work is cooperative. The portable observation
boundaries are conceptualized as follows:

```text
function beforeFirstProtosExecution(task):
    if task.future.cancellationRequested:
        honorCancellation(task)
    else:
        beginProtosExecution(task)

function beforeExplicitSuspension(task):
    if task.future.cancellationRequested:
        honorCancellation(task)
    else:
        suspend(task)

function beforeResumeIntoProtos(task):
    if task.future.cancellationRequested:
        honorCancellation(task)
    else:
        resumeProtosExecution(task)

function honorCancellation(task):
    outcome = unwind current asynchronous activation with Cancellation

    if outcome is ErrorTransfer(error):
        failFuture(task.future, error)
        return

    complete task.future as CANCELLED


function recordFutureCancellationRequest(future):
    if future.state != pending:
        return

    if future.cancellationRequested:
        return

    future.cancellationRequested = true

    if future.adoptedSource != none:
        cancelPendingAdoption(future)
        return

    if future.task != none:
        scheduler.ensureCancellationRunnable(future.task)
```

`recordFutureCancellationRequest` is conceptual runtime bookkeeping, not a new
language-visible Future operation. Every Core path that requests cancellation of
a Future uses this same semantic operation.

`scheduler.ensureCancellationRunnable(task)` is likewise conceptual and
idempotent. If the task has not yet begun ordinary Protos execution, including
when it is waiting for a semantic prerequisite before its first turn, it makes
the task eligible to reach `beforeFirstProtosExecution(task)` without satisfying
that prerequisite. If the task is already suspended, it makes the task eligible
to reach `beforeResumeIntoProtos(task)` without satisfying the original wait
condition. If the task is already runnable, it does not enqueue a duplicate
semantic execution. If the task is currently executing ordinary non-suspending
Protos code, it does not preempt it or create a new cancellation boundary; the
request remains pending for the next portable boundary.

The transition from no request to a pending cancellation request is idempotent.
For a task that has not yet started or is already suspended, recording that first
request therefore makes the task cancellation-runnable so the applicable
portable boundary can honor it. For a Future that is currently adopting another
Future, adoption is itself the cancellation-aware pending operation, so the
request may complete the destination as cancelled through
`cancelPendingAdoption` without executing Protos code.

If neither case applies, the request remains recorded for the task or
producer-specific cancellation boundary that already governs that Future. In
particular, a non-task I/O Future is not made cancelled merely by this helper; its
producer still follows the applicable I/O commitment/cancellation contract.

This bookkeeping does not introduce an additional ordinary-code cancellation
safe point. Waking an already-suspended task and observing cancellation in a
normatively cancellation-aware adoption are consequences of the portable rules
already defined above.

A task that is already suspended is also cancellation-runnable. Recording a
cancellation request for that task must arrange for the task to become eligible
for scheduling even when the condition named by the original suspension remains
pending.

Conceptually, a suspension therefore has two independent reasons to make its
task runnable:

```text
original suspension condition becomes ready
task cancellation is requested
```

The latter reason affects only the suspended task. It does not invoke
`cancel()` on an awaited Future and does not alter the awaited producer.

When the task is selected after either reason, `beforeResumeIntoProtos(task)` is
applied before the suspended operation can deliver a successful result or
execute further ordinary Protos code. Consequently, a cancellation request that
is pending at that resume boundary wins for the consumer even if the original
condition also became ready. A later completion of the awaited Future remains
that Future's own completion and cannot re-enter or rewrite the cancelled
consumer task.

Implementations may remove the cancelled task's waiter registration eagerly or
leave inert bookkeeping until source completion, provided this cannot retain
unbounded dead waiters and cannot execute Protos code after the task has
cancelled.

An operation whose normative contract is cancellation-aware may invoke the
equivalent of `honorCancellation` while its underlying work is pending, subject to
that operation's commitment/effect rules.

Ordinary non-suspending execution must not observe cancellation merely because the
implementation reaches a call boundary, allocation poll, loop back-edge, VM/JIT
safepoint, garbage-collection point, host call, or other runtime checkpoint.
Carrier interruption and internal polling are implementation mechanisms only; they
must not introduce an additional Protos-observable cancellation point.

---

# 31. Future Resolution

```text
function resolveFuture(future, value):
    if future.state != pending:
        signal InvalidFutureState()

    if isFuture(value):
        adoptFuture(
            destination = future,
            source = value
        )

        return

    future.state = resolved
    future.value = value

    wakeWaiters(future)


function adoptFuture(destination, source):
    if destination.state != pending:
        signal InvalidFutureState()

    if destination === source or adoptionChainReaches(source, destination):
        failFuture(
            destination,
            FutureResolutionCycle
        )
        return

    switch source.state:
        case resolved:
            destination.state = resolved
            destination.value = source.value
            wakeWaiters(destination)
            return

        case failed:
            destination.state = failed
            destination.error = source.error
            wakeWaiters(destination)
            return

        case cancelled:
            destination.state = cancelled
            wakeWaiters(destination)
            return

        case pending:
            destination.adoptedSource = source

            onFutureCompletion(source, terminalResult => {
                // Runtime bookkeeping only; no Protos code runs inline here.
                if destination.state != pending:
                    return

                destination.adoptedSource = none

                switch terminalResult.state:
                    case resolved:
                        destination.state = resolved
                        destination.value = terminalResult.value

                    case failed:
                        destination.state = failed
                        destination.error = terminalResult.error

                    case cancelled:
                        destination.state = cancelled

                wakeWaiters(destination)
            })

            // A request may already have been recorded before adoption became
            // the destination's pending producer. Do not require a later edge.
            if destination.cancellationRequested:
                cancelPendingAdoption(destination)

            return


function adoptionChainReaches(start, target):
    current = start

    while current != none and current.state == pending:
        if current === target:
            return true

        current = current.adoptedSource

    return false


function cancelPendingAdoption(destination):
    // Runtime bookkeeping only; no Protos code executes here.
    if destination.state != pending:
        return

    if destination.adoptedSource == none:
        return

    if not destination.cancellationRequested:
        return

    destination.adoptedSource = none
    destination.state = cancelled
    wakeWaiters(destination)
```

Automatic Future flattening therefore means outcome adoption, not ownership,
identity, or cancellation adoption.

While `destination` adopts a pending `source`, `destination.state` remains
`pending`; `adoptedSource` is conceptual runtime bookkeeping and is not a fifth
Future state or a language-visible slot.

The adopted source's terminal outcome is mirrored exactly: resolution uses the
same resolved value, failure uses the same error object, and source cancellation
cancels the destination. No Protos transformation or handler is invoked merely
to propagate that terminal outcome.

Adoption is one-way. Cancelling the destination while adoption is pending may
cancel the destination but never requests cancellation of the source and never
changes the source's ownership or detachment. Likewise, detaching a task-backed
destination does not detach or re-parent the adopted source.

Adoption is a normative cancellation-aware pending operation. Therefore a
cancellation request on an adopting destination can be honored even though the
task body that initiated adoption has already returned. If source completion and
destination cancellation race, the first terminal transition of the destination
wins; later bookkeeping observes the terminal state and has no effect.

Future-adoption cycles are invalid. Direct self-adoption and any transitive
pending adoption that would make the destination reachable from the source's
adoption chain fail the destination with the standard `FutureResolutionCycle`
error prototype, which delegates directly to `Error`. A cycle must not be left as
an implementation-dependent permanently pending Future.

No adoption callback executes ordinary Protos code inline. Implementations may
represent the dependency with callbacks, waiter lists, graph nodes, or another
mechanism, provided the observable outcome, cancellation direction, cycle
handling, and terminal-state rules above are preserved.

---

# 32. Future Failure

```text
function failFuture(future, error):
    if future.state != pending:
        signal InvalidFutureState()

    future.state = failed
    future.error = error

    wakeWaiters(future)
```

`wakeWaiters(future)` is conceptual runtime bookkeeping with the following
observable contract:

```text
function wakeWaiters(future):
    waiters = future.waiters
    future.waiters = empty

    for each waiter in waiters:
        scheduler.makeRunnableLater(waiter)
```

Every execution continuation suspended by `awaitFutureValue(future, ...)` is one
waiter on that Future. A waiter may belong to a task-backed execution or to
another execution context that is permitted to suspend; waiter identity is the
continuation to resume, not an assertion that every Future observer is itself a
`Task`.

### Internal task records are not Protos values

Any runtime task/fiber/continuation record used to realize asynchronous work is
internal execution machinery, not a Core Protos value or identity. Core exposes
the Future outcome and the already-defined activation/execution-domain
semantics, not a handle to the scheduler object that happens to produce that
outcome.

No runtime transformation may become observable by changing the number,
identity, parentage, inlining, splitting, fusion, migration, or carrier
assignment of internal task records while preserving the specified Future,
cancellation, structured-ownership, Actor, and P behavior.



Waiter registration and the Future's first terminal transition obey the atomic
race rule defined by `suspendOnPendingFuture`: a completion cannot fall into a
gap between observing `pending` and installing the waiter. When the Future makes
its first terminal transition, every waiter that was still registered for that
Future becomes eligible to resume; no waiter is skipped merely because another
waiter was also registered.

Clearing the waiter registration before making waiters runnable is semantic
bookkeeping: once the Future is terminal, no task remains a pending waiter on
that Future. A waiter that was independently cancelled before this wake-up may
already have been removed or made inert according to the cancellation rules;
such a waiter must not execute ordinary Protos code merely because a stale
registration is later encountered.

The operation is idempotent with respect to terminal Future state: there is one
wake-up event for the Future's terminal transition, and later attempts to wake
the same terminal Future do not enqueue duplicate semantic resumptions.

Implementations may use intrusive lists, callbacks, waiter nodes, queues,
condition variables, or another representation. They must release or make
inert the Future's waiter registrations after terminal transition so that a
terminal Future does not retain suspended-task continuation state indefinitely.
A program-held reference to a task/Future remains governed by ordinary object
reachability; this rule concerns runtime waiter bookkeeping only.

A waiter becoming runnable does not imply that it will execute immediately, and
it does not bypass the existing before-resume cancellation boundary or the
weak-fairness rule.

There is no separate promise-rejection type.

The stored value is an ordinary language error object.

---

# 33. Waiting for a Future

For:

```js
future.value()
```

conceptually:

```text
function awaitFutureValue(future, activation):
    switch future.state:

        case resolved:
            return future.value

        case failed:
            signal(
                future.error,
                activation
            )

        case cancelled:
            signal(
                Cancelled,
                activation
            )

        case pending:
            suspendOnPendingFuture(
                future,
                activation
            )

            return awaitFutureValue(
                future,
                activation
            )
```

Conceptually:

```text
function suspendOnPendingFuture(future, activation):
    task = taskContaining(activation)  // may be none
    waiter = suspendedContinuationOf(activation)

    // This is the ordinary explicit-suspension cancellation boundary when the
    // current execution is task-backed and therefore has a cancellation target.
    if task != none and task.future.cancellationRequested:
        honorCancellation(task)
        return

    atomically with respect to future's first terminal transition:
        if future.state != pending:
            return

        registerWaiter(
            future,
            waiter
        )

        mark waiter suspended waiting on future

        if task != none:
            associateSuspendedWaiter(task, waiter)
```

The atomic region above specifies an observable race property, not a required
locking mechanism. There is no interval in which `awaitFutureValue` has decided
to wait because the Future appeared pending but the Future can complete without
either observing the registered waiter or causing the attempted suspension to
notice that completion.

Equivalently, every conforming implementation must ensure one of these outcomes:

```text
Future terminal transition happens first
    -> the consumer does not remain suspended on that Future

waiter registration happens first
    -> that terminal transition includes the consumer in wakeWaiters(future)
```

A runtime may implement this with a lock, compare-and-set state, generation
counter, register-then-recheck protocol, or another mechanism. Spurious internal
wake-ups are permitted only when they remain semantically invisible; lost
terminal notifications are not.

A Future waiter denotes the suspended execution continuation that must become
eligible to resume, not necessarily a `Task` object. Ordinary Actor turns,
bootstrap/root execution, or another execution context that can explicitly wait
on a Future need not be manufactured into a task-backed Future merely to use
`value()`.

When the waiting execution is task-backed, the waiter remains associated with
that task so the existing cooperative-cancellation machinery can make the
suspended continuation cancellation-runnable. If cancellation was already
pending at the explicit suspension boundary, cancellation is honored instead of
installing a live waiter. A cancellation request that arrives after that boundary
follows the existing cancellation-runnable rules and cannot strand the registered
waiter.

For a non-task-backed execution there is no task cancellation flag to consult;
this does not create an uncancellable hidden task. It only means that the
execution is waiting under its own enclosing lifecycle rather than under a
Future/task cancellation target.

Returning from `suspendOnPendingFuture` because the Future was already terminal
does not itself execute user code or consume the result. `awaitFutureValue`
re-enters the normal state switch, which returns or signals the stable terminal
outcome.

Suspending an activation does not imply blocking an OS thread.

---

# 34. Future Composition

For:

```js
future.then(value => {
    transform(value)
})
```

conceptually:

```text
function futureThen(source, transformClosure, callerActivation):
    destination = new Future(
        state = pending
    )

    continuationTask = scheduler.createTask(
        owner = callerActivation,
        body = () => {
            switch source.state:
                case failed:
                    failFuture(
                        destination,
                        source.error
                    )
                    return

                case cancelled:
                    if destination.state == pending:
                        destination.state = cancelled
                        wakeWaiters(destination)
                    return

                case resolved:
                    try:
                        transformed = invoke(
                            transformClosure,
                            [source.value]
                        )

                        resolveFuture(
                            destination,
                            transformed
                        )

                    catch error:
                        failFuture(
                            destination,
                            error
                        )

                case pending:
                    signal InvalidFutureContinuationState()
        }
    )

    destination.task = continuationTask
    continuationTask.future = destination

    registerChildTask(
        callerActivation,
        continuationTask
    )

    onFutureCompletion(source, terminalResult => {
        // Runtime bookkeeping only. This callback must not invoke Protos code.
        scheduler.makeRunnableLater(
            continuationTask
        )
    })

    // Even if source was already terminal, makeRunnableLater must not run the
    // continuation inline in this call.
    return destination
```

If `transformed` is itself a Future, `resolveFuture` adopts it and flattens the result.

---

# 35. Structured Concurrency

Asynchronous work belongs by default to the execution context that created it.

Conceptually:

```text
function registerChildTask(parentActivation, task):
    parentActivation.childTasks.add(task)
    task.owner = parentActivation
    task.detached = false

function requestCooperativeCancellation(task):
    recordFutureCancellationRequest(task.future)
```

The task/Future link is conceptual runtime bookkeeping. It does not add a
language-visible slot to `Future` or `Task`, and it does not require a particular
scheduler representation. A task-backed Future and its producing task denote one
cooperative cancellation target: requesting cancellation through either
structured ownership or `future.cancel()` sets the same request observed by that
task at portable cancellation boundaries.

Before a newly created asynchronous task executes its first ordinary Protos
instruction, `beforeFirstProtosExecution(task)` is mandatory. This is a semantic
task-lifecycle boundary, not an implementation-selected VM/JIT safepoint. A
cancellation request already pending at that boundary is honored before the task
body can produce ordinary Protos effects. After first execution begins, ordinary
non-suspending code acquires no extra cancellation checkpoints from this rule.

A Future produced by a non-task facility such as an I/O operation may have no
`task`; its producer observes `future.cancellationRequested` according to that
facility's normative cancellation/commitment contract.

A parent activation cannot reach terminal completion while it owns non-detached child tasks.

Waiting for child termination during normal owner completion is not equivalent to
calling `value()` on those child Futures. The wait establishes the structured
lifetime boundary only; it neither consumes nor propagates a child result, failure,
or cancellation. In particular, no implementation may make normal owner completion
depend on whether a failed child Future was previously "observed", because Protos
defines no hidden failure-consumption state on Future objects.

Normal completion:

```text
function completeActivationNormally(activation, result):
    for each task in activation.childTasks:
        if not task.detached:
            awaitTerminalCompletion(task)

    // Structured ownership bounds lifetime only. Reaching a failed or
    // cancelled child terminal state here does not implicitly observe that
    // child's Future and does not replace `result`.
    complete activation with result
```

Error or cancellation unwind:

```text
function unwindActivation(activation, controlTransfer):
    children = all non-detached tasks owned by activation

    for each task in children:
        requestCooperativeCancellation(task)

    for each task in children:
        awaitTerminalCompletion(task)
        // child ensure cleanup has completed here

    continueUnwind(activation, controlTransfer)
```

Child cancellation uses the normal cooperative cancellation semantics: no unsafe forced termination is permitted, and child `ensure` cleanup runs before terminal completion.

Detachment:

```text
function detachFuture(future):
    if future.task == none:
        return future

    if future.state != pending:
        return future

    if future.task.detached:
        return future

    owner = future.task.owner

    future.task.detached = true
    future.task.owner = none

    if owner != none:
        remove future.task from owner

    return future
```

`Future.detach()` is an idempotent ownership operation and always returns the
same Future object. Its only semantic effect is to remove a still-pending
task-backed Future from its current activation's structured lifetime.

If the Future is not task-backed, there is no structured task ownership to
detach, so `detach()` is a state-preserving no-op. This includes Futures produced
directly by facilities such as I/O operations. Detachment does not cancel,
re-parent, abandon, or otherwise alter such a producer.

If the Future is already terminal, `detach()` is likewise a state-preserving
no-op. Repeated calls after successful detachment are no-ops. None of these cases
signals merely because there is no remaining structured ownership edge.

A detached task no longer participates in the former activation owner's
completion or cancellation lifetime. Detachment changes structured activation
ownership only; an Actor-local task remains in the same Actor execution and
lifecycle domain.

Conceptually, Actor termination while its hosting runtime can still execute
cleanup includes:

```text
### Actor delivery admission fairness

Actor delivery backpressure has a liveness obligation distinct from runnable
task scheduling. Conceptually:

```text
function considerAdmission(scope):
    candidates = liveDeliveryOperationsEligibleForAdmission(scope)

    choose some candidate subject to:
        no candidate that remains continuously admission-eligible
        may be bypassed forever while compatible admission
        opportunities repeatedly occur

    if candidate exists:
        advance candidate according to the ordinary routing and
        concrete-Actor acceptance rules
```

The choice mechanism is intentionally unspecified. It may use FIFO queues,
fair semaphores, rotating producer queues, tickets, aging, or another strategy.

For the same sender incarnation and same concrete Actor, the chooser must also
preserve the existing issuance FIFO among still-live operations. This
admission-order constraint ends for an earlier operation when that operation is
cancelled or becomes terminal before acceptance.

Admission eligibility is not Actor-task runnability. Making a delivery
operation admission-eligible therefore does not create an Actor turn and does
not weaken the existing definition of scheduler weak fairness.


### Core Cluster membership protocol boundary

Core runtime semantics consume established membership knowledge but do not
prescribe how that knowledge is distributed:

```text
function mayUseNodeAsClusterCapacity(clusterView, node):
    return clusterView.establishesMembership(node)
        and ordinaryEligibilityRulesHold(node)
```

The following implications are invalid in Core:

```text
transportConnected(node) -> member(node)
reachable(node)          -> member(node)
member(node)             -> hasAuthority(node)
notMember(node)          -> terminated(node)
```

An implementation-specific membership subsystem may maintain local views,
epochs, gossip state, consensus state, or external-service registrations.
Those structures are runtime machinery unless a future normative Cluster
facility standardizes them.

Core code must not observe unspecified membership-protocol timing or ordering as
a language guarantee. Membership-dependent runtime behavior may use only
membership facts that the active runtime has established without weakening the
closed identity, reachability, uncertainty, and Authority rules.


### Core split-brain safety

Core has no automatic partition winner or downing strategy. Runtime control
therefore reduces split-brain safety to Authority checks:

```text
function authorizeDuringReachabilityLoss(operation):
    if not operation.requiresAuthority:
        return ordinarySemanticEligibility(operation)

    if demonstrateCurrentAuthority(operation.authorityScope):
        return ALLOW

    return DENY
```

`DENY` means the authoritative operation does not occur. It does not by itself
terminate the local Process/Node, terminate the remote side, acquire replacement
Authority, or convert unreachability into termination.

A Core runtime must not contain a semantic shortcut such as:

```text
if partitionSuspected:
    winner = chooseMajorityOrOldestOrLocalSide()
    down(otherSide)
```

unless a future normative facility explicitly defines that policy and the
Authority/fencing guarantees that make its decision valid.

After reachability returns, ordinary communication and control may resume only
according to the identities, membership decisions, Authority state, and
operation outcomes that actually survived; Core performs no implicit state
merge or uncertain-message replay.


### Network-partition reporting

Core runtime classification must not infer a special semantic partition state
from communication loss:

```text
function reportDistributedCommunicationLoss(remoteScope):
    if authoritativeTerminationAlreadyKnown(remoteScope):
        return TERMINATED

    if communicationCurrentlyUnavailable(remoteScope):
        return UNREACHABLE

    return UNKNOWN
```

An internal detector may additionally record diagnostics such as
`partitionSuspected`, transport errors, probe history, or topology evidence.
Those diagnostics are non-semantic unless a future normative facility defines
otherwise.

In particular, Core has no transition of the form:

```text
unreachableFor >= implementationPartitionThreshold
    -> NETWORK_PARTITION
    -> acquireAuthorityOrTerminateRemoteSide
```

Restored communication may make the remote scope reachable again when no
independent authoritative decision has ended or removed the relevant
incarnation.


### Node termination knowledge

Core treats remote Node failure suspicion separately from authoritative
termination:

```text
function classifyRemoteNodeAfterCommunicationLoss(nodeRef):
    if authoritativeNodeTerminationAlreadyKnown(nodeRef):
        return TERMINATED

    if communicationCurrentlyUnavailable(nodeRef):
        return UNREACHABLE

    return UNKNOWN
```

`authoritativeNodeTerminationAlreadyKnown` is lifecycle or membership knowledge
established by an already-defined normative authority. It is not satisfied
merely by a heartbeat timeout, phi value, retry limit, transport exception,
host probe, container status, or infrastructure event.

Core v0.1 therefore has no transition of the form:

```text
failureDetectorSuspects(nodeRef)
    -> TERMINATED
```

A future distributed facility may add a suspicion detector and a distinct
downing/removal decision only through its own normative contract. Merely
suspecting a Node must remain reversible and must not itself retarget ActorRefs,
terminate hosted remote entities, or authorize replacement.


### Process termination knowledge

Core distinguishes direct lifecycle knowledge from distributed failure
detection:

```text
function classifyRemoteProcessAfterCommunicationLoss(processRef):
    // No Core heuristic may infer terminal lifecycle from silence.
    if authoritativeTerminationAlreadyKnown(processRef):
        return TERMINATED

    if communicationCurrentlyUnavailable(processRef):
        return UNREACHABLE

    return UNKNOWN
```

`authoritativeTerminationAlreadyKnown` denotes knowledge established by an
already-defined lifecycle authority, not a heartbeat timeout, retry limit,
transport exception, or host-specific probe.

Core v0.1 therefore has no runtime transition of the form:

```text
missedHeartbeats >= implementationThreshold
    -> TERMINATED
```

A future distributed failure-detection facility may introduce additional state
and transitions only through its own normative contract.


### Core Actor failure-authority policy

After `failActorIncarnation(actor, error)` has established fatal termination,
Core policy is conceptually:

```text
function applyCoreFailureAuthorityPolicy(actor, error):
    if actor is actor.process.rootActor:
        terminateProcessBecauseRootActorFailed(actor.process, actor, error)
        return

    // Non-root Core Actor:
    // termination itself is the complete failure-authority action.
    // Do not replace, escalate, or affect unrelated Actors here.
    return
```

This function does not prevent a distinct ActorGroup controller from later
observing that desired Group state is unsatisfied and creating a fresh Actor
incarnation. Such reconciliation is not a continuation or restart of the failed
Actor.

Core runtime implementations may fuse this policy into lifecycle machinery and
need not materialize a separate failure-authority object.


### Actor lifecycle observation

`ActorRef.termination()` is represented conceptually as a non-task-backed
Future observation:

```text
function observeActorTermination(actorRef):
    result = newPendingFuture(task = none)

    atomically:
        if terminationKnown(actorRef):
            resolveFuture(result, actorRef)
            return result

        registerTerminationObserver(actorRef, result)

    return result

function onActorTerminationKnown(actorRef):
    observers = takeLiveTerminationObservers(actorRef)

    for each observer in observers:
        resolveFuture(observer, actorRef)
```

Registration and the known-termination transition are one semantic atomicity
boundary. Implementations may realize it with locks, CAS, epochs,
register-then-recheck, distributed monitor protocols, or equivalent machinery.

Cancellation of `result` uses ordinary Future cancellation. Honoring that
cancellation removes or makes inert only `result`'s observation registration;
it does not mutate `actorRef`'s Actor.

`UNREACHABLE` and `UNKNOWN` do not call `onActorTerminationKnown`. Network or
node failure detection may do so only when another normative contract has
established that the concrete Actor incarnation is terminated rather than
merely unreachable.


### Unhandled Actor-turn failure

Conceptually, every ordinary Actor turn has an outer runtime boundary:

```text
function runActorTurn(actor, turn):
    try:
        execute(turn)
    on Error error escaping outermost dynamic handler boundary:
        failActorIncarnation(actor, error)
```

`failActorIncarnation` is lifecycle failure, not ordinary Future failure of the
turn. It records structured failure information for the Actor's failure
authority, prevents subsequent ordinary turns for that incarnation, and invokes
the existing Actor-termination cancellation/cleanup machinery.

A distinct asynchronous task retains the existing rule:

```text
function runAsyncTask(task):
    try:
        value = execute(task.body)
        resolveFuture(task.future, value)
    on Error error:
        failFuture(task.future, error)
```

The task error does not additionally call `failActorIncarnation`. If some later
Actor turn observes `task.future` and that observation re-signals the error,
`runActorTurn` handles fatality only if the re-signaled error escapes that later
turn unhandled.

Cancellation follows `honorCancellation` and is not routed through the
unhandled-`Error` fatality branch.


### Actor storage reclamation after termination

Actor lifecycle termination and implementation storage reclamation are distinct.

Conceptually:

```text
function mayReclaimActorImplementationState(actor):
    require actor.lifecycle == TERMINATED
    require reclaiming state preserves all remaining observable
            ActorRef, identity, monitoring, routing, and communication semantics
    return true
```

There is no corresponding `mayCollectLiveActorBecauseUnreferenced` operation in
Core v0.1. Ordinary object-graph reachability, absence of known ActorRefs,
idle-time heuristics, and memory pressure are not Actor termination causes.

An implementation may replace a terminated Actor's full runtime representation
with compact terminal metadata when that substitution is observationally
equivalent.


### Actor graceful-stop lifecycle cutover

The concurrency model's graceful-stop cutover is represented conceptually as:

```text
function beginGracefulActorTermination(actor):
    if actor.lifecycle is TERMINATING or TERMINATED:
        return

    actor.lifecycle = TERMINATING

    stopConcreteActorAcceptance(actor)
    preventNewOrdinaryTurns(actor)

    classifyAcceptedButNotStartedInteractionsAsLost(actor)

    cancelActorLocalWorkForTermination(actor)

    when required Actor-local task cleanup is complete:
        actor.lifecycle = TERMINATED
```

`classifyAcceptedButNotStartedInteractionsAsLost` does not execute their handlers
and does not rewrite acceptance history. Sender-visible outcomes use the ordinary
accepted-work loss and request-uncertainty rules.

If a turn was already executing when `beginGracefulActorTermination` established
the cutover, the runtime does not inject an asynchronous exception into arbitrary
ordinary code. The turn remains subject to the existing cancellation boundaries.
Normal completion before the next such boundary remains normal completion; reaching
a boundary with the termination cancellation pending begins the ordinary
cancellation unwind.

The conceptual `when` above is not permission to wait for every residual
non-task-backed producer Future. `cancelActorLocalWorkForTermination` already
separates required Actor-local task cleanup from producer custody. The Actor can
become TERMINATED once required Actor-local task cleanup has finished, even if
committed backend work continues under runtime/producer custody.

function cancelActorLocalWorkForTermination(actor):
    tasks = all pending Actor-local tasks in actor
        // includes tasks detached from activation ownership

    producerFutures = all pending non-task-backed Futures
        representing asynchronous operations initiated by actor

    for each task in tasks:
        requestCooperativeCancellation(task)

    for each future in producerFutures:
        recordFutureCancellationRequest(future)

    for each task in tasks:
        awaitTerminalCompletion(task)
        // ordinary cancellation unwind and applicable ensure cleanup complete

    // Actor termination does not wait merely for producerFutures to become
    // terminal. Their producers retain only the custody needed to obey their
    // own cancellation/commitment contracts.
```

Once Actor termination has begun, those tasks are not ordinary surviving work.
They may receive execution only as required to observe the already-requested
cancellation at the existing portable boundaries and to run the corresponding
unwind/`ensure` cleanup. No task is re-parented to another Actor, the RootActor,
or the Process.

The producer-Future cancellation pass is distinct from task cleanup. It does not
execute arbitrary Protos code in the terminating Actor, does not close an I/O
receiver, and does not revoke or destroy a Process-level capability. If a
producer has already committed an external effect or crossed a communication
acceptance boundary, the producer continues only under its own existing semantic
contract. A later producer completion does not resurrect the Actor or schedule
ordinary code in its dead execution domain.

If cleanup completes normally, the task Future reaches `cancelled`; if cleanup
signals an error, the existing cleanup-supersedes-cancellation rule makes that
Future `failed`. Actor replacement does not inherit the task or its Future.

This cleanup rule assumes that the hosting runtime remains able to schedule the
terminating Actor's cleanup work. Loss of the Process/runtime/execution substrate
may prevent further cleanup, but never permits Actor-local task execution to
resume in a different execution domain.

The scheduler may implement waiting by suspension rather than by blocking an operating-system thread.

---

# 36. Canonical Evaluation Sketch

A minimal evaluator can be described as:

```text
function evaluate(node, activation):

    match node:

        Literal(value):
            return value

        Lookup(name):
            return lookupName(
                activation,
                name
            )

        Member(receiverExpr, name):
            receiver = evaluate(
                receiverExpr,
                activation
            )

            return readMember(
                receiver,
                name
            )

        Create(targetExpr?, name, valueExpr):
            if targetExpr is absent:
                return createSlot(
                    target = activation.context,
                    name = name,
                    value = evaluate(
                        valueExpr,
                        activation
                    )
                )

            target = evaluate(
                targetExpr,
                activation
            )

            value = evaluate(
                valueExpr,
                activation
            )

            return createSlot(
                target,
                name,
                value
            )

        Assign(targetExpr?, name, valueExpr):
            if targetExpr is absent:
                return assignName(
                    activation,
                    name,
                    evaluate(
                        valueExpr,
                        activation
                    )
                )

            target = evaluate(
                targetExpr,
                activation
            )

            value = evaluate(
                valueExpr,
                activation
            )

            return assignMember(
                target,
                name,
                value
            )

        Closure(parameters, body):
            return createClosure(
                activation,
                parameters,
                body
            )

        Call(receiverExpr, arguments):
            receiver = evaluate(
                receiverExpr,
                activation
            )

            values = evaluateArguments(
                arguments,
                activation
            )

            return invoke(
                receiver,
                values
            )

        Send(receiverExpr, message, arguments):
            receiver = evaluate(
                receiverExpr,
                activation
            )

            values = evaluateArguments(
                arguments,
                activation
            )

            return send(
                receiver,
                message,
                values
            )

        Return(valueExpr):
            value = evaluate(
                valueExpr,
                activation
            )

            nonLocalReturn(
                activation,
                value
            )

        Sequence(expressions):
            result = null

            for each expression:
                result = evaluate(
                    expression,
                    activation
                )

            return result
```

This sketch is intentionally small.

Evaluation is strictly left-to-right wherever operands are evaluated. `Create` and `Assign` evaluate the target expression, when present, before the value expression: `getObject().x = makeValue()` evaluates `getObject()`, then `makeValue()`, then performs the assignment, and explicit slot creation `getObject().x: makeValue()` follows the same order. When no target expression exists, only the value expression is evaluated. Call and message-send arguments are evaluated left-to-right, and Indexed Access Lowering evaluates the receiver, then the index, then the assigned value, in the same left-to-right order.

Most high-level language behavior should be expressed through ordinary objects and message sends rather than by adding evaluator cases.

Expression separation is resolved entirely during parsing. The parser-level separators — an inline `;` between two expressions on the same logical source line, and a separating logical `NEWLINE` between expressions on different source lines — are source-level syntax: neither becomes a semantic AST node, and no runtime node, value, or object corresponds to either separator. A logical `NEWLINE` token consumed as continuation — while a syntactic construct is necessarily incomplete, or immediately before a leading structural `.` — likewise appears nowhere in the semantic representation. Repeated separating `NEWLINE` tokens (blank lines) and layout newlines inside open delimited constructs are formatting: they produce no semantic AST nodes and no runtime behavior, and they never create empty, omitted, or `null` expressions. Only the expressions themselves become distinct elements of a `Sequence`, so `a: 1; b: 2` and the newline-separated form contain the same ordered `Sequence` elements and are evaluated strictly left-to-right in `evaluateSequence`. Commas that separate elements of argument and parameter lists are resolved entirely during parsing as well: they delimit the list elements but produce no runtime node and introduce no runtime behavior. Argument evaluation, parameter binding, spread, rest, and default semantics are unchanged.

---

# 37. Runtime Boundary

The following operations may require implementation primitives:

```text
object allocation
slot storage
object identity
native arithmetic
I/O
filesystem access
process creation
network access
scheduler interaction
activation suspension/resumption
garbage collection
```

These primitives do not alter the object model.

They are implementation services exposed through ordinary language objects and messages whenever practical.

---

# 38. Design Test

A proposed new feature should normally be rejected as a new runtime primitive if it can be expressed cleanly using:

```text
objects
slots
delegation
closures
message sends
errors
futures
```

The runtime should grow only when the language requires behavior that cannot be implemented faithfully above that layer.

---

# Module Contexts and Top-Level Bindings

The language has no special global-variable category.

Every module executes inside a `moduleContext`, which is an ordinary language object. A binding created at the top level of a module is therefore simply a local slot of that module's execution context.

For example:

```js
version: "0.1"

printVersion: () => {
    print(version)
}
```

is conceptually:

```text
moduleContext
├── version
└── printVersion
```

Closures created by top-level module execution capture the module context through the normal lexical-context mechanism. No separate global lookup rule is required.

Conceptually, lexical lookup may eventually reach the module context:

```text
context
    ↓
captured lexical contexts
    ↓
moduleContext
```

After lexical lookup is exhausted, the ordinary receiver/delegation lookup rules continue as specified elsewhere in this document.

Modules do not implicitly share mutable global state. Each module instance has its own module context, local to the Actor that instantiated the module. Cross-module visibility is established explicitly through the module system and ordinary module instances, including the standard `import(specifier)` protocol and resolver plus Actor-local module-cache semantics defined by Core v0.1.

Universal language facilities such as core prototypes and standard behavior may be made available through a shared prelude or root environment. Such an environment is part of lexical/runtime setup and does not create a separate global-variable semantic category.

The standard prelude is a shared **frozen** context. Lookup may read its slots normally, but assignment may not modify them. Consequently, an unqualified assignment whose only matching slot is in the prelude fails with the ordinary non-writable/frozen assignment error. Shadowing is explicit slot creation in the module context.

```text
print("hello")     -> read prelude.print
print = myPrint     -> ERROR
print: myPrint      -> create moduleContext.print
```

Runtime initialization MUST freeze the prelude before executing user modules. `assignName` MUST respect that frozen state and MUST NOT special-case the prelude by mutating it.

Freezing is shallow, so freezing the prelude does not by itself make arbitrary mutable objects referenced by its slots safe to share between Actors. Any Protos object physically shared between Actors through the standard prelude must be semantically immutable for the duration of that sharing; mutable Protos state reachable through standard facilities must be Actor-local. The implementation may physically share immutable implementation artifacts — parsed syntax, bytecode, machine code, immutable metadata, and immutable constant data — where the sharing is semantically unobservable. Mutable standard-library/runtime state belongs to the Actor that uses it. The existing rule that freeze is shallow is unchanged: no deep freeze is introduced, Actor isolation is not weakened, and implementations are not required to duplicate immutable data unnecessarily.

Top-level creation:

```js
x: value
```

is equivalent to creating a local slot on the current module context when the current activation's `context` is that module context.

Conceptually:

```text
function createModuleContext(preludeContext):
    require preludeContext.state == frozen

    return Object(
        parent = Context,
        state = open
    )
```

The module instance of a module is its `moduleContext` object: the object in which the module body executes and on which its top-level bindings are created. `createModuleContext` therefore creates the module instance.

```text
function executeModuleBody(module, moduleContext, preludeContext):
    activation = Activation(
        context = moduleContext,
        lexicalParent = preludeContext,
        receiver = moduleContext,
        methodHome = null,
        returnHome = null,
        ownsReturnHome = false
    )

    executeModuleCode(
        module.code,
        activation
    )
```

`executeModuleBody` associates the frozen `preludeContext` as `lexicalParentOf(moduleContext)`. The module context's delegation chain — `Context` → `Object` — is its delegation chain, not its lexical chain: bare-name lookup reaches the prelude through `lexicalParentOf` only.

Module loading, canonical identity, Actor-local caching, initialization states, cycle handling, and failure semantics are defined later in this document; host-specific resolution and package policy remain outside Core v0.1.

Core invariant:

```text
There are no global variables as a special runtime category.

Top-level bindings are slots of a module execution context.

Modules do not implicitly share mutable global state.
```


## Dynamic Typing Runtime Requirement

Runtime dispatch is dynamic. Slots and parameters carry objects, not mandatory declared static types.

An implementation may specialize nodes based on observed receiver shapes, prototypes, numeric representations, or inferred types, but deoptimization must preserve the same semantics when those assumptions stop holding.

No runtime overload-resolution mechanism based on declared argument types is part of Core v0.1.

## Conditional Message Semantics

There is no runtime-wide `toBoolean`, truthiness table, or implicit Boolean coercion.

Conditional operations use normal message dispatch.

Conceptually:

```text
send(condition, "ifTrue", [block])
send(condition, "ifFalse", [block])
```

are ordinary sends. The standard objects `true` and `false` implement these messages with the expected Boolean behavior.

Logical operators preserve laziness by passing the right-hand expression as a closure:

```text
a && b  =>  send(a, "and", [closure(() => b)])
a || b  =>  send(a, "or",  [closure(() => b)])
```

The receiver decides whether to invoke the supplied closure.

Objects other than `true` and `false` may implement the same protocol. If a receiver does not understand the message, normal message-lookup failure semantics apply.

Equality and comparison implementations, including user-defined ones, are constrained by their protocol contract to return the canonical Boolean objects `true` or `false`, or signal an error. Returning another object is an invalid protocol result.

An implementation may use inline caches, specialized AST nodes, partial evaluation, or JIT compilation for common cases such as receivers known to be `true` or `false`, or numeric `+`. These optimizations must be observationally equivalent to the corresponding ordinary message sends.


## Error Signaling, Handler Matching, and Unwinding

Errors are ordinary objects. Signaling an error searches dynamically active handlers from the current activation outward.

Each handler has a match prototype. A handler matches when that prototype occurs in the signaled error object's delegation chain. Matching therefore uses the normal prototype/delegation model rather than a class hierarchy or static type test.

Conceptually:

```text
function signal(error, activation):
    handler = nearestMatchingDynamicHandler(error, activation)

    if handler == none:
        terminateAsUnhandled(error)

    unwindTo(handler.activation)
    invokeHandler(handler, error)
```

Conceptually, prototype matching is:

```text
function handlerMatches(handler, error):
    current = error

    loop:
        if current === handler.matchPrototype:
            return true

        if current === Object:
            return false

        current = current.parent
```

`Object` is the unique root and has no parent; no `null` or hidden sentinel is used as a delegation-chain terminator.

Core v0.1 handlers are **unwinding handlers**. Invoking a matching handler abandons the signaling continuation. If the handler returns normally, execution continues according to the handler-installation construct; it does not return a value to the original `signal` operation and does not resume immediately after the signaling point.

Core v0.1 does not expose resumable conditions, `resume`, `retry`, or equivalent operations. Implementations should keep signaling, handler search, and stack transfer conceptually separable so that a later explicit resumable-condition facility can be introduced without redefining error objects or prototype-based handler matching.\n\n### Error Prototype Taxonomy

Error taxonomy is semantic and intentionally shallow in Core v0.1.

`Error` is the mandatory root prototype for signaled Core errors and delegates
directly to `Object`. Unless a normative specification explicitly defines a
different standard parent relation, every normatively named standard error
prototype is a direct child of `Error`.

Conceptually:

```text
Object
  ^
  |
Error
  ^
  |
named standard error prototype
```

An implementation must not insert additional Protos-visible ancestors between a
standard error prototype and `Error`, because `handlerMatches` and ordinary
reflection could observe that difference.

For a Core failure whose normative rule merely says "signals an error" without
defining a named standard prototype, portable code may rely on matching `Error`
and on no finer implementation-chosen category. Runtime pseudocode constructor
names used to explain an operation do not by themselves create standard
prelude bindings or normative intermediate categories.

Program- and library-defined error prototypes remain ordinary objects and may
form arbitrarily deep delegation hierarchies below `Error`. The runtime uses the
same `handlerMatches` algorithm for those objects; no separate error type system
exists.\n
## Module Instances and the Actor-Local Module Cache

Module identity, caching, initialization, cycle handling, and failure are Actor-local. Each Actor owns a module cache keyed by canonical internal module identity. An Actor is an isolated domain of mutable Protos state and execution, with no shared mutable Protos memory between Actors. The module cache and the module instances it holds are part of that Actor's isolated runtime state. The broader Actor concurrency model is developed in `PROTOS_CONCURRENCY_MODEL.md`; this section depends only on the isolation and ownership consequences stated here.

Conceptually, the Actor-owned module state is:

```text
ActorModuleState:
    moduleCache: ModuleKey -> ModuleRecord

ModuleRecord:
    state          // INITIALIZING | READY
    instance       // the module instance; the module's moduleContext object
```

The cache maps each canonical `ModuleKey` to at most one active `ModuleRecord` at a time; a record whose state is `INITIALIZING` or `READY` is the active module instance for that key in this Actor. Cache membership and ordinary object reachability are distinct concepts: removing an entry ends a module instance's status as the active cached instance for its key without revoking, rolling back, or otherwise invalidating the instance object itself.

A module that is absent from the cache is not loaded in that Actor.

Possible module states are:

```text
INITIALIZING
READY
```

`INITIALIZING` means the module instance exists in the cache but its body has not completed execution. `READY` means its body completed successfully. A failed initialization attempt is removed from the cache; failure is not retained as a permanently cached module state. These states are internal semantics and are not exposed through a public state-inspection or reflection API.

A module specifier is not itself the cache key. The loader first resolves it relative to the importing module:

```text
resolve(importerKey, moduleSpecifier) -> ModuleKey
```

`ModuleKey` must be canonical and stable within an Actor. Equivalent requests for the same module must resolve to the same key.

For a file-backed host this may conceptually involve normalization such as:

```text
"./lib/../lib/foo.pt"
    -> canonical file identity
    -> file:///project/lib/foo.pt
```

The exact key representation is host-defined and need not be visible to language code.

The distinction is therefore:

```text
specifier resolution / locating code
        may be host-defined

module identity / Actor-local instance / cache /
initialization / cycles / failure
        defined by Protos semantics
```

Conceptual Actor-local module lifecycle:

```text
# Shared by import and by Actor startup of an importable initial module.
# Returns the Actor's active cached module instance for key, creating and
# caching it as INITIALIZING and executing its body first when no active
# record exists. Because import and initial-module startup both call this
# same function, neither path can create a second active cached instance
# for the same canonical ModuleKey.
function ensureModuleInstance(actor, key):
    record = actor.moduleCache.lookup(key)

    if record != none:
        # READY or INITIALIZING: the same Actor-local module instance is
        # returned. In the INITIALIZING case the module body is currently
        # executing (a recursive import, or a startup module already
        # registered before its body began); the body is not run again and
        # the existing partial instance is returned immediately without
        # waiting for READY.
        return record.instance

    # Cache before execute: the module instance is inserted as INITIALIZING
    # before its body executes, so recursive imports discover the same
    # instance instead of creating fresh ones.
    instance = createModuleContext(frozenPrelude)
    record = ModuleRecord(
        state = INITIALIZING,
        instance = instance
    )
    actor.moduleCache.insert(key, record)

    try:
        executeModuleBody(moduleForKey(key), instance, frozenPrelude)
    catch initializationFailure:
        actor.moduleCache.remove(key)
        signal initializationFailure

    record.state = READY
    return instance


function importModule(actor, importerKey, specifier):
    key = resolve(importerKey, specifier)
    return ensureModuleInstance(actor, key)


# Actor startup of the initial module when, at the moment that entry's
# execution begins, the host module resolver has an importable canonical
# identity for it. This function always starts an importable initial
# module through the ordinary cached lifecycle (ensureModuleInstance); it
# is never used to adopt or re-register an instance that was already
# executed as a standalone entry point.
function executeInitialModule(actor, initialModuleKey):
    return ensureModuleInstance(actor, initialModuleKey)


# Actor startup of a host entry point that the resolver cannot map to a
# canonical ModuleKey. The choice between executeInitialModule and this
# standalone path is made when execution of the entry begins and is not
# revisited later. The instance created here is Actor-local, is not
# registered in the module cache, has no ModuleKey, and remains outside
# the Actor's import namespace for its whole life. It is never later
# adopted as the active cached module instance of any ModuleKey, even if
# the host's resolution capabilities later change. If equivalent code is
# later imported through a canonical ModuleKey, ensureModuleInstance
# consults the Actor-local module cache: a cache miss creates a new,
# distinct module instance.
function executeStandaloneEntry(actor, entryModule):
    instance = createModuleContext(frozenPrelude)
    executeModuleBody(entryModule, instance, frozenPrelude)
```

Cache-before-execute invariant:

> The module must be discoverable through recursive imports before its body has finished executing.

When a recursive import discovers an existing `INITIALIZING` module in the current Actor's cache, the import returns that module instance immediately. It does not suspend waiting for initialization to finish and does not create a fresh instance; suspending would deadlock ordinary cyclic imports within the same Actor. No hidden Actor reentrancy or hidden suspension point is introduced for this case. Actor reentrancy remains identifiable only from explicit suspension operations. Cyclic module dependencies are therefore valid and are not rejected merely because they are cyclic.

A partially initialized module is observable: reading a member of the returned instance observes the module's top-level binding slots exactly as they exist at that point in sequential execution. Only slots whose creating top-level statement has already executed are present; reading a slot that has not yet been created follows the ordinary missing-slot / lookup error semantics. There is no predeclaration of module slots, no hoisting of future slot creations, and no module-specific temporal-dead-zone mechanism.

When execution of the module body completes normally, the record transitions:

```text
INITIALIZING -> READY
```

The same module instance and the same `moduleContext` remain cached, and a later import in that Actor returns that instance without re-executing the module body. No new module identity is created because initialization completed.

If module initialization terminates with an unhandled error:

- the initiating `import()` fails with that error according to the normal error-propagation model;
- the module instance does not remain as a successfully cached module;
- that attempt's cache entry is removed;
- a later import may attempt initialization again and may create a fresh module instance.

A failed attempt does not permanently poison the Actor's module cache, and a failed partial module instance is not defined as reusable by a later independent import. If cyclic participants obtained a reference to the partially initialized instance before failure, no rollback of already-executed observable effects or object references is invented: errors do not reverse effects that already occurred unless an existing rule explicitly says otherwise. Removing the failed module from the cache does not undo side effects already performed during its failed initialization.

A failed partial instance that remains reachable through an escaped reference stays an ordinary object after its cache entry is removed: it is not revoked, rolled back, or otherwise invalidated, and its continued existence does not prevent a later import from creating a fresh instance. The failed instance and a later successful instance may therefore both remain reachable within the same Actor, with the later instance being the active cached module instance for that `ModuleKey`:

```text
foo#1 -> INITIALIZING -> failure -> removed from cache
foo#2 -> INITIALIZING -> READY
```

with `foo#1 !== foo#2` under ordinary identity comparison. This coexistence is within one Actor and does not violate Actor isolation.

An Actor's initial module is executed by that Actor with the same module-context model: its `moduleContext` is created as above, belongs to that Actor, and is Actor-local rather than process-global. Creating a new Actor does not inherit the creator's module cache or live module contexts. When, at the moment Actor startup begins, the host module resolver has an importable canonical identity for the initial module, Actor startup uses `ensureModuleInstance` (through `executeInitialModule`), so the initial module is cached as `INITIALIZING` before its body executes and a cyclic import back to it returns the same instance rather than creating a second one. If the initial module fails to initialize after such registration, the ordinary failure rule applies: the cache entry is removed and no retry is invented as part of Actor startup. A host entry point that has no importable canonical identity at that same moment is executed directly by `executeStandaloneEntry` and is not registered under an invented module identity.

The choice between the importable-initial-module path and the standalone path is fixed when execution of the initial entry begins; it is not revisited later. A standalone instance is never later adopted as the active cached module instance of a `ModuleKey`, and later changes to the host's resolution capabilities do not mutate its identity or status. If code equivalent to a previously executed standalone entry later becomes importable under a canonical `ModuleKey`, a subsequent `import()` operates only on the Actor-local module cache: a cache miss creates a new module instance and executes its body through `ensureModuleInstance`. The standalone instance and that later cached instance are distinct objects under `===`, and the module body and its side effects may run again; this is not a double initialization of one module instance, because the standalone instance never occupied that `ModuleKey`. No retroactive cache registration, module-instance adoption, identity mutation, cache migration, source-code deduplication, or rollback is introduced.

Imports are eager by default. Lazy dependency behavior is expressed explicitly using ordinary closures or other language mechanisms rather than by changing import evaluation semantics. The module specifier is an ordinary expression, and `import(specifier)` returns the module instance; it does not introduce names into the importing lexical scope.

Host-specific resolution policy, package lookup, standard-library naming, remote sources, and package-manager behavior are outside Core Runtime Semantics v0.1.


## Indexed Access Lowering

The runtime has no separate semantic indexing primitive required by the language.

Slot operations and indexing protocol operations are distinct runtime paths. Slot creation (`createSlot`) and slot assignment (`assignMember`) operate on the object's local slot model, while indexed access rewrites to ordinary `at` / `atPut` sends whose behavior is defined by the receiver's protocol. Indexed contents are not automatically object slots, and an indexable object remains an ordinary object with ordinary slots.

The parser or semantic-lowering phase rewrites bracket forms to normal sends:

```text
receiver[index]
    -> Send(receiver, "at", [index])

receiver[index] = value
    -> Send(receiver, "atPut", [index, value])
```

Conceptual evaluation for indexed write:

```text
receiverValue = evaluate(receiver)
indexValue = evaluate(index)
assignedValue = evaluate(value)
send(receiverValue, "atPut", [indexValue, assignedValue])
result = assignedValue
```

Each subexpression is evaluated once and in left-to-right order.

No runtime lowering exists for indexed slot creation: `receiver[index]: value` is rejected syntactically by the grammar, has no semantic AST node, and never reaches the runtime. There is no indexed-creation primitive and no `atCreate`-style protocol. Whether `atPut` creates a new indexed entry, replaces an existing one, extends a collection, requires an existing or in-range index, or rejects the operation is defined by the receiver's `atPut` protocol; the `=` in indexed assignment imposes no universal existence requirement on the key or index.

`at` and `atPut` are ordinary selectors. Arrays, maps, strings, foreign objects, user-defined collections, or unrelated domain objects may implement either message. The runtime must not impose array-specific dispatch merely because bracket syntax was used.

Consistent with the existing assignment-expression rule, indexed assignment evaluates to the value written. The return value of the underlying `atPut` message is not the value of the indexed assignment expression.


## Standard Array Indexed-State Semantics

### Standard Array factory invocation

The standard invocation behavior provided by `Array` creates fresh receiver-owned
standard Array indexed state instead of using `Object`'s default
child-plus-`init` constructor.

Conceptually:

```text
function standardArrayFactoryInvoke(invocationReceiver, arguments):
    return newStandardArrayWithElements(
        elements = arguments,
        parent = invocationReceiver,
        state = open
    )
```

`arguments` is the already-evaluated outgoing positional argument vector.
Construction preserves its order and stores the exact argument object
references.

`newStandardArrayWithElements` creates a fresh identity and fresh standard Array
indexed state. Its optional `parent` parameter fixes the new Array's delegation
parent; it does not copy indexed state from that parent and does not require the
parent itself to own Array state.

The standard prelude `Array` object exposes this invocation behavior through the
ordinary invocation protocol. If another prototype inherits that same behavior
through ordinary delegation, invoking that prototype uses the actual invocation
receiver as `parent` for the fresh Array. Thus inherited factory behavior creates
new Array state without reclassifying the prototype receiver itself.

No `init` message is sent by this standard factory behavior. No argument value
is interpreted as a requested length or capacity. In particular, one semantic
Integer argument is one Array element exactly like any other object.

The operation performs no Protos user-code callback after invocation begins.
Implementations may allocate backing storage eagerly, lazily, compactly, or
through copy-on-write machinery provided fresh Array identity, exact element
references, element order, parent, open state, and all ordinary Array semantics
remain observationally identical.

The existing conceptual use:

```text
newStandardArrayWithElements(values)
```

for runtime-created Arrays such as `args` and rest bindings remains valid; when
no explicit parent is shown there, it denotes the standard `Array` parent unless
that surrounding rule explicitly specifies another parent.



Standard Array primitives operate on receiver-owned dense indexed state.

Conceptually:

```text
function requireArrayReceiver(receiver):
    if not ownsStandardArrayIndexedState(receiver):
        signal an Error for incompatible standard Array receiver

    return receiver

function requireArrayIndex(array, index):
    if not isSemanticIntegerValue(index):
        signal an Error for invalid Array index

    i = mathematicalIntegerValue(index)

    if i < 0 or i >= arrayIndexedLength(array):
        signal an Error for Array index out of bounds

    return i
```

These predicates are semantic runtime checks, not message sends. In particular,
`requireArrayReceiver` does not test delegation ancestry, and
`requireArrayIndex` does not invoke conversion, `hash`, equality, parsing, or
host integer coercion.

The standard read is equivalent to:

```text
function standardArrayAt(receiver, index):
    array = requireArrayReceiver(receiver)
    i = requireArrayIndex(array, index)
    return arrayIndexedElement(array, i)
```

The standard update is equivalent to:

```text
function standardArrayAtPut(receiver, index, value):
    array = requireArrayReceiver(receiver)

    if array.state == frozen:
        signal FrozenObject(array)

    i = requireArrayIndex(array, index)

    setArrayIndexedElement(array, i, value)
    return value
```

`setArrayIndexedElement` replaces one existing element. It never changes the
Array's indexed length, creates a sparse position, appends, inserts, removes, or
shifts another element.

A closed Array permits this replacement because no indexed structural growth or
removal occurs. A frozen Array does not. The frozen-state check precedes index
validation so a standard `atPut` whose receiver cannot be mutated does not
perform later Array-index validation work. Receiver and argument expressions
have already been evaluated by ordinary call evaluation before the standard
method begins, and those earlier effects are not rolled back.

The indexed state is separate from `receiver.localSlots`. Ordinary slot
creation/removal does not create/remove Array elements, and Array element
replacement does not mutate a local slot merely because an Integer or String
could also be used as some application-level slot name.

Implementations may represent dense Array state with contiguous storage,
segmented storage, persistent structures, specialized element layouts, or other
internal forms. They may use host-sized indices internally after the semantic
Integer has passed the normative range check, but host integer width,
overflow/wrapping, storage layout, and capacity must remain unobservable.

This runtime rule introduces no Array construction surface, resizing primitive,
slice object, iterator object, or extra identity relation. Standard Array
equality/hash continue to use the ordinary default identity semantics unless
user code explicitly overrides the ordinary messages.

### Standard Array iteration runtime semantics

Standard `Array.each(block)` validates the original receiver as a standard Array
and then captures a shallow logical snapshot of the receiver's indexed element
references in ascending index order before invoking user code.

Conceptually:

```text
`requireInvokable(value)` uses the same callability domain as ordinary
parenthesized invocation. Conceptually:

```text
function requireInvokable(value):
    if isBoundClosure(value) or isClosure(value):
        return value

    if lookupInvocationBehavior(value) == NOT_FOUND:
        signal NotCallable(value)

    return value
```

The check does not invoke the callback. It performs only the ordinary
callability lookup needed to determine whether a subsequent `invoke(value, ...)`
has an invocation behavior. Consequently `Array.each` does not create a
Closure-only callback category, and user-defined invokable objects participate
without adaptation.

function standardArrayEach(receiver, block):
    array = requireArrayReceiver(receiver)
    requireInvokable(block)

    snapshot = snapshotArrayElements(array)

    for element in snapshot from first to last:
        invoke(block, [element])

    return array
```

`snapshotArrayElements` captures exactly the element references stored at
indices `0 .. arrayIndexedLength(array) - 1` at the snapshot point. It performs
no user-message dispatch and invokes no equality, hashing, conversion, or
element protocol.

The snapshot is shallow. Mutating an element object does not replace the
snapshot reference. Replacing an indexed element in the Array after snapshot
capture does not rewrite that already-captured reference.

No standard Array mutation restriction is active merely because `each` is
running. A callback or another Actor-local task may invoke `atPut` on the same
Array when the existing state rules permit it. If such replacement succeeds,
later callbacks in the current `each` still receive the references captured by
the snapshot rather than re-reading current Array indexed state.

If a callback suspends, `snapshot` remains part of the suspended continuation's
logical state. The Actor scheduler remains free to run other work. No lock,
condition wait, mutation guard, or scheduler exclusion is introduced by Array
iteration.

Normal callback return proceeds to the next snapshot element. Error unwind,
non-local return, cancellation unwind, or another ordinary control transfer
that leaves the `each` invocation stops traversal immediately; no later element
is invoked, and prior effects are not rolled back.

The pseudocode does not require materializing a second Array. Implementations may
retain immutable/versioned storage, copy references lazily, use copy-on-write,
or employ another strategy provided that every callback observes exactly the
element reference captured for its snapshot position.

## Invocation Argument Binding

Each invocation records the caller-supplied positional arguments before default substitution.

Conceptually:

```text
Activation
    context
    receiver
    arguments
    methodHome
    returnHome
    ...
```

The intrinsic `args` resolves to an immutable ordinary collection representing `Activation.arguments`.

For a receiver-aware send:

```js
receiver.message(a, b)
```

the invocation state is conceptually:

```text
this = receiver
args = [evaluated(a), evaluated(b)]
```

The receiver is not inserted into `args`.

Parameter binding proceeds from left to right. Caller-supplied arguments bind first. Missing parameters with default expressions evaluate those defaults in the new invocation context. The original `args` collection is not modified by default substitution.

A trailing rest parameter receives an ordinary collection containing caller-supplied arguments that were not consumed by preceding positional parameters.

Spread arguments are evaluated left-to-right with surrounding arguments. After evaluation, their elements are expanded into the outgoing positional argument sequence exactly once.

Conceptually:

```text
f(...values)
```

becomes a normal invocation whose outgoing argument vector contains the elements produced by the spread operation.

The runtime may optimize argument vectors, rest Arrays, and `args` Arrays, but observable semantics must remain those of the frozen standard Arrays defined above.

No dispatch by argument type is implied. These mechanisms support dynamic arity, forwarding, and user-defined helper protocols without introducing method-overload resolution.



### Standard Array parallelMap runtime semantics

Conceptually:

```text
parallelMap(receiver, worker, extras, caller):
    requireStandardArrayReceiver(receiver)
    requireOrdinarilyInvokable(worker)

    source = shallowAscendingElementSnapshot(receiver)

    if source.size == 0:
        return resolvedFuture(freshStandardArray())

    prepared = freshFixedSequence(source.size)

    for i in 0 .. source.size - 1:
        prepared[i] =
            preparePInvocationGraph(
                worker,
                [source[i]] + extras
            )
        // failure here is synchronous NonParallelValue
        // no child is eligible before the whole loop succeeds

    resultFuture = freshPendingFutureOwnedBy(caller.activation)

    for i in 0 .. source.size - 1:
        scheduleIsolatedPChild(
            prepared[i],
            completionIndex = i,
            owner = resultFuture
        )

    return resultFuture
```

`preparePInvocationGraph` applies the ordinary P value/copy/projection rules.
Distinct indexes denote distinct child P domains. The implementation may share
immutable physical representation or otherwise optimize preparation only when
the specified independent logical child inputs remain observationally intact.

Child completion records either a successfully transferred result value or an
indexed failure. The parent result Future resolves only after every required
index has a successful transferred result. It resolves to a fresh standard Array
whose indexed order is the source-index order, not completion order.

If indexed failures exist, terminal failure selection is the smallest failing
source index. Implementations may avoid or cancel later work only after doing so
cannot change that selected logical failure or any other observable semantics.

Cancellation of the parent result Future requests cancellation of unfinished
children according to the normal structured Future/P rules. No partially filled
result Array becomes visible.

Physical chunking, batching, fusion, sequential execution, SIMD, work stealing,
and worker count are runtime choices and do not alter the conceptual per-index P
isolation or deterministic result/failure rules.


### Standard Array parallelFilter runtime semantics

Conceptually:

```text
parallelFilter(receiver, predicate, extras, caller):
    requireStandardArrayReceiver(receiver)
    requireOrdinarilyInvokable(predicate)

    source = shallowAscendingElementSnapshot(receiver)

    if source.size == 0:
        return resolvedFuture(freshStandardArray())

    prepared = freshFixedSequence(source.size)

    for i in 0 .. source.size - 1:
        prepared[i] =
            preparePInvocationGraph(
                predicate,
                [source[i]] + extras
            )
        // synchronous NonParallelValue on failure
        // no child is eligible until every input succeeds

    resultFuture = freshPendingFutureOwnedBy(caller.activation)

    for i in 0 .. source.size - 1:
        scheduleIsolatedPChild(
            prepared[i],
            completionIndex = i,
            owner = resultFuture
        )

    return resultFuture
```

Each child normal result is classified only as canonical `true` or canonical
`false`. Any other normal result records `InvalidPredicateResult` for that source
index.

After all logically relevant indexed outcomes are known, successful selected
elements are assembled in ascending source-index order into a fresh standard
Array. A selected value must cross to the caller domain under ordinary P result
rules; an untransferable selected value records caller-domain
`NonParallelValue` for that index. Rejected elements need not be transferred
back.

If any indexed failure exists, the smallest failing source index is the
deterministic operation failure. No partially assembled result Array is exposed.
Physical batching, fusion, vectorization, sequential execution, chunking, worker
count, and work stealing are unobservable implementation choices.


### Standard Array parallelFindIndex runtime semantics

Conceptually, preparation is identical to `parallelFilter`: validate the standard
Array receiver and ordinary predicate callability, capture the ascending shallow
source snapshot, prepare every non-empty per-index P invocation graph before any
child becomes eligible, and return an already-resolved `null` Future for empty
input.

Each indexed child records exactly one logical outcome:

```text
FALSE
TRUE
FAIL(error)
```

where a non-Boolean normal result is normalized to
`FAIL(InvalidPredicateResult)`.

Completion processing maintains the smallest source index not yet proven
`false`. The parent Future may become terminal when that frontier index has a
decisive outcome:

```text
TRUE
    -> resolve with that semantic Integer index

FAIL(error)
    -> fail with that error

FALSE
    -> advance the frontier across any consecutively completed FALSE indexes
       until another unresolved or decisive index is reached
```

If the frontier advances past the final source index, resolve with `null`.

A higher-index TRUE or failure may be recorded early but cannot become observable
while any lower index is unresolved. Once a decisive frontier outcome becomes
terminal, unfinished higher-index work may be cooperatively cancelled or omitted
when that cannot change other specified semantics.

Scheduler timing, batching, chunking, vectorization, worker count, and physical
completion order never alter the frontier rule.


### Standard Array parallelReduce runtime semantics

Conceptually, after receiver/callability validation, the runtime establishes the
submission snapshot required by the reduction before returning a pending Future.

For non-empty input, reduction state is a logical sequence of isolated values.
Each round constructs nodes from adjacent positions:

```text
next = []
for pairStart in 0, 2, 4, ...:
    if pairStart + 1 exists:
        next.add(
            isolatedPInvoke(
                reducerSnapshot,
                current[pairStart],
                current[pairStart + 1],
                argumentSnapshot...
            )
        )
    else:
        next.add(current[pairStart])
```

The next logical round is admitted only after every combine node in the current
round has a successful transferred result. On multiple failures in the same
round, select the failing node with the smallest `pairStart`.

A runtime may overlap physical implementation stages only when it preserves this
logical round/failure relation exactly. The final sole value is transferred to
the caller domain and resolves the Future.

The empty case returns resolved `null`. The singleton case performs no reducer
call but still realizes the required isolated value snapshot/transfer. No
intermediate sequence is a public Protos value.


### Standard Array parallelSort runtime semantics

The normative model is a stable recursive merge sort over the logical source
snapshot. Runtime data structures used to realize the tree are not Protos values.

Conceptually:

```text
sort(values):
    if values.size <= 1:
        return values

    split = floor(values.size / 2)

    leftResult  = sort(values[0 .. split])
    rightResult = sort(values[split .. size])

    require both successful
    return merge(leftResult, rightResult)
```

The two recursive sorts may run in parallel. If both fail, the left failure is
selected.

`merge` proceeds by output position. For current heads `a` and `b`, it performs
the two isolated Boolean comparator invocations for `(a,b)` and `(b,a)` and
applies the normative decision table. Comparator failures are ordered first by
merge output position and then forward comparison before reverse comparison.

When both comparisons are false, merge takes the left value and therefore
preserves source-relative order for equivalent values. When both are true,
`InvalidComparatorOrder` is recorded for that merge decision.

A physical implementation need not allocate this exact recursive structure, but
its result, comparator-visible behavior, failure selection, stability, P
boundaries, and cancellation/publication semantics must be observationally
equivalent.

### Exact call-spread expansion

Call-argument evaluation expands a spread operand using standard Array indexed
state directly.

Conceptually:

```text
function expandCallSpread(value):
    array = requireArrayReceiver(value)
    snapshot = snapshotArrayElements(array)
    return snapshot
```

`snapshotArrayElements` has the same shallow element-reference meaning used by
standard Array iteration, but spread expansion invokes no callback. It captures
the current indexed element references in ascending index order.

For an argument list, evaluation remains left-to-right. Conceptually:

```text
function evaluateCallArguments(argumentItems, activation):
    outgoing = []

    for item in argumentItems from left to right:
        if item is ordinary argument:
            outgoing.append(
                evaluate(item.expression, activation)
            )
            continue

        if item is spread argument:
            value = evaluate(item.expression, activation)
            elements = expandCallSpread(value)
            outgoing.appendAll(elements)
            continue

    return outgoing
```

If evaluation of a spread expression has ordinary effects, those effects remain
completed. If `requireArrayReceiver` then fails, no later argument item is
evaluated and no invocation occurs.

Spread expansion itself performs no user-message dispatch and has no explicit
suspension point. It therefore cannot execute user-defined `each`, `at`, `size`,
conversion, iterator, equality, hashing, or callback behavior merely to obtain
the expanded elements.

The captured outgoing references are independent of later structural or element
replacement changes to the source Array. They are not deep copies: mutation of
an element object through another reference remains visible through the same
argument object.

Implementations may avoid materializing an intermediate snapshot object or even
an intermediate outgoing vector when observable evaluation order, error timing,
element identity, and expansion order remain exactly equivalent to this model.

## Invocation argument Array representation

The conceptual `immutableArgumentCollection(values)` operation used by
activation creation and rest-parameter binding produces a fresh standard Array
whose indexed elements are exactly `values` in order, then freezes that Array
before exposing it to Protos code.

Conceptually:

```text
function immutableArgumentCollection(values):
    array = newStandardArrayWithElements(values)
    freeze(array)
    return array
```

`newStandardArrayWithElements` creates receiver-owned standard Array indexed
state and a fresh Array identity. `freeze(array)` has the ordinary shallow
frozen-object meaning.

Consequently:

```text
activation.arguments
```

is a fresh frozen standard Array for each activation, and every rest binding
created by `bindParametersLeftToRight` is another fresh frozen standard Array.
No `args` Array aliases the Array object of another invocation, and a rest Array
does not reuse the current activation's `args` identity.

The runtime may scalar-replace, virtualize, cache backing storage, or otherwise
avoid allocating a concrete Array object when those optimizations preserve
fresh semantic identity if observed, standard Array lookup/receiver behavior,
frozen mutation failure, element identity, ordering, `size`, and `each`.

Standard Array size is conceptually:

```text
function standardArraySize(receiver):
    array = requireArrayReceiver(receiver)

    return semanticIntegerFromMathematicalValue(
        arrayIndexedLength(array)
    )
```

The operation performs no element-message dispatch. Internal host-sized lengths
or indexes are permitted only when they preserve the exact mathematical Integer
result and cannot make overflow, truncation, or saturation observable.

Argument Arrays contain object references, not copied argument values.
Shallow freezing therefore does not freeze or copy mutable argument objects.

## Polymorphic Call Protocol and Default Construction (Normative Detail)

Invocation is a protocol operation on the evaluated receiver.

Conceptually:

```text
Call(receiverExpression, arguments):
    receiver = evaluate(receiverExpression)
    args = evaluateArgumentsLeftToRight(arguments)
    return invoke(receiver, args)
```

`invoke` is polymorphic. Closures provide executable-call behavior. Ordinary prototypes inherit default construction-call behavior from `Object`.

The default `Object` call behavior is conceptually:

```text
function objectCall(prototype, args):
    instance = createObject(parent = prototype)

    signalOrReturn = send(instance, "init", args)

    return instance
```

The result of `init` is deliberately ignored. Successful construction returns the fresh instance.

If `init` signals, normal error-unwinding semantics apply and the construction call does not successfully return the instance.

The standard `Object.init` accepts no arguments. A non-empty argument vector handled by the inherited default initialization signals an argument-count error.

`init` is found through ordinary message lookup beginning at the fresh instance, so a prototype may specialize initialization simply by providing an `init` slot.

Alternative constructors require no runtime facility. Named constructor-like messages are ordinary sends and may invoke the receiver or another prototype through the same call protocol.

Object-literal creation:

```text
Object(parent, body)
```

remains a separate semantic AST operation. It creates a fresh object with the given parent and evaluates its object body; it does not implicitly invoke `init`.

The implementation may specialize closure invocation and standard object construction in Truffle nodes or JIT-compiled paths, but observable behavior must remain equivalent to the polymorphic call protocol.


## Unwind-Safe Cleanup

The runtime maintains cleanup registrations associated with dynamic execution scopes.

Conceptually:

```text
ensure(body, cleanup):
    register cleanup for protected dynamic scope

    execute body

    on leaving the protected scope:
        execute cleanup
```

Cleanup runs when the protected scope is exited by:

```text
normal completion
non-local return (^)
error unwind
cancellation unwind
```

For cancellation, honoring the request converts that request into the active
control transfer before cleanup starts. Suspension points reached while executing
cleanup for that unwind do not re-observe the same already-honored cancellation
request:

```text
function runCleanupDuringUnwind(cleanup, controlTransfer):
    if controlTransfer is Cancellation:
        suppressRedeliveryOf(controlTransfer.request)

    try:
        invoke(cleanup)
    catch cleanupError:
        return ErrorTransfer(cleanupError)
    finally:
        if controlTransfer is Cancellation:
            stopSuppressingRedeliveryOf(controlTransfer.request)

    return controlTransfer
```

`suppressRedeliveryOf` is conceptual, not prescribed runtime machinery. It does
not suppress ordinary errors, failures from cleanup operations, or explicit
observation of some other Future's terminal result. Its only required effect is
that the cancellation request whose delivery caused the current unwind cannot
interrupt its own `ensure` cleanup at a later suspension boundary.

If cleanup returns normally, the original completion or control transfer continues.
For cancellation, the Future becomes `CANCELLED` only after every applicable
cleanup scope has completed.

If cleanup signals an error, the cleanup error becomes the active control transfer.
A previously active non-local return, error unwind, or cancellation unwind does
not continue past that point. A cleanup error during cancellation therefore fails
the task Future rather than completing it as cancelled.

This behavior uses the same general runtime machinery that tracks non-local control transfer and dynamic handlers, but resumable conditions are not required for Core v0.1.

A future condition that is resumed without leaving the protected dynamic scope must not trigger its cleanup merely because the condition was signaled.

The runtime may represent cleanup registrations as unwind records, dynamic frames, or another implementation-specific structure. The representation is not observable.

No GC finalizer or reachability callback is part of the deterministic resource-lifetime semantics.


## Exact Cross-Family Numeric Equality

Numeric `==` is defined by mathematical value rather than by coercing one operand into the representation of the other.

Conceptually:

```text
function numericEqual(a, b):
    require isNumeric(a)
    require isNumeric(b)

    return exactNumericValueCompare(a, b)
```

`exactNumericValueCompare` must be representation-aware enough to avoid rounding-induced false equality.

For Integer-versus-Float comparison, the runtime must not simply convert an arbitrary-precision Integer to Float. It may instead compare the exact Integer against the exact finite binary value represented by the Float.

For example:

```text
9007199254740992 == 9007199254740992.0  -> true
9007199254740993 == 9007199254740992.0  -> false
```

Cross-family numeric equality must be symmetric when both sides complete normally.

Numeric semantic identity is stricter. Conceptually:

```text
numericIdentity(a, b):
    return sameSemanticNumericFamily(a, b)
       and sameNumericIdentityValue(a, b)
```

Therefore:

```text
1 === 1.0               -> false
UInt8(1) === 1          -> false
Int32(1) === UInt32(1)  -> false
```

The implementation may use optimized paths, but it must preserve this distinction between exact numeric equality and numeric-family-sensitive identity.

Special Float identity/equality rules for NaN and signed zero are defined separately.

### Numeric hash normalization

The standard Number-family hash operation is conceptually based on a canonical
numeric equality key:

```text
function standardNumericHash(number):
    key = numericHashKey(number)
    return executionLocalHashInteger(key)
```

`numericHashKey` is representation-independent and must satisfy:

```text
numericEquals(a, b) == true
    => numericHashKey(a) == numericHashKey(b)
```

for every pair of Core Number values.

For finite values, the key represents the exact mathematical numeric value. It
must not contain the semantic numeric family, fixed-width Integer prototype,
signedness, boxing identity, source spelling, or host storage representation.

Consequently exact cross-family equalities share one key, including Integer
values and exactly equal binary64 Float values. Positive and negative Float zero
also share one key.

All Core Float NaN semantic values use one distinguished numeric hash key.
`numericEquals(NaN, NaN)` remains false; this special key exists only to prevent
hidden IEEE NaN representation details from leaking through standard hashing.

`executionLocalHashInteger` returns a semantic Integer and may use
per-execution salting or randomization. It need not be injective: collisions
between unequal numeric keys are valid. It must nevertheless be stable for a
given numeric key for the duration of the Protos execution.

`executionLocalHashInteger` is named for the semantic scope of the observable
mapping, not for an implementation process. If one Protos execution uses
multiple operating-system processes, workers, threads, Actors, or machines,
host placement must not cause the same standard numeric hash key to acquire a
different observable Integer solely because it is evaluated in another host
container. Implementations may propagate immutable execution-scoped hash
configuration or use any equivalent mechanism; no shared mutable hash registry
or runtime-global lock is required.

This abstract operation does not require conversion of an exact Integer through
binary64 and must not introduce rounding merely to hash it. Implementations may
use any optimized representation or mixing strategy that preserves the
specified equality classes and observable Integer-result contract.

The standard Number-family `hash` message uses `standardNumericHash(this)`;
it does not inherit the identity-based `Object.hash()` result for semantic
Number values. Identity-sensitive machinery continues to use
`identityHashOf(number)` instead.

## Float NaN Semantic Identity

IEEE-754 permits multiple NaN encodings, including distinct payloads and categories such as quiet and signaling NaNs.

Core v0.1 does not expose those representation distinctions through ordinary semantic identity.

Conceptually:

```text
function floatSemanticIdentity(a, b):
    if isNaN(a) and isNaN(b):
        return true

    return sameFloatIdentityValue(a, b)
```

By contrast, numeric equality follows IEEE-style NaN comparison behavior:

```text
NaN == anyNumericValue  -> false
anyNumericValue == NaN  -> false
```

including:

```text
NaN == NaN -> false
```

A runtime may preserve NaN payloads internally or expose them through an explicit low-level representation protocol, but payload/sign/boxing differences must not make ordinary `===` distinguish semantic NaN values.

`NaN` is not required to be implemented as a canonical singleton object. The runtime may produce many host-level or boxed NaN representations while preserving:

```text
nanA === nanB -> true
```

for semantic Float NaN values.

## Float Signed Zero Runtime Semantics

Float semantic equality and identity treat signed zero differently.

Conceptually:

```text
function floatNumericEqual(a, b):
    if isZero(a) and isZero(b):
        return true

    return ieeeNumericEqual(a, b)
```

Semantic identity preserves the sign distinction:

```text
function floatSemanticIdentity(a, b):
    if isNaN(a) and isNaN(b):
        return true

    if isZero(a) and isZero(b):
        return sameZeroSign(a, b)

    return sameFloatIdentityValue(a, b)
```

Therefore:

```text
+0.0 == -0.0   -> true
+0.0 === -0.0  -> false
```

The implementation may use native IEEE-754 operations or specialized representations, but must preserve these observable semantics.

## Numeric Runtime Semantics

Integer arithmetic is semantically exact and must not expose host-machine overflow. A runtime may specialize common integer operations using native machine widths and promote transparently to arbitrary-precision storage when required.

Such specialization is not observable through identity, equality, message lookup, or arithmetic results.

Fixed-width integer objects have explicit range and width semantics. Ordinary operations that exceed the representable range signal an error rather than silently wrapping. Separate explicitly wrapping protocols may be provided.

Numeric protocol dispatch remains ordinary receiver-based message lookup. Integer-only messages such as bit operations are found through the receiver's delegation chain; there is no static overload resolution.

Floating-point values may use an IEEE-754-compatible host representation provided observable language semantics are preserved.

Byte order is applied only when encoding or decoding numeric values to or from byte sequences. Endianness is not stored as an intrinsic property of the abstract numeric value.


## String and Bytes Runtime Separation

The runtime must preserve the semantic distinction between Unicode text and raw byte sequences.

A `String` represents abstract Unicode text. Its internal storage format is implementation-specific and must not be observable as though it were the String's semantic encoding.

A `Bytes` value represents an ordered sequence of byte values with no implicit character encoding.

Encoding and decoding are explicit operations parameterized by an encoding protocol/object. Implementations may intrinsify common encodings such as UTF-8 while preserving ordinary observable message semantics.

The exact meaning of String indexing and String size is defined separately from byte representation; internal code-unit layout must not determine those operations accidentally.


## Text Indexing and Mutability Runtime Semantics

Observable `String.size` and `String.at` semantics are based on Unicode grapheme clusters, not on the runtime's internal byte or code-unit representation.

Implementations may cache grapheme boundaries, specialize common ASCII/Latin text, or use representation-specific fast paths, provided observable indexing semantics remain unchanged.

`String` values are immutable. Runtime optimizations such as interning, deduplication, compact encodings, ropes, slices, or structural sharing are permitted when they preserve value semantics.

`Bytes` values are mutable raw byte sequences.

Encoded text representations are ordinary objects whose mutability is protocol-defined. The runtime must not infer writability merely from the fact that an object contains bytes or represents text.


### Exact standard String indexing runtime semantics

Standard String size and indexing use Unicode 17.0.0 default extended grapheme
clusters as defined by UAX #29 revision 47.

Conceptually:

```text
function standardStringGraphemeBoundaries(receiver):
    text = requireSemanticFamilyReceiver(
        receiver,
        isSemanticStringValue
    )

    return unicode17DefaultExtendedGraphemeBoundaries(
        semanticUnicodeScalarSequence(text)
    )

function standardStringSize(receiver):
    boundaries = standardStringGraphemeBoundaries(receiver)

    return semanticIntegerFromMathematicalValue(
        numberOfGraphemeIntervals(boundaries)
    )

function standardStringAt(receiver, index):
    text = requireSemanticFamilyReceiver(
        receiver,
        isSemanticStringValue
    )

    if not isSemanticIntegerValue(index):
        signal an Error for invalid String index

    i = mathematicalIntegerValue(index)

    boundaries = unicode17DefaultExtendedGraphemeBoundaries(
        semanticUnicodeScalarSequence(text)
    )

    if i < 0 or i >= numberOfGraphemeIntervals(boundaries):
        signal an Error for String index out of bounds

    scalars = exactScalarSubsequenceForInterval(
        semanticUnicodeScalarSequence(text),
        boundaries[i]
    )

    return semanticStringFromExactScalarSequence(scalars)
```

The Unicode segmentation operation is semantic runtime machinery, not a user
message. It performs no normalization, case folding, locale lookup, text
replacement, encoding conversion, or host-dependent tailoring.

`semanticStringFromExactScalarSequence` constructs the same semantic String
value category already defined by Core. It must preserve the selected scalar
sequence exactly. Interning, ropes, slices, shared backing storage, compact
encodings, or other representations are permitted when they do not change that
sequence or the value-identity result.

The conceptual boundary representation is not observable and need not be
allocated eagerly. An implementation may cache grapheme boundaries, maintain
indexes, specialize ASCII, or compute boundaries lazily. Cached data must be
semantically equivalent to Unicode 17.0.0 UAX #29 revision 47 and must not
silently change when the host Unicode/ICU tables are upgraded.

String-size results are exact semantic Integers. Internal host-sized counters or
indexes may be used only after the implementation has preserved the full
mathematical result and all observable range checks; overflow, saturation, or
wrapping is not a conforming substitute.

Standard String provides no in-place indexed mutation primitive. Generic
indexed assignment still lowers to ordinary `atPut`; if ordinary lookup does
not provide a user-defined applicable behavior, the operation fails through the
normal protocol/lookup rules rather than mutating String storage.

### Standard Bytes indexed-state semantics

### Standard Bytes size runtime semantics

### Complete standard Bytes sequence runtime semantics

The resizable standard Bytes operations are conceptually:

```text
function standardBytesFactoryInvoke(invocationReceiver, arguments):
    requireArgumentCount(arguments, 0)

    return newStandardBytes(
        parent = invocationReceiver,
        state = open,
        octets = empty
    )

function requireOpenBytesForResize(receiver):
    bytes = requireBytesReceiver(receiver)

    if objectState(bytes) != open:
        signal Error

    return bytes

function requireOctetValue(value):
    if not isSemanticInteger(value):
        signal Error

    if value < 0 or value > 255:
        signal Error

    return value

function standardBytesAdd(receiver, value):
    bytes = requireOpenBytesForResize(receiver)
    octet = requireOctetValue(value)

    appendOctet(bytes, octet)
    return value

function standardBytesRemoveAt(receiver, index):
    bytes = requireOpenBytesForResize(receiver)
    i = requireBytesIndex(bytes, index)

    removed = octetAt(bytes, i)
    removeOctetAndShiftLeft(bytes, i)
    return removed
```

`appendOctet` increases the logical octet length by exactly one.
`removeOctetAndShiftLeft` decreases it by exactly one and preserves the relative
order of all surviving octets. Neither operation exposes backing capacity,
native byte representation, or host array mechanics.

The existing `standardBytesAtPut` remains replacement-only and never calls
either resizing primitive.

Standard Bytes iteration is conceptually:

```text
function standardBytesEach(receiver, block):
    bytes = requireBytesReceiver(receiver)
    requireInvokable(block)

    snapshot = snapshotBytesOctets(bytes)

    for octet in snapshot from lowest to highest original index:
        invoke(block, [octet])

    return receiver
```

`requireInvokable` is the same ordinary polymorphic-callability check used by
standard Array and Map iteration. It runs before snapshot capture and invokes no
callback.

`snapshotBytesOctets` captures the logical octet values present at that point.
Subsequent `atPut`, `add`, or `removeAt` does not rewrite the captured sequence.
An implementation may avoid a physical copy when it preserves exactly that
observable snapshot behavior.

The standard factory and resizable operations are ordinary protocol
specializations; they do not grant byte-sequence state to the invocation
receiver itself. `newStandardBytes` creates fresh receiver-owned Bytes state and
fresh identity.

Closed Bytes permit existing-index replacement through the existing
`standardBytesAtPut` contract but fail `add` and `removeAt`; frozen Bytes fail all
standard mutation. Read-only `size`, `at`, and `each` remain permitted.



Standard Bytes size is conceptually:

```text
function standardBytesSize(receiver):
    bytes = requireBytesReceiver(receiver)

    return semanticIntegerFromMathematicalValue(
        bytesOctetLength(bytes)
    )
```

`bytesOctetLength` returns the exact mathematical number of octets in the
receiver-owned standard Bytes state. It is the same length that determines the
valid index range for `standardBytesAt` and `standardBytesAtPut`.

The operation performs no octet decoding, user-message dispatch, callback,
allocation visible to Protos, or mutation. Internal host-sized indexes or
lengths are permitted only when they preserve the exact semantic Integer result
for every standard Bytes value an implementation exposes.

Backing-storage capacity, sparse or segmented representation, native buffer
capacity, signed-byte representation, endianness, and host allocation strategy
are not observable through `Bytes.size`.



Standard Bytes primitives operate on receiver-owned finite dense octet state.

Conceptually:

```text
function requireBytesReceiver(receiver):
    if not ownsStandardBytesState(receiver):
        signal an Error for incompatible standard Bytes receiver

    return receiver

function requireBytesIndex(bytes, index):
    if not isSemanticIntegerValue(index):
        signal an Error for invalid Bytes index

    i = mathematicalIntegerValue(index)

    if i < 0 or i >= bytesLength(bytes):
        signal an Error for Bytes index out of bounds

    return i

function requireOctetValue(value):
    if not isSemanticIntegerValue(value):
        signal an Error for invalid byte value

    n = mathematicalIntegerValue(value)

    if n < 0 or n > 255:
        signal an Error for byte value out of range

    return n
```

These are semantic checks, not user-message sends. They perform no conversion,
parsing, equality, hashing, text decoding, or host-integer coercion.

The standard read is equivalent to:

```text
function standardBytesAt(receiver, index):
    bytes = requireBytesReceiver(receiver)
    i = requireBytesIndex(bytes, index)

    return semanticIntegerFromMathematicalValue(
        byteAt(bytes, i)
    )
```

`byteAt` yields the stored octet's mathematical value in `0 .. 255`.
`semanticIntegerFromMathematicalValue` does not require a particular fixed-width
Integer family and must not expose host byte signedness.

The standard update is equivalent to:

```text
function standardBytesAtPut(receiver, index, value):
    bytes = requireBytesReceiver(receiver)

    if bytes.state == frozen:
        signal FrozenObject(bytes)

    i = requireBytesIndex(bytes, index)
    octet = requireOctetValue(value)

    setByteAt(bytes, i, octet)
    return value
```

`setByteAt` replaces one existing octet only. It does not append, resize, create
holes, shift bytes, mutate ordinary local slots, or reinterpret the octet as
text.

The frozen-state check precedes index and byte-value validation because no
successful byte mutation is possible on a frozen receiver. Receiver and
argument expressions have already been evaluated under ordinary call
evaluation; their prior effects are not rolled back.

A closed Bytes object permits replacement because indexed length and structure
do not change. A frozen Bytes object does not. Read-only access is unaffected by
open/closed/frozen state.

Implementations may store bytes in signed host byte types, unsigned host byte
types, packed words, native buffers, segmented storage, copy-on-write storage,
or other forms. Host signedness, alignment, endian order of wider machine words,
capacity, and storage representation must not change the observable octet value
or index semantics.

This rule adds no resizing or construction surface. Standard Bytes equality and
hashing remain the ordinary identity-based defaults unless user code explicitly
overrides those ordinary messages.

## Map and Hash Runtime Semantics

Normal `Map` lookup uses the key protocol pair `hash` and `==`.

Map does not define a separate equality-result convention. It relies on the language-wide `==` contract: canonical `true` or `false`, or an error.

Implementations may use hash tables, inline caches, specialized key representations, or other internal structures, provided observable semantics follow the language-level equality/hash contract.

The required invariant for correctly behaving keys is:

```text
a == b  =>  a.hash == b.hash
```

Stable key behavior is a programmer-facing correctness contract, but violation of
that contract does not make normal `Map` behavior implementation-defined.

Each entry's `recordedHash` is fixed when the entry is first inserted and remains
associated with that entry until the entry is removed. Updating the entry's
value does not replace or recompute it. Mutating the stored key or any state used
by its `hash` or `==` behavior does not trigger implicit rehashing, relocation,
repair, or key replacement.

Consequently all searches, including searches performed with the stored key
object itself, continue to execute the same `findMapEntry` semantics:

```text
current query hash
        ↓
entries whose recordedHash equals that hash
        ↓
insertion-order queryKey == entry.key comparisons
        ↓
first true comparison wins
```

A key whose current hash differs from its recorded insertion hash will not be a
candidate in a search whose query hash no longer equals that recorded hash.
Two entries that become equal after insertion may coexist. If both are
candidates for a later query, insertion order determines which one is found. If
their recorded hashes differ, only entries matching the query's current hash are
candidates, irrespective of equality that would have been observed had the
other entry been compared.

Likewise, if user-defined equality and hashing violate
`a == b => a.hash == b.hash`, no repair or alternate equality pass is performed.
The deterministic search algorithm remains authoritative.

Implementations are not required to detect mutation or protocol instability and
must not add hidden key freezing or mutation hooks merely to maintain a hash
table. Optional diagnostics may observe and report misuse in debugging modes,
but ordinary execution must remain semantically equivalent to the rules above.

These rules confine malformed key protocols without importing Rust-style
unspecified logic-error behavior or Java-style unspecified mutable-key behavior:
no host/runtime memory corruption is permitted, and the implementation may not
substitute arbitrary failure, abort, nontermination, or implementation-specific
lookup results for the specified abstract algorithm.

### Hash result validation

Normal `Map` semantics use a validated semantic Integer hash value.

Conceptually:

```text
function requireHashResult(value):
    if not isSemanticIntegerValue(value):
        signal InvalidHashResult(value)

    return mathematicalIntegerValue(value)
```

`isSemanticIntegerValue` uses the language's semantic Integer classification,
not delegation. An ordinary object whose parent is an Integer value is therefore
not accepted merely because lookup through that parent finds Integer behavior.

`mathematicalIntegerValue` denotes the exact mathematical Integer represented by
the semantic Integer value. The implementation may keep the original Integer
object, a normalized internal integer, or another equivalent representation.
It must not expose host word size, signed overflow, truncation, masking, or
modulo reduction through Map behavior.

The conceptual `recordedHash` stored with a Map entry is this validated exact
Integer value. Internal bucket selection may derive an implementation-private
bounded hash/index from it, but collisions in that internal reduction do not
change which entries are logical hash candidates: logical candidacy remains
equality of the validated mathematical Integer hash values defined by the
deterministic Map search algorithm.

If validation fails, the consuming Map operation signals before mutating the Map.
Side effects performed by evaluating the key's `hash` message are not rolled
back.

`identityHash` has the same result-domain validation:

```text
function requireIdentityHashResult(value):
    if not isSemanticIntegerValue(value):
        signal InvalidIdentityHashResult(value)

    return mathematicalIntegerValue(value)
```

For the standard semantic identity-hash operation:

```text
a === b  =>  identityHash(a) == identityHash(b)
```

An identity-bearing object's standard identity hash is stable for its lifetime
during one Protos execution. Value-identity objects derive identity-hash
behavior from their semantic identity rather than from transient
boxing/allocation identity. Collisions remain permitted.

The observable identity-hash domain is the Protos execution rather than an
operating-system process, worker, thread, Actor placement, or machine. For a
Core value-identity category, evaluating `identityHashOf` for the same semantic
identity at different host placements within the same Protos execution must
produce the same semantic Integer. Separate Protos executions need not produce
the same Integer.

No global mutable identity-hash registry or global lock is implied. Immutable
execution-scoped configuration may determine value-identity hashes, while
identity-bearing objects may use local cached/assigned hashes when their
identity does not cross the relevant isolation boundary. If pass-by-value
transfer creates a distinct identity-bearing destination object, the source and
destination are different semantic identities and no identity-hash equality is
required between them.

The internal error names above are pseudocode notation unless another normative
specification explicitly defines them as standard error prototypes; the
normative requirement is that an invalid hash result signals an `Error`.

### Primitive semantic identity hashing

The runtime operation used by identity-sensitive machinery is:

```text
function identityHashOf(value):
    return semanticIdentityHash(value)
```

`semanticIdentityHash` is a primitive semantic operation. It performs no Protos
slot lookup, method binding, `send`, user closure invocation, or fallback to a
slot named `identityHash`.

Its result must satisfy the existing semantic-Integer result, stability,
collision, and `===` coherence rules.

If the standard prelude provides an ordinary method:

```text
Object.identityHash()
```

its standard behavior is conceptually:

```text
return identityHashOf(this)
```

An explicit source-level send such as `x.identityHash()` uses ordinary message
dispatch and can therefore observe a user override. That override affects only
that explicit message behavior.

Identity-keyed collection machinery must instead behave conceptually as:

```text
queryIdentityHash = identityHashOf(queryKey)
```

and compare candidate keys with primitive `===`. It must not perform:

```text
send(queryKey, "identityHash", [])
```

nor any observationally equivalent overridable dispatch.

Implementations may cache or lazily assign internal identity-hash data, provided
that cache state is not Protos-visible and does not change the required semantic
result.

### Default Object equality and hashing

The standard behavior inherited from `Object` is conceptually:

```text
Object.==(other):
    return this === other

Object.hash():
    return identityHashOf(this)
```

These are ordinary message-level behaviors backed by primitive semantic
operations. Ordinary lookup and overriding rules apply to the message slots
themselves.

The `===` operation in the default equality implementation is primitive semantic
identity and performs no overridable equality dispatch. The
`identityHashOf(this)` operation in the default hash implementation is the
primitive non-overridable identity-hash operation defined above.

An override of `==` or `hash` affects ordinary sends of that message and
therefore normal `Map` behavior as specified. It does not mutate or redefine the
primitive `===` or `identityHashOf` operations and therefore cannot alter
`IdentityMap` identity semantics.

For a receiver that inherits both defaults:

```text
send(a, "==", [b]) == true
```

if and only if:

```text
a === b
```

and equal default-`==` receivers necessarily obtain equal default hashes.

No enumeration of local slots, traversal of delegation parents, deep graph
comparison, or structural hashing is part of these defaults.


### Inequality evaluation

The standard default inequality behavior inherited from `Object` is
conceptually:

```text
Object.!=(other):
    result = send(this, "==", [other])
    result = requireBooleanEqualityResult(result)

    if result === true:
        return false

    return true
```

The `==` send is ordinary dynamic message dispatch. Therefore a receiver that
overrides `==` but inherits `Object.!=` gets the complement of that override.
Any error signaled by the `==` send propagates. A non-Boolean normal return from
`==` signals the existing invalid-equality-result error before `!=` returns.

An object may override the `!=` message itself; an explicit source-level
`a != b` uses that ordinary message behavior and validates its result under the
same equality Boolean-result contract.

Semantic identity inequality is primitive:

```text
function notIdentical(a, b):
    if identical(a, b):
        return false

    return true
```

Evaluation of `a !== b` evaluates `a` before `b`, then invokes this primitive
semantic operation. `notIdentical` performs no Protos message lookup or send and
cannot be overridden. It is exactly the Boolean complement of `identical`,
including all Core value-identity rules such as numeric-family identity, String
value identity, canonical Booleans, `null`, Float NaN identity, and signed-zero
identity.

### Standard Map receiver validation

Standard keyed collection primitives validate that the original receiver owns
the keyed-entry state required by the selected standard behavior.

Conceptually:

### Standard Map factory invocation

### Standard Map size runtime semantics

Standard Map size is conceptually:

```text
function standardNormalMapSize(receiver):
    map = requireNormalMapReceiver(receiver)

    return semanticIntegerFromMathematicalValue(
        storedAssociationCount(map)
    )

function standardIdentityMapSize(receiver):
    map = requireIdentityMapReceiver(receiver)

    return semanticIntegerFromMathematicalValue(
        storedAssociationCount(map)
    )
```

`storedAssociationCount` returns the exact mathematical number of associations
present in the receiver-owned keyed-entry state. It counts entries, not hash
buckets, currently distinct equality classes, backing-array cells, tombstones,
or implementation capacity.

The operation performs no key search. In particular it invokes no `hash`, `==`,
primitive identity hashing, `===`, callback, or iteration machinery. Mutable key
state therefore cannot trigger reclassification, rehashing, deduplication, or
repair merely because `size` is observed.

Implementations may maintain the count incrementally or derive it from internal
state, provided the exact semantic result matches the current stored
association count at the operation's evaluation point.



`Map` and `IdentityMap` provide ordinary invocation-protocol specializations
that create fresh empty keyed state rather than using `Object`'s default
child-plus-`init` construction path.

Conceptually:

```text
function standardMapFactoryInvoke(invocationReceiver, arguments):
    requireArgumentCount(arguments, 0)

    return newStandardMap(
        parent = invocationReceiver,
        state = open,
        entries = empty
    )

function standardIdentityMapFactoryInvoke(invocationReceiver, arguments):
    requireArgumentCount(arguments, 0)

    return newStandardIdentityMap(
        parent = invocationReceiver,
        state = open,
        entries = empty
    )
```

`arguments` is the already-evaluated outgoing positional argument vector.
Therefore argument-count failure occurs after ordinary argument evaluation but
before allocation of the new standard Map object.

`newStandardMap` and `newStandardIdentityMap` each create a fresh identity and
fresh receiver-owned keyed-entry state. Their `parent` parameter fixes the new
object's delegation parent; it neither copies keyed state from that parent nor
requires the parent itself to own keyed state.

The standard prelude `Map` and `IdentityMap` objects expose the corresponding
factory behavior through ordinary polymorphic invocation. If another object
inherits one of those factory behaviors through delegation, invocation uses the
actual invocation receiver as the new object's parent while preserving the
factory's map kind.

The factory performs no `init` send and no insertion operation. It therefore
performs no key `hash`, `==`, primitive identity hashing, `===` comparison,
iteration snapshot, callback, or keyed-state mutation beyond establishing the
new empty receiver-owned state itself.

Implementations may allocate empty table storage eagerly, lazily, compactly, or
through another representation, provided fresh object identity, exact map kind,
empty insertion order, open state, delegation parent, and all subsequent
standard keyed semantics are observationally identical.

```text
function requireNormalMapReceiver(receiver):
    if not ownsStandardNormalMapState(receiver):
        signal an Error for incompatible standard Map receiver

    return receiver

function requireIdentityMapReceiver(receiver):
    if not ownsStandardIdentityMapState(receiver):
        signal an Error for incompatible standard IdentityMap receiver

    return receiver
```

The predicates above are semantic receiver-state classifiers, not Protos message
sends and not delegation tests. An object does not satisfy them merely because
its parent is a Map, because lookup found a method on a Map-related ancestor, or
because a standard method closure was copied onto it.

A standard Map operation conceptually validates its receiver before any
keyed-entry-specific work. For example:

```text
function standardMapAt(receiver, key):
    map = requireNormalMapReceiver(receiver)
    return mapAt(map, key)

function standardIdentityMapAt(receiver, key):
    map = requireIdentityMapReceiver(receiver)
    return identityMapAt(map, key)
```

The same boundary applies to standard `atPut`, `containsKey`, `remove`, `each`,
and other standard keyed-state behavior. Where one standard behavior is
explicitly specified as generic over both Map kinds, it may validate the
corresponding union of receiver domains instead of one kind.

Receiver validation occurs after ordinary evaluation and lookup but before
hashing, equality callbacks, identity hashing for key search, keyed-state
permission checks, iteration snapshot capture, or keyed-entry mutation.
Therefore an incompatible receiver cannot trigger those standard Map effects.

Implementations may encode the required keyed-entry state using object layout,
side tables, specialized representations, capabilities, or other internal
mechanisms. They must not make ancestor storage become the receiver's storage,
must not lazily manufacture standard Map state merely because an inherited
standard method was invoked, and must not expose the physical representation as
a second observable notion of collection membership.

### Map key-state visibility during search

Map lookup fixes the candidate sequence and lookup hash state without cloning
or freezing mutable key objects.

Conceptually:

```text
candidates = candidate sequence fixed for this lookup
queryHash = the single hash result obtained for the query key
storedHash = the recorded hash associated with each stored candidate
```

User code executed by equality comparison may mutate the query key, a stored
key, or other reachable state. Such mutations are not rolled back or hidden.
A later candidate comparison observes the current state at that point.

The runtime must not:

- recompute a stored candidate's recorded hash because its object state changed;
- recompute the query hash after equality code mutates the query key;
- restart or reorder the candidate sequence because a key was mutated;
- create a semantic snapshot of mutable key objects for the lookup.

Identity lookup performs no user equality callback, but the same fixed
candidate-order and no-key-snapshot rules apply.

This permits arbitrary internal lookup structures while keeping search-control
state fixed and mutable Protos object state live.

### Default equality and hashing when Core defines no specialization

For every Core object whose standard behavior is not given an explicit
specialized equality/hash rule, the runtime uses the ordinary `Object`-level
defaults.

Conceptually:

```text
function standardDefaultEquals(receiver, other):
    return receiver === other

function standardDefaultHash(receiver):
    return identityHashOf(receiver)
```

This rule applies to both identity-bearing and value-identity Core objects.
`===` and `identityHashOf` already incorporate the semantic identity rules of
the receiver's value category, so no additional structural/content algorithm is
implied for String, Boolean, `null`, or any identity-bearing built-in object.

An implementation must not select structural, element-wise, byte-wise,
case-folded, locale-sensitive, state-derived, or host-conventional standard
equality/hashing merely because of the receiver's built-in family or internal
representation.

No entry/element/slot traversal, recursive comparison, cycle detector,
serialization, content hash, locale lookup, or mutation-sensitive recomputation
is introduced unless another normative rule explicitly requires it.

This is a semantic default, not a requirement to install distinct method bodies
on every standard prototype. Implementations may inherit, inline, or specialize
the ordinary `Object` behavior internally when the observable result is the
same.

Explicit normative specializations take precedence. Standard Number equality
and hashing remain governed by the existing numeric rules, including
cross-family numeric equality and numeric-hash coherence. The explicit
Map/IdentityMap default remains an instance of this general rule.

User-defined ordinary `==` and `hash` behavior remains normal Protos message
dispatch and may intentionally replace these defaults.

### Standard Map equality and hash dispatch

Standard `Map` and `IdentityMap` do not override the ordinary `Object` equality
and hash semantics with entry traversal.

Conceptually, when no user-defined override shadows the standard behavior:

```text
function standardMapEquals(map, other):
    return map === other

function standardMapHash(map):
    return identityHashOf(map)
```

The same conceptual behavior applies to `IdentityMap`.

These operations inspect no keyed-entry state and invoke no key or value
protocol. In particular, standard Map equality/hash does not call
`findMapEntry`, `findIdentityMapEntry`, `hash`, `==`, `each`, or any equivalent
entry-enumeration operation merely because the receiver is a Map.

Mutation, insertion, removal, mapped-value replacement, `close()`, and
`freeze()` therefore do not alter the standard Map object's equality/hash class.
The ordinary execution-scoped stability guarantee of `identityHashOf` is the
relevant default hash guarantee.

Implementations may inline these inherited defaults or avoid installing
Map-specific method bodies entirely. What is observable is that the standard
Map prototypes add no structural equality/hash behavior. A user-defined
ordinary `==` or `hash` override remains ordinary message dispatch and may
choose different semantics subject to the existing general protocol contracts.

This rule introduces no recursive traversal, cycle detector, snapshot,
collection lock, or callback scope for ordinary Map equality/hash.

### Map keyed-state mutation and object state

Standard `Map` and `IdentityMap` keyed-entry mutation observes the receiver's
ordinary `open` / `closed` / `frozen` state.

Conceptually:

```text
function requireMapMayAttemptAtPut(map):
    if map.state == frozen:
        signal FrozenObject(map)

function requireMapMayInsert(map):
    if map.state == frozen:
        signal FrozenObject(map)

    if map.state == closed:
        signal ClosedObject(map)

function requireMapMayRemove(map):
    if map.state == frozen:
        signal FrozenObject(map)

    if map.state == closed:
        signal ClosedObject(map)
```

Normal Map insertion/update is therefore equivalent to:

```text
function mapAtPut(map, queryKey, value):
    requireMapMayAttemptAtPut(map)

    queryHash = requireHashResult(
        send(queryKey, "hash", [])
    )

    entry = findMapEntryUsingKnownQueryHash(
        map,
        queryKey,
        queryHash
    )

    if entry != NOT_FOUND:
        requireMapMayAttemptAtPut(map)
        entry.value = value
        return value
    requireMapMayInsert(map)

    appendEntry(
        map,
        key = queryKey,
        value = value,
        recordedHash = queryHash
    )

    return value
```

A frozen Map therefore fails before user-defined key-search protocol executes.
A closed Map must search, because update of an existing entry remains permitted;
only the no-match insertion path fails.

`IdentityMap.atPut` follows the same state ordering but uses primitive
`identityHashOf` / semantic identity search instead of user callbacks.

A standard keyed-entry removal performs `requireMapMayRemove(map)` before key
search and, if a matching entry is to be removed, performs the same check again
immediately before removal. Therefore a Map that is already closed/frozen fails
before key search, while a Map that becomes closed/frozen during callback-capable
search cannot bypass the later state transition. Open-state removal otherwise
uses the already specified deterministic normal-Map or IdentityMap search.

Read-only keyed operations do not consult these mutation guards merely because
the receiver is closed or frozen.

The state applies only to the receiver's own keyed-entry structure and values.
No recursive close/freeze of keys or values occurs. Implementations may encode
Map state and entry storage differently provided these failure points and
observable protocol calls are preserved.

### Standard keyed removal operation

Standard normal-Map keyed removal is conceptually:

```text
function mapRemove(map, key):
    requireMapMayRemove(map)

    entry = findMapEntry(map, key)

    if entry == NOT_FOUND:
        signal an Error for missing Map key

    requireMapMayRemove(map)

    value = entry.value
    remove entry from map insertion order
    return value
```

Standard IdentityMap keyed removal is conceptually:

```text
function identityMapRemove(map, key):
    requireMapMayRemove(map)

    entry = findIdentityMapEntry(map, key)

    if entry == NOT_FOUND:
        signal an Error for missing IdentityMap key

    requireMapMayRemove(map)

    value = entry.value
    remove entry from map insertion order
    return value
```

The first state check preserves the existing rule that an already closed or
frozen Map fails before key search. The second check is a fresh observation at
the semantic mutation point and prevents callback-capable normal-Map search from
bypassing a `close()` or `freeze()` performed by `hash` or `==`.

A successful call returns the exact value object stored in the removed entry
immediately before removal. The entry's key, recorded hash, and insertion-order
position cease to belong to the Map once removal commits. A later insertion of
the same semantic key is a new insertion at the end, as already specified.

If search completes with `NOT_FOUND`, the operation signals an `Error`; the
internal `NOT_FOUND` marker never escapes as a Protos value. No ordinary
storable object, including `null` or `false`, is an absence result.

Normal-Map key-search effects remain ordinary effects and are not rolled back.
IdentityMap search remains callback-free. No snapshot, transaction, or hidden
retry is introduced.

### Revalidation of Map state after callback-capable search

Map object-state permission is not a capability captured by an earlier check.
For any keyed-entry mutation, the receiver must satisfy the required state at
the semantic mutation point.

Normal `Map` search may execute Protos code through `hash` and `==`. That code
may change the Map from `open` to `closed` or `frozen` without directly changing
keyed-entry state. Consequently an implementation must not hoist the initial
state check across callback-capable search as though the result remained valid.

For standard `Map.atPut`, the observable ordering is equivalent to:

```text
requireMapMayAttemptAtPut(map)

queryHash = requireHashResult(
    send(queryKey, "hash", [])
)

entry = findMapEntryUsingKnownQueryHash(
    map,
    queryKey,
    queryHash
)

if entry != NOT_FOUND:
    requireMapMayAttemptAtPut(map)
    entry.value = value
    return value

requireMapMayInsert(map)
append the new entry
return value
```

The second `requireMapMayAttemptAtPut` is a fresh state observation. A Map that
became frozen during `hash` or `==` therefore cannot be updated merely because it
was not frozen when `atPut` began. A Map that became closed may still replace an
existing entry but cannot take the insertion path.

For keyed-entry removal, the semantic ordering is equivalently:

```text
requireMapMayRemove(map)
entry = deterministic key search

if entry is to be removed:
    requireMapMayRemove(map)
    remove entry
```

The first check preserves immediate failure for an already closed/frozen Map;
the second prevents a callback-induced close/freeze from being bypassed after
search.

These rechecks occur before the Map's own mutation and do not undo effects
already performed by user `hash`/`==` behavior. They also do not add a new
reentrant-mutation scope. Existing comparison guards, hash-phase rules, and
error propagation continue to apply independently.

`IdentityMap` uses primitive callback-free identity search. It follows the same
point-of-mutation state requirement, although a runtime may optimize away a
provably redundant recheck.

### Deterministic `Map` key search

The abstract search operation used by normal `Map` is:

```text
function findMapEntry(map, queryKey):
    queryHash = requireHashResult(
        send(queryKey, "hash", [])
    )

    for entry in map.entriesInInsertionOrder:
        if entry.recordedHash == queryHash:
            equal = requireBooleanEqualityResult(
                send(queryKey, "==", [entry.key])
            )

            if equal === true:
                return entry

    return NOT_FOUND
```

The entry hash is conceptually recorded when that entry is first inserted:

```text
function mapAtPut(map, queryKey, value):
    requireMapMayAttemptAtPut(map)

    queryHash = requireHashResult(
        send(queryKey, "hash", [])
    )

    entry = findMapEntryUsingKnownQueryHash(
        map,
        queryKey,
        queryHash
    )

    if entry != NOT_FOUND:
        requireMapMayAttemptAtPut(map)
        entry.value = value
        return value
    requireMapMayInsert(map)

    appendEntry(
        map,
        key = queryKey,
        value = value,
        recordedHash = queryHash
    )

    return value

`findMapEntryUsingKnownQueryHash` performs exactly the insertion-order
candidate comparison described by `findMapEntry` without sending `hash` to the
query a second time.

The pseudocode is an observable semantic model, not a required table layout.
An implementation may use ordinary hash buckets, open addressing, trees,
specialized representations, cached protocol dispatch, or another strategy only
when user code observes the same `hash` and `==` calls, in the same required
comparison order, with the same first matching entry and failure behavior.

The query key is always the receiver of `==`; the stored key is the argument.
The runtime must not reverse that send, invoke both directions, or substitute
`===` as a shortcut. This matters for user-defined equality whose behavior or
side effects differ by receiver.

A mutating `Map` operation performs no map mutation until its key search
completes successfully. If the query `hash` or a required equality comparison
signals, that error propagates and the map remains unchanged by that operation.
Effects performed by the user-defined protocol code itself are ordinary effects
and are not rolled back.

Updating an existing mapping replaces only `entry.value`. `entry.key`,
`entry.recordedHash`, and the entry's insertion position remain unchanged.
Consequently two distinct objects that compare equal do not cause the later key
object to replace the representative key already stored in the map.

The existing unstable-key rule remains in force. If a stored key changes the
relevant equality/hash behavior while present, subsequent logical map behavior
is outside the correctly-behaving-key guarantee; this section does not require
automatic repair or reindexing and does not permit host/runtime memory
corruption.

`IdentityMap` continues to use semantic identity and `identityHashOf` and does not
use `findMapEntry`.

`IdentityMap` uses primitive semantic identity (`===`) together with a stable `identityHashOf`.

`hash` values need only be valid within the current Protos execution. Standard
built-in hash behavior may be salted per Protos execution, but must not expose
operating-system process, worker, thread, Actor-placement, or machine boundaries
as different hash domains inside that execution. Implementations remain free to
apply additional local random mixing to physical Map indexing when it is
unobservable and preserves the logical exact-Integer matching contract.
Persisted hash values must therefore not rely on the ordinary `hash` protocol.

Map iteration preserves insertion order as an observable collection property.


### Standard Map iteration callback validation

Standard `Map.each(block)` and `IdentityMap.each(block)` use the same
`requireInvokable` callability test as standard `Array.each`.

Conceptually, before association-snapshot establishment:

```text
function beginStandardMapEach(receiver, block, identityMode):
    if identityMode:
        map = requireIdentityMapReceiver(receiver)
    else:
        map = requireNormalMapReceiver(receiver)

    requireInvokable(block)
    snapshot = snapshotMapAssociations(map)

    return iterateMapSnapshot(snapshot, block)
```

`requireInvokable` is the ordinary polymorphic-callability check defined for
Array iteration. It does not invoke `block` and does not create a Map-specific
callback category.

For each snapshot association, callback execution is equivalent to:

```text
invoke(block, [snapshotKey, snapshotValue])
```

using the ordinary polymorphic invocation machinery. No Closure-only fast path
may change observable callback eligibility.

The callability check occurs after standard receiver validation but before
snapshot establishment. If it fails, no association snapshot is established
and no callback is invoked. It performs no key `hash`, key `==`,
`identityHashOf`, keyed-entry mutation, or other Map search behavior.

The check establishes only that the value is invokable. Argument-count and
other invocation errors are determined by the actual two-argument invocation
and propagate through the existing Map iteration failure semantics.

### Stable Map iteration snapshot

Standard `Map.each(block)` and `IdentityMap.each(block)` iterate a shallow
logical association snapshot established once at invocation start.

Conceptually:

```text
function captureMapIterationSnapshot(map):
    snapshot = []

    for entry in map.entriesInInsertionOrder:
        snapshot.append(
            key = entry.key,
            value = entry.value
        )

    return snapshot

function mapEach(map, block):
    snapshot = captureMapIterationSnapshot(map)

    for item in snapshot:
        invoke(block, [item.key, item.value])

    return map
```

`identityMapEach` uses the same algorithm; capturing the snapshot does not call
`hash`, `==`, `identityHashOf`, or any other key-search operation.

The pseudocode's `snapshot` and `append` are semantic notation only. A runtime
may avoid an eager O(n) copy when another implementation strategy preserves the
same observable association snapshot.

The snapshot fixes, for the lifetime of that `each` invocation:

- which associations will be visited;
- their visitation order;
- the representative key object passed for each visit; and
- the mapped value object passed for each visit.

Subsequent insertion, removal, or mapped-value replacement on the source Map
does not revise those four snapshot facts. The snapshot is shallow: mutations
inside a referenced key or value object remain ordinary object mutations.

No dynamic Map iteration lock or mutation guard is established. In particular,
an `each` callback may reach an explicit suspension point; another runnable task
in the same Actor may then mutate the Map according to the ordinary Map and
Actor rules without being blocked merely because an iteration is suspended.
When the iterating task resumes, it continues with its already-established
snapshot.

Each nested `each` invocation captures its own snapshot at its own invocation
start. A callback failure or non-local transfer stops the current iteration;
remaining snapshot elements are not invoked, and completed effects are not
rolled back.

Implementations may use persistent entry nodes, immutable snapshot descriptors,
copy-on-write structures, versioned representations, or other mechanisms. They
must not expose physical hash-table traversal, host iterator invalidation,
best-effort concurrent-modification detection, or scheduler timing as a
different iteration result.

### Deterministic `IdentityMap` key search and update

Identity-keyed lookup is conceptually:

```text
function findIdentityMapEntry(map, queryKey):
    queryIdentityHash = requireIdentityHashResult(identityHashOf(queryKey))

    for entry in map.entriesInInsertionOrder:
        if entry.recordedIdentityHash == queryIdentityHash:
            if semanticIdentity(queryKey, entry.key):
                return entry

    return NOT_FOUND
```

`semanticIdentity(a, b)` is the primitive operation implementing `a === b`.
Neither it nor `identityHashOf` performs user-message dispatch.

For a new `IdentityMap` entry, the logical `recordedIdentityHash` is the exact
semantic Integer result used by the search contract. Physical implementations
may use reduced hashes, buckets, cached identity hashes, or other internal
representations provided that collisions are resolved according to primitive
semantic identity and the observable result is equivalent to the algorithm
above.

Indexed insertion/update behaves conceptually as:

```text
function identityMapAtPut(map, key, value):
    requireMapMayAttemptAtPut(map)

    entry = findIdentityMapEntry(map, key)

    if entry != NOT_FOUND:
        requireMapMayAttemptAtPut(map)
        entry.value = value
        return value
    requireMapMayInsert(map)

    identityHash = requireIdentityHashResult(identityHashOf(key))
    append entry(key, value, identityHash) to map insertion order
    return value

An implementation need not literally compute `identityHashOf(key)` twice on the
no-match path: it may retain the already validated query identity hash from the
search and store that exact mathematical Integer as the entry's logical
recorded identity hash.

Updating an existing entry does not replace its key and does not move it in
insertion order. Removing an entry removes its insertion-order position; if the
same semantic key is inserted later, the new entry is appended at the end.

All standard `IdentityMap` operations that find a key use
`findIdentityMapEntry` semantics. The primitive operations involved execute no
ordinary Protos callbacks, so the normal `Map` equality-callback reentrancy
mechanism is neither needed nor invoked for `IdentityMap` key matching.

### Standard Map lookup and presence results

After the existing deterministic key-search operation completes, standard normal
Map lookup is conceptually:

```text
function mapAt(map, key):
    entry = findMapEntry(map, key)

    if entry == NOT_FOUND:
        signal an Error for missing Map key

    return entry.value
```

and presence testing is:

```text
function mapContainsKey(map, key):
    entry = findMapEntry(map, key)

    if entry == NOT_FOUND:
        return false

    return true
```

For `IdentityMap`, the corresponding operations use
`findIdentityMapEntry(map, key)` and have the same result rules:

```text
function identityMapAt(map, key):
    entry = findIdentityMapEntry(map, key)

    if entry == NOT_FOUND:
        signal an Error for missing IdentityMap key

    return entry.value

function identityMapContainsKey(map, key):
    entry = findIdentityMapEntry(map, key)

    if entry == NOT_FOUND:
        return false

    return true
```

`NOT_FOUND` in this pseudocode is implementation notation for control flow and is
not a Protos object or language-level value. It must never escape from these
operations. In particular, neither `null` nor another storable object is used as
an absence sentinel.

The lookup and presence operations perform no collection mutation of their own.
Any effects or errors caused by the underlying normal-Map `hash`/`==` protocol
remain governed by the existing deterministic search and reentrancy rules.
`IdentityMap` search remains callback-free as already specified.

### Reentrant Map mutation during equality callbacks

The deterministic Map search has a protected comparison phase for the Map whose
entries are being searched.

Conceptually:

```text
function compareMapCandidate(map, queryKey, storedKey):
    enterMapComparison(map)

    try:
        result = send(queryKey, "==", [storedKey])

        if result !== true and result !== false:
            signal InvalidEqualityResult(result)

        return result
    finally:
        leaveMapComparison(map)
```

`enterMapComparison(map)` is dynamically nestable. While its depth for `map` is
greater than zero, every primitive operation that would mutate `map`'s keyed
entry state must fail before mutation:

```text
function requireMapEntryMutationAllowed(map):
    if mapComparisonDepth(map) > 0:
        signal ReentrantMapMutation(map)
```

`ReentrantMapMutation` is pseudocode notation unless another normative
specification explicitly defines that name as a standard error prototype; the
normative requirement is that an `Error` is signaled before the attempted
Map-entry mutation.

The check applies to adding an entry, removing an entry, clearing entries,
replacing an entry value, changing a recorded hash, or changing insertion-order
state. Implementations must perform the check before any such mutation.

The check is per Map, not Actor-global. It does not prohibit read-only searches
of the same Map or any operation on another Map. Nested read-only searches may
therefore create nested comparison scopes for the same Map.

`findMapEntry` computes and validates the query hash before entering any
candidate comparison:

```text
queryHash = requireHashResult(send(queryKey, "hash", []))
```

No new comparison restriction is entered merely because this hash call belongs
to the current Map operation. In an outermost search, where
`mapComparisonDepth(map) == 0` on entry to the hash call, user-defined `hash`
behavior may therefore mutate the target Map according to ordinary semantics;
those effects occur before the subsequent candidate traversal and are visible to
it.

An outer comparison scope may already be active, however. If
`mapComparisonDepth(map) > 0` because the current search was invoked from within
an equality callback for that same Map, the hash call does not clear, mask, or
suspend that scope. Any keyed-entry mutation attempted by the hash behavior is
checked by `requireMapEntryMutationAllowed(map)` and signals the ordinary
reentrant-mutation Error before mutation. Nested searches therefore cannot use
their pre-comparison hash phase to bypass an enclosing same-Map comparison
restriction.

The candidate traversal then uses `compareMapCandidate`:

```text
for entry in map.entriesInInsertionOrder:
    if entry.recordedHash == queryHash:
        if compareMapCandidate(map, queryKey, entry.key):
            return entry
```

Because keyed-entry mutation of that Map cannot occur during each user equality
callback, the implementation cannot expose live-table iterator invalidation,
rehashing, bucket relocation, or implementation-specific concurrent-modification
behavior through the outer search.

Mutation of the key objects themselves is not intercepted by this mechanism.
The already-defined recorded-hash and current-equality rules remain
authoritative.

## Custom Operator Runtime Note

Custom symbolic operators do not create a separate runtime dispatch mechanism. After lexing and parsing, a custom binary operator is an ordinary message selector.

For example:

```js
a |> b
```

lowers conceptually to an ordinary send whose selector is `"|>"`.

The permitted symbolic character alphabet is a parser/lexer rule and is not mutable at runtime.

## Future Cancellation Runtime Semantics

Future cancellation is cooperative.

Conceptually:

```text
function cancel(future):
    recordFutureCancellationRequest(future)
    return future
```

`Future.cancel()` returns that same Future object. This return value is normative,
not implementation-defined.

Cancellation request is idempotent while the Future remains pending: repeated
calls do not create additional cancellation events or change the identity of the
cancellation target. Calling `cancel()` after the Future is already resolved,
failed, or cancelled does not change its terminal state, value, or error and
still returns that same Future.

A successful method call means only that the cancellation request has been
recorded when the Future was pending; it does not mean that cancellation has
already been observed, that the Future will necessarily end in `cancelled`, or
that effects already committed by the producing operation can be reversed.
Cancellation remains cooperative and follows the producer's normative
cancellation/commitment rules.

Cancellation observation follows the portable Future-cancellation boundaries
defined above. This section does not create a second category of runtime-selected
safe points. In particular, implementation polling, call boundaries, allocations,
loop back-edges, JIT/VM safepoints, garbage-collection points, and host calls must
not make cancellation observable unless they coincide with a portable cancellation
boundary.

When cancellation is honored:

```text
honorCancellation(task)
```

performs the already-defined cancellation unwind: the current asynchronous
activation unwinds, all applicable `ensure` cleanup runs, and its Future completes
as `CANCELLED`.

A later:

```text
future.value()
```

signals the standard cancellation condition/error, conceptually `Cancelled`.

The runtime must not bypass `ensure` merely to accelerate cancellation.

## Future Failure Propagation

An unhandled error in an asynchronous task is captured as the failed completion of its Future rather than transferred asynchronously into the creator's activation.

Conceptually:

```text
try:
    result = invoke(taskClosure, [])
    future.completeSuccess(result)

on unhandled error:
    future.completeFailure(error)
```

When a consumer executes:

```text
future.value()
```

the Future behaves conceptually as:

```text
if SUCCESS:
    return storedValue

if FAILED:
    signal(storedError) in current consumer activation

if CANCELLED:
    signal(Cancelled) in current consumer activation

if PENDING:
    suspend current activation until completion
```

Dynamic handlers active only in the Future creator are not implicitly preserved as the Future task's handler stack. Handlers installed inside the task govern errors while that task executes. Handlers surrounding `future.value()` govern re-signaled stored failures at observation time.

## Future Completion Visibility

Future completion is a synchronization boundary.

All memory effects performed by the Future task before it enters a terminal state are visible to a task after that task successfully observes the terminal state through `future.value()`.

Conceptually:

```text
task effects
    happen-before
Future terminal completion
    happens-before
return/signal from observer's future.value()
```

This guarantee applies to SUCCESS, FAILED, and CANCELLED completion with respect to effects that occurred before terminal completion.

Ordinary Future/task execution is Actor-local and cooperative: only one segment of Actor-local Protos code executes at a time, tasks interleave with other Actor-local work only at explicit suspension points, and between suspension points Actor-local state is serialized. The visibility guarantee above is what lets a suspended task correctly observe the effects of a completed task, including writes the completed task made to the Actor-local mutable state before completion.

Different Actors never share mutable Protos references, so no cross-Actor mutable-state visibility rule exists beyond Actor communication semantics. Standard `Closure.parallel(arguments...)` may execute Protos code simultaneously on other CPU carriers, but it crosses an isolation boundary through a fresh P-local Closure projection rather than carrying the source Closure's caller lexical environment. Its successful submission fixes logical cross-boundary input state before returning to the caller; mutation is confined to P-owned isolated state; failure or cancellation publishes no partial mutable result; and successful results return by value. Parallel execution has no implicit Actor sender identity or ambient Actor/Process/Node/Cluster/I/O authority. Cooperative `future()` tasks created inside one P domain remain serialized against that P-local mutable state; only a nested P boundary permits simultaneous Protos execution relative to it.

### Parallel submission and projection

```text
function standardClosureParallel(sourceClosure, sourceArguments, creatorActivation):
    validateArgumentCount(sourceClosure.parameterForm, length(sourceArguments))

    root = createParallelRootDescriptor(
        lexicalParent = StandardPrelude,
        thisValue = null,
        returnHome = freshReturnHome(),
        methodHome = NONE,
        dynamicHandlers = NONE
    )

    projected = formParallelSnapshot(
        bootstrapClosure = sourceClosure,
        arguments = sourceArguments,
        root = root
    )
    # Complete graph validation is atomic.
    # Invalid graph -> signal NonParallelValue synchronously.
    # No Future/task is published before this succeeds.

    future = new Future(PENDING)

    task = scheduler.createIsolatedParallelTask(
        owner = creatorActivation,
        resultFuture = future,
        parallelRoot = root,
        entryClosure = projected.bootstrapClosure,
        arguments = projected.arguments
    )

    registerStructuredChild(creatorActivation, task, future)
    scheduler.makeParallelEligible(task)
    return future
```

Successful return from `standardClosureParallel` is the P input snapshot point.

Parallel projection of a Closure is conceptually:

```text
function projectClosureForP(sourceClosure, root, snapshotMemo):
    if snapshotMemo contains sourceClosure:
        return snapshotMemo[sourceClosure]

    destination = fresh Closure
    snapshotMemo[sourceClosure] = destination

    destination.executableBody = sourceClosure.executableBody
    destination.parameterForm = sourceClosure.parameterForm
    destination.lexicalContext = root.context
    destination.capturedThis = null
    destination.returnHome = root.returnHome
    destination.methodHome = NONE

    for each user-visible local slot of sourceClosure:
        destination.createLocalSlot(
            slot.name,
            snapshotPValue(slot.value, root, snapshotMemo)
        )

    return destination
```

The omitted capture metadata is caller execution state prohibited from crossing
P; it is not traversed as part of the P value graph.

`formParallelSnapshot` and `snapshotPValue` describe logical snapshots, not
mandatory allocation or copying algorithms. An implementation may physically
share immutable representation, use copy-on-write, remap storage, reuse backing,
or apply another optimization only when every Protos observation remains
equivalent to the required isolated logical values.

No runtime-visible sharing predicate or representation choice is created by this
freedom. If safe physical sharing is unavailable, the implementation must use
another semantics-preserving representation; it must not signal
`NonParallelValue` solely because a sharing optimization cannot be used.

When the P task begins, it first observes the ordinary portable initial
cancellation boundary. If cancelled before ordinary P code starts, no projected
Closure body executes.

Normal completion and a non-local return to the P-local root home both produce a
candidate result that must cross back through the P value rules before resolving
the result Future. A non-transferable result fails that Future with
`NonParallelValue`.

A signaled Error is copied back as the Future failure value when transferable.
If the Error graph itself is not P-transferable, the Future instead fails with a
caller-domain `NonParallelValue`.

### P-local cooperative Future work

### Actor-boundary return-home confinement

Runtime representations of a return home are execution-domain-local control
metadata. Actor transfer must never serialize, proxy, remap, or reconnect such
metadata to another Actor.

Conceptually:

```text
evaluateNonLocalReturn(currentExecution, home, value):
    require home belongs to current Actor execution domain
    unwind locally to home
```

A destination Actor never receives a sender home through message payload,
bootstrap state, request metadata, or ActorRef/GroupRef routing state.

If an implementation internally represents calls/continuations using host
futures, stacks, fibers, continuations, callbacks, or RPC frames, those
representations do not create a Core-visible cross-Actor return path.

### Future.all aggregate observation

`Future.all(futures...)` creates a non-task-backed aggregate Future with one
observation slot per argument index.

Conceptually:

```text
aggregate.frontier = 0
aggregate.outcomes = pending[n]

onSourceTerminal(i, outcome):
    if aggregate is terminal:
        return

    outcomes[i] = outcome

    while frontier < n and outcomes[frontier] is resolved(value):
        frontier += 1

    if frontier == n:
        resolve aggregate with fresh Array of resolved values in index order
        unregister remaining source-observation records
        return

    if outcomes[frontier] is failed(error):
        fail aggregate with error
        unregister remaining source-observation records
        return

    if outcomes[frontier] is cancelled:
        cancel aggregate
        unregister remaining source-observation records
```

Callbacks for higher indexes may run earlier physically, but they only record
stable source outcomes until the ascending frontier can advance. This prevents
completion races from choosing failure/cancellation.

Cancelling the aggregate unregisters or makes inert its observation records but
does not call `cancel()` on any source. The aggregate has no producer Task and no
structured ownership edge.

`closure.future()` created while the current activation belongs to a P domain
creates an ordinary cooperative task owned by that P activation/domain. It keeps
ordinary live P-local Closure captures and does not use parallel projection.

### Actor-local cooperative execution segments

For an ordinary Actor-local task, one running Protos execution segment is
semantically non-preemptive with respect to other Protos work in the same Actor
domain.

Conceptually:

```text
runActorLocalSegment(task):
    while task has not completed/failed
          and task has not reached an explicit suspension boundary:
        execute next Protos step
        // no semantic scheduler handoff inside this segment
```

The runtime may physically interrupt or migrate the carrier executing the
segment, but it must not dispatch another Actor-local Protos continuation against
the same mutable Actor state until the current segment reaches a portable
boundary.

Loop polls, allocation safepoints, GC safepoints, JIT polls, host-thread quantum
expiration, and similar implementation events are not semantic suspension
points.

Therefore a CPU-bound `closure.future()` segment can delay other Actor-local work
indefinitely if it never reaches a suspension point. This does not violate
Actor-local fairness because Core fairness does not create hidden preemption
inside an executing cooperative segment.

Isolated CPU-parallel progress is provided by `closure.parallel(...)`, whose P
domain has separate mutable authority and its own scheduler/fairness rules.

### P runnable-work fairness

A P task is runnable only when the semantic prerequisites for its next execution
segment are satisfied. A parent P task suspended waiting for a child Future is
not runnable; a child whose prerequisites are satisfied is independently
runnable.

Continuously runnable P work is weakly fair: if Process scheduling repeatedly
offers opportunities capable of P execution, the item must eventually execute a
segment or become non-runnable/terminal for an independent semantic reason.

Nested scheduling must not require a spare carrier. If bounded carriers are
occupied by ancestors waiting on runnable descendants, the runtime must
release/reuse/help/steal/inline or otherwise arrange descendant execution.
Concrete scheduling machinery is not observable.

### SIMD/vectorization legality

The runtime may vectorize physical execution only after proving observational
equivalence to the already-defined scalar/logical operation. Vector width,
instruction selection, masking, scalar fallback, and cost modelling are not
runtime-visible Protos state.

Vectorization must not alter result values, identity/aliasing, required
evaluation or mutation order, dispatch/invocation behavior, failure precedence,
explicit suspension/cancellation behavior, P isolation/publication, or fairness.
A reduction must preserve its specified logical combination order unless the
invoked operation's own contract explicitly permits another result semantics.

When such equivalence cannot be established, scalar or another
semantics-preserving implementation is required.

### Standard byte-region submission

Conceptually, standard `Bytes.parallelRange(start, length, worker, extras...)`:

1. requires the current execution domain to be P, otherwise signals
   `ParallelRegionOutsideP`;
2. validates semantic Integer range bounds and Closure worker;
3. rejects non-empty overlap on the same logical receiver with
   `ParallelRegionOverlap`;
4. validates worker/extra P inputs before publishing any reservation;
5. reserves the exact half-open interval;
6. snapshots that interval into a fixed-size child `ByteRegion`;
7. launches a child P whose projected worker receives the region first, followed
   by explicit extra arguments.

Parent `Bytes.at`/`atPut` performs ordinary index/value validation and then
signals `ParallelRegionInUse` before accessing a reserved index. Any standard
operation that changes byte-sequence length or shifts indexes performs the same
active-reservation rejection before structural mutation.

On normal child completion, the runtime first validates/transfers the child
result. Only then does it atomically substitute exactly the region's fixed number
of final bytes into the reserved parent interval, release the reservation, and
resolve the Future. Failure, cancellation, or result-transfer failure releases
the reservation without committing region bytes.

Disjoint reservations have no semantic ordering relative to each other. A
`ByteRegion.parallelRange` recursively derives authority over a subrange and
applies the same rules.

At most one cooperative segment in the same P domain executes Protos code at a
time. Explicit suspension may let another runnable P-local cooperative task run.
A nested `closure.parallel(...)` creates a distinct isolated P domain and may
execute simultaneously.

Remaining P-local children are subject to structured cancellation/cleanup when
the P domain finishes. Detachment does not re-parent them to the caller Actor,
Process, RootActor, or another execution domain.

Implementations may map these guarantees to the host VM memory model, scheduler barriers, or equivalent mechanisms as long as language-level visibility is preserved.



## Core Reflection Runtime Semantics

The standard reflective messages operate on the receiver's own object structure rather than delegated lookup.

```text
hasSlot(name)
    inspect receiver.localSlots only

slotNames()
    enumerate receiver.localSlots only

### Map comparison-scope lifetime across suspension

`mapComparisonDepth(map)` denotes active comparison invocations for the Map, not
only comparison code that is currently consuming an Actor execution segment.

Conceptually, entering a normal Map candidate comparison creates one
Map-specific comparison-scope token:

```text
function compareMapCandidate(map, queryKey, storedKey):
    scope = enterMapComparisonScope(map)

    try:
        return requireBooleanEqualityResult(
            send(queryKey, "==", [storedKey])
        )
    finally:
        leaveMapComparisonScope(scope)
```

`enterMapComparisonScope` increments the Map's active comparison restriction,
and `leaveMapComparisonScope` removes exactly that contribution. The `finally`
semantics above include normal return, error unwind, non-local return, and
cancellation unwind.

If `send(queryKey, "==", [storedKey])` explicitly suspends, the scope token
remains live in the suspended continuation and the Map's active comparison
count remains greater than zero. The Actor may execute another runnable task,
but `requireMapEntryMutationAllowed(map)` in that task observes the still-active
Map restriction and rejects a keyed-entry mutation before it occurs.

The active count is therefore Map-scoped Actor state whose lifetime may span
several Actor turns. It is not task-private in the sense used for dynamic error
handlers. The scope token itself belongs to the comparison invocation so that
unwind can release exactly the contribution it created.

A different Actor cannot observe or contend on this state because mutable Map
identity does not cross Actor boundaries. No cross-Actor lock or synchronization
primitive is implied.

A read-only search of the same Map remains permitted while another comparison
scope is suspended. Because the Map's active comparison count is already
positive, that nested or interleaved search's query-hash phase remains subject
to `requireMapEntryMutationAllowed(map)` if its `hash` behavior attempts a
same-Map keyed-entry mutation.

Implementations may represent active comparison scopes with a depth counter,
scope tokens, continuation metadata, or another mechanism. They must preserve
the following observable invariants:

```text
active comparison exists for map
    -> keyed-entry mutation of map fails before mutation

comparison suspends
    -> restriction remains active

comparison resumes
    -> same restriction continues

comparison exits or unwinds
    -> its contribution is released exactly once
```

This mechanism must not block the Actor scheduler or prevent unrelated tasks
from executing. A conflicting mutation signals the existing Error rather than
waiting on the comparison scope.

### `slotNames()` ordering

Conceptually, reflection over local slot names behaves as:

```text
function slotNames(receiver):
    names = snapshotOfLocalSlotNames(receiver)

    sort names by ascending lexicographic Unicode-scalar sequence

    return Array(names)
```

For two slot-name Strings `a` and `b`, comparison is:

```text
function compareSlotName(a, b):
    for corresponding Unicode scalar values x, y:
        if x < y: return BEFORE
        if x > y: return AFTER

    if lengthInScalars(a) < lengthInScalars(b): return BEFORE
    if lengthInScalars(a) > lengthInScalars(b): return AFTER
    return SAME
```

`SAME` can occur only for the same semantic slot-name String, and a local object
cannot contain two distinct local slots with the same name.

The snapshot is taken from the receiver's local slots only. Delegated slots are
not included. Mutating the receiver after the snapshot has been produced does
not mutate the returned Array implicitly.

The pseudocode specifies observable ordering, not representation. The runtime
need not keep local slots physically sorted and need not maintain insertion-order
metadata merely for reflection. Any implementation is conforming if the
returned names are observably equivalent to this canonical order.

slotValue(name)
    read receiver.localSlots[name] only
    signal an error if absent

parent()
    return receiver.delegationParent
```

No reflective slot operation above walks the delegation chain.

Ordinary member lookup remains responsible for delegated lookup semantics.

For the unique root object:

```text
Object.parent()
```

signals an error because `Object` structurally has no delegation parent. The runtime must not expose `null`, a fabricated root object, or an implementation sentinel as Object's parent.

The exact concrete error prototypes used for missing reflective slots and root-parent access may be defined by the standard library/runtime error catalogue, provided they obey the language's normal error signaling rules.
