/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THIS LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
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

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtosSourceReadabilityAuthorityTest {
    @TempDir Path root;

    @Test
    void exactAdmissionIsReadOnlyAndUnrelatedPathsStayDenied() throws Exception {
        Path admitted = write(root.resolve("admitted.protos"), "42");
        Path unrelated = write(root.resolve("unrelated.protos"), "99");
        ProtosSourceReadabilityAuthority authority = new ProtosSourceReadabilityAuthority();

        assertDeniedRead(authority, admitted);
        assertDeniedRead(authority, unrelated);

        authority.admit(admitted);
        assertReadable(authority, admitted);
        assertDeniedRead(authority, unrelated);
        assertDeniedWrite(authority, admitted);
        assertEquals(1, authority.admittedPathCountForTesting());

        authority.admit(admitted);
        assertEquals(1, authority.admittedPathCountForTesting(), "admission is idempotent");
    }

    @Test
    void independentAuthoritiesDoNotShareAdmissions() throws Exception {
        Path file = write(root.resolve("isolated.protos"), "1");
        ProtosSourceReadabilityAuthority first = new ProtosSourceReadabilityAuthority();
        ProtosSourceReadabilityAuthority second = new ProtosSourceReadabilityAuthority();

        first.admit(file);

        assertReadable(first, file);
        assertDeniedRead(second, file);
        assertFalse(second.isAdmittedForTesting(file));
    }

    @Test
    void selectedSymlinkSpellingDoesNotAdmitCanonicalTargetWhenSupported() throws Exception {
        Path target = write(root.resolve("target.protos"), "7");
        Path selected = root.resolve("selected.protos");
        try {
            Files.createSymbolicLink(selected, target.getFileName());
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            Assumptions.assumeTrue(false, "symbolic links unavailable: " + unavailable);
            return;
        }

        ProtosSourceReadabilityAuthority authority = new ProtosSourceReadabilityAuthority();
        authority.admit(selected);

        assertTrue(authority.isAdmittedForTesting(selected));
        assertFalse(authority.isAdmittedForTesting(target));
        assertReadable(authority, selected);
        assertDeniedRead(authority, target);
    }

    @Test
    void configuredIoAccessExplicitlyKeepsSocketsAndAmbientHostFilesDisabled()
            throws IOException {
        String source =
                Files.readString(
                        Path.of(
                                "src/main/java/com/guillermomolina/protos/execution/"
                                        + "ProtosSourceReadabilityAuthority.java"));
        assertTrue(source.contains(".allowHostSocketAccess(false)"));
        assertFalse(source.contains("IOAccess.ALL"));
        assertFalse(source.contains(".allowHostFileAccess(true)"));
    }

    private static Path write(Path path, String characters) throws IOException {
        Files.writeString(path, characters);
        return path.toAbsolutePath().normalize();
    }

    private static void assertReadable(
            ProtosSourceReadabilityAuthority authority, Path path) throws IOException {
        try (SeekableByteChannel ignored =
                authority.fileSystemForTesting()
                        .newByteChannel(path, Set.of(StandardOpenOption.READ))) {
            // Successful open is the evidence.
        }
    }

    private static void assertDeniedRead(
            ProtosSourceReadabilityAuthority authority, Path path) {
        assertDenied(
                () ->
                        authority.fileSystemForTesting()
                                .newByteChannel(path, Set.of(StandardOpenOption.READ))
                                .close());
    }

    private static void assertDeniedWrite(
            ProtosSourceReadabilityAuthority authority, Path path) {
        assertDenied(
                () ->
                        authority.fileSystemForTesting()
                                .newByteChannel(path, Set.of(StandardOpenOption.WRITE))
                                .close());
    }

    private static void assertDenied(CheckedOperation operation) {
        Exception failure = assertThrows(Exception.class, operation::run);
        assertTrue(
                failure instanceof IOException || failure instanceof SecurityException,
                () -> "unexpected denial exception: " + failure);
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run() throws Exception;
    }
}
