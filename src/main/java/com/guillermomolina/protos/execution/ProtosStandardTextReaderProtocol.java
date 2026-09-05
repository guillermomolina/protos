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
import com.guillermomolina.protos.runtime.ProtosEncodingValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosTextReader;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.util.List;
import java.util.Objects;

/** Standard frozen TextReader factory plus per-wrapper readText/close capability surface. */
public final class ProtosStandardTextReaderProtocol {
    private ProtosStandardTextReaderProtocol() {}

    public static ProtosObjectValue installFactory(
            ProtosObjectValue factory, ProtosActivation bootstrap) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(bootstrap, "bootstrap");
        if (factory.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "Core TextReader factory must delegate directly to Object");
        }
        if (!factory.isOpen() || !factory.localSlotsSnapshot().isEmpty()) {
            throw new IllegalArgumentException(
                    "source-created Core TextReader factory must begin open and without local slots");
        }

        factory.createLocalSlot("call", factoryClosure(false));
        factory.createLocalSlot("owning", factoryClosure(true));
        return factory.freeze();
    }

    private static ProtosClosureValue factoryClosure(boolean owning) {
        return ProtosClosureValue.nativeClosure(
                (activation, supplied) ->
                        construct(activation, supplied, owning));
    }

    private static Object construct(
            ProtosActivation activation, List<?> supplied, boolean owning) {
        if (supplied.size() != 2) {
            throw invalidConstruction(activation);
        }

        Object source = supplied.get(0);
        Object encodingValue = supplied.get(1);
        if (!(encodingValue instanceof ProtosEncodingValue encoding)
                || !encoding.isPortableForRuntime()
                || !hasCallableCapability(source, "read", activation)
                || (owning && !hasCallableCapability(source, "close", activation))) {
            throw invalidConstruction(activation);
        }

        ProtosObjectValue wrapper =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosTextReader reader =
                new ProtosTextReader(
                        wrapper, source, encoding, activation, owning);

        wrapper.createLocalSlot(
                "readText",
                operationClosure(reader, wrapper, false));
        wrapper.createLocalSlot(
                "close",
                operationClosure(reader, wrapper, true));
        return wrapper;
    }

    private static ProtosClosureValue operationClosure(
            ProtosTextReader reader,
            ProtosObjectValue wrapper,
            boolean close) {
        return ProtosClosureValue.nativeClosure(
                (activation, supplied) -> {
                    if (activation.receiver() != wrapper
                            || !supplied.isEmpty()) {
                        return invalidFuture(activation);
                    }
                    return close
                            ? reader.close(activation)
                            : reader.readText(activation);
                });
    }

    private static boolean hasCallableCapability(
            Object receiver, String selector, ProtosActivation activation) {
        try {
            return ProtosValueLookup.lookup(
                            receiver,
                            selector,
                            activation.prelude().orElseThrow())
                    .filter(result -> result.value() instanceof ProtosClosureValue)
                    .isPresent();
        } catch (UnsupportedOperationException unsupported) {
            return false;
        }
    }

    private static ProtosSignalException invalidConstruction(
            ProtosActivation activation) {
        return new ProtosSignalException(
                ProtosCoreErrors.newOccurrence(
                        activation,
                        ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT));
    }

    private static ProtosFutureValue invalidFuture(
            ProtosActivation activation) {
        ProtosFutureValue future =
                new ProtosFutureValue(
                        activation.prelude().orElseThrow().futurePrototype(),
                        activation.executionDomain());
        future.fail(
                ProtosCoreErrors.newOccurrence(
                        activation,
                        ProtosCoreErrors.StandardError.INVALID_I_O_ARGUMENT));
        return future;
    }
}
