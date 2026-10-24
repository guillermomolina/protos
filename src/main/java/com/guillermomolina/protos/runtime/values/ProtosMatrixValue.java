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

package com.guillermomolina.protos.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A Standard Library {@code std:math/LinearAlgebra} Matrix (D214 C1/S1, LIB024).
 *
 * <p>An ordinary Protos object with private R1 storage: the exact semantic Integer dimensions
 * and the dense row-major element references, none of which is a slot. The dimensions are kept
 * as supplied, never narrowed to a host width, so a zero-volume shape such as {@code 0 × n} or
 * {@code n × 0} keeps a BigInteger {@code n}. A host index is derived only after the semantic
 * bounds prove that it is physically representable.
 */
public final class ProtosMatrixValue extends ProtosObjectValue {
    private final Object rows;
    private final Object columns;
    private final Object[] elements;

    /**
     * A {@code rows × columns} Matrix of exactly {@code elements} in row-major order. Throws
     * {@link IllegalArgumentException} for a dimension that is not a non-negative Integer, a
     * count other than {@code rows * columns}, or an element that is not a genuine Number.
     */
    public ProtosMatrixValue(Object parent, Object rows, Object columns, List<?> elements) {
        super(parent);
        Objects.requireNonNull(elements, "elements");
        requireDimension(rows);
        requireDimension(columns);
        Object[] owned = elements.toArray();
        if (!hasVolume(rows, columns, owned.length)) {
            throw new IllegalArgumentException("matrix element count does not match its shape");
        }
        for (Object element : owned) {
            ProtosVectorValue.requireNumber(element);
        }
        this.rows = rows;
        this.columns = columns;
        this.elements = owned;
    }

    private ProtosMatrixValue(Object parent, Object rows, Object columns, Object[] owned) {
        super(parent);
        this.rows = rows;
        this.columns = columns;
        this.elements = owned;
    }

    /**
     * A transfer shell of the given exact shape whose elements are genuine Integer {@code 0}
     * placeholders that transfer population replaces before the shell becomes observable.
     */
    public static ProtosMatrixValue transferShellForRuntime(
            Object parent, Object rows, Object columns) {
        requireDimension(rows);
        requireDimension(columns);
        Object[] placeholders = new Object[physicalVolume(rows, columns)];
        Arrays.fill(placeholders, ProtosNumericValueSupport.integer(0));
        return new ProtosMatrixValue(parent, rows, columns, placeholders);
    }

    /**
     * The host element count of a {@code rows × columns} shape of non-negative Integers. Zero
     * when either dimension is zero, whatever the other is; otherwise throws {@link
     * IllegalArgumentException} when the volume exceeds physical storage instead of truncating.
     */
    public static int physicalVolume(Object rows, Object columns) {
        if (ProtosNumericValueSupport.integerSignum(rows) == 0
                || ProtosNumericValueSupport.integerSignum(columns) == 0) {
            return 0;
        }
        if (ProtosNumericValueSupport.isIntegerInIntRange(rows)
                && ProtosNumericValueSupport.isIntegerInIntRange(columns)) {
            long volume =
                    (long) ProtosNumericValueSupport.exactInt(rows)
                            * ProtosNumericValueSupport.exactInt(columns);
            if (volume <= Integer.MAX_VALUE) {
                return (int) volume;
            }
        }
        throw new IllegalArgumentException("matrix volume exceeds physical storage");
    }

    /* Exactly whether rows * columns == count, without forming an overflowing product. */
    private static boolean hasVolume(Object rows, Object columns, int count) {
        if (ProtosNumericValueSupport.integerSignum(rows) == 0
                || ProtosNumericValueSupport.integerSignum(columns) == 0) {
            return count == 0;
        }
        // Both dimensions are positive, so each one exceeding count already proves a mismatch.
        Object physicalCount = ProtosNumericValueSupport.integer(count);
        if (ProtosNumericValueSupport.compareIntegers(rows, physicalCount) > 0
                || ProtosNumericValueSupport.compareIntegers(columns, physicalCount) > 0) {
            return false;
        }
        long volume =
                (long) ProtosNumericValueSupport.exactInt(rows)
                        * ProtosNumericValueSupport.exactInt(columns);
        return volume == count;
    }

    private static void requireDimension(Object dimension) {
        if (!ProtosNumericValueSupport.isCurrentInteger(dimension)
                || ProtosNumericValueSupport.integerSignum(dimension) < 0) {
            throw new IllegalArgumentException("matrix dimension is not a non-negative Integer");
        }
    }

    /** The exact semantic Integer row count. */
    public Object rowsValue() {
        return rows;
    }

    /** The exact semantic Integer column count. */
    public Object columnsValue() {
        return columns;
    }

    /** The element at the semantic Integer position; see {@link #linearIndex} for failures. */
    public Object indexedAt(Object row, Object column) {
        return elements[linearIndex(row, column)];
    }

    /**
     * Replaces one element, validating frozen state, then the row and column bounds, then numeric
     * admission, before any write; a failure leaves the storage unchanged. Answers the value.
     */
    public Object indexedPut(Object row, Object column, Object value) {
        if (isFrozen()) {
            throw new IllegalStateException("matrix is frozen");
        }
        int existing = linearIndex(row, column);
        ProtosVectorValue.requireNumber(value);
        elements[existing] = value;
        return value;
    }

    /** An independent row-major copy of the current elements, unaffected by later writes. */
    public List<Object> indexedSnapshot() {
        return List.of(elements.clone());
    }

    /**
     * Transfer population only: stores a genuine Number or an inert Fraction/Complex transfer
     * descriptor at a row-major position of a detached snapshot; destination materialization
     * replaces it by a Number before guest code can observe the Matrix.
     */
    public void transferPutForRuntime(int index, Object value) {
        Objects.requireNonNull(value, "value");
        if (!ProtosNumericValueSupport.isCurrentNumber(value)
                && !ProtosNumericValueSupport.isFractionTransferDescriptor(value)
                && !ProtosComplexNumericSupport.isTransferDescriptor(value)) {
            throw new IllegalArgumentException("matrix transfer element is not numeric");
        }
        elements[Objects.checkIndex(index, elements.length)] = value;
    }

    /*
     * The row-major host index of a position. Throws IllegalArgumentException for a non-Integer
     * coordinate and IndexOutOfBoundsException for one outside its dimension. Bounds are checked
     * semantically first; a valid position implies a positive volume equal to the host element
     * count, so both dimensions and the linear index are then host-representable.
     */
    private int linearIndex(Object row, Object column) {
        if (!ProtosNumericValueSupport.isCurrentInteger(row)
                || !ProtosNumericValueSupport.isCurrentInteger(column)) {
            throw new IllegalArgumentException("matrix index is not an Integer");
        }
        if (!withinDimension(row, rows)) {
            throw new IndexOutOfBoundsException("matrix row out of bounds");
        }
        if (!withinDimension(column, columns)) {
            throw new IndexOutOfBoundsException("matrix column out of bounds");
        }
        long linear =
                (long) ProtosNumericValueSupport.exactInt(row)
                                * ProtosNumericValueSupport.exactInt(columns)
                        + ProtosNumericValueSupport.exactInt(column);
        return Objects.checkIndex((int) linear, elements.length);
    }

    private static boolean withinDimension(Object index, Object dimension) {
        return ProtosNumericValueSupport.integerSignum(index) >= 0
                && ProtosNumericValueSupport.compareIntegers(index, dimension) < 0;
    }
}
