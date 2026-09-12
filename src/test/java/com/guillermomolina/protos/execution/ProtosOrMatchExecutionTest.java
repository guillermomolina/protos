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
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProtosOrMatchExecutionTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path MATCHING =
            Path.of("protos", "tests", "conformance", "matching");

    private static ProtosPrelude prelude;

    @BeforeAll
    static void bootstrapCore() throws Exception {
        prelude = new ProtosCoreBootstrap().bootstrap(CORE);
    }

    @Test
    void retainedProtosCasesCoverD090OrderedAlternativeSemantics() throws Exception {
        for (String name :
                List.of(
                        "or-ordered-effects-first-success.protos",
                        "or-same-subject.protos",
                        "or-structural-bindings.protos",
                        "or-nested-attempt-order.protos",
                        "or-subject-evaluated-once.protos",
                        "or-dynamic-captures.protos")) {
            assertSame(ProtosBooleanValue.TRUE, execute(name), name);
        }
    }

    @Test
    void invalidD072OutcomeDoesNotRetryALaterAlternative() throws Exception {
        assertThrows(
                ProtosSignalException.class,
                () -> execute("or-invalid-outcome-no-retry.protos"));
    }

    private static Object execute(String name) throws Exception {
        return new ProtosSourceCompiler()
                .compile(Files.readString(MATCHING.resolve(name)))
                .call(prelude.newModuleActivation());
    }
}
