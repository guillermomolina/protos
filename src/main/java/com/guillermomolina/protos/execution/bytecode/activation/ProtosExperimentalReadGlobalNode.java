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
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * I115-R6 / I115-H4-B / I115-H5 / I115-H7 EXPERIMENTAL, proxied as the {@code
 * ReadGlobal} operation: a bare read statically resolved to a module (ROOT)
 * declaration, as one DSL operation (GraalPy {@code ReadGlobalOrBuiltinNode}
 * shape: resolution separated from the fast access, both specialized in this
 * node, no caller-side branch).
 *
 * <p>I115-H7: {@link #readCached} is guarded by the identity of the module
 * scope selected from the invocation's captured chain (at most three) and
 * reads the CURRENT value of the module binding at its frame-backed
 * position, so a rebinding of the module binding is observed. When the
 * cached module no longer holds the binding at that position (for example a
 * prelude-level name), the read resolves generically for that execution.
 * {@link #readGeneric} is the separated fallback. The H5 unguarded value
 * cache is retired.
 *
 * <p>RECORDED DIVERGENCE (owner-authorized): only the statically resolved
 * module scope and the scopes outside it are consulted; no nearer-scope or
 * receiver-slot selection is performed.
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

    @Specialization(
            excludeForUncached = true,
            guards = {
                "module == cachedModule",
                "cachedModule != null"
            },
            limit = "3")
    static Object readCached(
            VirtualFrame frame,
            int frameOrdinal,
            TruffleString name,
            int lexicalDepth,
            @Bind("capturedModuleOrNull(frame.getArguments(), lexicalDepth)")
                    ProtosLexicalEnvironment module,
            @Cached("module") ProtosLexicalEnvironment cachedModule,
            @Cached("name.toJavaStringUncached()") String javaName) {
        // Java null is the frame-authority miss sentinel, never guest null.
        Object value = ProtosCapturedLexicalAccess.readOwnerFrameBindingOrNull(
                cachedModule, javaName, frameOrdinal);
        if (value != null) {
            return value;
        }
        return resolveInitial(frame.getArguments(), frameOrdinal, name, lexicalDepth);
    }

    @Specialization(replaces = "readCached")
    @InliningCutoff
    static Object readGeneric(
            VirtualFrame frame,
            int frameOrdinal,
            TruffleString name,
            int lexicalDepth) {
        return resolveInitial(frame.getArguments(), frameOrdinal, name, lexicalDepth);
    }

    /*
     * The generic resolution (fallback tier, uncached execution and the
     * cached tier's miss). The module scope of the invocation's captured chain, then
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
    static ProtosLexicalEnvironment capturedModuleOrNull(
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
