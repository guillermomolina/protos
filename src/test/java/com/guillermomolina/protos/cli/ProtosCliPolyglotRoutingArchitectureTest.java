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
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosCliPolyglotRoutingArchitectureTest {
    @Test
    void ordinaryCliReplAndBundledToolsUseProcessScopedPolyglotEntry()
            throws Exception {
        String source =
                Files.readString(
                        Path.of(
                                "src",
                                "main",
                                "java",
                                "com",
                                "guillermomolina",
                                "protos",
                                "cli",
                                "ProtosCli.java"));

        assertTrue(source.contains("ProtosPolyglotRuntimeHost.open()"));
        assertTrue(source.contains("runtimeHost.hostProcess("));
        assertTrue(source.contains("processContext.execute("));
        assertTrue(source.contains("processContext.evaluatePersistent("));
        assertTrue(source.contains("executeStandaloneRootTask(session.executeModuleSource(source))"));
        assertTrue(source.contains("session.activation, session.runtimeHost"));
        assertFalse(source.contains("legacyToolSession("));
        assertFalse(source.contains("ProtosSourceCompiler"));
        assertTrue(source.contains("implements AutoCloseable"));
        assertTrue(source.contains("try (Session session = session("));
        assertFalse(source.contains("s.compiler.compile(input).call"));
        assertFalse(source.contains("s.compiler.compile(src)"));
        assertTrue(source.contains("Source.newBuilder(ProtosLanguage.ID"));
        assertTrue(source.contains("processContext.executeFile("));
        assertTrue(source.contains("processContext.executeModuleSource("));
        assertFalse(source.contains("private static Source sourceFromPath("));
        assertFalse(source.contains(".uri(exact.toUri())"));
    }
}
