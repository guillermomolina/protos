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
import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosSealedValue;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.util.List;

/**
 * Private runtime sealing facilities for Standard Library semantic-value families whose public
 * contract requires safe exact-family recognition and opaque state (LIB020-A).
 *
 * <p>Every policy of a family stays in its Protos module. A facility owns only what Protos source
 * cannot express: minting values that guest code cannot forge, recognizing them from their host
 * representation without invoking any behavior of a candidate, and keeping their semantic state
 * out of Protos-visible slots. Each facility is its own family. Following the D101/D187 pattern,
 * each frozen facility is provisioned as a standard initial member of one exact module, which
 * captures it lexically and removes the bootstrap slot during initialization, so it never belongs
 * to the published module surface. The renderer module std:text/ANSI (LIB020-B) receives the same
 * two facility instances under the same bootstrap slots, so it reads run and foreground state of
 * exactly the families those modules mint; it likewise captures and removes them.
 *
 * <ul>
 *   <li>{@code seal(template, state)} answers a fresh FROZEN value of this family with the parent
 *       and local slots of the OPEN ordinary {@code template}; {@code state} must be a String or a
 *       frozen Array and is kept only by reference.
 *   <li>{@code recognizes(value)} answers whether {@code value} was minted by this facility.
 *   <li>{@code state(value)} answers the private state of a value of this family; any other
 *       argument signals an Error.
 * </ul>
 */
public final class ProtosSealedFamilyFacility {
    public static final ProtosModuleKey STYLE_MODULE_KEY = new ProtosModuleKey("std:text/Style");
    public static final String STYLE_BOOTSTRAP_SLOT = "_styleFacility";
    public static final ProtosModuleKey STYLED_TEXT_MODULE_KEY =
            new ProtosModuleKey("std:text/StyledText");
    public static final String STYLED_TEXT_BOOTSTRAP_SLOT = "_styledTextFacility";
    public static final ProtosModuleKey ANSI_MODULE_KEY = new ProtosModuleKey("std:text/ANSI");

    private ProtosSealedFamilyFacility() {}

    /** Creates a frozen facility that is the sole minting authority of a new family. */
    public static ProtosObjectValue createFacility() {
        ProtosObjectValue facility = new ProtosObjectValue(ProtosObjectValue.rootObject());
        Object family = facility;
        facility.createLocalSlot(
                "seal",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> seal(family, activation, supplied)));
        facility.createLocalSlot(
                "recognizes",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> recognizes(family, activation, supplied)));
        facility.createLocalSlot(
                "state",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> state(family, activation, supplied)));
        return facility.freeze();
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object seal(Object family, ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 2
                || supplied.get(0) == null
                || supplied.get(0).getClass() != ProtosObjectValue.class
                || !((ProtosObjectValue) supplied.get(0)).isOpen()
                || !inertState(supplied.get(1))) {
            throw invalid(activation);
        }
        return ProtosSealedValue.seal(family, (ProtosObjectValue) supplied.get(0), supplied.get(1));
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object recognizes(Object family, ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1) {
            throw invalid(activation);
        }
        return ProtosBooleanValue.of(member(family, supplied.get(0)));
    }

    @com.oracle.truffle.api.CompilerDirectives.TruffleBoundary
    private static Object state(Object family, ProtosActivation activation, List<?> supplied) {
        if (supplied.size() != 1 || !member(family, supplied.get(0))) {
            throw invalid(activation);
        }
        return ((ProtosSealedValue) supplied.get(0)).stateFor(family);
    }

    /** Reads host representation only; no slot or behavior of the candidate is consulted. */
    private static boolean member(Object family, Object candidate) {
        return candidate instanceof ProtosSealedValue value && value.belongsTo(family);
    }

    private static boolean inertState(Object state) {
        return state instanceof ProtosStringValue
                || (state instanceof ProtosArrayValue array && array.isFrozen());
    }

    private static ProtosSignalException invalid(ProtosActivation activation) {
        return new ProtosSignalException(ProtosCoreErrors.newError(activation));
    }
}
