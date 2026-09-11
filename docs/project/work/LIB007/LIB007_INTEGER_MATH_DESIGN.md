# LIB007 — Mathematical integer algorithms Standard Library design

Status: **LIB007-0 CLOSED — design/selection ratified; implementation pending**

Owning work item: GitHub Issue `#329` — `LIB007 — Mathematical integer algorithms`

Nature: project Standard Library design record; **non-normative**

Explicit project-owner approval: **2026-09-11**

Validation class: `GOVERNANCE_DOCUMENTATION_ONLY`

Normative dependencies: the existing Core numeric-family, Integer, Error, Object,
Module and invocation contracts under `spec/`; in particular ordinary `Integer`
remains the exact unbounded Core integer family and fixed-width integer families
remain semantically distinct.

## Purpose

This record closes the `LIB007-0` comparative design/selection checkpoint for a
small ordinary-Protos mathematical-integer Standard Library domain.

The selected design adds **no new numeric family**. It does not introduce a
`BigInteger` abstraction, a parallel `std:numbers` hierarchy, implicit widening
between numeric families, a Java/GMP/native Standard Library dependency, or a
second arithmetic universe.

The normative Protos specification under `spec/` remains authoritative. This
record selects only an optional Standard Library API over already-existing Core
`Integer` semantics.

## Selected architecture

The selected module identity is:

```text
std:math/Integer
```

The selected physical distribution path is:

```text
protos/lib/math/Integer.protos
```

The module is ordinary Standard Library code. Importing it grants no authority,
creates no global mutable state, changes no Core prototype, and changes no
numeric-family membership.

All public numeric arguments in the initial surface accept **only ordinary
unbounded Core `Integer` values**.

Fixed-width `Int8`/`UInt8`/`Int16`/`UInt16`/`Int32`/`UInt32`/`Int64`/`UInt64`
values are not accepted merely because they delegate through `Integer` or their
mathematical value happens to fit. `Float` is not accepted. A caller that
intentionally wants arbitrary-precision Integer semantics may perform the
existing explicit Core `Integer(...)` conversion before calling this library.

This rule deliberately keeps conversion visible and avoids inventing hidden
widening, wrapping, saturation, result-family selection or mixed-family policy.

## Selected initial public surface

```text
std:math/Integer

    gcd(a, b)                       -> Integer
    lcm(a, b)                       -> Integer
    factorial(n)                    -> Integer
    pow(base, exponent)             -> Integer
    powMod(base, exponent, modulus) -> Integer
```

The selected initial surface intentionally excludes `abs`, `sign`, `isEven`,
`isOdd`, `isqrt`, `binomial`, primality/factorization, modular inverse,
transcendental Float mathematics, Rational/Decimal/Complex abstractions,
bit/wrapping APIs and random/entropy APIs.

Those exclusions are not claims that the operations are undesirable. They keep
the first library bounded and avoid prematurely selecting their long-term
placement or wider contracts.

## Exact selected contracts

### `gcd(a, b)`

- both arguments are ordinary Core `Integer` values;
- signed inputs are accepted;
- the result is the mathematical greatest common divisor as an ordinary
  non-negative `Integer`;
- `gcd(0, 0) == 0`;
- zero with a non-zero argument returns the absolute mathematical value of the
  non-zero argument;
- wrong arity or wrong numeric family signals ordinary synchronous `Error`.

No algorithm is part of the public contract.

### `lcm(a, b)`

- both arguments are ordinary Core `Integer` values;
- signed inputs are accepted;
- the result is the mathematical least common multiple as an ordinary
  non-negative `Integer`;
- if either argument is zero, the result is `0`;
- wrong arity or wrong numeric family signals ordinary synchronous `Error`.

Implementations should avoid avoidable giant intermediates; the natural
reference formulation divides by the GCD before multiplying.

### `factorial(n)`

- `n` is an ordinary Core `Integer`;
- `n >= 0` is required;
- `factorial(0) == 1`;
- negative input signals ordinary synchronous `Error`;
- the result is an exact ordinary Core `Integer`;
- no arbitrary magnitude cap becomes Standard Library semantics.

The contract does not require repeated linear multiplication. Implementations
remain free to use balanced products, odd-part decomposition, product trees or
later optimized backends while preserving the same result and failures.

### `pow(base, exponent)`

- both arguments are ordinary Core `Integer` values;
- `exponent >= 0` is required;
- the result is exact ordinary Core `Integer`;
- `pow(0, 0) == 1`;
- a negative exponent signals ordinary synchronous `Error` rather than
  implicitly selecting Float, Rational or another future numeric abstraction;
- no arbitrary exponent or result-size cap becomes public semantics.

The implementation-quality floor is exponentiation by squaring or a method with
no worse asymptotic multiplication count; O(exponent) repeated multiplication is
not the intended scalable implementation.

### `powMod(base, exponent, modulus)`

- all arguments are ordinary Core `Integer` values;
- `base` may be signed;
- `exponent >= 0` is required;
- `modulus > 0` is required;
- the result is the canonical ordinary Integer representative satisfying
  `0 <= result < modulus`;
- `powMod(0, 0, m) == 1 mod m`, therefore the result is `0` when `m == 1`;
- negative exponents/modular inverse are not selected by LIB007-0;
- invalid domain, wrong arity or wrong numeric family signals ordinary
  synchronous `Error`.

The operation is mathematical modular exponentiation. It carries **no
constant-time, cache-oblivious or side-channel-resistance guarantee**. A future
cryptographic facility may define a separate security contract.

An implementation must reduce throughout the exponentiation rather than
materializing `base^exponent` first. Square-and-multiply is a suitable reference
implementation; Montgomery/windowed or other representations remain free later.

## Implementation freedom and scalability

The public API specifies mathematical results, accepted domains and failures,
not the concrete algorithm or host representation.

The initial ordinary-Protos implementation may start with simple scalable
reference algorithms, but the contract deliberately permits later invisible
specialization:

```text
gcd:       Euclid -> Lehmer -> HGCD / future algorithms
pow:       binary exponentiation -> future multiplication-aware strategies
powMod:    modular binary exponentiation -> Montgomery/windowed/future backend
factorial: balanced/product-tree/odd-part strategies as justified by evidence
```

A conforming future implementation may use Truffle specialization, Bytecode DSL,
a native implementation, GMP-like machinery, another VM, or another internal
representation only when observable behavior remains identical to this Standard
Library contract and the existing Core semantics.

This separation is intentional: large-integer algorithm evolution must not force
a public API migration.

## Comparative prior-art audit

LIB007-0 was selected only after an explicit comparative audit spanning
prototype/dynamic, static, functional/runtime and native big-integer systems.
The detailed discussion is retained in GitHub Issue `#329`; the durable findings
that affected the selection are summarized here.

| System / model | Future resilience | Scalability | Protos alignment | Durable lesson |
| --- | ---: | ---: | ---: | --- |
| Python 3.15 `math.integer` + CPython | 5.0 | 4.5 | 5.0 | Strong precedent for a separate integer-math library over the language's ordinary arbitrary-precision integer; implementation remains independently optimizable. |
| GHC `Integer` + `ghc-bignum` | 5.0 | 5.0 | 4.5 | Stable Integer semantics can sit above replaceable bignum backends without changing program-visible results. |
| Apple Swift Numerics / `IntegerUtilities` | 5.0 | 3.5 | 4.5 | Numeric API staging outside the language core preserves future options; generic fixed-width result preservation exposes representability/trap pressure that Protos avoids. |
| Ruby `Integer` | 4.5 | 4.5 | 4.5 | A single arbitrary-precision Integer concept is preferable to a user-visible small/big split; receiver-heavy placement is not required for Protos Core. |
| Elixir/BEAM `Integer` | 4.5 | 4.0 | 4.5 | A focused Integer module can provide higher-level operations over ordinary arbitrary-precision integers without inventing `BigInteger`. |
| Smalltalk / GNU Smalltalk | 4.0 | 3.0 | 4.5 | Integer algorithms are naturally composable with message-oriented integer behavior, but its richer Core protocol is broader than Protos needs initially. |
| Rust `num-integer` + `num-bigint` | 4.5 | 4.5 | 3.5 | Good implementation evidence; generic/fixed-width family behavior shows why width and overflow policy should remain separate from LIB007. |
| .NET `BigInteger` | 4.5 | 5.0 | 3.0 | Strong adaptive multi-limb implementation evidence, including Lehmer-style GCD; separate `BigInteger` is unnecessary in Protos. |
| OpenJDK `BigInteger` | 4.0 | 4.5 | 3.0 | Strong evidence for immutable public semantics with optimized mutable/internal arithmetic; separate big-integer public type is not needed. |
| Julia + GMP | 4.0 | 5.0 | 3.0 | Excellent scale evidence, but promotion-centric mixed numeric behavior conflicts with Protos' visible family distinctions. |
| Go `math/big` | 4.0 | 5.0 | 2.5 | Destination-oriented mutable APIs provide allocation control but would expose aliasing/identity machinery inappropriate for Protos Integer values. |
| C++ `std::gcd` / Boost integer utilities | 3.0 | 3.0 | 2.0 | Fixed-width common-type representability constraints demonstrate the hazards of mixing exact mathematical contracts with bounded families. |
| GMP | 5.0 | 5.0 | 1.5 as public API | Reference ceiling for adaptive arithmetic algorithms, not a suitable public Protos object/API model. |
| Apple Pkl | 2.5 | 2.0 | 2.5 | Useful control comparison, but its signed 64-bit `Int` does not solve the same arbitrary-precision semantic problem. |

Scores evaluate each approach **as precedent for Protos LIB007**, not the overall
quality of the language or implementation.

## Candidate selection

The meaningful Protos architecture candidates were:

1. add the algorithms directly to the universal Core `Integer` protocol;
2. place integer algorithms in a broad generic `std:math/Math` module;
3. create `std:math/Integer` restricted to ordinary unbounded `Integer`;
4. make `std:math/Integer` generic over all exact-integer families with implicit
   widening/result-family rules;
5. introduce a user-visible `BigInteger` library abstraction; or
6. bind the public library contract to a JVM/GMP/native implementation.

The project owner explicitly selected **candidate 3**.

It best preserves Protos' small-universe and mechanisms-over-institutions
principles: Core retains the mathematical integer semantics it already owns,
while optional higher-level algorithms remain ordinary imported library code.
There is no new privileged numeric object, no hidden conversion policy and no
host/runtime dependency in the public contract.

The strongest argument against this selection is ergonomics: in a prototype
language, `a.gcd(b)` may eventually feel more natural than a module operation.
That does not justify permanently expanding Core today. A future principled
scoped extension/protocol mechanism could provide receiver-oriented syntax while
retaining this module contract as the stable implementation/semantic layer.

## Future stress analysis

### Very large Integers

The mathematical contract remains valid independently of operand size. Pure
Protos reference implementations may later be replaced or intrinsified with
adaptive multi-limb algorithms without changing callers.

### Fixed-width integers and future `Bits`

Strict ordinary-Integer input prevents LIB007 from pre-selecting widening,
wrapping, saturation or result-family rules for fixed-width arithmetic. A future
`std:math/Bits` or other fixed-width facility remains independent.

### Rational, Decimal and Complex

Rejecting negative integer exponents here avoids pre-empting future exact
Rational/Decimal/Complex result semantics. Those domains may compose with
LIB007 later through explicit conversion or separately approved APIs.

### Crypto

`powMod` provides mathematical modular exponentiation only. Constant-time and
side-channel contracts remain available for future cryptographic work without
retroactively changing LIB007.

### Tasks, Actors, Processes and distribution

These functions are authority-free deterministic computations over immutable
value-family inputs/results. LIB007 introduces no resource ownership, identity,
transfer, scheduling, Future, cancellation or distributed protocol.

A caller may of course execute an expensive computation inside ordinary Task,
Actor or Process machinery; LIB007 itself does not create hidden asynchronous or
parallel execution.

### Alternative runtimes and Bytecode DSL

No public contract depends on Java `BigInteger`, Truffle AST nodes, host word
size, the JVM, GMP, the current interpreter or a specific compiler. Migration to
Bytecode DSL or another runtime remains an implementation concern.

## Intentionally deferred

LIB007-0 does not select:

- `abs`, `sign`, `isEven` or `isOdd` placement;
- integer square root, combinations/permutations or binomial APIs;
- primality testing, next-prime or factorization semantics/cost contracts;
- modular inverse or negative modular-exponent behavior;
- cryptographic constant-time arithmetic;
- fixed-width/wrapping integer math;
- Rational, Decimal or Complex abstractions;
- Float/transcendental `std:math/Math` contracts;
- parallel/Task-based variants or cancellation checkpoints for large pure
  calculations;
- a native/bignum optimization threshold or backend.

Any deferred item that introduces a substantive semantic or durable
architecture choice crosses the ordinary explicit approval gate before a future
implementation treats it as selected.

## Implementation decomposition released by this ratification

After this ratification is published, the approved initial surface may be
implemented in cost-aware slices without reopening LIB007-0 when no new design
choice appears:

```text
LIB007-A  gcd / lcm
LIB007-B  factorial
LIB007-C  pow / powMod
LIB007-D  integrated conformance / closure
```

Each implementation slice must use the real `std:` resolver path, retain strict
ordinary-Integer family validation, add boundary/error/large-value evidence and
follow the repository's current adaptive validation/publication policy.

If implementation exposes a new substantive language, Standard Library or
platform decision, stop the affected slice and route that question through the
appropriate approval gate rather than deciding it inside the patch.

## Change classification

`VALIDATION_CLASS=GOVERNANCE_DOCUMENTATION_ONLY`

This ratification adds only this durable non-normative LIB007 design record and a
matching changelog entry. It changes no Protos specification, executable
implementation/runtime, Standard Library executable source, Maven implementation
version, public release artifact, package format, license term or deployment.
