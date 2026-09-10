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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.math.BigDecimal;
import java.math.BigInteger;

@ExportLibrary(InteropLibrary.class)
public final class ProtosFloatValue implements ProtosRepresentedValue {
    private final double value;

    public ProtosFloatValue(double value) {
        this.value = value;
    }

    public double value() {
        return value;
    }

    @Override
    public Object representedDelegationParent(ProtosPrelude prelude) {
        return ProtosRepresentedValue.requirePrelude(prelude, "Float").floatPrototype();
    }


    @ExportMessage
    boolean isNumber() {
        return true;
    }

    @ExportMessage
    boolean fitsInByte() {
        byte converted = (byte) value;
        return converted == value && !isNegativeZero(value);
    }

    @ExportMessage
    boolean fitsInShort() {
        short converted = (short) value;
        return converted == value && !isNegativeZero(value);
    }

    @ExportMessage
    boolean fitsInInt() {
        int converted = (int) value;
        return converted == value && !isNegativeZero(value);
    }

    @ExportMessage
    boolean fitsInLong() {
        if (isNegativeZero(value)) {
            return false;
        }
        long converted = (long) value;
        return converted != Long.MAX_VALUE && converted == value;
    }

    @ExportMessage
    boolean fitsInBigInteger() {
        return value % 1.0d == 0.0d && !isNegativeZero(value);
    }

    @ExportMessage
    boolean fitsInFloat() {
        float converted = (float) value;
        return !Double.isFinite(value) || converted == value;
    }

    @ExportMessage
    boolean fitsInDouble() {
        return true;
    }

    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        byte converted = (byte) value;
        if (converted == value && !isNegativeZero(value)) {
            return converted;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        short converted = (short) value;
        if (converted == value && !isNegativeZero(value)) {
            return converted;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    int asInt() throws UnsupportedMessageException {
        int converted = (int) value;
        if (converted == value && !isNegativeZero(value)) {
            return converted;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        if (!isNegativeZero(value)) {
            long converted = (long) value;
            if (converted != Long.MAX_VALUE && converted == value) {
                return converted;
            }
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    BigInteger asBigInteger() throws UnsupportedMessageException {
        if (!fitsInBigInteger()) {
            throw UnsupportedMessageException.create();
        }
        return new BigDecimal(value).toBigIntegerExact();
    }

    @ExportMessage
    float asFloat() throws UnsupportedMessageException {
        float converted = (float) value;
        if (!Double.isFinite(value) || converted == value) {
            return converted;
        }
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    double asDouble() {
        return value;
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return Double.toString(value);
    }

    private static boolean isNegativeZero(double candidate) {
        return Double.doubleToRawLongBits(candidate)
                == Double.doubleToRawLongBits(-0.0d);
    }

}
