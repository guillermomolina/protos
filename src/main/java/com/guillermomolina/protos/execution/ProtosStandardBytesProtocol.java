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
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

public final class ProtosStandardBytesProtocol {
    private static final BigInteger MAX_OCTET = BigInteger.valueOf(255);

    private ProtosStandardBytesProtocol() {}

    public static void install(ProtosObjectValue bytesFactory) {
        Objects.requireNonNull(bytesFactory, "bytesFactory");
        for (String selector :
                List.of("call", "size", "at", "atPut", "each", "add", "removeAt")) {
            if (bytesFactory.hasLocalSlot(selector)) {
                throw new IllegalStateException(
                        "standard Bytes factory already defines local " + selector);
            }
        }

        bytesFactory.createLocalSlot(
                "call",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            Object receiver = activation.receiver();
                            if (!(receiver instanceof ProtosObjectValue prototype)
                                    || !delegatesTo(prototype, bytesFactory)) {
                                return fail(activation);
                            }
                            if (!supplied.isEmpty()) {
                                return fail(activation);
                            }
                            return new ProtosBytesValue(prototype);
                        }));

        bytesFactory.createLocalSlot(
                "size",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosBytesValue bytes = requireBytesReceiver(activation);
                            if (!supplied.isEmpty()) {
                                return fail(activation);
                            }
                            return new ProtosIntegerValue(bytes.indexedSize());
                        }));

        bytesFactory.createLocalSlot(
                "at",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosBytesValue bytes = requireBytesReceiver(activation);
                            if (supplied.size() != 1) {
                                return fail(activation);
                            }
                            BigInteger index =
                                    requireExistingIndex(
                                            supplied.get(0), bytes.indexedSize(), activation);
                            if(bytes.isIndexReserved(index))throw new ProtosSignalException(ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.PARALLEL_REGION_IN_USE));
                            return bytes.indexedAt(index);
                        }));

        bytesFactory.createLocalSlot(
                "atPut",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosBytesValue bytes = requireBytesReceiver(activation);
                            if (bytes.isFrozen()) {
                                return fail(activation);
                            }
                            if (supplied.size() != 2) {
                                return fail(activation);
                            }
                            BigInteger index =
                                    requireExistingIndex(
                                            supplied.get(0), bytes.indexedSize(), activation);
                            if(bytes.isIndexReserved(index))throw new ProtosSignalException(ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.PARALLEL_REGION_IN_USE));
                            requireOctet(supplied.get(1), activation);
                            return bytes.indexedPut(index, supplied.get(1));
                        }));

        bytesFactory.createLocalSlot(
                "each",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosBytesValue bytes = requireBytesReceiver(activation);
                            if (supplied.size() != 1) {
                                return fail(activation);
                            }
                            Object block = supplied.get(0);
                            requireInvokable(block, activation);
                            List<Object> snapshot = bytes.indexedSnapshot();
                            for (Object octet : snapshot) {
                                ProtosInvocation.invoke(block, List.of(octet), activation);
                            }
                            return bytes;
                        }));

        bytesFactory.createLocalSlot(
                "add",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosBytesValue bytes = requireBytesReceiver(activation);
                            if (!bytes.isOpen()) {
                                return fail(activation);
                            }
                            if(bytes.hasReservation())throw new ProtosSignalException(ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.PARALLEL_REGION_IN_USE));
                            if (supplied.size() != 1) {
                                return fail(activation);
                            }
                            requireOctet(supplied.get(0), activation);
                            return bytes.indexedAdd(supplied.get(0));
                        }));

        bytesFactory.createLocalSlot(
                "removeAt",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosBytesValue bytes = requireBytesReceiver(activation);
                            if (!bytes.isOpen()) {
                                return fail(activation);
                            }
                            if(bytes.hasReservation())throw new ProtosSignalException(ProtosCoreErrors.newOccurrence(activation,ProtosCoreErrors.StandardError.PARALLEL_REGION_IN_USE));
                            if (supplied.size() != 1) {
                                return fail(activation);
                            }
                            BigInteger index =
                                    requireExistingIndex(
                                            supplied.get(0), bytes.indexedSize(), activation);
                            return bytes.indexedRemoveAt(index);
                        }));
        ProtosParallelRuntime.installBytesParallel(bytesFactory);
    }

    private static ProtosBytesValue requireBytesReceiver(ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosBytesValue bytes)) {
            fail(activation);
        }
        return (ProtosBytesValue) activation.receiver();
    }

    private static BigInteger requireExistingIndex(
            Object candidate, BigInteger size, ProtosActivation activation) {
        BigInteger index = exactIntegerValue(candidate);
        if (index == null || index.signum() < 0 || index.compareTo(size) >= 0) {
            fail(activation);
        }
        return index;
    }

    private static void requireOctet(Object candidate, ProtosActivation activation) {
        BigInteger value = exactIntegerValue(candidate);
        if (value == null || value.signum() < 0 || value.compareTo(MAX_OCTET) > 0) {
            fail(activation);
        }
    }

    private static BigInteger exactIntegerValue(Object candidate) {
        if (candidate instanceof ProtosIntegerValue integer) {
            return integer.value();
        }
        if (candidate instanceof ProtosFixedIntegerValue integer) {
            return integer.value();
        }
        return null;
    }

    private static void requireInvokable(Object candidate, ProtosActivation activation) {
        ProtosPrelude prelude =
                activation.prelude()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "standard Bytes.each requires an owning Core prelude"));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(candidate, "call", prelude)
                            .orElseThrow(
                                    () ->
                                            new ProtosSignalException(
                                                    ProtosCoreErrors.newError(activation)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        if (!(selected.value() instanceof ProtosClosureValue)) {
            fail(activation);
        }
    }

    private static boolean delegatesTo(
            ProtosObjectValue receiver, ProtosObjectValue expectedAncestor) {
        Object current = receiver;
        while (current instanceof ProtosObjectValue ordinary) {
            if (ordinary == expectedAncestor) {
                return true;
            }
            current = ordinary.parent().orElse(null);
        }
        return false;
    }

    private static Object fail(ProtosActivation activation) {
        throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
