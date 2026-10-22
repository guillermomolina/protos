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

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.Debugger;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeClosureOneTail;
import com.guillermomolina.protos.execution.ProtosBytecodeRootNode.InvokeMethodOneTail;
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

    @Test
    void admittedTerminalB5HeaderMatchesValidatedConstructor()
            throws Exception {
        withCore(module -> {
            evaluate(
                    "make: () => { (value) => { value } }\n",
                    "i115d-b5-define.protos",
                    module);

            ProtosClosureValue closure =
                    closure("make()\n", module);

            // Published caller: every B5 slot is identical.
            Object[] validated =
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            closure, (Object) module, SUPPLIED);

            Object[] admitted =
                    ProtosFrameArguments
                            .compactAdmittedTerminalClosureCallOneNoHome(
                                    closure, module, SUPPLIED);

            assertEquals(validated.length, admitted.length);
            for (int i = 0; i < validated.length; i++) {
                assertSame(validated[i], admitted[i]);
            }

            assertSame(
                    module,
                    ProtosFrameArguments.compactCaller(admitted));

            // Deferred caller: preserve the original B5 array
            // by reference, without publishing its activation.
            Object[] callerArguments =
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            closure, module, null, SUPPLIED);

            Object[] deferred =
                    ProtosFrameArguments
                            .compactAdmittedTerminalClosureCallOneNoHome(
                                    closure, callerArguments, SUPPLIED);

            assertEquals(
                    ProtosFrameArguments.HEADER_LENGTH + 1,
                    deferred.length);
            assertSame(closure, deferred[0]);
            assertSame(callerArguments, deferred[3]);
            assertSame(SUPPLIED, deferred[5]);
            assertTrue(
                    ProtosFrameArguments.acceptsPrimitiveResult(deferred));

            assertSame(
                    module.prelude().orElse(null),
                    ProtosFrameArguments.preludeOrNull(deferred));
            assertSame(
                    module.task().orElse(null),
                    ProtosFrameArguments.compactTask(deferred));

            assertUnpublished(callerArguments);
            assertUnpublished(deferred);

            // General construction still rejects invalid callers.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ProtosFrameArguments
                            .compactDirectClosureCallOneNoHome(
                                    closure, (Object) "invalid", SUPPLIED));
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

    // ---- I, L. Two Contexts of one Engine; an attached debugger ----------------------

    /**
     * Each Context of one Engine owns its language instance, so a terminal site projects the
     * other Context's Closure in both directions. PLAT067-B: no compilation profile is involved.
     */
    @Test
    void terminalSiteProjectsAnotherContextsClosureInBothDirections() throws Exception {
        try (Engine engine = Engine.create();
                Harness first = new Harness(false, engine);
                Harness second = new Harness(false, engine)) {
            ProtosClosureValue fromFirst = first.call(Harness::identityClosure);
            ProtosClosureValue fromSecond = second.call(Harness::identityClosure);
            assertProjectedTerminalCall(second, fromFirst);
            assertProjectedTerminalCall(first, fromSecond);
        }
    }

    @Test
    void attachedDebuggerTerminalCallObservesOneCallerIdentity() throws Exception {
        try (Harness debug = new Harness(true, null)) {
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
            assertSame(
                    context.languageForRuntime(),
                    facts.effectivePlan().language().orElseThrow());
            return null;
        });
    }

    @Test
    void provenTerminalCallPropagatesOnlyToAcceptingNumericCaller()
            throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    forwardInteger: (f) => { f(7) }
                    forwardFloat: (f) => { f(7.5) }
                    """, "i115d2-numeric-define.protos", module);

            enterCachedTier("forwardInteger", module);
            enterCachedTier("forwardFloat", module);

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(
                        7,
                        evaluate(
                                "forwardInteger(make())\n",
                                "i115d2-integer-warm.protos",
                                module));
                assertTrue(
                        ProtosNumericValueSupport.isCurrentFloat(
                                evaluate(
                                        "forwardFloat(make())\n",
                                        "i115d2-float-warm.protos",
                                        module)));
            }

            ProtosClosureValue inner = closure("make()\n", module);

            for (String name : List.of("forwardInteger", "forwardFloat")) {
                ProtosClosureValue forward = assertInstanceOf(
                        ProtosClosureValue.class,
                        module.context().readLocalSlot(name).orElseThrow());

                assertTrue(
                        functionRoot(name, module)
                                .epilogProjectsReturnCarrier());

                RootCallTarget target = directTarget(forward);

                // The inner terminal call is PROVEN.
                // An accepting B5+ caller receives the raw carrier.
                Object[] accepting =
                        ProtosFrameArguments
                                .compactAdmittedTerminalClosureCallOneNoHome(
                                        forward, module, inner);

                Object carrier = target.call(accepting);

                if (name.equals("forwardInteger")) {
                    assertEquals(7L, assertInstanceOf(Long.class, carrier));
                } else {
                    assertEquals(
                            7.5d,
                            assertInstanceOf(Double.class, carrier));
                }

                // An ordinary B5 caller never observes an
                // internal Long or Double as a guest value.
                Object[] ordinary =
                        ProtosFrameArguments.compactDirectClosureCallOne(
                                forward, module, null,
                                inner);

                Object guest = target.call(ordinary);
                assertFalse(
                        ProtosNumericValueSupport.isPrimitiveCarrier(guest));

                if (name.equals("forwardInteger")) {
                    assertInteger(7, guest);
                } else {
                    assertTrue(
                            ProtosNumericValueSupport.isCurrentFloat(guest));
                }
            }
        });
    }

    // ---- I115. Admitted B5 accessors (GraalPy PArguments / ReadFrameNode) ------------

    /**
     * The admitted accessors answer exactly what the general, validating
     * accessors answer, for a published and an unpublished caller, through
     * deferred chains of depth 1, 2 and 5, and never publish anything.
     */
    @Test
    void admittedPreludeAndTaskAgreeWithTheGeneralAccessorsAtEveryDepth() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-admitted-define.protos", module);
            ProtosClosureValue owning = closure("make()\n", module);
            ProtosClosureValue template = closure("make()\n", module);
            assertSame(null, preludeFree(template).preludeOrNullForRuntime(), "inherited fixture");

            for (int depth : new int[] {1, 2, 5}) {
                String label = "depth " + depth;
                // Unpublished entry: a compact header whose caller is the module.
                Object[] entry = ProtosFrameArguments.compactDirectClosureCallOne(
                        preludeFree(template), module, null, SUPPLIED);
                Object[][] chain = new Object[depth + 1][];
                chain[0] = entry;
                for (int i = 1; i <= depth; i++) {
                    chain[i] = deferredCall(preludeFree(template), chain[i - 1]);
                }
                Object[] leaf = chain[depth];

                // Inherited prelude and Task.
                assertSame(ProtosFrameArguments.preludeOrNull(leaf),
                        ProtosFrameArguments.admittedPreludeOrNull(leaf), label);
                assertSame(module.prelude().orElse(null),
                        ProtosFrameArguments.admittedPreludeOrNull(leaf), label);
                assertSame(ProtosFrameArguments.compactTask(leaf),
                        ProtosFrameArguments.admittedCompactTask(leaf), label);

                // A guest Closure on top of the chain: same answer as the general accessor.
                Object[] owner = deferredCall(owning, leaf);
                assertSame(ProtosFrameArguments.preludeOrNull(owner),
                        ProtosFrameArguments.admittedPreludeOrNull(owner), label);
                for (Object[] link : chain) {
                    assertUnpublished(link);
                }
                assertUnpublished(owner);

                // A published link answers with its activation, without walking further.
                ProtosActivation published = ProtosFrameArguments.activation(chain[0]);
                assertSame(published.prelude().orElse(null),
                        ProtosFrameArguments.admittedPreludeOrNull(leaf), label);
                assertSame(published.task().orElse(null),
                        ProtosFrameArguments.admittedCompactTask(leaf), label);
                for (int i = 1; i <= depth; i++) {
                    assertUnpublished(chain[i]);
                }
            }
        });
    }

    @Test
    void admittedTaskWalkKeepsExplicitAndInheritedTasks() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-task-define.protos", module);
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosTask inherited = domain.createTask(null, current -> {});
            ProtosTask explicit = domain.createTask(null, current -> {});
            ProtosActivation owner = domainModule(module, domain);
            owner.attachTask(inherited);

            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(
                    closure("make()\n", module), owner, null, SUPPLIED);
            Object[] third = deferredCall(closure("make()\n", module),
                    deferredCall(closure("make()\n", module), first));
            assertSame(inherited, ProtosFrameArguments.admittedCompactTask(third));

            Object[] explicitEntry = ProtosFrameArguments.compactDirectClosureCallOne(
                    closure("make()\n", module), owner, explicit, SUPPLIED);
            Object[] child = deferredCall(closure("make()\n", module), explicitEntry);
            assertSame(explicit, ProtosFrameArguments.admittedCompactTask(child));
            assertSame(explicit, ProtosFrameArguments.admittedCompactTask(explicitEntry));
            assertUnpublished(first);
            assertUnpublished(explicitEntry);

            // A method-call header never carries an explicit Task: it inherits.
            Object[] method = ProtosFrameArguments.compactImmediateMethodCall(
                    closure("make()\n", module), module, ProtosObjectValue.rootObject(),
                    owner, new Object[] {SUPPLIED});
            assertSame(inherited, ProtosFrameArguments.admittedCompactTask(
                    deferredCall(closure("make()\n", module), method)));
            assertUnpublished(method);
        });
    }

    /** Late observation publishes one activation per invocation, by the existing mechanism. */
    @Test
    void admittedCallerResolutionPublishesOneActivationPerInvocation() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-resolve-define.protos", module);
            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(
                    closure("make()\n", module), module, null, SUPPLIED);
            Object[] second = deferredCall(closure("make()\n", module), first);
            Object[] third = deferredCall(closure("make()\n", module), second);

            assertSame(module, ProtosFrameArguments.resolveAdmittedCaller(module));
            ProtosActivation resolved = ProtosFrameArguments.resolveAdmittedCaller(third);
            assertSame(resolved, third[0]);
            assertSame(resolved, ProtosFrameArguments.resolveAdmittedCaller(third));
            assertSame(resolved, ProtosFrameArguments.resolveExactCaller(third));
            ProtosActivation secondActivation =
                    assertInstanceOf(ProtosActivation.class, second[0]);
            ProtosActivation firstActivation =
                    assertInstanceOf(ProtosActivation.class, first[0]);
            assertSame(secondActivation, ProtosFrameArguments.compactCaller(third));
            assertSame(firstActivation, ProtosFrameArguments.resolveAdmittedCaller(first));
            assertSame(second, third[3], "slot 3 is never rewritten");
            assertSameProvenance(module, resolved);
            assertSameProvenance(module, firstActivation);
        });
    }

    /** Admitted walks keep cycle detection, and unadmitted shapes still take the validating path. */
    @Test
    void admittedAccessorsRejectCyclesAndUnadmittedShapes() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-cycle-define.protos", module);
            ProtosClosureValue template = closure("make()\n", module);
            for (int links : new int[] {1, 2, 3}) {
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
                        () -> ProtosFrameArguments.admittedPreludeOrNull(entry), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.admittedCompactTask(entry), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.resolveAdmittedCaller(entry), label);
                for (Object[] link : chain) {
                    assertUnpublished(link);
                }
            }

            ProtosClosureValue inner = preludeFree(template);
            Object[] arbitrary = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                    inner, (Object) new Object[] {inner}, SUPPLIED);
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.admittedPreludeOrNull(arbitrary));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.admittedCompactTask(arbitrary));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.resolveAdmittedCaller("caller"));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.resolveAdmittedCaller(new Object[] {SUPPLIED}));
            assertUnpublished(arbitrary);
        });
    }

    /**
     * A malformed B5 header as the second deferred link: the general
     * accessors validate every link of an arbitrary chain, so they reject it
     * without publishing anything.
     */
    @Test
    void generalAccessorsRejectAMalformedSecondDeferredLink() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-second-link-define.protos", module);
            ProtosClosureValue inner = preludeFree(closure("make()\n", module));
            ProtosReturnHome home = ProtosReturnHome.unobservable();
            ProtosObjectValue root = ProtosObjectValue.rootObject();
            List<Object[]> malformed = List.of(
                    new Object[] {module, null, null, null, null},
                    new Object[] {module, SUPPLIED, null, module, home},
                    new Object[] {module, SUPPLIED, root, module, SUPPLIED},
                    new Object[] {module, SUPPLIED, root, SUPPLIED, home},
                    new Object[] {inner, null, null, module, home},
                    new Object[] {inner, SUPPLIED, null, module, home},
                    new Object[] {inner, SUPPLIED, root, null, home},
                    new Object[] {inner, SUPPLIED, root, module, null});
            for (Object[] bad : malformed) {
                String label = java.util.Arrays.toString(bad);
                Object[] firstLink = deferredCall(preludeFree(inner), bad);
                Object[] leaf = deferredCall(preludeFree(inner), firstLink);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.preludeOrNull(leaf), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.compactTask(leaf), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.compactCaller(leaf), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.activation(leaf), label);
                assertUnpublished(firstLink);
                assertUnpublished(leaf);
            }
        });
    }

    // ---- I115. Admitted prelude reads of untransported chains (former W2 path) -------

    /**
     * A published caller answers by identity: directly as the caller
     * reference, or as argument 0 of a deferred header, whatever the
     * Closure it replaced would have answered. Nothing new is published.
     */
    @Test
    void admittedPreludeAnswersPublishedCallersByIdentity() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-w2-published-define.protos", module);
            ProtosActivation other = otherPreludeModule();
            ProtosPrelude otherPrelude = other.prelude().orElseThrow();
            ProtosClosureValue template = closure("make()\n", module);

            Object[] direct = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(template), other, null, SUPPLIED);
            assertSame(otherPrelude, ProtosFrameArguments.admittedPreludeOrNull(direct));
            assertUnpublished(direct);

            Object[] link = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(template), other, null, SUPPLIED);
            ProtosActivation published = ProtosFrameArguments.activation(link);
            Object[] leaf = deferredCall(preludeFree(template), link);
            for (int i = 0; i < 2; i++) {
                assertSame(published.prelude().orElse(null),
                        ProtosFrameArguments.admittedPreludeOrNull(link));
                assertSame(published.prelude().orElse(null),
                        ProtosFrameArguments.admittedPreludeOrNull(leaf));
                assertSame(published, link[0]);
                assertUnpublished(leaf);
            }
        });
    }

    /**
     * A Closure's own prelude prevails over the inherited one at every
     * link, a published link answers with its activation's prelude, and the
     * query rewrites neither argument 0 nor the caller slot of any header.
     */
    @Test
    void admittedPreludeKeepsOwnOverInheritedPrecedence() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-w2-own-define.protos", module);
            ProtosPrelude modulePrelude = module.prelude().orElseThrow();
            ProtosActivation other = otherPreludeModule();
            ProtosPrelude otherPrelude = other.prelude().orElseThrow();
            ProtosClosureValue owning = ownPrelude(closure("make()\n", module), modulePrelude);
            assertNotSame(modulePrelude, otherPrelude, "distinct prelude fixture");
            assertSame(modulePrelude, owning.preludeOrNullForRuntime(), "own prelude fixture");

            assertSame(modulePrelude, ProtosFrameArguments.admittedPreludeOrNull(
                    ProtosFrameArguments.compactDirectClosureCallOne(owning, other, null, SUPPLIED)));
            assertSame(otherPrelude, ProtosFrameArguments.admittedPreludeOrNull(
                    ProtosFrameArguments.compactDirectClosureCallOne(
                            preludeFree(owning), other, null, SUPPLIED)));

            // An own prelude in a deferred link, before the published activation is reached.
            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(owning), other, null, SUPPLIED);
            Object[] middle = deferredCall(owning, first);
            Object[] leaf = deferredCall(preludeFree(owning), middle);
            Object leafClosure = leaf[0];
            assertSame(modulePrelude, ProtosFrameArguments.admittedPreludeOrNull(leaf));
            assertSame(leafClosure, leaf[0]);
            assertSame(owning, middle[0]);
            assertSame(middle, leaf[3]);
            assertSame(first, middle[3]);
            assertUnpublished(first);
            assertUnpublished(middle);
            assertUnpublished(leaf);

            // Publishing the first link does not override the middle link's own prelude.
            ProtosActivation published = ProtosFrameArguments.activation(first);
            assertSame(otherPrelude, published.prelude().orElse(null));
            assertSame(modulePrelude, ProtosFrameArguments.admittedPreludeOrNull(leaf));
            assertSame(otherPrelude, ProtosFrameArguments.admittedPreludeOrNull(
                    deferredCall(preludeFree(owning), first)));
            assertUnpublished(middle);
            assertUnpublished(leaf);
        });
    }

    /**
     * Valid untransported chains of 1, 2, 3 and 5 deferred links (the former
     * W2 fixed-path depths): every depth is recovered through the general
     * walker, with the same identity on every repetition and nothing
     * published. A Closure with its own prelude at the entry still prevails.
     */
    @Test
    void admittedPreludeFollowsFixedAndDeepChains() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-w2-depth-define.protos", module);
            ProtosPrelude modulePrelude = module.prelude().orElseThrow();
            ProtosActivation other = otherPreludeModule();
            ProtosPrelude otherPrelude = other.prelude().orElseThrow();
            ProtosClosureValue owning = ownPrelude(closure("make()\n", module), modulePrelude);

            for (int depth : new int[] {1, 2, 3, 5}) {
                for (boolean ownAtEntry : new boolean[] {false, true}) {
                    String label = "depth " + depth + (ownAtEntry ? ", own prelude at entry" : "");
                    Object[][] chain = new Object[depth + 1][];
                    chain[0] = ProtosFrameArguments.compactDirectClosureCallOne(
                            ownAtEntry ? owning : preludeFree(owning), other, null, SUPPLIED);
                    for (int i = 1; i <= depth; i++) {
                        chain[i] = deferredCall(preludeFree(owning), chain[i - 1]);
                    }
                    Object[] leaf = chain[depth];
                    ProtosPrelude expected = ownAtEntry ? modulePrelude : otherPrelude;
                    for (int repeat = 0; repeat < 2; repeat++) {
                        assertSame(expected, ProtosFrameArguments.admittedPreludeOrNull(leaf), label);
                        assertSame(expected, ProtosFrameArguments.preludeOrNull(leaf), label);
                        for (int i = 0; i <= depth; i++) {
                            assertUnpublished(chain[i]);
                        }
                        for (int i = 1; i <= depth; i++) {
                            assertSame(chain[i - 1], chain[i][3], label);
                        }
                    }
                }
            }
        });
    }

    /**
     * The general walker validates every link of an arbitrary chain and
     * rejects malformed headers and cycles of length 1, 2 and 3 without
     * publishing anything; a valid 64-link chain is accepted. A malformed
     * link in an untransported chain is rejected by the admitted path too,
     * because its recovery is the general walker.
     */
    @Test
    void generalPreludeWalkerValidatesArbitraryChains() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-w2-general-define.protos", module);
            ProtosClosureValue inner = preludeFree(closure("make()\n", module));
            ProtosReturnHome home = ProtosReturnHome.unobservable();
            ProtosObjectValue root = ProtosObjectValue.rootObject();
            List<Object[]> malformed = List.of(
                    // Too short.
                    new Object[] {inner, SUPPLIED, root, module},
                    // Invalid argument 0.
                    new Object[] {SUPPLIED, SUPPLIED, root, module, home},
                    // Invalid kind and Task slots.
                    new Object[] {inner, null, null, module, home},
                    new Object[] {inner, SUPPLIED, null, module, home},
                    // Invalid ReturnHome.
                    new Object[] {inner, SUPPLIED, root, module, null},
                    // Invalid caller.
                    new Object[] {inner, SUPPLIED, root, null, home},
                    new Object[] {inner, SUPPLIED, root, SUPPLIED, home});
            for (Object[] bad : malformed) {
                String label = java.util.Arrays.toString(bad);
                Object[] firstLink = deferredCall(preludeFree(inner), bad);
                Object[] leaf = deferredCall(preludeFree(inner), firstLink);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.preludeOrNull(leaf), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.resolveExactCaller(leaf), label);
                assertUnpublished(firstLink);
                assertUnpublished(leaf);

                Object[] deep = bad;
                List<Object[]> links = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    deep = deferredCall(preludeFree(inner), deep);
                    links.add(deep);
                }
                Object[] deepLeaf = deep;
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.admittedPreludeOrNull(deepLeaf), label);
                for (Object[] link : links) {
                    assertUnpublished(link);
                }
            }

            for (int links : new int[] {1, 2, 3}) {
                Object[][] chain = new Object[links][];
                chain[0] = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                        preludeFree(inner), (Object) module, SUPPLIED);
                for (int i = 1; i < links; i++) {
                    chain[i] = deferredCall(preludeFree(inner), chain[i - 1]);
                }
                chain[0][3] = chain[links - 1];
                Object[] leaf = deferredCall(preludeFree(inner), chain[links - 1]);
                String label = links + "-link cycle";
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.preludeOrNull(leaf), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.admittedPreludeOrNull(leaf), label);
                assertThrows(IllegalStateException.class,
                        () -> ProtosFrameArguments.resolveExactCaller(leaf), label);
                for (Object[] link : chain) {
                    assertUnpublished(link);
                }
                assertUnpublished(leaf);
            }

            Object[] deep = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(inner), module, null, SUPPLIED);
            List<Object[]> deepChain = new ArrayList<>();
            deepChain.add(deep);
            for (int i = 0; i < 64; i++) {
                deep = deferredCall(preludeFree(inner), deep);
                deepChain.add(deep);
            }
            assertSame(module.prelude().orElse(null), ProtosFrameArguments.preludeOrNull(deep));
            assertSame(module.prelude().orElse(null), ProtosFrameArguments.admittedPreludeOrNull(deep));
            for (Object[] link : deepChain) {
                assertUnpublished(link);
            }
        });
    }

    /**
     * Prelude queries leave Task inheritance and explicit resolution
     * unchanged: an inherited and an explicit Task, one activation per
     * invocation, stable identities on repeated resolution, callee-first
     * materialization, and untouched ReturnHome slots.
     */
    @Test
    void preludeQueriesPreserveTaskAndExactCallerResolution() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-w2-task-define.protos", module);
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosTask inherited = domain.createTask(null, current -> {});
            ProtosTask explicit = domain.createTask(null, current -> {});
            ProtosActivation owner = domainModule(module, domain);
            owner.attachTask(inherited);

            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(
                    closure("make()\n", module), owner, null, SUPPLIED);
            Object[] second = deferredCall(closure("make()\n", module), first);
            Object[] third = deferredCall(closure("make()\n", module), second);
            Object[] leaf = deferredCall(closure("make()\n", module), deferredCall(
                    closure("make()\n", module), third));
            Object firstHome = first[4];
            Object thirdHome = third[4];
            ProtosFrameArguments.admittedPreludeOrNull(leaf);
            assertSame(inherited, ProtosFrameArguments.admittedCompactTask(leaf));
            assertSame(inherited, ProtosFrameArguments.compactTask(leaf));
            assertUnpublished(first);
            assertUnpublished(third);

            Object[] explicitEntry = ProtosFrameArguments.compactDirectClosureCallOne(
                    closure("make()\n", module), owner, explicit, SUPPLIED);
            Object[] explicitChild = deferredCall(closure("make()\n", module), explicitEntry);
            ProtosFrameArguments.admittedPreludeOrNull(explicitChild);
            assertSame(explicit, ProtosFrameArguments.admittedCompactTask(explicitChild));

            // Callee-first materialization, then explicit resolution of each caller.
            ProtosActivation thirdActivation = ProtosFrameArguments.activation(third);
            ProtosActivation secondActivation =
                    assertInstanceOf(ProtosActivation.class, second[0]);
            ProtosActivation firstActivation =
                    assertInstanceOf(ProtosActivation.class, first[0]);
            assertSame(inherited, firstActivation.task().orElse(null));
            assertSame(firstActivation.task().orElse(null),
                    ProtosFrameArguments.admittedCompactTask(deferredCall(
                            closure("make()\n", module), first)));
            for (int i = 0; i < 2; i++) {
                assertSame(thirdActivation, ProtosFrameArguments.resolveExactCaller(third));
                assertSame(secondActivation, ProtosFrameArguments.resolveExactCaller(second));
                assertSame(firstActivation, ProtosFrameArguments.resolveExactCaller(first));
                assertSame(secondActivation, ProtosFrameArguments.compactCaller(third));
            }
            assertSame(firstActivation.prelude().orElse(null),
                    ProtosFrameArguments.admittedPreludeOrNull(leaf));
            assertSame(firstHome, first[4]);
            assertSame(thirdHome, third[4]);
            assertSame(second, third[3], "slot 3 is never rewritten");
            assertSameProvenance(firstActivation, thirdActivation);
            assertSame(owner.dynamicControlStateIfPresent().orElse(null),
                    thirdActivation.dynamicControlStateIfPresent().orElse(null));
        });
    }

    /** PROVEN and UNKNOWN entries leave the deferred caller readable by the admitted accessors. */
    @Test
    void admittedAccessorsReadTheCallerOfProvenAndUnknownEntries() throws Exception {
        withCore(module -> {
            evaluate("""
                    helper: (value) => { value }
                    proven: () => { (value) => { value } }
                    unknown: () => { (value) => { helper(value) } }
                    """, "i115-entries-define.protos", module);
            for (String maker : List.of("proven()\n", "unknown()\n")) {
                ProtosClosureValue callee = closure(maker, module);
                RootCallTarget target = directTarget(callee);
                ((ProtosBytecodeRootNode) target.getRootNode()).getBytecodeNode()
                        .setUncachedThreshold(0);
                assertEquals(maker.startsWith("proven"),
                        InvokeClosureOneTail.provenNonSuspending(target), maker);
                Object[] callerArguments = ProtosFrameArguments.compactDirectClosureCallOne(
                        closure("proven()\n", module), module, null, SUPPLIED);
                Object[] calleeArguments = ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                        callee, (Object) callerArguments, evaluate("8\n", "i115-eight.protos", module));
                assertInteger(8, ProtosNumericValueSupport.guestValue(target.call(calleeArguments)));
                assertUnpublished(callerArguments);
                assertSame(module.prelude().orElse(null),
                        ProtosFrameArguments.admittedPreludeOrNull(calleeArguments), maker);
                assertSame(module.task().orElse(null),
                        ProtosFrameArguments.admittedCompactTask(calleeArguments), maker);
                assertUnpublished(callerArguments);
            }
        });
    }

    // ---- I115. Per-root caller needs (GraalPy CallerFlagsAssumptionSet) -------------

    @Test
    void callerNeedsAreMonotonicPerRootAndConverge() throws Exception {
        withCore(module -> {
            evaluate("""
                    first: (value) => { value }
                    second: (value) => { value }
                    """, "i115-needs-define.protos", module);
            ProtosBytecodeRootNode first = functionRoot("first", module);
            ProtosBytecodeRootNode second = functionRoot("second", module);
            assertNotSame(first, second);
            int need = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;

            com.oracle.truffle.api.Assumption otherBefore = second.callerNeedsStability();
            boolean otherNeeded = second.hasCallerNeed(need);
            com.oracle.truffle.api.Assumption before = first.callerNeedsStability();
            boolean alreadyNeeded = first.hasCallerNeed(need);

            first.requestCallerNeed(need);
            assertTrue(first.hasCallerNeed(need));
            com.oracle.truffle.api.Assumption registered = first.callerNeedsStability();
            assertTrue(registered.isValid());
            if (!alreadyNeeded) {
                assertNotSame(before, registered, "a new need replaces the Assumption");
                assertFalse(before.isValid(), "the previous Assumption is invalidated");
            }

            // Convergence: the same need never invalidates again.
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                first.requestCallerNeed(need);
            }
            assertSame(registered, first.callerNeedsStability());
            assertTrue(registered.isValid());

            // The state belongs to one root: the other root is unaffected.
            assertSame(otherBefore, second.callerNeedsStability());
            assertEquals(otherNeeded, second.hasCallerNeed(need));
        });
    }

    /**
     * A root that publishes its own still-compact activation (here for
     * {@code context}) registers NEEDS_EXACT_ACTIVATION with exactly one
     * Assumption replacement; later publications of new invocations of the
     * same root do not invalidate again.
     */
    @Test
    void realPublicationRegistersTheExactActivationNeedOnce() throws Exception {
        withCore(module -> {
            evaluate("""
                    publishing: () => { (value) => {
                        context.hasSlot("value")
                        value
                    } }
                    """, "i115-needs-publish-define.protos", module);
            ProtosClosureValue callee = closure("publishing()\n", module);
            RootCallTarget target = directTarget(callee);
            ProtosBytecodeRootNode root =
                    assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
            int need = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;
            assertFalse(root.hasCallerNeed(need), "a fresh root has no caller need");
            com.oracle.truffle.api.Assumption before = root.callerNeedsStability();

            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(
                    callee, module, null, SUPPLIED);
            target.call(first);
            ProtosActivation published = assertInstanceOf(ProtosActivation.class, first[0]);
            assertSameProvenance(module, published);
            assertTrue(root.hasCallerNeed(need));
            assertFalse(before.isValid(), "the first need invalidates once");
            com.oracle.truffle.api.Assumption registered = root.callerNeedsStability();
            assertTrue(registered.isValid());

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Object[] next = ProtosFrameArguments.compactDirectClosureCallOne(
                        callee, module, null, SUPPLIED);
                target.call(next);
                assertInstanceOf(ProtosActivation.class, next[0]);
            }
            assertSame(registered, root.callerNeedsStability(), "no repeated invalidation");
            assertTrue(registered.isValid());
        });
    }

    /**
     * A terminal site that first runs only its admitted entry and later needs
     * its exact caller (the fallback for a local {@code call}) registers the
     * need once; its results are unchanged and later executions do not
     * invalidate the root's needs again.
     */
    @Test
    void terminalFallbackRegistersTheExactActivationNeedOnce() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: () => { (value) => { value } }
                    late: make()
                    late.call: (value) => { 100 + value }
                    callOne: (f, value) => { f(value) }
                    """, "i115-needs-fallback-define.protos", module);
            enterCachedTier("callOne", module);
            ProtosBytecodeRootNode root = functionRoot("callOne", module);
            int need = ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION;
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(3, evaluate("callOne(make(), 3)\n", "i115-needs-warm.protos", module));
            }
            assertInteger(104, evaluate("callOne(late, 4)\n", "i115-needs-late.protos", module));
            com.oracle.truffle.api.Assumption settled = root.callerNeedsStability();
            boolean registered = root.hasCallerNeed(need);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(105, evaluate("callOne(late, 5)\n", "i115-needs-late2.protos", module));
                assertInteger(6, evaluate("callOne(make(), 6)\n", "i115-needs-admitted.protos", module));
            }
            assertSame(settled, root.callerNeedsStability(), "no repeated invalidation");
            assertTrue(settled.isValid());
            assertEquals(registered, root.hasCallerNeed(need));
        });
    }

    // ---- I115. Caller frame references (GraalPy PFrame.Reference / CallContext) -----

    /**
     * A producer prepares nothing for a callee that has not requested the
     * caller prelude; once requested, it transports the caller's prelude
     * with the caller's own header, except for a published caller or a
     * Closure with its own prelude. Nothing is published.
     */
    @Test
    void callerArgumentPreparesOnlyWhatTheCalleeRequested() throws Exception {
        withCore(module -> {
            evaluate("""
                    first: (value) => { value }
                    second: (value) => { value }
                    make: () => { (value) => { value } }
                    """, "i115-ref-prepare-define.protos", module);
            ProtosBytecodeRootNode callerRoot = functionRoot("first", module);
            ProtosBytecodeRootNode callee = functionRoot("second", module);
            ProtosPrelude modulePrelude = module.prelude().orElseThrow();
            ProtosClosureValue inherited = preludeFree(closure("make()\n", module));
            ProtosClosureValue owning = ownPrelude(closure("make()\n", module), modulePrelude);
            Object[] callerArguments = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(inherited), module, null, SUPPLIED);
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            assertFalse(callee.hasCallerNeed(need), "a fresh callee has no caller need");

            assertSame(callerArguments, InvokeClosureOneTail.callerArgument(
                    callerRoot, callee, inherited, callerArguments));
            assertSame(module, InvokeClosureOneTail.callerArgument(
                    callerRoot, callee, inherited, module));
            assertSame(callerArguments, InvokeMethodOneTail.callerArgument(
                    callee, inherited, callerArguments, modulePrelude));
            assertUnpublished(callerArguments);

            callee.requestCallerNeed(need);
            assertSame(module, InvokeClosureOneTail.callerArgument(
                    callerRoot, callee, inherited, module), "a published caller answers already");
            assertSame(callerArguments, InvokeClosureOneTail.callerArgument(
                    callerRoot, callee, owning, callerArguments), "an own prelude is never inherited");

            ProtosCallerFrameReference reference = assertInstanceOf(
                    ProtosCallerFrameReference.class,
                    InvokeClosureOneTail.callerArgument(
                            callerRoot, callee, inherited, callerArguments));
            assertSame(callerArguments, reference.callerArguments());
            assertSame(modulePrelude, reference.transportedPrelude());
            assertSame(null, reference.publishedActivationOrNull());

            // The method form transports the prelude its PIC guard read, even null.
            ProtosCallerFrameReference transportedNull = assertInstanceOf(
                    ProtosCallerFrameReference.class,
                    InvokeMethodOneTail.callerArgument(callee, inherited, callerArguments, null));
            assertSame(null, transportedNull.transportedPrelude());
            assertUnpublished(callerArguments);

            // The caller read its prelude from an activation caller: no need of its own.
            assertFalse(callerRoot.hasCallerNeed(need));
            assertFalse(callerRoot.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION));
        });
    }

    /**
     * Transitive propagation over A → B → C → D, where A owns its prelude,
     * B and C inherit it and D requests it. The first preparation of each
     * hop recovers an untransported prelude and registers the need on the
     * intermediate root that had to compute it (C, then B), never on A.
     * Once registered, every hop transports a reference, no header of the
     * chain inherits an untransported prelude, and no root's Assumption is
     * invalidated again.
     */
    @Test
    void callerPreludeNeedPropagatesTransitivelyAndConverges() throws Exception {
        withCore(module -> {
            evaluate("""
                    a: (value) => { value }
                    b: (value) => { value }
                    c: (value) => { value }
                    d: (value) => { value }
                    make: () => { (value) => { value } }
                    """, "i115-ref-transitive-define.protos", module);
            ProtosBytecodeRootNode rootA = functionRoot("a", module);
            ProtosBytecodeRootNode rootB = functionRoot("b", module);
            ProtosBytecodeRootNode rootC = functionRoot("c", module);
            ProtosBytecodeRootNode rootD = functionRoot("d", module);
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            ProtosPrelude modulePrelude = module.prelude().orElseThrow();
            ProtosActivation other = otherPreludeModule();
            ProtosClosureValue template = closure("make()\n", module);
            // A owns modulePrelude; its own caller has a different prelude.
            ProtosClosureValue closureA = ownPrelude(template, modulePrelude);
            ProtosClosureValue closureB = preludeFree(template);
            ProtosClosureValue closureC = preludeFree(template);
            ProtosClosureValue closureD = preludeFree(template);
            rootD.requestCallerNeed(need);
            for (ProtosBytecodeRootNode root : List.of(rootA, rootB, rootC)) {
                assertFalse(root.hasCallerNeed(need), "fresh intermediate root");
            }

            // First execution: nothing transported yet.
            Object[] a1 = ProtosFrameArguments.compactDirectClosureCallOne(closureA, other, null, SUPPLIED);
            Object[] b1 = deferredCall(closureB, a1);
            Object[] c1 = deferredCall(closureC, b1);
            com.oracle.truffle.api.Assumption cBefore = rootC.callerNeedsStability();
            ProtosCallerFrameReference toC = assertInstanceOf(ProtosCallerFrameReference.class,
                    InvokeClosureOneTail.callerArgument(rootC, rootD, closureD, c1));
            assertSame(modulePrelude, toC.transportedPrelude());
            assertTrue(rootC.hasCallerNeed(need), "C computed an inherited untransported prelude");
            assertFalse(cBefore.isValid());
            assertFalse(rootB.hasCallerNeed(need), "B is registered only when it must compute");

            // Next execution: C now requests it, so B's preparation registers B.
            com.oracle.truffle.api.Assumption bBefore = rootB.callerNeedsStability();
            Object[] a2 = ProtosFrameArguments.compactDirectClosureCallOne(closureA, other, null, SUPPLIED);
            Object[] b2 = deferredCall(closureB, a2);
            ProtosCallerFrameReference toB2 = assertInstanceOf(ProtosCallerFrameReference.class,
                    InvokeClosureOneTail.callerArgument(rootB, rootC, closureC, b2));
            assertSame(modulePrelude, toB2.transportedPrelude());
            assertTrue(rootB.hasCallerNeed(need));
            assertFalse(bBefore.isValid());
            assertFalse(rootA.hasCallerNeed(need), "A reads its own prelude");

            // Converged executions: every hop transports, no recovery, no invalidation.
            com.oracle.truffle.api.Assumption aSettled = rootA.callerNeedsStability();
            com.oracle.truffle.api.Assumption bSettled = rootB.callerNeedsStability();
            com.oracle.truffle.api.Assumption cSettled = rootC.callerNeedsStability();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Object[] a = ProtosFrameArguments.compactDirectClosureCallOne(closureA, other, null, SUPPLIED);
                Object[] b = ProtosFrameArguments.compactDirectClosureCallOneNoHome(closureB,
                        InvokeClosureOneTail.callerArgument(rootA, rootB, closureB, a), SUPPLIED);
                Object[] c = ProtosFrameArguments.compactDirectClosureCallOneNoHome(closureC,
                        InvokeClosureOneTail.callerArgument(rootB, rootC, closureC, b), SUPPLIED);
                Object[] d = ProtosFrameArguments.compactDirectClosureCallOneNoHome(closureD,
                        InvokeClosureOneTail.callerArgument(rootC, rootD, closureD, c), SUPPLIED);
                for (Object[] link : List.of(b, c, d)) {
                    assertInstanceOf(ProtosCallerFrameReference.class, link[3]);
                    assertFalse(ProtosFrameArguments.inheritsUntransportedCallerPrelude(link));
                    assertSame(modulePrelude, ProtosFrameArguments.admittedPreludeOrNull(link));
                }
                for (Object[] link : List.of(a, b, c, d)) {
                    assertUnpublished(link);
                }
            }
            assertSame(aSettled, rootA.callerNeedsStability());
            assertSame(bSettled, rootB.callerNeedsStability());
            assertSame(cSettled, rootC.callerNeedsStability());
            assertFalse(rootA.hasCallerNeed(need));
        });
    }

    /**
     * Integration over real guest calls A → B → C → D: A is a module
     * function with its own prelude; B, C and D are non-capturing Closures
     * (no own prelude) and each of A, B and C fetches the next Closure by a
     * guarded source send (which, unlike a native send such as indexing,
     * does not publish the sender) and ends in an admitted terminal Call1. The callee root each terminal site caches is the root of the
     * target its DirectCallNode invokes, and the needs land on exactly
     * those roots: D and C, which read an inherited prelude their caller
     * did not transport. A reads its own prelude; B's caller is the
     * activation the top-level call publishes for A, which answers
     * directly. After the needs settle, further runs invalidate nothing.
     */
    @Test
    void realChainRegistersNeedsOnTheInvokedRootsAndConverges() throws Exception {
        withCore(module -> {
            evaluate("""
                    makeB: () => { (k) => {
                        next: k.toC()
                        next(k)
                    } }
                    makeC: () => { (k) => {
                        next: k.toD()
                        next(k)
                    } }
                    makeD: () => { (k) => { k.identity(7) } }
                    b: makeB()
                    c: makeC()
                    d: makeD()
                    chain: {
                        identity: (value) => { value }
                        toB: () => { b }
                        toC: () => { c }
                        toD: () => { d }
                    }
                    a: (k) => {
                        next: k.toB()
                        next(k)
                    }
                    """, "i115-ref-chain-define.protos", module);
            ProtosBytecodeRootNode rootA = functionRoot("a", module);
            List<ProtosBytecodeRootNode> inherited = new ArrayList<>();
            for (String name : List.of("b", "c", "d")) {
                ProtosClosureValue closure = assertInstanceOf(ProtosClosureValue.class,
                        module.context().readLocalSlot(name).orElseThrow());
                assertSame(null, closure.preludeOrNullForRuntime(),
                        name + ": non-capturing fixture inherits its prelude");
                inherited.add(assertInstanceOf(ProtosBytecodeRootNode.class,
                        directTarget(closure).getRootNode()));
            }
            ProtosBytecodeRootNode rootB = inherited.get(0);
            ProtosBytecodeRootNode rootC = inherited.get(1);
            ProtosBytecodeRootNode rootD = inherited.get(2);
            List<ProtosBytecodeRootNode> roots = List.of(rootA, rootB, rootC, rootD);
            java.util.Set<ProtosBytecodeRootNode> distinct =
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            distinct.addAll(roots);
            assertEquals(4, distinct.size(), "four distinct roots");
            for (ProtosBytecodeRootNode root : roots) {
                root.getBytecodeNode().setUncachedThreshold(0);
            }
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;

            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("a(chain)\n", "i115-ref-chain-warm.protos", module));
            }

            // Each terminal site's cached callee is the root its DirectCallNode invokes.
            assertSame(rootB, tailCallee(rootA));
            assertSame(rootC, tailCallee(rootB));
            assertSame(rootD, tailCallee(rootC));

            assertTrue(rootD.hasCallerNeed(need), "D reads an untransported inherited prelude");
            assertTrue(rootC.hasCallerNeed(need), "C computes the prelude D requested");
            assertFalse(rootB.hasCallerNeed(need), "B's caller is A's published activation");
            assertFalse(rootA.hasCallerNeed(need), "A owns its prelude");

            List<com.oracle.truffle.api.Assumption> settled = new ArrayList<>();
            for (ProtosBytecodeRootNode root : roots) {
                settled.add(root.callerNeedsStability());
            }
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(7, evaluate("a(chain)\n", "i115-ref-chain-again.protos", module));
            }
            for (int i = 0; i < roots.size(); i++) {
                assertSame(settled.get(i), roots.get(i).callerNeedsStability(),
                        "root " + i + ": no repeated invalidation");
                assertTrue(settled.get(i).isValid());
            }
        });
    }

    /**
     * The first read of an untransported inherited prelude registers
     * NEEDS_CALLER_PRELUDE with one Assumption replacement and answers
     * through the general walker; repeated reads do not invalidate again,
     * and a transported, published or own prelude is not a request.
     */
    @Test
    void firstUntransportedPreludeReadRegistersTheNeedOnceAndRecovers() throws Exception {
        withCore(module -> {
            evaluate("""
                    first: (value) => { value }
                    make: () => { (value) => { value } }
                    """, "i115-ref-first-define.protos", module);
            ProtosBytecodeRootNode root = functionRoot("first", module);
            ProtosPrelude modulePrelude = module.prelude().orElseThrow();
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            assertFalse(root.hasCallerNeed(need), "a fresh root has no caller need");
            com.oracle.truffle.api.Assumption before = root.callerNeedsStability();

            ProtosClosureValue template = closure("make()\n", module);
            Object[] entry = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(template), module, null, SUPPLIED);
            Object[] middle = deferredCall(preludeFree(template), entry);
            Object[] leaf = deferredCall(preludeFree(template), middle);
            assertFalse(ProtosFrameArguments.inheritsUntransportedCallerPrelude(entry));
            assertTrue(ProtosFrameArguments.inheritsUntransportedCallerPrelude(leaf));

            assertSame(modulePrelude, InvokeClosureOneTail.admittedCallerPrelude(root, leaf));
            assertTrue(root.hasCallerNeed(need));
            assertFalse(before.isValid(), "the first need invalidates once");
            com.oracle.truffle.api.Assumption registered = root.callerNeedsStability();
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertSame(modulePrelude, InvokeClosureOneTail.admittedCallerPrelude(root, leaf));
            }
            assertSame(registered, root.callerNeedsStability(), "no repeated invalidation");
            assertTrue(registered.isValid());
            assertFalse(root.hasCallerNeed(ProtosBytecodeRootNode.NEEDS_EXACT_ACTIVATION),
                    "needs are registered independently");

            assertFalse(ProtosFrameArguments.inheritsUntransportedCallerPrelude(
                    referencedCall(preludeFree(template), middle, modulePrelude)));
            assertFalse(ProtosFrameArguments.inheritsUntransportedCallerPrelude(
                    deferredCall(ownPrelude(template, modulePrelude), middle)));
            assertUnpublished(entry);
            assertUnpublished(middle);
            assertUnpublished(leaf);
        });
    }

    /**
     * A transported prelude is answered without walking, including a
     * transported null (a value, not an absence); an own prelude still
     * prevails; two distinct preludes of one Context stay distinct. Past a
     * reference the admitted read follows nothing, while the general
     * accessors still validate the referenced header and reject it.
     */
    @Test
    void transportedPreludeAnswersWithoutWalkingAndKeepsPrecedence() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-ref-read-define.protos", module);
            ProtosPrelude modulePrelude = module.prelude().orElseThrow();
            ProtosActivation other = otherPreludeModule();
            ProtosPrelude otherPrelude = other.prelude().orElseThrow();
            assertNotSame(modulePrelude, otherPrelude, "distinct prelude fixture");
            ProtosClosureValue template = closure("make()\n", module);
            ProtosClosureValue owning = ownPrelude(template, modulePrelude);

            Object[] otherEntry = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(template), other, null, SUPPLIED);
            Object[] moduleEntry = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(template), module, null, SUPPLIED);
            Object[] fromOther = referencedCall(preludeFree(template), otherEntry, otherPrelude);
            Object[] fromModule = referencedCall(preludeFree(template), moduleEntry, modulePrelude);
            assertSame(otherPrelude, ProtosFrameArguments.admittedPreludeOrNull(fromOther));
            assertSame(otherPrelude, ProtosFrameArguments.preludeOrNull(fromOther));
            assertSame(modulePrelude, ProtosFrameArguments.admittedPreludeOrNull(fromModule));
            assertSame(modulePrelude, ProtosFrameArguments.preludeOrNull(fromModule));

            Object[] transportedNull = referencedCall(preludeFree(template), otherEntry, null);
            assertSame(null, ProtosFrameArguments.admittedPreludeOrNull(transportedNull));
            assertFalse(ProtosFrameArguments.inheritsUntransportedCallerPrelude(transportedNull));
            assertSame(otherPrelude, ProtosFrameArguments.admittedPreludeOrNull(
                    deferredCall(preludeFree(template), otherEntry)), "not transported: recovered");

            assertSame(modulePrelude, ProtosFrameArguments.admittedPreludeOrNull(
                    referencedCall(owning, otherEntry, otherPrelude)));
            assertSame(modulePrelude, ProtosFrameArguments.preludeOrNull(
                    referencedCall(owning, otherEntry, otherPrelude)));

            Object[] loop = deferredCall(preludeFree(template), otherEntry);
            loop[3] = loop;
            Object[] past = referencedCall(preludeFree(template), loop, otherPrelude);
            assertSame(otherPrelude, ProtosFrameArguments.admittedPreludeOrNull(past),
                    "the admitted read does not walk past a transported prelude");
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.preludeOrNull(past));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.compactTask(past));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.compactCaller(past));
            assertUnpublished(otherEntry);
            assertUnpublished(moduleEntry);
            assertUnpublished(loop);
            assertUnpublished(past);
        });
    }

    /**
     * Exact resolution through a reference publishes each invocation once,
     * in its own argument 0, and later resolutions return the same identity;
     * Task inheritance, explicit Tasks and ReturnHome slots are unchanged,
     * and the reference itself is never rewritten.
     */
    @Test
    void referencesPublishOnceAndKeepTaskAndReturnHome() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-ref-publish-define.protos", module);
            ProtosActorExecutionDomain domain = new ProtosActorExecutionDomain();
            ProtosTask inherited = domain.createTask(null, current -> {});
            ProtosTask explicit = domain.createTask(null, current -> {});
            ProtosActivation owner = domainModule(module, domain);
            owner.attachTask(inherited);
            ProtosPrelude prelude = owner.prelude().orElse(null);

            Object[] first = ProtosFrameArguments.compactDirectClosureCallOne(
                    closure("make()\n", module), owner, null, SUPPLIED);
            Object[] second = referencedCall(closure("make()\n", module), first, prelude);
            Object[] third = referencedCall(closure("make()\n", module), second, prelude);
            ProtosCallerFrameReference toSecond =
                    assertInstanceOf(ProtosCallerFrameReference.class, third[3]);
            Object firstHome = first[4];
            Object thirdHome = third[4];

            assertSame(inherited, ProtosFrameArguments.admittedCompactTask(third));
            assertSame(inherited, ProtosFrameArguments.compactTask(third));
            assertSame(null, toSecond.publishedActivationOrNull());
            assertUnpublished(first);
            assertUnpublished(second);

            ProtosActivation secondActivation = ProtosFrameArguments.compactCaller(third);
            assertSame(secondActivation, second[0]);
            assertSame(secondActivation, toSecond.publishedActivationOrNull());
            ProtosActivation firstActivation = assertInstanceOf(ProtosActivation.class, first[0]);
            for (int i = 0; i < 2; i++) {
                assertSame(secondActivation, ProtosFrameArguments.resolveExactCaller(toSecond));
                assertSame(secondActivation, ProtosFrameArguments.resolveAdmittedCaller(toSecond));
                assertSame(secondActivation, ProtosFrameArguments.compactCaller(third));
                assertSame(firstActivation, ProtosFrameArguments.resolveExactCaller(first));
            }
            assertUnpublished(third);
            ProtosActivation thirdActivation = ProtosFrameArguments.activation(third);
            assertSame(thirdActivation, third[0]);
            assertSame(toSecond, third[3], "slot 3 is never rewritten");
            assertSame(firstHome, first[4]);
            assertSame(thirdHome, third[4]);
            assertSame(inherited, thirdActivation.task().orElse(null));
            assertSameProvenance(firstActivation, thirdActivation);

            Object[] explicitEntry = ProtosFrameArguments.compactDirectClosureCallOne(
                    closure("make()\n", module), owner, explicit, SUPPLIED);
            Object[] child = referencedCall(closure("make()\n", module), explicitEntry, prelude);
            assertSame(explicit, ProtosFrameArguments.admittedCompactTask(child));
            assertSame(explicit, ProtosFrameArguments.compactTask(child));
            assertUnpublished(explicitEntry);
        });
    }

    /**
     * A valid chain of 64 references: both prelude accessors answer at the
     * leaf, Task queries follow it, and resolving the leaf's caller
     * publishes exactly one distinct activation per invocation.
     */
    @Test
    void deepReferenceChainResolvesOneActivationPerInvocation() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-ref-deep-define.protos", module);
            ProtosPrelude modulePrelude = module.prelude().orElseThrow();
            ProtosClosureValue template = closure("make()\n", module);
            List<Object[]> chain = new ArrayList<>();
            chain.add(ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(template), module, null, SUPPLIED));
            for (int i = 0; i < 64; i++) {
                chain.add(referencedCall(preludeFree(template), chain.get(i), modulePrelude));
            }
            Object[] leaf = chain.get(64);
            assertSame(modulePrelude, ProtosFrameArguments.admittedPreludeOrNull(leaf));
            assertSame(modulePrelude, ProtosFrameArguments.preludeOrNull(leaf));
            assertSame(module.task().orElse(null), ProtosFrameArguments.compactTask(leaf));
            assertSame(module.task().orElse(null), ProtosFrameArguments.admittedCompactTask(leaf));
            for (Object[] link : chain) {
                assertUnpublished(link);
            }

            ProtosActivation caller = ProtosFrameArguments.resolveExactCaller(leaf[3]);
            assertSame(caller, chain.get(63)[0]);
            java.util.Set<ProtosActivation> identities =
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (int i = 0; i < 64; i++) {
                identities.add(assertInstanceOf(ProtosActivation.class, chain.get(i)[0]));
            }
            assertEquals(64, identities.size(), "one activation per invocation");
            assertSame(caller, ProtosFrameArguments.resolveExactCaller(leaf[3]));
            assertUnpublished(leaf);
            assertSameProvenance(module, caller);
        });
    }

    /**
     * Cycles through references, and a reference to its own header, are
     * rejected by the general walkers (Prelude included: a transported
     * prelude never ends the general walk), by the admitted recovery and by
     * publication, which publishes nothing.
     */
    @Test
    void cyclicAndSelfReferencesAreRejectedWithoutPublication() throws Exception {
        withCore(module -> {
            evaluate("make: () => { (value) => { value } }\n", "i115-ref-cycle-define.protos", module);
            ProtosPrelude modulePrelude = module.prelude().orElseThrow();
            ProtosClosureValue template = closure("make()\n", module);
            Object[] entry = ProtosFrameArguments.compactDirectClosureCallOne(
                    preludeFree(template), module, null, SUPPLIED);
            Object[] a = deferredCall(preludeFree(template), entry);
            Object[] b = referencedCall(preludeFree(template), a, modulePrelude);
            a[3] = ProtosFrameArguments.callerFrameReference(b, modulePrelude);
            Object[] leaf = deferredCall(preludeFree(template), b);
            assertTrue(ProtosFrameArguments.inheritsUntransportedCallerPrelude(leaf));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.preludeOrNull(leaf));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.admittedPreludeOrNull(leaf),
                    "the admitted recovery is the general walker");
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.compactTask(leaf));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.admittedCompactTask(leaf));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.resolveExactCaller(leaf[3]));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.activation(leaf));
            assertUnpublished(a);
            assertUnpublished(b);
            assertUnpublished(leaf);

            Object[] self = deferredCall(preludeFree(template), entry);
            self[3] = ProtosFrameArguments.callerFrameReference(self, modulePrelude);
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.preludeOrNull(self));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.compactCaller(self));
            assertThrows(IllegalStateException.class,
                    () -> ProtosFrameArguments.resolveExactCaller(
                            ProtosFrameArguments.callerFrameReference(
                                    new Object[] {SUPPLIED}, modulePrelude)));
            assertUnpublished(self);
            assertUnpublished(entry);
        });
    }

    /**
     * Guest path: a non-capturing Closure (no own prelude) whose terminal
     * send reads its inherited prelude. Entered with an untransported
     * compact caller, its first read registers NEEDS_CALLER_PRELUDE once;
     * entered as the producer then prepares it (a reference transporting
     * the prelude), it converges without another invalidation, and every
     * result is unchanged. A published caller never registers it: a guest
     * call from top-level code enters through a published activation, which
     * answers the prelude directly.
     */
    @Test
    void terminalSendRegistersTheCallerPreludeNeedOnceAndConverges() throws Exception {
        withCore(module -> {
            evaluate("""
                    receiver: { identity: (value) => { value } }
                    make: () => { (target) => { target.identity(7) } }
                    first: (value) => { value }
                    """, "i115-ref-guest-define.protos", module);
            Object receiver = module.context().readLocalSlot("receiver").orElseThrow();
            ProtosClosureValue inner = closure("make()\n", module);
            assertSame(null, inner.preludeOrNullForRuntime(),
                    "non-capturing fixture inherits its prelude");
            RootCallTarget target = directTarget(inner);
            ProtosBytecodeRootNode innerRoot =
                    assertInstanceOf(ProtosBytecodeRootNode.class, target.getRootNode());
            innerRoot.getBytecodeNode().setUncachedThreshold(0);
            ProtosBytecodeRootNode callerRoot = functionRoot("first", module);
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;

            // A published caller answers the prelude: nothing is requested.
            assertInteger(7, ProtosNumericValueSupport.guestValue(target.call(
                    ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                            inner, (Object) module, receiver))));
            assertFalse(innerRoot.hasCallerNeed(need), "a published caller registers nothing");
            com.oracle.truffle.api.Assumption before = innerRoot.callerNeedsStability();

            // Untransported compact caller: the first read registers the need once.
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Object[] callerArguments = ProtosFrameArguments.compactDirectClosureCallOne(
                        closure("make()\n", module), module, null, SUPPLIED);
                assertInteger(7, ProtosNumericValueSupport.guestValue(target.call(
                        ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                                inner, (Object) callerArguments, receiver))));
                assertUnpublished(callerArguments);
            }
            assertTrue(innerRoot.hasCallerNeed(need), "the inherited prelude read registered it");
            assertFalse(before.isValid());
            com.oracle.truffle.api.Assumption settled = innerRoot.callerNeedsStability();

            // The producer now transports it, and the callee converges.
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                Object[] callerArguments = ProtosFrameArguments.compactDirectClosureCallOne(
                        closure("make()\n", module), module, null, SUPPLIED);
                Object prepared = InvokeClosureOneTail.callerArgument(
                        callerRoot, innerRoot, inner, callerArguments);
                assertInstanceOf(ProtosCallerFrameReference.class, prepared);
                assertInteger(7, ProtosNumericValueSupport.guestValue(target.call(
                        ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                                inner, prepared, receiver))));
                assertUnpublished(callerArguments);
            }
            assertSame(settled, innerRoot.callerNeedsStability(), "no repeated invalidation");
            assertTrue(settled.isValid());
        });
    }

    /**
     * A callee root that has registered the caller-prelude need and
     * suspends inside an UNKNOWN terminal entry resumes with its own state
     * and its exact result. The top-level guest call enters callOne already
     * published, so this entry passes an activation, not a reference: it
     * proves that a registered need does not disturb suspension, while the
     * immutability of a suspended reference is covered structurally by
     * referencesPublishOnceAndKeepTaskAndReturnHome.
     */
    @Test
    void transportedCallerSurvivesSuspensionAndResumption() throws Exception {
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
                        make: () => { (future) => {
                            result: future.value()
                            result
                        } }
                        callOne: (f, value) => { f(value) }
                        """, "i115-ref-suspend-define.protos", module);
                ProtosClosureValue inner = closure("make()\n", module);
                assertSame(null, inner.preludeOrNullForRuntime(),
                        "non-capturing fixture inherits its prelude");
                ProtosBytecodeRootNode innerRoot = assertInstanceOf(
                        ProtosBytecodeRootNode.class, directTarget(inner).getRootNode());
                innerRoot.requestCallerNeed(ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE);
                enterCachedTier("callOne", module);
                CallTarget top = ProtosLanguageContext.current().parsePublic(
                        Source.newBuilder(ProtosLanguage.ID, "callOne(make(), wait)\n",
                                        "i115-ref-suspend-top.protos")
                                .mimeType(ProtosLanguage.MIME_TYPE)
                                .build());

                ProtosTask task = domain.createTask(
                        null, current -> ProtosBytecodeTaskExecution.execute(current, top, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.SUSPENDED, task.state(), "really suspended");

                assertTrue(wait.resolve(resolved, module));
                assertTrue(domain.dispatchOne());
                assertEquals(ProtosTask.State.COMPLETED, task.state());
                assertSame(resolved, task.result().orElseThrow(), "exact resumed result");
            } finally {
                context.leave();
            }
        }
    }

    /** Caller needs belong to one Context's root: registering in one leaves the other unchanged. */
    @Test
    void callerNeedsAreIsolatedBetweenContexts() throws Exception {
        String define = "make: () => { (value) => { value } }\n";
        try (Harness first = new Harness(false, null);
                Harness second = new Harness(false, null)) {
            ModuleAction<ProtosBytecodeRootNode> innerRoot = module -> {
                evaluate(define, "i115-ref-context-define.protos", module);
                return assertInstanceOf(ProtosBytecodeRootNode.class,
                        directTarget(closure("make()\n", module)).getRootNode());
            };
            ProtosBytecodeRootNode firstRoot = first.call(innerRoot);
            ProtosBytecodeRootNode secondRoot = second.call(innerRoot);
            assertNotSame(firstRoot, secondRoot);
            int need = ProtosBytecodeRootNode.NEEDS_CALLER_PRELUDE;
            com.oracle.truffle.api.Assumption secondBefore = secondRoot.callerNeedsStability();
            boolean secondNeeded = secondRoot.hasCallerNeed(need);

            first.call(module -> {
                firstRoot.requestCallerNeed(need);
                return null;
            });
            assertTrue(firstRoot.hasCallerNeed(need));
            assertSame(secondBefore, secondRoot.callerNeedsStability());
            assertEquals(secondNeeded, secondRoot.hasCallerNeed(need));
        }
    }

    // ---- I115. Captured reads after the activation is published ---------------------

    /**
     * A captured read in a root whose activation was published (here by
     * {@code context}) keeps reading the owner frame, and abandons its
     * selection once the owner binding is removed and reads the recreated
     * binding of the same owner afterwards (D179 C0).
     */
    @Test
    void capturedReadAfterPublicationFollowsTheOwnerBinding() throws Exception {
        withCore(module -> {
            evaluate("""
                    make: (seed) => {
                        owner: context
                        middle: (unused) => {
                            (v) => {
                                context.hasSlot("v")
                                seed + v
                            }
                        }
                        reader: middle(0)
                        [reader, () => { owner.removeSlot("seed") }, () => { owner.seed: 30 }]
                    }
                    pair: make(10)
                    read: pair[0]
                    drop: pair[1]
                    recreate: pair[2]
                    """, "i115-captured-define.protos", module);
            ProtosClosureValue read = assertInstanceOf(
                    ProtosClosureValue.class, module.context().readLocalSlot("read").orElseThrow());
            read.executionPlan().orElseThrow()
                    .bytecodeActivationRootForTesting().getBytecodeNode().setUncachedThreshold(0);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(10 + i, evaluate("read(" + i + ")\n", "i115-captured-read.protos", module));
            }
            evaluate("drop()\n", "i115-captured-drop.protos", module);
            assertThrows(com.guillermomolina.protos.runtime.ProtosSignalException.class,
                    () -> evaluate("read(1)\n", "i115-captured-removed.protos", module));
            // D179: the recreated binding of the same owner is read again.
            evaluate("recreate()\n", "i115-captured-recreate.protos", module);
            for (int i = 0; i < PAST_CACHE_LIMITS; i++) {
                assertInteger(30 + i, evaluate("read(" + i + ")\n", "i115-captured-again.protos", module));
            }
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

    /** The same definition and captures, with {@code prelude} as its own prelude; never executed. */
    private static ProtosClosureValue ownPrelude(ProtosClosureValue template, ProtosPrelude prelude) {
        return new ProtosClosureValue(
                template.definition(),
                template.capturedLexicalContexts(),
                template.capturedReceiver(),
                template.methodHome().orElse(null),
                template.returnHome().orElse(null),
                prelude);
    }

    private static Object[] deferredCall(ProtosClosureValue closure, Object[] callerArguments) {
        return ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                closure, (Object) callerArguments, SUPPLIED);
    }

    /** A terminal Call1 header whose caller slot transports {@code prelude} (I115 reference). */
    private static Object[] referencedCall(
            ProtosClosureValue closure, Object[] callerArguments, ProtosPrelude prelude) {
        return ProtosFrameArguments.compactDirectClosureCallOneNoHome(
                closure,
                (Object) ProtosFrameArguments.callerFrameReference(callerArguments, prelude),
                SUPPLIED);
    }

    /** A module of a second, independently bootstrapped prelude in the current Context. */
    private static ProtosActivation otherPreludeModule() throws Exception {
        return new ProtosCoreBootstrap().bootstrap(CORE).newModuleActivation();
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

    /**
     * The callee root cached by every direct entry of {@code root}'s single
     * terminal Call1 site, after checking that it is the root of both the
     * entry's executable facts and the target its DirectCallNode invokes.
     */
    private static ProtosBytecodeRootNode tailCallee(ProtosBytecodeRootNode root)
            throws Exception {
        Node[] cachedNodes = (Node[]) field(root.getBytecodeNode(), "cachedNodes_");
        String nodeName = InvokeClosureOneTail.class.getSimpleName() + "_Node";
        ProtosBytecodeRootNode callee = null;
        int entries = 0;
        for (Node node : cachedNodes) {
            if (node == null || !node.getClass().getSimpleName().equals(nodeName)) {
                continue;
            }
            for (String cache : List.of("guardedSource_cache", "definitionSource_cache")) {
                for (Object entry = field(node, cache); entry != null; entry = field(entry, "next_")) {
                    entries++;
                    ProtosBytecodeRootNode cached = assertInstanceOf(
                            ProtosBytecodeRootNode.class, field(entry, "cachedCallee_"));
                    DirectClosureExecutableFacts facts =
                            (DirectClosureExecutableFacts) field(entry, "cachedFacts_");
                    assertSame(facts.target().getRootNode(), cached, "facts target root");
                    com.oracle.truffle.api.nodes.DirectCallNode direct = assertInstanceOf(
                            com.oracle.truffle.api.nodes.DirectCallNode.class, field(entry, "node_"));
                    assertSame(
                            assertInstanceOf(RootCallTarget.class, direct.getCallTarget()).getRootNode(),
                            cached, "invoked target root");
                    if (callee != null) {
                        assertSame(callee, cached, "one callee per site in this chain");
                    }
                    callee = cached;
                }
            }
        }
        assertTrue(entries > 0, "the terminal site has a direct entry");
        return callee;
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

    /**
     * One Context of a possibly shared Engine. With {@code debugger}, a Truffle debugger session
     * is attached to the Engine before any lowering (PLAT067-B).
     */
    private static final class Harness implements AutoCloseable {
        private final Context context;
        private final DebuggerSession debugger;
        private ProtosPrelude prelude;

        Harness(boolean debugger, Engine engine) {
            Context.Builder builder = Context.newBuilder(ProtosLanguage.ID);
            if (engine != null) {
                builder.engine(engine);
            }
            this.context = builder.build();
            this.debugger =
                    debugger
                            ? Debugger.find(context.getEngine()).startSession(event -> {})
                            : null;
            context.initialize(ProtosLanguage.ID);
        }

        <T> T call(ModuleAction<T> action) throws Exception {
            context.enter();
            try {
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
            if (debugger != null) {
                debugger.close();
            }
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
