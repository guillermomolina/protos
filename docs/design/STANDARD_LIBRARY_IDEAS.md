# Standard Library Ideas and Numeric Boundary Audit

Status: exploratory design; non-normative

This document records candidate Protos Standard Library domains and a focused
architecture audit of numeric library placement. It does not reserve a `LIBxxx`
identifier, create implementation work, or change Core language semantics.

The normative specification under `spec/` remains authoritative. If a candidate
library requires a new semantic numeric family, new Core observable behavior, or
another missing runtime/Core prerequisite, that prerequisite must be designed and
tracked outside Standard Library work before the library relies on it.

## Why this audit exists

A functional subject area and a semantic layer are independent classifications.
The Collections work already established this distinction: `Array`, `Map`, and
`IdentityMap` are collection concepts but remain Core because their identities,
state, and fundamental contracts are Core semantics, while importable collection
algorithms live in ordinary Standard Library modules.

Numeric work must use the same rule. A concept does not move out of Core merely
because a future Standard Library module operates in the same subject area.

## Existing Core numeric boundary

Core v0.1 already owns the semantic numeric model:

```text
Number
├── Integer
│   ├── UInt8
│   ├── Int8
│   ├── UInt16
│   ├── Int16
│   ├── UInt32
│   ├── Int32
│   ├── UInt64
│   └── Int64
└── Float
```

The following therefore remain Core responsibilities:

- numeric semantic-family membership;
- standard `Number`, `Integer`, `Float`, and fixed-width prototype identities;
- exact fixed-width ranges;
- unbounded ordinary `Integer` semantics;
- IEEE binary64 `Float` semantics;
- numeric `==`, `===`, ordering, and hash coherence;
- standard arithmetic already owned by Core;
- exact standard numeric conversion factories;
- Actor/P transfer semantics for Core numeric values;
- the distinction between semantic value and host/internal representation.

`Int64` is therefore simultaneously a numeric concept and a Core concept, just
as `Map` is simultaneously a collection concept and a Core concept.

### No Standard Library `BigInteger` value family

Ordinary Protos `Integer` already denotes mathematical integers with unbounded
precision. `SmallInteger`, `BigInteger`, tagged integers, machine-word integers,
and similar names are implementation representation categories, not visible Core
numeric families.

A Standard Library module must not introduce `BigInteger` merely to expose a
large-integer representation. Algorithms that benefit from arbitrarily large
integers should operate on the existing Core `Integer` value family.

A genuinely distinct future numeric abstraction such as decimal arithmetic,
rationals, or complex numbers must not be called a Core `Number` family merely
because that is convenient for operator reuse. If it needs Core numeric-family
membership, numeric value identity, cross-family equality/hash, or Core operator
integration, that is a normative Core design question rather than ordinary
library work.

## Math versus Numbers

### Candidate: separate `std:numbers/...`

Rejected for the initial Standard Library shape.

The name suggests ownership of numeric types or a general numeric hierarchy,
while Protos already has a closed normative Core numeric model. A second numeric
namespace would create a recurring ambiguity about whether `Integer`, `Int64`,
`Float`, future number-like objects, conversions, or algorithms belong to Core,
`std:numbers`, or `std:math`.

Some languages have a useful `numbers` layer because they need an abstract type
hierarchy or extensible numeric tower. That motivation does not transfer to
Protos: semantic numeric-family membership is not conferred by delegation or by
implementing an ordinary library protocol.

### Selected exploratory direction: one `std:math/...` domain

Use one mathematical Standard Library domain and split modules by responsibility
inside it:

```text
std:math/Math
std:math/Integer
```

Possible future modules, only after focused design, include:

```text
std:math/Rational
std:math/Decimal
std:math/Complex
std:math/Bits
```

This keeps one subject namespace while preserving semantic distinctions between
Core numeric families and optional algorithms/data abstractions.

Physical source placement would follow the existing Standard Library model:

```text
protos/lib/math/Math.protos
protos/lib/math/Integer.protos
```

The names above are exploratory module identities, not committed API.

## `std:math/Integer` direction

This is the lowest-risk numeric library candidate because ordinary Core
`Integer` is already exact and unbounded, and many useful algorithms can be
written entirely in Protos without a new runtime value family or host bridge.

### Strong initial candidates

```text
gcd(integer, other)
lcm(integer, other)
factorial(integer)
pow(integer, exponent)
powMod(integer, exponent, modulus)
```

Additional low-level predicates/utilities may be considered after API review:

```text
abs(integer)
sign(integer)
isEven(integer)
isOdd(integer)
```

The initial audit should decide whether these functions accept only ordinary
unbounded `Integer` or all exact-integer Core families. Hidden widening,
wrapping, or family conversion must not be inferred merely for convenience.
Callers can always request an explicit Core conversion when a particular family
is required.

### Later integer-algorithm candidates

These are useful but deserve separate complexity and contract review:

```text
isPrime
nextPrime
primeFactors
binomial
```

Primality APIs in particular need explicit guarantees: deterministic versus
probabilistic result, accepted magnitude, worst-case cost, cancellation/progress
expectations, and whether random input is involved. They should not be bundled
into the first slice merely because arbitrary-precision storage already exists.

## `std:math/Math` direction

`Math` should contain mathematical functions rather than define numeric
families. Candidate surface includes:

```text
sqrt
cbrt
pow
exp
log
log2
log10
sin
cos
tan
asin
acos
atan
atan2
sinh
cosh
tanh
hypot
```

Candidate constants include:

```text
pi
e
tau
```

Rounding/decomposition functions such as `floor`, `ceil`, `trunc`, and `round`
need a focused result-family audit before inclusion. The library must not hide a
Float-to-Integer conversion policy that Core deliberately makes explicit.

### Float-domain recommendation

The first transcendental API should prefer explicit Float inputs and Float
results rather than silently converting arbitrary `Number` values. In
particular, accepting an arbitrary unbounded `Integer` by implicitly calling
`Float(value)` would silently introduce possible precision loss into an otherwise
ordinary library call.

A caller that wants binary64 behavior can write the conversion explicitly.

### Required quality/portability design before implementation

Transcendental functions are not yet a low-risk pure-Protos slice. Before they
are standardized, the library needs an explicit contract for:

- NaN and infinity cases;
- signed-zero preservation where observable;
- radians versus any other angular unit;
- required accuracy / ulp bounds;
- reproducibility across host platforms and implementations;
- monotonicity or correct-rounding guarantees where relevant;
- overflow/underflow behavior;
- whether a pure-Protos implementation is required or an audited host-backed
  Standard Library bridge is permitted.

Do not expose JVM `Math` behavior as Protos semantics merely because the current
implementation runs on the JVM. If host-backed implementation is selected, its
portable library contract must be defined independently of the Java API and the
repository must establish an explicit non-Core Standard Library native-boundary
architecture rather than quietly extending the Core native boundary.

## Rational, Decimal, and Complex

These are deliberately not part of the first `Math`/`Integer` implementation.
They are separate representation and semantic-design questions.

### Rational

A library-only representation could use ordinary exact Integers, for example a
canonical numerator and positive denominator reduced by `gcd`. This can be pure
and exact.

However, an ordinary library value is not automatically a Core `Number`. Before
choosing operator-shaped ergonomics, decide how equality, hashing, Actor transfer,
behavior placement, normalization, zero denominators, and representation
validation work without making a library prototype a privileged Core identity.

### Decimal

A library Decimal could use an exact Integer coefficient plus a decimal exponent
or scale, similar in spirit to the exact decimal representation already useful
for JSON numbers. A general Decimal abstraction additionally needs explicit
rounding/context policy, division semantics, normalization, equality versus
representation scale, and formatting/parsing contracts.

Do not introduce `BigDecimal` merely as a host-representation mirror.

### Complex

A library Complex value can be represented by ordinary components, but a design
must choose the component domain and result rules. If Complex is expected to
participate in Core Number identity/equality/hash or ordinary Core arithmetic as
a semantic numeric family, that is a Core language change and must be designed
normatively first.

## Bit operations are related but distinct

Core explicitly leaves integer bit-manipulation and wrapping protocols for later
ordinary-library design. They should not be smuggled into `Math` simply because
they operate on integers.

A future focused `std:math/Bits` audit can define questions such as:

- unbounded Integer bit semantics;
- fixed-width bit semantics;
- signed right shift versus logical right shift;
- explicit wrapping arithmetic;
- width selection and masks;
- conversion to/from Bytes and the separate role of endianness.

This area is likely valuable for compression, crypto, codecs, and systems work,
but the exact width/wrapping laws must remain visible.

## Randomness should remain a separate domain

Deterministic pseudorandom generation and host entropy are semantically different.
A pure seeded PRNG can be ordinary library behavior and should be reproducible by
algorithm/version. Cryptographically secure or host-derived entropy crosses a
resource/authority boundary and must not be introduced as ambient `Math.random()`.

A future `std:random/...` design is therefore preferable to placing randomness in
`std:math/Math`.

## Candidate Standard Library portfolio

The following are exploratory ideas only. No `LIBxxx` identifiers are reserved.
Existing LIB001-LIB004 work remains independent and is not renamed by this list.

### Mostly ordinary-library candidates

These appear capable of substantial useful work over existing Core mechanisms
before requiring a new runtime boundary:

| Candidate | Initial role | Main design pressure |
| --- | --- | --- |
| `math` | exact integer algorithms; later Float math | Float quality/portability contract |
| `statistics` | descriptive statistics and simple relations | accepted collection/input surface; numeric result rules |
| `csv` | CSV parser/encoder | dialect policy, streaming composition, malformed-input errors |
| `uri` | URI parsing, normalization, resolution | exact standards/profile and Unicode/percent-encoding rules |
| `cli` | argument/option parsing and help generation | policy versus mechanism; no hidden Process authority |
| `toml` | TOML parser/encoder and explicit data model | datetime/numeric mapping and duplicate-key rules |

### Candidates needing a deeper semantic/API audit

| Candidate | Initial role | Main design pressure |
| --- | --- | --- |
| `linear-algebra` | vectors, matrices, algebraic algorithms | numeric-domain abstraction without inventing a fake Number hierarchy |
| `regex` | pattern matching | syntax/Unicode contract and implementation complexity |
| `datetime` | dates, durations, calendars, time zones | separate pure calendar math from clock/time-zone authority |
| `xml` | XML document/event processing | namespaces, attributes, mixed content, streaming |
| `yaml` | YAML data/graph processing | tags, aliases, shared identity/cycles, security limits |
| `logging` | structured events, formatting, sinks | I/O authority, concurrency and backpressure |
| `testing` | assertions, fixtures, runners/helpers | failure representation, isolation, execution tooling |

### Candidates likely to require explicit host/runtime capability boundaries

| Candidate | Initial role | Boundary pressure |
| --- | --- | --- |
| `random` secure entropy | secure randomness | host entropy authority |
| `compression` | gzip/deflate and related codecs | bit operations, streaming, native acceleration policy |
| `crypto` | hashes/MACs/cryptographic primitives | constant-time/security contract, entropy, native implementations |
| `networking` | sockets/connections/endpoints | live resource authority, async I/O, cancellation, transfer |
| `http` | HTTP messages/client/server | networking prerequisite, streaming/backpressure, TLS authority |

A candidate being in the final group does not mean it belongs in Core. It means
its Standard Library design probably needs a separately audited runtime or
capability substrate rather than pretending to be pure module code.

## Prior-art models used for this exploration

The comparison is intentionally about design models, not API popularity.

- Python separates `numbers` (an abstract numeric hierarchy), `math`/`cmath`
  (functions), `decimal`/`fractions` (numeric data abstractions), and `statistics`.
  Protos does not need Python's `numbers` role because Core numeric-family
  membership is already semantic and closed.
- Java separates `Math` from `BigInteger`/`BigDecimal`; its `BigInteger` combines
  arbitrary-precision representation with number-theoretic algorithms. Protos
  already has arbitrary precision in ordinary `Integer`, so copying the Java
  representation type would be redundant even though several algorithms are
  useful precedent.
- Rust places many mathematical operations directly on primitive numeric types.
  That model is natural for Rust's type system but would unnecessarily expand
  Protos Core when optional algorithms can remain ordinary modules.
- Smalltalk places a rich mathematical protocol on its Number/Integer hierarchy.
  This validates message-oriented mathematical APIs, but Protos deliberately
  keeps semantic-family membership and optional library behavior separate.

Primary reference entry points:

- https://docs.python.org/3/library/numeric.html
- https://docs.python.org/3/library/statistics.html
- https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Math.html
- https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/package-summary.html
- https://doc.rust-lang.org/stable/core/primitive.f64.html
- https://www.gnu.org/software/smalltalk/manual-base/html_node/Integer_002dmath-methods.html
- https://www.gnu.org/software/smalltalk/manual-base/html_node/Number_002dmisc-math.html

## Recommended sequencing if promoted to implementation work

Do not reserve work-item identifiers while this remains exploratory. If the idea
is promoted, prefer cost-aware slices rather than one broad "numeric library"
implementation:

1. focused `std:math/Integer` API audit and pure-Protos exact algorithms;
2. separate Float/transcendental quality and implementation-boundary design;
3. `std:math/Math` only after that contract is closed;
4. independent Rational/Decimal/Complex audits if real use cases justify them;
5. independent bit/wrapping and random-domain designs.

Each promoted library slice must remain ordinary Standard Library work unless it
uncovers a genuine missing Core/runtime prerequisite. Such a prerequisite is
tracked and resolved separately; library pressure does not redefine Core by
accident.

## Current recommendation

```text
KEEP IN CORE
    Number
    Integer
    Float
    UInt8 / Int8
    UInt16 / Int16
    UInt32 / Int32
    UInt64 / Int64
    fundamental arithmetic/equality/order/hash/conversion semantics

DO NOT ADD
    BigInteger as a visible numeric family
    std:numbers as a parallel numeric hierarchy/domain

EXPLORE AS STANDARD LIBRARY
    std:math/Integer
    std:math/Math

DESIGN SEPARATELY BEFORE COMMITTING
    Rational
    Decimal
    Complex
    Bits / wrapping
    Random / entropy
```
