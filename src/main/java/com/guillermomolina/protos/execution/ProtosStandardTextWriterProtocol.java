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
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosTextWriter;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.util.List;
import java.util.Objects;

/** Standard frozen TextWriter factory and per-wrapper text-output/lifecycle surface. */
public final class ProtosStandardTextWriterProtocol {
    private ProtosStandardTextWriterProtocol() {}

    public static ProtosObjectValue installFactory(
            ProtosObjectValue factory, ProtosActivation bootstrap) {
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(bootstrap, "bootstrap");
        if (factory.parent().orElse(null) != ProtosObjectValue.rootObject()) {
            throw new IllegalArgumentException(
                    "Core TextWriter factory must delegate directly to Object");
        }
        if (!factory.isOpen() || !factory.localSlotsSnapshot().isEmpty()) {
            throw new IllegalArgumentException(
                    "source-created Core TextWriter factory must begin open and without local slots");
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
            ProtosActivation activation,
            List<?> supplied,
            boolean owning) {
        if (supplied.size() != 2) {
            throw invalidConstruction(activation);
        }

        Object target = supplied.get(0);
        Object encodingValue = supplied.get(1);
        if (!(encodingValue instanceof ProtosEncodingValue encoding)
                || !hasCallableCapability(target, "write", activation)
                || (owning
                        && !hasCallableCapability(target, "close", activation))) {
            throw invalidConstruction(activation);
        }

        boolean targetFlushable =
                hasCallableCapability(target, "flush", activation);
        ProtosObjectValue wrapper =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosTextWriter writer =
                new ProtosTextWriter(
                        wrapper,
                        target,
                        encoding,
                        targetFlushable,
                        owning,
                        activation);

        wrapper.createLocalSlot(
                "writeText",
                operationClosure(writer, wrapper, Operation.WRITE_TEXT));
        wrapper.createLocalSlot(
                "writeLine",
                operationClosure(writer, wrapper, Operation.WRITE_LINE));
        wrapper.createLocalSlot(
                "flush",
                operationClosure(writer, wrapper, Operation.FLUSH));
        wrapper.createLocalSlot(
                "close",
                operationClosure(writer, wrapper, Operation.CLOSE));
        return wrapper;
    }

    private enum Operation {
        WRITE_TEXT,
        WRITE_LINE,
        FLUSH,
        CLOSE
    }

    private static ProtosClosureValue operationClosure(
            ProtosTextWriter writer,
            ProtosObjectValue wrapper,
            Operation operation) {
        return ProtosClosureValue.nativeClosure(
                (activation, supplied) -> {
                    if (activation.receiver() != wrapper) {
                        return invalidFuture(activation);
                    }
                    return switch (operation) {
                        case WRITE_TEXT, WRITE_LINE -> {
                            if (supplied.size() != 1
                                    || !(supplied.get(0)
                                            instanceof ProtosStringValue text)) {
                                yield invalidFuture(activation);
                            }
                            yield writer.writeText(
                                    activation,
                                    text.value(),
                                    operation == Operation.WRITE_LINE);
                        }
                        case FLUSH ->
                                supplied.isEmpty()
                                        ? writer.flush(activation)
                                        : invalidFuture(activation);
                        case CLOSE ->
                                supplied.isEmpty()
                                        ? writer.close(activation)
                                        : invalidFuture(activation);
                    };
                });
    }

    private static boolean hasCallableCapability(
            Object receiver,
            String selector,
            ProtosActivation activation) {
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
