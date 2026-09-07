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

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosCryptoSha256ModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");
    private static final Path CASE_ROOT =
            Path.of("protos", "tests", "library", "crypto", "sha256");

    private static final String[] POSITIVE_CASES = {
        "empty.protos",
        "abc.protos",
        "multi.protos",
        "binary.protos",
        "a55.protos",
        "a56.protos",
        "a64.protos",
        "fresh-result.protos",
        "input-unchanged.protos"
    };

    @Test
    void protosFixturesOwnSha256Behavior() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        for (String fixture : POSITIVE_CASES) {
            ProtosActivation activation = prelude.newModuleActivation();
            Object result =
                    new ProtosSourceCompiler()
                            .compile(
                                    Files.readString(
                                            CASE_ROOT.resolve(fixture),
                                            StandardCharsets.UTF_8))
                            .call(activation);

            assertSame(ProtosBooleanValue.TRUE, result, fixture);
        }

        ProtosActivation activation = prelude.newModuleActivation();
        assertThrows(
                ProtosSignalException.class,
                () ->
                        new ProtosSourceCompiler()
                                .compile(
                                        Files.readString(
                                                CASE_ROOT.resolve(
                                                        "invalid-non-bytes.protos"),
                                                StandardCharsets.UTF_8))
                                .call(activation));
    }
}
