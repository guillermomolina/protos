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
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.modules.ProtosModuleKey;
import com.guillermomolina.protos.runtime.lookup.ProtosSlotLookupResult;
import com.guillermomolina.protos.runtime.ProtosValueLookup;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Suspension-scoped Truffle view of one exact Protos activation.
 *
 * <p>This object is tooling machinery, not a Protos guest value. It owns no
 * variable store and performs no guest execution while enumerating names.
 */
@ExportLibrary(InteropLibrary.class)
final class ProtosDebuggerScope implements TruffleObject {
    private final ProtosActivation activation;
    private final Map<String, Object> inlineCallbackBindings;

    ProtosDebuggerScope(ProtosActivation activation) {
        this(activation, Map.of());
    }

    /** Tooling-only virtual callback scope; no guest activation is constructed. */
    ProtosDebuggerScope(
            ProtosActivation activation,
            Map<String, Object> inlineCallbackBindings) {
        this.activation = Objects.requireNonNull(activation, "activation");
        this.inlineCallbackBindings = java.util.Collections.unmodifiableMap(
                new java.util.LinkedHashMap<>(Objects.requireNonNull(
                        inlineCallbackBindings, "inlineCallbackBindings")));
    }

    @ExportMessage
    boolean isScope() {
        return true;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean hasLanguageId() {
        return true;
    }

    @ExportMessage
    String getLanguageId() {
        return ProtosLanguage.ID;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new ProtosDebuggerScopeMemberNames(visibleNamesSnapshot());
    }

    @ExportMessage
    @TruffleBoundary
    boolean isMemberReadable(String member) {
        Objects.requireNonNull(member, "member");
        Optional<Object> raw = rawVisibleValue(member);
        return raw.isPresent() && InteropLibrary.isValidValue(raw.orElseThrow());
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnknownIdentifierException {
        Objects.requireNonNull(member, "member");
        final Optional<Object> value;
        try {
            value = rawVisibleValue(member);
        } catch (UnsupportedOperationException failure) {
            throw UnknownIdentifierException.create(member);
        }
        if (value.isEmpty()
                || !InteropLibrary.isValidValue(
                        value.orElseThrow())) {
            throw UnknownIdentifierException.create(member);
        }
        return value.orElseThrow();
    }

    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "scope";
    }

    private ProtosObjectValue sourceModuleContextOrNull() {
        ProtosModuleKey origin =
                activation.currentModuleKey().orElse(null);
        if (origin == null || !origin.isSource()) {
            return null;
        }
        var record =
                activation.actorModuleState().lookup(origin).orElse(null);
        return record == null ? null : record.instance();
    }

    private List<String> lexicalNamesSnapshot() {
        ArrayList<String> names = new ArrayList<>();
        names.addAll(inlineCallbackBindings.keySet());

        ProtosObjectValue context = activation.materializedContextOrNullForRuntime();
        if (context != null) {
            appendLocalNames(context, names);
        } else if (activation.publishedClosureLexicalAuthorityForRuntime() != null) {
            activation.publishedClosureLexicalAuthorityForRuntime().appendBindingNamesTo(names);
        }

        ProtosDebuggerScope lexicalCreatorScope = lexicalCreatorScope();
        if (lexicalCreatorScope != null) {
            names.addAll(lexicalCreatorScope.lexicalNamesSnapshot());
            return List.copyOf(names);
        }

        List<String> capturedNames = capturedCellNames();
        var cells = activation.capturedCellsForRuntime();
        for (int index = 0; index < capturedNames.size(); index++) {
            if (cells[index].isPresentForRuntime()) {
                names.add(capturedNames.get(index));
            }
        }

        for (ProtosObjectValue lexical :
                activation.capturedLexicalContexts()) {
            appendLocalNames(lexical, names);
        }
        return List.copyOf(names);
    }

    private List<String> visibleNamesSnapshot() {
        ArrayList<String> names = new ArrayList<>(lexicalNamesSnapshot());
        ProtosObjectValue sourceContext = sourceModuleContextOrNull();
        if (sourceContext != null) {
            ArrayList<String> moduleNames = new ArrayList<>();
            sourceContext.appendLocalBindingNamesTo(moduleNames);
            for (String name : moduleNames) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        appendReceiverDelegationNames(names);
        return List.copyOf(names);
    }

    private static void appendLocalNames(
            ProtosObjectValue object,
            List<String> names) {
        ArrayList<String> localNames = new ArrayList<>();
        object.appendLocalBindingNamesTo(localNames);
        names.addAll(localNames);
    }

    private void appendReceiverDelegationNames(List<String> names) {
        Object current = activation.receiver();
        ProtosPrelude prelude = activation.prelude().orElse(null);
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();

        while (visited.put(current, Boolean.TRUE) == null) {
            if (current instanceof ProtosObjectValue ordinary) {
                appendLocalNames(ordinary, names);
            }

            final Optional<Object> parent;
            try {
                parent = ProtosValueLookup.delegationParent(current, prelude);
            } catch (UnsupportedOperationException failure) {
                return;
            }
            if (parent.isEmpty()) {
                return;
            }
            current = parent.orElseThrow();
        }
    }

    /** Tooling reads the shared cells live; no captured value is copied. */
    private List<String> capturedCellNames() {
        var closure = activation.invokedClosureOrNullForRuntime();
        if (closure == null || activation.capturedCellsForRuntime().length == 0) {
            return List.of();
        }
        var plan = closure.preparedExecutionPlanOrNullForRuntime();
        return plan == null ? List.of() : plan.capturedCellNamesForTooling();
    }

    /** Resolve only for this observation; Scope retains no creator or frame registry. */
    private ProtosDebuggerScope lexicalCreatorScope() {
        ProtosActivation creator =
                ProtosBytecodeTagTreeNodeExports.lexicalCreatorForTooling(activation);
        return creator == null ? null : new ProtosDebuggerScope(creator);
    }

    private Optional<Object> rawLexicalValue(String member) {
        if (inlineCallbackBindings.containsKey(member)) {
            return Optional.of(inlineCallbackBindings.get(member));
        }
        Optional<Object> local =
                activation.readCurrentLocalSlotForRuntime(member);
        if (local.isPresent()) {
            return local;
        }

        ProtosDebuggerScope lexicalCreatorScope = lexicalCreatorScope();
        if (lexicalCreatorScope != null) {
            return lexicalCreatorScope.rawLexicalValue(member);
        }

        List<String> capturedNames = capturedCellNames();
        var cells = activation.capturedCellsForRuntime();
        for (int index = 0; index < capturedNames.size(); index++) {
            if (capturedNames.get(index).equals(member)) {
                Object value = cells[index].valueOrNullForRuntime();
                if (value != null) {
                    return Optional.of(value);
                }
            }
        }

        for (ProtosObjectValue lexical :
                activation.capturedLexicalContexts()) {
            Optional<Object> captured =
                    lexical.readLocalSlot(member);
            if (captured.isPresent()) {
                return captured;
            }
        }

        return Optional.empty();
    }

    private Optional<Object> rawVisibleValue(String member) {
        Optional<Object> lexical = rawLexicalValue(member);
        if (lexical.isPresent()) {
            return lexical;
        }

        ProtosObjectValue sourceContext = sourceModuleContextOrNull();
        if (sourceContext != null) {
            Optional<Object> sourceValue =
                    sourceContext.readLocalSlot(member);
            if (sourceValue.isPresent()) {
                return sourceValue;
            }
        }

        try {
            return ProtosValueLookup.lookup(
                            activation.receiver(),
                            member,
                            activation.prelude().orElse(null))
                    .map(ProtosSlotLookupResult::value);
        } catch (UnsupportedOperationException failure) {
            return Optional.empty();
        }
    }
}
