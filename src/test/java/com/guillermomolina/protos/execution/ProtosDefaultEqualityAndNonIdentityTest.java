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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosDefaultEqualityAndNonIdentityTest {
    @Test
    void defaultObjectEqualityUsesSemanticIdentity() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosObjectValue a = new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue b = new ProtosObjectValue(ProtosObjectValue.rootObject());

        assertSame(
                ProtosBooleanValue.TRUE,
                ProtosInvocation.invokeMessage(a, "==", List.of(a), prelude.newModuleActivation()));
        assertSame(
                ProtosBooleanValue.FALSE,
                ProtosInvocation.invokeMessage(a, "==", List.of(b), prelude.newModuleActivation()));
    }

    @Test
    void defaultInequalityComplementsDynamicEqualityOverride() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot(
                "==",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> ProtosBooleanValue.TRUE));

        assertSame(
                ProtosBooleanValue.FALSE,
                ProtosInvocation.invokeMessage(
                        receiver,
                        "!=",
                        List.of(new ProtosObjectValue(ProtosObjectValue.rootObject())),
                        prelude.newModuleActivation()));
    }

    @Test
    void defaultInequalityRejectsInvalidEqualityResult() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosObjectValue receiver = new ProtosObjectValue(ProtosObjectValue.rootObject());
        receiver.createLocalSlot(
                "==",
                ProtosClosureValue.nativeClosure(
                        (activation, supplied) -> new ProtosIntegerValue(BigInteger.ONE)));

        assertThrows(
                ProtosSignalException.class,
                () ->
                        ProtosInvocation.invokeMessage(
                                receiver,
                                "!=",
                                List.of(receiver),
                                prelude.newModuleActivation()));
    }

    @Test
    void sourceInequalityUsesObjectInequalityProtocol() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertBoolean(prelude, "1 != 2", true);
        assertBoolean(prelude, "1 != 1.0", false);
        assertBoolean(prelude, "UInt8(1) != Int32(1)", false);
    }

    @Test
    void nonIdentityIsPrimitiveComplementWithoutNotDispatch() throws IOException {
        ProtosPrelude prelude = corePrelude();

        assertBoolean(prelude, "1 !== 1", false);
        assertBoolean(prelude, "1 !== 1.0", true);
        assertBoolean(prelude, "{} !== {}", true);
    }

    private static void assertBoolean(ProtosPrelude prelude, String source, boolean expected) {
        Object result =
                new ProtosSourceCompiler()
                        .compile(source)
                        .call(prelude.newModuleActivation());
        assertSame(expected ? ProtosBooleanValue.TRUE : ProtosBooleanValue.FALSE, result);
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}
