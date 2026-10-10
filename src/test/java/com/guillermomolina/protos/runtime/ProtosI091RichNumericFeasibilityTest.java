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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * I091 / PLAT056 Candidate C gate F1 (internal mechanism only): a rich numeric value can be an
 * ordinary FROZEN Protos object with ordinary delegation, family-private exact state, value
 * identity and a coherent identity hash, without a dedicated Java value class per family and
 * without guest forgery. The fixture family is test-only; no public numeric family is installed.
 */
final class ProtosI091RichNumericFeasibilityTest {
    private static final RatioFixtureFamily RATIO =
            new RatioFixtureFamily("std:i091-fixture/Ratio", 90);
    private static final RatioFixtureFamily OTHER =
            new RatioFixtureFamily("std:i091-fixture/OtherRatio", 91);

    @Test
    void ordinaryDelegationAndImmutability() {
        ProtosObjectValue prototype = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStringValue marker = new ProtosStringValue("ratio method");
        prototype.createLocalSlot("describe", marker);

        ProtosObjectValue half = RATIO.mint(prototype, BigInteger.ONE, BigInteger.TWO);

        assertSame(prototype, half.parent().orElseThrow());
        assertSame(marker,
                ProtosValueLookup.lookup(half, "describe", null).orElseThrow().value());
        assertTrue(half.isFrozen());
        assertThrows(RuntimeException.class,
                () -> half.createLocalSlot("mutated", ProtosNullValue.INSTANCE));
    }

    @Test
    void valueIdentityAndIdentityHashAreFamilyAndValueSensitive() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue a = RATIO.mint(root, BigInteger.ONE, BigInteger.TWO);
        ProtosObjectValue b = RATIO.mint(root, BigInteger.ONE, BigInteger.TWO);
        ProtosObjectValue c = RATIO.mint(root, BigInteger.ONE, BigInteger.valueOf(3));
        ProtosObjectValue foreignFamily = OTHER.mint(root, BigInteger.ONE, BigInteger.TWO);

        assertTrue(ProtosIdentity.identical(a, b));
        assertEquals(ProtosIdentity.identityHash(a), ProtosIdentity.identityHash(b));
        assertFalse(ProtosIdentity.identical(a, c));
        assertFalse(ProtosIdentity.identical(a, foreignFamily));
        assertNotEquals(ProtosIdentity.identityHash(a), ProtosIdentity.identityHash(foreignFamily));
    }

    @Test
    void membershipCannotBeForgedOrObservedBeforeFreezing() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue minted = RATIO.mint(root, BigInteger.ONE, BigInteger.TWO);

        ProtosObjectValue lookalike = new ProtosObjectValue(root);
        lookalike.freeze();
        assertFalse(ProtosNumericValueSupport.isRichNumericValue(lookalike));
        assertFalse(ProtosIdentity.identical(minted, lookalike));

        ProtosObjectValue open = RATIO.mintOpen(root, BigInteger.ONE, BigInteger.TWO);
        assertFalse(ProtosNumericValueSupport.isRichNumericValue(open));
        assertFalse(ProtosIdentity.identical(minted, open));

        // A rich value is never a current Integer/Float and never a primitive carrier.
        assertFalse(ProtosNumericValueSupport.isCurrentNumber(minted));
        assertSame(minted, ProtosNumericValueSupport.guestValue(minted));
    }

    /** Test-only exact ratio family; state is an immutable reduced pair. */
    private static final class RatioFixtureFamily
            extends ProtosSemanticTransferFamily implements ProtosRichNumericFamily {
        private final int tag;

        RatioFixtureFamily(String ownerModule, int tag) {
            super(new ProtosModuleKey(ownerModule));
            this.tag = tag;
        }

        ProtosSemanticTransferValue mint(Object parent, BigInteger numerator, BigInteger denominator) {
            ProtosSemanticTransferValue value = mintOpen(parent, numerator, denominator);
            value.freeze();
            return value;
        }

        ProtosSemanticTransferValue mintOpen(
                Object parent, BigInteger numerator, BigInteger denominator) {
            BigInteger gcd = numerator.gcd(denominator);
            return newValue(parent, new BigInteger[] {numerator.divide(gcd), denominator.divide(gcd)});
        }

        @Override
        public int identityTag() {
            return tag;
        }

        @Override
        public boolean sameValue(Object leftState, Object rightState) {
            BigInteger[] left = (BigInteger[]) leftState;
            BigInteger[] right = (BigInteger[]) rightState;
            return left[0].equals(right[0]) && left[1].equals(right[1]);
        }

        @Override
        public int valueHash(Object state) {
            BigInteger[] ratio = (BigInteger[]) state;
            return 31 * ratio[0].hashCode() + ratio[1].hashCode();
        }

        @Override
        protected ProtosSemanticTransferPayload extract(ProtosSemanticTransferValue value) {
            BigInteger[] ratio = (BigInteger[]) familyState(value);
            return ProtosSemanticTransferPayload.of(ratio[0], ratio[1]);
        }

        @Override
        protected boolean acceptsPayload(ProtosSemanticTransferPayload payload) {
            return payload.size() == 2
                    && payload.get(0) instanceof BigInteger
                    && payload.get(1) instanceof BigInteger denominator
                    && denominator.signum() > 0;
        }

        @Override
        protected ProtosSemanticTransferValue materialize(
                ProtosSemanticTransferPayload payload,
                ProtosSemanticTransferDestination destination) {
            return mint(ProtosObjectValue.rootObject(),
                    (BigInteger) payload.get(0), (BigInteger) payload.get(1));
        }
    }
}
