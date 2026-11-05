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
import com.guillermomolina.protos.runtime.ProtosLexicalCell;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalSequence;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocal;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * BUG013-B regression evidence, reconciled with I115: establishing a
 * binding's native storage never forces the deferred guest Context into
 * existence, a later observation materializes exactly one Context identity
 * that every observer shares, and an escaped capture keeps the owner
 * binding's single storage alive after the owner frame has returned. The
 * retired frame lexical authority and its retained materialized frame are
 * replaced by the storage that owns a captured binding now: one shared cell.
 *
 * <p>KNOWN I115 SEMANTIC DIVERGENCE: a Closure activation's Context holds
 * only NAME bindings, so LOCAL/CELL bindings are not asserted through it.
 */
final class ProtosBug013FrameLexicalAuthorityMaterializationTest {
    private static final LanguageReference<ProtosLanguage> LANGUAGE_REF =
            LanguageReference.create(ProtosLanguage.class);

    @Test
    void cellStorageIsEstablishedBeforeAndWithoutDeferredContextObservation()
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

                ProtosObjectValue value =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                ProtosBytecodeRootNode root = establishingRoot(language);
                ProtosLexicalCell cell =
                        assertInstanceOf(
                                ProtosLexicalCell.class,
                                root.getCallTarget().call(activation, value));
                assertSame(value, cell.valueOrNullForRuntime());

                assertNull(
                        privateField(activation, "context"),
                        "establishing native binding storage must not force Context observation");

                // A capture before observation shares the still-deferred scope.
                ProtosLexicalEnvironment captured =
                        activation.lexicalEnvironmentForClosureCapture();
                assertTrue(captured.isDeferredForRuntime());
                assertSame(captured, activation.lexicalEnvironmentForClosureCapture());

                // Observation materializes one Context identity for every observer.
                ProtosExecutionContextValue observedContext =
                        assertInstanceOf(
                                ProtosExecutionContextValue.class,
                                activation.context());
                assertSame(observedContext, activation.context());
                assertSame(observedContext, captured.context());
                assertSame(observedContext, activation.materializedContextOrNullForRuntime());

                // A NAME binding created through the activation is the Context's binding.
                ProtosObjectValue named =
                        new ProtosObjectValue(ProtosObjectValue.rootObject());
                activation.createCurrentLocalSlotForRuntime("y", named);
                assertSame(named, observedContext.readLocalSlot("y").orElseThrow());
                assertSame(named, captured.readLocalSlotForRuntime("y").orElseThrow());
                assertSame(value, cell.valueOrNullForRuntime(), "the cell is unaffected");
            } finally {
                context.leave();
            }
        }
    }

    /**
     * I115 lazy capture: a captured binding lives in one cell created by the
     * owner activation; creating escaping Closures neither materializes the
     * owner's Context nor copies the binding, and the escaped Closure reads
     * the owner binding after the owner frame has returned.
     */
    @Test
    void lazyCaptureSharesOneCellWithTheEscapedClosure() throws Exception {
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

                assertNull(
                        privateField(activation, "context"),
                        "creating the escaping Closures must not materialize the owner Context");
                ProtosLexicalCell[] cells = escaped.capturedCellsForRuntime();
                assertEquals(1, cells.length);
                assertSame(argument, cells[0].valueOrNullForRuntime());
                assertSame(
                        argument,
                        ProtosClosureInvoker.invoke(escaped, List.of()));
                assertSame(argument, cells[0].valueOrNullForRuntime());
            } finally {
                context.leave();
            }
        }
    }

    /**
     * {@code x} is a CELL binding: the root creates its cell in its native
     * local, establishes it from argument 1 and returns the cell. It never
     * observes the current activation.
     */
    private static ProtosBytecodeRootNode establishingRoot(ProtosLanguage language) {
        BytecodeRootNodes<ProtosBytecodeRootNode> roots =
                ProtosBytecodeRootNodeGen.create(
                        language,
                        BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();

                            BytecodeLocal x = builder.createLocal("x", null);
                            builder.beginStoreLocal(x);
                            builder.emitCreateCell();
                            builder.endStoreLocal();

                            builder.beginStoreCell();
                            builder.emitLoadLocal(x);
                            builder.emitLoadArgument(1);
                            builder.endStoreCell();

                            builder.beginReturn();
                            builder.emitLoadLocal(x);
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
