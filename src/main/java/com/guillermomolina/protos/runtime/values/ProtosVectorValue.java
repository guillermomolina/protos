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
 * A Standard Library {@code std:math/LinearAlgebra} Vector (D214 C1/S1, LIB024).
 *
 * <p>An ordinary Protos object whose dense, fixed-length numeric payload is private R1 storage of
 * element references: it is not a slot, so reflection and slot copying never see it. Every
 * stored element is a genuine Core Number, admitted by {@link
 * ProtosNumericValueSupport#isCurrentNumber} without conversion or guest code. No operation here
 * changes the length.
 */
public final class ProtosVectorValue extends ProtosObjectValue {
    private final Object[] elements;

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
        this.elements = owned;
    }

    private ProtosVectorValue(Object parent, Object[] owned) {
        super(parent);
        this.elements = owned;
    }

    /**
     * A transfer shell of {@code size} elements, each a genuine Integer {@code 0} placeholder that
     * transfer population replaces before the shell becomes observable.
     */
    public static ProtosVectorValue transferShellForRuntime(Object parent, int size) {
        Object[] placeholders = new Object[size];
        Arrays.fill(placeholders, ProtosNumericValueSupport.integer(0));
        return new ProtosVectorValue(parent, placeholders);
    }

    public int indexedSize() {
        return elements.length;
    }

    /**
     * The element at the semantic Integer {@code index}. Throws {@link IllegalArgumentException}
     * for a non-Integer and {@link IndexOutOfBoundsException} for an Integer outside the Vector.
     */
    public Object indexedAt(Object index) {
        return elements[existingIndex(index)];
    }

    /**
     * Replaces one element, validating frozen state, then bounds, then numeric admission, before
     * any write; a failure leaves the storage unchanged. Answers the assigned value.
     */
    public Object indexedPut(Object index, Object value) {
        if (isFrozen()) {
            throw new IllegalStateException("vector is frozen");
        }
        int existing = existingIndex(index);
        requireNumber(value);
        elements[existing] = value;
        return value;
    }

    /** An independent copy of the current elements, unaffected by later writes. */
    public List<Object> indexedSnapshot() {
        return List.of(elements.clone());
    }

    /**
     * Transfer population only: stores a genuine Number or an inert Fraction/Complex transfer
     * descriptor of a detached snapshot, which destination materialization replaces by a Number
     * before guest code can observe the Vector.
     */
    public void transferPutForRuntime(int index, Object value) {
        Objects.requireNonNull(value, "value");
        if (!ProtosNumericValueSupport.isCurrentNumber(value)
                && !ProtosNumericValueSupport.isFractionTransferDescriptor(value)
                && !ProtosComplexNumericSupport.isTransferDescriptor(value)) {
            throw new IllegalArgumentException("vector transfer element is not numeric");
        }
        elements[Objects.checkIndex(index, elements.length)] = value;
    }

    private int existingIndex(Object index) {
        if (!ProtosNumericValueSupport.isCurrentInteger(index)) {
            throw new IllegalArgumentException("vector index is not an Integer");
        }
        // The length is a host int, so an Integer outside the int range is out of bounds.
        if (!ProtosNumericValueSupport.isIntegerInIntRange(index)) {
            throw new IndexOutOfBoundsException("vector index out of bounds");
        }
        int exact = ProtosNumericValueSupport.exactInt(index);
        if (exact < 0 || exact >= elements.length) {
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
