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
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Internal fixed-width carrier for host/foreign interop.
 *
 * <p>This is not a Protos semantic numeric value, has no Core prototype, and
 * does not participate in Protos equality, identity, hashing, transfer, or
 * arithmetic. It preserves width/range validation and exact Truffle integral
 * projection machinery for a future explicitly designed interop boundary.
 */
@ExportLibrary(InteropLibrary.class)
final class ProtosFixedIntegerInteropValue implements TruffleObject {
    public enum Kind {
        UINT8(BigInteger.ZERO, BigInteger.ONE.shiftLeft(8).subtract(BigInteger.ONE)),
        INT8(BigInteger.ONE.shiftLeft(7).negate(), BigInteger.ONE.shiftLeft(7).subtract(BigInteger.ONE)),
        UINT16(BigInteger.ZERO, BigInteger.ONE.shiftLeft(16).subtract(BigInteger.ONE)),
        INT16(BigInteger.ONE.shiftLeft(15).negate(), BigInteger.ONE.shiftLeft(15).subtract(BigInteger.ONE)),
        UINT32(BigInteger.ZERO, BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE)),
        INT32(BigInteger.ONE.shiftLeft(31).negate(), BigInteger.ONE.shiftLeft(31).subtract(BigInteger.ONE)),
        UINT64(BigInteger.ZERO, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)),
        INT64(BigInteger.ONE.shiftLeft(63).negate(), BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE));

        private final BigInteger minimum;
        private final BigInteger maximum;

        Kind(BigInteger minimum, BigInteger maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public BigInteger minimum() {
            return minimum;
        }

        public BigInteger maximum() {
            return maximum;
        }

        public boolean contains(BigInteger value) {
            Objects.requireNonNull(value, "value");
            return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
        }
    }

    private final Kind kind;
    private final BigInteger value;

    public ProtosFixedIntegerInteropValue(Kind kind, BigInteger value) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.value = Objects.requireNonNull(value, "value");
        if (!kind.contains(value)) {
            throw new IllegalArgumentException(value + " is outside " + kind + " range");
        }
    }

    public Kind kind() {
        return kind;
    }

    public BigInteger value() {
        return value;
    }

    @ExportMessage
    boolean isNumber() {
        return true;
    }

    @ExportMessage
    boolean fitsInByte() {
        return ProtosIntegralInteropSupport.fitsInByte(value);
    }

    @ExportMessage
    boolean fitsInShort() {
        return ProtosIntegralInteropSupport.fitsInShort(value);
    }

    @ExportMessage
    boolean fitsInInt() {
        return ProtosIntegralInteropSupport.fitsInInt(value);
    }

    @ExportMessage
    boolean fitsInLong() {
        return ProtosIntegralInteropSupport.fitsInLong(value);
    }

    @ExportMessage
    boolean fitsInBigInteger() {
        return true;
    }

    @ExportMessage
    boolean fitsInFloat() {
        return ProtosIntegralInteropSupport.fitsInFloat(value);
    }

    @ExportMessage
    boolean fitsInDouble() {
        return ProtosIntegralInteropSupport.fitsInDouble(value);
    }

    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asByte(value);
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asShort(value);
    }

    @ExportMessage
    int asInt() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asInt(value);
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asLong(value);
    }

    @ExportMessage
    BigInteger asBigInteger() {
        return value;
    }

    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asFloat(value);
    }

    @ExportMessage
    double asDouble() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asDouble(value);
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return value.toString();
    }
}
