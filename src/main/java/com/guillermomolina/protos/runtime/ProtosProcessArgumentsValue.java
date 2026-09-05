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

/**
 * Immutable bootstrap snapshot of one Protos Process's application arguments.
 *
 * <p>The value is not an Array and carries no mutation surface or host authority. Canonical
 * acquisition from one logical Process returns that Process's one stored snapshot object. Ordinary
 * Actor/P value transfer rematerializes a destination snapshot with a fresh semantic identity while
 * immutable String backing may be shared invisibly.
 */
public final class ProtosProcessArgumentsValue implements ProtosRepresentedValue {
    private final ProtosObjectValue prototype;
    private final List<ProtosStringValue> arguments;

    private ProtosProcessArgumentsValue(
            ProtosObjectValue prototype, List<ProtosStringValue> arguments) {
        this.prototype = Objects.requireNonNull(prototype, "prototype");
        this.arguments = List.copyOf(arguments);
    }

    static ProtosProcessArgumentsValue captureForRuntime(
            ProtosObjectValue prototype, List<String> hostArguments) {
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(hostArguments, "hostArguments");

        // First form one detached host snapshot and validate the complete sequence. No
        // Protos String is published or retained before every element is representable.
        List<String> captured = List.copyOf(hostArguments);
        for (String value : captured) {
            if (!isUnicodeScalarString(value)) {
                throw new IllegalArgumentException(
                        "Process argument is not representable as Protos Unicode text");
            }
        }

        ArrayList<ProtosStringValue> converted = new ArrayList<>(captured.size());
        for (String value : captured) {
            converted.add(new ProtosStringValue(value));
        }
        return new ProtosProcessArgumentsValue(prototype, converted);
    }

    public BigInteger indexedSizeForRuntime() {
        return BigInteger.valueOf(arguments.size());
    }

    public ProtosStringValue indexedAtForRuntime(BigInteger index) {
        Objects.requireNonNull(index, "index");
        if (index.signum() < 0
                || index.compareTo(BigInteger.valueOf(arguments.size())) >= 0) {
            throw new IndexOutOfBoundsException("Process argument index outside snapshot");
        }
        return arguments.get(index.intValueExact());
    }

    public List<ProtosStringValue> valuesForRuntime() {
        return arguments;
    }

    /** Ordinary Actor value transfer gives the destination snapshot a fresh semantic identity. */
    ProtosProcessArgumentsValue rematerializeForActorTransfer() {
        return new ProtosProcessArgumentsValue(prototype, arguments);
    }

    /** Ordinary P transfer gives the isolated destination a fresh semantic identity. */
    public ProtosProcessArgumentsValue rematerializeForParallelTransfer() {
        return new ProtosProcessArgumentsValue(prototype, arguments);
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude ignored) {
        return prototype;
    }

    private static boolean isUnicodeScalarString(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }
}
