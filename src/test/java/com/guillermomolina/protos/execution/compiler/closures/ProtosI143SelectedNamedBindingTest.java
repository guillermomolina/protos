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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.SelectedBinding;
import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.SourceBinding;
import com.guillermomolina.protos.execution.ProtosNamedArgumentAssociation.StandardForward;
import com.guillermomolina.protos.execution.ProtosPreparedCalls.PreparedArgumentVector;
import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.parser.ast.SurfaceSequence;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.callables.ProtosNativeClosureBody;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalClosure;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * I143 / D239-C1: actual selected implementation classification.
 *
 * <p>Does not execute or bypass D013 selection. Its input is the
 * implementation that the future named dispatch must select first.
 */
class ProtosI143SelectedNamedBindingTest {

    @Test
    void sourceImplementationBindsItsOwnDeclaredNames() {
        ProtosClosureValue selected = sourceClosure(
                "(a: 10, b: 20, c: 30) => a");

        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.appendNamed("c", 99);
        arguments.append(1);

        SourceBinding result = assertInstanceOf(
                SourceBinding.class, classify(selected, arguments));

        assertSame(selected, result.selected());
        assertTrue(result.association().isPresent(0));
        assertEquals(1, result.association().valueAt(0));
        assertFalse(result.association().isPresent(1));
        assertEquals(99, result.association().valueAt(2));
    }

    @Test
    void selectedImplementationNotReceiverGuessControlsBinding() {
        ProtosClosureValue original = sourceClosure("(a) => a");
        ProtosClosureValue replacement = sourceClosure("(b) => b");

        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.appendNamed("b", 7);

        assertNull(classify(original, arguments));

        SourceBinding accepted = assertInstanceOf(
                SourceBinding.class, classify(replacement, arguments));

        assertSame(replacement, accepted.selected());
        assertEquals(7, accepted.association().valueAt(0));
    }

    @Test
    void unknownNamesAndPositionalCollisionsAreRejected() {
        ProtosClosureValue selected = sourceClosure("(a, b) => a");

        PreparedArgumentVector unknown = new PreparedArgumentVector();
        unknown.appendNamed("missing", 3);
        assertNull(classify(selected, unknown));

        PreparedArgumentVector collision = new PreparedArgumentVector();
        collision.appendNamed("a", 3);
        collision.append(1);
        assertNull(classify(selected, collision));
    }

    @Test
    void canonicalNullIsSuppliedNotAbsent() {
        ProtosClosureValue selected = sourceClosure("(a: 10) => a");

        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.appendNamed("a", ProtosNullValue.INSTANCE);

        SourceBinding result = assertInstanceOf(
                SourceBinding.class, classify(selected, arguments));

        assertTrue(result.association().isPresent(0));
        assertSame(
                ProtosNullValue.INSTANCE,
                result.association().valueAt(0));
    }

    @Test
    void ordinaryNativeBehaviorDoesNotInferJavaParameterNames() {
        ProtosClosureValue nativeBehavior =
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ProtosNullValue.INSTANCE);

        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.appendNamed("supplied", 1);

        assertNull(classify(nativeBehavior, arguments));
    }

    @Test
    void copiedStandardCallImplementationForwardsUnvalidatedPacket()
            throws Exception {
        Field bodyField = ProtosStandardObjectProtocol.class
                .getDeclaredField("STANDARD_CALL_BODY");
        bodyField.setAccessible(true);

        ProtosNativeClosureBody implementation =
                (ProtosNativeClosureBody) bodyField.get(null);

        // A new Closure object carrying the same executable behavior.
        // Neither the original STANDARD_CALL Closure identity nor a
        // methodHome == Object condition is required.
        ProtosClosureValue copy =
                ProtosClosureValue.nativeClosure(implementation);

        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.appendNamed("notAWrapperParameter", 11);
        arguments.append(9);

        StandardForward forwarded = assertInstanceOf(
                StandardForward.class, classify(copy, arguments));

        assertEquals(List.of(9), forwarded.positional());
        assertEquals(1, forwarded.named().size());
        assertEquals(
                "notAWrapperParameter",
                forwarded.named().get(0).name());
        assertEquals(11, forwarded.named().get(0).value());
    }

    @Test
    void copiedStandardConstructImplementationForwardsUnvalidatedPacket()
            throws Exception {
        Field bodyField = ProtosStandardObjectProtocol.class
                .getDeclaredField("STANDARD_CONSTRUCT_BODY");
        bodyField.setAccessible(true);

        // D239-C1: the executable body, not the selector or methodHome,
        // admits transparent forwarding to the selected init.
        ProtosClosureValue copy = ProtosClosureValue.nativeClosure(
                (ProtosNativeClosureBody) bodyField.get(null));

        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.appendNamed("initParameter", 5);
        arguments.append(4);

        StandardForward forwarded = assertInstanceOf(
                StandardForward.class, classify(copy, arguments));

        assertEquals(List.of(4), forwarded.positional());
        assertEquals(1, forwarded.named().size());
        assertEquals("initParameter", forwarded.named().get(0).name());
        assertEquals(5, forwarded.named().get(0).value());
    }

    @Test
    void sourceConstructBindsItsOwnFormals() {
        ProtosClosureValue selected = sourceClosure(
                "(start: 0, stop: 0) => stop");

        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.appendNamed("stop", 3);

        SourceBinding result = assertInstanceOf(
                SourceBinding.class, classify(selected, arguments));

        assertFalse(result.association().isPresent(0));
        assertEquals(3, result.association().valueAt(1));
    }

    @Test
    void forwardPacketIsIndependentOfSubsequentAppends()
            throws Exception {
        Field bodyField = ProtosStandardObjectProtocol.class
                .getDeclaredField("STANDARD_CALL_BODY");
        bodyField.setAccessible(true);

        ProtosClosureValue copy = ProtosClosureValue.nativeClosure(
                (ProtosNativeClosureBody) bodyField.get(null));

        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.appendNamed("a", 1);

        StandardForward forwarded = assertInstanceOf(
                StandardForward.class, classify(copy, arguments));

        arguments.appendNamed("b", 2);
        arguments.append(3);

        assertEquals(1, forwarded.named().size());
        assertTrue(forwarded.positional().isEmpty());

        assertThrows(
                UnsupportedOperationException.class,
                () -> forwarded.named().clear());
    }

    @Test
    void positionalOnlySitesCannotEnterNamedClassification() {
        ProtosClosureValue selected = sourceClosure("(a) => a");
        PreparedArgumentVector arguments = new PreparedArgumentVector();
        arguments.append(1);

        assertThrows(
                IllegalArgumentException.class,
                () -> classify(selected, arguments));
    }

    private static SelectedBinding classify(
            ProtosClosureValue selected,
            PreparedArgumentVector arguments) {
        return ProtosNamedArgumentAssociation
                .classifySelectedImplementation(selected, arguments);
    }

    private static ProtosClosureValue sourceClosure(String text) {
        SurfaceSequence program = new ProtosParser(text).parseProgram();
        CanonicalClosure definition = assertInstanceOf(
                CanonicalClosure.class,
                new Canonicalizer().canonicalize(
                        program.expressions().get(0)));

        return new ProtosClosureValue(
                definition,
                List.of(),
                ProtosObjectValue.rootObject());
    }
}
