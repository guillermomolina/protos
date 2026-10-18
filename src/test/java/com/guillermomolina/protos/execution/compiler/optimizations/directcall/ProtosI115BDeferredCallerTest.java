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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeClosureOneTail;
import com.guillermomolina.protos.execution.ProtosClosureCallPreparation.DirectClosureExecutableFacts;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosDynamicControlState;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosTask;
import com.guillermomolina.protos.runtime.control.ProtosReturnHome;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

/**
 * I115-B: the converged terminal direct Call1 ({@code InvokeClosureOneTail}).
 *
 * <p>The B5+ header of an admitted, proven non-suspending terminal call may
 * carry the caller's own compact frame arguments in slot 3 instead of a
 * materialized caller activation. These tests prove, on real
 * guest-materialized Closures, that cheap readers answer without publishing
 * that caller, that every reader needing the exact caller resolves the one
 * activation of that invocation (publishing it into the caller's argument
 * 0), that the other B5 producers keep their activation contract, and that
 * guest call sites keep their D013 {@code call} selection, captures and
 * Context projection through both cache tiers. Suspension, cancellation,
 * {@code ensure}, errors and nonlocal returns through the unchanged UNKNOWN
 * transport remain covered by {@code ProtosI112TailContinuationTest}.
 */
final class ProtosI115BDeferredCallerTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final int PAST_CACHE_LIMITS = 12;
    private static final Object SUPPLIED = ProtosNullValue.INSTANCE;

    // ---- A, B, F. A deferred caller is resolved only when observed ------------------

    @Test
    void deferredCallerIsPublishedOnlyWhenObserved() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-a-define.protos", module);
            ProtosClosureValue outer = closure("make()\n", module);
            ProtosClosureValue inner = closure("make()\n", module);

            Object[] callerArguments =
                    ProtosFrameArguments.compactDirectClosureCallOne(outer, module, null, SUPPLIED);
            Object[] calleeArguments =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            inner, (Object) callerArguments, SUPPLIED);

            // A: the fixed B5 layout, with the caller's own frame arguments in slot 3.
            assertEquals(ProtosFrameArguments.HEADER_LENGTH + 1, calleeArguments.length);
            assertSame(inner, calleeArguments[0]);
            assertSame(callerArguments, calleeArguments[3]);
            assertTrue(ProtosFrameArguments.acceptsPrimitiveResult(calleeArguments));
            assertSame(SUPPLIED, ProtosFrameArguments.compactSuppliedArgument(calleeArguments, 0));

            // F: provenance answered without resolving the deferred caller.
            assertSame(
                    module.prelude().orElse(null),
                    ProtosFrameArguments.preludeOrNull(calleeArguments));
            assertSame(
                    module.task().orElse(null),
                    ProtosFrameArguments.compactTask(calleeArguments));
            assertUnpublished(callerArguments);
            assertUnpublished(calleeArguments);

            // B: the first observer publishes the caller's single activation.
            ProtosActivation caller = ProtosFrameArguments.compactCaller(calleeArguments);
            assertSame(caller, callerArguments[0]);
            assertSame(caller, ProtosFrameArguments.activation(callerArguments));
            assertSame(caller, ProtosFrameArguments.resolveExactCaller(callerArguments));
            assertSame(caller, ProtosFrameArguments.compactCaller(calleeArguments));
            assertUnpublished(calleeArguments);
            assertSameProvenance(module, caller);

            ProtosActivation callee = ProtosFrameArguments.activation(calleeArguments);
            assertSame(callee, calleeArguments[0]);
            assertSame(callerArguments, calleeArguments[3], "slot 3 is never rewritten");
            assertNotSame(caller, callee);
            assertSameProvenance(module, callee);
            assertSame(callee, ProtosFrameArguments.activation(calleeArguments));
        });
    }

    @Test
    void materializingTheCalleeFirstPublishesTheCallerExactlyOnce() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-b-define.protos", module);
            Object[] callerArguments =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure("make()\n", module), module, null, SUPPLIED);
            Object[] calleeArguments =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            closure("make()\n", module), (Object) callerArguments, SUPPLIED);

            ProtosActivation callee = ProtosFrameArguments.activation(calleeArguments);
            ProtosActivation caller =
                    assertInstanceOf(ProtosActivation.class, callerArguments[0]);
            assertSame(caller, ProtosFrameArguments.activation(callerArguments));
            assertSame(caller, ProtosFrameArguments.resolveExactCaller(callerArguments));
            assertSame(caller, ProtosFrameArguments.compactCaller(calleeArguments));
            assertSameProvenance(caller, callee);
        });
    }

    // ---- C, K. Published callers and the other B5 producers ---------------------------

    @Test
    void publishedCallersAndOtherProducersKeepTheActivationSlot() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-c-define.protos", module);
            ProtosClosureValue inner = closure("make()\n", module);

            Object[] published =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            inner, (Object) module, SUPPLIED);
            assertSame(module, published[3]);
            assertSame(module, ProtosFrameArguments.resolveExactCaller(module));
            assertSame(module, ProtosFrameArguments.compactCaller(published));

            // A caller already published in its own frame arguments resolves to it.
            Object[] callerArguments =
                    ProtosFrameArguments.compactDirectClosureCallOne(inner, module, null, SUPPLIED);
            ProtosActivation caller = ProtosFrameArguments.activation(callerArguments);
            Object[] afterPublication =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            inner, (Object) callerArguments, SUPPLIED);
            assertSame(caller, ProtosFrameArguments.compactCaller(afterPublication));
            assertSame(caller, ProtosFrameArguments.unmaterializedFullHeaderCaller(afterPublication));

            // K: every other producer still writes the exact caller activation.
            assertSame(module,
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(inner, module, SUPPLIED)[3]);
            assertSame(module,
                    ProtosFrameArguments.compactDirectClosureCallZeroNoHome(inner, module)[3]);
            assertSame(module,
                    ProtosFrameArguments.compactDirectClosureCall(
                            inner, module, null, new Object[] {SUPPLIED, SUPPLIED})[3]);
            assertSame(module,
                    ProtosFrameArguments.compactImmediateMethodCall(
                            inner, inner, ProtosObjectValue.rootObject(), module,
                            new Object[] {SUPPLIED})[3]);
            Object[] method =
                    ProtosFrameArguments.compactImmediateMethodCallOne(
                            inner, inner, ProtosObjectValue.rootObject(), module,
                            inner.invocationReturnHomeForRuntime(), SUPPLIED);
            assertSame(module, ProtosFrameArguments.compactCaller(method));
            assertSame(module, ProtosFrameArguments.unmaterializedInheritedProvenanceCaller(method));
        });
    }

    // ---- D. Reentrant chains keep one identity per invocation ------------------------

    @Test
    void chainOfDeferredCallersPublishesOneActivationPerInvocation() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-d-define.protos", module);
            Object[] first =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure("make()\n", module), module, null, SUPPLIED);
            Object[] second =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            closure("make()\n", module), (Object) first, SUPPLIED);
            Object[] third =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            closure("make()\n", module), (Object) second, SUPPLIED);

            assertSame(module.prelude().orElse(null), ProtosFrameArguments.preludeOrNull(third));
            assertSame(module.task().orElse(null), ProtosFrameArguments.compactTask(third));
            assertUnpublished(first);
            assertUnpublished(second);
            assertUnpublished(third);

            ProtosActivation thirdActivation = ProtosFrameArguments.activation(third);
            ProtosActivation secondActivation =
                    assertInstanceOf(ProtosActivation.class, second[0]);
            ProtosActivation firstActivation =
                    assertInstanceOf(ProtosActivation.class, first[0]);
            assertNotSame(firstActivation, secondActivation);
            assertNotSame(secondActivation, thirdActivation);
            assertNotSame(firstActivation, thirdActivation);
            assertSame(secondActivation, ProtosFrameArguments.compactCaller(third));
            assertSame(firstActivation, ProtosFrameArguments.resolveExactCaller(first));
            assertSame(secondActivation, ProtosFrameArguments.activation(second));
            assertSameProvenance(module, firstActivation);
            assertSameProvenance(module, secondActivation);
            assertSameProvenance(module, thirdActivation);
        });
    }

    @Test
    void invalidCallerRepresentationsAreRejected() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-invalid-define.protos", module);
            ProtosClosureValue inner = closure("make()\n", module);

            assertThrows(IllegalArgumentException.class,
                    () -> ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            inner, (Object) "caller", SUPPLIED));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.resolveExactCaller("caller"));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.resolveExactCaller(new Object[] {SUPPLIED}));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.resolveExactCaller(
                            new Object[] {SUPPLIED, null, null, module, null}));

            Object[] arbitrary =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            inner, (Object) new Object[] {inner}, SUPPLIED);
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.compactCaller(arbitrary));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.activation(arbitrary));

            Object[] selfReferential =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            inner, (Object) module, SUPPLIED);
            selfReferential[3] = selfReferential;
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.compactCaller(selfReferential));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.activation(selfReferential));
            assertUnpublished(selfReferential);
        });
    }

    @Test
    void cyclicDeferredCallerChainsAreRejectedWithoutPublication() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-cycle-define.protos", module);
            ProtosClosureValue template = closure("make()\n", module);
            for (int links : new int[] {2, 3}) {
                Object[][] chain = new Object[links][];
                chain[0] = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                        preludeFree(template), (Object) module, SUPPLIED);
                for (int i = 1; i < links; i++) {
                    chain[i] = deferredCall(preludeFree(template), chain[i - 1]);
                }
                chain[0][3] = chain[links - 1];
                Object[] entry = chain[links - 1];
                String label = links + "-link cycle";

                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.compactTask(entry), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.preludeOrNull(entry), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.compactCaller(entry), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.resolveExactCaller(entry), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.unmaterializedInheritedProvenanceCaller(entry),
                        label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.activation(entry), label);
                for (Object[] link : chain) {
                    assertUnpublished(link);
                }
            }
        });
    }

    @Test
    void malformedHeadersAreNotAcceptedAsDeferredCallers() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-malformed-define.protos", module);
            ProtosClosureValue inner = closure("make()\n", module);
            ProtosReturnHome home = ProtosReturnHome.unobservable();
            ProtosObjectValue root = ProtosObjectValue.rootObject();
            List<Object[]> malformed = List.of(
                    // Published activation in [0], but the rest is not a B5 header.
                    new Object[] {module, null, null, null, null},
                    new Object[] {module, SUPPLIED, null, module, home},
                    new Object[] {module, SUPPLIED, root, module, SUPPLIED},
                    new Object[] {module, SUPPLIED, root, SUPPLIED, home},
                    // Compact Closure in [0] with invalid kind, task, caller or home slots.
                    new Object[] {inner, null, null, module, home},
                    new Object[] {inner, SUPPLIED, null, module, home},
                    new Object[] {inner, SUPPLIED, root, null, home},
                    new Object[] {inner, SUPPLIED, root, module, null});
            for (Object[] bad : malformed) {
                String label = java.util.Arrays.toString(bad);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.resolveExactCaller(bad), label);
                Object[] callee = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                        inner, (Object) bad, SUPPLIED);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.compactCaller(callee), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.compactTask(callee), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.preludeOrNull(callee), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.activation(callee), label);
                assertUnpublished(callee);
            }

            // Positive controls: well-formed published and compact headers.
            Object[] compact = ProtosFrameArguments.compactImmediateMethodCall(
                    inner, inner, root, module, new Object[] {SUPPLIED});
            Object[] viaCompact = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    inner, (Object) compact, SUPPLIED);
            ProtosActivation compactCaller = ProtosFrameArguments.compactCaller(viaCompact);
            assertSame(compactCaller, compact[0]);
            Object[] viaPublished = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    inner, (Object) compact, SUPPLIED);
            assertSame(compactCaller, ProtosFrameArguments.compactCaller(viaPublished));
        });
    }

    // ---- Inherited Task and dynamic-control authority of a deferred chain -----------

    @Test
    void deferredChainInheritsTheTaskOfItsEntry() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-task-define.protos", module);
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosTask task = domain.createTask(null, current -> {});
            ProtosActivation owner = domainModule(module, domain);
            owner.attachTask(task);

            // Inherited from the published owner through two deferred links.
            Object[] first =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure("make()\n", module), owner, null, SUPPLIED);
            Object[] second = deferredCall(closure("make()\n", module), first);
            Object[] third = deferredCall(closure("make()\n", module), second);
            assertSame(task, ProtosFrameArguments.compactTask(third));
            assertUnpublished(first);
            assertUnpublished(second);
            ProtosActivation thirdActivation = ProtosFrameArguments.activation(third);
            assertSame(task, thirdActivation.task().orElse(null));
            assertSame(task, assertInstanceOf(ProtosActivation.class, second[0]).task().orElse(null));
            assertSame(task, assertInstanceOf(ProtosActivation.class, first[0]).task().orElse(null));

            // An explicit Task in the entry's own slot 2 is inherited the same way.
            Object[] explicit =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure("make()\n", module), module, task, SUPPLIED);
            Object[] child = deferredCall(closure("make()\n", module), explicit);
            assertSame(task, ProtosFrameArguments.compactTask(child));
            assertUnpublished(explicit);
            assertSame(task, ProtosFrameArguments.activation(child).task().orElse(null));
            assertSame(task, assertInstanceOf(ProtosActivation.class, explicit[0]).task().orElse(null));
        });
    }

    /**
     * The direct dynamic-control state an activation hands down is fixed
     * before it calls anything (a {@code handle}/{@code ensure} activation
     * creates it on entry, before its body), so a chain published late shares
     * exactly that state object, and a chain entered without one has none.
     */
    @Test
    void deferredChainKeepsTheDynamicControlAuthorityOfItsEntry() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-dynamic-define.protos", module);
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();

            ProtosActivation protectedOwner = domainModule(module, domain);
            ProtosDynamicControlState state = protectedOwner.dynamicControlState();
            Object[] first =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure("make()\n", module), protectedOwner, null, SUPPLIED);
            Object[] second = deferredCall(closure("make()\n", module), first);
            ProtosActivation secondActivation = ProtosFrameArguments.activation(second);
            assertSame(state, secondActivation.dynamicControlStateIfPresent().orElse(null));
            assertSame(
                    state,
                    assertInstanceOf(ProtosActivation.class, first[0])
                            .dynamicControlStateIfPresent().orElse(null));
            assertSame(null, secondActivation.task().orElse(null));

            ProtosActivation plainOwner = domainModule(module, domain);
            Object[] plainFirst =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure("make()\n", module), plainOwner, null, SUPPLIED);
            Object[] plainSecond = deferredCall(closure("make()\n", module), plainFirst);
            ProtosActivation plainActivation = ProtosFrameArguments.activation(plainSecond);
            assertTrue(plainActivation.dynamicControlStateIfPresent().isEmpty());
            assertTrue(assertInstanceOf(ProtosActivation.class, plainFirst[0])
                    .dynamicControlStateIfPresent().isEmpty());
        });
    }

    // ---- Late publication: Error, capture and tooling observe one identity ---------

    @Test
    void latePublicationByErrorOrToolingYieldsOneIdentity() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-late-define.protos", module);
            Object[] callerArguments =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure("make()\n", module), module, null, SUPPLIED);
            Object[] calleeArguments = deferredCall(closure("make()\n", module), callerArguments);

            // An Error created in the callee publishes the callee, then its caller.
            ProtosActivation callee = ProtosFrameArguments.activation(calleeArguments);
            assertNotNull(ProtosCoreErrors.newError(callee));
            ProtosActivation caller = assertInstanceOf(ProtosActivation.class, callerArguments[0]);

            // Tooling-style observations afterwards see exactly the same identities.
            assertTrue(ProtosFrameArguments.hasPublishedActivation(callerArguments));
            assertSame(caller, ProtosFrameArguments.publishedActivation(callerArguments));
            assertSame(caller, ProtosFrameArguments.compactCaller(calleeArguments));
            assertSame(caller, ProtosFrameArguments.resolveExactCaller(callerArguments));
            assertSame(callee, ProtosFrameArguments.activation(calleeArguments));
            assertNotNull(ProtosCoreErrors.newError(callee));
            assertSame(caller, callerArguments[0]);
            assertSame(callee, calleeArguments[0]);
        });
    }

    // ---- PROVEN and UNKNOWN entries both carry the deferred caller -----------------

    @Test
    void unknownSynchronousEntryLeavesItsCompactCallerUnpublished() throws Exception {
        withCore(module -> {
            evaluate("""
                    helper: (value) => { value }
                    make: () => { (value) => { helper(value) } }
                    """, "i115b-unknown-sync-define.protos", module);
            ProtosClosureValue callee = closure("make()\n", module);
            RootCallTarget target = directTarget(callee);
            assertFalse(InvokeClosureOneTail.provenNonSuspending(target), "UNKNOWN fixture");
            ((ProtosBytecodeRootNode) target.getRootNode()).getBytecodeNode().setUncachedThreshold(0);

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Object[] callerArguments =
                        ProtosFrameArguments.compactDirectClosureCallOne(
                                closure("make()\n", module), module, null, SUPPLIED);
                Object[] calleeArguments =
                        ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                                callee, (Object) callerArguments, evaluate("5\n", "i115b-five.protos", module));
                assertInteger(5, ProtosNumericValueSupport.guestValue(target.call(calleeArguments)));
                assertUnpublished(callerArguments);
            }
        });
    }

    @Test
    void provenEntryCallerIsStillPublishedWhenObservedAfterReturn() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115b-proven-define.protos", module);
            ProtosClosureValue callee = closure("make()\n", module);
            RootCallTarget target = directTarget(callee);
            assertTrue(InvokeClosureOneTail.provenNonSuspending(target), "PROVEN fixture");

            Object[] callerArguments =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure("make()\n", module), module, null, SUPPLIED);
            Object[] calleeArguments =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            callee, (Object) callerArguments, evaluate("6\n", "i115b-six.protos", module));
            assertInteger(6, ProtosNumericValueSupport.guestValue(target.call(calleeArguments)));
            assertUnpublished(callerArguments);

            ProtosActivation observed = ProtosFrameArguments.activation(calleeArguments);
            ProtosActivation caller = assertInstanceOf(ProtosActivation.class, callerArguments[0]);
            assertSame(caller, ProtosFrameArguments.compactCaller(calleeArguments));
            assertSame(observed, ProtosFrameArguments.activation(calleeArguments));
            assertSameProvenance(module, caller);
            assertSameProvenance(caller, observed);
        });
    }

    @Test
    void unknownTerminalEntryThatSuspendsResumesWithItsOwnState() throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
                ProtosActivation module =
                        prelude.newModuleActivation(
                                new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
                ProtosFutureValue wait = new ProtosFutureValue(prelude.futurePrototype(), domain);
                ProtosObjectValue resolved = new ProtosObjectValue(ProtosObjectValue.rootObject());
                module.context().createLocalSlot("wait", wait);
                evaluate("""
                        waiter: (value) => {
                            result: wait.value()
                            keep: (other) => { result }
                            keep(value)
                        }
                        callOne: (f, value) => { f(value) }
                        """, "i115b-suspend-define.protos", module);
                enterCachedTier("callOne", module);
                CallTarget top = ProtosLanguageContext.current().parsePublic(
                        Source.newBuilder(ProtosLanguage.ID, "callOne(waiter, 0)\n", "i115b-suspend-top.protos")
                                .mimeType(ProtosLanguage.MIME_TYPE)
                                .build());

                ProtosTask task = domain.createTask(
                        null, current -> ProtosBytecodeTaskExecution.execute(current, top, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state(), "really suspended");

                List<Object> entries = tierEntries("callOne", "guardedSource_cache", module);
                assertEquals(1, entries.size());
                DirectClosureExecutableFacts facts =
                        (DirectClosureExecutableFacts) field(entries.get(0), "cachedFacts_");
                assertFalse(InvokeClosureOneTail.provenNonSuspending(facts.target()), "UNKNOWN entry");

                assertTrue(wait.resolve(resolved, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertSame(resolved, task.result().orElseThrow(), "exact resumed result");
            } finally {
                context.leave();
            }
        }
    }

    // ---- E, H, J. Guest terminal sites through both cache tiers ----------------------

    @Test
    void freshClosuresShareTheDefinitionTargetButKeepTheirCaptures() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: (seed) => { (value) => { seed + value } }
                    callOne: (f, value) => { f(value) }
                    """, "i115b-h-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(11, evaluate("callOne(make(10), 1)\n", "i115b-h-ten.protos", module));
                assertInteger(21, evaluate("callOne(make(20), 1)\n", "i115b-h-twenty.protos", module));
            }
            List<Object> entries = tierEntries("callOne", "definitionSource_cache", module);
            assertEquals(1, entries.size());
            Object entry = entries.get(0);
            RootCallTarget target = (RootCallTarget) field(entry, "cachedTarget_");
            ProtosClosureExecutionPlan template =
                    (ProtosClosureExecutionPlan) field(entry, "cachedSourcePlan_");
            assertSame(template.bytecodeActivationTargetForComposition(), target);
            DirectClosureExecutableFacts facts =
                    (DirectClosureExecutableFacts) field(entry, "cachedFacts_");
            assertSame(target, facts.target());
            assertTrue(facts.rootCallStability().isValid());
        });
    }

    @Test
    void identityTargetIsProvenAndEntersSynchronously() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    callOne: (f, value) => { f(value) }
                    """, "i115b-j-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(1, evaluate("callOne(make(), 1)\n", "i115b-j-run.protos", module));
            }
            List<Object> entries = tierEntries("callOne", "definitionSource_cache", module);
            assertEquals(1, entries.size());
            RootCallTarget target = (RootCallTarget) field(entries.get(0), "cachedTarget_");
            assertTrue(InvokeClosureOneTail.provenNonSuspending(target));
            assertTrue(InvokeClosureOneTail.admittedTailTarget(target));
        });
    }

    @Test
    void entryClassificationFollowsTheTargetsEffectiveRootProof() throws Exception {
        withCore(module -> {
            evaluate("""
                    helper: (value) => { value }
                    make: () => { (value) => { helper(value) } }
                    callOne: (f, value) => { f(value) }
                    """, "i115b-unknown-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(5, evaluate("callOne(make(), 5)\n", "i115b-unknown-run.protos", module));
            }
            for (Object entry : tierEntries("callOne", "definitionSource_cache", module)) {
                RootCallTarget target = (RootCallTarget) field(entry, "cachedTarget_");
                ProtosBytecodeRootNode root =
                        assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
                assertEquals(
                        root.provablyNonSuspendingBody(),
                        InvokeClosureOneTail.provenNonSuspending(target));
            }
        });
    }

    @Test
    void escapingClosureCreatedByTheCalleeKeepsItsCaptures() throws Exception {
        withCore(module -> {
            evaluate("""
                    wrap: (value) => { (other) => { value + other } }
                    callOne: (f, value) => { f(value) }
                    apply: (h, other) => { h(other) }
                    """, "i115b-e-define.protos", module);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(
                        3, evaluate("apply(callOne(wrap, 1), 2)\n", "i115b-e-run.protos", module));
                assertInteger(
                        30, evaluate("apply(callOne(wrap, 10), 20)\n", "i115b-e-escape.protos", module));
            }
        });
    }

    // ---- G. D013 selection after operand evaluation, and recovery -------------------

    @Test
    void callCreatedWhileEvaluatingTheArgumentBypassesTheCachedTarget() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    late: make()
                    plain: () => { 7 }
                    arm: () => {
                        late.call: (value) => { 100 + value }
                        7
                    }
                    callWith: (f, g) => { f(g()) }
                    """, "i115b-g-define.protos", module);
            enterCachedTier("callWith", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callWith(make(), plain)\n", "i115b-g-warm.protos", module));
                assertInteger(7, evaluate("callWith(late, plain)\n", "i115b-g-late.protos", module));
            }
            for (int round = 0; round < 3; round++) {
                assertInteger(107, evaluate("callWith(late, arm)\n", "i115b-g-arm.protos", module));
                assertTrue(late(module).hasLocalCallSlotForRuntime());
                evaluate("late.removeSlot(\"call\")\n", "i115b-g-remove.protos", module);
                assertFalse(late(module).hasLocalCallSlotForRuntime());
                assertInteger(7, evaluate("callWith(late, plain)\n", "i115b-g-restored.protos", module));
                assertInteger(7, evaluate("callWith(make(), plain)\n", "i115b-g-fresh.protos", module));
            }
            /*
             * A call-valued argument lowers this site to the non-terminal Call1;
             * the same scenario at an InvokeClosureOneTail site is covered by
             * ProtosI112TailContinuationTest#callSelectionObservesAnOverrideInstalledByArgumentEvaluation.
             */
        });
    }

    // ---- I, L. NORMAL and DEBUG Contexts of one Engine ------------------------------

    @Test
    void terminalSiteProjectsAnotherProfilesClosureInBothDirections() throws Exception {
        try (Engine engine = Engine.create();
                Harness normal = new Harness(ProtosExecutionMode.NORMAL, engine);
                Harness debug = new Harness(ProtosExecutionMode.DEBUG, engine)) {
            ProtosClosureValue fromNormal = normal.call(Harness::identityClosure);
            ProtosClosureValue fromDebug = debug.call(Harness::identityClosure);
            assertProjectedTerminalCall(debug, fromNormal);
            assertProjectedTerminalCall(normal, fromDebug);
        }
    }

    @Test
    void debugProfileTerminalCallObservesOneCallerIdentity() throws Exception {
        try (Harness debug = new Harness(ProtosExecutionMode.DEBUG, null)) {
            debug.call(module -> {
                evaluate("""
                        make: () => { (value) => { value } }
                        wrap: (value) => { (other) => { value + other } }
                        callOne: (f, value) => { f(value) }
                        apply: (h, other) => { h(other) }
                        """, "i115b-debug-define.protos", module);
                enterCachedTier("callOne", module);
                for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                    assertInteger(4, evaluate("callOne(make(), 4)\n", "i115b-debug-run.protos", module));
                    assertInteger(
                            5,
                            evaluate("apply(callOne(wrap, 2), 3)\n", "i115b-debug-escape.protos", module));
                }
                return null;
            });
        }
    }

    private static void assertProjectedTerminalCall(
            Harness destination, ProtosClosureValue foreign) throws Exception {
        ProtosClosureExecutionPlan template = foreign.preparedExecutionPlanOrNullForRuntime();
        assertNotNull(template);
        RootCallTarget foreignTarget = template.bytecodeActivationTargetForComposition();
        destination.call(module -> {
            evaluate("callOne: (f, value) => { f(value) }\n", "i115b-i-define.protos", module);
            module.context().createLocalSlot("foreign", foreign);
            enterCachedTier("callOne", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("callOne(foreign, 7)\n", "i115b-i-run.protos", module));
            }
            ProtosLanguageContext context = ProtosLanguageContext.current();
            if (context.executionMode() != ProtosExecutionMode.NORMAL) {
                // The DEBUG profile lowers this final call to the non-terminal Call1.
                return null;
            }
            List<Object> entries = tierEntries("callOne", "guardedSource_cache", module);
            assertEquals(1, entries.size());
            DirectClosureExecutableFacts facts =
                    (DirectClosureExecutableFacts) field(entries.get(0), "cachedFacts_");
            assertTrue(facts.projectionRequired());
            assertSame(context, facts.enteredContext());
            assertSame(template, facts.template());
            assertNotSame(foreignTarget, facts.target());
            assertSame(
                    context.bytecodeExecutionPlanForDefinition(foreign.definition(), template)
                            .bytecodeActivationTargetForComposition(),
                    facts.target());
            assertEquals(context.executionMode(), facts.effectivePlan().bytecodeProfileForRuntime());
            return null;
        });
    }

    // ---- helpers ---------------------------------------------------------------------

    /** The same definition and captures, without an own prelude; never executed. */
    private static ProtosClosureValue preludeFree(ProtosClosureValue template) {
        return new ProtosClosureValue(
                template.definition(),
                template.capturedLexicalContexts(),
                template.capturedReceiver());
    }

    private static Object[] deferredCall(ProtosClosureValue closure, Object[] callerArguments) {
        return ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                closure, (Object) callerArguments, SUPPLIED);
    }

    private static ProtosActivation domainModule(
            ProtosActivation module, ProtosActorExecutionDomain domain) {
        ProtosPrelude prelude = module.prelude().orElseThrow();
        return prelude.newModuleActivation(
                new ProtosActorModuleState(), null, prelude.newExecutionContext(), domain);
    }

    private static RootCallTarget directTarget(ProtosClosureValue closure) {
        DirectClosureExecutableFacts facts =
                ProtosClosureCallPreparation.establishDirectClosureExecutableFactsOrNull(
                        closure, ProtosLanguageContext.current());
        assertNotNull(facts);
        return facts.target();
    }

    private static void assertUnpublished(Object[] arguments) {
        assertInstanceOf(ProtosClosureValue.class, arguments[0]);
    }

    private static void assertSameProvenance(ProtosActivation expected, ProtosActivation actual) {
        assertSame(expected.prelude().orElse(null), actual.prelude().orElse(null));
        assertSame(expected.actorModuleState(), actual.actorModuleState());
        assertSame(expected.currentModuleKey().orElse(null), actual.currentModuleKey().orElse(null));
        assertSame(expected.executionDomain(), actual.executionDomain());
        assertSame(expected.task().orElse(null), actual.task().orElse(null));
    }

    private static ProtosClosureValue late(ProtosActivation module) {
        return assertInstanceOf(
                ProtosClosureValue.class, module.context().readLocalSlot("late").orElseThrow());
    }

    /**
     * Moves the named module function's root to the cached Bytecode tier on
     * its next entry, so its operation nodes and their caches exist.
     */
    private static void enterCachedTier(String function, ProtosActivation module) {
        functionRoot(function, module).getBytecodeNode().setUncachedThreshold(0);
    }

    private static ProtosBytecodeRootNode functionRoot(String function, ProtosActivation module) {
        ProtosClosureValue closure = assertInstanceOf(
                ProtosClosureValue.class,
                module.context().readLocalSlot(function).orElseThrow());
        return closure.executionPlan().orElseThrow().bytecodeActivationRootForTesting();
    }

    /** Structural, test-only view of the terminal Call1 site's cache entries. */
    private static List<Object> tierEntries(
            String function, String cache, ProtosActivation module) throws Exception {
        Object bytecode = functionRoot(function, module).getBytecodeNode();
        Node[] cachedNodes = (Node[]) field(bytecode, "cachedNodes_");
        String nodeName = InvokeClosureOneTail.class.getSimpleName() + "_Node";
        List<Object> entries = new ArrayList<>();
        int sites = 0;
        for (Node node : cachedNodes) {
            if (node == null || !node.getClass().getSimpleName().equals(nodeName)) {
                continue;
            }
            sites++;
            for (Object entry = field(node, cache); entry != null; entry = field(entry, "next_")) {
                entries.add(entry);
            }
        }
        int counted = sites;
        assertEquals(1, counted, () -> "expected exactly one " + nodeName + " site in " + function
                + "; instructions: "
                + functionRoot(function, module).getBytecodeNode().getInstructionsAsList().stream()
                        .map(ProtosInstructionNames::of)
                        .toList()
                + "; cached tier: "
                + (cachedNodes.length > 0));
        return entries;
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException absent) {
                // Continue with the superclass.
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private interface CoreTest {
        void run(ProtosActivation module) throws Exception;
    }

    private interface ModuleAction<T> {
        T run(ProtosActivation module) throws Exception;
    }

    /** One Context of a possibly shared Engine, in an explicit profile. */
    private static final class Harness implements AutoCloseable {
        private final ProtosExecutionMode mode;
        private final Context context;
        private ProtosPrelude prelude;

        Harness(ProtosExecutionMode mode, Engine engine) {
            this.mode = mode;
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (engine != null) {
                builder.engine(engine);
            }
            if (mode == ProtosExecutionMode.DEBUG) {
                builder.allowExperimentalOptions(true).option("protos.Debug", "true");
            }
            this.context = builder.build();
            context.initialize(ProtosLanguage.ID);
        }

        <T> T call(ModuleAction<T> action) throws Exception {
            context.enter();
            try {
                assertEquals(mode, ProtosLanguageContext.current().executionMode());
                if (prelude == null) {
                    prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                }
                return action.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }

        static ProtosClosureValue identityClosure(ProtosActivation module) {
            return closure("(value) => { value }\n", module);
        }

        @Override
        public void close() {
            context.close();
        }
    }

    private static void withCore(CoreTest test) throws Exception {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.initialize(ProtosLanguage.ID);
            context.enter();
            try {
                ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
                test.run(prelude.newModuleActivation());
            } finally {
                context.leave();
            }
        }
    }

    private static Object evaluate(String characters, String name, ProtosActivation activation) {
        Source source =
                Source.newBuilder(ProtosLanguage.ID, characters, name)
                        .mimeType(ProtosLanguage.MIME_TYPE)
                        .build();
        CallTarget target = ProtosLanguageContext.current().parsePublic(source);
        return target.call(activation);
    }

    private static ProtosClosureValue closure(String characters, ProtosActivation activation) {
        return assertInstanceOf(
                ProtosClosureValue.class,
                evaluate(characters, "i115b-closure.protos", activation));
    }

    private static void assertInteger(long expected, Object actual) {
        assertEquals(
                Long.toString(expected),
                com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(actual));
    }
}
