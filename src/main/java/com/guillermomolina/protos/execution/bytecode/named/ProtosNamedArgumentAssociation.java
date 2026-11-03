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
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import java.util.List;
import java.util.Objects;

/**
 * I143 / D237-MIX-C: association of already evaluated positional and
 * named values with the selected Closure's declared parameters.
 *
 * <p>This helper performs no guest evaluation, creates no parameter
 * slots, and executes no defaults. Its caller must select the actual
 * callable first and translate a null result into a fresh Protos Error
 * before parameter binding.
 *
 * <p>Only named-call sites need this representation. Positional-only
 * calls retain their existing PLAT060-B5 binding path.
 */
final class ProtosNamedArgumentAssociation {
    private ProtosNamedArgumentAssociation() {}

    /**
     * A label and its already evaluated guest value.
     *
     * <p>The guest's canonical null is an ordinary non-null value.
     */
    record NamedValue(String name, Object value) {
        NamedValue {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            if (name.isEmpty()) {
                throw new IllegalArgumentException(
                        "empty named argument label");
            }
        }
    }

    /**
     * Association established before any formal default executes.
     *
     * <p>The presence vector is independent from the values. Therefore
     * absence never aliases an explicitly supplied canonical null.
     */
    static final class Association {
        private final Object[] values;
        private final boolean[] present;
        private final List<?> restValues;
        private final boolean hasRest;

        private Association(
                Object[] values,
                boolean[] present,
                List<?> restValues,
                boolean hasRest) {
            this.values = values.clone();
            this.present = present.clone();
            this.restValues = List.copyOf(restValues);
            this.hasRest = hasRest;
        }

        int ordinaryParameterCount() {
            return values.length;
        }

        boolean isPresent(int index) {
            return present[index];
        }

        Object valueAt(int index) {
            if (!present[index]) {
                throw new IllegalStateException(
                        "unsupplied parameter has no bound value");
            }
            return values[index];
        }

        boolean hasRest() {
            return hasRest;
        }

        List<?> restValues() {
            return restValues;
        }

        /**
         * I143: the presence of each declared non-rest parameter.
         * The returned array cannot mutate this association.
         */
        boolean[] parameterPresenceForRuntime() {
            return present.clone();
        }

        /**
         * The named-only invocation representation: ordinary formal
         * positions followed by the positional rest suffix.
         *
         * <p>Missing positions contain canonical null as a transport
         * value, but remain absent according to parameterPresenceForRuntime.
         * Thus a supplied canonical null suppresses a default while a
         * missing position does not.
         *
         * <p>Extra positionals without rest were already discarded by
         * association, as permitted by D203. All their source effects
         * have already occurred before this method is called.
         */
        List<Object> boundSuppliedArgumentsForRuntime() {
            java.util.ArrayList<Object> supplied =
                    new java.util.ArrayList<>(
                            values.length + restValues.size());

            for (int index = 0; index < values.length; index++) {
                supplied.add(
                        present[index]
                                ? values[index]
                                : ProtosNullValue.INSTANCE);
            }

            for (Object restValue : restValues) {
                supplied.add(java.util.Objects.requireNonNull(
                        restValue, "rest positional value"));
            }

            return List.copyOf(supplied);
        }
    }

    /**
     * Returns null when a label is unknown, refers to rest, or attempts
     * to assign an already supplied parameter.
     *
     * <p>Callers must raise a fresh guest Error at the actual invocation
     * boundary. No Java exception from this validation becomes a guest
     * error by accident.
     *
     * <p>Positional values are already expanded, including all spreads.
     * Their ordinals never shift to fill holes left by named arguments.
     */
    static Association associate(
            List<CanonicalParameter> parameters,
            List<?> positional,
            List<NamedValue> named) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(positional, "positional");
        Objects.requireNonNull(named, "named");

        int ordinaryCount = parameters.size();
        boolean hasRest = false;

        for (int index = 0; index < parameters.size(); index++) {
            if (parameters.get(index).rest()) {
                if (index != parameters.size() - 1) {
                    throw new IllegalArgumentException(
                            "rest must be the final formal parameter");
                }
                ordinaryCount = index;
                hasRest = true;
            }
        }

        Object[] values = new Object[ordinaryCount];
        boolean[] present = new boolean[ordinaryCount];

        int suppliedOrdinary = Math.min(
                positional.size(), ordinaryCount);

        for (int index = 0; index < suppliedOrdinary; index++) {
            values[index] = Objects.requireNonNull(
                    positional.get(index), "positional argument");
            present[index] = true;
        }

        for (NamedValue item : named) {
            Objects.requireNonNull(item, "named argument");

            int matched = -1;

            for (int index = 0; index < ordinaryCount; index++) {
                if (parameters.get(index).name().equals(item.name())) {
                    matched = index;
                    break;
                }
            }

            if (matched < 0 || present[matched]) {
                return null;
            }

            values[matched] = item.value();
            present[matched] = true;
        }

        List<?> restValues = hasRest
                ? List.copyOf(positional.subList(
                        Math.min(ordinaryCount, positional.size()),
                        positional.size()))
                : List.of();

        return new Association(
                values, present, restValues, hasRest);
    }

    /**
     * I143 / D237: classification of an already selected executable
     * implementation. Selection itself belongs to the ordinary D013
     * lookup path, not to this helper.
     *
     * <p>A null classification means invalid name association or a
     * native implementation without a declared named signature.
     * The future caller must signal a fresh generic guest Error.
     */
    sealed interface SelectedBinding
            permits SourceBinding, StandardForward {}

    /**
     * Association for the exact selected source Closure.
     * The invocation will use this Closure, not a statically guessed
     * receiver or an earlier call candidate.
     */
    record SourceBinding(
            ProtosClosureValue selected,
            Association association)
            implements SelectedBinding {
        SourceBinding {
            Objects.requireNonNull(selected, "selected");
            Objects.requireNonNull(association, "association");
        }
    }

    /**
     * The actual standard Object.call implementation owns transparent
     * forwarding, including copied, inherited or extracted instances.
     * No binding against a native wrapper signature is attempted.
     */
    record StandardForward(
            List<?> positional,
            List<NamedValue> named)
            implements SelectedBinding {
        StandardForward {
            positional = List.copyOf(
                    Objects.requireNonNull(positional, "positional"));
            named = List.copyOf(
                    Objects.requireNonNull(named, "named"));
        }
    }

    /**
     * Consumes the evaluated vector only after ordinary selection.
     * The positional-only route never enters this operation.
     */
    static SelectedBinding classifySelectedImplementation(
            ProtosClosureValue selected,
            ProtosPreparedCalls.PreparedArgumentVector supplied) {
        Objects.requireNonNull(supplied, "supplied");
        if (!supplied.hasNamedArguments()) {
            throw new IllegalArgumentException(
                    "named classification requires a named call site");
        }
        return classifySelectedImplementation(
                selected,
                supplied.positionalSnapshot(),
                supplied.namedSnapshot());
    }

    /**
     * The selected executable implementation, not selector spelling or
     * methodHome, determines whether standard forwarding is permitted.
     *
     * <p>All native behaviors other than the actual standard Object.call
     * implementation remain positional-only until an explicit semantic
     * named signature is introduced.
     */
    static SelectedBinding classifySelectedImplementation(
            ProtosClosureValue selected,
            List<?> positional,
            List<NamedValue> named) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(positional, "positional");
        Objects.requireNonNull(named, "named");

        if (named.isEmpty()) {
            throw new IllegalArgumentException(
                    "named classification requires at least one label");
        }

        if (ProtosStandardObjectProtocol
                .isStandardCallImplementation(selected)) {
            return new StandardForward(positional, named);
        }

        if (selected.nativeBody().isPresent()) {
            return null;
        }

        CanonicalClosure definition = selected.definition();
        if (definition == null) {
            return null;
        }

        Association result = associate(
                definition.parameters(), positional, named);

        return result == null
                ? null
                : new SourceBinding(selected, result);
    }
}
