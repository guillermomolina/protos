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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosExecutionContextValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF012: focused evidence that the precomputed {@link
 * ProtosFrameLexicalLayout} describes the expected frame-backed bindings
 * exactly, that it is built once and reused by reference across every {@link
 * ProtosFrameLexicalBindingAuthority} instance installed for the same
 * lowered root, and that the PLAT036 Candidate D / D179 C0 / I071 runtime
 * invariants the authority already guaranteed before this change still hold
 * once its name/ordinal metadata comes from that shared layout instead of a
 * per-invocation rebuild.
 */
final class ProtosPerf012FrameLexicalLayoutTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void layoutDescribesDeclarationOrderNamesAndOrdinalsExactly() {
        ProtosFrameLexicalLayout layout =
                ProtosFrameLexicalLayout.of(new String[] {"a", "b", "c"});

        assertEquals(3, layout.length());
        assertEquals("a", layout.nameAt(0));
        assertEquals("b", layout.nameAt(1));
        assertEquals("c", layout.nameAt(2));
        assertEquals(Integer.valueOf(0), layout.offsetOf("a"));
        assertEquals(Integer.valueOf(1), layout.offsetOf("b"));
        assertEquals(Integer.valueOf(2), layout.offsetOf("c"));
        assertNull(layout.offsetOf("neverDeclared"));
    }

    @Test
    void layoutRejectsDuplicateNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtosFrameLexicalLayout.of(new String[] {"x", "x"}));
    }

    @Test
    void layoutRejectsANullNameEntry() {
        assertThrows(
                NullPointerException.class,
                () -> ProtosFrameLexicalLayout.of(new String[] {"x", null}));
    }

    @Test
    void multipleInvocationsOfTheSameRootShareOneLayoutInstanceWithIndependentFrames()
            throws Exception {
        withEnteredLanguage(
                (language, ignoredModule) -> {
                    ProtosSemanticBytecodeRootNode root = lowerRoot(language, "x: 41\nx");

                    ProtosActivation firstModule = moduleActivation();
                    ProtosActivation secondModule = moduleActivation();
                    root.getCallTarget().call(firstModule);
                    root.getCallTarget().call(secondModule);

                    ProtosFrameLexicalBindingAuthority firstAuthority =
                            authorityOf(firstModule);
                    ProtosFrameLexicalBindingAuthority secondAuthority =
                            authorityOf(secondModule);

                    // One authority object per invocation ...
                    assertNotSame(firstAuthority, secondAuthority);
                    // ... but both share the identical precomputed layout: no
                    // per-invocation LinkedHashMap/ArrayList rebuild.
                    assertSame(
                            frameBackedLayoutOf(firstAuthority),
                            frameBackedLayoutOf(secondAuthority));

                    // Runtime frame state remains fully independent despite the
                    // shared layout.
                    ((ProtosExecutionContextValue) secondModule.context())
                            .assignLocalSlot("x", 999L);
                    assertEquals(
                            41L,
                            unwrapNumber(
                                    ((ProtosExecutionContextValue) firstModule.context())
                                            .readLocalSlot("x")
                                            .orElseThrow()));
                    assertEquals(
                            999L,
                            unwrapNumber(
                                    ((ProtosExecutionContextValue) secondModule.context())
                                            .readLocalSlot("x")
                                            .orElseThrow()));
                });
    }

    @Test
    void presentNullBindingRemainsDistinctFromAbsentOnTheSharedLayout()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    run(language, module, "x: null");

                    ProtosExecutionContextValue context =
                            (ProtosExecutionContextValue) module.context();
                    ProtosFrameLexicalBindingAuthority authority = authorityOf(module);

                    assertTrue(authority.hasFrameBackedBindingAt("x", 0));
                    assertSame(
                            ProtosNullValue.INSTANCE,
                            context.readLocalSlot("x").orElseThrow());
                    assertFalse(context.hasLocalSlot("neverDeclared"));
                });
    }

    @Test
    void clearedThenRecreatedBindingReusesTheSameOrdinalOnTheUnchangedLayout()
            throws Exception {
        withEnteredLanguage(
                (language, module) -> {
                    run(
                            language,
                            module,
                            "removed: \"before\"\ncontext.removeSlot(\"removed\")\nnull");

                    ProtosExecutionContextValue context =
                            (ProtosExecutionContextValue) module.context();
                    ProtosFrameLexicalBindingAuthority authority = authorityOf(module);
                    Object layoutBeforeRecreate = frameBackedLayoutOf(authority);

                    assertFalse(authority.hasFrameBackedBindingAt("removed", 0));
                    assertFalse(context.hasLocalSlot("removed"));

                    context.createLocalSlot("removed", 7L);

                    assertTrue(authority.hasFrameBackedBindingAt("removed", 0));
                    assertEquals(
                            7L, unwrapNumber(context.readLocalSlot("removed").orElseThrow()));
                    // D179 C0 clear/recreate never migrates a statically laid-out
                    // name to dynamic overflow, and never replaces the shared
                    // layout with a second copy.
                    assertSame(layoutBeforeRecreate, frameBackedLayoutOf(authority));
                });
    }

    private static ProtosFrameLexicalBindingAuthority authorityOf(
            ProtosActivation activation) {
        return assertInstanceOf(
                ProtosFrameLexicalBindingAuthority.class,
                ((ProtosExecutionContextValue) activation.context())
                        .lexicalBindingAuthorityForRuntime());
    }

    private static Object frameBackedLayoutOf(
            ProtosFrameLexicalBindingAuthority authority) throws ReflectiveOperationException {
        Field field =
                ProtosFrameLexicalBindingAuthority.class.getDeclaredField(
                        "frameBackedLayout");
        field.setAccessible(true);
        return field.get(authority);
    }

    private interface ModuleTest {
        void run(ProtosLanguage language, ProtosActivation module) throws Exception;
    }

    private static void withEnteredLanguage(ModuleTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                test.run(LANGUAGE_REF.get(null), moduleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static Object run(
            ProtosLanguage language, ProtosActivation module, String characters) {
        return lowerRoot(language, characters).getCallTarget().call(module);
    }

    private static ProtosSemanticBytecodeRootNode lowerRoot(
            ProtosLanguage language, String characters) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "perf012-layout.protos")
                        .build();
        return new CanonicalToBytecodeLowerer(language, source)
                .lowerRoot(canonicalize(characters));
    }

    private static long unwrapNumber(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof java.math.BigInteger bigInteger) {
            return bigInteger.longValueExact();
        }
        if (value instanceof com.guillermomolina.protos.runtime.ProtosIntegerValue integerValue) {
            return integerValue.value().longValueExact();
        }
        throw new AssertionError("expected a Protos integer literal result, got: " + value);
    }

    private static CanonicalSequence canonicalize(String characters) {
        return (CanonicalSequence)
                new Canonicalizer().canonicalize(new ProtosParser(characters).parseProgram());
    }

    private static ProtosActivation moduleActivation() {
        ProtosStandardObjectProtocol.install();

        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings = new ProtosObjectValue(contextPrototype);
        ProtosObjectValue errorPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot("Error", errorPrototype);
        bindings.createLocalSlot(
                "SlotNotFound", new ProtosObjectValue(errorPrototype));
        bindings.createLocalSlot(
                "Array", new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(bindings, contextPrototype).newModuleActivation();
    }
}
