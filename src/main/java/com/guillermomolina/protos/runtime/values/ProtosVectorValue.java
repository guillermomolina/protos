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
 * A Standard Library {@code std:math/linear_algebra} Vector (D214 C1/S1, LIB024).
 *
 * <p>An ordinary Protos object whose dense, fixed-length numeric payload is private storage: it is
 * not a slot, so reflection and slot copying never see it. Every stored element is a genuine Core
 * Number, admitted by {@link ProtosNumericValueSupport#isCurrentNumber} without conversion or
 * guest code. No operation here changes the length.
 *
 * <p>The storage is exactly one of a {@code long[]} of signed-64 Integers, a {@code double[]} of
 * Floats, or an {@code Object[]} of element references for any other or mixed content. Writing a
 * value its primitive array cannot hold exactly first generalizes to element references. The
 * active array is invisible: reads reify semantic Numbers and snapshots are independent.
 */
public final class ProtosVectorValue extends ProtosObjectValue {
    /* A long[], a double[] or an Object[]; never escapes. */
    private Object storage;

    /**
     * A Vector holding exactly {@code elements}, in order. Throws {@link IllegalArgumentException}
     * when any element is not a genuine Number; the sequence is copied, never retained.
     */
    public ProtosVectorValue(Object parent, List<?> elements) {
        super(parent);
        Objects.requireNonNull(elements, "elements");
        Object[] owned = elements.toArray();
        for (Object element : owned) {
            requireNumber(element);
        }
        this.storage = specializedStorage(owned);
    }

    private ProtosVectorValue(Object parent, Object[] owned) {
        super(parent);
        this.storage = owned;
    }

    /**
     * A transfer shell of {@code size} elements, each a genuine Integer {@code 0} placeholder that
     * transfer population replaces before the shell becomes observable. Its storage is element
     * references so population may hold transfer descriptors.
     */
    public static ProtosVectorValue transferShellForRuntime(Object parent, int size) {
        Object[] placeholders = new Object[size];
        Arrays.fill(placeholders, ProtosNumericValueSupport.integer(0));
        return new ProtosVectorValue(parent, placeholders);
    }

    public int indexedSize() {
        return physicalLength(storage);
    }

    /**
     * The element at the semantic Integer {@code index}. Throws {@link IllegalArgumentException}
     * for a non-Integer and {@link IndexOutOfBoundsException} for an Integer outside the Vector.
     */
    public Object indexedAt(Object index) {
        Object current = storage;
        int existing = existingIndex(index, current);
        if (current instanceof long[] integers) {
            return ProtosNumericValueSupport.integer(integers[existing]);
        }
        if (current instanceof double[] floats) {
            return ProtosNumericValueSupport.floating(floats[existing]);
        }
        return ((Object[]) current)[existing];
    }

    /**
     * Replaces one element, validating frozen state, then bounds, then numeric admission, before
     * any write; a failure leaves the storage unchanged. A value the active primitive array cannot
     * hold exactly first generalizes the storage to element references. Answers the assigned
     * value.
     */
    public Object indexedPut(Object index, Object value) {
        if (isFrozen()) {
            throw new IllegalStateException("vector is frozen");
        }
        Object current = storage;
        int existing = existingIndex(index, current);
        requireNumber(value);
        if (current instanceof long[] integers) {
            if (value instanceof Long integer) {
                integers[existing] = integer.longValue();
                return value;
            }
        } else if (current instanceof double[] floats) {
            if (ProtosNumericValueSupport.isCurrentFloat(value)) {
                floats[existing] = ProtosNumericValueSupport.currentFloatValue(value);
                return value;
            }
        } else {
            ((Object[]) current)[existing] = value;
            return value;
        }
        Object[] generalized = references(current);
        generalized[existing] = value;
        storage = generalized;
        return value;
    }

    /** An independent copy of the current elements, unaffected by later writes. */
    public List<Object> indexedSnapshot() {
        Object current = storage;
        return List.of(current instanceof Object[] elements ? elements.clone() : references(current));
    }

    /**
     * Transfer population only: stores a genuine Number or an inert Fraction/Complex transfer
     * descriptor of a detached snapshot, which destination materialization replaces by a Number
     * before guest code can observe the Vector. Transfer shells hold element references.
     */
    public void transferPutForRuntime(int index, Object value) {
        Objects.requireNonNull(value, "value");
        if (!ProtosNumericValueSupport.isCurrentNumber(value)
                && !ProtosNumericValueSupport.isFractionTransferDescriptor(value)
                && !ProtosComplexNumericSupport.isTransferDescriptor(value)) {
            throw new IllegalArgumentException("vector transfer element is not numeric");
        }
        if (!(storage instanceof Object[] elements)) {
            throw new IllegalStateException("vector transfer population requires a transfer shell");
        }
        elements[Objects.checkIndex(index, elements.length)] = value;
    }

    /**
     * Transfer population only, once every element of a transfer shell is in place: replaces
     * element references by the primitive storage their Number family admits, in this same
     * Vector. References that hold a rich, mixed or not yet materialized element stay as they
     * are. No element, identity, slot or state changes.
     */
    public void completeTransferForRuntime() {
        if (storage instanceof Object[] elements) {
            storage = specializedStorage(elements);
        }
    }

    /*
     * The storage for validated Numbers: a long[] when every element is a signed-64 Integer, a
     * double[] when every element is a Float, otherwise the references themselves.
     */
    private static Object specializedStorage(Object[] owned) {
        if (owned.length == 0) {
            return owned;
        }
        boolean allIntegers = true;
        boolean allFloats = true;
        for (Object element : owned) {
            allIntegers &= element instanceof Long;
            allFloats &= ProtosNumericValueSupport.isCurrentFloat(element);
        }
        if (allIntegers) {
            long[] integers = new long[owned.length];
            for (int index = 0; index < integers.length; index++) {
                integers[index] = (Long) owned[index];
            }
            return integers;
        }
        if (allFloats) {
            double[] floats = new double[owned.length];
            for (int index = 0; index < floats.length; index++) {
                floats[index] = ProtosNumericValueSupport.currentFloatValue(owned[index]);
            }
            return floats;
        }
        return owned;
    }

    /* Fresh element references reifying every lane of a primitive storage. */
    private static Object[] references(Object current) {
        if (current instanceof long[] integers) {
            Object[] result = new Object[integers.length];
            for (int index = 0; index < result.length; index++) {
                result[index] = ProtosNumericValueSupport.integer(integers[index]);
            }
            return result;
        }
        if (current instanceof double[] floats) {
            Object[] result = new Object[floats.length];
            for (int index = 0; index < result.length; index++) {
                result[index] = ProtosNumericValueSupport.floating(floats[index]);
            }
            return result;
        }
        return ((Object[]) current).clone();
    }

    private static int physicalLength(Object current) {
        if (current instanceof long[] integers) {
            return integers.length;
        }
        if (current instanceof double[] floats) {
            return floats.length;
        }
        return ((Object[]) current).length;
    }

    private static int existingIndex(Object index, Object current) {
        if (!ProtosNumericValueSupport.isCurrentInteger(index)) {
            throw new IllegalArgumentException("vector index is not an Integer");
        }
        // The length is a host int, so an Integer outside the int range is out of bounds.
        if (!ProtosNumericValueSupport.isIntegerInIntRange(index)) {
            throw new IndexOutOfBoundsException("vector index out of bounds");
        }
        int exact = ProtosNumericValueSupport.exactInt(index);
        if (exact < 0 || exact >= physicalLength(current)) {
            throw new IndexOutOfBoundsException("vector index out of bounds: " + exact);
        }
        return exact;
    }

    static void requireNumber(Object value) {
        if (!ProtosNumericValueSupport.isCurrentNumber(value)) {
            throw new IllegalArgumentException("element is not a genuine Number");
        }
    }
}
