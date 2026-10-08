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
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
import com.oracle.truffle.api.nodes.IndirectCallNode;
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
 * {@link #embedding} Process. Each execution is then a RootActor host entry (concurrent entry is
 * rejected, an Error escaping an outermost entry fails the RootActor), and it accepts Protos values
 * as arguments, which ordinary binding applies with its defaults, rest, and arity Errors. A Closure
 * without a compact Bytecode target (a native body, or a non-standard {@code call} selection) runs
 * through ordinary generic invocation ({@link ProtosInvocation#invoke}); no second call engine
 * exists.
 */
@ExportLibrary(InteropLibrary.class)
final class ProtosHostExecutableClosure implements TruffleObject {
    private static final Object[] NO_SUPPLIED_ARGUMENTS = new Object[0];

    final ProtosClosureValue closure;
    final ProtosActivation caller;
    final ProtosLanguageContext owner;
    /** The compact Bytecode target; null only for an embedding adapter on the generic path. */
    final RootCallTarget target;
    /** The standard-embedding Process whose RootActor each execution enters; null for sessions. */
    final ProtosEmbeddedProcess embedding;

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
                ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(closure, owner);
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
                        : ProtosBytecodeRootNode.PrepareSendArguments.fastOrdinarySendTarget(
                                closure, owner);
        return new ProtosHostExecutableClosure(closure, caller, owner, target, embedding);
    }

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    static final class Execute {
        @Specialization(guards = "receiver.target == null")
        static Object generic(
                ProtosHostExecutableClosure receiver,
                Object[] arguments,
                @Bind("$node") Node node)
                throws ArityException, UnsupportedTypeException {
            Object[] supplied = receiver.admitArguments(arguments, node);
            ProtosEmbeddedProcess embedding = receiver.embedding;
            Thread token = embedding.enterRootActor();
            try {
                return invokeGeneric(receiver.closure, supplied, receiver.caller);
            } catch (ProtosSignalException failure) {
                embedding.outermostEntryFailed(token, failure);
                throw failure;
            } finally {
                embedding.exitRootActor(token);
            }
        }

        @Specialization(
                guards = {"receiver.target != null", "receiver.target == cachedTarget"},
                limit = "3")
        static Object direct(
                ProtosHostExecutableClosure receiver,
                Object[] arguments,
                @Bind("$node") Node node,
                @Cached("receiver.target") RootCallTarget cachedTarget,
                @Cached("create(cachedTarget)") DirectCallNode call)
                throws ArityException, UnsupportedTypeException {
            Object[] supplied = receiver.admitArguments(arguments, node);
            ProtosEmbeddedProcess embedding = receiver.embedding;
            Thread token = embedding == null ? null : embedding.enterRootActor();
            try {
                ProtosBytecodeRootNode.OrdinarySourceCall prepared = receiver.prepare(supplied);
                return prepared.finish(
                        ProtosBytecodeRootNode.EnterClosureCall.ordinaryDirect(
                                prepared, cachedTarget, call));
            } catch (ProtosSignalException failure) {
                if (embedding != null) {
                    embedding.outermostEntryFailed(token, failure);
                }
                throw failure;
            } finally {
                if (embedding != null) {
                    embedding.exitRootActor(token);
                }
            }
        }

        @Specialization(guards = "receiver.target != null", replaces = "direct")
        static Object indirect(
                ProtosHostExecutableClosure receiver,
                Object[] arguments,
                @Bind("$node") Node node,
                @Cached IndirectCallNode call)
                throws ArityException, UnsupportedTypeException {
            Object[] supplied = receiver.admitArguments(arguments, node);
            ProtosEmbeddedProcess embedding = receiver.embedding;
            Thread token = embedding == null ? null : embedding.enterRootActor();
            try {
                ProtosBytecodeRootNode.OrdinarySourceCall prepared = receiver.prepare(supplied);
                return prepared.finish(
                        ProtosBytecodeRootNode.EnterClosureCall.ordinaryIndirect(prepared, call));
            } catch (ProtosSignalException failure) {
                if (embedding != null) {
                    embedding.outermostEntryFailed(token, failure);
                }
                throw failure;
            } finally {
                if (embedding != null) {
                    embedding.exitRootActor(token);
                }
            }
        }

        @TruffleBoundary
        private static Object invokeGeneric(
                ProtosClosureValue closure, Object[] supplied, ProtosActivation caller) {
            return ProtosInvocation.invoke(closure, List.of(supplied), caller);
        }
    }

    /**
     * Validates the owning Context and returns the supplied Protos arguments. A session adapter
     * admits only zero arguments; an embedding adapter admits Protos values, whose arity is then
     * checked by ordinary binding as a guest Error. Host values that are not Protos values are not
     * converted here.
     */
    Object[] admitArguments(Object[] arguments, Node node)
            throws ArityException, UnsupportedTypeException {
        if (embedding == null) {
            if (arguments.length != 0) {
                throw ArityException.create(0, 0, arguments.length);
            }
        } else {
            for (Object argument : arguments) {
                if (!ProtosValueLookup.isProtosValue(argument)) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedTypeException.create(
                            arguments, "Protos host execution accepts only Protos values");
                }
            }
        }
        if (ProtosLanguageContext.current(node) != owner) {
            // The framework never enters another Context for this value; a foreign target is fatal.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(
                    "host executable Closure entered outside its owning Context");
        }
        return arguments.length == 0 ? NO_SUPPLIED_ARGUMENTS : arguments;
    }

    /**
     * The compact prepared call one session-adapter execution enters; package-visible for
     * structural tests. A session adapter never raises {@link UnsupportedTypeException}.
     */
    ProtosBytecodeRootNode.OrdinarySourceCall prepare(Object[] arguments, Node node)
            throws ArityException {
        try {
            return prepare(admitArguments(arguments, node));
        } catch (UnsupportedTypeException unexpected) {
            throw new IllegalStateException("session adapter rejected a Protos argument", unexpected);
        }
    }

    private ProtosBytecodeRootNode.OrdinarySourceCall prepare(Object[] supplied) {
        return (ProtosBytecodeRootNode.OrdinarySourceCall)
                ProtosBytecodeRootNode.finishDirectClosureCall(closure, target, supplied, caller);
    }
}
