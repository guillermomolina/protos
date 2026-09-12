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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosNativeClosureBody;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

public final class ProtosStandardArrayProtocol {
    private static final ProtosNativeClosureBody STANDARD_EACH_BODY =
            ProtosStandardArrayProtocol::each;
    private static final ProtosClosureValue STANDARD_EACH =
            ProtosClosureValue.nativeClosure(STANDARD_EACH_BODY);

    private ProtosStandardArrayProtocol() {}

    static boolean isStandardEachImplementation(ProtosClosureValue closure) {
        return closure.nativeBody().orElse(null) == STANDARD_EACH_BODY;
    }

    static boolean isCanonicalStandardEachSelection(
            ProtosClosureValue behavior,
            ProtosObjectValue home,
            ProtosActivation caller) {
        return behavior == STANDARD_EACH
                && caller.prelude()
                        .map(prelude -> prelude.arrayPrototype() == home)
                        .orElse(false);
    }

    public static void install(ProtosObjectValue arrayPrototype) {
        Objects.requireNonNull(arrayPrototype, "arrayPrototype");

        if (arrayPrototype.hasLocalSlot("call")) {
            throw new IllegalStateException("Core Array already defines a local call slot");
        }

        arrayPrototype.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            Object receiver = activation.receiver();
                            if (!(receiver instanceof ProtosObjectValue prototype)
                                    || !delegatesTo(prototype, arrayPrototype)) {
                                throw invalid(activation);
                            }
                            return new ProtosArrayValue(prototype, supplied);
                        }));

        arrayPrototype.createLocalSlot(
                "at",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosArrayValue array = requireArrayReceiver(activation);
                            if (supplied.size() != 1) {
                                throw invalid(activation);
                            }
                            BigInteger value = requireInteger(activation, supplied.get(0));
                            if (value.signum() < 0
                                    || value.compareTo(array.indexedSize()) >= 0) {
                                throw invalid(activation);
                            }
                            return array.indexedAt(value);
                        }));

        arrayPrototype.createLocalSlot(
                "atPut",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosArrayValue array = requireArrayReceiver(activation);
                            if (array.isFrozen()) {
                                throw invalid(activation);
                            }
                            if (supplied.size() != 2) {
                                throw invalid(activation);
                            }
                            BigInteger value = requireInteger(activation, supplied.get(0));
                            if (value.signum() < 0
                                    || value.compareTo(array.indexedSize()) >= 0) {
                                throw invalid(activation);
                            }
                            return array.indexedPut(value, supplied.get(1));
                        }));

        arrayPrototype.createLocalSlot(
                "size",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosArrayValue array = requireArrayReceiver(activation);
                            if (!supplied.isEmpty()) {
                                throw invalid(activation);
                            }
                            return new ProtosIntegerValue(array.indexedSize());
                        }));

        arrayPrototype.createLocalSlot("each", STANDARD_EACH);
    }

    private static Object each(ProtosActivation activation, List<?> supplied) {
        ProtosArrayValue array = requireArrayReceiver(activation);
        if (supplied.size() != 1) {
            throw invalid(activation);
        }
        Object block = supplied.get(0);
        requireInvokableForStructured(block, activation);
        List<Object> snapshot = array.indexedSnapshot();
        for (Object element : snapshot) {
            ProtosInvocation.invoke(block, List.of(element), activation);
        }
        return array;
    }

    static void requireInvokableForStructured(
            Object candidate,
            ProtosActivation activation) {
        com.guillermomolina.protos.runtime.ProtosPrelude prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "standard Array.each requires an owning Core prelude"));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(candidate, "call", prelude)
                            .orElseThrow(() -> invalid(activation));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw invalid(activation);
        }
        if (!(selected.value() instanceof ProtosClosureValue)) {
            throw invalid(activation);
        }
    }

    private static BigInteger requireInteger(
            ProtosActivation activation,
            Object value) {
        if (value instanceof ProtosIntegerValue integer) {
            return integer.value();
        }
        if (value instanceof ProtosFixedIntegerValue integer) {
            return integer.value();
        }
        throw invalid(activation);
    }

    private static ProtosArrayValue requireArrayReceiver(
            ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosArrayValue array)) {
            throw invalid(activation);
        }
        return array;
    }

    private static boolean delegatesTo(
            ProtosObjectValue receiver,
            ProtosObjectValue expectedAncestor) {
        Object current = receiver;
        while (current instanceof ProtosObjectValue ordinary) {
            if (ordinary == expectedAncestor) {
                return true;
            }
            current = ordinary.parent().orElse(null);
        }
        return false;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
