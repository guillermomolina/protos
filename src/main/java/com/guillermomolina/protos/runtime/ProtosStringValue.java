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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.List;
import java.util.Objects;

@ExportLibrary(InteropLibrary.class)
public final class ProtosStringValue implements ProtosRepresentedValue {
    private final String value;
    private final int scalarCount;

    /**
     * Forms a semantic String from arbitrary host text.
     *
     * <p>This is the validating ingress boundary: the complete host representation is checked for
     * the Unicode-scalar-sequence invariant while its semantic scalar count is derived in the same
     * traversal.
     */
    public ProtosStringValue(String value) {
        Objects.requireNonNull(value, "value");

        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(
                            "String value contains an unpaired high surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(
                        "String value contains an unpaired low surrogate");
            }
            count++;
        }

        this.value = value;
        this.scalarCount = count;
    }

    private ProtosStringValue(String value, int scalarCount) {
        this.value = Objects.requireNonNull(value, "value");
        if (scalarCount < 0) {
            throw new IllegalArgumentException("scalarCount must be non-negative");
        }
        this.scalarCount = scalarCount;
    }

    public String value() {
        return value;
    }

    public int scalarCountForRuntime() {
        return scalarCount;
    }

    public ProtosStringValue copyForRuntime() {
        return new ProtosStringValue(value, scalarCount);
    }

    public ProtosStringValue scalarAtForRuntime(int wanted) {
        if (wanted < 0 || wanted >= scalarCount) {
            return null;
        }

        int index = 0;
        for (int start = 0; start < value.length(); ) {
            int codePoint = value.codePointAt(start);
            int end = start + Character.charCount(codePoint);
            if (index == wanted) {
                return new ProtosStringValue(value.substring(start, end), 1);
            }
            index++;
            start = end;
        }

        throw new IllegalStateException("cached String scalar count disagrees with value");
    }

    public static ProtosStringValue concatenateForRuntime(
            ProtosStringValue left, ProtosStringValue right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");

        String result = left.value + right.value;
        int resultScalarCount = Math.addExact(left.scalarCount, right.scalarCount);
        return new ProtosStringValue(result, resultScalarCount);
    }

    public static ProtosStringValue concatenateAllForRuntime(
            ProtosStringValue receiver, List<?> supplied) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(supplied, "supplied");

        StringBuilder result = new StringBuilder(receiver.value);
        int resultScalarCount = receiver.scalarCount;
        for (Object value : supplied) {
            if (!(value instanceof ProtosStringValue string)) {
                throw new IllegalArgumentException(
                        "String concatenation requires ProtosStringValue arguments");
            }
            result.append(string.value);
            resultScalarCount = Math.addExact(resultScalarCount, string.scalarCount);
        }
        return new ProtosStringValue(result.toString(), resultScalarCount);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude prelude) {
        return ProtosRepresentedValue.requirePrelude(prelude, "String").stringPrototype();
    }

    @ExportMessage
    boolean isString() {
        return true;
    }

    @ExportMessage
    String asString() {
        return value;
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return value;
    }
}
