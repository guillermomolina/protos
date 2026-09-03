# Core Runtime Semantics v0.1

Language version: 0.1  
Document revision: 124
Status: Draft  
Last updated: 2026-09-03
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
    unwind current asynchronous activation
    run all applicable ensure cleanup
    complete task.future as CANCELLED
```

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
```

This performs automatic Future flattening.

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
            scheduler.suspend(
                activation,
                until = future
            )

            return awaitFutureValue(
                future,
                activation
            )
```

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
    if task.future.state == pending:
        task.future.cancellationRequested = true
```

The task/Future link is conceptual runtime bookkeeping. It does not add a
language-visible slot to `Future` or `Task`, and it does not require a particular
scheduler representation. A task-backed Future and its producing task denote one
cooperative cancellation target: requesting cancellation through either
structured ownership or `future.cancel()` sets the same request observed by that
task at portable cancellation boundaries.

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
    future.task.detached = true
    remove future.task from its owner
    future.task.owner = none

    return future
```

A detached task no longer participates in the former owner's completion or cancellation lifetime.

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

The runtime may optimize argument vectors, rest collections, and `args` views, but observable semantics must remain those of ordinary immutable collections.

No dispatch by argument type is implied. These mechanisms support dynamic arity, forwarding, and user-defined helper protocols without introducing method-overload resolution.


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
    return processLocalHashInteger(key)
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

`processLocalHashInteger` returns a semantic Integer and may use per-execution
salting or randomization. It need not be injective: collisions between unequal
numeric keys are valid. It must nevertheless be stable for a given numeric key
for the duration of the execution.

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
during one execution. Value-identity objects derive identity-hash behavior from
their semantic identity rather than from transient boxing/allocation identity.
Collisions remain permitted.

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
    queryHash = requireHashResult(
        send(queryKey, "hash", [])
    )

    entry = findMapEntryUsingKnownQueryHash(
        map,
        queryKey,
        queryHash
    )

    if entry != NOT_FOUND:
        oldValue = entry.value
        entry.value = value
        return oldValue

    appendEntry(
        map,
        key = queryKey,
        value = value,
        recordedHash = queryHash
    )

    return ABSENT
```

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

`IdentityMap` continues to use semantic identity and `identityHash` and does not
use `findMapEntry`.

`IdentityMap` uses primitive semantic identity (`===`) together with a stable `identityHash`.

`hash` values need only be valid within the current execution. The runtime may salt hashes per process. Persisted hash values must therefore not rely on the ordinary `hash` protocol.

Map iteration preserves insertion order as an observable collection property.

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
    if future.state == pending:
        future.cancellationRequested = true

    return future
```

The exact return value of `cancel()` may be refined by the standard protocol, but cancellation request is not an unsafe immediate kill.

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

Different Actors never share mutable Protos references, so no cross-Actor mutable-state visibility rule exists beyond Actor communication semantics. Explicit isolated parallel computation, where provided, may execute Protos code simultaneously on other CPU carriers, but it crosses an isolation boundary, receives no arbitrary live mutable aliases to the calling Actor's state, and returns its result by value, so conflicting concurrent mutation of one Actor's mutable state does not arise.

Implementations may map these guarantees to the host VM memory model, scheduler barriers, or equivalent mechanisms as long as language-level visibility is preserved.



## Core Reflection Runtime Semantics

The standard reflective messages operate on the receiver's own object structure rather than delegated lookup.

```text
hasSlot(name)
    inspect receiver.localSlots only

slotNames()
    enumerate receiver.localSlots only

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
