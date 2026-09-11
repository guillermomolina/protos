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

package com.guillermomolina.protos.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosProjectBindingTest {
    private static final Path ROOT =
            Path.of(System.getProperty("user.dir"))
                    .toAbsolutePath()
                    .normalize()
                    .resolve("project-binding-test-root");
    private static final Path MEMBER = ROOT.resolve("libs/member");

    @Test
    void projectionAndBindingDefensivelyFreezeTheirInputCollections() {
        ArrayList<ProtosProjectBindingProjection.PackageRef> projected = new ArrayList<>();
        projected.add(new ProtosProjectBindingProjection.PackageRef("root", ""));
        projected.add(new ProtosProjectBindingProjection.PackageRef("member", "libs/member"));

        ProtosProjectBindingProjection projection = projection(projected);
        projected.clear();

        ArrayList<ProtosProjectBinding.PackageRoot> roots = new ArrayList<>();
        roots.add(new ProtosProjectBinding.PackageRoot("root", "", ROOT));
        roots.add(new ProtosProjectBinding.PackageRoot("member", "libs/member", MEMBER));
        ArrayList<ProtosProjectBinding.Source> sources = new ArrayList<>();
        sources.add(new ProtosProjectBinding.Source("root", "Main", ROOT.resolve("Main.protos")));
        sources.add(new ProtosProjectBinding.Source("member", "Api", MEMBER.resolve("Api.protos")));

        ProtosProjectBinding binding = new ProtosProjectBinding(projection, roots, sources);
        roots.clear();
        sources.clear();

        assertEquals(2, projection.packages().size());
        assertEquals(2, binding.packageRoots().size());
        assertEquals(2, binding.sources().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> projection.packages().add(new ProtosProjectBindingProjection.PackageRef("x", "x")));
        assertThrows(
                UnsupportedOperationException.class,
                () -> binding.sources().add(new ProtosProjectBinding.Source("root", "Other", ROOT.resolve("Other.protos"))));
    }

    @Test
    void projectionRequiresExactRootPackageAndUniquePackageIdentityAndLocation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosProjectBindingProjection(
                        1,
                        ROOT,
                        "missing",
                        List.of(new ProtosProjectBindingProjection.PackageRef("root", "")),
                        "fresh"));
        assertThrows(
                IllegalArgumentException.class,
                () -> projection(
                        List.of(
                                new ProtosProjectBindingProjection.PackageRef("root", ""),
                                new ProtosProjectBindingProjection.PackageRef("root", "member"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> projection(
                        List.of(
                                new ProtosProjectBindingProjection.PackageRef("root", ""),
                                new ProtosProjectBindingProjection.PackageRef("member", ""))));
    }

    @Test
    void bindingRequiresCompleteProjectionCoverageAndCanonicalRootWitness() {
        ProtosProjectBindingProjection projection = standardProjection();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosProjectBinding(
                        projection,
                        List.of(new ProtosProjectBinding.PackageRoot("root", "", ROOT)),
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosProjectBinding(
                        projection,
                        List.of(
                                new ProtosProjectBinding.PackageRoot("root", "", Path.of("/other")),
                                new ProtosProjectBinding.PackageRoot("member", "libs/member", MEMBER)),
                        List.of()));
    }

    @Test
    void bindingRejectsForeignEscapingAndDuplicateSourceIdentities() {
        ProtosProjectBindingProjection projection = standardProjection();
        List<ProtosProjectBinding.PackageRoot> roots = standardRoots();

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosProjectBinding(
                        projection,
                        roots,
                        List.of(new ProtosProjectBinding.Source("foreign", "Main", ROOT.resolve("Main.protos")))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosProjectBinding(
                        projection,
                        roots,
                        List.of(new ProtosProjectBinding.Source("member", "Api", ROOT.resolve("Outside.protos")))));
        ProtosProjectBinding.Source source =
                new ProtosProjectBinding.Source("root", "Main", ROOT.resolve("Main.protos"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProtosProjectBinding(projection, roots, List.of(source, source)));
    }

    @Test
    void carrierDoesNotInterpretFreshnessWitnessContent() {
        ProtosProjectBindingProjection projection = new ProtosProjectBindingProjection(
                ProtosProjectBindingProjection.CURRENT_GENERATION,
                ROOT,
                "root",
                List.of(new ProtosProjectBindingProjection.PackageRef("root", "")),
                "opaque:authority-owned:value");

        assertEquals("opaque:authority-owned:value", projection.freshnessWitness());
    }

    private static ProtosProjectBindingProjection standardProjection() {
        return projection(
                List.of(
                        new ProtosProjectBindingProjection.PackageRef("root", ""),
                        new ProtosProjectBindingProjection.PackageRef("member", "libs/member")));
    }

    private static ProtosProjectBindingProjection projection(
            List<ProtosProjectBindingProjection.PackageRef> packages) {
        return new ProtosProjectBindingProjection(
                ProtosProjectBindingProjection.CURRENT_GENERATION,
                ROOT,
                "root",
                packages,
                "opaque-freshness-witness");
    }

    private static List<ProtosProjectBinding.PackageRoot> standardRoots() {
        return List.of(
                new ProtosProjectBinding.PackageRoot("root", "", ROOT),
                new ProtosProjectBinding.PackageRoot("member", "libs/member", MEMBER));
    }
}
