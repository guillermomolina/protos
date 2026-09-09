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

package com.guillermomolina.protos.parser;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * LM008-B3 retained Protos-source parser rejection evidence.
 *
 * <p>The invalid syntax remains real guest source. Java is only the smallest
 * observation boundary because these forms must fail before a Protos Process
 * can execute them.
 */
final class ProtosCallableSurfaceNegativeFixtureTest {
    private static final Path FIXTURE_ROOT =
            Path.of("protos", "tests", "parser");

    @Test
    void rejectsInvalidClosureParameterAndTrailingClosureForms()
            throws Exception {
        for (String fixture :
                List.of(
                        "lm008-b3-duplicate-parameter-rejected.protos",
                        "lm008-b3-nonfinal-rest-rejected.protos",
                        "lm008-b3-second-trailing-closure-rejected.protos")) {
            String source =
                    Files.readString(
                            FIXTURE_ROOT.resolve(fixture),
                            StandardCharsets.UTF_8);
            assertThrows(
                    ParseError.class,
                    () -> new ProtosParser(source).parseProgram(),
                    fixture);
        }
    }
}
