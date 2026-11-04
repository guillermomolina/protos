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

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosModuleBindingLocation;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * I115-R6 EXPERIMENTAL, proxied as the {@code ReadGlobal} operation: a bare
 * read from a Closure root statically resolved to a module (ROOT)
 * declaration. I115: module declarations are NAME bindings of the module
 * context, so this is one name operation over the captured module scope at
 * the constant {@code lexicalDepth}, then the scopes outside it (the
 * prelude). It never reads a frame.
 *
 * <p>{@link #readCached} is guarded by the identity of the invoked compact
 * Closure (at most three entries). A Closure's captured environment, and so
 * its module context and that context's single store, never change, which
 * justifies reusing the {@link ProtosModuleBindingLocation} resolved once at
 * specialization: the hot read is one inlined {@code DynamicObject} get of the
 * current value, with no chain walk, boundary call or allocation. A binding
 * absent at that location (removed) takes the unspecialized NAME read over
 * the same storage; published activations and PIC misses use {@link
 * #readGeneric}.
 *
 * <p>RECORDED DIVERGENCE (owner-authorized): only the statically resolved
 * module scope and the scopes outside it are consulted; no nearer-scope or
 * receiver-slot selection is performed.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
@ConstantOperand(type = TruffleString.class, name = "name")
@ConstantOperand(type = int.class, name = "lexicalDepth")
abstract class ProtosExperimentalReadGlobalNode extends Node {

    abstract Object execute(VirtualFrame frame, TruffleString name, int lexicalDepth);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "closure != null",
                "closure == cachedClosure",
                "location != null"
            },
            limit = "3")
    static Object readCached(
            VirtualFrame frame,
            TruffleString name,
            int lexicalDepth,
            @Bind("compactClosureOrNull(frame.getArguments())") ProtosClosureValue closure,
            @Cached("closure") ProtosClosureValue cachedClosure,
            @Cached("locationOrNull(cachedClosure, name, lexicalDepth)")
                    ProtosModuleBindingLocation location,
            @Cached DynamicObject.GetNode getNode) {
        Object value = location.readOrNull(getNode);
        if (value == null) {
            return readModuleBinding(frame.getArguments(), name, lexicalDepth);
        }
        return value;
    }

    @Specialization(replaces = "readCached")
    static Object readGeneric(VirtualFrame frame, TruffleString name, int lexicalDepth) {
        return readModuleBinding(frame.getArguments(), name, lexicalDepth);
    }

    /*
     * I115-H7-R4: B5's first field identifies the invoked Closure while the
     * call is compact; a published activation is deliberately not admitted.
     */
    static ProtosClosureValue compactClosureOrNull(Object[] arguments) {
        if (arguments.length < ProtosFrameArguments.HEADER_LENGTH) {
            // I115-H11 captured unary entry, plain or Prelude-extended.
            return ProtosFrameArguments.experimentalCapturedOwnerOrNull(arguments);
        }
        return arguments[0] instanceof ProtosClosureValue closure ? closure : null;
    }

    /* Specialization time only: the module binding's location for this Closure. */
    @TruffleBoundary
    static ProtosModuleBindingLocation locationOrNull(
            ProtosClosureValue closure, TruffleString name, int lexicalDepth) {
        if (closure == null) {
            return null;
        }
        ProtosLexicalEnvironment module =
                ProtosLexicalEnvironment.at(
                        closure.capturedLexicalEnvironmentForRuntime(), lexicalDepth - 1);
        return module == null
                ? null
                : ProtosModuleBindingLocation.ofOrNull(module.context(), name.toJavaStringUncached());
    }

    @TruffleBoundary
    static Object readModuleBinding(
            Object[] arguments, TruffleString name, int lexicalDepth) {
        String javaName = name.toJavaStringUncached();
        ProtosLexicalEnvironment module = capturedModuleOrNull(arguments, lexicalDepth);
        if (module == null) {
            return readOutsideModuleGlobal(arguments, null, javaName);
        }
        java.util.Optional<Object> present = module.readLocalSlotForRuntime(javaName);
        if (present.isPresent()) {
            return present.get();
        }
        return readOutsideModuleGlobal(arguments, module.outer(), javaName);
    }

    /*
     * The captured chain of the invocation, from its B5 header: a
     * still-compact call's Closure, or the published activation's.
     */
    static ProtosLexicalEnvironment capturedModuleOrNull(
            Object[] arguments, int lexicalDepth) {
        ProtosLexicalEnvironment captured;
        ProtosClosureValue owner = arguments.length < ProtosFrameArguments.HEADER_LENGTH
                ? ProtosFrameArguments.experimentalCapturedOwnerOrNull(arguments)
                : null;
        if (owner != null) {
            // I115-H11: the compact unary entry retains its lexical owner.
            captured = owner.capturedLexicalEnvironmentForRuntime();
        } else if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)) {
            captured = ProtosFrameArguments.compactCapturedLexicalEnvironment(arguments);
        } else {
            captured = ProtosFrameArguments.publishedActivation(arguments)
                    .capturedLexicalEnvironmentForRuntime();
        }
        return ProtosLexicalEnvironment.at(captured, lexicalDepth - 1);
    }

    /* ABSENT in the module: only the scopes outside it (the prelude). */
    private static Object readOutsideModuleGlobal(
            Object[] arguments,
            ProtosLexicalEnvironment outside,
            String name) {
        for (ProtosLexicalEnvironment scope = outside;
                scope != null;
                scope = scope.outer()) {
            java.util.Optional<Object> present = scope.readLocalSlotForRuntime(name);
            if (present.isPresent()) {
                return present.get();
            }
        }
        throw new ProtosSignalException(
                ProtosCoreErrors.newSlotNotFound(
                        ProtosFrameArguments.activation(arguments)));
    }
}
