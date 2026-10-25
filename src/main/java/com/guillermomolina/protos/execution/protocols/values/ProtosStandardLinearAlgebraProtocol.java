/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */

package com.guillermomolina.protos.execution;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosComplexArithmetic;
import com.guillermomolina.protos.runtime.ProtosComplexArithmetic.Operation;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosMatrixValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosVectorValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The native protocol of the {@code std:math/LinearAlgebra} Vector and Matrix prototypes (D214
 * C1/S1, LIB024).
 *
 * <p>Every method is an ordinary native slot selected by ordinary lookup. Domain, index and shape
 * failures signal the standard Error; no host exception reaches the guest. The kernels compute
 * with {@link ProtosComplexArithmetic#canonicalNumberBinary} and never send a message to an
 * element. Every contraction is reduced under D214-F1: ascending contraction index, the first
 * product as the initial accumulator, and the exact Integer {@code 0} for an empty contraction.
 * Every Vector or Matrix result is a fresh open object delegating directly to the standard
 * prototype of its kind, with storage of its own. An all-Float {@code matmul} computes in host
 * binary64 over packed Matrix storage and answers a packed result (PLAT068 R2-Float, I124).
 */
public final class ProtosStandardLinearAlgebraProtocol {
    private ProtosStandardLinearAlgebraProtocol() {}

    /**
     * Creates the standard {@code Vector} and {@code Matrix} prototypes, direct children of Object
     * carrying their complete protocols, keyed by their {@code std:math/LinearAlgebra} member
     * names. Core bootstrap freezes them before publication.
     */
    public static Map<String, ProtosObjectValue> createModuleMembers() {
        ProtosObjectValue vectorPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue matrixPrototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        install(vectorPrototype, matrixPrototype);
        return Map.of("Vector", vectorPrototype, "Matrix", matrixPrototype);
    }

    /* Installs both protocols together, so neither prototype is ever published half-installed. */
    private static void install(
            ProtosObjectValue vectorPrototype, ProtosObjectValue matrixPrototype) {
        Objects.requireNonNull(vectorPrototype, "vectorPrototype");
        Objects.requireNonNull(matrixPrototype, "matrixPrototype");
        installVector(vectorPrototype);
        installMatrix(matrixPrototype, vectorPrototype);
    }

    private static void installVector(ProtosObjectValue vectorPrototype) {
        define(vectorPrototype, "call", (activation, supplied) -> {
            ProtosObjectValue prototype = requireConstructionReceiver(activation, vectorPrototype);
            try {
                return new ProtosVectorValue(prototype, supplied);
            } catch (IllegalArgumentException rejected) {
                throw invalid(activation);
            }
        });
        define(vectorPrototype, "size", (activation, supplied) -> {
            ProtosVectorValue vector = requireVector(activation, activation.receiver());
            requireArity(activation, supplied, 0);
            return ProtosNumericValueSupport.integer(vector.indexedSize());
        });
        define(vectorPrototype, "at", (activation, supplied) -> {
            ProtosVectorValue vector = requireVector(activation, activation.receiver());
            requireArity(activation, supplied, 1);
            try {
                return vector.indexedAt(supplied.get(0));
            } catch (IllegalArgumentException | IndexOutOfBoundsException rejected) {
                throw invalid(activation);
            }
        });
        define(vectorPrototype, "atPut", (activation, supplied) -> {
            ProtosVectorValue vector = requireVector(activation, activation.receiver());
            requireArity(activation, supplied, 2);
            try {
                return vector.indexedPut(supplied.get(0), supplied.get(1));
            } catch (IllegalStateException
                    | IllegalArgumentException
                    | IndexOutOfBoundsException rejected) {
                throw invalid(activation);
            }
        });
        define(vectorPrototype, "add", (activation, supplied) -> {
            ProtosVectorValue vector = requireVector(activation, activation.receiver());
            requireArity(activation, supplied, 1);
            ProtosVectorValue other = requireVector(activation, supplied.get(0));
            List<Object> left = vector.indexedSnapshot();
            List<Object> right = other.indexedSnapshot();
            if (left.size() != right.size()) {
                throw invalid(activation);
            }
            ProtosPrelude prelude = prelude(activation);
            Object[] sums = new Object[left.size()];
            for (int index = 0; index < sums.length; index++) {
                sums[index] = add(left.get(index), right.get(index), prelude);
            }
            return newVector(vectorPrototype, sums);
        });
        define(vectorPrototype, "scale", (activation, supplied) -> {
            ProtosVectorValue vector = requireVector(activation, activation.receiver());
            requireArity(activation, supplied, 1);
            Object scalar = requireNumber(activation, supplied.get(0));
            return newVector(
                    vectorPrototype, scaled(vector.indexedSnapshot(), scalar, prelude(activation)));
        });
        define(vectorPrototype, "dot", (activation, supplied) -> {
            ProtosVectorValue vector = requireVector(activation, activation.receiver());
            requireArity(activation, supplied, 1);
            ProtosVectorValue other = requireVector(activation, supplied.get(0));
            List<Object> left = vector.indexedSnapshot();
            List<Object> right = other.indexedSnapshot();
            if (left.size() != right.size()) {
                throw invalid(activation);
            }
            ProtosPrelude prelude = prelude(activation);
            // Hermitian: only the receiver's elements are conjugated.
            Object accumulator = null;
            for (int index = 0; index < left.size(); index++) {
                Object product =
                        multiply(
                                ProtosComplexArithmetic.conjugateNumber(left.get(index), prelude),
                                right.get(index),
                                prelude);
                accumulator = accumulate(accumulator, product, prelude);
            }
            return reduced(accumulator);
        });
        define(vectorPrototype, "copy", (activation, supplied) -> {
            ProtosVectorValue vector = requireVector(activation, activation.receiver());
            requireArity(activation, supplied, 0);
            return newVector(vectorPrototype, vector.indexedSnapshot().toArray());
        });
    }

    private static void installMatrix(
            ProtosObjectValue matrixPrototype, ProtosObjectValue vectorPrototype) {
        define(matrixPrototype, "call", (activation, supplied) -> {
            ProtosObjectValue prototype = requireConstructionReceiver(activation, matrixPrototype);
            if (supplied.size() < 2) {
                throw invalid(activation);
            }
            try {
                return new ProtosMatrixValue(
                        prototype,
                        supplied.get(0),
                        supplied.get(1),
                        supplied.subList(2, supplied.size()));
            } catch (IllegalArgumentException rejected) {
                throw invalid(activation);
            }
        });
        define(matrixPrototype, "rows", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 0);
            return matrix.rowsValue();
        });
        define(matrixPrototype, "columns", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 0);
            return matrix.columnsValue();
        });
        define(matrixPrototype, "at", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 2);
            try {
                return matrix.indexedAt(supplied.get(0), supplied.get(1));
            } catch (IllegalArgumentException | IndexOutOfBoundsException rejected) {
                throw invalid(activation);
            }
        });
        define(matrixPrototype, "atPut", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 3);
            try {
                return matrix.indexedPut(supplied.get(0), supplied.get(1), supplied.get(2));
            } catch (IllegalStateException
                    | IllegalArgumentException
                    | IndexOutOfBoundsException rejected) {
                throw invalid(activation);
            }
        });
        define(matrixPrototype, "add", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 1);
            ProtosMatrixValue other = requireMatrix(activation, supplied.get(0));
            if (!ProtosNumericValueSupport.sameInteger(matrix.rowsValue(), other.rowsValue())
                    || !ProtosNumericValueSupport.sameInteger(
                            matrix.columnsValue(), other.columnsValue())) {
                throw invalid(activation);
            }
            List<Object> left = matrix.indexedSnapshot();
            List<Object> right = other.indexedSnapshot();
            ProtosPrelude prelude = prelude(activation);
            Object[] sums = new Object[left.size()];
            for (int index = 0; index < sums.length; index++) {
                sums[index] = add(left.get(index), right.get(index), prelude);
            }
            return newMatrix(
                    activation, matrixPrototype, matrix.rowsValue(), matrix.columnsValue(), sums);
        });
        define(matrixPrototype, "scale", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 1);
            Object scalar = requireNumber(activation, supplied.get(0));
            return newMatrix(
                    activation,
                    matrixPrototype,
                    matrix.rowsValue(),
                    matrix.columnsValue(),
                    scaled(matrix.indexedSnapshot(), scalar, prelude(activation)));
        });
        define(matrixPrototype, "matvec", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 1);
            ProtosVectorValue vector = requireVector(activation, supplied.get(0));
            List<Object> operand = vector.indexedSnapshot();
            if (!ProtosNumericValueSupport.sameInteger(
                    matrix.columnsValue(), ProtosNumericValueSupport.integer(operand.size()))) {
                throw invalid(activation);
            }
            int rows =
                    physicalVolume(
                            activation, matrix.rowsValue(), ProtosNumericValueSupport.integer(1));
            int columns = operand.size();
            List<Object> elements = matrix.indexedSnapshot();
            ProtosPrelude prelude = prelude(activation);
            Object[] results = new Object[rows];
            for (int row = 0; row < rows; row++) {
                Object accumulator = null;
                for (int k = 0; k < columns; k++) {
                    Object product =
                            multiply(elements.get(row * columns + k), operand.get(k), prelude);
                    accumulator = accumulate(accumulator, product, prelude);
                }
                results[row] = reduced(accumulator);
            }
            return newVector(vectorPrototype, results);
        });
        define(matrixPrototype, "matmul", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 1);
            ProtosMatrixValue other = requireMatrix(activation, supplied.get(0));
            Object contraction = matrix.columnsValue();
            if (!ProtosNumericValueSupport.sameInteger(contraction, other.rowsValue())) {
                throw invalid(activation);
            }
            Object resultRows = matrix.rowsValue();
            Object resultColumns = other.columnsValue();
            int volume = physicalVolume(activation, resultRows, resultColumns);
            Object[] cells = new Object[volume];
            if (volume > 0) {
                // A positive result volume makes both result dimensions host ints; a positive
                // contraction then gives the left operand a positive host volume as well.
                int rows = ProtosNumericValueSupport.exactInt(resultRows);
                int columns = ProtosNumericValueSupport.exactInt(resultColumns);
                int inner =
                        ProtosNumericValueSupport.integerSignum(contraction) == 0
                                ? 0
                                : ProtosNumericValueSupport.exactInt(contraction);
                if (inner > 0) {
                    double[] leftFloats = matrix.readonlyPackedFloatsForRuntime();
                    double[] rightFloats =
                            leftFloats == null ? null : other.readonlyPackedFloatsForRuntime();
                    if (rightFloats != null) {
                        return newPackedMatrix(
                                activation,
                                matrixPrototype,
                                resultRows,
                                resultColumns,
                                floatMatmul(leftFloats, rightFloats, rows, columns, inner));
                    }
                }
                List<Object> left = matrix.indexedSnapshot();
                List<Object> right = other.indexedSnapshot();
                ProtosPrelude prelude = prelude(activation);
                for (int row = 0; row < rows; row++) {
                    for (int column = 0; column < columns; column++) {
                        Object accumulator = null;
                        for (int k = 0; k < inner; k++) {
                            Object product =
                                    multiply(
                                            left.get(row * inner + k),
                                            right.get(k * columns + column),
                                            prelude);
                            accumulator = accumulate(accumulator, product, prelude);
                        }
                        cells[row * columns + column] = reduced(accumulator);
                    }
                }
            }
            return newMatrix(activation, matrixPrototype, resultRows, resultColumns, cells);
        });
        define(matrixPrototype, "transpose", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 0);
            List<Object> elements = matrix.indexedSnapshot();
            Object[] transposed = new Object[elements.size()];
            if (!elements.isEmpty()) {
                int rows = ProtosNumericValueSupport.exactInt(matrix.rowsValue());
                int columns = ProtosNumericValueSupport.exactInt(matrix.columnsValue());
                for (int row = 0; row < rows; row++) {
                    for (int column = 0; column < columns; column++) {
                        transposed[column * rows + row] = elements.get(row * columns + column);
                    }
                }
            }
            return newMatrix(
                    activation,
                    matrixPrototype,
                    matrix.columnsValue(),
                    matrix.rowsValue(),
                    transposed);
        });
        define(matrixPrototype, "copy", (activation, supplied) -> {
            ProtosMatrixValue matrix = requireMatrix(activation, activation.receiver());
            requireArity(activation, supplied, 0);
            return newMatrix(
                    activation,
                    matrixPrototype,
                    matrix.rowsValue(),
                    matrix.columnsValue(),
                    matrix.indexedSnapshot().toArray());
        });
    }

    private static void define(
            ProtosObjectValue prototype, String selector, ProtosNativeClosureBody body) {
        if (prototype.hasLocalSlot(selector)) {
            throw new IllegalStateException(
                    "standard LinearAlgebra prototype already defines " + selector);
        }
        prototype.createLocalSlot(selector, ProtosClosureValue.nativeClosure(body));
    }

    /* ---- D214-F1 reduction: the first product starts the sum; an empty sum is exact 0. ---- */

    private static Object accumulate(Object accumulator, Object product, ProtosPrelude prelude) {
        return accumulator == null ? product : add(accumulator, product, prelude);
    }

    private static Object reduced(Object accumulator) {
        return accumulator == null ? ProtosNumericValueSupport.integer(0) : accumulator;
    }

    private static Object add(Object left, Object right, ProtosPrelude prelude) {
        return ProtosComplexArithmetic.canonicalNumberBinary(Operation.ADD, left, right, prelude);
    }

    private static Object multiply(Object left, Object right, ProtosPrelude prelude) {
        return ProtosComplexArithmetic.canonicalNumberBinary(
                Operation.MULTIPLY, left, right, prelude);
    }

    /*
     * The all-Float matmul kernel (PLAT068 R2-Float, I124): the generic loop above with each
     * canonical Float product and sum as one host binary64 operation, so every cell is exactly
     * the generic D214-F1 result. The first product seeds the cell, later products are added in
     * ascending k, and nothing is reassociated, fused or split into partial sums.
     */
    private static double[] floatMatmul(
            double[] left, double[] right, int rows, int columns, int inner) {
        double[] cells = new double[rows * columns];
        for (int row = 0; row < rows; row++) {
            int leftRow = row * inner;
            for (int column = 0; column < columns; column++) {
                double accumulator = left[leftRow] * right[column];
                for (int k = 1; k < inner; k++) {
                    double product = left[leftRow + k] * right[k * columns + column];
                    accumulator = accumulator + product;
                }
                cells[row * columns + column] = accumulator;
            }
        }
        return cells;
    }

    private static Object[] scaled(List<Object> elements, Object scalar, ProtosPrelude prelude) {
        Object[] products = new Object[elements.size()];
        for (int index = 0; index < products.length; index++) {
            products[index] = multiply(elements.get(index), scalar, prelude);
        }
        return products;
    }

    /* ---- Results: fresh open objects delegating directly to the standard prototype. ---- */

    private static ProtosVectorValue newVector(
            ProtosObjectValue vectorPrototype, Object[] elements) {
        return new ProtosVectorValue(vectorPrototype, Arrays.asList(elements));
    }

    private static ProtosMatrixValue newMatrix(
            ProtosActivation activation,
            ProtosObjectValue matrixPrototype,
            Object rows,
            Object columns,
            Object[] elements) {
        try {
            return new ProtosMatrixValue(matrixPrototype, rows, columns, Arrays.asList(elements));
        } catch (IllegalArgumentException rejected) {
            throw invalid(activation);
        }
    }

    private static ProtosMatrixValue newPackedMatrix(
            ProtosActivation activation,
            ProtosObjectValue matrixPrototype,
            Object rows,
            Object columns,
            double[] owned) {
        try {
            return ProtosMatrixValue.ownedPackedFloatsForRuntime(
                    matrixPrototype, rows, columns, owned);
        } catch (IllegalArgumentException rejected) {
            throw invalid(activation);
        }
    }

    /* A result shape larger than physical storage is an ordinary failure, never a truncation. */
    private static int physicalVolume(ProtosActivation activation, Object rows, Object columns) {
        try {
            return ProtosMatrixValue.physicalVolume(rows, columns);
        } catch (IllegalArgumentException exceeded) {
            throw invalid(activation);
        }
    }

    /* ---- Validation. ---- */

    private static ProtosObjectValue requireConstructionReceiver(
            ProtosActivation activation, ProtosObjectValue standardPrototype) {
        Object receiver = activation.receiver();
        if (!(receiver instanceof ProtosObjectValue prototype)
                || !delegatesTo(prototype, standardPrototype)) {
            throw invalid(activation);
        }
        return prototype;
    }

    private static boolean delegatesTo(
            ProtosObjectValue receiver, ProtosObjectValue expectedAncestor) {
        Object current = receiver;
        while (current instanceof ProtosObjectValue ordinary) {
            if (ordinary == expectedAncestor) {
                return true;
            }
            current = ordinary.parent().orElse(null);
        }
        return false;
    }

    private static ProtosVectorValue requireVector(ProtosActivation activation, Object value) {
        if (!(value instanceof ProtosVectorValue vector)) {
            throw invalid(activation);
        }
        return vector;
    }

    private static ProtosMatrixValue requireMatrix(ProtosActivation activation, Object value) {
        if (!(value instanceof ProtosMatrixValue matrix)) {
            throw invalid(activation);
        }
        return matrix;
    }

    private static Object requireNumber(ProtosActivation activation, Object value) {
        if (!ProtosNumericValueSupport.isCurrentNumber(value)) {
            throw invalid(activation);
        }
        return value;
    }

    private static void requireArity(ProtosActivation activation, List<?> supplied, int arity) {
        if (supplied.size() != arity) {
            throw invalid(activation);
        }
    }

    private static ProtosPrelude prelude(ProtosActivation activation) {
        return activation.prelude()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "standard LinearAlgebra requires an owning Core prelude"));
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
