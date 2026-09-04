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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosStandardStringProtocolTest {
    @Test
    void literalsUseStringPrototypeAndExactScalarValueIdentity() throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertSame(
                ProtosBooleanValue.TRUE,
                execute(prelude, "\"hello\" === (\"hel\" + \"lo\")"));
        assertSame(
                ProtosBooleanValue.TRUE,
                execute(prelude, "\"hello\" == (\"hel\" + \"lo\")"));
        assertSame(
                ProtosBooleanValue.TRUE,
                execute(prelude, "\"é\" !== \"e\\u{301}\""));
        assertSame(
                ProtosBooleanValue.FALSE,
                execute(prelude, "\"é\" == \"e\\u{301}\""));
    }

    @Test
    void sizeAndAtUseUnicode17ExtendedGraphemeClusters() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertInteger(BigInteger.ZERO, execute(prelude, "\"\".size()"));
        assertInteger(BigInteger.valueOf(3), execute(prelude, "\"abc\".size()"));
        assertInteger(BigInteger.ONE, execute(prelude, "\"e\\u{301}\".size()"));
        assertInteger(BigInteger.ONE, execute(prelude, "\"👨‍👩‍👧‍👦\".size()"));

        ProtosStringValue combining =
                (ProtosStringValue) execute(prelude, "\"e\\u{301}\"[0]");
        assertEquals("e\u0301", combining.value());

        ProtosStringValue supplementary =
                (ProtosStringValue) execute(prelude, "\"😀\"[0]");
        assertEquals("😀", supplementary.value());
    }

    @Test
    void atAcceptsAllSemanticIntegerFamiliesAndRejectsInvalidIndexes() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertEquals(
                "b",
                ((ProtosStringValue) execute(prelude, "\"abc\".at(UInt8(1))")).value());
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "\"abc\".at(-1)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "\"abc\".at(3)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "\"abc\".at(1.0)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "\"abc\".at(\"1\")"));
    }

    @Test
    void concatenationIsStringOnlyAndPreservesExactScalarSequence() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertEquals(
                "e\u0301x",
                ((ProtosStringValue) execute(prelude, "\"e\\u{301}\" + \"x\"")).value());
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "\"x\" + 1"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "\"x\" + null"));
    }

    @Test
    void inheritedStandardBehaviorKeepsOriginalReceiverAndDoesNotConferMembership()
            throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(
                ProtosSignalException.class,
                () ->
                        execute(
                                prelude,
                                """
                                Fake: String {}
                                Fake.size()
                                """));
        assertThrows(
                ProtosSignalException.class,
                () ->
                        execute(
                                prelude,
                                """
                                Fake: String {}
                                Fake + "x"
                                """));
    }

    @Test
    void standardMethodsRejectWrongArityAsProtosErrors() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertThrows(ProtosSignalException.class, () -> execute(prelude, "\"x\".size(1)"));
        assertThrows(ProtosSignalException.class, () -> execute(prelude, "\"x\".at()"));
    }

    @Test
    void stringPrototypeIsFrozenPreludeBindingDelegatingDirectlyToObject()
            throws IOException {
        ProtosPrelude prelude = corePrelude();
        assertSame(
                com.guillermomolina.protos.runtime.ProtosObjectValue.rootObject(),
                prelude.stringPrototype().parent().orElseThrow());
        assertTrue(prelude.bindings().isFrozen());
        assertSame(
                prelude.stringPrototype(),
                prelude.bindings().readLocalSlot("String").orElseThrow());
    }

    private static void assertInteger(BigInteger expected, Object actual) {
        assertEquals(expected, ((ProtosIntegerValue) actual).value());
    }

    private static Object execute(ProtosPrelude prelude, String source) {
        return new ProtosSourceCompiler()
                .compile(source)
                .call(prelude.newModuleActivation());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap()
                .bootstrap(Path.of("protos", "lib", "core"));
    }
}
