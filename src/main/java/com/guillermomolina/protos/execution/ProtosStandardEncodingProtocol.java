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

import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Installs the standard Encoding semantic-family one-shot conversion boundary. */
public final class ProtosStandardEncodingProtocol {
    private static final BigInteger MAX_OCTET = BigInteger.valueOf(255);

    private ProtosStandardEncodingProtocol() {}

    public static void install(
            ProtosObjectValue encodingPrototype, ProtosObjectValue bytesPrototype) {
        Objects.requireNonNull(encodingPrototype, "encodingPrototype");
        Objects.requireNonNull(bytesPrototype, "bytesPrototype");
        for (String selector :
                List.of("UTF8", "UTF16LE", "UTF16BE", "Latin1", "encode", "decode")) {
            if (encodingPrototype.hasLocalSlot(selector)) {
                throw new IllegalStateException(
                        "standard Encoding already defines local " + selector);
            }
        }

        encodingPrototype.createLocalSlot(
                "UTF8",
                ProtosEncodingValue.portableForRuntime(
                        encodingPrototype, ProtosEncodingValue.PortableKind.UTF8));
        encodingPrototype.createLocalSlot(
                "UTF16LE",
                ProtosEncodingValue.portableForRuntime(
                        encodingPrototype, ProtosEncodingValue.PortableKind.UTF16LE));
        encodingPrototype.createLocalSlot(
                "UTF16BE",
                ProtosEncodingValue.portableForRuntime(
                        encodingPrototype, ProtosEncodingValue.PortableKind.UTF16BE));
        encodingPrototype.createLocalSlot(
                "Latin1",
                ProtosEncodingValue.portableForRuntime(
                        encodingPrototype, ProtosEncodingValue.PortableKind.LATIN1));

        encodingPrototype.createLocalSlot(
                "encode",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosEncodingValue encoding = requireReceiver(activation);
                            if (supplied.size() != 1
                                    || !(supplied.get(0) instanceof ProtosStringValue text)) {
                                throw invalid(activation);
                            }
                            final byte[] converted;
                            try {
                                converted = encoding.encodeForRuntime(text.value());
                            } catch (ProtosEncodingValue.ConversionFailure failure) {
                                throw encodingError(activation);
                            }
                            ProtosBytesValue result = new ProtosBytesValue(bytesPrototype);
                            for (byte value : converted) {
                                result.indexedAdd(
                                        new ProtosIntegerValue(BigInteger.valueOf(value & 0xff)));
                            }
                            return result;
                        }));

        encodingPrototype.createLocalSlot(
                "decode",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosEncodingValue encoding = requireReceiver(activation);
                            if (supplied.size() != 1
                                    || !(supplied.get(0) instanceof ProtosBytesValue bytes)) {
                                throw invalid(activation);
                            }
                            final String converted;
                            try {
                                converted = encoding.decodeForRuntime(exactOctets(bytes, activation));
                            } catch (ProtosEncodingValue.ConversionFailure failure) {
                                throw encodingError(activation);
                            }
                            return new ProtosStringValue(converted);
                        }));

        encodingPrototype.freeze();
    }

    private static ProtosEncodingValue requireReceiver(ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosEncodingValue encoding)) {
            throw invalid(activation);
        }
        return encoding;
    }

    private static byte[] exactOctets(ProtosBytesValue bytes, ProtosActivation activation) {
        List<Object> values = bytes.indexedSnapshot();
        byte[] result = new byte[values.size()];
        for (int i = 0; i < values.size(); i++) {
            BigInteger value = exactInteger(values.get(i));
            if (value == null || value.signum() < 0 || value.compareTo(MAX_OCTET) > 0) {
                throw invalid(activation);
            }
            result[i] = (byte) value.intValue();
        }
        return result;
    }

    private static BigInteger exactInteger(Object value) {
        if (value instanceof ProtosIntegerValue integer) return integer.value();
        if (value instanceof ProtosFixedIntegerValue integer) return integer.value();
        return null;
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static ProtosSignalException encodingError(ProtosActivation activation) {
        return new ProtosSignalException(
                ProtosCoreErrors.newOccurrence(
                        activation, ProtosCoreErrors.StandardError.ENCODING_ERROR));
    }
}
