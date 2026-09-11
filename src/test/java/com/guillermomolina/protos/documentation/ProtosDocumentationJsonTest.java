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

package com.guillermomolina.protos.documentation;

import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Artifact;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Callable;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Generator;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Module;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.RepositoryRevisionScope;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SourceProvenance;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SourceRange;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.StandardModuleIdentity;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Symbol;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SymbolIdentity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosDocumentationJsonTest {
    @Test
    void canonicalBytesAreIndependentOfInputEntityOrder() {
        StandardModuleIdentity setModule = new StandardModuleIdentity("std:collections/Set");
        StandardModuleIdentity textModule = new StandardModuleIdentity("std:text");
        SourceProvenance setSource = source("protos/lib/collections/Set.protos", 1, 1, 5, 1);
        SourceProvenance textSource = source("protos/lib/text.protos", 1, 1, 3, 1);

        Module set = new Module(setModule, "Set docs.", setSource);
        Module text = new Module(textModule, null, textSource);
        Symbol contains = new Symbol(
                new SymbolIdentity(setModule, "contains"),
                "Checks `element`.",
                setSource,
                new Callable(List.of("element"), null));
        Symbol size = new Symbol(
                new SymbolIdentity(setModule, "size"),
                null,
                setSource,
                null);

        Artifact first = artifact(
                List.of(text, set),
                List.of(size, contains));
        Artifact second = artifact(
                List.of(set, text),
                List.of(contains, size));

        assertArrayEquals(
                ProtosDocumentationJson.serialize(first),
                ProtosDocumentationJson.serialize(second));
    }

    @Test
    void canonicalJsonSeparatesIdentityProvenanceAndCallableFacts() {
        StandardModuleIdentity module = new StandardModuleIdentity("std:collections/Set");
        SourceProvenance source = source("protos/lib/collections/Set.protos", 7, 2, 9, 1);
        Artifact artifact = new Artifact(
                new Generator("protos-doc", "test-version"),
                new RepositoryRevisionScope("guillermomolina/protos", "abc123"),
                List.of(new Module(module, "A **set**.", source)),
                List.of(new Symbol(
                        new SymbolIdentity(module, "contains"),
                        "Checks \"membership\".\nSecond line.",
                        source,
                        new Callable(List.of("element"), "additional"))));

        String json = new String(
                ProtosDocumentationJson.serialize(artifact),
                StandardCharsets.UTF_8);

        assertEquals(
                """
                {
                  "format":{"name":"protos-documentation","major":1,"minor":0},
                  "generator":{"name":"protos-doc","version":"test-version"},
                  "provenance":{"kind":"repositoryRevision","repository":"guillermomolina/protos","revision":"abc123"},
                  "modules":[
                    {"kind":"module","identity":{"kind":"std","name":"std:collections/Set"},"documentation":"A **set**.","source":{"path":"protos/lib/collections/Set.protos","range":{"start":{"line":7,"column":2},"end":{"line":9,"column":1}}}}
                  ],
                  "symbols":[
                    {"kind":"slot","identity":{"module":{"kind":"std","name":"std:collections/Set"},"slot":"contains"},"documentation":"Checks \\"membership\\".\\nSecond line.","source":{"path":"protos/lib/collections/Set.protos","range":{"start":{"line":7,"column":2},"end":{"line":9,"column":1}}},"callable":{"parameters":["element"],"restParameter":"additional"}}
                  ],
                  "articles":[],
                  "relationships":[]
                }
                """,
                json);
    }

    @Test
    void canonicalOutputHasUtf8UnicodeLfAndExactlyOneFinalNewline() {
        StandardModuleIdentity module = new StandardModuleIdentity("std:colecciones/Ñ");
        SourceProvenance source = source("protos/lib/colecciones/Ñ.protos", 1, 1, 1, 2);
        Artifact artifact = new Artifact(
                new Generator("protos-doc", "test"),
                new RepositoryRevisionScope("guillermomolina/protos", "abc"),
                List.of(new Module(module, "café 😀", source)),
                List.of());

        byte[] bytes = ProtosDocumentationJson.serialize(artifact);
        String json = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(json.contains("café 😀"));
        assertFalse(json.contains("\r"));
        assertTrue(json.endsWith("\n"));
        assertFalse(json.endsWith("\n\n"));
    }

    private static Artifact artifact(List<Module> modules, List<Symbol> symbols) {
        return new Artifact(
                new Generator("protos-doc", "test"),
                new RepositoryRevisionScope("guillermomolina/protos", "abc"),
                modules,
                symbols);
    }

    private static SourceProvenance source(
            String path,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn) {
        return new SourceProvenance(
                path,
                new SourceRange(startLine, startColumn, endLine, endColumn));
    }
}
