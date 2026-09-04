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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosCorePrelude;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosCoreContextSourceTest {
    @Test
    void coreSourceConstructsContextAsAnOrdinaryObject() throws IOException {
        ProtosObjectValue bootstrapContext = ProtosCorePrelude.newExecutionContext();
        ProtosActivation activation =
                new ProtosActivation(
                        bootstrapContext,
                        List.of(),
                        bootstrapContext);

        new ProtosSourceFileLoader()
                .load(Path.of("protos", "lib", "core", "context.protos"))
                .call(activation);

        ProtosObjectValue contextPrototype =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        bootstrapContext
                                .readLocalSlot("Context")
                                .orElseThrow());
        assertSame(
                ProtosObjectValue.rootObject(),
                contextPrototype.parent().orElseThrow());
    }
}
