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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A Standard Library {@code std:math/LinearAlgebra} Matrix (D214 C1/S1, LIB024).
 *
 * <p>An ordinary Protos object with private storage: the exact semantic Integer dimensions and
 * the dense row-major elements, none of which is a slot. The dimensions are kept as supplied,
 * never narrowed to a host width, so a zero-volume shape such as {@code 0 × n} or {@code n × 0}
 * keeps a BigInteger {@code n}. A host index is derived only after the semantic bounds prove that
 * it is physically representable.
 *
 * <p>The elements live in exactly one of two mutually exclusive private states (PLAT068 R2-Float,
 * I124): R1, the element references, and R2, the binary64 values of an all-Float Matrix. Every
 * Matrix is constructed in R1 except a Float kernel result, which is born in R2. An R1 Matrix
 * whose elements are all Floats enters R2 when a Float kernel first asks for its packed values,
 * and stays there until a non-Float write returns it to R1; a frozen Matrix packs persistently
 * too, since packing changes no element. The state is invisible: reads reify semantic Floats and
 * snapshots are independent, so no guest operation observes which is active.
 *
 * <p>The state is one volatile reference, either an {@code Object[]} (R1) or a {@code double[]}
 * (R2), so a transition is a single atomic store that safely publishes the completely filled
 * array. Readers load it once per operation. Packing installs its array by compare-and-set, so
 * concurrent first readers of a frozen Matrix agree on one persistent packing. Writes stay
 * confined to the owning Actor, as for every mutable object.
 */
public final class ProtosMatrixValue extends ProtosObjectValue {
    private final Object rows;
    private final Object columns;
    private static final VarHandle STORAGE;

    static {
        try {
            STORAGE =
                    MethodHandles.lookup()
                            .findVarHandle(ProtosMatrixValue.class, "storage", Object.class);
        } catch (ReflectiveOperationException unavailable) {
            throw new ExceptionInInitializerError(unavailable);
        }
    }

    /*
     * R1 element references (Object[]) or R2 binary64 values (double[]). An R2 array never
     * escapes except as trusted read-only kernel input.
     */
    private volatile Object storage;
    /*
     * R1 only: a cached proof that some element is not a Float; any write clears it. A lost or
     * stale cache only costs a rescan, so it needs no ordering of its own.
     */
    private boolean knownNotAllFloat;

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
        this.storage = owned;
    }

    private ProtosMatrixValue(Object parent, Object rows, Object columns, Object[] owned) {
        super(parent);
        this.rows = rows;
        this.columns = columns;
        this.storage = owned;
    }

    private ProtosMatrixValue(Object parent, Object rows, Object columns, double[] owned) {
        super(parent);
        this.rows = rows;
        this.columns = columns;
        this.storage = owned;
    }

    /**
     * Float kernel results only: a fresh Matrix born in R2 that takes ownership of {@code
     * packed}, its row-major binary64 elements. The caller must hold the only reference to {@code
     * packed} and drop it. Throws {@link IllegalArgumentException} for a dimension that is not a
     * non-negative Integer or a length other than {@code rows * columns}.
     */
    public static ProtosMatrixValue ownedPackedFloatsForRuntime(
            Object parent, Object rows, Object columns, double[] packed) {
        Objects.requireNonNull(packed, "packed");
        requireDimension(rows);
        requireDimension(columns);
        if (!hasVolume(rows, columns, packed.length)) {
            throw new IllegalArgumentException("matrix element count does not match its shape");
        }
        return new ProtosMatrixValue(parent, rows, columns, packed);
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
        Object current = storage;
        int index = linearIndex(row, column, current);
        return current instanceof double[] packed
                ? new ProtosFloatValue(packed[index])
                : ((Object[]) current)[index];
    }

    /**
     * Replaces one element, validating frozen state, then the row and column bounds, then numeric
     * admission, before any write; a failure leaves the storage unchanged. A Float written in R2
     * replaces its lane; any other Number written in R2 first returns the Matrix to R1, keeping
     * every other element as a Float. Answers the value.
     */
    public Object indexedPut(Object row, Object column, Object value) {
        if (isFrozen()) {
            throw new IllegalStateException("matrix is frozen");
        }
        Object current = storage;
        int existing = linearIndex(row, column, current);
        ProtosVectorValue.requireNumber(value);
        if (current instanceof double[] packed) {
            if (value instanceof ProtosFloatValue floating) {
                packed[existing] = floating.value();
                return value;
            }
            Object[] unpacked = unpackedFloats(packed);
            unpacked[existing] = value;
            knownNotAllFloat = true;
            storage = unpacked;
            return value;
        }
        ((Object[]) current)[existing] = value;
        knownNotAllFloat = false;
        return value;
    }

    /** An independent row-major copy of the current elements, unaffected by later writes. */
    public List<Object> indexedSnapshot() {
        Object current = storage;
        return List.of(
                current instanceof double[] packed
                        ? unpackedFloats(packed)
                        : ((Object[]) current).clone());
    }

    /**
     * Float kernels only: the row-major binary64 elements when every element is a genuine Float,
     * else {@code null}; nothing is ever coerced. The answer is the persistent R2 storage itself,
     * trusted read-only kernel input that must not be written, retained or published; it is
     * public only because the kernels live in another package, and no guest operation reaches
     * it. An R1 Matrix of Floats, frozen or not, enters R2 here exactly once, so later kernels
     * reuse the same values without rescanning.
     */
    public double[] readonlyPackedFloatsForRuntime() {
        Object current = storage;
        if (current instanceof double[] packed) {
            return packed;
        }
        if (knownNotAllFloat) {
            return null;
        }
        Object[] references = (Object[]) current;
        double[] packed = new double[references.length];
        for (int index = 0; index < packed.length; index++) {
            if (!(references[index] instanceof ProtosFloatValue floating)) {
                knownNotAllFloat = true;
                return null;
            }
            packed[index] = floating.value();
        }
        // A concurrent first reader of a frozen Matrix may have packed it already; keep its array.
        Object witness = STORAGE.compareAndExchange(this, current, packed);
        return witness == current ? packed : (double[]) witness;
    }

    /**
     * Transfer population only: stores a genuine Number or an inert Fraction/Complex transfer
     * descriptor at a row-major position of a detached snapshot; destination materialization
     * replaces it by a Number before guest code can observe the Matrix. Transfer shells are R1.
     */
    public void transferPutForRuntime(int index, Object value) {
        Objects.requireNonNull(value, "value");
        if (!ProtosNumericValueSupport.isCurrentNumber(value)
                && !ProtosNumericValueSupport.isFractionTransferDescriptor(value)
                && !ProtosComplexNumericSupport.isTransferDescriptor(value)) {
            throw new IllegalArgumentException("matrix transfer element is not numeric");
        }
        if (!(storage instanceof Object[] references)) {
            throw new IllegalStateException("matrix transfer population requires a transfer shell");
        }
        references[Objects.checkIndex(index, references.length)] = value;
        knownNotAllFloat = false;
    }

    /* Fresh R1 references reifying every R2 lane as a genuine Float. */
    private static Object[] unpackedFloats(double[] packed) {
        Object[] unpacked = new Object[packed.length];
        for (int index = 0; index < unpacked.length; index++) {
            unpacked[index] = new ProtosFloatValue(packed[index]);
        }
        return unpacked;
    }

    private static int physicalLength(Object current) {
        return current instanceof double[] packed ? packed.length : ((Object[]) current).length;
    }

    /*
     * The row-major host index of a position. Throws IllegalArgumentException for a non-Integer
     * coordinate and IndexOutOfBoundsException for one outside its dimension. Bounds are checked
     * semantically first; a valid position implies a positive volume equal to the host element
     * count, so both dimensions and the linear index are then host-representable.
     */
    private int linearIndex(Object row, Object column, Object current) {
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
        return Objects.checkIndex((int) linear, physicalLength(current));
    }

    private static boolean withinDimension(Object index, Object dimension) {
        return ProtosNumericValueSupport.integerSignum(index) >= 0
                && ProtosNumericValueSupport.compareIntegers(index, dimension) < 0;
    }
}
