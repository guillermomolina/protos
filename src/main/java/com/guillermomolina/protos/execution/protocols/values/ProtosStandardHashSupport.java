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
import com.guillermomolina.protos.runtime.ProtosIdentity;
import com.guillermomolina.protos.runtime.ProtosNumericValueSupport;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;

/**
 * The standard {@code hash} behaviors of root Object, Number and String.
 *
 * <p>Each installed native body has a stable identity, so a specialized
 * caller can prove that the behavior actually selected for {@code hash} is
 * exactly one of them at its standard home, and then compute the same result
 * through the shared helpers below without invoking it. A copied or rebound
 * behavior, or one selected outside its home or receiver domain, is never
 * admitted and keeps its ordinary invocation.
 */
public final class ProtosStandardHashSupport {
    static final String SELECTOR = "hash";

    private static final ProtosNativeClosureBody OBJECT_HASH_BODY =
            (activation, supplied) -> {
                if (!supplied.isEmpty()) {
                    throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                }
                return objectHash(activation.receiver());
            };

    private static final ProtosNativeClosureBody NUMBER_HASH_BODY =
            (activation, supplied) -> {
                Object receiver = activation.receiver();
                if (!supplied.isEmpty()
                        || !ProtosNumericValueSupport.isCurrentNumber(receiver)) {
                    throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                }
                return ProtosCurrentNumericRelations.normalHash(
                        receiver, activation.prelude().orElse(null));
            };

    private static final ProtosNativeClosureBody STRING_HASH_BODY =
            (activation, supplied) -> {
                if (!supplied.isEmpty()
                        || !(activation.receiver() instanceof ProtosStringValue string)) {
                    throw new ProtosSignalException(ProtosCoreErrors.newError(activation));
                }
                return stringHash(string);
            };

    private ProtosStandardHashSupport() {}

    public static void installObjectHash() {
        ProtosObjectValue root = ProtosObjectValue.rootObject();
        if (!root.hasLocalSlot(SELECTOR)) {
            root.createLocalSlot(SELECTOR, ProtosClosureValue.nativeClosure(OBJECT_HASH_BODY));
        }
    }

    public static void installNumberHash(ProtosObjectValue numberPrototype) {
        if (!numberPrototype.hasLocalSlot(SELECTOR)) {
            numberPrototype.createLocalSlot(
                    SELECTOR, ProtosClosureValue.nativeClosure(NUMBER_HASH_BODY));
        }
    }

    public static void installStringHash(ProtosObjectValue stringPrototype) {
        if (!stringPrototype.hasLocalSlot(SELECTOR)) {
            stringPrototype.createLocalSlot(
                    SELECTOR, ProtosClosureValue.nativeClosure(STRING_HASH_BODY));
        }
    }

    /** The standard Object hash: the semantic identity hash of the receiver. */
    static Object objectHash(Object receiver) {
        return ProtosNumericValueSupport.integer(ProtosIdentity.identityHash(receiver));
    }

    /** The standard String hash. */
    static Object stringHash(ProtosStringValue string) {
        return ProtosNumericValueSupport.integer(string.value().hashCode());
    }

    /**
     * Whether {@code behavior}, selected for {@code hash} on {@code receiver}
     * at {@code home} under {@code prelude}, is exactly the installed
     * standard hash whose result the shared helpers compute for that
     * receiver's domain: the Number hash at the Prelude's Number prototype
     * for a Number receiver, the String hash at the Prelude's String
     * prototype for a String receiver, and the Object hash at root Object for
     * any other receiver. Observes identities only and invokes nothing;
     * callers must separately protect the selection against mutation.
     */
    static boolean isCanonicalStandardHashSelection(
            Object receiver, Object behavior, ProtosObjectValue home, ProtosPrelude prelude) {
        if (prelude == null
                || !(behavior instanceof ProtosClosureValue closure)) {
            return false;
        }
        ProtosNativeClosureBody body = closure.nativeBodyOrNullForRuntime();
        if (body == null) {
            return false;
        }
        if (ProtosNumericValueSupport.isCurrentNumber(receiver)) {
            return body == NUMBER_HASH_BODY && home == prelude.numberPrototype();
        }
        if (receiver instanceof ProtosStringValue) {
            return body == STRING_HASH_BODY && home == prelude.stringPrototype();
        }
        return body == OBJECT_HASH_BODY && home == ProtosObjectValue.rootObject();
    }
}
