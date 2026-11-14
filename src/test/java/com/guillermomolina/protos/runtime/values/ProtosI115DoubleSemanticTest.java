package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** I115: Float representation and prototype-selection invariants. */
final class ProtosI115DoubleSemanticTest {
    @Test void directDoubleIsSemanticFloat() {
        Object value = ProtosNumericValueSupport.floating(7.5);
        assertInstanceOf(Double.class, value);
        assertTrue(ProtosNumericValueSupport.isCurrentFloat(value));
        assertTrue(ProtosNumericValueSupport.isCurrentRealNumber(value));
        assertSame(value, ProtosNumericValueSupport.guestValue(value));
        assertTrue(ProtosValueLookup.isProtosValue(value));
        assertEquals(7.5, ProtosNumericValueSupport.currentFloatValue(value));
    }

    @Test void primitiveAndLegacyObjectHaveSameFloatIdentityAndHash() {
        Double direct = 7.5;
        ProtosFloatValue boxed = new ProtosFloatValue(7.5);
        assertTrue(ProtosNumericValueSupport.sameCurrentFamilyIdentity(direct, boxed));
        assertEquals(ProtosNumericValueSupport.currentNumericIdentityHash(direct),
                     ProtosNumericValueSupport.currentNumericIdentityHash(boxed));
    }

    @Test void floatBitsPreserveNegativeZeroAndCanonicalizeNanIdentity() {
        assertFalse(ProtosNumericValueSupport.sameCurrentFamilyIdentity(-0.0d, 0.0d));
        assertTrue(ProtosNumericValueSupport.sameCurrentFamilyIdentity(
                Double.NaN, Double.longBitsToDouble(0x7ff8000000000001L)));
    }

    @Test void copyOfDirectFloatNeedsNoRepresentationObject() {
        Object direct = ProtosNumericValueSupport.floating(1.25);
        assertSame(direct, ProtosNumericValueSupport.copyCurrentNumberOrNull(direct, null));
    }
}
