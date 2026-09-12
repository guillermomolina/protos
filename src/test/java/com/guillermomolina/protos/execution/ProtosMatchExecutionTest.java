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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProtosMatchExecutionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path MATCHING =
            Path.of("protos", "tests", "conformance", "matching");

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrapCore() throws Exception {
        prelude = new ProtosCoreBootstrap().bootstrap(CORE);
    }

    @Test
    void subjectArmOrderMatcherAndBodyAreExactlyOnceAndLazy() throws Exception {
        assertSame(
                ProtosBooleanValue.TRUE,
                execute("value-order-subject-matcher-once.protos"));
    }

    @Test
    void binderAndWildcardUseTheRatifiedZeroAndOneCaptureAbi() throws Exception {
        assertSame(
                ProtosBooleanValue.TRUE,
                execute("binder-wildcard.protos"));
    }

    @Test
    void opaqueMatcherCaptureInterfacesUseOrdinaryFixedAndRestParameters()
            throws Exception {
        assertSame(
                ProtosBooleanValue.TRUE,
                execute("opaque-captures-fixed-rest.protos"));
    }

    @Test
    void noMatchSignalsFreshGenericErrors() throws Exception {
        String source = source("no-match-error.protos");

        ProtosSignalException first =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile(source)
                                        .call(prelude.newModuleActivation()));
        ProtosSignalException second =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile(source)
                                        .call(prelude.newModuleActivation()));

        assertSame(prelude.errorPrototype(), first.error().parent().orElseThrow());
        assertSame(prelude.errorPrototype(), second.error().parent().orElseThrow());
        assertNotSame(first.error(), second.error());
    }

    @Test
    void invalidAndEmptyMatcherOutcomesSignalGenericError() throws Exception {
        assertGenericError("invalid-outcome-error.protos");
        assertGenericError("empty-capture-array-error.protos");
    }

    @Test
    void selectedArmArityFailureDoesNotRetryLaterArm() throws Exception {
        ProtosActivation activation = prelude.newModuleActivation();

        ProtosSignalException signalled =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile(source("capture-arity-no-retry-error.protos"))
                                        .call(activation));

        assertSame(prelude.errorPrototype(), signalled.error().parent().orElseThrow());

        ProtosObjectValue state =
                (ProtosObjectValue) activation.lookup("state").orElseThrow();
        ProtosIntegerValue later =
                (ProtosIntegerValue) state.readLocalSlot("later").orElseThrow();
        assertEquals(BigInteger.ZERO, later.value());
    }

    private static void assertGenericError(String name) throws Exception {
        ProtosSignalException signalled =
                assertThrows(
                        ProtosSignalException.class,
                        () ->
                                new ProtosSourceCompiler()
                                        .compile(source(name))
                                        .call(prelude.newModuleActivation()));
        assertSame(prelude.errorPrototype(), signalled.error().parent().orElseThrow());
    }

    private static Object execute(String name) throws Exception {
        return new ProtosSourceCompiler()
                .compile(source(name))
                .call(prelude.newModuleActivation());
    }

    private static String source(String name) throws Exception {
        return Files.readString(MATCHING.resolve(name));
    }
}
