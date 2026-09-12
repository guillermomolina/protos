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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Host/runtime representation coverage retained after TEST001-D2 moved
 * ordinary Array-pattern semantics to TOOL002.
 */
class ProtosArrayMatchExecutionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path MATCHING =
            Path.of("protos", "tests", "conformance", "matching");

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrapCore() throws Exception {
        prelude = new ProtosCoreBootstrap().bootstrap(CORE);
    }

    @Test
    void materializedRemaindersAreFreshFrozenStandardArrays() throws Exception {
        ProtosArrayValue pair =
                (ProtosArrayValue) execute("array-remainder-identity.protos");
        List<Object> values = pair.indexedSnapshot();
        assertEquals(2, values.size());

        ProtosArrayValue first = (ProtosArrayValue) values.get(0);
        ProtosArrayValue second = (ProtosArrayValue) values.get(1);

        assertTrue(first.isFrozen());
        assertTrue(second.isFrozen());
        assertSame(prelude.arrayPrototype(), first.parent().orElseThrow());
        assertSame(prelude.arrayPrototype(), second.parent().orElseThrow());
        assertNotSame(first, second);
        assertEquals(1, first.indexedSnapshot().size());
        assertEquals(1, second.indexedSnapshot().size());
        assertSame(
                first.indexedSnapshot().get(0),
                second.indexedSnapshot().get(0));
    }

    private static Object execute(String name) throws Exception {
        return new ProtosSourceCompiler()
                .compile(Files.readString(MATCHING.resolve(name)))
                .call(prelude.newModuleActivation());
    }
}
