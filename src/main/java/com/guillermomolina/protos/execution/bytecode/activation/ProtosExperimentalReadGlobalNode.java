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

import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * I115-R6 / I115-H4-B / I115-H5 EXPERIMENTAL, proxied as the {@code
 * ReadGlobal} operation: a bare read statically resolved to a module (ROOT)
 * declaration.
 *
 * <p>I115-H5: the value is resolved once, when the specialization is
 * installed, and every later execution returns that value. The cached read
 * has no guard and no fallback: a later rebinding, a different module,
 * authority or Context is not observed. The uncached interpreter resolves on
 * every execution. A recorded, owner-authorized divergence.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
@ConstantOperand(type = int.class, name = "frameOrdinal")
@ConstantOperand(type = TruffleString.class, name = "name")
@ConstantOperand(type = int.class, name = "lexicalDepth")
abstract class ProtosExperimentalReadGlobalNode extends Node {

    abstract Object execute(
            VirtualFrame frame, int frameOrdinal, TruffleString name, int lexicalDepth);

    @Specialization
    static Object readCached(
            VirtualFrame frame,
            int frameOrdinal,
            TruffleString name,
            int lexicalDepth,
            @Cached(
                            value = "resolveInitial(frame.getArguments(), frameOrdinal, name, lexicalDepth)",
                            uncached = "resolveInitial(frame.getArguments(), frameOrdinal, name, lexicalDepth)",
                            neverDefault = true)
                    Object cachedValue) {
        return cachedValue;
    }

    /*
     * Installation (or uncached execution) only: the former generic
     * resolution. The module scope of the invocation's captured chain, then
     * the scopes outside it (the prelude).
     */
    @TruffleBoundary
    static Object resolveInitial(
            Object[] arguments, int frameOrdinal, TruffleString name, int lexicalDepth) {
        String javaName = name.toJavaStringUncached();
        ProtosLexicalEnvironment module = capturedModuleOrNull(arguments, lexicalDepth);
        if (module == null) {
            return readOutsideModuleGlobal(arguments, null, javaName);
        }
        // Java null is the frame-authority miss sentinel, never guest null.
        Object value =
                ProtosCapturedLexicalAccess.readOwnerFrameBindingOrNull(
                        module, javaName, frameOrdinal);
        if (value != null) {
            return value;
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
    private static ProtosLexicalEnvironment capturedModuleOrNull(
            Object[] arguments, int lexicalDepth) {
        ProtosLexicalEnvironment captured;
        if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)) {
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
