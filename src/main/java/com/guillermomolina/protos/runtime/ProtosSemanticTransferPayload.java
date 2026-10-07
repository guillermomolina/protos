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
import java.util.Collections;
import java.util.List;

/**
 * Inert, immutable semantic payload of a {@link ProtosSemanticTransferFamily} value (PLAT051).
 *
 * <p>A payload is an ordered acyclic tree whose leaves are host scalars only: {@link String},
 * {@link BigInteger}, {@link Boolean} and {@link Double}; inner nodes are nested payloads. It can
 * hold no Protos runtime value, so no Closure, prototype, execution state or authority can cross
 * through it, and a future Process transport could encode the same logical content. It defines
 * no wire format.
 */
public final class ProtosSemanticTransferPayload {
    private final List<Object> elements;

    private ProtosSemanticTransferPayload(List<Object> elements) {
        this.elements = elements;
    }

    /** Builds a payload, rejecting any element that is not an inert leaf or nested payload. */
    public static ProtosSemanticTransferPayload of(Object... elements) {
        ArrayList<Object> copy = new ArrayList<>(elements.length);
        for (Object element : elements) {
            if (!(element instanceof String
                    || element instanceof BigInteger
                    || element instanceof Boolean
                    || element instanceof Double
                    || element instanceof ProtosSemanticTransferPayload)) {
                throw new IllegalArgumentException("semantic transfer payload element is not inert");
            }
            copy.add(element);
        }
        return new ProtosSemanticTransferPayload(Collections.unmodifiableList(copy));
    }

    public int size() {
        return elements.size();
    }

    public Object get(int index) {
        return elements.get(index);
    }
}
