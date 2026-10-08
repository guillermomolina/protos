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

import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
 * The member-name array reuses ProtosDebuggerScopeMemberNames: it is a plain read-only interop
 * array of Strings with no debugger-specific behavior.
 */

/**
 * PLAT054 host binding scope of one Polyglot Context ({@code Context.getBindings("protos")}).
 *
 * <p>One stable view per Context. Each message re-reads the current selection, so the view
 * follows later normal completions without being replaced. It exposes exclusively the own local
 * slots of the selected {@code moduleContext}: no prelude, delegated slot, export registry, or
 * other module. It exports no write or removal message, so it is read-only from the host while
 * Protos code keeps mutating those slots normally. Querying it never creates the Process, and after
 * the Process stops running it exposes no members.
 *
 * <p>This is interop machinery, not a Protos value; guest code never observes it.
 */
@ExportLibrary(InteropLibrary.class)
final class ProtosHostBindingsScope implements TruffleObject {
    private final ProtosLanguageContext owner;

    ProtosHostBindingsScope(ProtosLanguageContext owner) {
        this.owner = owner;
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return ProtosLanguage.class;
    }

    @ExportMessage
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "protos bindings";
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        ProtosEmbeddedProcess process = owner.embeddedProcessOrNull();
        if (process == null || !process.isLive()) {
            return new ProtosDebuggerScopeMemberNames(List.of());
        }
        Thread token = process.enterRootActor();
        try {
            Optional<ProtosObjectValue> selected = process.selectedModuleContext();
            if (selected.isEmpty()) {
                return new ProtosDebuggerScopeMemberNames(List.of());
            }
            ArrayList<String> names = new ArrayList<>();
            selected.get()
                    .localSlotsSnapshot()
                    .forEach(
                            (name, value) -> {
                                if (InteropLibrary.isValidValue(value)) {
                                    names.add(name);
                                }
                            });
            return new ProtosDebuggerScopeMemberNames(names);
        } finally {
            process.exitRootActor(token);
        }
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        ProtosEmbeddedProcess process = owner.embeddedProcessOrNull();
        if (process == null || !process.isLive()) {
            return false;
        }
        Thread token = process.enterRootActor();
        try {
            return process.selectedModuleContext()
                    .flatMap(selected -> selected.readLocalSlot(member))
                    .filter(InteropLibrary::isValidValue)
                    .isPresent();
        } finally {
            process.exitRootActor(token);
        }
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException, UnsupportedMessageException {
        ProtosEmbeddedProcess process = owner.embeddedProcessOrNull();
        if (process == null || !process.isLive()) {
            throw UnknownIdentifierException.create(member);
        }
        Thread token = process.enterRootActor();
        try {
            ProtosObjectValue selected =
                    process.selectedModuleContext()
                            .orElseThrow(() -> UnknownIdentifierException.create(member));
            Object value =
                    selected.readLocalSlot(member)
                            .filter(InteropLibrary::isValidValue)
                            .orElseThrow(() -> UnknownIdentifierException.create(member));
            return process.readSelectedMember(selected, value);
        } finally {
            process.exitRootActor(token);
        }
    }
}
