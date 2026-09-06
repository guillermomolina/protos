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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.parser.ProtosParser;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosNonLocalReturnException;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosReturnHome;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.semantic.Canonicalizer;
import com.guillermomolina.protos.semantic.ast.CanonicalExpression;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosNonLocalReturnTest {
    /**
     * Deliberately Java-side: this is an implementation-control test. It
     * manufactures a Closure with a specific host ReturnHome and verifies that
     * nested invocation rethrows the exact transfer to that still-active home.
     * Executable Protos conformance independently covers the observable result
     * of a nested non-local return.
     */
    @Test
    void nestedInvocationWithCapturedActiveHomeRethrowsTransferToOwner()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosClosureValue source =
                closure(prelude, "() => ^42");
        ProtosReturnHome capturedHome = new ProtosReturnHome();
        ProtosClosureValue nested =
                new ProtosClosureValue(
                        source.definition(),
                        source.capturedLexicalContexts(),
                        source.capturedReceiver(),
                        source.methodHome().orElse(null),
                        capturedHome,
                        source.prelude().orElseThrow(),
                        source.executionPlan().orElseThrow());

        ProtosNonLocalReturnException transfer =
                assertThrows(
                        ProtosNonLocalReturnException.class,
                        () -> ProtosClosureInvoker.invoke(nested, List.of()));

        assertSame(capturedHome, transfer.target());
        assertTrue(capturedHome.isActive());
    }

    /**
     * Deliberately Java-side: the generic language conformance runner can
     * observe that the escaped Closure signals, but cannot assert either the
     * exact InvalidReturn prototype or that two independent observations
     * manufacture distinct standard failure occurrences.
     */
    @Test
    void escapedClosureSignalsFreshInvalidReturnAfterHomeCompleted()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosClosureValue maker =
                closure(prelude, "() => { () => ^42 }");

        ProtosClosureValue escaped =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        ProtosClosureInvoker.invoke(maker, List.of()));
        ProtosReturnHome completedHome = escaped.returnHome().orElseThrow();

        assertFalse(completedHome.isActive());

        ProtosSignalException first =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosClosureInvoker.invoke(escaped, List.of()));
        ProtosSignalException second =
                assertThrows(
                        ProtosSignalException.class,
                        () -> ProtosClosureInvoker.invoke(escaped, List.of()));

        ProtosObjectValue invalidReturnPrototype =
                prelude.invalidReturnPrototype();
        assertSame(
                invalidReturnPrototype,
                first.error().parent().orElseThrow());
        assertSame(
                invalidReturnPrototype,
                second.error().parent().orElseThrow());
        org.junit.jupiter.api.Assertions.assertNotSame(
                first.error(),
                second.error());
    }

    private static ProtosClosureValue closure(
            ProtosPrelude prelude,
            String source) {
        CanonicalExpression canonical =
                new Canonicalizer()
                        .canonicalize(
                                new ProtosParser(source).parseProgram());
        ProtosExpressionNode lowered =
                new CanonicalToTruffleLowerer().lower(canonical);
        return assertInstanceOf(
                ProtosClosureValue.class,
                ProtosExecution.createCallTarget(lowered)
                        .call(prelude.newModuleActivation()));
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}
