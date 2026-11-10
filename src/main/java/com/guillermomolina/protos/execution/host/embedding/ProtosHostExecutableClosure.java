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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorExecutionDomain.HostEntryExtent;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import java.util.List;
import java.util.Objects;

/**
 * PERF033-A host-facing executable presentation of one exact source-backed Protos Closure.
 *
 * <p>This is interop machinery, not a Protos value: it is never visible to guest code, has no
 * Protos identity of its own, and has no semantic authority. Executing it is ordinary direct
 * activation of exactly {@link #closure}, entered through the PERF025 compact direct-Closure frame
 * ABI with {@link #caller} as provenance and no Task. The callee activation is materialized only
 * when an operation, an Error path, or tooling observes it, inheriting the caller's dynamic-control
 * state exactly as a guest direct Closure call does. Polyglot Context entry is owned by the
 * framework's host-to-guest boundary ({@code Value.execute()}); this adapter performs none.
 *
 * <p>The adapter retains the Context-owned Bytecode target derived once in its owning Context, so
 * the cached {@code execute} specialization reaches the body through a {@link DirectCallNode}.
 * A session-prepared adapter supports only zero-argument execution; other arities fail through
 * {@link ArityException}.
 *
 * <p>PLAT054: an adapter produced by a standard-embedding binding read additionally carries its
 * {@link #embedding} Process. Conventional execution enters the RootActor with exclusive admission.
 * I115 temporarily bypasses this admission for proven non-suspending source roots, relaxing
 * concurrent-entry exclusion; its fallback keeps conventional RootActor semantics.
 * Host execution accepts Protos values
 * and exact Java integer and String scalars (PLAT054-3B) as arguments, which ordinary binding
 * applies with its defaults and rest (D203 flexible arity). A Closure
 * without a compact Bytecode target (a native body, or a non-standard {@code call} selection) runs
 * through ordinary generic invocation ({@link ProtosInvocation#invoke}); no second call engine
 * exists.
 *
 * <p>PLAT054-3E2: an outermost embedding execution on the compact path is a suspendible host entry
 * ({@code FUTURES_AND_TASKS.md} §29): a pending {@code Future.value()} yields a C-prime
 * continuation back to this adapter, which waits and resumes it through {@link
 * ProtosHostEntrySuspension} before finishing the call. A nested (same-thread) entry is a foreign
 * callback and never suspends. An outermost entry of a Closure without a compact target (native
 * body or non-canonical {@code call}) runs through the shared C-prime entry root and suspends the
 * same way.
 *
 * <p>I102 (PLAT060-B5): every execution on the compact path enters through the single ordinary
 * Closure-call ABI; no host configuration selects a different frame-argument layout.
 *
 * <p>I115-H6 (experimental, deliberate semantic divergence): a source-backed execution with zero
 * or one argument builds the compact no-home B5 frame and performs exactly one guest call, direct
 * or indirect. It performs no Context revalidation, Process admission, RootActor entry, host-entry
 * suspension, ReturnHome completion, failure mapping, or guest-result projection; the conventional
 * and PLAT054-3E2 descriptions above apply only to the target-null generic path.
 */
@ExportLibrary(InteropLibrary.class)
final class ProtosHostExecutableClosure implements TruffleObject {
    private static final Object[] NO_SUPPLIED_ARGUMENTS = new Object[0];

    final ProtosClosureValue closure;
    final ProtosActivation caller;
    final ProtosLanguageContext owner;
    /** The compact Bytecode target; null only for an embedding adapter on the generic path. */
    final RootCallTarget target;
    /** Standard-embedding Process; null for session adapters. */
    final ProtosEmbeddedProcess embedding;

    /**
     * I115: effective-root non-suspension proof, captured once
     * when the host executable is prepared.
     *
     * This does not prove independence from RootActor state.
     */
    final boolean fastNonSuspendingHostEntry;
    /** I115: cached eligibility for a host call without a ReturnHome lifecycle. */
    final boolean fastNoHomeHostEntry;

    private ProtosHostExecutableClosure(
            ProtosClosureValue closure,
            ProtosActivation caller,
            ProtosLanguageContext owner,
            RootCallTarget target,
            ProtosEmbeddedProcess embedding) {
        this.closure = closure;
        this.caller = caller;
        this.owner = owner;
        this.target = target;
        this.embedding = embedding;
        this.fastNonSuspendingHostEntry =
                embedding != null
                        && target != null
                        && target.getRootNode() instanceof ProtosBytecodeRootNode root
                        && root.provablyNonSuspendingBody();
        this.fastNoHomeHostEntry =
                fastNonSuspendingHostEntry
                        && closure.hasUnobservableInvocationReturnHomeForRuntime();
    }

    /**
     * Prepares the adapter inside the entered owning Context. {@code caller} supplies the module,
     * Actor-domain, and dynamic-control provenance of the activation and must not own a Task.
     */
    static ProtosHostExecutableClosure prepareForRuntime(
            ProtosClosureValue closure, ProtosActivation caller) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(caller, "caller");
        if (closure.nativeBody().isPresent()) {
            throw new IllegalArgumentException("host executable requires a source-backed Closure");
        }
        if (caller.task().isPresent()) {
            throw new IllegalArgumentException("host executable provenance must not own a Task");
        }
        ProtosLanguageContext owner = ProtosLanguageContext.currentIfEnteredForRuntime();
        if (owner == null) {
            throw new IllegalStateException("host executable preparation requires an entered Context");
        }
        RootCallTarget target =
                ProtosClosureCallPreparation.functionCallTarget(closure, owner);
        if (target == null) {
            throw new IllegalArgumentException(
                    "Closure has no Bytecode target in the owning Context");
        }
        return new ProtosHostExecutableClosure(closure, caller, owner, target, null);
    }

    /**
     * PLAT054: prepares the executable for one receiver-bound extraction read from the host binding
     * scope of {@code embedding}, inside its entered owning Context. Source-backed and native
     * Closures are both accepted.
     */
    static ProtosHostExecutableClosure prepareForEmbeddingRuntime(
            ProtosClosureValue closure, ProtosActivation caller, ProtosEmbeddedProcess embedding) {
        Objects.requireNonNull(closure, "closure");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(embedding, "embedding");
        if (caller.task().isPresent()) {
            throw new IllegalArgumentException("host executable provenance must not own a Task");
        }
        ProtosLanguageContext owner = ProtosLanguageContext.currentIfEnteredForRuntime();
        if (owner == null) {
            throw new IllegalStateException("host executable preparation requires an entered Context");
        }
        RootCallTarget target =
                closure.nativeBody().isPresent()
                        ? null
                        : ProtosClosureCallPreparation.functionCallTarget(closure, owner);
        return new ProtosHostExecutableClosure(closure, caller, owner, target, embedding);
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    static final class Execute {
        @Specialization(guards = "receiver.target == null")
        @InliningCutoff
        static Object generic(
                ProtosHostExecutableClosure receiver,
                Object[] arguments,
                @Bind("$node") Node node)
                throws ArityException, UnsupportedTypeException {
            Object[] supplied = receiver.admitArguments(arguments, node);
            ProtosEmbeddedProcess embedding = receiver.embedding;
            Thread token = embedding.enterRootActor();
            HostEntryExtent previous = embedding.beginHostEntryExtent(token);
            try {
                if (token == null) {
                    // A nested entry is a foreign callback: ordinary synchronous invocation.
                    return invokeGeneric(receiver.closure, supplied, receiver.caller);
                }
                return ProtosHostEntrySuspension.executeSelected(
                        embedding, receiver.closure, supplied, receiver.caller);
            } catch (ProtosSignalException failure) {
                embedding.outermostEntryFailed(token, failure);
                throw failure;
            } finally {
                embedding.endHostEntryExtent(previous);
                embedding.exitRootActor(token);
            }
        }

        /*
         * I115: a source-backed host entry goes through the shared dispatcher
         * (GraalPy CallNode). The callee root owns its ReturnHome; a pending
         * outcome is driven by the host-entry suspension driver.
         */
        /*
         * I115: GraalPy PExecuteNode / CallDispatchers-inspired
         * zero-argument source entry. The benchmark surface supplies
         * no host arguments, so no conversion or host-entry protocol
         * is required for this experimental call path.
         */
        @Specialization(guards = {
                "receiver.target != null",
                "arguments.length == 0"
        })
        static Object sourceZero(
                ProtosHostExecutableClosure receiver,
                Object[] arguments,
                @Bind("$node") Node inliningTarget,
                @Cached ProtosFunctionCallDispatchers.FunctionCachedCallNode callNode) {
            // This host adapter has a prepared source-backed target; the
            // generic CallNode source/native classification is redundant.
            // Keep the entered Context, caller provenance and B5 ABI unchanged.
            Object outcome = callNode.execute(
                    null,
                    inliningTarget,
                    false,
                    receiver.closure,
                    ProtosFrameArguments.dispatchedCall0(
                            null,
                            ProtosFrameArguments.functionCallMarker(),
                            null,
                            null,
                            null),
                    receiver.caller,
                    null,
                    ProtosFunctionCallDispatchers.currentContext(inliningTarget));
            /*
             * I115 experimental zero-argument fast path:
             * return the exact guest outcome without the general
             * host Integer normalization. This keeps the Long
             * carrier unchanged when return speculation is disabled.
             */
            return outcome;
        }

        /*
         * GraalPy-style cold fallback: retain the nonzero-argument
         * implementation without expanding it into the ordinary
         * zero-argument host call.
         */
        @Specialization(guards = {
                "receiver.target != null",
                "arguments.length != 0"
        })
        @InliningCutoff
        static Object source(
                ProtosHostExecutableClosure receiver,
                Object[] arguments,
                @Bind("$node") Node node,
                @Cached ProtosFunctionCallDispatchers.CallNode callNode)
                throws ArityException, UnsupportedTypeException {
            Object[] supplied = receiver.admitArguments(arguments, node);
            ProtosEmbeddedProcess embedding = receiver.embedding;
            Thread token = embedding == null ? null : embedding.enterRootActor();
            HostEntryExtent previous =
                    embedding == null ? null : embedding.beginHostEntryExtent(token);
            try {
                Object outcome;

                    outcome =
                            callNode.execute(
                                    null,
                                    false,
                                    receiver.closure,
                                    ProtosFrameArguments.functionCallArguments(supplied),
                                    receiver.caller,
                                    null);

                if (ProtosTailCallContinuation.isPending(outcome)) {
                    outcome = drivePending(embedding, outcome);
                }
                return hostResult(receiver, outcome);
            } catch (ProtosSignalException failure) {
                if (embedding != null) {
                    embedding.outermostEntryFailed(token, failure);
                }
                throw failure;
            } finally {
                if (embedding != null) {
                    embedding.endHostEntryExtent(previous);
                    embedding.exitRootActor(token);
                }
            }
        }

        @TruffleBoundary
        private static Object drivePending(ProtosEmbeddedProcess embedding, Object outcome) {
            if (embedding == null) {
                ProtosTailCallContinuation.abandon(outcome);
                throw new IllegalStateException(
                        "a session host executable cannot drive a suspended continuation");
            }
            return ProtosHostEntrySuspension.resumeAfterWaits(embedding, outcome);
        }

        /*
         * I115: a signed-64 long carrier returned by any source root crosses
         * this Polyglot boundary as is, so hostResult does not recreate the
         * guest allocation removed from EpilogForReturn. A Java Integer is
         * never an I115 carrier: it keeps the ordinary normalization to a
         * guest Integer (there is no 32-bit guest integer).
         */
        private static Object hostResult(
                ProtosHostExecutableClosure receiver, Object result) {
            if (result instanceof Integer small) {
                return ProtosNumericValueSupport.guestValue(small);
            }
            return result;
        }

        @TruffleBoundary
        private static Object invokeGeneric(
                ProtosClosureValue closure, Object[] supplied, ProtosActivation caller) {
            return ProtosInvocation.invoke(closure, List.of(supplied), caller);
        }
    }

    /**
     * Validates the owning Context and returns the supplied Protos arguments. A session adapter
     * admits only zero arguments. An embedding adapter admits Protos values unchanged and, in
     * PLAT054-3B, the exact Java scalars {@link #admitHostScalar} converts, preserving argument
     * order; ordinary binding then applies D203 flexible arity. The supplied array is
     * copied only when some argument requires conversion.
     */
    Object[] admitArguments(Object[] arguments, Node node)
            throws ArityException, UnsupportedTypeException {
        Object[] admitted = arguments;
        if (embedding == null) {
            if (arguments.length != 0) {
                throw ArityException.create(0, 0, arguments.length);
            }
        } else {
            for (int index = 0; index < arguments.length; index++) {
                Object argument = arguments[index];
                if (!ProtosValueLookup.isProtosValue(argument)) {
                    if (admitted == arguments) {
                        admitted = arguments.clone();
                    }
                    admitted[index] = admitHostScalar(arguments, argument);
                }
            }
        }
        requireOwningContext(node);
        return admitted.length == 0 ? NO_SUPPLIED_ARGUMENTS : admitted;
    }

    private void requireOwningContext(Node node) {
        if (ProtosLanguageContext.current(node) != owner) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(
                    "host executable Closure entered outside its owning Context");
        }
    }

    /**
     * PLAT054-3B exact boundary conversion of one Java scalar argument: a Java {@code Byte},
     * {@code Short}, {@code Integer}, or {@code Long} becomes the ordinary Protos Integer of the
     * same value, and a Java {@code String} becomes the ordinary Protos String of the same Unicode
     * scalar sequence. A String that is not a valid scalar sequence, and every other host value, is
     * rejected before any guest code runs; nothing is converted lossily or through {@code
     * toString()}.
     */
    /**
     * I115: keep exact Java integer admission visible to Graal.
     * Text validation and rejected foreign types remain cold boundaries.
     */
    private static Object admitHostScalar(Object[] arguments, Object argument)
            throws UnsupportedTypeException {
        if (argument instanceof Integer
                || argument instanceof Long
                || argument instanceof Short
                || argument instanceof Byte) {
            return ProtosNumericValueSupport.integer(((Number) argument).longValue());
        }
        return admitHostTextOrReject(arguments, argument);
    }

    @TruffleBoundary
    private static Object admitHostTextOrReject(Object[] arguments, Object argument)
            throws UnsupportedTypeException {
        if (argument instanceof String text) {
            try {
                return new ProtosStringValue(text);
            } catch (IllegalArgumentException invalid) {
                throw UnsupportedTypeException.create(
                        arguments, "Java String argument is not a valid Unicode scalar sequence");
            }
        }
        throw UnsupportedTypeException.create(
                arguments,
                "Protos host execution accepts only Protos values, Java integers, and Java Strings");
    }
}
