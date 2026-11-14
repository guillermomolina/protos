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
import com.guillermomolina.protos.runtime.ProtosLexicalEnvironment;
import com.guillermomolina.protos.runtime.ProtosModuleBindingLocation;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
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
 * I115, proxied as the {@code SelectWriteGlobal} operation: the destination
 * selection of a bare assignment statically resolved to a module (ROOT)
 * declaration, the write counterpart of {@code ReadGlobal}, with the same
 * physical {@code lexicalDepth} contract ({@code 0}: the running module
 * root's own context; {@code n >= 1}: the captured module of the running
 * Closure root).
 *
 * <p>The lowering emits it before the right-hand side and keeps the selected
 * {@link ProtosModuleBindingLocation}, which fixes the exact module context
 * and binding key. The {@code WriteGlobal} operation then assigns through
 * that location only, without resolving the name again.
 *
 * <p>I115-S1c-L: when the module binding is absent at selection time it is not
 * the selected destination; {@link ProtosLexicalSelectionNode} selects the
 * exact writable destination instead (or signals {@code SlotNotFound}), still
 * before the right-hand side runs, and {@code WriteGlobal} assigns through
 * that destination. No binding is ever created.
 *
 * <p>{@link #selectOwnCached} is guarded by the identity of the module
 * context, {@link #selectCapturedCached} by the identity of the invoked
 * Closure (argument 0 of a still-compact B5 header); at most three entries
 * each. Neither identity's module context ever changes, so the location
 * resolved once at specialization remains the binding's address; only its
 * presence is checked per execution. Published Closure activations and PIC
 * misses take {@link #selectGeneric}, a selection over the same module
 * storage.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
@ConstantOperand(type = TruffleString.class, name = "name")
@ConstantOperand(type = int.class, name = "lexicalDepth")
abstract class ProtosExperimentalWriteGlobalNode extends Node {

    /** The selected destination: a {@link ProtosModuleBindingLocation} or a selected target. */
    abstract Object execute(
            VirtualFrame frame, TruffleString name, int lexicalDepth);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "lexicalDepth == 0",
                "context != null",
                "context == cachedContext",
                "location != null"
            },
            limit = "3")
    static Object selectOwnCached(
            VirtualFrame frame,
            TruffleString name,
            int lexicalDepth,
            @Bind("ownModuleContextOrNull(frame.getArguments())") ProtosObjectValue context,
            @Cached("context") ProtosObjectValue cachedContext,
            @Cached("ownLocationOrNull(cachedContext, name)")
                    ProtosModuleBindingLocation location,
            @Cached DynamicObject.GetNode getNode) {
        if (!location.isPresent(getNode)) {
            return selectLexically(frame.getArguments(), name);
        }
        return location;
    }

    @Specialization(
            excludeForUncached = true,
            guards = {
                "lexicalDepth >= 1",
                "closure != null",
                "closure == cachedClosure",
                "location != null"
            },
            limit = "3")
    static Object selectCapturedCached(
            VirtualFrame frame,
            TruffleString name,
            int lexicalDepth,
            @Bind("compactClosureOrNull(frame.getArguments())") ProtosClosureValue closure,
            @Cached("closure") ProtosClosureValue cachedClosure,
            @Cached("locationOrNull(cachedClosure, name, lexicalDepth)")
                    ProtosModuleBindingLocation location,
            @Cached DynamicObject.GetNode getNode) {
        if (!location.isPresent(getNode)) {
            return selectLexically(frame.getArguments(), name);
        }
        return location;
    }

    @Specialization(replaces = {"selectOwnCached", "selectCapturedCached"})
    static Object selectGeneric(
            VirtualFrame frame, TruffleString name, int lexicalDepth) {
        return selectModuleBinding(frame.getArguments(), name, lexicalDepth);
    }

    static ProtosObjectValue ownModuleContextOrNull(Object[] arguments) {
        return ProtosExperimentalReadGlobalNode.ownModuleContextOrNull(arguments);
    }

    static ProtosClosureValue compactClosureOrNull(Object[] arguments) {
        return ProtosExperimentalReadGlobalNode.compactClosureOrNull(arguments);
    }

    /* Specialization time only: the module binding's location in its own module context. */
    static ProtosModuleBindingLocation ownLocationOrNull(
            ProtosObjectValue context, TruffleString name) {
        return ProtosExperimentalReadGlobalNode.ownLocationOrNull(context, name);
    }

    /* Specialization time only: the module binding's location for this Closure. */
    static ProtosModuleBindingLocation locationOrNull(
            ProtosClosureValue closure, TruffleString name, int lexicalDepth) {
        return ProtosExperimentalReadGlobalNode.locationOrNull(closure, name, lexicalDepth);
    }

    @TruffleBoundary
    static Object selectModuleBinding(
            Object[] arguments, TruffleString name, int lexicalDepth) {
        ProtosObjectValue context;
        if (lexicalDepth == 0) {
            context = ProtosExperimentalReadGlobalNode.ownModuleActivation(arguments)
                    .materializedContextOrNullForRuntime();
        } else {
            ProtosLexicalEnvironment module =
                    ProtosExperimentalReadGlobalNode.capturedModuleOrNull(arguments, lexicalDepth);
            context = module == null ? null : module.context();
        }
        ProtosModuleBindingLocation location =
                context == null
                        ? null
                        : ProtosModuleBindingLocation.ofOrNull(context, name.toJavaStringUncached());
        if (location == null) {
            return selectLexically(arguments, name);
        }
        return location;
    }

    /* The absent module binding was not selected: live lexical write-target selection. */
    @TruffleBoundary
    private static Object selectLexically(Object[] arguments, TruffleString name) {
        return ProtosLexicalSelectionNode.writeTargetOrThrow(
                ProtosFrameArguments.activation(arguments), name.toJavaStringUncached());
    }
}
