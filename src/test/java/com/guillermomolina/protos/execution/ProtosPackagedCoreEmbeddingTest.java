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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosProcessRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

/**
 * PLAT054-3C / I086: a standard Polyglot Context with no options resolves the Core packaged in the
 * Protos JAR as a Truffle internal resource ({@link ProtosCoreResource}).
 *
 * <p>Every test asserts that the resolved Core lies in the Truffle internal-resource cache that
 * the build isolates below {@code target}, never in the checkout's {@code protos/lib}, so a pass
 * cannot come from the source tree. The JAR-level counterpart, run from outside the checkout
 * against the distributed JAR, is {@code dist/smoke_polyglot_embedding.sh}.
 */
final class ProtosPackagedCoreEmbeddingTest {
    private static final String SOURCE_TREE_LIBRARY = "protos/lib";

    private static ProtosEmbeddedProcess embedded(Context context) {
        context.enter();
        try {
            return ProtosLanguageContext.current().embeddedProcessOrNull();
        } finally {
            context.leave();
        }
    }

    private static Path resourceCache() {
        String cache = System.getProperty("polyglot.engine.userResourceCache");
        assertNotNull(cache, "the build isolates the Truffle internal-resource cache");
        return Path.of(cache).toAbsolutePath().normalize();
    }

    /** The resolved Core is the unpacked packaged resource, not the checkout source tree. */
    private static Path assertPackagedCore(Context context) throws IOException {
        Path core = embedded(context).coreRootForTesting().toRealPath();
        assertTrue(
                core.startsWith(resourceCache().toRealPath()),
                "Core resolved from the packaged resource: " + core);
        Path sourceTree = Path.of(SOURCE_TREE_LIBRARY).toAbsolutePath().toRealPath();
        assertFalse(core.startsWith(sourceTree), "Core must not come from the checkout: " + core);
        assertTrue(Files.isRegularFile(core.resolve("Context.protos")));
        return core;
    }

    private static long fileCount(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.count();
        }
    }

    @Test
    void optionFreeContextRunsThePublicExample() throws IOException {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.eval(
                    ProtosLanguage.ID,
                    """
                    truffleRun: () => { 42 }
                    """);
            Value run = context.getBindings(ProtosLanguage.ID).getMember("truffleRun");
            assertEquals(42, run.execute().asInt());
            assertEquals(42, run.execute().asInt());
            assertPackagedCore(context);

            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertFalse(bindings.hasMember("filesystem"), "packaged Core grants no Filesystem");
            assertFalse(bindings.hasMember("network"), "no default Network authority");
            // Pay as you grow: ordinary Closure calls allocate no Task and start no Actor carrier.
            assertEquals(
                    0,
                    embedded(context)
                            .processForTesting()
                            .rootActorForRuntime()
                            .executionDomain()
                            .liveTaskCount());
            assertFalse(embedded(context).actorCarrierSubstrateInitializedForTesting());
        }
    }

    @Test
    void constructionAndBindingsDoNotBootstrapThePackagedCore() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            Value bindings = context.getBindings(ProtosLanguage.ID);
            assertTrue(bindings.getMemberKeys().isEmpty());
            assertNull(embedded(context), "no Process before the first evaluation");
        }
    }

    @Test
    void standardModulesImportFromThePackagedCoreWithOrdinaryIdentity() throws IOException {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            String source = "m: import(\"std:test/Assertions\")\n0";
            context.eval(ProtosLanguage.ID, source);
            ProtosEmbeddedProcess process = embedded(context);
            ProtosObjectValue first = process.selectedModuleContext().orElseThrow();
            context.eval(ProtosLanguage.ID, source);
            ProtosObjectValue second = process.selectedModuleContext().orElseThrow();
            assertSame(
                    first.readLocalSlot("m").orElseThrow(),
                    second.readLocalSlot("m").orElseThrow(),
                    "READY module reused from the Actor-local cache");
            assertTrue(
                    process.processForTesting()
                            .rootActorForRuntime()
                            .moduleState()
                            .lookup(new ProtosModuleKey("std:test/Assertions"))
                            .isPresent(),
                    "canonical std: ModuleKey, independent of the physical Core origin");
            assertPackagedCore(context);
        }
    }

    @Test
    void contextsShareOneUnpackedCoreAndCloseLeavesNoResidue() throws IOException {
        Path core;
        long files;
        ProtosProcessRuntime leftProcess;
        try (Context left = Context.newBuilder(ProtosLanguage.ID).build();
                Context right = Context.newBuilder(ProtosLanguage.ID).build()) {
            left.eval(ProtosLanguage.ID, "v: 1\n0");
            right.eval(ProtosLanguage.ID, "v: 2\n0");
            assertNotSame(embedded(left), embedded(right), "one Process per Context");
            assertEquals(1, left.getBindings(ProtosLanguage.ID).getMember("v").asInt());
            assertEquals(2, right.getBindings(ProtosLanguage.ID).getMember("v").asInt());
            core = assertPackagedCore(left);
            assertEquals(core, assertPackagedCore(right), "unpacked once, not per Context");
            files = fileCount(resourceCache());
            leftProcess = embedded(left).processForTesting();
        }
        assertEquals(ProtosProcessRuntime.LifecycleState.TERMINATED, leftProcess.lifecycleState());
        assertEquals(files, fileCount(resourceCache()), "Context close adds no resource copies");
        assertTrue(Files.isRegularFile(core.resolve("Context.protos")));
    }

    @Test
    void javaScalarArgumentsStillAdmittedWithPackagedCore() {
        try (Context context = Context.newBuilder(ProtosLanguage.ID).build()) {
            context.eval(
                    ProtosLanguage.ID,
                    """
                    inc: (x) => { x + 1 }
                    size: (s) => { s.size() }
                    0
                    """);
            Value bindings = context.getBindings(ProtosLanguage.ID);
            Value inc = bindings.getMember("inc");
            Value size = bindings.getMember("size");
            for (Object argument : List.of((byte) 1, (short) 1, 1, 1L)) {
                assertEquals(2, inc.execute(argument).asInt());
            }
            assertEquals(3, size.execute("abc").asInt());
        }
    }
}
