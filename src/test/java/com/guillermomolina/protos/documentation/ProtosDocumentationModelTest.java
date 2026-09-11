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
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.PackageModuleIdentity;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.RepositoryRevisionScope;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SourceProvenance;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SourceRange;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.StandardModuleIdentity;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.Symbol;
import static com.guillermomolina.protos.documentation.ProtosDocumentationModel.SymbolIdentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtosDocumentationModelTest {
    @Test
    void sourceMovementAndCallableEvolutionDoNotChangeSymbolIdentity() {
        StandardModuleIdentity module = new StandardModuleIdentity("std:collections/Set");
        SymbolIdentity identity = new SymbolIdentity(module, "contains");

        Symbol first = new Symbol(
                identity,
                "Tests membership.",
                source("protos/lib/collections/Set.protos", 10, 1, 10, 20),
                new Callable(List.of("element"), null));
        Symbol moved = new Symbol(
                identity,
                "Tests membership.",
                source("protos/lib/set/Set.protos", 80, 3, 84, 1),
                new Callable(List.of("candidate", "comparison"), "rest"));

        assertEquals(first.identity(), moved.identity());
        assertNotEquals(first.source(), moved.source());
        assertNotEquals(first.callable(), moved.callable());
    }

    @Test
    void exactArtifactScopeSeparatesOccurrencesWithoutChangingLineage() {
        StandardModuleIdentity module = new StandardModuleIdentity("std:collections/Set");
        Symbol symbol = new Symbol(
                new SymbolIdentity(module, "contains"),
                null,
                source("protos/lib/collections/Set.protos", 1, 1, 2, 1),
                null);

        Artifact first = artifact(
                new RepositoryRevisionScope("guillermomolina/protos", "abc"),
                List.of(new Module(module, null, symbol.source())),
                List.of(symbol));
        Artifact second = artifact(
                new RepositoryRevisionScope("guillermomolina/protos", "def"),
                List.of(new Module(module, null, symbol.source())),
                List.of(symbol));

        assertEquals(first.occurrenceKey(symbol.identity()).identity(), second.occurrenceKey(symbol.identity()).identity());
        assertNotEquals(first.occurrenceKey(symbol.identity()), second.occurrenceKey(symbol.identity()));
    }

    @Test
    void packageVersionsShareLineageButHaveDistinctExactOccurrences() {
        PackageModuleIdentity module =
                new PackageModuleIdentity("package-id", "collections/Set");
        Symbol symbol = new Symbol(
                new SymbolIdentity(module, "contains"),
                null,
                source("src/collections/Set.protos", 1, 1, 2, 1),
                null);
        Module moduleRecord = new Module(module, null, symbol.source());

        Artifact first = artifact(
                new ProtosDocumentationModel.PackageReleaseScope(
                        "package-id", "1.7.0", "content-one"),
                List.of(moduleRecord),
                List.of(symbol));
        Artifact second = artifact(
                new ProtosDocumentationModel.PackageReleaseScope(
                        "package-id", "2.0.0", "content-two"),
                List.of(moduleRecord),
                List.of(symbol));

        assertEquals(
                first.occurrenceKey(symbol.identity()).identity(),
                second.occurrenceKey(symbol.identity()).identity());
        assertNotEquals(
                first.occurrenceKey(symbol.identity()),
                second.occurrenceKey(symbol.identity()));
    }

    @Test
    void duplicateSymbolIdentityWithinOneArtifactIsRejected() {
        StandardModuleIdentity module = new StandardModuleIdentity("std:collections/Set");
        SourceProvenance source = source("protos/lib/collections/Set.protos", 1, 1, 2, 1);
        SymbolIdentity identity = new SymbolIdentity(module, "contains");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> artifact(
                        new RepositoryRevisionScope("guillermomolina/protos", "abc"),
                        List.of(new Module(module, null, source)),
                        List.of(
                                new Symbol(identity, null, source, null),
                                new Symbol(
                                        identity,
                                        null,
                                        source("protos/lib/collections/Set.protos", 20, 1, 21, 1),
                                        new Callable(List.of("x"), null)))));

        assertEquals(
                "duplicate symbol identity in exact artifact: " + identity,
                error.getMessage());
    }

    @Test
    void symbolMustReferenceAModulePresentInArtifact() {
        StandardModuleIdentity declared = new StandardModuleIdentity("std:collections/Set");
        PackageModuleIdentity missing = new PackageModuleIdentity("package-id", "collections/Set");
        SourceProvenance source = source("protos/lib/collections/Set.protos", 1, 1, 2, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> artifact(
                        new RepositoryRevisionScope("guillermomolina/protos", "abc"),
                        List.of(new Module(declared, null, source)),
                        List.of(new Symbol(
                                new SymbolIdentity(missing, "contains"),
                                null,
                                source,
                                null))));
    }

    @Test
    void absoluteAndNonNormalizedPathsAreRejectedPortably() {
        SourceRange range = new SourceRange(1, 1, 1, 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceProvenance("/home/user/Set.protos", range));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceProvenance("C:\\Users\\user\\Set.protos", range));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceProvenance("protos/lib/../Set.protos", range));
    }

    @Test
    void standardLibraryCoreCannotBeInventedFromPhysicalSourcePlacement() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StandardModuleIdentity("std:core"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StandardModuleIdentity("std:core/Object"));
    }

    @Test
    void markdownLineEndingsAreCanonicalizedWithoutChangingItsContentModel() {
        StandardModuleIdentity module = new StandardModuleIdentity("std:collections/Set");
        Module documented = new Module(
                module,
                "First\r\nsecond\rthird",
                source("protos/lib/collections/Set.protos", 1, 1, 2, 1));

        assertEquals("First\nsecond\nthird", documented.documentation());
    }

    private static Artifact artifact(
            ProtosDocumentationModel.ExactArtifactScope scope,
            List<Module> modules,
            List<Symbol> symbols) {
        return new Artifact(new Generator("protos", "test"), scope, modules, symbols);
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
