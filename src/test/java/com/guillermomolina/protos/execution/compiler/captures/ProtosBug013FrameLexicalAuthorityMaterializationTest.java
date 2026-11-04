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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosExecutionContextValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * BUG013-B regression evidence: a frame-backed lexical authority must retain
 * an escape-safe materialized frame immediately when the generated root
 * installs it, before deferred execution-context observation can occur.
 */
final class ProtosBug013FrameLexicalAuthorityMaterializationTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void authorityRetainsMaterializedFrameBeforeDeferredContextObservation()
            throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = prelude();
                ProtosActivation activation = deferredActivation(prelude);

                assertNull(
                        privateField(activation, "context"),
                        "the compact activation must begin with its guest Context deferred");

                ProtosBytecodeRootNode root = installingRoot(language);
                assertSame(
                        ProtosNullValue.INSTANCE,
                        root.getCallTarget().call(activation));

                assertNull(
                        privateField(activation, "context"),
                        "installing the lexical authority must not force Context observation");

                ProtosFrameLexicalBindingAuthority authority =
                        assertInstanceOf(
                                ProtosFrameLexicalBindingAuthority.class,
                                privateField(activation, "deferredContextAuthority"));

                MaterializedFrame retainedFrame =
                        assertInstanceOf(
                                MaterializedFrame.class,
                                privateField(authority, "frame"));
                assertSame(
                        retainedFrame,
                        authority.retainedMaterializedFrameForCapturedAccess());

                ProtosObjectValue value =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                authority.putBinding("x", value);
                assertTrue(authority.containsBinding("x"));
                assertSame(value, authority.readBinding("x").orElseThrow());

                ProtosExecutionContextValue observedContext =
                        assertInstanceOf(
                                ProtosExecutionContextValue.class,
                                activation.context());
                assertSame(
                        authority,
                        observedContext.lexicalBindingAuthorityForRuntime());
                assertSame(
                        value,
                        observedContext.readLocalSlot("x").orElseThrow());
            } finally {
                context.leave();
            }
        }
    }

    /**
     * I115 lazy-context experiment: when a root that started frame-native is
     * promoted on demand (here, before an escaping capture), the installed
     * authority retains the same escape-safe materialized frame BUG013-B
     * requires of the entry installation, and the escaped Closure reads the
     * owner binding after the owner frame has returned. A second promotion of
     * the same activation installs nothing new.
     */
    @Test
    void lazyPromotionRetainsMaterializedFrameForEscapedCapture() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosLanguage language = LANGUAGE_REF.get(null);
                ProtosPrelude prelude = prelude();
                String characters = "(x) => { g: () => x\n" + "h: () => x\n" + "g }";
                Source source = Source.newBuilder(
                        ProtosLanguage.ID, characters, "bug013-lazy-promotion.protos").build();
                CanonicalSequence canonical = (CanonicalSequence) new Canonicalizer()
                        .canonicalize(new ProtosParser(characters).parseProgram());
                ProtosClosureValue owner = assertInstanceOf(
                        ProtosClosureValue.class,
                        new CanonicalToBytecodeLowerer(language, source)
                                .lowerRoot(canonical)
                                .getCallTarget()
                                .call(prelude.newModuleActivation()));

                ProtosObjectValue argument =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosActivation activation = ProtosActivation.forImmediateMethodInvocationWithReturnHomeForRuntime(
                        owner,
                        List.of(argument),
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        new ProtosObjectValue(ProtosObjectValue.rootObject()),
                        prelude,
                        new ProtosActorModuleState(),
                        null,
                        new ProtosActorExecutionDomain(),
                        new ProtosReturnHome());
                ProtosClosureValue escaped = assertInstanceOf(
                        ProtosClosureValue.class,
                        owner.executionPlan().orElseThrow()
                                .executeBytecodeActivationForTesting(activation));

                ProtosFrameLexicalBindingAuthority authority =
                        assertInstanceOf(
                                ProtosFrameLexicalBindingAuthority.class,
                                activation.currentLexicalBindingAuthorityForRuntime());
                MaterializedFrame retainedFrame =
                        assertInstanceOf(
                                MaterializedFrame.class,
                                privateField(authority, "frame"));
                assertSame(
                        retainedFrame,
                        authority.retainedMaterializedFrameForCapturedAccess());
                assertSame(argument, authority.readBinding("x").orElseThrow());
                assertSame(
                        argument,
                        ProtosClosureInvoker.invoke(escaped, List.of()));
            } finally {
                context.leave();
            }
        }
    }

    private static ProtosBytecodeRootNode installingRoot(ProtosLanguage language) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal x = builder.createLocal("x", null);
                            builder.beginInstallFrameLexicalAuthority(
                                    new BytecodeLocal[] {x},
                                    ProtosFrameLexicalLayout.of(
                                            new String[] {"x"},
                                            ProtosFrameLexicalLayout.localOffsetsOf(
                                                    new BytecodeLocal[] {x})));
                            builder.emitLoadArgument(0);
                            builder.endInstallFrameLexicalAuthority();

                            builder.beginReturn();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endReturn();

                            builder.endRoot();
                        });
        return roots.getNode(0);
    }

    private static ProtosActivation deferredActivation(ProtosPrelude prelude) {
        ProtosObjectValue receiver =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue methodHome =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosClosureValue closure =
                new ProtosClosureValue(null, List.of(), receiver);

        return ProtosActivation.forImmediateMethodInvocationWithReturnHomeForRuntime(
                closure,
                List.of(),
                receiver,
                methodHome,
                prelude,
                new ProtosActorModuleState(),
                null,
                new ProtosActorExecutionDomain(),
                new ProtosReturnHome());
    }

    private static ProtosPrelude prelude() {
        ProtosStandardObjectProtocol.install();

        ProtosObjectValue contextPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue bindings =
                new ProtosObjectValue(contextPrototype);
        bindings.createLocalSlot("Context", contextPrototype);
        bindings.createLocalSlot(
                "Error",
                new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.createLocalSlot(
                "Array",
                new ProtosObjectValue(ProtosObjectValue.rootObject()));
        bindings.freeze();

        return new ProtosPrelude(bindings, contextPrototype);
    }

    private static Object privateField(Object target, String name)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
