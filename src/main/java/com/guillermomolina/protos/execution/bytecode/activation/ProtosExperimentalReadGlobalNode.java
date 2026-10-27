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
import com.guillermomolina.protos.runtime.ProtosLexicalBindingAuthority;
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.StoreBytecodeIndex;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * I115-R6 / I115-H4-B EXPERIMENTAL (GraalPy ReadGlobalOrBuiltinNode),
 * proxied as the {@code ReadGlobal} operation: a bare read statically
 * resolved to a module (ROOT) declaration reads the exact module scope of
 * the invoked Closure's captured chain, with no nearer-scope absence guard
 * and no receiver fallback. The value is read on every execution.
 *
 * <p>ADAPTED: GraalPy's globals dictionary Shape is represented by the
 * identity of the module's lexical binding authority; there is no PDict or
 * DynamicObjectStorage. {@link #readPreludeFastPath} caches that identity
 * while the name is absent from the module together with the selected outer
 * (prelude) scope; {@link #readGlobalFastPath} caches the module's
 * frame-backed authority and reads the constant frame ordinal; {@link
 * #readGlobalGeneric} is the former resolution. The TruffleString name is
 * converted once per cache installation.
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

    @ForceQuickening
    @Specialization(
            excludeForUncached = true,
            guards = {
                "module != null",
                "authorityOf(module) == cachedAuthority",
                "cachedOutside != null"
            },
            limit = "1")
    static Object readPreludeFastPath(
            VirtualFrame frame,
            int frameOrdinal,
            TruffleString name,
            int lexicalDepth,
            @Bind("moduleEnvironmentOrNull(frame, lexicalDepth)") ProtosLexicalEnvironment module,
            @Cached("name.toJavaStringUncached()") String cachedName,
            @Cached(value = "authorityOf(module)", weak = true)
                    ProtosLexicalBindingAuthority cachedAuthority,
            @Cached(value = "outsideScopeOrNull(module, cachedName, frameOrdinal)", weak = true)
                    ProtosLexicalEnvironment cachedOutside) {
        java.util.Optional<Object> present = cachedOutside.readLocalSlotForRuntime(cachedName);
        if (present.isPresent()) {
            return present.get();
        }
        return resolve(frame.getArguments(), module, frameOrdinal, cachedName);
    }

    @ForceQuickening
    @Specialization(
            excludeForUncached = true,
            replaces = "readPreludeFastPath",
            guards = {
                "module != null",
                "isAuthorityOf(module, cachedAuthority)",
                "cachedAuthority != null"
            },
            limit = "1")
    static Object readGlobalFastPath(
            VirtualFrame frame,
            int frameOrdinal,
            TruffleString name,
            int lexicalDepth,
            @Bind("moduleEnvironmentOrNull(frame, lexicalDepth)") ProtosLexicalEnvironment module,
            @Cached("name.toJavaStringUncached()") String cachedName,
            @Cached(value = "frameAuthorityHoldingOrNull(module, cachedName, frameOrdinal)", weak = true)
                    ProtosFrameLexicalBindingAuthority cachedAuthority) {
        if (cachedAuthority.hasFrameBackedBindingAt(cachedName, frameOrdinal)) {
            return cachedAuthority.readFrameBackedBindingAt(cachedName, frameOrdinal);
        }
        return resolve(frame.getArguments(), module, frameOrdinal, cachedName);
    }

    @StoreBytecodeIndex
    @Specialization(replaces = {"readPreludeFastPath", "readGlobalFastPath"})
    static Object readGlobalGeneric(
            VirtualFrame frame,
            int frameOrdinal,
            TruffleString name,
            int lexicalDepth,
            @Bind Node inliningTarget,
            @Cached TruffleString.ToJavaStringNode toJavaString,
            @Cached InlinedBranchProfile outsideModuleProfile) {
        Object[] arguments = frame.getArguments();
        String javaName = toJavaString.execute(name);
        ProtosLexicalEnvironment module = capturedModuleOrNull(arguments, lexicalDepth);
        if (module == null) {
            outsideModuleProfile.enter(inliningTarget);
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
        outsideModuleProfile.enter(inliningTarget);
        return readOutsideModuleGlobal(arguments, module.outer(), javaName);
    }

    @NonIdempotent
    static ProtosLexicalEnvironment moduleEnvironmentOrNull(VirtualFrame frame, int lexicalDepth) {
        return capturedModuleOrNull(frame.getArguments(), lexicalDepth);
    }

    @NonIdempotent
    static ProtosLexicalBindingAuthority authorityOf(ProtosLexicalEnvironment module) {
        return module.lexicalBindingAuthorityForRuntime();
    }

    /* Identity of the module's current authority and a cached frame-backed one. */
    @NonIdempotent
    static boolean isAuthorityOf(
            ProtosLexicalEnvironment module, ProtosFrameLexicalBindingAuthority cached) {
        return module.lexicalBindingAuthorityForRuntime() == cached;
    }

    /*
     * The captured chain of the invocation: the GraalPy unary header's
     * globals slot, a still-compact call's Closure, or the published
     * activation's.
     */
    private static ProtosLexicalEnvironment capturedModuleOrNull(
            Object[] arguments, int lexicalDepth) {
        ProtosLexicalEnvironment captured;
        if (ProtosFrameArguments.isExperimentalGraalPyUnaryCall(arguments)) {
            captured = ProtosFrameArguments.experimentalGlobals(arguments);
        } else if (ProtosFrameArguments.isUnmaterializedCompactCall(arguments)) {
            captured = ProtosFrameArguments.compactCapturedLexicalEnvironment(arguments);
        } else {
            captured = ProtosFrameArguments.publishedActivation(arguments)
                    .capturedLexicalEnvironmentForRuntime();
        }
        return ProtosLexicalEnvironment.at(captured, lexicalDepth - 1);
    }

    /* Installation only: the module's frame-backed authority holding the name, or null. */
    static ProtosFrameLexicalBindingAuthority frameAuthorityHoldingOrNull(
            ProtosLexicalEnvironment module, String name, int frameOrdinal) {
        return module.lexicalBindingAuthorityForRuntime()
                                instanceof ProtosFrameLexicalBindingAuthority authority
                        && authority.hasFrameBackedBindingAt(name, frameOrdinal)
                ? authority
                : null;
    }

    /*
     * Installation only: the selected scope outside the module that holds
     * the name while the module does not, or null.
     */
    @TruffleBoundary
    static ProtosLexicalEnvironment outsideScopeOrNull(
            ProtosLexicalEnvironment module, String name, int frameOrdinal) {
        if (ProtosCapturedLexicalAccess.readOwnerFrameBindingOrNull(module, name, frameOrdinal)
                        != null
                || module.readLocalSlotForRuntime(name).isPresent()) {
            return null;
        }
        for (ProtosLexicalEnvironment scope = module.outer();
                scope != null;
                scope = scope.outer()) {
            if (scope.readLocalSlotForRuntime(name).isPresent()) {
                return scope;
            }
        }
        return null;
    }

    /* A fast-path miss: the generic resolution, unchanged. */
    @TruffleBoundary
    private static Object resolve(
            Object[] arguments,
            ProtosLexicalEnvironment module,
            int frameOrdinal,
            String name) {
        Object value =
                ProtosCapturedLexicalAccess.readOwnerFrameBindingOrNull(module, name, frameOrdinal);
        if (value != null) {
            return value;
        }
        java.util.Optional<Object> present = module.readLocalSlotForRuntime(name);
        if (present.isPresent()) {
            return present.get();
        }
        return readOutsideModuleGlobal(arguments, module.outer(), name);
    }

    /* ABSENT in the module: only the scopes outside it (the prelude). */
    @TruffleBoundary
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
