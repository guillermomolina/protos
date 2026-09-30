/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPathValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosStandardPathProtocolTest {
    private static ProtosPrelude core() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }

    // Deliberately Java-side: this verifies the represented Path component model
    // (ordered normal component Strings, D169) behind source-visible construction.
    @Test
    void constructionPreservesOrderedNormalStringComponents() throws Exception {
        var prelude = core();
        var represented =
                (ProtosPathValue)
                        ProtosTestExecutionSupport.evaluate(
                                "Path.relative().child(\"a/b\").child(\"c\\\\d\").child(\"e:f\")",
                                prelude.newModuleActivation());

        assertEquals(List.of("a/b", "c\\d", "e:f"), represented.components());

        var empty =
                (ProtosPathValue)
                        ProtosTestExecutionSupport.evaluate(
                                "Path.relative()",
                                prelude.newModuleActivation());
        assertTrue(empty.components().isEmpty());
    }

    // Deliberately Java-side: lookup-home identity is an implementation/representation
    // invariant of the represented Path value.
    @Test
    void representedLookupHomeIsPathPrototype() throws Exception {
        var prelude = core();
        var value =
                (ProtosPathValue)
                        ProtosTestExecutionSupport.evaluate(
                                "Path.relative().child(\"a\")",
                                prelude.newModuleActivation());

        assertSame(
                prelude.pathPrototype(),
                ProtosValueLookup.lookup(value, "child", prelude).orElseThrow().home());
    }

    // Deliberately Java-side: structural equality/hash are defined only by the ordered
    // component sequence and do not consult the delegation prototype.
    @Test
    void structuralEqualityAndHashDependOnlyOnOrderedComponents() throws Exception {
        var prelude = core();
        var ab = new ProtosPathValue(prelude.pathPrototype(), List.of("a", "b"));
        var abOther = new ProtosPathValue(ProtosObjectValue.rootObject(), List.of("a", "b"));
        var ba = new ProtosPathValue(prelude.pathPrototype(), List.of("b", "a"));

        assertTrue(ab.structurallyEquals(abOther));
        assertEquals(ab.structuralHash(), abOther.structuralHash());
        assertFalse(ab.structurallyEquals(ba));
        assertFalse(ab.structurallyEquals(null));
    }

    // Deliberately Java-side: this checks bootstrap object identity/frozen state,
    // not ordinary Path message behavior.
    @Test
    void pathPrototypeIsFrozenPreludeBinding() throws Exception {
        var prelude = core();

        assertTrue(prelude.bindings().isFrozen());
        assertSame(
                prelude.pathPrototype(),
                prelude.bindings().readLocalSlot("Path").orElseThrow());
        assertSame(
                ProtosObjectValue.rootObject(),
                prelude.pathPrototype().parent().orElseThrow());
    }
}
