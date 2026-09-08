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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

class ProtosLanguageSourceParsingTest {
    @Test
    void polyglotContextParsesNestedProtosRootsThroughTheRealFrontendWithoutExecutingThem() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            org.graalvm.polyglot.Source source =
                    org.graalvm.polyglot.Source
                            .newBuilder(
                                    ProtosLanguage.ID,
                                    "{ value: () => { nested: 1 } }",
                                    "polyglot-parse.protos")
                            .uri(URI.create("memory:///polyglot-parse.protos"))
                            .buildLiteral();

            Value parsed = context.parse(source);

            assertTrue(parsed.canExecute());
        }
    }

    @Test
    void sourceBoundRootFactoryRetainsTheExactTruffleSourceAndLanguageIdentity() {
        ProtosLanguage language = new ProtosLanguage();
        com.oracle.truffle.api.source.Source source =
                com.oracle.truffle.api.source.Source
                        .newBuilder(
                                ProtosLanguage.ID,
                                "1\n2",
                                "source-owned.protos")
                        .uri(URI.create("memory:///source-owned.protos"))
                        .build();

        ProtosRootFactory roots = ProtosRootFactory.sourceBound(language, source);

        assertSame(language, roots.language().orElseThrow());
        assertSame(source, roots.source().orElseThrow());
    }
}
