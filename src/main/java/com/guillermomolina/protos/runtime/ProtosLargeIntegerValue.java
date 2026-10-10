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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.math.BigInteger;
import java.util.Objects;

/**
 * I091 / PLAT056 Candidate C: an exact semantic Integer outside the signed-64 range, materialized
 * as a FROZEN ordinary Protos object.
 *
 * <p>The value delegates ordinarily to the Integer prototype of the Prelude that minted it, has no
 * local slots, and cannot be mutated. Its exact magnitude is the narrowly encapsulated numeric
 * payload PLAT056 permits; it is reachable only through {@link ProtosNumericValueSupport}, which
 * also owns value identity, hashing, ordering, arithmetic and normalization, so a value within the
 * signed-64 range is never represented here.
 *
 * <p>Under the current normative specification this is an ordinary Integer, recognized by
 * {@code Integer.recognizes}; the representation is unobservable. Any guest-visible distinction
 * of a separate D197 {@code BigInteger} family belongs to its authorized implementation owner
 * (I090), which would change only the minting prototype and recognition here.
 *
 * <p>Instances are minted per Prelude and are never shared across isolated execution domains:
 * transfer rematerializes them with the destination Prelude's prototype.
 */
@ExportLibrary(InteropLibrary.class)
public final class ProtosLargeIntegerValue extends ProtosObjectValue {
    private final BigInteger value;

    ProtosLargeIntegerValue(ProtosObjectValue integerPrototype, BigInteger value) {
        super(Objects.requireNonNull(integerPrototype, "integerPrototype"));
        this.value = Objects.requireNonNull(value, "value");
        if (value.bitLength() < Long.SIZE) {
            throw new IllegalArgumentException("a signed-64 Integer is not a large Integer");
        }
        freeze();
    }

    /** The exact value; only the numeric boundary reads it. */
    BigInteger exactValue() {
        return value;
    }

    @ExportMessage
    boolean isNumber() {
        return true;
    }

    /* Outside the signed-64 range no fixed host integral width can hold the value. */
    @ExportMessage
    boolean fitsInByte() {
        return false;
    }

    @ExportMessage
    boolean fitsInShort() {
        return false;
    }

    @ExportMessage
    boolean fitsInInt() {
        return false;
    }

    @ExportMessage
    boolean fitsInLong() {
        return false;
    }

    @ExportMessage
    boolean fitsInBigInteger() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInFloat() {
        return ProtosIntegralInteropSupport.fitsInFloat(value);
    }

    @ExportMessage
    @TruffleBoundary
    boolean fitsInDouble() {
        return ProtosIntegralInteropSupport.fitsInDouble(value);
    }

    @ExportMessage
    byte asByte() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    short asShort() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    int asInt() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    long asLong() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    BigInteger asBigInteger() {
        return value;
    }

    @ExportMessage
    @TruffleBoundary
    float asFloat() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asFloat(value);
    }

    @ExportMessage
    @TruffleBoundary
    double asDouble() throws UnsupportedMessageException {
        return ProtosIntegralInteropSupport.asDouble(value);
    }

    @Override
    @ExportMessage
    @TruffleBoundary
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return value.toString();
    }
}
