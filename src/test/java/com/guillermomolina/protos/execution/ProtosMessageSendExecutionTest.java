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

import com.guillermomolina.protos.runtime.ProtosCoreErrors;
import com.guillermomolina.protos.runtime.ProtosCoreErrors.StandardError;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProtosMessageSendExecutionTest {
    /**
     * Deliberately Java-side: executable source conformance covers ordinary and
     * inherited method sends, dynamic receiver/method-home behavior, and
     * argument/spread evaluation. The generic `error` expectation can only
     * assert that a Protos signal occurred, so retain this exact standard-error
     * category check until source-level handling or a category-aware harness
     * makes the transported Error observable.
     */
    @Test
    void missingMessageSignalsExactSlotNotFoundCategory() throws IOException {
        ProtosPrelude prelude = corePrelude();
        ProtosSignalException signal =
                assertThrows(
                        ProtosSignalException.class,
                        () -> execute(prelude, "({}).missing()"));

        assertSame(
                ProtosCoreErrors.prototype(
                        prelude.newModuleActivation(),
                        StandardError.SLOT_NOT_FOUND),
                signal.error().parent().orElseThrow());
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
