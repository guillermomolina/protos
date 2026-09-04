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

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.VersionInfo;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;

public final class ProtosStandardStringProtocol {
    private static final VersionInfo REQUIRED_UNICODE = VersionInfo.getInstance(17, 0, 0, 0);

    private ProtosStandardStringProtocol() {}

    public static void install(ProtosObjectValue stringPrototype) {
        Objects.requireNonNull(stringPrototype, "stringPrototype");
        requireUnicode17();

        if (stringPrototype.hasLocalSlot("size")
                || stringPrototype.hasLocalSlot("at")
                || stringPrototype.hasLocalSlot("+")) {
            throw new IllegalStateException("Core String already defines a standard protocol slot");
        }

        stringPrototype.createLocalSlot(
                "size",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosStringValue receiver = requireStringReceiver(activation);
                            requireArity(activation, supplied.size(), 0);
                            return new ProtosIntegerValue(
                                    BigInteger.valueOf(graphemeCount(receiver.value())));
                        }));

        stringPrototype.createLocalSlot(
                "at",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosStringValue receiver = requireStringReceiver(activation);
                            requireArity(activation, supplied.size(), 1);
                            BigInteger index = requireInteger(activation, supplied.get(0));
                            if (index.signum() < 0 || index.bitLength() > 31) {
                                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                            }
                            String grapheme = graphemeAt(receiver.value(), index.intValue());
                            if (grapheme == null) {
                                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosStringValue(grapheme);
                        }));

        stringPrototype.createLocalSlot(
                "+",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> {
                            ProtosStringValue receiver = requireStringReceiver(activation);
                            requireArity(activation, supplied.size(), 1);
                            if (!(supplied.get(0) instanceof ProtosStringValue right)) {
                                throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                            }
                            return new ProtosStringValue(receiver.value() + right.value());
                        }));
    }

    private static ProtosStringValue requireStringReceiver(
            com.guillermomolina.protos.runtime.ProtosActivation activation) {
        if (!(activation.receiver() instanceof ProtosStringValue string)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return string;
    }

    private static void requireArity(
            com.guillermomolina.protos.runtime.ProtosActivation activation,
            int actual,
            int expected) {
        if (actual != expected) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
    }

    private static BigInteger requireInteger(
            com.guillermomolina.protos.runtime.ProtosActivation activation,
            Object value) {
        if (value instanceof ProtosIntegerValue integer) {
            return integer.value();
        }
        if (value instanceof ProtosFixedIntegerValue integer) {
            return integer.value();
        }
        throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }

    private static long graphemeCount(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        long count = 0;
        for (int boundary = iterator.first(), next = iterator.next();
                next != BreakIterator.DONE;
                boundary = next, next = iterator.next()) {
            count++;
        }
        return count;
    }

    private static String graphemeAt(String text, int wanted) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        int index = 0;
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (index == wanted) {
                return text.substring(start, end);
            }
            index++;
        }
        return null;
    }

    private static void requireUnicode17() {
        if (UCharacter.getUnicodeVersion().compareTo(REQUIRED_UNICODE) < 0) {
            throw new IllegalStateException(
                    "I003 requires Unicode 17.0.0 grapheme data; ICU reports "
                            + UCharacter.getUnicodeVersion());
        }
    }
}
