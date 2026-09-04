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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProtosArrayValue extends ProtosObjectValue {
    private final List<Object> elements;

    public ProtosArrayValue(Object parent, List<?> elements) {
        super(parent);
        Objects.requireNonNull(elements, "elements");
        this.elements = new ArrayList<>(elements.size());
        for (Object element : elements) {
            this.elements.add(Objects.requireNonNull(element, "element"));
        }
    }

    public BigInteger indexedSize() {
        return BigInteger.valueOf(elements.size());
    }

    public Object indexedAt(BigInteger index) {
        return elements.get(requireExistingIndex(index));
    }

    public Object indexedPut(BigInteger index, Object value) {
        Objects.requireNonNull(value, "value");
        if (isFrozen()) {
            throw new IllegalStateException("array is frozen");
        }

        elements.set(requireExistingIndex(index), value);
        return value;
    }

    public List<Object> indexedSnapshot() {
        return List.copyOf(elements);
    }

    private int requireExistingIndex(BigInteger index) {
        Objects.requireNonNull(index, "index");
        if (index.signum() < 0
                || index.compareTo(BigInteger.valueOf(elements.size())) >= 0) {
            throw new IndexOutOfBoundsException("array index out of bounds: " + index);
        }
        return index.intValueExact();
    }
}
