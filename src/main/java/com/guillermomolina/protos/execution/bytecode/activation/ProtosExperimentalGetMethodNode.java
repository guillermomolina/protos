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
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosFloatValue;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.guillermomolina.protos.runtime.ProtosValueLookup.StructuralSelection;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ForceQuickening;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.PropertyGetter;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Proxied as the {@code GetMethod} operation: the selection half of every
 * ordinary send. The receiver was evaluated once by the caller bytecode; the
 * selector is a constant operand.
 * Ordinary selection returns a {@link ProtosBoundMethod} carrying the
 * selected Closure, receiver and methodHome. Direct selection returns the
 * raw Closure only when the receiver itself owns the selected slot (an
 * inherited selection keeps its owning methodHome); D188 foreign member
 * selection returns its read callable. Nothing is prepared or executed here.
 *
 * <p>Selection and binding are distinct: each cached entry selects the
 * owner and the current Closure; the actual evaluated receiver is bound on
 * every execution. Every cached entry guards the selector identity, so a
 * child reused for several selectors never answers one selector with the
 * selection of another. The cached entries are:
 *
 * <ul>
 *   <li>{@link #doOwn}: an exact ordinary receiver owning the selector. The
 *       cached {@code Shape} proves the local slot; the receiver's current
 *       value is read through the cached {@link PropertyGetter}, so receivers
 *       sharing the Shape keep their own Closures.
 *   <li>{@link #doInherited}: an exact ordinary receiver not owning the
 *       selector. One structural selection from the receiver's direct parent
 *       is shared by every receiver with this Shape and that exact parent;
 *       its home is the real owner.
 *   <li>{@link #doLong}, {@link #doDouble}, {@link #doFloatValue},
 *       {@link #doString}: a SmallInteger, Float or String receiver, selected from the prototype of the
 *       observing Prelude's FROZEN binding, cached per Prelude identity and
 *       never per receiver value.
 *   <li>{@link #doRootDelegated}: a Boolean or null receiver, selected from
 *       the fixed root Object.
 * </ul>
 *
 * <p>A chain whose examined owners are all FROZEN caches its selected value;
 * a mutable chain checks every examined {@code Shape} and reads the owner's
 * current slot on every execution, so an in-place reassignment is observed
 * and a structural change (removal, recreation, shadowing) misses the entry.
 * A selected slot that does not hold a Closure takes the complete selection,
 * which raises the ordinary Error.
 *
 * <p>The complete ordinary selection ({@link #select}) is reached through
 * one general entry per receiver family, each delimited by the same
 * representation guard as its cached entries: Shape-backed ordinary objects
 * ({@link #doObjectGeneral}), SmallInteger ({@link #doLongGeneral}), double
 * ({@link #doDoubleGeneral}), represented Float
 * ({@link #doFloatValueGeneral}), String ({@link #doStringGeneral}),
 * Boolean and null ({@link #doRootDelegatedGeneral}), and every remaining
 * representation (map-backed objects, large Integer, Fraction, Complex,
 * instance-retained prototypes, foreign projections) through
 * {@link #doOtherGeneral}, whose guard is the complement of the preceding
 * families. No general entry replaces a cached entry, and none admits a
 * receiver of another family: a family that has used the complete
 * selection never prevents an unseen family from installing its own cached
 * entry. Within one family, once its general entry is active a receiver no
 * installed entry admits takes that general entry instead of installing a
 * further entry; the installed entries remain authoritative.
 */
@GenerateUncached
@GenerateInline(false)
@OperationProxy.Proxyable(allowUncached = true, storeBytecodeIndex = true)
@ConstantOperand(type = TruffleString.class, name = "selector")
@ConstantOperand(type = boolean.class, name = "direct")
abstract class ProtosExperimentalGetMethodNode extends Node {
    static final String SMALL_INTEGER_BINDING =
            ProtosBytecodeRootNode.StandardKeySelectionNode.SMALL_INTEGER_BINDING;
    static final String FLOAT_BINDING =
            ProtosBytecodeRootNode.StandardKeySelectionNode.FLOAT_BINDING;
    static final String STRING_BINDING =
            ProtosBytecodeRootNode.StandardKeySelectionNode.STRING_BINDING;

    abstract Object execute(
            VirtualFrame frame, TruffleString selector,
            boolean direct, Object receiver);

    /*
     * An exact ordinary receiver owning the selector. The receiver identity
     * is not cached: the Shape proves the local slot and its current value
     * is read from the actual receiver.
     */
    @Specialization(
            excludeForUncached = true,
            guards = {
                "selector == cachedSelector",
                "isOrdinaryObject(receiver)",
                "receiver.usesObjectStorageForRuntime()",
                "cachedShape.check(receiver)",
                "getter != null",
                "getter.accepts(receiver)"
            },
            limit = "3")
    @ForceQuickening
    static Object doOwn(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            ProtosObjectValue receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached("selector") TruffleString cachedSelector,
            @Cached("receiver.getShape()") Shape cachedShape,
            @Cached("ownGetter(cachedShape, cachedSelector)") PropertyGetter getter) {
        Object selected = getter.get(receiver);
        if (selected instanceof ProtosClosureValue closure) {
            // The receiver travels separately and is its own methodHome.
            return direct ? closure : new ProtosBoundMethod(closure, receiver, receiver);
        }
        return selectComplete(frame, cachedSelector, receiver, root);
    }

    /*
     * An exact ordinary receiver not owning the selector: the structural
     * selection from its direct parent is shared by every receiver with
     * this Shape and that exact parent, never by Shape alone.
     */
    @Specialization(
            excludeForUncached = true,
            guards = {
                "selector == cachedSelector",
                "isOrdinaryObject(receiver)",
                "receiver.usesObjectStorageForRuntime()",
                "receiverShape.check(receiver)",
                "selection != null",
                "hasSelectionParent(receiver, selection)",
                "frozen || selection.isValid()"
            },
            limit = "3")
    @ForceQuickening
    static Object doInherited(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            ProtosObjectValue receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached("selector") TruffleString cachedSelector,
            @Cached("receiver.getShape()") Shape receiverShape,
            @Cached("selectInherited(receiver, cachedSelector)") StructuralSelection selection,
            @Cached("allOwnersFrozen(selection)") boolean frozen,
            @Cached("frozenValue(frozen, selection)")
                    Object frozenSelected) {
        return bindSelected(
                frame, cachedSelector, receiver, root, selection,
                frozen ? frozenSelected : selection.read());
    }

    /*
     * A SmallInteger receiver: the selection from the prototype of the
     * observing Prelude's FROZEN SmallInteger binding, shared by every
     * value. An absent Prelude or a mutable binding installs nothing.
     */
    @Specialization(
            excludeForUncached = true,
            guards = {
                "selector == cachedSelector",
                "prelude == cachedPrelude",
                "cachedPrelude != null",
                "selection != null",
                "frozen || selection.isValid()"
            },
            limit = "3")
    static Object doLong(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            long receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Bind("preludeOf(frame, root)") ProtosPrelude prelude,
            @Cached("selector") TruffleString cachedSelector,
            @Cached("prelude") ProtosPrelude cachedPrelude,
            @Cached("selectFromBinding(cachedPrelude, SMALL_INTEGER_BINDING, cachedSelector)")
                    StructuralSelection selection,
            @Cached("allOwnersFrozen(selection)") boolean frozen,
            @Cached("frozenValue(frozen, selection)")
                    Object frozenSelected) {
        return bindSelected(
                frame, cachedSelector, receiver, root, selection,
                frozen ? frozenSelected : selection.read());
    }

    /* A Float receiver, as {@link #doLong} through the Float binding. */
    @Specialization(
            excludeForUncached = true,
            guards = {
                "selector == cachedSelector",
                "prelude == cachedPrelude",
                "cachedPrelude != null",
                "selection != null",
                "frozen || selection.isValid()"
            },
            limit = "3")
    static Object doDouble(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            double receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Bind("preludeOf(frame, root)") ProtosPrelude prelude,
            @Cached("selector") TruffleString cachedSelector,
            @Cached("prelude") ProtosPrelude cachedPrelude,
            @Cached("selectFromBinding(cachedPrelude, FLOAT_BINDING, cachedSelector)")
                    StructuralSelection selection,
            @Cached("allOwnersFrozen(selection)") boolean frozen,
            @Cached("frozenValue(frozen, selection)")
                    Object frozenSelected) {
        return bindSelected(
                frame, cachedSelector, receiver, root, selection,
                frozen ? frozenSelected : selection.read());
    }

    /*
     * A represented Float receiver: its final representation delegates to
     * the observing Prelude's Float prototype exactly as a double does.
     */
    @Specialization(
            excludeForUncached = true,
            guards = {
                "selector == cachedSelector",
                "prelude == cachedPrelude",
                "cachedPrelude != null",
                "selection != null",
                "frozen || selection.isValid()"
            },
            limit = "3")
    static Object doFloatValue(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            ProtosFloatValue receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Bind("preludeOf(frame, root)") ProtosPrelude prelude,
            @Cached("selector") TruffleString cachedSelector,
            @Cached("prelude") ProtosPrelude cachedPrelude,
            @Cached("selectFromBinding(cachedPrelude, FLOAT_BINDING, cachedSelector)")
                    StructuralSelection selection,
            @Cached("allOwnersFrozen(selection)") boolean frozen,
            @Cached("frozenValue(frozen, selection)")
                    Object frozenSelected) {
        return bindSelected(
                frame, cachedSelector, receiver, root, selection,
                frozen ? frozenSelected : selection.read());
    }

    /*
     * A String receiver, as {@link #doLong} through the String binding; the
     * actual String receiver is bound, never a cached one.
     */
    @Specialization(
            excludeForUncached = true,
            guards = {
                "selector == cachedSelector",
                "prelude == cachedPrelude",
                "cachedPrelude != null",
                "selection != null",
                "frozen || selection.isValid()"
            },
            limit = "3")
    static Object doString(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            ProtosStringValue receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Bind("preludeOf(frame, root)") ProtosPrelude prelude,
            @Cached("selector") TruffleString cachedSelector,
            @Cached("prelude") ProtosPrelude cachedPrelude,
            @Cached("selectFromBinding(cachedPrelude, STRING_BINDING, cachedSelector)")
                    StructuralSelection selection,
            @Cached("allOwnersFrozen(selection)") boolean frozen,
            @Cached("frozenValue(frozen, selection)")
                    Object frozenSelected) {
        return bindSelected(
                frame, cachedSelector, receiver, root, selection,
                frozen ? frozenSelected : selection.read());
    }

    /*
     * A Boolean or the canonical null: both delegate directly to the fixed
     * root Object, so no Prelude is read.
     */
    @Specialization(
            excludeForUncached = true,
            guards = {
                "isRootDelegated(receiver)",
                "selector == cachedSelector",
                "selection != null",
                "frozen || selection.isValid()"
            },
            limit = "3")
    static Object doRootDelegated(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            Object receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root,
            @Cached("selector") TruffleString cachedSelector,
            @Cached("selectFromRoot(cachedSelector)") StructuralSelection selection,
            @Cached("allOwnersFrozen(selection)") boolean frozen,
            @Cached("frozenValue(frozen, selection)")
                    Object frozenSelected) {
        return bindSelected(
                frame, cachedSelector, receiver, root, selection,
                frozen ? frozenSelected : selection.read());
    }

    /*
     * The general entries. Each admits exactly one receiver family, so the
     * complete selection used by one family never handles a receiver of
     * another family before that family has specialized. None replaces a
     * cached entry.
     */

    /* A Shape-backed exact ordinary receiver no cached entry admits. */
    @Specialization(
            guards = {"isOrdinaryObject(receiver)", "receiver.usesObjectStorageForRuntime()"})
    @InliningCutoff
    static Object doObjectGeneral(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            ProtosObjectValue receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return selectComplete(frame, selector, receiver, root);
    }

    /* A SmallInteger receiver no cached entry admits. */
    @Specialization
    @InliningCutoff
    static Object doLongGeneral(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            long receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return selectComplete(frame, selector, receiver, root);
    }

    /* A double receiver no cached entry admits. */
    @Specialization
    @InliningCutoff
    static Object doDoubleGeneral(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            double receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return selectComplete(frame, selector, receiver, root);
    }

    /* A represented Float receiver no cached entry admits. */
    @Specialization
    @InliningCutoff
    static Object doFloatValueGeneral(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            ProtosFloatValue receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return selectComplete(frame, selector, receiver, root);
    }

    /* A String receiver no cached entry admits. */
    @Specialization
    @InliningCutoff
    static Object doStringGeneral(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            ProtosStringValue receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return selectComplete(frame, selector, receiver, root);
    }

    /* A Boolean or null receiver no cached entry admits. */
    @Specialization(guards = "isRootDelegated(receiver)")
    @InliningCutoff
    static Object doRootDelegatedGeneral(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            Object receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return selectComplete(frame, selector, receiver, root);
    }

    /*
     * Every receiver outside the preceding families, normalized to its guest
     * value before the complete selection.
     */
    @Specialization(guards = "isOtherRepresentation(receiver)")
    @InliningCutoff
    static Object doOtherGeneral(
            VirtualFrame frame,
            TruffleString selector,
            boolean direct,
            Object receiver,
            @Bind("rootOf($node)") ProtosBytecodeRootNode root) {
        return selectComplete(
                frame, selector, ProtosNumericValueSupport.guestValue(receiver), root);
    }

    /*
     * The inherited selection bound to the actual receiver and the real
     * owner. A selected value that is not a Closure takes the complete
     * selection, which raises its ordinary Error.
     */
    private static Object bindSelected(
            VirtualFrame frame,
            TruffleString selector,
            Object receiver,
            ProtosBytecodeRootNode root,
            StructuralSelection selection,
            Object selected) {
        if (selected instanceof ProtosClosureValue closure) {
            return new ProtosBoundMethod(closure, receiver, selection.home());
        }
        return selectComplete(frame, selector, receiver, root);
    }

    /* The complete ordinary selection with the actual caller arguments. */
    private static Object selectComplete(
            VirtualFrame frame,
            TruffleString selector,
            Object receiver,
            ProtosBytecodeRootNode root) {
        return select(receiver, selector.toJavaStringUncached(), root, frame.getArguments());
    }

    static boolean isOrdinaryObject(Object receiver) {
        return ProtosValueLookup.isOrdinaryObject(receiver);
    }

    static boolean isRootDelegated(Object receiver) {
        return ProtosBytecodeRootNode.StandardKeySelectionNode.isRootDelegated(receiver);
    }

    /*
     * Classifies only which general entry owns the receiver: true for every
     * receiver outside the Shape-backed ordinary, SmallInteger, Float,
     * String and root-delegated families. It decides no delegation.
     */
    static boolean isOtherRepresentation(Object receiver) {
        if (receiver instanceof Long
                || receiver instanceof Double
                || receiver instanceof ProtosFloatValue
                || receiver instanceof ProtosStringValue
                || isRootDelegated(receiver)) {
            return false;
        }
        return !(isOrdinaryObject(receiver)
                && ((ProtosObjectValue) receiver).usesObjectStorageForRuntime());
    }

    static boolean hasSelectionParent(
            ProtosObjectValue receiver, StructuralSelection selection) {
        return ProtosValueLookup.hasSelectionParent(receiver, selection);
    }

    /*
     * The observing caller's Prelude, read through the admitted caller
     * authority exactly as the complete selection reads it, so an
     * untransported inherited Prelude registers the root's caller need and
     * nothing is published. Read only by the Prelude-delegated entries.
     */
    @NonIdempotent
    static ProtosPrelude preludeOf(VirtualFrame frame, ProtosBytecodeRootNode root) {
        return ProtosFunctionCallDispatchers.admittedCallerPrelude(root, frame.getArguments());
    }

    /* Specialization time only. */
    static boolean allOwnersFrozen(StructuralSelection selection) {
        return selection != null && selection.allOwnersFrozen();
    }

    /* Specialization time only: the value of an all-FROZEN chain, never of a mutable one. */
    static Object frozenValue(boolean frozen, StructuralSelection selection) {
        return frozen && selection.isValid() ? selection.read() : null;
    }

    /* Specialization time only: the getter of a local selector, or null. */
    @TruffleBoundary
    static PropertyGetter ownGetter(Shape shape, TruffleString selector) {
        return ProtosValueLookup.propertyGetterWithFinalAssumption(shape, selectorName(selector));
    }

    /* Specialization time only: null when the receiver owns the selector. */
    @TruffleBoundary
    static StructuralSelection selectInherited(Object receiver, TruffleString selector) {
        return ProtosValueLookup.selectInheritedStructural(receiver, selectorName(selector));
    }

    /* Specialization time only: null without a Prelude or a FROZEN binding. */
    @TruffleBoundary
    static StructuralSelection selectFromBinding(
            ProtosPrelude prelude, String binding, TruffleString selector) {
        return ProtosBytecodeRootNode.StandardKeySelectionNode.selectFromBinding(
                prelude, binding, selectorName(selector));
    }

    /* Specialization time only. */
    @TruffleBoundary
    static StructuralSelection selectFromRoot(TruffleString selector) {
        return ProtosBytecodeRootNode.StandardKeySelectionNode.selectFromRoot(
                selectorName(selector));
    }

    @TruffleBoundary
    private static String selectorName(TruffleString selector) {
        return selector.toJavaStringUncached().intern();
    }

    /* The structural selection from an exact ordinary receiver, or null. */
    @TruffleBoundary
    static StructuralSelection selectOrNull(Object receiver, TruffleString selector) {
        if (!ProtosValueLookup.isOrdinaryObject(receiver)) {
            return null;
        }
        return ProtosValueLookup.selectStructural(receiver, selectorName(selector));
    }

    /**
     * The complete D013 send selection: a missing slot is SlotNotFound, an
     * unsupported representation or a non-Closure selection is Error, a D188
     * foreign member is its read value.
     */
    @TruffleBoundary
    static Object select(
            Object receiver, String selector, ProtosBytecodeRootNode root, Object caller) {
        ProtosPrelude prelude = ProtosFunctionCallDispatchers.admittedCallerPrelude(root, caller);
        ProtosSlotLookupResult selected;
        try {
            var found = ProtosValueLookup.lookup(receiver, selector, prelude);
            if (found.isEmpty()) {
                throw new ProtosSignalException(
                        ProtosCoreErrors.newSlotNotFound(exactCaller(root, caller)));
            }
            selected = found.orElseThrow();
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(exactCaller(root, caller)));
        }
        return bind(receiver, selected, root, caller);
    }

    /**
     * The super-send selection: the lookup
     * starts at the parent of the caller's methodHome and the selected method
     * is bound to the caller's receiver.
     */
    @TruffleBoundary
    static Object selectSuper(String selector, ProtosBytecodeRootNode root, Object caller) {
        ProtosActivation activation = exactCaller(root, caller);
        ProtosObjectValue methodHome =
                activation.methodHome()
                        .orElseThrow(
                                () -> new ProtosSignalException(
                                        ProtosCoreErrors.newInvalidSuper(activation)));
        Object lookupOrigin =
                methodHome.parent()
                        .orElseThrow(
                                () -> new ProtosSignalException(
                                        ProtosCoreErrors.newSlotNotFound(activation)));
        ProtosSlotLookupResult selected;
        try {
            selected =
                    ProtosValueLookup.lookup(lookupOrigin, selector, activation.preludeOrNullForRuntime())
                            .orElseThrow(
                                    () -> new ProtosSignalException(
                                            ProtosCoreErrors.newSlotNotFound(activation)));
        } catch (UnsupportedOperationException unsupportedRepresentation) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
        }
        return new ProtosBoundMethod(closure, activation.receiver(), selected.home());
    }

    private static Object bind(
            Object receiver,
            ProtosSlotLookupResult selected,
            ProtosBytecodeRootNode root,
            Object caller) {
        if (ProtosForeignProjectedOperations.isForeignMemberSelection(selected)) {
            // D188: the read foreign member is invoked as an ordinary callable.
            return selected.value();
        }
        if (!(selected.value() instanceof ProtosClosureValue closure)) {
            throw new ProtosSignalException(ProtosCoreErrors.newError(exactCaller(root, caller)));
        }
        return new ProtosBoundMethod(closure, receiver, selected.home());
    }

    private static ProtosActivation exactCaller(ProtosBytecodeRootNode root, Object caller) {
        return ProtosFunctionCallDispatchers.exactCaller(root, caller);
    }

    /*
     * The Protos root executing this operation. In a proxied
     * node the default root bind is typed Node, so it is resolved from the
     * enclosing BytecodeNode.
     */
    static ProtosBytecodeRootNode rootOf(Node node) {
        return (ProtosBytecodeRootNode) BytecodeNode.get(node).getBytecodeRootNode();
    }
}
