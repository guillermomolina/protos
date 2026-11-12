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

import com.guillermomolina.protos.execution.ProtosDispatchTestSupport.DirectEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

/**
 * PERF025 virtual return home: an owning invocation of a source-backed Closure
 * whose plan statically proves that neither the Closure nor any lexical
 * descendant (parameter defaults, nested Closures at any depth, inline Object
 * bodies) contains {@code ^} runs under the non-materialized
 * {@link ProtosReturnHome#unobservable()} marker instead of a fresh home.
 * Every shape that may observe the home keeps the exact physical lifecycle.
 *
 * <p>The marker is already inactive and {@link ProtosReturnHome#complete()}
 * rejects a completed home, so a passing NLR-dead call also shows that the
 * owner neither completes it nor matches a transfer against it.
 */
final class ProtosPerf025VirtualReturnHomeTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void staticProofClassifiesDefaultsDescendantsAndObjectBodies() throws Exception {
        withCore(module -> {
            assertUnobservable(module, "() => 42");
            assertUnobservable(module, "(value) => value");
            assertUnobservable(module, "() => {\n    () => 42\n}");
            assertUnobservable(module, "(x: () => 1) => x");
            assertUnobservable(module, "() => {\n    Object {\n        m: () => 1\n    }\n}");

            assertObservable(module, "() => ^42");
            assertObservable(module, "(x: ^42) => x");
            assertObservable(module, "(x: () => ^42) => x");
            assertObservable(module, "() => {\n    () => ^42\n}");
            assertObservable(module, "() => {\n    () => {\n        () => ^42\n    }\n}");
            assertObservable(
                    module, "() => {\n    Object {\n        escape: () => { ^42 }\n    }\n}");
        });
        System.out.println("RETURN_HOME_STATIC_ANALYSIS=YES");
        System.out.println("ANALYSIS_RUN_PER_INVOCATION=NO");
    }

    @Test
    void nlrDeadInvocationsDoNotMaterializeAReturnHome() throws Exception {
        withCore(module -> {
            ProtosClosureValue constant = parsedClosure("() => 42", module);
            DirectEntry first = fastDirect(constant, module);
            assertVirtualHome(first);
            assertInteger(42, enter(first));

            ProtosClosureValue identity = parsedClosure("(value) => value", module);
            DirectEntry second =
                    fastDirect(identity, module, integer(5));
            assertVirtualHome(second);
            assertInteger(5, enter(second));
            ProtosActivation callee = ProtosFrameArguments.activation(second.targetArguments());
            assertTrue(callee.ownsReturnHome(), "the invocation still owns its (virtual) home");
            assertSame(ProtosReturnHome.unobservable(), callee.returnHome().orElseThrow());
        });
        System.out.println("SOURCE_NLR_DEAD_INVOCATION_NEW_PROTOS_RETURN_HOME=NO");
        System.out.println("SOURCE_NLR_DEAD_COMPLETE_CALL=NO");
    }

    @Test
    void nlrDeadDescendantKeepsSharedHomeProvenanceWithoutMaterialization()
            throws Exception {
        withCore(module -> {
            ProtosClosureValue outer = parsedClosure("() => {\n    () => 42\n}", module);
            DirectEntry prepared = fastDirect(outer, module);
            assertVirtualHome(prepared);
            ProtosClosureValue child =
                    assertInstanceOf(ProtosClosureValue.class, enter(prepared));

            // I115 experimental: the child does not retain its creator's
            // unobservable home; its invocation owns the virtual home.
            assertTrue(child.returnHome().isEmpty());
            ProtosActivation childInvocation =
                    ProtosActivation.forClosureInvocation(
                            child, List.of(), module.prelude().orElseThrow());
            assertTrue(childInvocation.ownsReturnHome());
            assertSame(ProtosReturnHome.unobservable(),
                    childInvocation.returnHome().orElseThrow());
        });
        System.out.println("NLR_DEAD_DESCENDANT_OWNER_HOME_MATERIALIZED=NO");
        System.out.println("NLR_DEAD_DESCENDANT_HOME_PROVENANCE=NOT_CAPTURED");
    }

    @Test
    void directAndDefaultReturnKeepPhysicalHomeLifecycle() throws Exception {
        withCore(module -> {
            ProtosClosureValue direct = parsedClosure("() => ^42", module);
            DirectEntry call = fastDirect(direct, module);
            ProtosReturnHome home = assertPhysicalActiveHome(call);
            assertInteger(42, enter(call));
            assertFalse(home.isActive(), "the owned physical home completes with the call");

            ProtosClosureValue defaulted = parsedClosure("(x: ^42) => x", module);
            DirectEntry defaultCall =
                    fastDirect(defaulted, module);
            ProtosReturnHome defaultHome = assertPhysicalActiveHome(defaultCall);
            assertInteger(42, enter(defaultCall));
            assertFalse(defaultHome.isActive());
        });
        System.out.println("DIRECT_CANONICAL_RETURN_RETAINS_PHYSICAL_HOME=YES");
        System.out.println("DEFAULT_CANONICAL_RETURN_RETAINS_PHYSICAL_HOME=YES");
    }

    @Test
    void descendantReturnRetainsOwnerHomeAtAnyDepth() throws Exception {
        withCore(module -> {
            ProtosClosureValue outer = parsedClosure("() => {\n    () => ^42\n}", module);
            DirectEntry call = fastDirect(outer, module);
            ProtosReturnHome home = assertPhysicalActiveHome(call);
            ProtosClosureValue child =
                    assertInstanceOf(ProtosClosureValue.class, enter(call));
            assertSame(home, child.returnHome().orElseThrow());

            ProtosClosureValue transitive =
                    parsedClosure("() => {\n    () => {\n        () => ^42\n    }\n}", module);
            DirectEntry transitiveCall =
                    fastDirect(transitive, module);
            ProtosReturnHome transitiveHome = assertPhysicalActiveHome(transitiveCall);
            ProtosClosureValue middle =
                    assertInstanceOf(ProtosClosureValue.class, enter(transitiveCall));
            assertSame(transitiveHome, middle.returnHome().orElseThrow());
        });
        System.out.println("OUTER_DIRECT_CANONICAL_RETURN=NO");
        System.out.println("DESCENDANT_CANONICAL_RETURN=YES");
        System.out.println("OUTER_HOME_MATERIALIZED=YES");
        System.out.println("TRANSITIVE_DESCENDANT_CANONICAL_RETURN_RETAINS_OWNER_HOME=YES");
    }

    @Test
    void nonLocalReturnSemanticsArePreserved() throws Exception {
        withCore(module -> {
            assertInteger(
                    42,
                    callParsed(module, "() => {\n    g: () => {\n        ^42\n    }\n    g()\n    0\n}"));
            assertInteger(
                    42,
                    callParsed(
                            module,
                            "() => {\n    g: () => {\n        h: () => {\n            ^42\n        }\n"
                                    + "        h()\n    }\n    g()\n    0\n}"));
            assertInteger(
                    42,
                    callParsed(
                            module,
                            "() => {\n    holder: Object {\n        escape: () => { ^42 }\n    }\n"
                                    + "    holder.escape()\n    0\n}"));

            ProtosClosureValue escaped =
                    assertInstanceOf(
                            ProtosClosureValue.class,
                            evaluate("make: () => {\n    () => {\n        ^42\n    }\n}\nmake()\n", module));
            ProtosExecutionOutcome failed =
                    ProtosRootTaskExecution.executeClosure(escaped, List.of(), module);
            assertSame(
                    ProtosCoreErrors.prototype(
                            module, ProtosCoreErrors.StandardError.INVALID_RETURN),
                    failed.error().parent().orElseThrow(),
                    "an escaped return to a completed home is InvalidReturn");
        });
        System.out.println("SAME_TASK_NLR=PRESERVED");
        System.out.println("OBJECT_BODY_DESCENDANT_RETURN_RETAINS_OWNER_HOME=YES");
        System.out.println("ESCAPED_NLR_INVALID_RETURN=PRESERVED");
    }

    @Test
    void literalInlineCallbackUnderVirtualHomeKeepsItsResult() throws Exception {
        withCore(module -> {
            ProtosClosureValue run =
                    parsedClosure("() => {\n    true.ifTrue(() => {\n        41 + 1\n    })\n}", module);
            assertTrue(planOf(run).returnHomeProvenUnobservableForRuntime());
            DirectEntry call = fastDirect(run, module);
            assertVirtualHome(call);
            assertInteger(42, enter(call));
        });
        System.out.println("PLAT044_INLINE_CALLBACK_ADMISSION=PRESERVED");
    }

    @Test
    void nativeAndUnpreparedClosuresKeepAFreshPhysicalHome() throws Exception {
        withCore(module -> {
            ProtosClosureValue nativeClosure =
                    ProtosClosureValue.nativeClosure((activation, supplied) -> ProtosNullValue.INSTANCE);
            ProtosReturnHome nativeHome = nativeClosure.invocationReturnHomeForRuntime();
            assertTrue(nativeHome.isMaterialized());
            assertTrue(nativeHome.isActive());
            assertNotSame(nativeHome, nativeClosure.invocationReturnHomeForRuntime());

            ProtosClosureValue parsed = parsedClosure("() => 42", module);
            ProtosClosureValue unprepared =
                    new ProtosClosureValue(
                            parsed.definition(), List.of(), parsed.capturedReceiver());
            assertTrue(unprepared.invocationReturnHomeForRuntime().isMaterialized());
        });
        System.out.println("NATIVE_UNKNOWN_FALLBACK=PRESERVED");
    }

    /**
     * I115-A: the executable facts of a direct Closure call are independent of
     * the instance's return home, while the per-instance home proof keeps its
     * exact classification; establishing either never materializes a home or
     * changes a captured one.
     */
    @Test
    void i115aExecutableFactsIgnoreReturnHomeWhileTheInstanceProofClassifies() throws Exception {
        withCore(module -> {
            ProtosClosureValue prepared = parsedClosure("(value) => { value }", module);
            ProtosClosureExecutionPlan plan = planOf(prepared);
            ProtosPrelude prelude = module.prelude().orElseThrow();
            ProtosLanguageContext context = ProtosLanguageContext.current();
            RootCallTarget target = plan.bytecodeActivationTargetForComposition();

            assertI115aHome(true, target, prepared, context);
            assertI115aHome(true, target, withHome(prepared, ProtosReturnHome.unobservable(), prelude), context);
            ProtosReturnHome captured = new ProtosReturnHome();
            ProtosClosureValue materialized = withHome(prepared, captured, prelude);
            assertI115aHome(false, target, materialized, context);
            assertSame(captured, materialized.returnHome().orElseThrow());
            assertTrue(captured.isActive());
            assertI115aHome(false, target, withHome(prepared,
                    ProtosReturnHome.unobservableAcceptingPrimitiveResultForRuntime(), prelude), context);

            ProtosClosureValue escaper = parsedClosure("(value) => { ^value }", module);
            assertFalse(planOf(escaper).returnHomeProvenUnobservableForRuntime());
            assertI115aHome(false, planOf(escaper).bytecodeActivationTargetForComposition(), escaper, context);

            ProtosClosureValue nativeClosure =
                    ProtosClosureValue.nativeClosure((activation, supplied) -> ProtosNullValue.INSTANCE);
            assertFalse(nativeClosure.hasUnobservableInvocationReturnHomeForRuntime());
            // I115: a native Closure has no source CallTarget and is not a source function.
            assertEquals(null, ProtosClosureCallPreparation.functionCallTarget(nativeClosure, context));
            assertFalse(ProtosFunctionCallDispatchers.CallNode.isSourceFunction(nativeClosure));

            ProtosClosureValue unprepared =
                    new ProtosClosureValue(prepared.definition(), List.of(), prepared.capturedReceiver());
            assertFalse(unprepared.hasUnobservableInvocationReturnHomeForRuntime());
        });
    }

    /** I115-A: guest call sites keep the generic semantics around direct admission. */
    @Test
    void i115aGuestCallSitesRecoverGenericSemantics() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    callOne: (f, value) => { f(value) }
                    late: make()
                    escape: () => {
                        f: (value) => { ^value }
                        callOne(f, 7)
                        0
                    }
                    """, module);
            for (int i = 0; i < 12; i++) {
                assertInteger(7, evaluate("callOne(make(), 7)\n", module));
                assertInteger(7, evaluate("escape()\n", module));
            }
            evaluate("late.call: (value) => { 100 + value }\n", module);
            // CALLABLES: a nearer call slot of a Closure is selected by the
            // ordinary invocation lookup instead of the Closure's own body.
            assertInteger(107, evaluate("callOne(late, 7)\n", module));
            evaluate("late.removeSlot(\"call\")\n", module);
            assertInteger(7, evaluate("callOne(late, 7)\n", module));
            assertInteger(7, evaluate("escape()\n", module));
        });
    }

    @Test
    void deferredRichEntryEstablishesProofBeforeSelectingHome() throws Exception {
        withCore(module -> {
            for (String source : List.of("() => 42", "() => ^42")) {
                ProtosClosureValue template = parsedClosure(source, module);
                java.util.concurrent.atomic.AtomicInteger preparations =
                        new java.util.concurrent.atomic.AtomicInteger();
                ProtosClosureValue deferred = template.parallelProjectionDeferred(
                        template.capturedLexicalContexts(), template.capturedReceiver(),
                        module.prelude().orElseThrow(), () -> {
                            preparations.incrementAndGet();
                            return planOf(template);
                        });
                assertTrue(deferred.executionPlan().isEmpty());
                ProtosActivation invocation = ProtosActivation.forClosureInvocation(
                        deferred, List.of(), module.prelude().orElseThrow());
                var task = module.executionDomain().createTask(null, current -> {});
                invocation.attachTask(task);
                assertEquals(1, preparations.get());
                ProtosReturnHome home = invocation.returnHome().orElseThrow();
                if (source.contains("^")) {
                    assertTrue(home.isMaterialized());
                    assertTrue(home.isActive());
                    assertSame(task, home.ownerTaskForRuntime());
                } else {
                    assertSame(ProtosReturnHome.unobservable(), home);
                    assertSame(null, home.ownerTaskForRuntime());
                }
                assertInteger(42, ProtosFunctionCallDispatchers.callPublishedUncached(
                        deferred, invocation));
                assertFalse(home.isActive());
                assertSame(null, home.ownerTaskForRuntime());
            }
        });
    }

    @Test
    void owningInvocationsAreDistinctAndSiblingCapturesAreIdentical() throws Exception {
        withCore(module -> {
            ProtosClosureValue direct = parsedClosure("() => ^42", module);
            DirectEntry first = fastDirect(direct, module);
            DirectEntry second = fastDirect(direct, module);
            ProtosReturnHome firstHome = assertPhysicalActiveHome(first);
            ProtosReturnHome secondHome = assertPhysicalActiveHome(second);
            assertNotSame(firstHome, secondHome);
            assertInteger(42, enter(first));
            assertFalse(firstHome.isActive());
            assertTrue(secondHome.isActive());
            assertInteger(42, enter(second));
            assertFalse(secondHome.isActive());

            ProtosClosureValue siblings = parsedClosure(
                    "() => { [() => ^41, () => ^42] }", module);
            DirectEntry owner = fastDirect(siblings, module);
            ProtosReturnHome home = assertPhysicalActiveHome(owner);
            var children = assertInstanceOf(
                    com.guillermomolina.protos.runtime.ProtosArrayValue.class, enter(owner));
            assertSame(home, assertInstanceOf(ProtosClosureValue.class,
                    children.indexedSnapshot().get(0)).returnHome().orElseThrow());
            assertSame(home, assertInstanceOf(ProtosClosureValue.class,
                    children.indexedSnapshot().get(1)).returnHome().orElseThrow());
            assertFalse(home.isActive());
        });
    }

    @Test
    void i115a4ProvenNoHomeRootStaysSpecializedForOrdinaryInvocations() throws Exception {
        withCore(module -> {
            ProtosClosureValue source = parsedClosure("() => 42", module);
            ProtosBytecodeRootNode root = rootOf(source);
            assertTrue(root.returnHomeProvenUnobservable());
            assertFalse(root.needsPhysicalHomeCompletionInEpilogue());

            assertInteger(42, enter(fastDirect(source, module)));
            assertFalse(root.needsPhysicalHomeCompletionInEpilogue());

            ProtosActivation published =
                    ProtosActivation.forClosureInvocation(
                            source, List.of(), module.prelude().orElseThrow(),
                            module.actorModuleState(), module.currentModuleKey().orElse(null),
                            module.executionDomain());
            assertFalse(published.returnHomeOrNullForRuntime().isMaterialized());
            assertInteger(42, ProtosFunctionCallDispatchers.callPublishedUncached(source, published));
            assertFalse(root.needsPhysicalHomeCompletionInEpilogue(),
                    "an unobservable home is not a published physical owner");
        });
    }

    @Test
    void i115a4CapturedPhysicalHomeDoesNotActivateOwnerCompletion() throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.prelude().orElseThrow();
            ProtosClosureValue source = parsedClosure("() => 42", module);
            var task = module.executionDomain().createTask(null, current -> {});
            ProtosReturnHome captured = new ProtosReturnHome();
            captured.bindOwnerTaskForRuntime(task);
            ProtosClosureValue child = withHome(source, captured, prelude);
            ProtosBytecodeRootNode root = rootOf(child);
            assertSame(rootOf(source), root);
            assertTrue(root.returnHomeProvenUnobservable());
            assertFalse(root.needsPhysicalHomeCompletionInEpilogue());

            ProtosActivation activation =
                    ProtosActivation.forClosureInvocation(
                            child, List.of(), prelude,
                            module.actorModuleState(), module.currentModuleKey().orElse(null),
                            module.executionDomain());
            assertFalse(activation.ownsReturnHome());
            assertSame(captured, activation.returnHomeOrNullForRuntime());
            assertInteger(42, ProtosFunctionCallDispatchers.callPublishedUncached(child, activation));
            assertTrue(captured.isActive(), "a child invocation never closes a captured home");
            assertSame(task, captured.ownerTaskForRuntime());
            assertFalse(root.needsPhysicalHomeCompletionInEpilogue(),
                    "a captured home is not owned by the invoked root");
            captured.closeOwned();
        });
    }

    @Test
    void publishedPhysicalOwnerCompletesEvenUnderNoHomeRootProof() throws Exception {
        withCore(module -> {
            ProtosClosureValue source = parsedClosure("() => 42", module);
            ProtosBytecodeRootNode root = rootOf(source);
            assertTrue(root.returnHomeProvenUnobservable());
            assertFalse(root.needsPhysicalHomeCompletionInEpilogue());
            ProtosReturnHome physical = new ProtosReturnHome();
            ProtosActivation activation =
                    ProtosActivation.forDirectClosureInvocationWithReturnHomeForRuntime(
                            source, List.of(), module.prelude().orElseThrow(),
                            module.actorModuleState(), module.currentModuleKey().orElse(null),
                            module.executionDomain(), physical);
            var task = module.executionDomain().createTask(null, current -> {});
            activation.attachTask(task);
            assertSame(task, physical.ownerTaskForRuntime());
            assertTrue(physical.isActive());
            assertInteger(42, ProtosFunctionCallDispatchers.callPublishedUncached(source, activation));
            assertTrue(root.needsPhysicalHomeCompletionInEpilogue(),
                    "published physical ownership invalidates the root before entry");
            assertSame(physical, activation.returnHome().orElseThrow());
            assertFalse(physical.isActive());
            assertSame(null, physical.ownerTaskForRuntime());

            // After invalidation an ordinary compact no-home invocation still completes.
            DirectEntry compact = fastDirect(source, module);
            assertVirtualHome(compact);
            assertInteger(42, enter(compact));
            assertTrue(root.needsPhysicalHomeCompletionInEpilogue(), "invalidation is permanent");

            var failure = module.prelude().orElseThrow().newError();
            module.context().createLocalSlot("publishedFailure", failure);
            ProtosClosureValue throwing = parsedClosure("() => { publishedFailure.signal() }", module);
            assertTrue(planOf(throwing).returnHomeProvenUnobservableForRuntime());
            ProtosBytecodeRootNode throwingRoot = rootOf(throwing);
            assertTrue(throwingRoot.returnHomeProvenUnobservable());
            assertFalse(throwingRoot.needsPhysicalHomeCompletionInEpilogue());
            ProtosReturnHome failedHome = new ProtosReturnHome();
            ProtosActivation failed =
                    ProtosActivation.forDirectClosureInvocationWithReturnHomeForRuntime(
                            throwing, List.of(), module.prelude().orElseThrow(),
                            module.actorModuleState(), module.currentModuleKey().orElse(null),
                            module.executionDomain(), failedHome);
            var signaled = org.junit.jupiter.api.Assertions.assertThrows(
                    com.guillermomolina.protos.runtime.ProtosSignalException.class,
                    () -> ProtosFunctionCallDispatchers.callPublishedUncached(throwing, failed));
            assertSame(failure, signaled.error());
            assertTrue(throwingRoot.needsPhysicalHomeCompletionInEpilogue());
            assertFalse(failedHome.isActive());
        });
    }

    @Test
    void physicalCloseReleasesTaskWithoutClosingCapturedOrVirtualHomes() throws Exception {
        withCore(module -> {
            var task = module.executionDomain().createTask(null, current -> {});
            ProtosReturnHome outer = new ProtosReturnHome();
            outer.bindOwnerTaskForRuntime(task);
            ProtosClosureValue child = withHome(parsedClosure("() => 42", module), outer,
                    module.prelude().orElseThrow());
            assertInteger(42, enter(fastDirect(child, module)));
            assertTrue(outer.isActive());
            assertSame(task, outer.ownerTaskForRuntime());
            outer.closeOwned();
            assertFalse(outer.isActive());
            assertSame(null, outer.ownerTaskForRuntime());
            outer.closeOwned();
            assertSame(null, outer.ownerTaskForRuntime());
            var virtual = ProtosReturnHome.unobservable();
            virtual.bindOwnerTaskForRuntime(task);
            virtual.closeOwned();
            assertFalse(virtual.isActive());
            assertSame(null, virtual.ownerTaskForRuntime());
        });
    }

    @Test
    void definitiveInternalFailureClosesOnlyTheOwnedPhysicalHome() throws Exception {
        withCore(module -> {
            ProtosClosureValue source = parsedClosure("() => ^42", module);
            DirectEntry entry = fastDirect(source, module);
            ProtosReturnHome home = assertPhysicalActiveHome(entry);
            var root = (ProtosBytecodeRootNode) entry.target().getRootNode();
            var frame = com.oracle.truffle.api.Truffle.getRuntime().createVirtualFrame(
                    entry.targetArguments(), root.getFrameDescriptor());
            RuntimeException failure = new RuntimeException("internal failure identity");
            assertSame(failure, root.interceptInternalException(
                    failure, frame, root.getBytecodeNode(), 0));
            assertFalse(home.isActive());

            ProtosReturnHome captured = new ProtosReturnHome();
            DirectEntry child = fastDirect(withHome(source, captured,
                    module.prelude().orElseThrow()), module);
            var childFrame = com.oracle.truffle.api.Truffle.getRuntime().createVirtualFrame(
                    child.targetArguments(), root.getFrameDescriptor());
            assertSame(failure, root.interceptInternalException(
                    failure, childFrame, root.getBytecodeNode(), 0));
            assertTrue(captured.isActive());
        });
    }

    @Test
    void ownerTransferCompletionPreservesPayloadAndUnrelatedTransferIdentity() throws Exception {
        withCore(module -> {
            var task = module.executionDomain().createTask(null, current -> {});
            Object token = new ProtosObjectValue(ProtosObjectValue.rootObject());
            ProtosReturnHome owner = new ProtosReturnHome();
            owner.bindOwnerTaskForRuntime(task);
            var returned = new com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException(
                    owner, token);
            assertSame(token, ProtosFunctionCallDispatchers.completeOwnedTransfer(owner, returned));
            assertFalse(owner.isActive());
            assertSame(null, owner.ownerTaskForRuntime());

            ProtosReturnHome inner = new ProtosReturnHome();
            inner.bindOwnerTaskForRuntime(task);
            ProtosReturnHome outer = new ProtosReturnHome();
            outer.bindOwnerTaskForRuntime(task);
            var escaping = new com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException(
                    outer, token);
            assertSame(escaping, org.junit.jupiter.api.Assertions.assertThrows(
                    com.guillermomolina.protos.runtime.control.ProtosNonLocalReturnException.class,
                    () -> ProtosFunctionCallDispatchers.completeOwnedTransfer(inner, escaping)));
            assertFalse(inner.isActive());
            assertSame(null, inner.ownerTaskForRuntime());
            assertTrue(outer.isActive());
            assertSame(task, outer.ownerTaskForRuntime());
            outer.closeOwned();

            ProtosReturnHome departing = new ProtosReturnHome();
            departing.bindOwnerTaskForRuntime(task);
            var unrelated = new ProtosTaskCancellationException();
            assertSame(unrelated, org.junit.jupiter.api.Assertions.assertThrows(
                    com.oracle.truffle.api.nodes.ControlFlowException.class,
                    () -> ProtosFunctionCallDispatchers.completeOwnedTransfer(departing, unrelated)));
            assertFalse(departing.isActive());
            assertSame(null, departing.ownerTaskForRuntime());
        });
    }

    /**
     * I115-A: each dispatcher ReturnHome mode matches the lifecycle of the
     * home written into the B5 header, independently of the call's result:
     * the virtual marker never materializes, a captured home is neither closed
     * nor rebound by the inner invocation, and an owned physical home is fresh,
     * active during the call and closed when the call really ends.
     */
    @Test
    void i115aReturnHomeModesMatchTheLifecycleOfTheWrittenHome() throws Exception {
        withCore(module -> {
            ProtosPrelude prelude = module.prelude().orElseThrow();
            var task = module.executionDomain().createTask(null, current -> {});

            ProtosClosureValue constant = parsedClosure("() => 42", module);
            assertMode(ProtosFunctionCallDispatchers.ReturnHomeMode.UNOBSERVABLE, constant);
            DirectEntry virtual = fastDirect(constant, module);
            assertVirtualHome(virtual);
            assertInteger(42, enter(virtual));
            assertFalse(ProtosReturnHome.unobservable().isMaterialized());

            ProtosClosureValue template = parsedClosure("() => {\n    unused: () => ^1\n    42\n}", module);
            ProtosReturnHome outer = new ProtosReturnHome();
            outer.bindOwnerTaskForRuntime(task);
            for (ProtosClosureValue body : List.of(constant, template)) {
                ProtosClosureValue captured = withHome(body, outer, prelude);
                assertMode(ProtosFunctionCallDispatchers.ReturnHomeMode.CAPTURED, captured);
                DirectEntry inner = fastDirect(captured, module);
                assertSame(outer, ProtosFrameArguments.compactReturnHome(inner.targetArguments()));
                assertFalse(ProtosFrameArguments.compactOwnsReturnHome(inner.targetArguments()));
                assertInteger(42, enter(inner));
                assertTrue(outer.isActive(), "an inner Closure never closes its owner's home");
                assertSame(task, outer.ownerTaskForRuntime());
            }

            ProtosClosureValue marker = withHome(template, ProtosReturnHome.unobservable(), prelude);
            assertMode(ProtosFunctionCallDispatchers.ReturnHomeMode.UNOBSERVABLE, marker);
            DirectEntry markerEntry = fastDirect(marker, module);
            assertSame(ProtosReturnHome.unobservable(),
                    ProtosFrameArguments.compactReturnHome(markerEntry.targetArguments()));
            assertInteger(42, enter(markerEntry));

            ProtosClosureValue direct = parsedClosure("() => ^42", module);
            assertMode(ProtosFunctionCallDispatchers.ReturnHomeMode.OWNED_PHYSICAL, direct);
            DirectEntry first = fastDirect(direct, module);
            DirectEntry second = fastDirect(direct, module);
            ProtosReturnHome firstHome = assertPhysicalActiveHome(first);
            ProtosReturnHome secondHome = assertPhysicalActiveHome(second);

            assertNotSame(firstHome, secondHome);

            assertInteger(42, enter(first));
            assertFalse(firstHome.isActive());

            assertInteger(42, enter(second));
            assertFalse(secondHome.isActive());

            firstHome.closeOwned();
            assertFalse(firstHome.isActive(), "closing stays idempotent");
            assertSame(null, firstHome.ownerTaskForRuntime());
            assertSame(null, secondHome.ownerTaskForRuntime());
        });
    }

    private static void assertMode(
            ProtosFunctionCallDispatchers.ReturnHomeMode expected, ProtosClosureValue closure) {
        ProtosBytecodeRootNode root = ProtosFunctionCallDispatchers.calleeRootOrNull(
                planOf(closure).bytecodeActivationTargetForComposition());
        assertSame(expected, ProtosFunctionCallDispatchers.classifyReturnHome(closure, root));
    }

    private static ProtosBytecodeRootNode rootOf(ProtosClosureValue closure) {
        return assertInstanceOf(ProtosBytecodeRootNode.class,
                ProtosDispatchTestSupport.target(closure).getRootNode());
    }

    private static ProtosClosureValue withHome(
            ProtosClosureValue prepared, ProtosReturnHome home, ProtosPrelude prelude) {
        return new ProtosClosureValue(
                prepared.definition(), prepared.capturedLexicalContexts(),
                prepared.capturedReceiver(), null, home, prelude, planOf(prepared));
    }

    private static void assertI115aHome(
            boolean unobservable,
            RootCallTarget target,
            ProtosClosureValue closure,
            ProtosLanguageContext context) {
        ProtosReturnHome before = closure.returnHome().orElse(null);
        assertEquals(unobservable, closure.hasUnobservableInvocationReturnHomeForRuntime());
        // I115: selected by representation; the CallTarget ignores the ReturnHome.
        assertTrue(ProtosFunctionCallDispatchers.CallNode.isSourceFunction(closure));
        assertSame(target, ProtosClosureCallPreparation.functionCallTarget(closure, context));
        assertSame(before, closure.returnHome().orElse(null));
    }

    private interface CoreBody {
        void run(ProtosActivation module) throws Exception;
    }

    private static void withCore(CoreBody body) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                body.run(coreModule());
            } finally {
                context.leave();
            }
        }
    }

    private static void assertUnobservable(ProtosActivation module, String characters) {
        assertTrue(
                planOf(parsedClosure(characters, module)).returnHomeProvenUnobservableForRuntime(),
                () -> "expected return-home-unobservable: " + characters);
    }

    private static void assertObservable(ProtosActivation module, String characters) {
        assertFalse(
                planOf(parsedClosure(characters, module)).returnHomeProvenUnobservableForRuntime(),
                () -> "expected return-home-observable: " + characters);
    }

    private static ProtosClosureExecutionPlan planOf(ProtosClosureValue closure) {
        return closure.executionPlan().orElseThrow();
    }

    private static void assertVirtualHome(DirectEntry call) {
        assertSame(
                ProtosReturnHome.unobservable(),
                ProtosFrameArguments.compactReturnHome(call.targetArguments()),
                "a proven-unobservable owned home is not materialized");
        assertTrue(ProtosFrameArguments.compactOwnsReturnHome(call.targetArguments()));
    }

    private static ProtosReturnHome assertPhysicalActiveHome(
            DirectEntry call) {
        ProtosReturnHome home = ProtosFrameArguments.compactReturnHome(call.targetArguments());
        assertTrue(home.isMaterialized());
        assertTrue(home.isActive());
        assertTrue(ProtosFrameArguments.compactOwnsReturnHome(call.targetArguments()));
        return home;
    }

    private static Object callParsed(ProtosActivation module, String characters) {
        return enter(fastDirect(parsedClosure(characters, module), module));
    }

    private static DirectEntry fastDirect(
            ProtosClosureValue closure, ProtosActivation caller, Object... supplied) {
        // I115: the B5 header the shared dispatcher writes for a function call.
        return ProtosDispatchTestSupport.direct(closure, caller, supplied);
    }

    private static Object enter(DirectEntry prepared) {
        return ProtosDispatchTestSupport.enter(prepared);
    }

    private static ProtosActivation coreModule() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        return prelude.newModuleActivation();
    }

    private static Object evaluate(String characters, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, "perf025-virtual-home.protos")
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return target.call(activation);
    }

    private static ProtosClosureValue parsedClosure(
            String characters, ProtosActivation activation) {
        return assertInstanceOf(ProtosClosureValue.class, evaluate(characters, activation));
    }

    private static Long integer(long value) {
        return Long.valueOf(value);
    }

    private static void assertInteger(long expected, Object value) {
        assertEquals(Long.toString(expected), com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(value));
    }
}
