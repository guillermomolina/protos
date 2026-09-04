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

import java.math.BigInteger;
import java.util.Objects;

public final class ProtosFixedIntegerValue implements ProtosRepresentedValue {
    public enum Family {
        UINT8("UInt8", BigInteger.ZERO, BigInteger.ONE.shiftLeft(8).subtract(BigInteger.ONE)),
        INT8("Int8", BigInteger.ONE.shiftLeft(7).negate(), BigInteger.ONE.shiftLeft(7).subtract(BigInteger.ONE)),
        UINT16("UInt16", BigInteger.ZERO, BigInteger.ONE.shiftLeft(16).subtract(BigInteger.ONE)),
        INT16("Int16", BigInteger.ONE.shiftLeft(15).negate(), BigInteger.ONE.shiftLeft(15).subtract(BigInteger.ONE)),
        UINT32("UInt32", BigInteger.ZERO, BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE)),
        INT32("Int32", BigInteger.ONE.shiftLeft(31).negate(), BigInteger.ONE.shiftLeft(31).subtract(BigInteger.ONE)),
        UINT64("UInt64", BigInteger.ZERO, BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)),
        INT64("Int64", BigInteger.ONE.shiftLeft(63).negate(), BigInteger.ONE.shiftLeft(63).subtract(BigInteger.ONE));

        private final String prototypeName;
        private final BigInteger minimum;
        private final BigInteger maximum;

        Family(String prototypeName, BigInteger minimum, BigInteger maximum) {
            this.prototypeName = prototypeName;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public String prototypeName() { return prototypeName; }
        public BigInteger minimum() { return minimum; }
        public BigInteger maximum() { return maximum; }

        public boolean contains(BigInteger value) {
            return value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
        }

        public static Family fromPrototypeName(String name) {
            for (Family family : values()) {
                if (family.prototypeName.equals(name)) {
                    return family;
                }
            }
            throw new IllegalArgumentException("unknown fixed-width integer family: " + name);
        }
    }

    private final Family family;
    private final BigInteger value;

    public ProtosFixedIntegerValue(Family family, BigInteger value) {
        this.family = Objects.requireNonNull(family, "family");
        this.value = Objects.requireNonNull(value, "value");
        if (!family.contains(value)) {
            throw new IllegalArgumentException(value + " is outside " + family.prototypeName() + " range");
        }
    }

    public Family family() { return family; }
    public BigInteger value() { return value; }

    @Override
    public Object representedDelegationParent(ProtosPrelude prelude) {
        return ProtosRepresentedValue.requirePrelude(prelude, "fixed-width Integer")
                .fixedIntegerPrototype(family);
    }

}
