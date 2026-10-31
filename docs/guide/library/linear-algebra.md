# Vectors and matrices: `std:math/LinearAlgebra`

`std:math/LinearAlgebra` provides dense numeric `Vector` and `Matrix` values
over the ordinary Protos Number families. The module is
[`protos/lib/math/LinearAlgebra.protos`](../../../protos/lib/math/LinearAlgebra.protos),
whose doc comment is the exact reference; the normative value semantics are in
[`spec/semantics/VALUES_AND_COLLECTIONS.md`](../../../spec/semantics/VALUES_AND_COLLECTIONS.md).
If this guide and the specification disagree, the specification wins.

## Importing the module

There is no Prelude binding and no separate `std:math/Vector` or
`std:math/Matrix` module. Import the one module and bind its two prototypes:

```protos
LA: import("std:math/LinearAlgebra")
Vector: LA.Vector
Matrix: LA.Matrix

v: Vector(1, 2, 3)
m: Matrix(2, 2, 1, 2, 3, 4)
```

Bind the members first. `LA.Vector(1)` is a message send to the module's
`Vector` slot, and that slot holds a prototype, not a Closure, so the send
signals an Error under ordinary message-send rules.

## Vector

| Operation | Answers |
|---|---|
| `Vector(x, ...)` | A Vector of exactly the supplied elements, in order; `Vector()` is empty, `Vector(...[1, 2, 3])` spreads an Array |
| `size()` | The length |
| `at(i)` or `v[i]` | The element at `0 <= i < size()` |
| `atPut(i, value)` | Replaces one element and answers `value` |
| `add(other)` | The element-wise sum with a Vector of the same size |
| `scale(number)` | Every element multiplied by `number` |
| `dot(other)` | The Hermitian inner product with a Vector of the same size |
| `copy()` | An independent Vector with the same elements |

Indexes are zero-based exact Integers. Shapes must match exactly: `add` and
`dot` reject a Vector of another size. `add`, `scale` and `copy` answer fresh,
independent Vectors; `dot` answers a Number, `size` an Integer, `at` the
selected element, and `atPut` the assigned value.

## Matrix

| Operation | Answers |
|---|---|
| `Matrix(rows, columns, ...values)` | A `rows × columns` Matrix from exactly `rows * columns` values in row-major order |
| `rows()`, `columns()` | The dimensions |
| `at(row, column)` | One element |
| `atPut(row, column, value)` | Replaces one element and answers `value` |
| `add(other)` | The element-wise sum with a Matrix of the same shape |
| `scale(number)` | Every element multiplied by `number` |
| `matvec(vector)` | The Vector of size `rows()`; `vector.size()` must be `columns()` |
| `matmul(other)` | The `rows() × other.columns()` product; `other.rows()` must be `columns()` |
| `transpose()` | The independent `columns() × rows()` transpose |
| `copy()` | An independent Matrix with the same shape and elements |

Constructor data is row-major: `Matrix(2, 2, 1, 2, 3, 4)` has first row
`1, 2` and second row `3, 4`. Any other value count fails. `Matrix(0, n)` and
`Matrix(n, 0)` are distinct empty shapes. There is no `m[row]` or
`m[row, column]` indexing form; use `at(row, column)`.

## Number semantics

Elements are genuine Integers, Fractions, Floats or Complex values, and one
Vector or Matrix may mix families. `null`, Booleans, Strings and objects that
merely delegate to a Number prototype are rejected. Nothing is converted.

There is no dtype, broadcasting, reshaping, padding or promotion policy of the
module's own. Every scalar operation is the standard Protos Number arithmetic,
so mixing families behaves exactly as `+` and `*` do elsewhere: exact-real and
Float operands follow the established mixed numeric rules, and any operation
involving a Complex follows the standard Complex rules. Vector and Matrix add
no conversion or promotion policy of their own.

## Complex arithmetic

`Vector.dot` is Hermitian: it conjugates the *receiver's* elements, never the
argument's. `Matrix.matvec` and `Matrix.matmul` conjugate nothing:

```protos
i: Complex(0, 1)
Vector(i).dot(Vector(i))                    // 1, because conj(i) * i == 1
Matrix(1, 1, i).matvec(Vector(i))[0]        // -1, because i * i == -1
```

## Mutability and identity

Vectors and matrices are ordinary identity-bearing objects that delegate to
their standard prototype. Their numbers are private storage, not slots, and
two Vectors with equal elements are neither `===` nor `==` unless your program
redefines equality.

- `atPut` mutates an open instance.
- A frozen instance rejects `atPut`.
- A closed instance still accepts `atPut` on existing elements: closing stops
  slot changes, not element replacement.
- Algebraic operations (`add`, `scale`, `matvec`, `matmul`, `transpose`) never
  mutate an operand and answer fresh, open, independent results.
- `copy()` answers an independent instance; mutating it never affects the
  original.

## Numeric determinism

Every contraction (`dot`, each `matvec` element, each `matmul` cell) sums its
products in ascending index order, starting from the first product rather than
from `0` or `0.0`. An empty contraction answers the exact Integer `0`.

Float addition is not associative, so this fixed order is observable, including
for signed zero and NaN:

```protos
Vector(1.0e16, 1.0, -1.0e16).dot(Vector(1, 1, 1))   // 0.0
Vector(-1.0).dot(Vector(0.0))                       // -0.0
Vector().dot(Vector())                              // 0
```

Do not rely on any other reduction order; no reordering is permitted.

## Performance boundary

An implementation may run eligible operations through optimized CPU kernels.
That is an implementation detail: it never changes results, never introduces a
separate "fast" result mode, and is not a guaranteed speedup. There is no GPU
execution and no promised SIMD acceleration.

## Executable material

- [`tutorials/14-linear-algebra/`](../../../protos/tutorials/14-linear-algebra/):
  Vector operations, Matrix operations, and Complex values with shape rules.
- [`examples/numbers/vector-matrix.protos`](../../../protos/examples/numbers/vector-matrix.protos):
  a small linear transformation, verified two ways.
- [`protos/tests/library/math/linear-algebra/`](../../../protos/tests/library/math/linear-algebra/):
  the conformance tests, including the reduction-order and Complex cases above.

## Normative references

- [`spec/semantics/VALUES_AND_COLLECTIONS.md`](../../../spec/semantics/VALUES_AND_COLLECTIONS.md):
  Number families, Vector and Matrix semantics.
- [`protos/lib/math/LinearAlgebra.protos`](../../../protos/lib/math/LinearAlgebra.protos):
  the module reference.
