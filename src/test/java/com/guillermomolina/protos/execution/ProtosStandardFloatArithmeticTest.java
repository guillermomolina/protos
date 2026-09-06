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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosClosureValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosStandardFloatArithmeticTest {
    // Deliberately Java-side: this verifies the Core self-hosting/provenance
    // boundary, not observable Float arithmetic semantics.
    @Test
    void negatedRemainsSourceBackedOverNativeMultiplication() throws IOException {
        ProtosPrelude prelude = corePrelude();

        ProtosClosureValue negated =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        prelude.floatPrototype().readLocalSlot("negated").orElseThrow());
        assertNotNull(negated.definition());
        assertTrue(negated.executionPlan().isPresent());
        assertTrue(negated.nativeBody().isEmpty());

        ProtosClosureValue multiply =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        prelude.floatPrototype().readLocalSlot("*").orElseThrow());
        assertTrue(multiply.nativeBody().isPresent());
    }

    private static ProtosPrelude corePrelude() throws IOException {
        return new ProtosCoreBootstrap().bootstrap(Path.of("protos", "lib", "core"));
    }
}
