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

import com.guillermomolina.protos.execution.ProtosFrameLocalBindings.ResolvedLexicalWriteTarget;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosExecutionContextValue;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosModuleBindingLocation;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosTestPrelude;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosLexicalSelectionNodeTest {
    @Test
    void lookupPrefersCurrentContextThenCapturedLexicalContextsThenReceiver() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiverParent = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(receiverParent);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        ProtosObjectValue inner = new ProtosObjectValue(root);
        ProtosObjectValue current = new ProtosObjectValue(root);

        Object receiverValue = new ProtosStringValue("receiver");
        Object outerValue = new ProtosStringValue("outer");
        Object innerValue = new ProtosStringValue("inner");
        Object currentValue = new ProtosStringValue("current");

        receiver.createLocalSlot("name", receiverValue);
        outer.createLocalSlot("name", outerValue);
        inner.createLocalSlot("name", innerValue);
        current.createLocalSlot("name", currentValue);

        ProtosActivation activation =
                new ProtosActivation(current, List.of(inner, outer), receiver);

        assertSame(currentValue, read(activation, "name"));

        current.assignLocalSlot("name", ProtosNullValue.INSTANCE);
        assertSame(ProtosNullValue.INSTANCE, read(activation, "name"));
    }

    @Test
    void capturedLexicalContextsShadowReceiverAndPreserveNearestFirstOrder() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        ProtosObjectValue inner = new ProtosObjectValue(root);
        ProtosObjectValue current = new ProtosObjectValue(root);

        Object receiverValue = new ProtosStringValue("receiver");
        Object outerValue = new ProtosStringValue("outer");
        Object innerValue = new ProtosStringValue("inner");

        receiver.createLocalSlot("state", receiverValue);
        outer.createLocalSlot("state", outerValue);
        inner.createLocalSlot("state", innerValue);

        ProtosActivation activation =
                new ProtosActivation(current, List.of(inner, outer), receiver);

        assertSame(innerValue, read(activation, "state"));
    }

    @Test
    void receiverLookupDelegatesAfterLexicalContextsAreExhausted() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue current = new ProtosObjectValue(root);

        Object inherited = new ProtosStringValue("inherited");
        prototype.createLocalSlot("name", inherited);

        ProtosActivation activation =
                new ProtosActivation(current, List.of(), receiver);

        assertSame(inherited, read(activation, "name"));
    }

    @Test
    void receiverFallbackClosureReadBindsOriginalReceiverAndLookupHomeOnlyAfterLexicalExhaustion() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosClosureValue method =
                ProtosClosureValue.nativeClosure(
                        (ignoredActivation, ignoredSupplied) -> ProtosNullValue.INSTANCE);
        prototype.createLocalSlot("method", method);

        ProtosActivation activation =
                new ProtosActivation(current, List.of(), receiver);

        ProtosClosureValue bound =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        read(activation, "method"));
        assertNotSame(method, bound);
        assertSame(receiver, bound.capturedReceiver());
        assertSame(prototype, bound.methodHome().orElseThrow());

        current.createLocalSlot("method", method);
        assertSame(method, read(activation, "method"));
    }

    @Test
    void objectConstructionUsesObjectAsContextWithoutCapturingItLexically() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue enclosingContext = new ProtosObjectValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        ProtosObjectValue enclosingReceiver = new ProtosObjectValue(root);
        ProtosActivation enclosing =
                new ProtosActivation(
                        enclosingContext,
                        List.of(outer),
                        enclosingReceiver);
        ProtosObjectValue object = new ProtosObjectValue(root);

        ProtosActivation construction =
                ProtosActivation.forObjectConstruction(object, enclosing);

        assertSame(object, construction.context());
        assertSame(object, construction.receiver());
        assertSame(
                enclosingContext,
                construction.lexicalContextsForClosureCapture().get(0));
        assertSame(
                outer,
                construction.lexicalContextsForClosureCapture().get(1));
        assertTrue(
                construction.lexicalContextsForClosureCapture().stream()
                        .noneMatch(candidate -> candidate == object));
    }

    @Test
    void nestedConstructionSkipsEveryConstructionObjectForClosureCapture() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue lexical = new ProtosObjectValue(root);
        ProtosActivation enclosing =
                new ProtosActivation(
                        lexical,
                        List.of(),
                        new ProtosObjectValue(root));
        ProtosObjectValue outerObject = new ProtosObjectValue(root);
        ProtosObjectValue innerObject = new ProtosObjectValue(root);

        ProtosActivation outerConstruction =
                ProtosActivation.forObjectConstruction(
                        outerObject,
                        enclosing);
        ProtosActivation innerConstruction =
                ProtosActivation.forObjectConstruction(
                        innerObject,
                        outerConstruction);

        assertSame(
                lexical,
                innerConstruction.lexicalContextsForClosureCapture().get(0));
        assertTrue(
                innerConstruction.lexicalContextsForClosureCapture().stream()
                        .noneMatch(candidate ->
                                candidate == outerObject || candidate == innerObject));
    }

    @Test
    void writeSelectionAssignsOnlyTheSelectedDestination() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue lexical = new ProtosObjectValue(root);
        ProtosObjectValue current = new ProtosObjectValue(root);

        prototype.createLocalSlot("inherited", Boolean.TRUE);
        receiver.createLocalSlot("receiverLocal", Boolean.TRUE);
        lexical.createLocalSlot("captured", Boolean.TRUE);
        current.createLocalSlot("local", Boolean.TRUE);

        ProtosActivation activation =
                ProtosTestPrelude.activation(current, List.of(lexical), receiver);

        assign(activation, "local", Boolean.FALSE);
        assertSame(Boolean.FALSE, current.readLocalSlot("local").orElseThrow());
        assertFalse(lexical.hasLocalSlot("local"));
        assertFalse(receiver.hasLocalSlot("local"));

        assign(activation, "captured", Boolean.FALSE);
        assertSame(Boolean.FALSE, lexical.readLocalSlot("captured").orElseThrow());
        assertFalse(current.hasLocalSlot("captured"));
        assertFalse(receiver.hasLocalSlot("captured"));

        assign(activation, "receiverLocal", Boolean.FALSE);
        assertSame(Boolean.FALSE, receiver.readLocalSlot("receiverLocal").orElseThrow());
        assertFalse(current.hasLocalSlot("receiverLocal"));
        assertFalse(lexical.hasLocalSlot("receiverLocal"));
        assertFalse(prototype.hasLocalSlot("receiverLocal"));
    }

    @Test
    void writeSelectionNeverTraversesReceiverDelegationParents() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue prototype = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(prototype);
        ProtosObjectValue lexical = new ProtosObjectValue(root);
        ProtosObjectValue current = new ProtosObjectValue(root);

        prototype.createLocalSlot("inherited", Boolean.TRUE);

        ProtosActivation activation =
                ProtosTestPrelude.activation(current, List.of(lexical), receiver);

        for (String name : List.of("inherited", "missing")) {
            ProtosSignalException signal =
                    assertThrows(ProtosSignalException.class, () -> selectWrite(activation, name));
            assertSame(
                    ProtosTestPrelude.slotNotFoundPrototype(),
                    signal.error().parent().orElseThrow());
        }
        assertSame(Boolean.TRUE, prototype.readLocalSlot("inherited").orElseThrow());
        assertFalse(receiver.hasLocalSlot("inherited"));
        assertFalse(current.hasLocalSlot("inherited"));
        assertFalse(lexical.hasLocalSlot("inherited"));
    }

    @Test
    void missingReadSignalsSlotNotFound() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosActivation activation =
                ProtosTestPrelude.activation(
                        new ProtosObjectValue(root), List.of(), new ProtosObjectValue(root));

        ProtosSignalException signal =
                assertThrows(ProtosSignalException.class, () -> read(activation, "missing"));
        assertSame(
                ProtosTestPrelude.slotNotFoundPrototype(),
                signal.error().parent().orElseThrow());
    }

    @Test
    void cachedAndUncachedSelectionAgreeAfterDynamicContextShadowing() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue nearer = new ProtosObjectValue(root);
        ProtosObjectValue owner = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        Object original = new ProtosStringValue("original");
        Object shadow = new ProtosStringValue("shadow");
        owner.createLocalSlot("name", original);
        receiver.createLocalSlot("name", Boolean.FALSE);
        ProtosActivation activation = ProtosTestPrelude.activation(
                current, List.of(nearer, owner), receiver);
        ProtosLexicalSelectionNode cached = ProtosLexicalSelectionNodeGen.create();
        assertSame(original, cached.execute(activation, "name", false));
        nearer.createLocalSlot("name", shadow);
        assertSame(shadow, cached.execute(activation, "name", false));
        assertSame(shadow, read(activation, "name"));
        nearer.removeLocalSlot("name");
        owner.removeLocalSlot("name");
        assertSame(Boolean.FALSE, cached.execute(activation, "name", false));
        current.createLocalSlot("name", ProtosNullValue.INSTANCE);
        assertSame(ProtosNullValue.INSTANCE, cached.execute(activation, "name", false));
        assertSame(ProtosNullValue.INSTANCE, read(activation, "name"));
    }

    @Test
    void removedSelectedWriteOwnerDoesNotRedirectToAnotherScope() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue selected = new ProtosObjectValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        Object original = new ProtosStringValue("selected");
        Object untouched = new ProtosStringValue("outer");
        selected.createLocalSlot("name", original);
        outer.createLocalSlot("name", untouched);
        ProtosActivation activation = ProtosTestPrelude.activation(
                current, List.of(selected, outer), new ProtosObjectValue(root));
        ResolvedLexicalWriteTarget destination = selectWrite(activation, "name");
        assertSame(original, selected.removeLocalSlot("name"));
        current.createLocalSlot("name", ProtosNullValue.INSTANCE);
        assertThrows(ProtosSignalException.class,
                () -> ProtosFrameLocalBindings.assignResolvedLexicalTarget(
                        activation, destination, "name", Boolean.TRUE));
        assertFalse(selected.hasLocalSlot("name"));
        assertSame(untouched, outer.readLocalSlot("name").orElseThrow());
        assertSame(ProtosNullValue.INSTANCE, current.readLocalSlot("name").orElseThrow());
    }

    @Test
    void sameCachedSelectorObservesCurrentCreationNullRemovalAndFixedWriteOwner() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue current = new ProtosObjectValue(root);
        ProtosObjectValue captured = new ProtosObjectValue(root);
        Object capturedValue = new ProtosStringValue("captured");
        Object localValue = new ProtosStringValue("local");
        Object assignedValue = new ProtosStringValue("assigned");
        captured.createLocalSlot("name", capturedValue);
        ProtosActivation activation = ProtosTestPrelude.activation(
                current, List.of(captured), new ProtosObjectValue(root));
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        assertSame(capturedValue, selection.execute(activation, "name", false));
        current.createLocalSlot("name", localValue);
        assertSame(localValue, selection.execute(activation, "name", false));
        assertSame(capturedValue, captured.readLocalSlot("name").orElseThrow());
        current.assignLocalSlot("name", ProtosNullValue.INSTANCE);
        assertSame(ProtosNullValue.INSTANCE, selection.execute(activation, "name", false));
        assertSame(ProtosNullValue.INSTANCE, current.removeLocalSlot("name"));
        assertSame(capturedValue, selection.execute(activation, "name", false));
        ResolvedLexicalWriteTarget destination = assertInstanceOf(
                ResolvedLexicalWriteTarget.class, selection.execute(activation, "name", true));
        current.createLocalSlot("name", localValue);
        assertSame(assignedValue, ProtosFrameLocalBindings.assignResolvedLexicalTarget(
                activation, destination, "name", assignedValue));
        assertSame(assignedValue, captured.readLocalSlot("name").orElseThrow());
        assertSame(localValue, current.readLocalSlot("name").orElseThrow());
        assertSame(localValue, selection.execute(activation, "name", false));
    }

    @Test
    void unpublishedHeaderWithoutPhysicalLayoutIsNotAnAbsenceProof() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue captured = new ProtosObjectValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        captured.createLocalSlot("name", ProtosNullValue.INSTANCE);
        ProtosActivation caller = ProtosTestPrelude.activation(
                new ProtosObjectValue(root), List.of(), receiver);
        ProtosClosureValue closure = new ProtosClosureValue(null, List.of(captured), receiver);
        Object[] arguments = ProtosFrameArguments.compactImmediateMethodCall(
                closure, receiver, root, caller, new Object[0]);
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        assertFalse(ProtosLexicalSelectionNode.hasNoCurrentScopeStorage(arguments, "name", null));
        assertNull(selection.execute(arguments, "name", false));
        assertNull(selection.execute(arguments, "name", true));
        assertSame(closure, arguments[0]);
        assertSame(ProtosNullValue.INSTANCE,
                ProtosLexicalSelectionNodeGen.create().execute(arguments, "name", false,
                        ProtosBytecodeRootNode.EMPTY_LEXICAL_FRAME_LAYOUT));
        assertSame(closure, arguments[0]);
    }

    @Test
    void lexicalLayoutCopiesEveryArrayAndUsesContentEqualityPerRoot() {
        try (org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context
                .newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                com.oracle.truffle.api.bytecode.BytecodeLocal[] locals =
                        new com.oracle.truffle.api.bytecode.BytecodeLocal[4];
                var roots = ProtosBytecodeRootNodeGen.create(
                        com.oracle.truffle.api.TruffleLanguage.LanguageReference
                                .create(ProtosLanguage.class).get(null),
                        com.oracle.truffle.api.bytecode.BytecodeConfig.DEFAULT,
                        builder -> {
                            builder.beginRoot();
                            for (int index = 0; index < 3; index++) {
                                locals[index] = builder.createLocal("local" + index, null);
                            }
                            builder.beginReturn();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endReturn();
                            builder.endRoot();
                            builder.beginRoot();
                            locals[3] = builder.createLocal("other", null);
                            builder.beginReturn();
                            builder.emitLoadConstant(ProtosNullValue.INSTANCE);
                            builder.endReturn();
                            builder.endRoot();
                        });
                String[] names = {new String("first"), "middle", "last"};
                int[] offsets = {0, 1, 2};
                boolean[] cells = {false, true, false};
                com.oracle.truffle.api.bytecode.LocalAccessor[] accessors = {
                    com.oracle.truffle.api.bytecode.LocalAccessor.constantOf(locals[0]),
                    com.oracle.truffle.api.bytecode.LocalAccessor.constantOf(locals[1]),
                    com.oracle.truffle.api.bytecode.LocalAccessor.constantOf(locals[2])
                };
                var firstAccessor = accessors[0];
                var layout = new ProtosBytecodeRootNode.LexicalFrameLayout(
                        names, offsets, cells, accessors);
                names[0] = "changed";
                offsets[0] = 99;
                cells[0] = true;
                accessors[0] = accessors[2];
                assertEquals(3, layout.size());
                assertEquals("first", layout.name(0));
                assertEquals(0, layout.offset(0));
                assertFalse(layout.isCell(0));
                assertTrue(layout.isCell(1));
                assertSame(firstAccessor, layout.accessor(0));
                for (int index = 0; index < layout.size(); index++) {
                    String equalName = new String(layout.name(index));
                    assertNotSame(layout.name(index), equalName);
                    assertEquals(index, layout.indexOf(equalName));
                }
                assertEquals(-1, layout.indexOf("missing"));
                assertEquals(-1, layout.indexOf("changed"));
                var other = new ProtosBytecodeRootNode.LexicalFrameLayout(
                        new String[] {"other"}, new int[] {0}, new boolean[] {false},
                        new com.oracle.truffle.api.bytecode.LocalAccessor[] {
                            com.oracle.truffle.api.bytecode.LocalAccessor.constantOf(locals[3])
                        });
                roots.getNode(0).recordLexicalFrameLayout(layout);
                roots.getNode(1).recordLexicalFrameLayout(other);
                assertSame(layout, roots.getNode(0).lexicalFrameLayoutOrNull());
                assertSame(other, roots.getNode(1).lexicalFrameLayoutOrNull());
                assertEquals(-1, other.indexOf("first"));
                assertEquals(-1, layout.indexOf("other"));

                ProtosObjectValue objectRoot = ProtosObjectValue.rootObject();
                ProtosObjectValue captured = new ProtosObjectValue(objectRoot);
                captured.createLocalSlot("first", ProtosNullValue.INSTANCE);
                captured.createLocalSlot("missing", ProtosNullValue.INSTANCE);
                ProtosActivation caller = ProtosTestPrelude.activation(
                        new ProtosObjectValue(objectRoot), List.of(), new ProtosObjectValue(objectRoot));
                ProtosClosureValue closure = new ProtosClosureValue(
                        null, List.of(captured), caller.receiver());
                Object[] arguments = ProtosFrameArguments.compactImmediateMethodCall(
                        closure, caller.receiver(), objectRoot, caller, new Object[0]);
                var selection = ProtosLexicalSelectionNodeGen.create();
                assertFalse(ProtosLexicalSelectionNode.hasNoCurrentScopeStorage(
                        arguments, new String("first"), layout));
                assertNull(ProtosLexicalSelectionNodeGen.create()
                                .execute(arguments, "first", false, layout),
                        "physical membership requests frame resolution before captured lookup");
                assertSame(closure, arguments[0]);
                assertSame(ProtosNullValue.INSTANCE,
                        selection.execute(arguments, "missing", false, layout));
                assertSame(closure, arguments[0]);
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void physicalMultiNameReadsPrecedeCapturedNullWithoutPublishingActivation() throws Exception {
        try (org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context
                .newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                var prelude = new ProtosCoreBootstrap().bootstrap(
                        java.nio.file.Path.of("protos", "lib", "core"));
                ProtosActivation module = prelude.newModuleActivation();
                var source = com.oracle.truffle.api.source.Source.newBuilder(
                        ProtosLanguage.ID, "(first, middle, last) => [first, middle, last]",
                        "multi-name-physical-selection.protos").build();
                ProtosClosureValue template = assertInstanceOf(ProtosClosureValue.class,
                        ProtosLanguageContext.current().parsePublic(source).call(module));
                ProtosObjectValue captured = new ProtosObjectValue(ProtosObjectValue.rootObject());
                for (String name : List.of("first", "middle", "last")) {
                    captured.createLocalSlot(name, ProtosNullValue.INSTANCE);
                }
                captured.assignLocalSlot("middle", new ProtosStringValue("captured middle"));
                var capturedContexts = new java.util.ArrayList<>(template.capturedLexicalContexts());
                capturedContexts.set(0, captured);
                ProtosClosureValue closure = new ProtosClosureValue(
                        template.definition(), capturedContexts, module.receiver(), null, null,
                        module.prelude().orElseThrow(), template.executionPlan().orElseThrow());
                Object first = new ProtosStringValue("first");
                Object last = new ProtosStringValue("last");
                var entry = ProtosDispatchTestSupport.direct(
                        closure, module, first, ProtosNullValue.INSTANCE, last);
                List<Object> values = assertInstanceOf(
                        com.guillermomolina.protos.runtime.ProtosArrayValue.class,
                        ProtosDispatchTestSupport.enter(entry)).indexedSnapshot();
                assertSame(first, values.get(0));
                assertSame(ProtosNullValue.INSTANCE, values.get(1));
                assertSame(last, values.get(2));
                assertSame(closure, entry.targetArguments()[0]);
            } finally {
                context.leave();
            }
        }
    }

    @Test
    void cachedModuleLocationRemainsUsableAcrossStableStorageMutations() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        ProtosActivation caller = ProtosTestPrelude.activation(new ProtosObjectValue(root), List.of(), receiver);
        ProtosActivation module = caller.prelude().orElseThrow().newModuleActivation();
        ProtosObjectValue context = module.context();
        String name = "stableModuleName";
        Object initial = new ProtosStringValue("initial");
        Object replacement = new ProtosStringValue("replacement");
        Object recreated = new ProtosStringValue("recreated");
        Object fallback = new ProtosStringValue("receiver");
        context.createLocalSlot(name, initial);
        receiver.createLocalSlot(name, fallback);
        ProtosActivation activation = deferredSelectionActivation(List.of(context), receiver);
        ProtosLexicalEnvironment scope = activation.capturedLexicalEnvironmentForRuntime();
        assertNull(scope.outer());
        ProtosModuleBindingLocation location = assertInstanceOf(ProtosModuleBindingLocation.class,
                ProtosModuleBindingLocation.ofOrNull(context, name));
        assertInstanceOf(ProtosModuleBindingLocation.class,
                ProtosLexicalSelectionNode.moduleLocationOrNull(scope, name));
        assertTrue(location.hasCurrentStorage());
        var installation = location.storageInstallationAssumption();
        assertTrue(installation.isValid());
        var get = com.oracle.truffle.api.object.DynamicObject.GetNode.getUncached();
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        for (Object value : new Object[] {initial, replacement, ProtosNullValue.INSTANCE}) {
            context.assignLocalSlot(name, value);
            for (int repeat = 0; repeat < 8; repeat++) {
                assertSame(value, selection.execute(activation, name, false));
                assertSame(value, location.readOrNull(get));
                assertTrue(location.isPresent(get));
                assertTrue(location.hasCurrentStorage());
                assertTrue(installation.isValid());
                assertSame(installation, location.storageInstallationAssumption());
            }
        }
        assertSame(ProtosNullValue.INSTANCE, context.removeLocalSlot(name));
        assertFalse(context.hasLocalSlot(name));
        assertFalse(location.isPresent(get));
        assertNull(location.readOrNull(get));
        assertSame(fallback, selection.execute(activation, name, false));
        assertTrue(location.hasCurrentStorage());
        assertTrue(installation.isValid());
        assertSame(installation, location.storageInstallationAssumption());
        context.createLocalSlot(name, recreated);
        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(recreated, selection.execute(activation, name, false));
            assertSame(recreated, location.readOrNull(get));
            assertTrue(location.isPresent(get));
            assertTrue(location.hasCurrentStorage());
            assertTrue(installation.isValid());
            assertSame(installation, location.storageInstallationAssumption());
        }
        assertSame(context, scope.context());
        assertSame(context, module.context());
        assertNull(activation.materializedContextOrNullForRuntime());
    }

    @Test
    void frameBackedContextDoesNotExposeItsContainerShapeAsBindingStorage() throws Exception {
        try (org.graalvm.polyglot.Context entered = org.graalvm.polyglot.Context
                .newBuilder(ProtosLanguage.ID).build()) {
            entered.initialize(ProtosLanguage.ID);
            entered.enter();
            try {
                var prelude = new ProtosCoreBootstrap().bootstrap(
                        java.nio.file.Path.of("protos", "lib", "core"));
                ProtosActivation module = prelude.newModuleActivation();
                var source = com.oracle.truffle.api.source.Source.newBuilder(
                        ProtosLanguage.ID, "() => { probe: null\n context }",
                        "frame-backed-store-query.protos").build();
                ProtosClosureValue closure = assertInstanceOf(ProtosClosureValue.class,
                        ProtosLanguageContext.current().parsePublic(source).call(module));
                Object[] arguments = ProtosFrameArguments.compactDirectClosureCall(
                        closure, module, null, new Object[0]);
                ProtosExecutionContextValue context = assertInstanceOf(ProtosExecutionContextValue.class,
                        ProtosDispatchTestSupport.target(closure).call(arguments));
                ProtosLexicalEnvironment scope = ProtosLexicalEnvironment.ofContexts(List.of(context));
                assertSame(context, scope.context());
                assertTrue(scope.hasLocalSlotForRuntime("probe"));
                assertSame(ProtosNullValue.INSTANCE, scope.readLocalSlotForRuntime("probe").orElseThrow());
                assertNull(com.oracle.truffle.api.object.DynamicObject.GetNode.getUncached()
                        .execute(context, "probe", null));
                assertNull(scope.outer());
                assertNull(ProtosModuleBindingLocation.ofOrNull(context, "probe"));
                assertNull(ProtosLexicalSelectionNode.moduleLocationOrNull(scope, "probe"));
                ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
                Object fallback = new ProtosStringValue("receiver");
                Object replacement = new ProtosStringValue("frame binding");
                receiver.createLocalSlot("probe", fallback);
                ProtosActivation readerState = deferredSelectionActivation(List.of(context), receiver);
                ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
                for (int repeat = 0; repeat < 8; repeat++) {
                    assertSame(ProtosNullValue.INSTANCE, selection.execute(readerState, "probe", false));
                }
                context.assignLocalSlot("probe", replacement);
                assertSame(replacement, selection.execute(readerState, "probe", false));
                assertNull(com.oracle.truffle.api.object.DynamicObject.GetNode.getUncached()
                        .execute(context, "probe", null));
                assertSame(replacement, context.removeLocalSlot("probe"));
                assertSame(fallback, selection.execute(readerState, "probe", false));
                context.createLocalSlot("probe", ProtosNullValue.INSTANCE);
                assertTrue(context.hasLocalSlot("probe"));
                assertSame(ProtosNullValue.INSTANCE, selection.execute(readerState, "probe", false));
                assertSame(context, scope.context());
                assertNull(readerState.materializedContextOrNullForRuntime());
                assertFalse(context.usesObjectStorageForRuntime());
                context.freeze();
                assertTrue(context.isFrozen());
                for (int repeat = 0; repeat < 8; repeat++) {
                    assertSame(ProtosNullValue.INSTANCE, selection.execute(readerState, "probe", false));
                }
                readerState.context().createLocalSlot("probe", replacement);
                assertSame(replacement, selection.execute(readerState, "probe", false));
                readerState.context().removeLocalSlot("probe");
                assertSame(ProtosNullValue.INSTANCE, selection.execute(readerState, "probe", false));
            } finally {
                entered.leave();
            }
        }
    }

    @Test
    void sameSelectorReadsLiveMapBackedOwnersWithNoncanonicalNames() {
        for (boolean executionContexts : new boolean[] {false, true}) {
            ProtosObjectValue root = ProtosObjectValue.rootObject();
            ProtosObjectValue inner = executionContexts
                    ? new ProtosExecutionContextValue(root) : new ProtosObjectValue(root);
            ProtosObjectValue outer = executionContexts
                    ? new ProtosExecutionContextValue(root) : new ProtosObjectValue(root);
            ProtosObjectValue receiver = new ProtosObjectValue(root);
            Object outside = new ProtosStringValue("outside");
            Object inside = new ProtosStringValue("inside");
            Object recreated = new ProtosStringValue("recreated");
            Object fallback = new ProtosStringValue("receiver");
            String name = new String("liveMapName");
            String equivalent = new String("liveMapName");
            assertNotSame(name, equivalent);
            outer.createLocalSlot(name, outside);
            receiver.createLocalSlot(name, fallback);
            // A trailing scope keeps the mutable Context owners off the module-location path.
            ProtosActivation activation = deferredSelectionActivation(
                    List.of(inner, outer, new ProtosObjectValue(root)), receiver);
            ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
            var chain = activation.capturedLexicalEnvironmentForRuntime();
            assertSame(outside, selection.execute(activation, name, false));
            assertSame(outside, selection.execute(activation, equivalent, false));
            for (Object value : new Object[] {recreated, ProtosNullValue.INSTANCE, outside}) {
                outer.assignLocalSlot(name, value);
                assertSame(value, selection.execute(activation, name, false));
                assertSame(value, selection.execute(activation, equivalent, false));
            }
            for (int round = 0; round < 8; round++) {
                assertSame(outside, selection.execute(activation, name, false));
                assertTrue(ProtosLexicalSelectionNode.nearerScopesLackBinding(chain, 1, name));
                inner.createLocalSlot(name, inside);
                assertFalse(ProtosLexicalSelectionNode.nearerScopesLackBinding(chain, 1, name));
                assertSame(inside, selection.execute(activation, name, false));
                inner.assignLocalSlot(name, ProtosNullValue.INSTANCE);
                assertSame(ProtosNullValue.INSTANCE, selection.execute(activation, name, false));
                assertFalse(ProtosLexicalSelectionNode.nearerScopesLackBinding(chain, 1, name));
                assertSame(ProtosNullValue.INSTANCE, inner.removeLocalSlot(name));
                assertSame(outside, selection.execute(activation, name, false));
                outer.removeLocalSlot(name);
                assertSame(fallback, selection.execute(activation, name, false));
                outer.createLocalSlot(name, recreated);
                assertSame(recreated, selection.execute(activation, name, false));
                assertSame(recreated, selection.execute(activation, equivalent, false));
                outer.assignLocalSlot(name, outside);
            }
            assertNull(activation.materializedContextOrNullForRuntime());
        }
    }

    @Test
    void cachedReceiverYieldsToLaterMapBackedBindingIncludingGuestNull() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosExecutionContextValue inner = new ProtosExecutionContextValue(root);
        ProtosObjectValue receiver = new ProtosObjectValue(root);
        Object fallback = new ProtosStringValue("receiver");
        receiver.createLocalSlot("lateMapName", fallback);
        ProtosActivation activation = deferredSelectionActivation(
                List.of(inner, new ProtosObjectValue(root)), receiver);
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        String name = new String("lateMapName");
        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(fallback, selection.execute(activation, name, false));
        }
        inner.createLocalSlot(name, ProtosNullValue.INSTANCE);
        assertSame(ProtosNullValue.INSTANCE, selection.execute(activation, name, false));
        inner.removeLocalSlot(name);
        assertSame(fallback, selection.execute(activation, name, false));
        Object recreated = new ProtosStringValue("lexical");
        inner.createLocalSlot(name, recreated);
        assertSame(recreated, selection.execute(activation, name, false));
        assertSame(fallback, receiver.readLocalSlot(name).orElseThrow());
        assertNull(activation.materializedContextOrNullForRuntime());
    }

    @Test
    void cachedMapBackedWriteSelectionChecksPresenceAndKeepsChosenDestination() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosExecutionContextValue inner = new ProtosExecutionContextValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        Object outside = new ProtosStringValue("outside");
        Object assigned = new ProtosStringValue("assigned");
        outer.createLocalSlot("liveWrite", outside);
        inner.createLocalSlot("liveWrite", ProtosNullValue.INSTANCE);
        ProtosActivation activation = deferredSelectionActivation(
                List.of(inner, outer), new ProtosObjectValue(root));
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        for (int round = 0; round < 8; round++) {
            var selected = assertInstanceOf(ResolvedLexicalWriteTarget.class,
                    selection.execute(activation, "liveWrite", true));
            ProtosFrameLocalBindings.assignResolvedLexicalTarget(activation, selected, "liveWrite", assigned);
            assertSame(assigned, inner.readLocalSlot("liveWrite").orElseThrow());
            assertSame(outside, outer.readLocalSlot("liveWrite").orElseThrow());
            inner.assignLocalSlot("liveWrite", ProtosNullValue.INSTANCE);
        }
        var fixed = assertInstanceOf(ResolvedLexicalWriteTarget.class,
                selection.execute(activation, "liveWrite", true));
        inner.removeLocalSlot("liveWrite");
        assertThrows(ProtosSignalException.class, () -> ProtosFrameLocalBindings.assignResolvedLexicalTarget(
                activation, fixed, "liveWrite", assigned));
        assertFalse(inner.hasLocalSlot("liveWrite"));
        assertSame(outside, outer.readLocalSlot("liveWrite").orElseThrow());
        var next = assertInstanceOf(ResolvedLexicalWriteTarget.class,
                selection.execute(activation, "liveWrite", true));
        ProtosFrameLocalBindings.assignResolvedLexicalTarget(activation, next, "liveWrite", assigned);
        assertSame(assigned, outer.readLocalSlot("liveWrite").orElseThrow());
        assertFalse(inner.hasLocalSlot("liveWrite"));
        inner.createLocalSlot("liveWrite", ProtosNullValue.INSTANCE);
        var recreated = assertInstanceOf(ResolvedLexicalWriteTarget.class,
                selection.execute(activation, "liveWrite", true));
        ProtosFrameLocalBindings.assignResolvedLexicalTarget(activation, recreated, "liveWrite", outside);
        assertSame(outside, inner.readLocalSlot("liveWrite").orElseThrow());
        assertSame(assigned, outer.readLocalSlot("liveWrite").orElseThrow());
        assertNull(activation.materializedContextOrNullForRuntime());
    }

    @Test
    void frozenOwnerValueIsStableAcrossReads() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        for (Object value : new Object[] {
                4096L, 1.25d, Boolean.FALSE, new ProtosObjectValue(root), ProtosNullValue.INSTANCE}) {
            ProtosObjectValue owner = new ProtosObjectValue(root);
            owner.createLocalSlot("frozenValue", value);
            owner.freeze();
            ProtosActivation activation = deferredSelectionActivation(
                    List.of(owner), new ProtosObjectValue(root));
            ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
            for (int repeat = 0; repeat < 8; repeat++) {
                Object selected = selection.execute(activation, "frozenValue", false);
                assertEquals(value.getClass(), selected.getClass());
                if (value instanceof Long || value instanceof Double || value instanceof Boolean) {
                    assertEquals(value, selected);
                } else {
                    assertSame(value, selected);
                }
                assertEquals(selected, read(activation, "frozenValue"));
            }
            assertSame(owner, activation.capturedLexicalEnvironmentForRuntime()
                    .materializedContextOrNullForRuntime());
            assertThrows(IllegalStateException.class,
                    () -> owner.assignLocalSlot("frozenValue", Boolean.TRUE));
            assertThrows(IllegalStateException.class,
                    () -> owner.createLocalSlot("other", Boolean.TRUE));
            assertThrows(IllegalStateException.class, () -> owner.removeLocalSlot("frozenValue"));
            assertNull(activation.materializedContextOrNullForRuntime());
        }
    }

    @Test
    void frozenOwnerIdentitySeparatesValues() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue first = new ProtosObjectValue(root);
        ProtosObjectValue second = new ProtosObjectValue(root);
        Object firstValue = new ProtosObjectValue(root);
        Object secondValue = new ProtosObjectValue(root);
        first.createLocalSlot("frozenValue", firstValue);
        second.createLocalSlot("frozenValue", secondValue);
        first.freeze();
        second.freeze();
        ProtosActivation firstReader = deferredSelectionActivation(List.of(first), root);
        ProtosActivation secondReader = deferredSelectionActivation(List.of(second), root);
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(firstValue, selection.execute(firstReader, "frozenValue", false));
            assertSame(secondValue, selection.execute(secondReader, "frozenValue", false));
        }
    }

    @Test
    void frozenOwnerStillYieldsToNearerShadowing() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue owner = new ProtosObjectValue(root);
        ProtosObjectValue nearer = new ProtosObjectValue(root);
        Object value = new ProtosObjectValue(root);
        owner.createLocalSlot("frozenValue", value);
        owner.freeze();
        ProtosActivation activation = deferredSelectionActivation(List.of(nearer, owner), root);
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(value, selection.execute(activation, "frozenValue", false));
            nearer.createLocalSlot("frozenValue", ProtosNullValue.INSTANCE);
            assertSame(ProtosNullValue.INSTANCE, selection.execute(activation, "frozenValue", false));
            assertSame(ProtosNullValue.INSTANCE, read(activation, "frozenValue"));
            nearer.removeLocalSlot("frozenValue");
            assertSame(value, selection.execute(activation, "frozenValue", false));
        }
    }

    @Test
    void mutableAndDeferredOwnersRetainLiveSelection() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosObjectValue referenced = new ProtosObjectValue(root);
        referenced.createLocalSlot("state", Boolean.FALSE);
        ProtosObjectValue frozen = new ProtosObjectValue(root);
        frozen.createLocalSlot("frozenValue", referenced);
        frozen.freeze();
        ProtosActivation owner = deferredSelectionActivation(List.of(frozen), root);
        ProtosLexicalEnvironment scope = owner.lexicalEnvironmentForClosureCapture();
        assertNull(ProtosLexicalSelectionNode.absentScopeLocationOrNull(scope, 1, "frozenValue"));
        ProtosClosureValue closure = new ProtosClosureValue(
                null, scope, root, null, null, owner.prelude().orElseThrow(), null);
        Object[] arguments = ProtosFrameArguments.compactDirectClosureCall(
                closure, owner, null, new Object[0]);
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(referenced, selection.execute(arguments, "frozenValue", false,
                    ProtosBytecodeRootNode.EMPTY_LEXICAL_FRAME_LAYOUT));
            assertTrue(scope.isDeferredForRuntime());
            assertNull(owner.materializedContextOrNullForRuntime());
            assertSame(closure, arguments[0]);
        }
        referenced.assignLocalSlot("state", Boolean.TRUE);
        assertFalse(referenced.isFrozen());
        assertSame(Boolean.TRUE, referenced.readLocalSlot("state").orElseThrow());
        ProtosActivation reader = ProtosFrameArguments.activation(arguments);
        assertSame(referenced, selection.execute(arguments, "frozenValue", false));
        assertSame(reader, ProtosFrameArguments.publishedActivation(arguments));
        ProtosObjectValue mutable = owner.context();
        for (int repeat = 0; repeat < 4; repeat++) {
            mutable.createLocalSlot("frozenValue", Boolean.FALSE);
            assertSame(Boolean.FALSE, selection.execute(arguments, "frozenValue", false));
            mutable.assignLocalSlot("frozenValue", ProtosNullValue.INSTANCE);
            assertSame(ProtosNullValue.INSTANCE, selection.execute(arguments, "frozenValue", false));
            mutable.removeLocalSlot("frozenValue");
            assertSame(referenced, selection.execute(arguments, "frozenValue", false));
        }
        assertSame(mutable, scope.context());
        assertSame(mutable, scope.materializedContextOrNullForRuntime());
        assertNull(reader.materializedContextOrNullForRuntime());
    }

    @Test
    void compactStableAbsenceKeepsClosureEnvironmentsAndPublicationIndependent() {
        String name = "compactStableAbsenceBindingProbe";
        ProtosObjectValue root = ProtosObjectValue.rootObject();

        ProtosExecutionContextValue firstNearer =
                new ProtosExecutionContextValue(root);
        ProtosExecutionContextValue secondNearer =
                new ProtosExecutionContextValue(root);

        ProtosObjectValue firstOuter = new ProtosObjectValue(root);
        ProtosObjectValue secondOuter = new ProtosObjectValue(root);

        Object firstValue = new ProtosStringValue("first");
        Object secondValue = new ProtosStringValue("second");

        firstOuter.createLocalSlot(name, firstValue);
        secondOuter.createLocalSlot(name, secondValue);
        firstOuter.freeze();
        secondOuter.freeze();

        ProtosActivation firstReader = deferredSelectionActivation(
                List.of(firstNearer, firstOuter), root);
        ProtosActivation secondReader = deferredSelectionActivation(
                List.of(secondNearer, secondOuter), root);

        var firstEnvironment =
                firstReader.capturedLexicalEnvironmentForRuntime();
        var secondEnvironment =
                secondReader.capturedLexicalEnvironmentForRuntime();

        ProtosClosureValue firstClosure = new ProtosClosureValue(
                null, firstEnvironment, root, null, null,
                firstReader.prelude().orElseThrow(), null);

        ProtosClosureValue sameEnvironmentClosure = new ProtosClosureValue(
                null, firstEnvironment, root, null, null,
                firstReader.prelude().orElseThrow(), null);

        ProtosClosureValue secondClosure = new ProtosClosureValue(
                null, secondEnvironment, root, null, null,
                secondReader.prelude().orElseThrow(), null);

        assertNotSame(firstClosure, sameEnvironmentClosure);
        assertNotSame(firstEnvironment, secondEnvironment);

        assertSame(firstEnvironment,
                sameEnvironmentClosure.capturedLexicalEnvironmentForRuntime());

        Object[] firstArguments =
                ProtosFrameArguments.compactDirectClosureCall(
                        firstClosure, firstReader, null, new Object[0]);

        Object[] sameEnvironmentArguments =
                ProtosFrameArguments.compactDirectClosureCall(
                        sameEnvironmentClosure, firstReader, null, new Object[0]);

        Object[] secondArguments =
                ProtosFrameArguments.compactDirectClosureCall(
                        secondClosure, secondReader, null, new Object[0]);

        var layout = ProtosBytecodeRootNode.EMPTY_LEXICAL_FRAME_LAYOUT;

        var firstLocation =
                ProtosModuleBindingLocation.storageLocationOrNull(firstNearer, name);

        var property = firstLocation.storageMissingPropertyAssumption(
                firstLocation.storageShapeIfMissing());

        assertTrue(property.isValid());

        ProtosLexicalSelectionNode selection =
                ProtosLexicalSelectionNodeGen.create();

        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(firstValue,
                    selection.execute(firstArguments, name, false, layout));
            assertSame(firstValue,
                    selection.execute(sameEnvironmentArguments, name, false, layout));
            assertSame(secondValue,
                    selection.execute(secondArguments, name, false, layout));
        }

        assertSame(firstClosure, firstArguments[0]);
        assertSame(sameEnvironmentClosure, sameEnvironmentArguments[0]);
        assertSame(secondClosure, secondArguments[0]);

        // Use another site to check publication while absence is still stable.
        ProtosLexicalSelectionNode publicationSelection =
                ProtosLexicalSelectionNodeGen.create();

        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(firstValue,
                    publicationSelection.execute(
                            firstArguments, name, false, layout));
        }

        ProtosActivation published =
                ProtosFrameArguments.activation(firstArguments);

        assertNull(ProtosLexicalSelectionNode.compactClosure(firstArguments));

        assertSame(firstValue,
                publicationSelection.execute(firstArguments, name, false));

        published.context().createLocalSlot(name, ProtosNullValue.INSTANCE);

        assertSame(ProtosNullValue.INSTANCE,
                publicationSelection.execute(firstArguments, name, false));

        published.context().removeLocalSlot(name);

        assertSame(firstValue,
                publicationSelection.execute(firstArguments, name, false));

        // Mutation of a captured scope must invalidate the stable selection.
        secondNearer.createLocalSlot(name, ProtosNullValue.INSTANCE);

        assertFalse(property.isValid());

        assertSame(firstValue,
                selection.execute(sameEnvironmentArguments, name, false, layout));

        assertSame(ProtosNullValue.INSTANCE,
                selection.execute(secondArguments, name, false, layout));

        secondNearer.removeLocalSlot(name);

        assertSame(secondValue,
                selection.execute(secondArguments, name, false, layout));

        firstNearer.createLocalSlot(name, secondValue);

        assertSame(secondValue,
                selection.execute(sameEnvironmentArguments, name, false, layout));

        firstNearer.removeLocalSlot(name);

        assertSame(firstValue,
                selection.execute(sameEnvironmentArguments, name, false, layout));

        assertSame(sameEnvironmentClosure, sameEnvironmentArguments[0]);
        assertSame(secondClosure, secondArguments[0]);
    }

    @Test
    void stableMissingPropertySelectionObservesCreationRemovalAndRecreation() {
        String name = "stableMissingPropertyBindingProbe";
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosExecutionContextValue nearer = new ProtosExecutionContextValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        Object outside = new ProtosStringValue("outside");
        Object inside = new ProtosStringValue("inside");

        outer.createLocalSlot(name, outside);
        outer.freeze();

        ProtosActivation reader = deferredSelectionActivation(
                List.of(nearer, outer), root);

        ProtosModuleBindingLocation location =
                ProtosModuleBindingLocation.storageLocationOrNull(nearer, name);

        var missingShape = location.storageShapeIfMissing();
        var installation = location.storageInstallationAssumption();
        var property = location.storageMissingPropertyAssumption(missingShape);

        assertTrue(installation.isValid());
        assertTrue(missingShape.getValidAssumption().isValid());
        assertTrue(property.isValid());

        ProtosLexicalSelectionNode selection =
                ProtosLexicalSelectionNodeGen.create();

        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(outside, selection.execute(reader, name, false));
        }

        // An unrelated Shape transition does not establish this binding.
        nearer.createLocalSlot("stableMissingPropertyUnrelatedProbe", inside);

        assertFalse(location.hasStorageShape(missingShape));
        assertTrue(property.isValid());
        assertTrue(installation.isValid());
        assertSame(outside, selection.execute(reader, name, false));

        // A stale Shape must not grant a newly acquired proof.
        assertFalse(
                location.storageMissingPropertyAssumption(missingShape).isValid());

        // A present guest null is a nearer binding, not absence.
        nearer.createLocalSlot(name, ProtosNullValue.INSTANCE);

        assertFalse(property.isValid());
        assertTrue(installation.isValid());
        assertNull(location.storageShapeIfMissing());
        assertSame(ProtosNullValue.INSTANCE,
                selection.execute(reader, name, false));
        assertSame(ProtosNullValue.INSTANCE, read(reader, name));

        nearer.assignLocalSlot(name, inside);

        assertSame(inside, selection.execute(reader, name, false));
        assertSame(inside, read(reader, name));

        nearer.removeLocalSlot(name);

        assertSame(outside, selection.execute(reader, name, false));
        assertSame(outside, read(reader, name));
        assertFalse(property.isValid());

        nearer.createLocalSlot(name, inside);

        assertSame(inside, selection.execute(reader, name, false));
        assertSame(inside, read(reader, name));

        nearer.removeLocalSlot(name);
        nearer.removeLocalSlot("stableMissingPropertyUnrelatedProbe");

        assertSame(outside, selection.execute(reader, name, false));
        assertTrue(installation.isValid());
        assertNull(reader.materializedContextOrNullForRuntime());
    }

    @Test
    void sharedShapePropertyInvalidationPreservesIndependentStorageSelection() {
        String name = "sharedShapeMissingPropertyBindingProbe";
        ProtosObjectValue root = ProtosObjectValue.rootObject();

        ProtosExecutionContextValue first =
                new ProtosExecutionContextValue(root);
        ProtosExecutionContextValue second =
                new ProtosExecutionContextValue(root);

        ProtosObjectValue outer = new ProtosObjectValue(root);
        Object outside = new ProtosStringValue("outside");
        outer.createLocalSlot(name, outside);
        outer.freeze();

        ProtosActivation firstReader = deferredSelectionActivation(
                List.of(first, outer), root);
        ProtosActivation secondReader = deferredSelectionActivation(
                List.of(second, outer), root);

        var firstLocation =
                ProtosModuleBindingLocation.storageLocationOrNull(first, name);
        var secondLocation =
                ProtosModuleBindingLocation.storageLocationOrNull(second, name);

        var firstShape = firstLocation.storageShapeIfMissing();
        var secondShape = secondLocation.storageShapeIfMissing();

        assertSame(firstShape, secondShape);

        var firstInstallation = firstLocation.storageInstallationAssumption();
        var secondInstallation = secondLocation.storageInstallationAssumption();

        assertNotSame(firstInstallation, secondInstallation);

        var firstProperty =
                firstLocation.storageMissingPropertyAssumption(firstShape);
        var secondProperty =
                secondLocation.storageMissingPropertyAssumption(secondShape);

        assertTrue(firstProperty.isValid());
        assertTrue(secondProperty.isValid());

        ProtosLexicalSelectionNode selection =
                ProtosLexicalSelectionNodeGen.create();

        for (int repeat = 0; repeat < 8; repeat++) {
            assertSame(outside, selection.execute(firstReader, name, false));
            assertSame(outside, selection.execute(secondReader, name, false));
        }

        second.createLocalSlot(name, ProtosNullValue.INSTANCE);

        assertFalse(secondProperty.isValid());
        assertTrue(firstLocation.hasStorageShape(firstShape));
        assertTrue(firstInstallation.isValid());
        assertTrue(secondInstallation.isValid());

        assertSame(outside, selection.execute(firstReader, name, false));
        assertSame(ProtosNullValue.INSTANCE,
                selection.execute(secondReader, name, false));

        second.removeLocalSlot(name);

        assertSame(outside, selection.execute(firstReader, name, false));
        assertSame(outside, selection.execute(secondReader, name, false));

        first.createLocalSlot(name, ProtosNullValue.INSTANCE);

        assertSame(ProtosNullValue.INSTANCE,
                selection.execute(firstReader, name, false));
        assertSame(outside, selection.execute(secondReader, name, false));

        first.removeLocalSlot(name);

        assertSame(outside, selection.execute(firstReader, name, false));
        assertSame(outside, selection.execute(secondReader, name, false));
        assertNull(firstReader.materializedContextOrNullForRuntime());
        assertNull(secondReader.materializedContextOrNullForRuntime());
    }

    @Test
    void negativeArraySelectionTracksPhysicalStorageShapeAndFixedWriteDestination() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosExecutionContextValue nearer = new ProtosExecutionContextValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        Object outside = new ProtosStringValue("outside");
        Object inside = new ProtosStringValue("inside");
        outer.createLocalSlot("Array", outside);
        ProtosActivation reader = deferredSelectionActivation(List.of(nearer, outer), root);
        ProtosLexicalEnvironment scope = reader.capturedLexicalEnvironmentForRuntime();
        ProtosModuleBindingLocation absence = assertInstanceOf(ProtosModuleBindingLocation.class,
                ProtosLexicalSelectionNode.absentScopeLocationOrNull(scope, 1, "Array"));
        var missingShape = absence.storageShapeIfMissing();
        var contextShape = nearer.getShape();
        assertNotSame(nearer.getShape(), missingShape);
        assertTrue(absence.hasCurrentStorage());
        assertTrue(absence.hasStorageShape(missingShape));
        assertNull(ProtosLexicalSelectionNode.absentScopeLocationOrNull(scope, 2, "Array"));
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        for (int round = 0; round < 8; round++) {
            assertSame(outside, selection.execute(reader, "Array", false));
            var destination = assertInstanceOf(ResolvedLexicalWriteTarget.class,
                    selection.execute(reader, "Array", true));
            var beforeCreation = absence.storageShapeIfMissing();
            assertTrue(absence.hasStorageShape(beforeCreation));
            nearer.createLocalSlot("Array", inside);
            assertSame(contextShape, nearer.getShape());
            assertFalse(absence.hasStorageShape(beforeCreation));
            assertNull(absence.storageShapeIfMissing());
            assertSame(inside, selection.execute(reader, "Array", false));
            assertSame(inside, read(reader, "Array"));
            // An intervening nearer creation never redirects an already selected write.
            ProtosFrameLocalBindings.assignResolvedLexicalTarget(reader, destination, "Array", outside);
            assertSame(inside, nearer.readLocalSlot("Array").orElseThrow());
            nearer.assignLocalSlot("Array", ProtosNullValue.INSTANCE);
            assertNull(absence.storageShapeIfMissing());
            assertSame(ProtosNullValue.INSTANCE, selection.execute(reader, "Array", false));
            assertSame(ProtosNullValue.INSTANCE, read(reader, "Array"));
            nearer.removeLocalSlot("Array");
            assertTrue(absence.hasStorageShape(absence.storageShapeIfMissing()));
            assertSame(outside, selection.execute(reader, "Array", false));
            assertSame(outside, read(reader, "Array"));
        }
        var beforeUnrelatedCreation = absence.storageShapeIfMissing();
        nearer.createLocalSlot("unrelated", inside);
        assertFalse(absence.hasStorageShape(beforeUnrelatedCreation));
        assertSame(outside, selection.execute(reader, "Array", false));
        nearer.removeLocalSlot("unrelated");
        assertSame(outside, selection.execute(reader, "Array", false));
        assertSame(nearer, scope.materializedContextOrNullForRuntime());
        assertNull(reader.materializedContextOrNullForRuntime());
    }

    @Test
    void negativeArraySelectionSeparatesNearerStorageIdentitiesAndObservesPublication() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        ProtosExecutionContextValue first = new ProtosExecutionContextValue(root);
        ProtosExecutionContextValue second = new ProtosExecutionContextValue(root);
        ProtosObjectValue outer = new ProtosObjectValue(root);
        Object outside = new ProtosStringValue("outside");
        outer.createLocalSlot("Array", outside);
        ProtosActivation firstReader = deferredSelectionActivation(List.of(first, outer), root);
        ProtosActivation secondReader = deferredSelectionActivation(List.of(second, outer), root);
        ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
        ProtosModuleBindingLocation firstLocation = ProtosModuleBindingLocation.storageLocationOrNull(first, "Array");
        var firstShape = firstLocation.storageShapeIfMissing();

        var secondLocation = ProtosModuleBindingLocation.storageLocationOrNull(
                second, "Array");

        var firstInstallation = firstLocation.storageInstallationAssumption();
        var secondInstallation = secondLocation.storageInstallationAssumption();

        assertNotSame(firstInstallation, secondInstallation);
        assertTrue(firstInstallation.isValid());
        assertTrue(secondInstallation.isValid());

        for (int round = 0; round < 8; round++) {
            assertSame(outside, selection.execute(firstReader, "Array", false));
            assertSame(outside, selection.execute(secondReader, "Array", false));
            second.createLocalSlot("Array", ProtosNullValue.INSTANCE);
            assertTrue(firstLocation.hasStorageShape(firstShape));
            assertSame(outside, selection.execute(firstReader, "Array", false));
            assertSame(ProtosNullValue.INSTANCE, selection.execute(secondReader, "Array", false));
            second.removeLocalSlot("Array");
            assertTrue(firstInstallation.isValid());
            assertTrue(secondInstallation.isValid());
        }
        var closure = new ProtosClosureValue(null,
                firstReader.capturedLexicalEnvironmentForRuntime(), root, null, null,
                firstReader.prelude().orElseThrow(), null);
        Object[] arguments = ProtosFrameArguments.compactDirectClosureCall(
                closure, firstReader, null, new Object[0]);
        var compactSelection = ProtosLexicalSelectionNodeGen.create();
        assertSame(outside, compactSelection.execute(arguments, "Array", false,
                ProtosBytecodeRootNode.EMPTY_LEXICAL_FRAME_LAYOUT));
        assertSame(closure, arguments[0]);
        ProtosActivation published = ProtosFrameArguments.activation(arguments);
        assertSame(outside, compactSelection.execute(arguments, "Array", false));
        published.context().createLocalSlot("Array", ProtosNullValue.INSTANCE);
        assertSame(ProtosNullValue.INSTANCE, compactSelection.execute(arguments, "Array", false));
        published.context().removeLocalSlot("Array");
        assertSame(outside, compactSelection.execute(arguments, "Array", false));
    }

    @Test
    void negativeArraySelectionFollowsAuthorityHandoffAndGuestRedefinition() throws Exception {
        try (org.graalvm.polyglot.Context entered = org.graalvm.polyglot.Context
                .newBuilder(ProtosLanguage.ID).build()) {
            entered.initialize(ProtosLanguage.ID);
            entered.enter();
            try {
                var prelude = new ProtosCoreBootstrap().bootstrap(java.nio.file.Path.of("protos", "lib", "core"));
                ProtosActivation module = prelude.newModuleActivation();
                var source = com.oracle.truffle.api.source.Source.newBuilder(ProtosLanguage.ID,
                        "() => { probe: null\n context }", "negative-array-handoff.protos").build();
                ProtosClosureValue ownerClosure = assertInstanceOf(ProtosClosureValue.class,
                        ProtosLanguageContext.current().parsePublic(source).call(module));
                ProtosActivation owner = ProtosActivation.forClosureInvocation(
                        ownerClosure, List.of(), prelude, module.actorModuleState(),
                        module.currentModuleKey().orElse(null), module.executionDomain());
                ProtosExecutionContextValue nearer = assertInstanceOf(ProtosExecutionContextValue.class, owner.context());
                ProtosActivation reader = deferredSelectionActivation(
                        List.of(nearer, module.capturedLexicalEnvironmentForRuntime().context()),
                        assertInstanceOf(ProtosObjectValue.class, module.receiver()));
                ProtosLexicalSelectionNode selection = ProtosLexicalSelectionNodeGen.create();
                ProtosModuleBindingLocation absence = ProtosModuleBindingLocation.storageLocationOrNull(nearer, "Array");
                var missingShape = absence.storageShapeIfMissing();
                var installation = absence.storageInstallationAssumption();
                assertTrue(installation.isValid());

                Object arrayFactory = selection.execute(reader, "Array", false);
                for (int repeat = 0; repeat < 8; repeat++) {
                    assertSame(arrayFactory, selection.execute(reader, "Array", false));
                }
                assertSame(prelude.bindings().readLocalSlot("Array").orElseThrow(), arrayFactory);
                assertSame(nearer, ownerClosure.executionPlan().orElseThrow().executeBytecodeActivationForTesting(owner));
                assertFalse(absence.hasCurrentStorage());
                assertFalse(installation.isValid(),
                        "Retiring the authority invalidates installed lookup proofs");
                assertFalse(absence.storageInstallationAssumption().isValid(),
                        "A retired storage location must never renew its proof");

                // The retired store retains its Shape; its installation is invalid.
                assertTrue(absence.hasStorageShape(missingShape));
                assertNull(ProtosModuleBindingLocation.storageLocationOrNull(nearer, "Array"));
                assertSame(arrayFactory, selection.execute(reader, "Array", false));
                nearer.createLocalSlot("Array", ProtosNullValue.INSTANCE);
                assertSame(ProtosNullValue.INSTANCE, selection.execute(reader, "Array", false));
                nearer.removeLocalSlot("Array");
                assertSame(arrayFactory, selection.execute(reader, "Array", false));

                var readerSource = com.oracle.truffle.api.source.Source.newBuilder(ProtosLanguage.ID,
                        "() => Array", "negative-array-dynamic-read.protos").build();
                ProtosClosureValue guestReader = assertInstanceOf(ProtosClosureValue.class,
                        ProtosLanguageContext.current().parsePublic(readerSource).call(module));
                for (int round = 0; round < 8; round++) {
                    assertSame(arrayFactory, ProtosDispatchTestSupport.enter(ProtosDispatchTestSupport.direct(guestReader, module)));
                }
                module.context().createLocalSlot("Array", ProtosNullValue.INSTANCE);
                assertSame(ProtosNullValue.INSTANCE,
                        ProtosDispatchTestSupport.enter(ProtosDispatchTestSupport.direct(guestReader, module)));
                module.context().removeLocalSlot("Array");
                assertSame(arrayFactory, ProtosDispatchTestSupport.enter(ProtosDispatchTestSupport.direct(guestReader, module)));
            } finally {
                entered.leave();
            }
        }
    }

    private static ProtosActivation deferredSelectionActivation(
            List<ProtosObjectValue> scopes, ProtosObjectValue receiver) {
        ProtosActivation caller = ProtosTestPrelude.activation(
                new ProtosObjectValue(ProtosObjectValue.rootObject()), List.of(), receiver);
        ProtosClosureValue closure = new ProtosClosureValue(
                null, scopes, receiver, null, null, caller.prelude().orElseThrow());
        return ProtosFrameArguments.activation(ProtosFrameArguments.compactDirectClosureCall(
                closure, caller, null, new Object[0]));
    }

    private static Object read(ProtosActivation activation, String name) {
        return ProtosLexicalSelectionNodeGen.getUncached().execute(activation, name, false);
    }

    private static ResolvedLexicalWriteTarget selectWrite(ProtosActivation activation, String name) {
        return assertInstanceOf(
                ResolvedLexicalWriteTarget.class,
                ProtosLexicalSelectionNodeGen.getUncached().execute(activation, name, true));
    }

    private static void assign(ProtosActivation activation, String name, Object value) {
        ProtosFrameLocalBindings.assignResolvedLexicalTarget(
                activation, selectWrite(activation, name), name, value);
    }
}
