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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessArgumentsValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.math.BigInteger;
import java.util.List;

/**
 * Representation bridge for the immutable Process-argument snapshot protocol.
 *
 * <p>The returned prototype is construction-only implementation state, not a named Core-prelude
 * binding. The normative snapshot itself is runtime-backed; these three native selectors are the
 * minimal bridge needed to observe its immutable indexed representation and perform eager
 * polymorphic callback validation.
 */
public final class ProtosStandardProcessArgumentsProtocol {
    private ProtosStandardProcessArgumentsProtocol() {}

    public static ProtosObjectValue createPrototype() {
        ProtosObjectValue prototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());

        prototype.createLocalSlot(
                "size",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosProcessArgumentsValue arguments =
                                    requireReceiver(activation);
                            if (!supplied.isEmpty()) {
                                throw error(activation);
                            }
                            return new ProtosIntegerValue(
                                    arguments.indexedSizeForRuntime());
                        }));

        prototype.createLocalSlot(
                "at",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosProcessArgumentsValue arguments =
                                    requireReceiver(activation);
                            if (supplied.size() != 1
                                    || !(supplied.get(0)
                                            instanceof ProtosIntegerValue index)) {
                                throw error(activation);
                            }
                            BigInteger value = index.value();
                            if (value.signum() < 0
                                    || value.compareTo(
                                                    arguments.indexedSizeForRuntime())
                                            >= 0) {
                                throw error(activation);
                            }
                            return arguments.indexedAtForRuntime(value);
                        }));

        prototype.createLocalSlot(
                "each",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosProcessArgumentsValue arguments =
                                    requireReceiver(activation);
                            if (supplied.size() != 1) {
                                throw error(activation);
                            }
                            Object block = supplied.get(0);
                            requireInvokable(block, activation);
                            for (Object argument : arguments.valuesForRuntime()) {
                                ProtosInvocation.invoke(
                                        block, List.of(argument), activation);
                            }
                            return arguments;
                        }));

        return prototype.freeze();
    }

    private static ProtosProcessArgumentsValue requireReceiver(
            ProtosActivation activation) {
        if (!(activation.receiver()
                instanceof ProtosProcessArgumentsValue arguments)) {
            throw error(activation);
        }
        return arguments;
    }

    private static void requireInvokable(
            Object candidate, ProtosActivation activation) {
        var prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Process arguments each requires an owning Core prelude"));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(candidate, "call", prelude)
                            .orElseThrow(() -> error(activation));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw error(activation);
        }
        if (!(selected.value() instanceof ProtosClosureValue)) {
            throw error(activation);
        }
    }

    private static ProtosSignalException error(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
