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
    // behind otherwise source-visible construction semantics.
    @Test
    void constructionPreservesPortableRepresentation() throws Exception {
        var prelude = core();
        var represented =
                (ProtosPathValue)
                        new ProtosSourceCompiler()
                                .compile(
                                        "Path.relative().child(\"a/b\").parentComponent().child(\"c\")")
                                .call(prelude.newModuleActivation());

        assertFalse(represented.rooted());
        assertEquals(
                List.of(
                        new ProtosPathValue.Normal("a/b"),
                        ProtosPathValue.Parent.INSTANCE,
                        new ProtosPathValue.Normal("c")),
                represented.components());

        var rooted =
                (ProtosPathValue)
                        new ProtosSourceCompiler()
                                .compile("Path.rooted()")
                                .call(prelude.newModuleActivation());
        assertTrue(rooted.rooted());
        assertTrue(rooted.components().isEmpty());
    }

    // Deliberately Java-side: lookup-home identity and the internal Parent
    // component representation are implementation/representation invariants.
    @Test
    void representedLookupAndParentComponentAreDistinct() throws Exception {
        var prelude = core();
        var value =
                (ProtosPathValue)
                        new ProtosSourceCompiler()
                                .compile("Path.relative()")
                                .call(prelude.newModuleActivation());

        assertSame(
                prelude.pathPrototype(),
                ProtosValueLookup.lookup(value, "child", prelude).orElseThrow().home());

        var activation = prelude.newModuleActivation();
        activation.context().createLocalSlot("value", value);
        var withParent =
                (ProtosPathValue)
                        new ProtosSourceCompiler()
                                .compile("value.parentComponent()")
                                .call(activation);

        assertSame(
                prelude.pathPrototype(),
                ProtosValueLookup.lookup(withParent, "child", prelude).orElseThrow().home());
        assertEquals(List.of(ProtosPathValue.Parent.INSTANCE), withParent.components());
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
