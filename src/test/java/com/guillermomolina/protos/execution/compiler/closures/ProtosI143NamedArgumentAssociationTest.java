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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.Association;
import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.NamedValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.semantic.ast.CanonicalParameter;
import com.guillermomolina.protos.source.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** I143: pure dynamic name association before guest activation binding. */
class ProtosI143NamedArgumentAssociationTest {

    @Test
    void namedArgumentsDoNotShiftPositionalOrdinals() {
        Association result = associate(
                formals("a", "b", "c"),
                List.of(1),
                List.of(named("c", 99)));

        assertNotNull(result);
        assertEquals(1, result.valueAt(0));
        assertFalse(result.isPresent(1));
        assertEquals(99, result.valueAt(2));
        assertThrows(
                IllegalStateException.class,
                () -> result.valueAt(1));
    }

    @Test
    void namesCanAppearBeforeOrAfterPositionals() {
        Association result = associate(
                formals("a", "b", "c"),
                List.of(1, 2),
                List.of(named("c", 99)));

        assertNotNull(result);
        assertEquals(1, result.valueAt(0));
        assertEquals(2, result.valueAt(1));
        assertEquals(99, result.valueAt(2));
    }

    @Test
    void positionalAndNamedAssignmentToSameFormalCollide() {
        assertNull(associate(
                formals("a", "b"),
                List.of(1),
                List.of(named("a", 99))));

        assertNull(associate(
                formals("a", "b"),
                List.of(1, 2),
                List.of(named("b", 99))));

        // Positional values may have originated from a spread.
        assertNull(associate(
                formals("a", "b", "c"),
                List.of(1, 2),
                List.of(named("b", 99))));
    }

    @Test
    void cannotFillHolesInsteadOfCheckingCollisions() {
        assertNull(associate(
                formals("a", "b"),
                List.of(1, 2),
                List.of(named("a", 99))));
    }

    @Test
    void unknownAndRepeatedNamesAreRejected() {
        assertNull(associate(
                formals("a", "b"),
                List.of(),
                List.of(named("unknown", 9))));

        assertNull(associate(
                formals("a", "b"),
                List.of(),
                List.of(named("a", 1), named("a", 2))));
    }

    @Test
    void explicitGuestNullCountsAsSupplied() {
        Association result = associate(
                formals("a", "b"),
                List.of(),
                List.of(named("b", ProtosNullValue.INSTANCE)));

        assertNotNull(result);
        assertFalse(result.isPresent(0));
        assertTrue(result.isPresent(1));
        assertSame(ProtosNullValue.INSTANCE, result.valueAt(1));
    }

    @Test
    void missingFormalsRemainAbsentForDefaults() {
        Association result = associate(
                formals("a", "b", "c"),
                List.of(),
                List.of(named("c", 30)));

        assertNotNull(result);
        assertFalse(result.isPresent(0));
        assertFalse(result.isPresent(1));
        assertTrue(result.isPresent(2));

        // The actual default expressions will be evaluated later,
        // in formal declaration order by the selected activation.
    }

    @Test
    void excessPositionalsWithoutRestRemainIgnored() {
        Association result = associate(
                formals("a", "b"),
                List.of(1, 2, 3, 4),
                List.of());

        assertNotNull(result);
        assertEquals(2, result.ordinaryParameterCount());
        assertEquals(1, result.valueAt(0));
        assertEquals(2, result.valueAt(1));
        assertFalse(result.hasRest());
        assertTrue(result.restValues().isEmpty());
    }

    @Test
    void restCollectsOnlyPositionalSuffix() {
        List<CanonicalParameter> parameters = new ArrayList<>(
                formals("a", "b"));
        parameters.add(rest("rest"));

        Association result = associate(
                parameters,
                List.of(1, 2, 3, 4),
                List.of());

        assertNotNull(result);
        assertTrue(result.hasRest());
        assertEquals(List.of(3, 4), result.restValues());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.restValues().clear());
    }

    @Test
    void restIsEmptyWhenNamedFormalFollowsShortPositionalPrefix() {
        List<CanonicalParameter> parameters =
                new ArrayList<>(formals("a", "b", "c"));
        parameters.add(rest("rest"));

        Association result = associate(
                parameters,
                List.of(1, 2),
                List.of(named("c", 30)));

        assertNotNull(result);
        assertTrue(result.hasRest());
        assertEquals(1, result.valueAt(0));
        assertEquals(2, result.valueAt(1));
        assertEquals(30, result.valueAt(2));
        assertTrue(result.restValues().isEmpty());
        assertEquals(
                List.of(1, 2, 30),
                result.boundSuppliedArgumentsForRuntime());
    }

    @Test
    void restIsEmptyWhenOnlyNamedValuesAreSupplied() {
        List<CanonicalParameter> parameters =
                new ArrayList<>(formals("a", "b", "c"));
        parameters.add(rest("rest"));

        Association result = associate(
                parameters,
                List.of(),
                List.of(named("c", 30)));

        assertNotNull(result);
        assertFalse(result.isPresent(0));
        assertFalse(result.isPresent(1));
        assertTrue(result.isPresent(2));
        assertTrue(result.restValues().isEmpty());
        assertEquals(
                List.of(
                        ProtosNullValue.INSTANCE,
                        ProtosNullValue.INSTANCE,
                        30),
                result.boundSuppliedArgumentsForRuntime());
    }

    @Test
    void restNameIsNotAValidNamedParameter() {
        List<CanonicalParameter> parameters = new ArrayList<>(
                formals("a"));
        parameters.add(rest("rest"));

        assertNull(associate(
                parameters,
                List.of(1),
                List.of(named("rest", 99))));
    }

    @Test
    void parameterNamesArePartOfThePublicCallContract() {
        assertNotNull(associate(
                formals("oldName"),
                List.of(),
                List.of(named("oldName", 1))));

        assertNull(associate(
                formals("newName"),
                List.of(),
                List.of(named("oldName", 1))));
    }

    private static Association associate(
            List<CanonicalParameter> parameters,
            List<?> positional,
            List<NamedValue> named) {
        return ProtosNamedArgumentAssociation.associate(
                parameters, positional, named);
    }

    private static NamedValue named(String name, Object value) {
        return new NamedValue(name, value);
    }

    private static List<CanonicalParameter> formals(String... names) {
        List<CanonicalParameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(new CanonicalParameter(
                    name,
                    Optional.empty(),
                    false,
                    new SourceSpan(0, 0)));
        }
        return List.copyOf(parameters);
    }

    private static CanonicalParameter rest(String name) {
        return new CanonicalParameter(
                name,
                Optional.empty(),
                true,
                new SourceSpan(0, 0));
    }
}
