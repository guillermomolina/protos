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
 * I115-R6, proxied as the {@code ReadGlobal} operation: a bare read
 * statically resolved to a module (ROOT) declaration. I115: module
 * declarations are NAME bindings of the module context, so this is one name
 * operation over that context, found from the running root's frame
 * arguments at the constant physical {@code lexicalDepth}:
 * <ul>
 *   <li>{@code 0}: the running root is the module root itself, entered with
 *       its published module activation in argument 0; the module context is
 *       that activation's own context;</li>
 *   <li>{@code n >= 1}: the running root is a Closure root; the module
 *       context is the scope {@code n - 1} of the invoked Closure's captured
 *       environment.</li>
 * </ul>
 * Locating a present module binding does not materialize an activation or
 * consult dynamic lexical lookup. An absent binding resumes ordinary lookup
 * through the invoking activation with {@link ProtosLexicalSelectionNode},
 * including receiver fallback.
 *
 * <p>{@link #readOwnCached} (depth 0) is guarded by the identity of the
 * module context, and {@link #readCapturedCached} (depth {@code >= 1}) by
 * the identity of the invoked Closure, read from argument 0 of a still-compact
 * B5 header (at most three entries each). Neither identity's module context
 * or store ever changes, which justifies reusing the {@link
 * ProtosModuleBindingLocation} resolved once at specialization: the hot read
 * is one inlined {@code DynamicObject} get of the current value. A binding
 * absent at that location takes the unspecialized NAME read over the same
 * storage; published Closure activations and PIC misses use {@link
 * #readGeneric}.
 *
 * <p>{@code candidate} is true when the site was classified {@code
 * Candidate}: the module statically declares the name but establishment is
 * not guaranteed at this program point. Candidate classification does not
 * terminate lookup when the selected binding is absent. If ordinary lookup
 * also fails, the read signals the standard unqualified {@code SlotNotFound} Error.
 *
 * <p>I115-S1c-L nearer shadowing: the lowering emits {@code ReadGlobal} only
 * when the module binding is the actual nearest selected binding whenever it
 * is PRESENT: no Closure scope between the site and the module may hold a
 * nearer binding of the name, because none of them declares it and none of
 * them can have its Context observed (a dynamic binding can only be created
 * through an observed Context). Every other site uses live lexical selection
 * instead, so a present module binding never hides a nearer dynamic one. The
 * module context's identity and its fixed store guard the cached location;
 * removal and recreation are observed through that store.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = false)
@ConstantOperand(type = TruffleString.class, name = "name")
@ConstantOperand(type = int.class, name = "lexicalDepth")
@ConstantOperand(type = boolean.class, name = "candidate")
abstract class ProtosExperimentalReadGlobalNode extends Node {

    abstract Object execute(
            VirtualFrame frame, TruffleString name, int lexicalDepth, boolean candidate);

    @Specialization(
            excludeForUncached = true,
            guards = {
                "lexicalDepth == 0",
                "context != null",
                "context == cachedContext",
                "location != null"
            },
            limit = "3")
    static Object readOwnCached(
            VirtualFrame frame,
            TruffleString name,
            int lexicalDepth,
            boolean candidate,
            @Bind("ownModuleContextOrNull(frame.getArguments())") ProtosObjectValue context,
            @Cached("context") ProtosObjectValue cachedContext,
            @Cached("ownLocationOrNull(cachedContext, name)")
                    ProtosModuleBindingLocation location,
            @Cached DynamicObject.GetNode getNode) {
        Object value = location.readOrNull(getNode);
        if (value == null) {
            return readModuleBinding(frame.getArguments(), name, lexicalDepth, candidate);
        }
        return value;
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
    static Object readCapturedCached(
            VirtualFrame frame,
            TruffleString name,
            int lexicalDepth,
            boolean candidate,
            @Bind("compactClosureOrNull(frame.getArguments())") ProtosClosureValue closure,
            @Cached("closure") ProtosClosureValue cachedClosure,
            @Cached("locationOrNull(cachedClosure, name, lexicalDepth)")
                    ProtosModuleBindingLocation location,
            @Cached DynamicObject.GetNode getNode) {
        Object value = location.readOrNull(getNode);
        if (value == null) {
            return readModuleBinding(frame.getArguments(), name, lexicalDepth, candidate);
        }
        return value;
    }

    @Specialization(replaces = {"readOwnCached", "readCapturedCached"})
    static Object readGeneric(
            VirtualFrame frame, TruffleString name, int lexicalDepth, boolean candidate) {
        return readModuleBinding(frame.getArguments(), name, lexicalDepth, candidate);
    }

    /*
     * Depth 0: the context of the module activation published in argument 0,
     * or null when that context does not exist yet (no binding can be present
     * then). Never materializes the activation or its context.
     */
    static ProtosObjectValue ownModuleContextOrNull(Object[] arguments) {
        return arguments.length > 0 && arguments[0] instanceof ProtosActivation activation
                ? activation.materializedContextOrNullForRuntime()
                : null;
    }

    /*
     * Depth >= 1: the invoked Closure of a still-compact B5 call (argument 0);
     * a published activation is deliberately not admitted by the PIC.
     */
    static ProtosClosureValue compactClosureOrNull(Object[] arguments) {
        return arguments.length >= ProtosFrameArguments.HEADER_LENGTH
                        && arguments[0] instanceof ProtosClosureValue closure
                ? closure
                : null;
    }

    /* Specialization time only: the module binding's location in its own module context. */
    @TruffleBoundary
    static ProtosModuleBindingLocation ownLocationOrNull(
            ProtosObjectValue context, TruffleString name) {
        return ProtosModuleBindingLocation.ofOrNull(context, name.toJavaStringUncached());
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
            Object[] arguments, TruffleString name, int lexicalDepth, boolean candidate) {
        String javaName = name.toJavaStringUncached();
        if (lexicalDepth == 0) {
            ProtosActivation module = ownModuleActivation(arguments);
            java.util.Optional<Object> present = module.readCurrentLocalSlotForRuntime(javaName);
            if (present.isPresent()) {
                return present.get();
            }
        } else {
            ProtosLexicalEnvironment module = capturedModuleOrNull(arguments, lexicalDepth);
            if (module != null) {
                java.util.Optional<Object> present = module.readLocalSlotForRuntime(javaName);
                if (present.isPresent()) {
                    return present.get();
                }
            }
        }
        // I115-S1c-L: the absent module binding was not selected; live
        // lexical selection continues the ordinary lookup from the start.
        return ProtosLexicalSelectionNode.readOrThrow(
                ProtosFrameArguments.activation(arguments), javaName);
    }

    /*
     * Depth 0: the module root's own activation. A module root is entered
     * with its module activation already published in argument 0, so this
     * returns it unchanged; it is the same accessor every operation of the
     * root uses for its current activation.
     */
    static ProtosActivation ownModuleActivation(Object[] arguments) {
        return ProtosFrameArguments.activation(arguments);
    }

    /*
     * Depth >= 1: the captured module scope of the invocation, from its B5
     * header: a still-compact call's Closure, or the published activation's.
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

}
