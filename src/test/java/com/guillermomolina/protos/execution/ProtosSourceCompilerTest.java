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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guillermomolina.protos.parser.ParseError;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCorePrelude;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosSourceCompilerTest {
    @Test
    void compilesCompleteSourceThroughParserCanonicalizerAndLowerer() {
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        Object result =
                compiler.compile("1\n2")
                        .call(freshTopLevelActivation());

        ProtosIntegerValue integer =
                assertInstanceOf(ProtosIntegerValue.class, result);
        assertEquals(BigInteger.valueOf(2), integer.value());
    }

    @Test
    void compiledObjectSourceUsesOrdinaryObjectExecution() {
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        Object result =
                compiler.compile("{ value: 7 }")
                        .call(freshTopLevelActivation());

        ProtosObjectValue object =
                assertInstanceOf(ProtosObjectValue.class, result);
        ProtosIntegerValue value =
                assertInstanceOf(
                        ProtosIntegerValue.class,
                        object.readLocalSlot("value").orElseThrow());
        assertEquals(BigInteger.valueOf(7), value.value());
        assertSame(
                ProtosObjectValue.rootObject(),
                object.parent().orElseThrow());
    }

    @Test
    void parserFailuresPropagateWithoutCreatingAnExecutionTarget() {
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        assertThrows(
                ParseError.class,
                () -> compiler.compile("name\n)"));
    }

    private static ProtosActivation freshTopLevelActivation() {
        ProtosObjectValue context = ProtosCorePrelude.newExecutionContext();
        return new ProtosActivation(context, List.of(), context);
    }
}
