/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosFixedIntegerValue;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosIdentityMapValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosIdentityMapConformanceTest {
    // Deliberately Java-side: this checks implementation representation and the
    // exact bootstrap binding, not ordinary source-visible IdentityMap behavior.
    @Test
    void factoryMaterializesRepresentedIdentityMapAndPreludeBinding() throws IOException {
        ProtosPrelude prelude = core();
        Object value = exec(prelude, "IdentityMap()");

        assertInstanceOf(ProtosIdentityMapValue.class, value);
        assertSame(
                prelude.identityMapPrototype(),
                prelude.bindings().readLocalSlot("IdentityMap").orElseThrow());
    }

    // Deliberately Java-side: ProtosIdentity is the primitive representation
    // helper consumed by IdentityMap; testing its raw family/NaN/signed-zero
    // behavior is an implementation contract rather than a message-level test.
    @Test
    void identityHashesAreCoherent() {
        var a =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.INT32, BigInteger.ONE);
        var b =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.INT32, BigInteger.ONE);
        var c =
                new ProtosFixedIntegerValue(
                        ProtosFixedIntegerValue.Family.UINT32, BigInteger.ONE);

        assertTrue(ProtosIdentity.identical(a, b));
        assertEquals(ProtosIdentity.identityHash(a), ProtosIdentity.identityHash(b));
        assertFalse(ProtosIdentity.identical(a, c));

        var nan1 = new ProtosFloatValue(Double.longBitsToDouble(0x7ff8000000000001L));
        var nan2 = new ProtosFloatValue(Double.longBitsToDouble(0x7ff8000000000002L));
        assertTrue(ProtosIdentity.identical(nan1, nan2));
        assertEquals(ProtosIdentity.identityHash(nan1), ProtosIdentity.identityHash(nan2));

        assertFalse(
                ProtosIdentity.identical(
                        new ProtosFloatValue(0.0), new ProtosFloatValue(-0.0)));
    }

    private static Object exec(ProtosPrelude prelude, String source) {
        return new ProtosSourceCompiler().compile(source).call(prelude.newModuleActivation());
    }

    private static ProtosPrelude core() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}
